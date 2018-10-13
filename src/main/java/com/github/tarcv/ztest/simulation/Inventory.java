package com.github.tarcv.ztest.simulation;

public class Inventory extends CustomInventory {
    protected Inventory(Simulation simulation) {
        super(simulation);
    }

    @Override
    public final Object Pickup() {
        return null;
    }
}
