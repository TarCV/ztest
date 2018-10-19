package com.github.tarcv.ztest.simulation;

public class PlayerPawn extends Actor {
    private final Player player;
    private final int classIndex;

    PlayerPawn(Simulation simulation, Player player, int playerclass, int health, int armor) {
        super(simulation);
        this.player = player;
        this.classIndex = playerclass;
        int currentHealth = getHealth();
        if (health > currentHealth) {
            this.A_GiveInventory("Health", health - currentHealth);
        } else if (health < currentHealth) {
            this.A_TakeInventory("Health", currentHealth - health);
        }
        this.A_GiveInventory("Armor", armor);
    }

    Player getPlayer() {
        return player;
    }

    @Override
    protected Object Spawn() {
        return null;
    }

    void A_Print(String format, Object... arguments) {
        player.A_Print(format, arguments);
    }

    void SetPlayerProperty(int which, int set) {
        player.setProperty(which, set);
    }

    void print(String format, Object[] args) {
        player.printbold(format, args);
    }

    int getClassIndex() {
        return classIndex;
    }
}
