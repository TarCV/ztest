package com.github.tarcv.ztest.simulation;

public abstract class CustomInventory extends Thing {
    private volatile Thing owner = null;

    protected CustomInventory(Simulation simulation) {
        super(simulation);
    }

    public Object Pickup() {
        throw new AssertionError("CustomInventory inheritors must implement Pickup states");
    }

    final void pickupBy(Thing owner) {
        this.owner = owner;
        this.setActivator(owner);
        simulation.scheduleOnNextTic(this::Pickup);
    }
}
