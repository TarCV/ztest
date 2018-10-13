package com.github.tarcv.ztest.simulation;

public abstract class CustomInventory extends Thing {
    private volatile Thing owner = null;

    protected CustomInventory(Simulation simulation) {
        super(simulation);
    }

    public Object Pickup() {
        throw new AssertionError("CustomInventory inheritors must implement Pickup states");
    }

    public final void pickupBy(Thing owner) {
        if (!(owner instanceof Owner)) {
            throw new IllegalArgumentException("Trying to give item to " + owner.getClass().getSimpleName());
        }
        this.owner = owner;
        this.setActivator(owner);
        this.Pickup();
    }
}
