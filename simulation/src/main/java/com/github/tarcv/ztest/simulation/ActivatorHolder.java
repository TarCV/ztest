package com.github.tarcv.ztest.simulation;

import org.jetbrains.annotations.Nullable;

class ActivatorHolder {
    ActivatorHolder(@Nullable Thing activator) {
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
