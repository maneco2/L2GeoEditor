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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

public final class RegionLoadManagerTest {
	public static void main(final String[] args) throws Exception {
		try (RegionLoadManager manager = new RegionLoadManager()) {
			check(RegionLoadManager.getNeighborLoadBatchSize() > 0, "stream batch size");
			check(RegionLoadManager.getNeighborBatchPauseMillis() >= 0, "stream batch pause");
			check(RegionLoadManager.loadPauseMillisFor(8) == 8, "small window throttle");
			check(RegionLoadManager.loadPauseMillisFor(48) == 16, "medium window throttle");
			check(RegionLoadManager.loadPauseMillisFor(80) == 30, "large window throttle");
			check(RegionLoadManager.loadBatchSizeFor(80) == 1, "large windows must yield after every region");
			check(RegionLoadManager.batchPauseMillisFor(80) == 0, "large window must not stack two pauses");
			check(RegionLoadManager.batchPauseMillisFor(24) > 0, "small batches retain cooperative pause");
			manager.selectCenter(12, 12);
			manager.mark(new RegionLoadManager.RegionKey(12, 12), RegionLoadManager.State.LOADED);
			check(manager.snapshot().size() == 9, "expected 3x3 window");
			check(manager.snapshot().containsKey(new RegionLoadManager.RegionKey(11, 11)),
					"missing diagonal neighbor");
			check(manager.getState(new RegionLoadManager.RegionKey(12, 12)) == RegionLoadManager.State.LOADED,
					"direct state lookup failed");
			check(manager.getState(new RegionLoadManager.RegionKey(99, 99)) == null,
					"unknown state must be null");
			check(manager.getState(null) == null, "null state key must be safe");

			manager.checkNeighborsAsync(key -> key.x() == 11).get(2L, TimeUnit.SECONDS);
			final Map<RegionLoadManager.RegionKey, RegionLoadManager.State> checked = manager.snapshot();
			check(checked.get(new RegionLoadManager.RegionKey(11, 12)) == RegionLoadManager.State.AVAILABLE,
					"existing neighbor not available");
			check(checked.get(new RegionLoadManager.RegionKey(13, 12)) == RegionLoadManager.State.MISSING,
					"missing neighbor not distinguished");
			check(checked.get(new RegionLoadManager.RegionKey(12, 12)) == RegionLoadManager.State.LOADED,
					"center state changed");
			manager.loadAvailableNeighborsAsync(key -> true).get(2L, TimeUnit.SECONDS);
			check(manager.snapshot().get(new RegionLoadManager.RegionKey(11, 12)) == RegionLoadManager.State.LOADED,
					"available neighbor not loaded");
			check(manager.snapshot().get(new RegionLoadManager.RegionKey(13, 12)) == RegionLoadManager.State.MISSING,
					"missing neighbor was loaded");

			manager.selectCenter(13, 12);
			check(!manager.snapshot().containsKey(new RegionLoadManager.RegionKey(11, 11)),
					"stale region retained");
			check(manager.snapshot().containsKey(new RegionLoadManager.RegionKey(14, 13)),
					"new neighbor missing");

			manager.checkNeighborsAsync(key -> {
				if (key.x() == 12 && key.y() == 11)
					throw new IllegalStateException("probe failure");
				return true;
			}).get(2L, TimeUnit.SECONDS);
			check(manager.snapshot().get(new RegionLoadManager.RegionKey(12, 11)) == RegionLoadManager.State.FAILED,
					"probe failure not recorded");
			manager.selectSingleCenter(20, 21);
			check(manager.snapshot().size() == 1, "single-region mode retained neighbors");
			check(manager.snapshot().get(new RegionLoadManager.RegionKey(20, 21)) == RegionLoadManager.State.LOADED,
					"single-region center not loaded");
		}
		try (RegionLoadManager manager = new RegionLoadManager()) {
			manager.selectCenter(19, 13, 2);
			check(manager.snapshot().size() == 25, "expected 5x5 window");
			check(manager.radius() == 2, "window radius");
			final AtomicReference<RegionLoadManager.Progress> progress = new AtomicReference<>();
			manager.setProgressListener(progress::set);
			manager.checkNeighborsAsync(key -> false).get(2L, TimeUnit.SECONDS);
			check(progress.get() != null && progress.get().completed() == 24 && progress.get().total() == 24,
					"missing regions must complete progress");
			manager.selectCenter(21, 13, 2);
			check(manager.center().equals(new RegionLoadManager.RegionKey(21, 13)), "new anchored center");
			check(!manager.snapshot().containsKey(new RegionLoadManager.RegionKey(17, 11)),
					"complete reset retained previous edge");
			check(manager.snapshot().containsKey(new RegionLoadManager.RegionKey(23, 15)),
					"complete reset omitted new edge");
		}
		try (RegionLoadManager manager = new RegionLoadManager()) {
			manager.selectCenter(30, 30);
			manager.mark(new RegionLoadManager.RegionKey(30, 30), RegionLoadManager.State.LOADED);
			manager.checkNeighborsAsync(key -> true).get(2L, TimeUnit.SECONDS);
			final var loading = manager.loadAvailableNeighborsAsync(key -> {
				try {
					Thread.sleep(25L);
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				return true;
			});
			manager.selectCenter(31, 30);
			loading.get(2L, TimeUnit.SECONDS);
			check(!manager.snapshot().containsKey(new RegionLoadManager.RegionKey(29, 29)),
					"stale generation retained an old region");
		}
		try (RegionLoadManager manager = new RegionLoadManager()) {
			manager.selectCenter(50, 50, 2);
			final var innerWindow = manager.snapshot().keySet().stream()
					.filter(key -> Math.max(Math.abs(key.x() - 50), Math.abs(key.y() - 50)) <= 1)
					.toList();
			manager.markLoaded(innerWindow);
			final AtomicInteger probes = new AtomicInteger();
			manager.checkNeighborsAsync(key -> {
				probes.incrementAndGet();
				return true;
			}).get(2L, TimeUnit.SECONDS);
			check(probes.get() == 16, "increase must probe only the sixteen new outer regions");
			final AtomicInteger loads = new AtomicInteger();
			manager.loadAvailableNeighborsAsync(key -> {
				loads.incrementAndGet();
				return true;
			}).get(2L, TimeUnit.SECONDS);
			check(loads.get() == 16, "increase must load only missing outer regions");

			manager.selectCenter(50, 50, 1);
			manager.markLoaded(innerWindow);
			probes.set(0);
			loads.set(0);
			manager.checkNeighborsAsync(key -> {
				probes.incrementAndGet();
				return true;
			}).get(2L, TimeUnit.SECONDS);
			manager.loadAvailableNeighborsAsync(key -> {
				loads.incrementAndGet();
				return true;
			}).get(2L, TimeUnit.SECONDS);
			check(probes.get() == 0, "decrease must not probe retained regions");
			check(loads.get() == 0, "decrease must not reload retained regions");
		}
		try (RegionLoadManager manager = new RegionLoadManager()) {
			manager.selectCenter(40, 40);
			manager.mark(new RegionLoadManager.RegionKey(40, 40), RegionLoadManager.State.LOADED);
			manager.checkNeighborsAsync(key -> true).get(2L, TimeUnit.SECONDS);
			final AtomicInteger active = new AtomicInteger();
			final AtomicInteger peak = new AtomicInteger();
			manager.loadAvailableNeighborsAsync(key -> {
				final int now = active.incrementAndGet();
				peak.updateAndGet(value -> Math.max(value, now));
				active.decrementAndGet();
				return true;
			}).get(2L, TimeUnit.SECONDS);
			check(peak.get() == 1, "neighbor loads must remain sequential");
		}
		System.out.println("RegionLoadManagerTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
