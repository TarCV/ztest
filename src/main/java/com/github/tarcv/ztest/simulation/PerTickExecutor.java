package com.github.tarcv.ztest.simulation;

import net.jcip.annotations.GuardedBy;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static java.lang.Thread.State.RUNNABLE;
import static java.lang.Thread.State.TERMINATED;

class PerTickExecutor {
    public static final int JOIN_TIMEOUT_MILLIS = 1000 / 35 * 5;
    private final Random randomSource;

    private final Thread executorThread = Thread.currentThread();

    private final List<ThreadContextImpl> delayedTickThreads = Collections.synchronizedList(new ArrayList<>());

    private final List<NamedRunnable> scheduledRunnables = Collections.synchronizedList(new ArrayList<>());

    @GuardedBy("tickDataLock")
    private int tick = -1;

    private final ReentrantLock tickDataLock = new ReentrantLock();

    PerTickExecutor(Random randomSource) {
        this.randomSource = randomSource;
    }

    ThreadContext getThreadContext() {
        return ((TickThread)Thread.currentThread()).threadContext.get();
    }

    void scheduleRunnable(NamedRunnable runnable) {
        scheduledRunnables.add(runnable);
    }

    void executeTick() throws TimeoutException, InterruptedException {
        assert getCurrentTick() >= 0;

        executeRunnablesInternal(scheduledRunnables);
    }

    void executeTickWithRunnables(List<NamedRunnable> namedRunnable) throws TimeoutException, InterruptedException {
        assert getCurrentTick() == -1 && delayedTickThreads.isEmpty();

        ArrayList<NamedRunnable> copy = new ArrayList<>(namedRunnable);
        executeRunnablesInternal(copy);
    }

    private void executeRunnablesInternal(List<NamedRunnable> newRunnables) throws InterruptedException, TimeoutException {
        assert Thread.currentThread() == executorThread;

        withTickLock(() -> {
            ++tick;
        });

        synchronized (newRunnables) {
            while (!newRunnables.isEmpty()) {
                NamedRunnable runnable = newRunnables.remove(randomSource.nextInt(newRunnables.size()));
                ThreadContextImpl thread = new ThreadContextImpl(runnable);
                thread.start(); // actually it is delayed right after starting
                delayedTickThreads.add(thread); // to support getThreadContext call
            }
        }

        List<ThreadContextImpl> threadsLeft = new ArrayList<>();

        synchronized (delayedTickThreads) {
            waitTillNothingExecutes();

            for (ThreadContextImpl thread : delayedTickThreads) {
                assert thread.isDelayed();
                thread.tryContinue();

                boolean finished = thread.tryJoin();
                if (!finished) {
                    threadsLeft.add(thread);
                }
            }

            assert delayedTickThreads.stream().noneMatch(t -> t.thread.getState() == RUNNABLE);
            delayedTickThreads.clear();
            delayedTickThreads.addAll(threadsLeft);
        }
    }

    private void waitTillNothingExecutes() {
        long startTime = System.nanoTime();
        boolean nothingExecutes = false;
        while (System.nanoTime() - startTime < JOIN_TIMEOUT_MILLIS*1_000_000 && !nothingExecutes) {
            nothingExecutes = delayedTickThreads.stream().noneMatch(t -> t.thread.getState() == RUNNABLE);
        }
        if (!nothingExecutes) {
            throw new IllegalStateException("By this time no scripts or function should be in progress");
        }
    }

    void withTickLock(Runnable runnable) {
        tickDataLock.lock();
        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tickDataLock.unlock();
        }
    }

    <T> T withTickLock(Callable<T> runnable) {
        tickDataLock.lock();
        try {
            return runnable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tickDataLock.unlock();
        }
    }

    void assertTickLockHeld() {
        assert tickDataLock.isHeldByCurrentThread();
    }



    int getCurrentTick() {
        return withTickLock(() -> tick);
    }

    ArrayList<String> getActiveRunnables() {
        assert Thread.currentThread() == executorThread;

        return withTickLock(() -> {
            ArrayList<String> runnables = new ArrayList<>();
            scheduledRunnables.forEach(runnable -> runnables.add(runnable.name()));
            delayedTickThreads.forEach(thread -> runnables.add(thread.runnableName));
            return runnables;
        });
    }

    private class TickThread extends Thread {
        private final AtomicReference<ThreadContextImpl> threadContext = new AtomicReference<>();

        TickThread(Runnable runnable, String name) {
            super(runnable, name);
        }
    }

    private class ThreadContextImpl implements ThreadContext {
        private final TickThread thread;
        private final ReadWriteLock delayLock = new ReentrantReadWriteLock();

        @GuardedBy("delayLock")
        private final AtomicReference<DelayContext> delayContext = new AtomicReference<>();

        private final String runnableName;

        private ThreadContextImpl(NamedRunnable runnable) {
            assert Thread.currentThread() == executorThread;
            runnableName = runnable.name();
            thread = new TickThread(() -> withTickLock(() -> {
                this.delayUntil(() -> true); // required by executeTick
                runnable.run();
            }), "TickThread - " + runnable.name());
            thread.threadContext.set(this);
        }

        // executed by TickThread.thread
        @Override
        public void delayUntil(Supplier<Boolean> untilPredicate) {
            assert Thread.currentThread() == thread;

            delayLock.writeLock().lock();
            try {
                assert tickDataLock.isHeldByCurrentThread();

                DelayContext newDelay = new DelayContext(untilPredicate);
                delayContext.set(newDelay);

                tickDataLock.unlock();
            } finally {
                delayLock.writeLock().unlock();
            }

            try {
                assert !tickDataLock.isHeldByCurrentThread();
                delayContext.get().await();
            } finally {
                tickDataLock.lock();
                assert tickDataLock.isHeldByCurrentThread();
            }
        }

        // executed by PerTickExecutor
        void tryContinue() {
            assert Thread.currentThread() == executorThread;

            delayLock.readLock().lock();
            try {
                delayContext.get().tryContinue();
            } finally {
                delayLock.readLock().unlock();
            }
        }

        private boolean tryJoin() throws InterruptedException, TimeoutException {
            assert Thread.currentThread() == executorThread;

            thread.join(JOIN_TIMEOUT_MILLIS);
            if (thread.getState() != TERMINATED) {
                if (isDelayed()) {
                    return false;
                } else {
                    throw new TimeoutException("Some thread is run-away. Probably you forgot to add 'delay'?");
                }
            } else {
                return true;
            }
        }

        private boolean isDelayed() {
            assert Thread.currentThread() == executorThread;

            delayLock.readLock().lock();
            try {
                return thread.getState() != RUNNABLE;
            } finally {
                delayLock.readLock().unlock();
            }
        }

        void start() {
            assert Thread.currentThread() == executorThread;

            thread.start();
        }
    }

    class DelayContext {
        private final Supplier<Boolean> untilPredicate;
        private final CountDownLatch latch = new CountDownLatch(1);

        DelayContext(Supplier<Boolean> untilPredicate) {
            assert Thread.currentThread() != executorThread;

            Objects.requireNonNull(untilPredicate);
            this.untilPredicate = untilPredicate;
        }

        void tryContinue() {
            assert Thread.currentThread() == executorThread;

            if (latch.getCount() > 0) {
                withTickLock(() -> {
                    try {
                        if (untilPredicate.get()) {
                            latch.countDown();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }

        private boolean isWaiting() {
            assert Thread.currentThread() == executorThread;

            return latch.getCount() > 0;
        }

        void await() {
            assert Thread.currentThread() != executorThread;

            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new TerminateScriptException(e);
            }
        }
    }
}

interface NamedRunnable extends Runnable {
    String name();
}

interface ThreadContext {
    // executed by TickThread.thread
    void delayUntil(Supplier<Boolean> untilPredicate);
}