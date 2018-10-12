package com.github.tarcv.ztest.simulation;

import java.util.*;

public class DamageType {
    private Set<String> flags = Collections.synchronizedSet(new HashSet<>());
    private Map<String, Object> properties = Collections.synchronizedMap(new HashMap<String, Object>());

    protected final void setFlag(String flag) {
        flags.add(flag.toUpperCase());
    }
}
