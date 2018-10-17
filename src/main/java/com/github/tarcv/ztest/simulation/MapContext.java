    package com.github.tarcv.ztest.simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MapContext {
    final List<String> executedScripts = Collections.synchronizedList(new ArrayList<>());
    final ScriptContext.ScriptContextCreator ctor;
    final Simulation simulation;
    final List<ScriptContext.Script> scripts; // TODO: make read-only

    MapContext(Simulation simulation, ScriptContext.ScriptContextCreator supplier, ScriptContext.Script[] scripts) {
        super();
        this.ctor = supplier;
        this.simulation = simulation;
        this.scripts = Collections.unmodifiableList(Arrays.asList(scripts));
    }

}

