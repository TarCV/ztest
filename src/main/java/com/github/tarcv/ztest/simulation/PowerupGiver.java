package com.github.tarcv.ztest.simulation;

public class PowerupGiver extends CustomInventory {
    protected void setProperty(String property, int r, int g, int n, double r1, double g1, double b1) {
        if (!"Powerup.Colormap".equals(property)) {
            throw new IllegalArgumentException("Only \"Powerup.Colormap\" is supported");
        }
        // TODO
    }


    @Override
    public final Object Pickup() {
        if (hasFlag("INVENTORY.AUTOACTIVATE") && hasFlag("INVENTORY.ALWAYSPICKUP")) {
            getActivator().pickItem(new Powerup());
        } else {
            throw new UnsupportedOperationException();
        }
        return null;
    }

    public class Powerup extends CustomInventory {
        @Override
        public final Object Pickup() {
            return null;
        }
    }
}
