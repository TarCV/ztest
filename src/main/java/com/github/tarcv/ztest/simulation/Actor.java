package com.github.tarcv.ztest.simulation;

public abstract class Actor extends Thing {
    public Actor(Simulation simulation) {
        super(simulation);
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
