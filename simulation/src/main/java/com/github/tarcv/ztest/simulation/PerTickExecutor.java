package com.github.tarcv.ztest.simulation;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FiberExecutorScheduler;
import co.paralleluniverse.fibers.FiberScheduler;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand.State;
import co.paralleluniverse.strands.SuspendableRunnable;
import co.paralleluniverse.strands.concurrent.CountDownLatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

class PerTickExecutor {
    private static final int JOIN_TIMEOUT_MILLIS = 1000 / 35 * 10;
    private final Random randomSource;

    private final Thread executorThread = Thread.currentThread();
    private final FiberScheduler scheduler =
            new FiberExecutorScheduler("Tick scheduler", Executors.newSingleThreadExecutor());
    private final AtomicReference<Thread> fiberThread = new AtomicReference<>();

    private final List<ThreadContextImpl> delayedTickThreads = Collections.synchronizedList(new ArrayList<>());
    private final List<ScriptContext.NamedRunnable> scheduledRunnables = Collections.synchronizedList(new ArrayList<>());

    private final ScriptThreadEnforcer<PerTickExecutorData> data = new ScriptThreadEnforcer<PerTickExecutorData>(this, new PerTickExecutorData());

    PerTickExecutor(Random randomSource) {
        this.randomSource = randomSource;
    }

    ThreadContext getThreadContext() {
        return ((TickThread)Fiber.currentFiber()).threadContext.get();
    }

    void assertIsFiberThread() {
        Thread actualFiberThread = fiberThread.updateAndGet(thread -> {
            if (thread == null) {
                return Thread.currentThread();
            }
            return thread;
        });
        assert Thread.currentThread() == actualFiberThread;
    }

    void scheduleRunnable(ScriptContext.NamedRunnable runnable) {
        scheduledRunnables.add(runnable);
    }

    void executeTick() throws TimeoutException {
        assert getCurrentTick() >= 0;

        executeRunnablesInternal(scheduledRunnables);
    }

    void executeTickWithRunnables(List<ScriptContext.NamedRunnable> namedRunnable) throws TimeoutException {
        assert getCurrentTick() == -1 && delayedTickThreads.isEmpty();

        ArrayList<ScriptContext.NamedRunnable> copy = new ArrayList<>(namedRunnable);
        executeRunnablesInternal(copy);
    }

    void executeWithinScriptThread(Runnable runnable) {
        executeWithinScriptThread(() -> {
            runnable.run();
            return null;
        });
    }

    private void executeRunnablesInternal(List<ScriptContext.NamedRunnable> newRunnables) throws TimeoutException {
        assertIsExecutorThread();

        executeWithinScriptThread(() -> ++data.get().tick);

        synchronized (newRunnables) {
            while (!newRunnables.isEmpty()) {
                ScriptContext.NamedRunnable runnable = newRunnables.remove(randomSource.nextInt(newRunnables.size()));
                ThreadContextImpl thread = new ThreadContextImpl(runnable);
                thread.start(); // actually it is delayed right after starting
                delayedTickThreads.add(thread); // to support getThreadContext call
            }
        }


        synchronized (delayedTickThreads) {
            waitTillNothingExecutes();
            List<ThreadContextImpl> threadsLeft = new ArrayList<>();

            for (ThreadContextImpl thread : delayedTickThreads) {
                assert thread.isDelayed();
                boolean finished = false;
                if (thread.tryContinue()) {
                    finished = thread.tryJoin();
                }
                if (!finished) {
                    threadsLeft.add(thread);
                }
            }

            assert delayedTickThreads.stream().noneMatch(t ->
                    isRunning(t.thread));
            delayedTickThreads.clear();
            delayedTickThreads.addAll(threadsLeft);
        }

    }

    private void assertIsExecutorThread() {
        assert Thread.currentThread() == executorThread;
    }

    private static boolean isRunning(TickThread tickThread) {
        return tickThread.getState() == State.RUNNING
        || tickThread.getState() == State.NEW
        || tickThread.getState() == State.STARTED;
    }

    private void waitTillNothingExecutes() {
        long startTime = System.nanoTime();
        boolean nothingExecutes = false;
        while (System.nanoTime() - startTime < JOIN_TIMEOUT_MILLIS*1_000_000 && !nothingExecutes) {
            nothingExecutes = delayedTickThreads.stream().noneMatch(t -> isRunning(t.thread));
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!nothingExecutes) {
            throw new IllegalStateException("By this time no scripts or function should be in progress");
        }
    }

    <T> T executeWithinScriptThread(Supplier<T> callable) {
        if (Thread.currentThread() == fiberThread.get()) {
            return callable.get();
        } else {
            waitTillNothingExecutes();
            Fiber<T> thread = new Fiber<>("Between ticks execution", scheduler, callable::get);
            thread.start();
            try {
                return thread.get();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TerminateScriptException(e);
            }
        }
    }

    int getCurrentTick() {
        return executeWithinScriptThread(() -> data.get().tick);
    }

    ArrayList<String> getActiveRunnables() {
        assertIsExecutorThread();

        ArrayList<String> runnables = new ArrayList<>();
        scheduledRunnables.forEach(runnable -> runnables.add(runnable.name()));
        delayedTickThreads.forEach(thread -> runnables.add(thread.runnableName));
        return runnables;
    }

    void printlnMarked(String s) {
        printfMarked("%s%n", s);
    }

    void printfMarked(String format, Object... args) {
        System.out.printf(System.identityHashCode(this) + ": " + format, args);
    }

    private class TickThread extends Fiber<Void> {
        private final AtomicReference<ThreadContextImpl> threadContext = new AtomicReference<>();

        TickThread(SuspendableRunnable runnable, String name) {
            super(name, scheduler, runnable);

            assertIsExecutorThread();
        }
    }

    private class ThreadContextImpl implements ThreadContext {
        private final TickThread thread;
        private final AtomicReference<UntilContext> untilContext = new AtomicReference<>();

        private final String runnableName;
        private final SuspendableRunnable suspendableRunnable;

        private ThreadContextImpl(ScriptContext.NamedRunnable runnable) {
            assertIsExecutorThread();

            runnableName = runnable.name();
            suspendableRunnable = () -> {
                this.delayUntil(() -> true); // required by executeTick
                printlnMarked("Actually starting " + runnableName);
                runnable.run();
                printlnMarked("Successfully finished " + runnableName);
            };
            thread = new TickThread(suspendableRunnable, "TickThread - " + runnable.name());
            thread.threadContext.set(this);
        }

        // executed by TickThread.thread
        @Override
        public void delayUntil(BooleanSupplier untilPredicate) throws SuspendExecution {
            assertIsFiberThread();

            if (untilPredicate.getAsBoolean()) return;

            UntilContext oldContext = untilContext.getAndUpdate(old -> new UntilContext(untilPredicate));
            assert oldContext == null || oldContext.latch.getCount() == 0;
            UntilContext untilContext = this.untilContext.get();

            try {
                do {
                    untilContext.latch.await();
                } while (untilContext.latch.getCount() > 0);
                assert untilContext.latch.getCount() == 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TerminateScriptException(e);
            }
            printlnMarked("Resuming after delay - " + thread.threadContext.get().runnableName);
        }

        boolean tryContinue() {
            assertIsExecutorThread();

            boolean canBeResumed = executeWithinScriptThread(() -> {
                UntilContext untilContext = this.untilContext.get();
                return untilContext == null || untilContext.condition.getAsBoolean();
            });
            if (canBeResumed) {
                UntilContext untilContext = this.untilContext.get();
                if (untilContext != null) {
                    untilContext.latch.countDown();
                }
                //printlnMarked(runnableName + "- resumed");
            }
            return canBeResumed;
        }

        private boolean tryJoin() throws TimeoutException {
            assertIsExecutorThread();

            try {
                thread.join(JOIN_TIMEOUT_MILLIS, MILLISECONDS);
                return true;
            } catch (TimeoutException e) {
                if (isDelayed()) {
                    return false;
                } else {
                    thread.cancel(true);
                    throw new TimeoutException(
                            String.format("Thread '%s' is run-away. Probably you forgot to add 'delay'?",
                                    thread.threadContext.get().runnableName));
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw new RuntimeException(cause != null ? cause : e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TerminateScriptException(e);
            }
        }

        private boolean isDelayed() {
            assertIsExecutorThread();

            return !isRunning(thread);
        }

        void start() {
            assertIsExecutorThread();

            thread.start();
        }
    }

    static class UntilContext {
        final CountDownLatch latch = new CountDownLatch(1);
        final BooleanSupplier condition;

        private UntilContext(BooleanSupplier condition) {
            this.condition = condition;
        }
    }

    private static class PerTickExecutorData {
        private int tick = -1;
    }
}

interface ThreadContext {
    // executed by TickThread.thread
    void delayUntil(BooleanSupplier untilPredicate) throws SuspendExecution;
}