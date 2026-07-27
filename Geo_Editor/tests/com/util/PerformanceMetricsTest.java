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

/** Deterministic checks for frame and streaming metrics. @author Maneco2 @version 1.0 */
public final class PerformanceMetricsTest {
	public static void main(final String[] args) {
		final PerformanceMetrics metrics = new PerformanceMetrics();
		metrics.beginFrame();
		metrics.endFrame();
		metrics.recordRegionLoad(1500L, true);
		metrics.recordRegionLoad(2500L, false);
		metrics.recordRegionLoad(500L, false);
		metrics.setLoadedRegions(7);
		metrics.setLoadedLayerCount(128L);
		metrics.setLayerCounts(4L, 64L, 60L);
		metrics.recordRegionDiscard(321L);
		metrics.recordRegionRead(654L);
		metrics.setSelectionParallelism(8);
		metrics.recordBufferUpload(77L);

		final PerformanceMetrics.Snapshot snapshot = metrics.snapshot();
		check(snapshot.frames() == 1L, "frame count");
		check(snapshot.lastCentralLoadMicros() == 1500L, "central load");
		check(snapshot.neighborLoadMicros() == 3000L, "neighbor load total");
		check(snapshot.loadedRegions() == 7, "loaded region count");
		check(snapshot.loadedLayerCount() == 128L, "loaded layer count");
		check(snapshot.flatLayerCount() == 4L, "flat layer count");
		check(snapshot.complexLayerCount() == 64L, "complex layer count");
		check(snapshot.multilayerLayerCount() == 60L, "multilayer layer count");
		check(snapshot.lastRegionDiscardMicros() == 321L, "region discard time");
		check(snapshot.lastRegionReadMicros() == 654L, "region read time");
		check(snapshot.selectionParallelism() == 8, "selection parallelism");
		check(snapshot.lastBufferUploadMicros() == 77L, "buffer upload time");
		check(PerformanceMetrics.formatReport(snapshot).contains("loadedLayerCount=128"),
				"loaded layer report");
		check(PerformanceMetrics.formatReport(snapshot).contains("lastRegionReadMicros=654"),
				"region read report");
		check(snapshot.peakMemoryBytes() > 0L, "peak memory");
		metrics.recordRegionLoad(900L, true);
		check(metrics.snapshot().neighborLoadMicros() == 0L, "neighbor total reset for new center");
		final PerformanceMetrics deterministic = new PerformanceMetrics();
		deterministic.recordFrameDurationMicros(10_000L);
		deterministic.recordFrameDurationMicros(20_000L);
		deterministic.recordFrameDurationMicros(40_000L);
		final PerformanceMetrics.Snapshot measured = deterministic.snapshot();
		check(measured.averageFrameMicros() == 23_333L, "rolling frame average");
		check(PerformanceMetrics.formatReport(measured).contains("averageFps=42"), "average FPS report");
		check(measured.worstFrameMicros() == 40_000L, "worst frame");
		check(measured.slowFrames() == 1, "slow frame count");
		deterministic.reset();
		check(deterministic.snapshot().frames() == 0L, "reset frame count");
		check(deterministic.snapshot().worstFrameMicros() == 0L, "reset worst frame");
		check(deterministic.snapshot().lastRegionReadMicros() == 0L, "reset region read time");
		deterministic.recordRegionRead(-10L);
		deterministic.recordRegionDiscard(-10L);
		check(deterministic.snapshot().lastRegionReadMicros() == 0L, "negative region read time");
		check(deterministic.snapshot().lastRegionDiscardMicros() == 0L, "negative region discard time");
		System.out.println("PerformanceMetricsTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
