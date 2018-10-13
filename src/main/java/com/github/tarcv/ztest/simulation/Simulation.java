package com.github.tarcv.ztest.simulation;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tarcv.ztest.simulation.Simulation.CVarTypes.USER;

public class Simulation {
    private final Player[] players = new Player[32];
    private final List<Thing> things = new ArrayList<>();
    private final List<MapContext> scriptEventListeners = new ArrayList<>();
    private final List<DelayableRunnable> scheduledRunnables = new ArrayList<>();
    private final List<SchedulerThread> schedulerThreads = new ArrayList<SchedulerThread>();
    private final Map<String, CVarTypes> cvarTypes = new HashMap<>();
    private final Map<String, Object> serverCvarValues = new HashMap<>();
    private final Random randomSource;
    private final Object lock;
    private int tick = 0;

    public Simulation(long seed) {
        this.randomSource = new Random(seed);
        this.lock = new Object();

        this.cvarTypes.put("playerclass", USER);
    }

    public Player addPlayer(String name, int health, int armor, boolean isBot) {
        synchronized (lock) {
            Player player = new Player(this, name, health, armor, isBot);
            addPlayerToArray(player);
            return player;
        }
    }

    private void addPlayerToArray(Player player) {
        synchronized (lock) {
            for (int i = 0; i < players.length; i++) {
                if (players[i] == null) {
                    players[i] = player;
                    return;
                }
            }
            throw new IllegalStateException("Max number of players is already reached");
        }
    }

    void registerScriptEventsListener(MapContext mapContext) {
        scriptEventListeners.add(mapContext);
    }

    public void registerThing(Thing thing) {
        synchronized (lock) {
            things.add(thing);
        }
    }

    void onPlayerJoined(PlayerPawn player) {
        fireScriptEventListeners(listener -> listener.onPlayerJoined(player));
    }

    void onPlayerRespawned(PlayerPawn player) {
        fireScriptEventListeners(listener -> listener.onPlayerRespawned(player));
    }

    void scheduleOnNextTic(DelayableRunnable runnable) {
        synchronized (lock) {
            scheduledRunnables.add(runnable);
        }
    }

    List<Player> getPlayers() {
        synchronized (lock) {
            return Stream.of(players)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    Player getPlayerByIndex(int playerNumber) {
        synchronized (lock) {
            Player player = players[playerNumber];
            if (player == null) throw new IllegalStateException("Player with this number is not present");
            return player;
        }
    }

    int getPlayerIndex(Player player) {
        synchronized (lock) {
            for (int i = 0; i < players.length; i++) {
                if (players[i] == player) return i;
            }
        }
        throw new IllegalStateException("Player is not present in the simulation");
    }

    int getCurrentTic() {
        synchronized (lock) {
            return tick;
        }
    }

    CVarTypes getCVarType(String name) {
        synchronized (lock) {
            CVarTypes cVarType = cvarTypes.get(name);
            if (cVarType == null) throw new IllegalStateException("CVAR was not registered by a test");
            return cVarType;
        }
    }

    Object getCVarInternal(String name) {
        synchronized (lock) {
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
            synchronized (lock) {
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

    void setCVar(String name, String newValue) {
        synchronized (lock) {
            if (getCVarType(name).isPlayerOwned()) throw new IllegalArgumentException("CVAR is not a server one");
            serverCvarValues.put(name, newValue);
        }
    }

    void onDeath(Actor actor, Thing killedBy) {
        // TODO: play Death or XDeath animation
        if (actor instanceof PlayerPawn) {
            synchronized (lock) {
                PlayerPawn playerPawn = (PlayerPawn) actor;
                fireScriptEventListeners(listener -> listener.onPlayerKilled(playerPawn));
            }
        }
    }

    private void fireScriptEventListeners(Consumer<MapContext> mapContextConsumer) {
        synchronized (lock) {
            scriptEventListeners.forEach(mapContextConsumer);
        }
    }

    void createThing(String type, double x, double y, int z, int newtid, int angle) {
        synchronized (lock) {
            Thing newThing;
            try {
                newThing = (Thing) Class.forName(type).getConstructor(Simulation.class).newInstance(this);
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

    public void runTicks(int ticks) throws TimeoutException, InterruptedException {
        for (int i = 0; i < ticks; i++) {
            synchronized (lock) {
                while (!scheduledRunnables.isEmpty()) {
                    DelayableRunnable runnable = scheduledRunnables.remove(randomSource.nextInt(scheduledRunnables.size()));
                    SchedulerThread thread = new SchedulerThread(runnable);
                    schedulerThreads.add(thread);
                }

                List<SchedulerThread> newThreads = new ArrayList<>();
                while (!schedulerThreads.isEmpty()) {
                    SchedulerThread thread = schedulerThreads.remove(0);
                    if (!thread.tryJoin()) {
                        newThreads.add(thread);
                    }
                }
                schedulerThreads.addAll(newThreads);
            }
        }
    }

    enum CVarTypes {
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

    private static class SchedulerThread {
        private final DelayableRunnable runnable;
        private final Thread thread;

        private SchedulerThread(DelayableRunnable runnable) {
            this.runnable = runnable;

            Thread newThread = new Thread(runnable, "Scheduled actions thread");
            newThread.setDaemon(true);
            this.thread = newThread;
        }

        private boolean isDelayed() {
            return runnable.delayableContext.get().delayed.get();
        }

        private void start() {
            thread.start();
        }

        private boolean tryJoin() throws InterruptedException, TimeoutException {
            thread.join(200);
            if (thread.isAlive()) {
                boolean isDelayed = runnable.delayableContext.get().delayed.get();
                if (isDelayed) {
                    return false;
                } else {
                    throw new TimeoutException("Some thread is run-away. Probably you forgot to add 'delay'?");
                }
            } else {
                return true;
            }
        }
    }

    static class DelayableContext {
        private final AtomicBoolean delayed = new AtomicBoolean();

        void setDelayed(boolean value) {
            delayed.set(value);
        }
    }

    abstract static class DelayableRunnable implements Runnable {
        private ThreadLocal<DelayableContext> delayableContext = ThreadLocal.withInitial(DelayableContext::new);
    }
}