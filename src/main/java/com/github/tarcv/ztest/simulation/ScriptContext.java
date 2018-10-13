package com.github.tarcv.ztest.simulation;

class ScriptContext {
    ScriptContext(Simulation simulation, Thing activator) {
        this.activator = activator;
    }

    private volatile Thing activator;

    Thing getActivator() {
        return activator;
    }

    void setActivator(Thing activator) {
        this.activator = activator;
    }
}
