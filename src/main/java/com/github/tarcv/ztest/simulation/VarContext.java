package com.github.tarcv.ztest.simulation;

import java.util.concurrent.atomic.AtomicReference;

public class VarContext<M extends MapContext> {
    private AtomicReference<M> scripts = new AtomicReference<>();

    protected void setScripts(M mapContext) {
        assert getScripts() == null;
        this.scripts.set(mapContext);
    }

    public M getScripts() {
        return scripts.get();
    }
}
