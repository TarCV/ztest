package com.github.tarcv.ztest.simulation;

import net.jcip.annotations.GuardedBy;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tarcv.ztest.simulation.Simulation.CVarTypes.USER;

public class Simulation {
    private final Player[] players = new Player[32];
    private final List<Thing> things = new ArrayList<>();
    private final List<MapContext> scriptEventListeners = new ArrayList<>();

    private final List<TickThread> tickThreads = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, CVarTypes> cvarTypes = new HashMap<>();
    private final Map<String, Object> serverCvarValues = new HashMap<>();
    private final ClassGetter classGetter = new ClassGetter();
    private final Random randomSource;

    private final ReentrantLock tickLock = new ReentrantLock();

    private final List<Runnable> scheduledRunnables = Collections.synchronizedList(new ArrayList<>());

    @GuardedBy("tickLock")
    private int tick = 0;

    public Simulation(long seed) {
        this.randomSource = new Random(seed);
        this.cvarTypes.put("playerclass", USER);
    }

    public Player addPlayer(String name, int health, int armor, boolean isBot) {
        tickLock.lock();
        try {
            Player player = new Player(this, name, health, armor, isBot);
            addPlayerToArray(player);
            return player;
        } finally {
            tickLock.unlock();
        }
    }

    private void addPlayerToArray(Player player) {
        {
            assert tickLock.isHeldByCurrentThread();
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
        if (getCurrentTic() != 0)   throw new IllegalStateException("Can only be called during the very first tick");
        scriptEventListeners.add(mapContext);
        mapContext.onMapLoaded();
    }

    public void registerThing(Thing thing) {
        {
            assert tickLock.isHeldByCurrentThread();
            things.add(thing);
        }
    }

    void onPlayerJoined(PlayerPawn player) {
        fireScriptEventListeners(listener -> listener.onPlayerJoined(player));
    }

    void onPlayerRespawned(PlayerPawn player) {
        fireScriptEventListeners(listener -> listener.onPlayerRespawned(player));
    }

    void scheduleOnNextTic(Runnable runnable) {
        tickLock.lock();
        try {
            scheduledRunnables.add(runnable);
        } finally {
            tickLock.unlock();
        }
    }

    List<Player> getPlayers() {
        {
            assert tickLock.isHeldByCurrentThread();
            return Stream.of(players)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    Player getPlayerByIndex(int playerNumber) {
        {
            assert tickLock.isHeldByCurrentThread();
            Player player = players[playerNumber];
            if (player == null) throw new IllegalStateException("Player with this number is not present");
            return player;
        }
    }

    int getPlayerIndex(Player player) {
        {
            assert tickLock.isHeldByCurrentThread();
            for (int i = 0; i < players.length; i++) {
                if (players[i] == player) return i;
            }
        }
        throw new IllegalStateException("Player is not present in the simulation");
    }

    int getCurrentTic() {
        tickLock.lock();
        try {
            return tick;
        } finally {
            tickLock.unlock();
        }
    }

    CVarTypes getCVarType(String name) {
        {
            assert tickLock.isHeldByCurrentThread();
            CVarTypes cVarType = cvarTypes.get(name);
            if (cVarType == null) throw new IllegalStateException("CVAR was not registered by a test");
            return cVarType;
        }
    }

    Object getCVarInternal(String name) {
        {
            assert tickLock.isHeldByCurrentThread();
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
                assert tickLock.isHeldByCurrentThread();
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
        {
            assert tickLock.isHeldByCurrentThread();
            if (getCVarType(name).isPlayerOwned()) throw new IllegalArgumentException("CVAR is not a server one");
            serverCvarValues.put(name, newValue);
        }
    }

    void onDeath(Actor actor, Thing killedBy) {
        // TODO: play Death or XDeath animation
        if (actor instanceof PlayerPawn) {
            {
                assert tickLock.isHeldByCurrentThread();
                PlayerPawn playerPawn = (PlayerPawn) actor;
                fireScriptEventListeners(listener -> listener.onPlayerKilled(playerPawn));
            }
        }
    }

    private void fireScriptEventListeners(Consumer<MapContext> mapContextConsumer) {
        {
            assert tickLock.isHeldByCurrentThread();
            scriptEventListeners.forEach(mapContextConsumer);
        }
    }

    void createThing(String type, double x, double y, int z, int newtid, int angle) {
        {
            assert tickLock.isHeldByCurrentThread();
            Thing newThing;
            try {
                newThing = (Thing) classForSimpleName(type).getConstructor(Simulation.class).newInstance(this);
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
            {
                assert tickLock.getHoldCount() == 0;

                tickLock.lock();
                try {
                    scriptEventListeners
                            .stream()
                            .flatMap(listener -> listener.createInitRunnables().stream())
                            .forEach(runnable -> {
                                TickThread thread = new TickThread(tickLock, runnable);
                                thread.start();
                                tickThreads.add(thread);
                            });
                } finally {
                    tickLock.unlock();
                }

                synchronized (scheduledRunnables) {
                    while (!scheduledRunnables.isEmpty()) {
                        Runnable runnable = scheduledRunnables.remove(randomSource.nextInt(scheduledRunnables.size()));
                        TickThread thread = new TickThread(tickLock, runnable);
                        thread.start();
                        tickThreads.add(thread);
                    }
                }

                synchronized (tickThreads) {
                    List<TickThread> newThreads = new ArrayList<>();
                    while (!tickThreads.isEmpty()) {
                        TickThread thread = tickThreads.remove(0);
                        if (!thread.tryJoin()) {
                            newThreads.add(thread);
                        }
                    }
                    tickThreads.addAll(newThreads);
                }

                tickLock.lock();
                try {
                    this.tick += 1;
                } finally {
                    tickLock.unlock();
                }
            }
        }
    }

    Class<?> classForSimpleName(String className) throws ClassNotFoundException {
        return classGetter.forSimpleName(className);
    }

    void withTickLock(Runnable runnable) {
        tickLock.lock();
        try {
            runnable.run();
        } finally {
            tickLock.unlock();
        }
    }

    static DelayableContext getThreadContext() {
        return ((TickThread) Thread.currentThread()).context;
    }

    void assertTickLockHeld() {
        assert tickLock.isHeldByCurrentThread();
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

    private static class TickThread extends Thread {
        private final DelayableContext context;

        private TickThread(ReentrantLock globalLock, Runnable runnable) {
            super(() -> {
                try {
                    globalLock.lock();
                    runnable.run();
                } finally {
                    assert globalLock.isHeldByCurrentThread();
                    globalLock.unlock();
                }
            }, "Scheduled actions thread");
            this.context = new DelayableContext(globalLock);
            this.setDaemon(true);
        }

        private boolean isDelayed() {
            return context.delayed.get();
        }

        private boolean tryJoin() throws InterruptedException, TimeoutException {
            this.join(1000 / 35 * 5);
            if (this.isAlive()) {
                boolean isDelayed = context.delayed.get();
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
        private final ReentrantLock tickLock;

        @GuardedBy("tickLock")
        private AtomicBoolean delayed = new AtomicBoolean();

        DelayableContext(ReentrantLock tickLock) {
            this.tickLock = tickLock;
        }

        /*
            delayed -> delayed          lock count doesn't change (should be 0)
            delayed -> not delayed      lock count increments by 1 (becomes 1)
            not delayed -> delayed      lock count decrements by 1 (becomes 0
            not delayed -> not delayed  lock count doesn't change (should be 1)
         */
        void setDelayed(boolean value) {
            tickLock.lock();
            boolean wasDelayed = delayed.get();
            delayed.set(value);
            if (wasDelayed && !value) {
                tickLock.lock();
            } else if (!wasDelayed && value) {
                tickLock.unlock();
            }
            tickLock.unlock();
            assert tickLock.isHeldByCurrentThread() != value;
        }
    }
}