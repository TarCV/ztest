package com.github.tarcv.ztest.simulation;

import java.util.*;

public class Simulation {
    private final Map<Integer, List<Thing>> tidsToThings = new HashMap<>();
    private final Player[] players = new Player[32];
    private final List<Thing> things = new ArrayList<>();
    private final List<MapContext> scriptEventListeners = new ArrayList<>();
    private final List<Runnable> scheduledRunnables = new ArrayList<>();
    private final Random randomSource;
    private final Object lock;

    public Simulation(long seed) {
        this.randomSource = new Random(seed);
        this.lock = new Object();
    }

    public Player addPlayer(String name, int health, int armor) {
        synchronized (lock) {
            Player player = new Player(this, name, health, armor);
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
        synchronized (lock) {
            scriptEventListeners.forEach(listener -> {
                listener.onPlayerJoined(player);
            });
        }
    }

    void scheduleOnNextTic(Runnable runnable) {
        synchronized (lock) {
            scheduledRunnables.add(runnable);
        }
    }
}