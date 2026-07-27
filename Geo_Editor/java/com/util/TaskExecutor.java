/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.util;

import com.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Executes a batch of independent tasks and waits for its completion.
 * @author Maneco2
 * @version 1.0
 */
public final class TaskExecutor {
    private final ExecutorService _executor;
    private final int _parallelism;
    private volatile boolean _shutdown;

    public TaskExecutor(final int threads) {
        final int workerCount = Math.max(1, threads);
        _parallelism = workerCount;
        _executor = Executors.newFixedThreadPool(workerCount, new DaemonThreadFactory());
    }

    /** Returns the configured worker count for diagnostics and profiling. */
    public final int getParallelism() {
        return _parallelism;
    }

    public final void shutdown() {
        if (_shutdown) {
            return;
        }
        _shutdown = true;
        _executor.shutdownNow();
        try {
            _executor.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Executes the first {@code size} entries and waits until all are complete. */
    public final void execute(final Runnable[] tasks, final int size) {
        if (_shutdown || size <= 0) {
            return;
        }

        final int taskCount = Math.min(size, tasks.length);
        if (!Config.USE_MULTITHREADING) {
            for (int i = taskCount; i-- > 0;) {
                tasks[i].run();
            }
            return;
        }

        final List<CallableTask> batch = new ArrayList<>(taskCount);
        for (int i = taskCount; i-- > 0;) {
            batch.add(new CallableTask(tasks[i]));
        }

        try {
            _executor.invokeAll(batch);
            for (final CallableTask task : batch) {
                task.rethrowFailure();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class CallableTask implements java.util.concurrent.Callable<Void> {
        private final Runnable _task;
        private Throwable _failure;

        private CallableTask(final Runnable task) {
            _task = task;
        }

        @Override
        public Void call() {
            try {
                _task.run();
            } catch (final Throwable t) {
                _failure = t;
            }
            return null;
        }

        private void rethrowFailure() {
            if (_failure == null) {
                return;
            }
            if (_failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (_failure instanceof Error error) {
                throw error;
            }
            throw new RuntimeException("Task execution failed", _failure);
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private int _sequence;

        @Override
        public Thread newThread(final Runnable task) {
            final Thread thread = new Thread(task, "GeoEditor-Task-" + (++_sequence));
            thread.setDaemon(true);
            return thread;
        }
    }
}
