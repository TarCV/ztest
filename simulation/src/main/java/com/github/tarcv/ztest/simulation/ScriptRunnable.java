package com.github.tarcv.ztest.simulation;

import co.paralleluniverse.fibers.SuspendExecution;

@FunctionalInterface
public interface ScriptRunnable<T extends ScriptContext> {
    void callScript(T thisArg, Object[] args) throws SuspendExecution;
}
