package com.github.tarcv.ztest.simulation;

public abstract class CustomInventory extends Thing {
    protected CustomInventory(Simulation simulation) {
        super(simulation);
    }

    public Object Pickup() {
        throw new AssertionError("CustomInventory inheritors must implement Pickup states");
    }

    final void pickupBy(Thing owner) {
        this.setActivator(owner);
        simulation.scheduleOnNextTic(new NamedRunnable() {
            @Override
            public String name() {
                CustomInventory customInventory = CustomInventory.this;
                return customInventory.getClass().getSimpleName() + "@" + System.identityHashCode(customInventory);
            }

            @Override
            public void run() {
                Pickup();
            }
        });
    }
}
