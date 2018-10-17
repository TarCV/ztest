package com.github.tarcv.ztest.simulation;

public abstract class Actor extends Thing {
    public Actor(Simulation simulation) {
        super(simulation);
    }

    int getHealth() {
        return checkInventory("Health");
    }

    @Override
    protected void setProperty(String name, Object value) {
        if (name.equalsIgnoreCase("health")) {
            addHealth(getHealth() - ((Integer)value), getActivator());
        } else {
            super.setProperty(name, value);
        }
    }

    @Override
    protected Object getProperty(String name) {
        if (name.equalsIgnoreCase("health")) {
            return getHealth();
        } else {
            return super.getProperty(name);
        }
    }

    @Override
    protected void verifyPropertyNameAndValue(String name, Object value) {
        switch (name) {
            case "Health":
            case "Radius":
            case "Height":
            case "Mass":
            case "Speed":
                if (!(value instanceof Number))
                    throw new IllegalArgumentException("The value should be a number");
                return;
            case "Obituary":
                if (!(value instanceof String))
                    throw new IllegalArgumentException("The value should be a string");
                return;
            default:
                super.verifyPropertyNameAndValue(name, value);
        }
    }



    void damageThing(int damage, DamageType damageType, Thing byWhom) {
        if (!damageType.hasFlag("noarmor")) throw new UnsupportedOperationException("Only noarmor damage type is supported");
        addHealth(-damage, byWhom);
    }

    private void addHealth(int amount, Thing byWhom) {
        if (amount > 0) {
            A_GiveInventory("Health", amount);
        } else {
            A_TakeInventory("Health", -amount);
            if (checkInventory("Health") <= 0) {
                // TODO: implement gibbing
                simulation.onDeath(this, byWhom);
            }
        }
    }

    protected void setFlag(String combo) {
        switch (combo.toUpperCase()) {
            case "MONSTER":
                addFlag("SHOOTABLE");
                addFlag("COUNTKILL");
                addFlag("SOLID");
                addFlag("CANPUSHWALLS");
                addFlag("CANUSEWALLS");
                addFlag("ACTIVATEMCROSS");
                addFlag("CANPASS");
                addFlag("ISMONSTER");
                return;
            case "PROJECTILE":
                addFlag("NOBLOCKMAP");
                addFlag("NOGRAVITY");
                addFlag("DROPOFF");
                addFlag("MISSILE");
                addFlag("ACTIVATEIMPACT");
                addFlag("ACTIVATEPCROSS");
                addFlag("NOTELEPORT");
                return;
            default:
                throw new IllegalArgumentException("Unknown flag combo");
        }
    }

    abstract protected Object Spawn();
}
