package com.github.tarcv.ztest.simulation;

import java.util.*;

public class DamageType {
    private final Set<String> flags = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Object> properties = Collections.synchronizedMap(new HashMap<>());

    protected final void setFlag(String flag) {
        flags.add(flag.toUpperCase());
    }

    boolean hasFlag(String flag) {
        return flags.contains(flag);
    }
}
