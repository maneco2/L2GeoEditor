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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Reclaims discarded region graphs before a large replacement window is loaded. @author Maneco2 @version 1.0 */
public final class MemoryReclaimer {
	static final long COLLECTION_THRESHOLD_BYTES = 2L * 1024L * 1024L * 1024L;
	private static final long MINIMUM_INTERVAL_NANOS = 10_000_000_000L;
	private static final AtomicLong LAST_COLLECTION = new AtomicLong(Long.MIN_VALUE);
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "GeoEditor-memory-reclaimer");
		thread.setDaemon(true);
		return thread;
	});

	private MemoryReclaimer() {
	}

	public static boolean isCollectionRecommended() {
		return shouldCollect(MemoryUsage.capture().usedBytes(), System.nanoTime(), LAST_COLLECTION.get());
	}

	public static CompletableFuture<Void> reclaimIfNeededAsync() {
		return reclaimAsync(false);
	}

	public static CompletableFuture<Void> reclaimAfterWindowChangeAsync(final int previousNeighbors,
			final int nextNeighbors) {
		return reclaimAsync(previousNeighbors >= 48 && nextNeighbors < previousNeighbors);
	}

	private static CompletableFuture<Void> reclaimAsync(final boolean force) {
		final long now = System.nanoTime();
		final long previousCollection = LAST_COLLECTION.get();
		if (!shouldCollect(force, MemoryUsage.capture().usedBytes(), now, previousCollection))
			return CompletableFuture.completedFuture(null);
		if (!LAST_COLLECTION.compareAndSet(previousCollection, now))
			return CompletableFuture.completedFuture(null);
		return CompletableFuture.runAsync(System::gc, EXECUTOR);
	}

	static boolean shouldCollect(final long usedBytes, final long now, final long lastCollection) {
		return shouldCollect(false, usedBytes, now, lastCollection);
	}

	static boolean shouldCollect(final boolean force, final long usedBytes, final long now,
			final long lastCollection) {
		return (force || usedBytes >= COLLECTION_THRESHOLD_BYTES)
				&& (lastCollection == Long.MIN_VALUE || now - lastCollection >= MINIMUM_INTERVAL_NANOS);
	}
}
