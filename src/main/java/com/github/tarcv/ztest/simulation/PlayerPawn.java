package com.github.tarcv.ztest.simulation;

public class PlayerPawn extends Actor {
    private final Player player;

    public PlayerPawn(Simulation simulation, Player player, int health, int armor) {
        super(simulation);
        this.player = player;
        this.A_GiveInventory("Health", health);
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
        // TODO
    }

    public void printbold(String format, Object[] args) {
        player.printbold(format, args);
    }
}
