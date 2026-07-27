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
package com.geo;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Consumer;

/**
 * Tracks a bounded square region window around an anchored streaming center.
 * @author Maneco2
 * @version 1.0
 */
public final class RegionLoadManager implements AutoCloseable {
	private static final int NEIGHBOR_LOAD_PAUSE_MILLIS = 8;
	private static final int NEIGHBOR_LOAD_BATCH_SIZE = 2;
	private static final int NEIGHBOR_BATCH_PAUSE_MILLIS = 24;
	public enum State { UNLOADED, CHECKING, AVAILABLE, LOADING, LOADED, MISSING, FAILED }

	public record RegionKey(int x, int y) { }
	public record Progress(String phase, int completed, int total) { }

	private final Map<RegionKey, State> _states = new LinkedHashMap<>();
	private final ExecutorService _availabilityExecutor = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "GeoRegionAvailability");
		thread.setDaemon(true);
		return thread;
	});
	private int _centerX;
	private int _centerY;
	private int _radius = 1;
	private long _generation;
	private boolean _closed;
	private Consumer<Progress> _progressListener = progress -> { };

	public synchronized void setProgressListener(final Consumer<Progress> listener) {
		_progressListener = listener == null ? progress -> { } : listener;
	}

	public synchronized void selectCenter(final int regionX, final int regionY) {
		selectCenter(regionX, regionY, 1);
	}

	public synchronized void selectCenter(final int regionX, final int regionY, final int radius) {
		if (_closed)
			return;
		if (radius < 0 || radius > 5)
			throw new IllegalArgumentException("Region window radius must be between 0 and 5.");
		_centerX = regionX;
		_centerY = regionY;
		_radius = radius;
		_generation++;
		_states.clear();
		for (int x = regionX - radius; x <= regionX + radius; x++) {
			for (int y = regionY - radius; y <= regionY + radius; y++)
				_states.put(new RegionKey(x, y), x == regionX && y == regionY ? State.LOADED : State.UNLOADED);
		}
	}

	public synchronized void selectSingleCenter(final int regionX, final int regionY) {
		if (_closed)
			return;
		_centerX = regionX;
		_centerY = regionY;
		_radius = 0;
		_generation++;
		_states.clear();
		_states.put(new RegionKey(regionX, regionY), State.LOADED);
	}

	public synchronized RegionKey center() {
		return new RegionKey(_centerX, _centerY);
	}

	public synchronized int radius() {
		return _radius;
	}

	public synchronized Map<RegionKey, State> snapshot() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(_states));
	}

	/** Reads one region state without allocating a snapshot of the whole window. */
	public synchronized State getState(final RegionKey key) {
		return key == null ? null : _states.get(key);
	}

	public static int getNeighborLoadBatchSize() {
		return NEIGHBOR_LOAD_BATCH_SIZE;
	}

	public static int getNeighborBatchPauseMillis() {
		return NEIGHBOR_BATCH_PAUSE_MILLIS;
	}

	static int loadPauseMillisFor(final int regionCount) {
		if (regionCount > 48)
			return 30;
		if (regionCount > 24)
			return 16;
		return NEIGHBOR_LOAD_PAUSE_MILLIS;
	}

	static int loadBatchSizeFor(final int regionCount) {
		return regionCount > 48 ? 1 : NEIGHBOR_LOAD_BATCH_SIZE;
	}

	static int batchPauseMillisFor(final int regionCount) {
		return loadBatchSizeFor(regionCount) == 1 ? 0 : NEIGHBOR_BATCH_PAUSE_MILLIS;
	}

	public synchronized void mark(final RegionKey key, final State state) {
		if (_states.containsKey(key))
			_states.put(key, state);
	}

	/**
	 * Checks neighboring files away from the Swing EDT. Results from an obsolete
	 * center selection are discarded.
	 */
	public CompletableFuture<Void> checkNeighborsAsync(final Predicate<RegionKey> availabilityCheck) {
		final RegionKey center;
		final long generation;
		final RegionKey[] neighbors;
		synchronized (this) {
			if (_closed)
				return CompletableFuture.completedFuture(null);
			center = new RegionKey(_centerX, _centerY);
			generation = _generation;
			neighbors = _states.keySet().stream().filter(key -> !key.equals(center))
					.sorted(Comparator.comparingInt(key -> distanceSquared(center, key)))
					.toArray(RegionKey[]::new);
			for (final RegionKey key : neighbors)
				_states.put(key, State.CHECKING);
		}

		return CompletableFuture.runAsync(() -> {
			for (final RegionKey key : neighbors) {
				try {
					updateIfCurrent(key, availabilityCheck.test(key) ? State.AVAILABLE : State.MISSING, generation);
				} catch (final RuntimeException exception) {
					updateIfCurrent(key, State.FAILED, generation);
				}
				publishProgress("Checking geodata", generation);
			}
		}, _availabilityExecutor);
	}

	/** Loads available neighbors sequentially on the same bounded worker. */
	public CompletableFuture<Void> loadAvailableNeighborsAsync(final Function<RegionKey, Boolean> loader) {
		final long generation;
		final RegionKey[] available;
		synchronized (this) {
			if (_closed)
				return CompletableFuture.completedFuture(null);
			generation = _generation;
			final RegionKey center = new RegionKey(_centerX, _centerY);
			available = _states.entrySet().stream()
					.filter(entry -> entry.getValue() == State.AVAILABLE)
					.map(Map.Entry::getKey)
					.sorted(Comparator.comparingInt(key -> distanceSquared(center, key)))
					.toArray(RegionKey[]::new);
		}

		return CompletableFuture.runAsync(() -> {
			int batchCount = 0;
			final int loadPauseMillis = loadPauseMillisFor(available.length);
			final int loadBatchSize = loadBatchSizeFor(available.length);
			final int batchPauseMillis = batchPauseMillisFor(available.length);
			for (int index = 0; index < available.length; index++) {
				final RegionKey key = available[index];
				if (!transitionIfCurrent(key, State.AVAILABLE, State.LOADING, generation))
					continue;
				try {
					updateIfCurrent(key, Boolean.TRUE.equals(loader.apply(key)) ? State.LOADED : State.FAILED,
							generation);
					if (loadPauseMillis > 0)
						Thread.sleep(loadPauseMillis);
					if (++batchCount == loadBatchSize && index + 1 < available.length) {
						if (batchPauseMillis > 0)
							Thread.sleep(batchPauseMillis);
						batchCount = 0;
					}
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					return;
				} catch (final RuntimeException exception) {
					updateIfCurrent(key, State.FAILED, generation);
				}
				publishProgress("Preparing geodata", generation);
			}
			publishProgress("Finalizing region window", generation);
		}, _availabilityExecutor);
	}

	/** Prepares a region just beyond the current window on the bounded streaming worker. */
	public CompletableFuture<Boolean> prepareTransitionRegionAsync(final RegionKey key,
			final Function<RegionKey, Boolean> loader) {
		synchronized (this) {
			if (_closed)
				return CompletableFuture.completedFuture(Boolean.FALSE);
		}
		return CompletableFuture.supplyAsync(() -> Boolean.valueOf(loader.apply(key)), _availabilityExecutor);
	}

	private void publishProgress(final String phase, final long generation) {
		final Consumer<Progress> listener;
		final Progress progress;
		synchronized (this) {
			if (_closed || _generation != generation)
				return;
			final RegionKey center = new RegionKey(_centerX, _centerY);
			final int total = (int) _states.keySet().stream().filter(key -> !key.equals(center)).count();
			final int completed = (int) _states.entrySet().stream()
					.filter(entry -> !entry.getKey().equals(center) && isTerminal(entry.getValue())).count();
			listener = _progressListener;
			progress = new Progress(phase, completed, total);
		}
		listener.accept(progress);
	}

	private static boolean isTerminal(final State state) {
		return state == State.LOADED || state == State.MISSING || state == State.FAILED;
	}

	private static int distanceSquared(final RegionKey center, final RegionKey key) {
		final int dx = key.x() - center.x();
		final int dy = key.y() - center.y();
		return dx * dx + dy * dy;
	}

	private synchronized boolean transitionIfCurrent(final RegionKey key, final State expected, final State state,
			final long generation) {
		if (_closed || _generation != generation || _states.get(key) != expected)
			return false;
		_states.put(key, state);
		return true;
	}

	private synchronized void updateIfCurrent(final RegionKey key, final State state, final long generation) {
		if (!_closed && _generation == generation && _states.containsKey(key))
			_states.put(key, state);
	}

	@Override
	public synchronized void close() {
		if (_closed)
			return;
		_closed = true;
		_generation++;
		_states.clear();
		_availabilityExecutor.shutdownNow();
	}
}
