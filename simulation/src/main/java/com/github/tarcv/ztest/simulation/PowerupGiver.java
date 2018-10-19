package com.github.tarcv.ztest.simulation;

public class PowerupGiver extends CustomInventory {
    protected PowerupGiver(Simulation simulation) {
        super(simulation);
    }

    protected void setProperty(String property, int r, int g, int n, double r1, double g1, double b1) {
        if (!"Powerup.Colormap".equals(property)) {
            throw new IllegalArgumentException("Only \"Powerup.Colormap\" property can be written using this syntax");
        }
        // TODO
    }

    @Override
    protected void verifyPropertyNameAndValue(String name, Object value) {
        switch (name) {
            case "Powerup.Duration":
                assert value instanceof Integer;
                break;
            case "Powerup.Type":
                if (!value.equals("GhostTint")) {
                    throw new UnsupportedOperationException("Only \"GhostTint\" is supported");
                }
                break;
            default:
                super.verifyPropertyNameAndValue(name, value);
        }
    }

    @Override
    public final Object Pickup() {
        if (hasFlag("INVENTORY.AUTOACTIVATE") && hasFlag("INVENTORY.ALWAYSPICKUP")) {
            getActivator().pickItem(new Powerup(simulation));
        } else {
            throw new UnsupportedOperationException();
        }
        return null;
    }

    public class Powerup extends CustomInventory {
        Powerup(Simulation simulation) {
            super(simulation);
        }

        @Override
        public final Object Pickup() {
            return null;
        }
    }
}
