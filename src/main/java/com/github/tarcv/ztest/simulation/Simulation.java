package com.github.tarcv.ztest.simulation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tarcv.ztest.simulation.Simulation.CVarTypes.USER;

public class Simulation {
    private final Random randomSource;

    private final Player[] players = new Player[32];
    private final List<Thing> things = new ArrayList<>();
    private final List<ScriptContext> scriptEventListeners = Collections.synchronizedList(new ArrayList<>());
    private final PerTickExecutor executor;
    private final Map<String, CVarTypes> cvarTypes = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Object> serverCvarValues = Collections.synchronizedMap(new HashMap<>());
    private final ClassGetter classGetter = new ClassGetter();

    public Simulation(long seed) {
        this.randomSource = new Random(seed);
        this.executor = new PerTickExecutor(randomSource);
        this.cvarTypes.put("playerclass", USER);
    }

    public Player addPlayer(String name, int health, int armor, boolean isBot) {
        return executor.withTickLock(() -> {
            Player player = new Player(this, name, health, armor, isBot);
            addPlayerToArray(player);
            return player;
        });
    }

    private void addPlayerToArray(Player player) {
        {
            executor.assertTickLockHeld();
            for (int i = 0; i < players.length; i++) {
                if (players[i] == null) {
                    players[i] = player;
                    return;
                }
            }
            throw new IllegalStateException("Max number of players is already reached");
        }
    }

    public void registerScriptEventsListener(ScriptContext scriptContext) {
        int currentTic = getCurrentTick();
        if (currentTic != -1)   throw new IllegalStateException(
                String.format("Can only be called before the very first tick. Got: %d", currentTic));
        scriptContext.assertIsMainScriptContext();

        executor.withTickLock(() -> {
            scriptEventListeners.add(scriptContext);
        });
    }

    void registerThing(Thing thing) {
        {
            executor.assertTickLockHeld();
            things.add(thing);
        }
    }

    void onPlayerJoined(PlayerPawn player) {
        fireScriptEventListeners(listener -> listener.onPlayerJoined(player));
    }

    void onPlayerRespawned(PlayerPawn player) {
        fireScriptEventListeners(listener -> listener.onPlayerRespawned(player));
    }

    void scheduleOnNextTic(NamedRunnable runnable) {
        executor.scheduleRunnable(runnable);
    }

    List<Player> getPlayers() {
        {
            executor.assertTickLockHeld();
            return Stream.of(players)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    Player getPlayerByIndex(int playerNumber) {
        {
            executor.assertTickLockHeld();
            Player player = players[playerNumber];
            if (player == null) throw new IllegalStateException("Player with this number is not present");
            return player;
        }
    }

    int getPlayerIndex(Player player) {
        {
            executor.assertTickLockHeld();
            for (int i = 0; i < players.length; i++) {
                if (players[i] == player) return i;
            }
        }
        throw new IllegalStateException("Player is not present in the simulation");
    }

    int getCurrentTick() {
        return executor.getCurrentTick();
    }

    CVarTypes getCVarType(String name) {
        return executor.withTickLock(() -> {
            CVarTypes cVarType = cvarTypes.get(name);
            if (cVarType == null) throw new IllegalStateException("CVAR was not registered by a test");
            return cVarType;
        });
    }

    Object getCVarInternal(String name) {
        {
            executor.assertTickLockHeld();
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

    private List<Thing> getThingsByTid(int tid, Thing activator) {
        if (tid != 0) {
            {
                executor.assertTickLockHeld();
                return things.stream()
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
        executor.withTickLock(() -> {
            if (getCVarType(name).isPlayerOwned()) throw new IllegalArgumentException("CVAR is not a server one");
            serverCvarValues.put(name, newValue);
        });
    }

    void onDeath(Actor actor, Thing killedBy) {
        // TODO: play Death or XDeath animation
        if (actor instanceof PlayerPawn) {
            {
                executor.assertTickLockHeld();
                PlayerPawn playerPawn = (PlayerPawn) actor;
                fireScriptEventListeners(listener -> listener.onPlayerKilled(playerPawn));
            }
        }
    }

    private void fireScriptEventListeners(Consumer<ScriptContext> mapContextConsumer) {
        executor.withTickLock(() -> {
            synchronized (scriptEventListeners) {
                scriptEventListeners.forEach(mapContextConsumer);
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
            executor.assertTickLockHeld();
            Thing newThing;
            try {
                newThing = (Thing) getConstructor(classForSimpleName(type)).newInstance(this);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException | ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            newThing.setPosition(x, y, z);
            newThing.setAngle(angle);
            newThing.setTid(newtid);

            things.add(newThing);
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

    public void runAtLeastTicks(int seconds, Predicate<List<String>> isIdle) {
        try {
            if (executor.getCurrentTick() == -1) {
                synchronized (scriptEventListeners) {
                    List<NamedRunnable> openRunnables = scriptEventListeners.stream()
                            .flatMap(listener -> listener.createInitRunnables().stream())
                            .collect(Collectors.toList());
                    executor.executeTickWithRunnables(openRunnables);
                }
            }

            boolean isSimIdle = false;
            for (int i = 0; i < seconds || !isSimIdle; i++) {
                withTickLock(() -> {
                    int currentTick = this.getCurrentTick();
                    double second = currentTick / 35.0;
                    System.out.printf("-- Tick %d | %.2f second in the sim ---------------------%n",
                            currentTick, second);
                });

                for (int j = 0; j < 17; j++) {
                    executor.executeTick();
                }
                isSimIdle = isIdle.test(executor.getActiveRunnables());
            }
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
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

    public void withTickLock(Runnable runnable) {
        executor.withTickLock(runnable);
    }

    public void assertTickLockHeld() {
        executor.assertTickLockHeld();
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