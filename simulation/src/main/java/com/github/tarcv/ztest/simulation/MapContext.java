    package com.github.tarcv.ztest.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapContext<T extends ScriptContext> {
    final List<String> executedScripts = Collections.synchronizedList(new ArrayList<>());
    final ScriptContext.ScriptContextCreator<T> ctor;
    final Simulation<T> simulation;
    final List<ScriptContext.Script<T>> scripts; // TODO: make read-only

    MapContext(Simulation<T> simulation, ScriptContext.ScriptContextCreator<T> supplier, List<ScriptContext.Script<T>> scripts) {
        super();
        this.ctor = supplier;
        this.simulation = simulation;
        this.scripts = Collections.unmodifiableList(new ArrayList<>(scripts));
    }

}

