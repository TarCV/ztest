package com.github.tarcv.ztest.simulation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DamageType {
    private final Simulation simulation;
    private final Set<String> flags = Collections.synchronizedSet(new HashSet<>());

    public DamageType(Simulation simulation) {
        this.simulation = simulation;
    }

    protected final void setFlag(String flag) {
        flags.add(flag.toUpperCase());
    }

    boolean hasFlag(String flag) {
        return flags.contains(flag.toUpperCase());
    }
}
