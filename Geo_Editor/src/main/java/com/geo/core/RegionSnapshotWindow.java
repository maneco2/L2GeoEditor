/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.geo.RegionLoadManager;
import com.geo.RegionWindowPlan;

/** Shared bounded ownership/cache for immutable read-only region snapshots. */
public final class RegionSnapshotWindow implements AutoCloseable {
	public record Snapshot(Map<RegionLoadManager.RegionKey, RegionSnapshot> regions,
			long loadMicros, int reusedNeighbors, int loadedNeighbors,
			int discardedNeighbors) {
		public Snapshot {
			regions = Map.copyOf(regions);
		}
	}

	private final RegionLoadManager manager = new RegionLoadManager();
	private final Map<RegionLoadManager.RegionKey, RegionSnapshot> cache =
			new ConcurrentHashMap<>();
	private final AtomicLong generation = new AtomicLong();

	public CompletableFuture<Snapshot> select(final RegionSnapshot center,
			final File geodataDirectory, final boolean loadNeighbors,
			final int neighborCount, final Consumer<RegionLoadManager.Progress> progress) {
		final long selectedGeneration = generation.incrementAndGet();
		final RegionLoadManager.RegionKey centerKey =
				new RegionLoadManager.RegionKey(center.regionX(), center.regionY());
		manager.setProgressListener(progress);
		if (!loadNeighbors) {
			final int discarded = Math.max(0, cache.size() - 1);
			manager.selectSingleCenter(center.regionX(), center.regionY());
			cache.clear();
			cache.put(centerKey, center);
			return CompletableFuture.completedFuture(snapshot(0L, 0, 0, discarded));
		}

		final int radius = RegionWindowPlan.radiusForNeighborCount(neighborCount);
		manager.selectCenter(center.regionX(), center.regionY(), radius);
		final Set<RegionLoadManager.RegionKey> requested = manager.snapshot().keySet();
		final Set<RegionLoadManager.RegionKey> previous = Set.copyOf(cache.keySet());
		final int reused = (int) previous.stream()
				.filter(key -> !key.equals(centerKey) && requested.contains(key)).count();
		final int discarded = (int) previous.stream()
				.filter(key -> !key.equals(centerKey) && !requested.contains(key)).count();
		cache.keySet().retainAll(requested);
		cache.put(centerKey, center);
		manager.markLoaded(cache.keySet());
		final AtomicInteger loaded = new AtomicInteger();
		final long started = System.nanoTime();
		return manager.checkNeighborsAsync(key -> regionFile(geodataDirectory, key).isFile())
				.thenCompose(ignored -> manager.loadAvailableNeighborsAsync(key ->
						loadAndCount(key, geodataDirectory, selectedGeneration, loaded)))
				.thenApply(ignored -> snapshot((System.nanoTime() - started) / 1_000L,
						reused, loaded.get(), discarded));
	}

	private boolean loadAndCount(final RegionLoadManager.RegionKey key,
			final File directory, final long selectedGeneration,
			final AtomicInteger loaded) {
		final boolean result = load(key, directory, selectedGeneration);
		if (result)
			loaded.incrementAndGet();
		return result;
	}

	private boolean load(final RegionLoadManager.RegionKey key,
			final File directory, final long selectedGeneration) {
		if (generation.get() != selectedGeneration)
			return false;
		if (cache.containsKey(key))
			return true;
		try {
			final RegionSnapshot loaded = RegionSnapshotLoader.load(regionFile(directory, key));
			if (generation.get() != selectedGeneration)
				return false;
			cache.put(key, loaded);
			return true;
		} catch (final IOException exception) {
			return false;
		}
	}

	private Snapshot snapshot(final long loadMicros, final int reused,
			final int loaded, final int discarded) {
		return new Snapshot(new LinkedHashMap<>(cache), loadMicros,
				reused, loaded, discarded);
	}

	private static File regionFile(final File directory,
			final RegionLoadManager.RegionKey key) {
		return new File(directory, String.format("%02d_%02d.l2j",
				key.x() + 10, key.y() + 10));
	}

	@Override
	public void close() {
		generation.incrementAndGet();
		manager.close();
		cache.clear();
	}
}
