package com.github.tarcv.ztest.simulation;

public class Player implements Owner {
    private final Hud console = new Hud();
    private final Object lock = new Object();
    private final String name;
    private final Simulation simulation;
    private final int initialHealth;
    private final int initialArmor;
    private PlayerPawn pawn;

    public Player(Simulation simulation, String name, int initialHealth, int initialArmor) {
        this.simulation = simulation;
        this.name = name;
        this.initialHealth = initialHealth;
        this.initialArmor = initialArmor;
    }

    protected void A_Print(String format, Object[] arguments) {
        Hud.print(format, arguments);
    }

    public void printbold(String format, Object[] args) {
        Hud.print(format, args);
    }

    public void joinGame() {
        synchronized (lock) {
            this.pawn = new PlayerPawn(simulation, this, initialHealth, initialArmor);
            simulation.onPlayerJoined(pawn);
        }
    }
}
