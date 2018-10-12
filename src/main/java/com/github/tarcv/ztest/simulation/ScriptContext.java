package com.github.tarcv.ztest.simulation;

public class ScriptContext {
    public ScriptContext(Simulation simulation, Thing activator) {
        this.activator = activator;
    }

    private volatile Thing activator;

    public Thing getActivator() {
        return activator;
    }

    public void setActivator(Thing activator) {
        this.activator = activator;
    }
}
