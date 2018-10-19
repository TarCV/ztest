package com.github.tarcv.ztest.simulation;

public class ScriptThreadEnforcer<T> {
    private final PerTickExecutor executor;
    private final T data;

    ScriptThreadEnforcer(PerTickExecutor executor, T data) {
        this.executor = executor;
        this.data = data;
    }

    T get() {
        executor.assertIsFiberThread();
        return data;
    }
}
