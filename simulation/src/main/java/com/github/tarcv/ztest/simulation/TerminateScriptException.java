package com.github.tarcv.ztest.simulation;

class TerminateScriptException extends RuntimeException {
    TerminateScriptException(InterruptedException e) {
        super(e);
    }

    TerminateScriptException() {

    }
}
