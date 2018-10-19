package com.github.tarcv.ztest.simulation;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tarcv.ztest.simulation.Simulation.CVarTypes.USER;

public class Simulation<T extends ScriptContext> {
    private final Random randomSource;
    private final ScriptThreadEnforcer<SimulationData> data;
    private final List<ScriptContext<T>> scriptEventListeners = Collections.synchronizedList(new ArrayList<>());
    private final PerTickExecutor executor;
    private final Map<String, CVarTypes> cvarTypes = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Object> serverCvarValues = Collections.synchronizedMap(new HashMap<>());
    private final ClassGetter classGetter = new ClassGetter();

    public Simulation(long seed) {
        this.randomSource = new Random(seed);
        this.executor = new PerTickExecutor(randomSource);
        this.data = new ScriptThreadEnforcer<>(executor, new SimulationData());
        this.cvarTypes.put("playerclass", USER);
    }

    public Player addPlayer(String name, int health, int armor, boolean isBot) {
        return executor.executeWithinScriptThread(() -> {
            Player player = new Player(this, name, health, armor, isBot);
            addPlayerToArray(player);
            return player;
        });
    }

    private void addPlayerToArray(Player player) {
        executor.assertIsFiberThread();
        for (int i = 0; i < data.get().players.length; i++) {
                if (data.get().players[i] == null) {
                    data.get().players[i] = player;
                    return;
                }
            }
            throw new IllegalStateException("Max number of players is already reached");
    }

    public void registerScriptEventsListener(ScriptContext scriptContext) {
        int currentTic = getCurrentTick();
        if (currentTic != -1)   throw new IllegalStateException(
                String.format("Can only be called before the very first tick. Got: %d", currentTic));
        scriptContext.assertIsMainScriptContext();

        executor.executeWithinScriptThread(() -> {
            scriptEventListeners.add(scriptContext);
            return null;
        });
    }

    void registerThing(Thing thing) {
        {
            executor.assertIsFiberThread();
            data.get().things.add(thing);
        }
    }

    void onPlayerJoined(PlayerPawn player) {
        fireScriptEventListeners(listener -> listener.onPlayerJoined(player));
    }

    void onPlayerRespawned(PlayerPawn player) {
        fireScriptEventListeners(listener -> listener.onPlayerRespawned(player));
    }

    void scheduleOnNextTic(ScriptContext.NamedRunnable runnable) {
        executor.scheduleRunnable(runnable);
    }

    List<Player> getPlayers() {
        {
            executor.assertIsFiberThread();
            return Stream.of(data.get().players)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    Player getPlayerByIndex(int playerNumber) {
        {
            executor.assertIsFiberThread();
            Player player = data.get().players[playerNumber];
            if (player == null) throw new IllegalStateException("Player with this number is not present");
            return player;
        }
    }

    int getPlayerIndex(Player player) {
        {
            executor.assertIsFiberThread();
            for (int i = 0; i < data.get().players.length; i++) {
                if (data.get().players[i] == player) return i;
            }
        }
        throw new IllegalStateException("Player is not present in the simulation");
    }

    int getCurrentTick() {
        return executor.getCurrentTick();
    }

    CVarTypes getCVarType(String name) {
        return executor.executeWithinScriptThread(() -> {
            CVarTypes cVarType = cvarTypes.get(name);
            if (cVarType == null) throw new IllegalStateException("CVAR was not registered by a test");
            return cVarType;
        });
    }

    Object getCVarInternal(String name) {
        {
            executor.assertIsFiberThread();
            if (getCVarType(name).isPlayerOwned()) throw new IllegalArgumentException("CVAR is not a server one");
            return serverCvarValues.get(name);
        }
    }

    int random(int min, int maxInclusive) {
        return min + randomSource.nextInt(maxInclusive - min + 1);
    }

    List<Thing> assertedGetThingsByTid(int tid, Thing activator) {
        List<Thing> things = getThingsByTid(tid, activator);
        if (things.isEmpty()) {
            throw new IllegalStateException("No things were found by tid");
        }
        return things;
    }

    private List<Thing> getThingsByTid(int tid, @Nullable Thing activator) {
        if (tid != 0) {
            {
                executor.assertIsFiberThread();
                return data.get().things.stream()
                        .filter(t -> t.getTid() == tid)
                        .collect(Collectors.toList());
            }
        } else if (activator != null) {
            return Collections.singletonList(activator);
        } else {
            throw new IllegalArgumentException("tid of 0 can't be used");
        }
    }

    public void setCVar(String name, Object newValue) {
        executor.executeWithinScriptThread(() -> {
            if (getCVarType(name).isPlayerOwned()) throw new IllegalArgumentException("CVAR is not a server one");
            printlnMarked("setting " + name);
            serverCvarValues.put(name, newValue);
        });
    }

    void onDeath(Actor actor, Thing killedBy) {
        // TODO: play Death or XDeath animation
        if (actor instanceof PlayerPawn) {
            {
                executor.assertIsFiberThread();
                PlayerPawn playerPawn = (PlayerPawn) actor;
                fireScriptEventListeners(listener -> listener.onPlayerKilled(playerPawn));
            }
        }
    }

    private void fireScriptEventListeners(Consumer<ScriptContext> mapContextConsumer) {
        executor.executeWithinScriptThread(() -> {
            synchronized (scriptEventListeners) {
                scriptEventListeners.forEach(mapContextConsumer);
                return null;
            }
        });
    }

    static Constructor<?> getConstructor(Class<?> aClass) throws NoSuchMethodException {
        Constructor<?> constructor = aClass.getDeclaredConstructor(Simulation.class);
        constructor.setAccessible(true);
        return constructor;
    }

    void createThing(String type, double x, double y, int z, int newtid, int angle) {
        {
            executor.assertIsFiberThread();
            Thing newThing;
            try {
                newThing = (Thing) getConstructor(classForSimpleName(type)).newInstance(this);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException | ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            newThing.setPosition(x, y, z);
            newThing.setAngle(angle);
            newThing.setTid(newtid);

            data.get().things.add(newThing);
        }
    }

    int createUniqueTid() {
        for (int i = 0; i < 10; ++i) {
            int tid = Math.abs(randomSource.nextInt());
            if (getThingsByTid(tid, null).isEmpty()) {
                return tid;
            }
        }
        return 0;
    }

    public void runAtLeastTicks(int ticks, Predicate<List<String>> isIdle) {
        try {
            if (executor.getCurrentTick() == -1) {
                synchronized (scriptEventListeners) {
                    List<ScriptContext.NamedRunnable> openRunnables = scriptEventListeners.stream()
                            .flatMap(listener -> listener.createInitRunnables().stream())
                            .collect(Collectors.toList());
                    printlnMarked("Executing OPEN scripts");
                    executor.executeTickWithRunnables(openRunnables);
                }
            }

            boolean isSimIdle = false;
            for (int i = 0; i < ticks || !isSimIdle; i++) {
                int currentTick = executor.getCurrentTick();
                double second = currentTick / 35.0;
                printfMarked("-- Tick %d | %.2f second in the sim ---------------------%n",
                        currentTick, second);

                executor.executeTick();
                isSimIdle = isIdle.test(executor.getActiveRunnables());
            }
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public void printlnMarked(String s) {
        executor.printlnMarked(s);
    }

    Class<?> classForSimpleName(String className) throws ClassNotFoundException {
        return classGetter.forSimpleName(className);
    }

    public void registerCVar(String name, CVarTypes cvarType) {
        this.cvarTypes.put(name, cvarType);
    }

    ThreadContext getThreadContext() {
        return executor.getThreadContext();
    }

    void assertTickLockHeld() {
        executor.assertIsFiberThread();
    }

    public void withTickLock(Runnable runnable) {
        executor.executeWithinScriptThread(runnable);
    }

    public void printfMarked(String format, Object... args) {
        executor.printfMarked(format, args);
    }

    private static class SimulationData {
        // Should be accessed within script context only
        private final Player[] players = new Player[32];
        private final List<Thing> things = new ArrayList<>();
    }

    public enum CVarTypes {
        USER(false, true),
        USER_CUSTOM(true, true),
        SERVER(false, false),
        SERVER_CUSTOM(true, false);

        private final boolean modifiableFromScripts;
        private final boolean isPlayerOwned;

        CVarTypes(boolean modifiableFromScripts, boolean isPlayerOwned) {
            this.modifiableFromScripts = modifiableFromScripts;
            this.isPlayerOwned = isPlayerOwned;
        }

        boolean isModifiableFromScripts() {
            return this.modifiableFromScripts;
        }

        boolean isPlayerOwned() {
            return this.isPlayerOwned;
        }
    }
}