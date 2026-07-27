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

/** Deterministic checks for frame and streaming metrics. @author Maneco2 @version 1.1 */
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
		metrics.recordRegionDiscard(321L, 4096L);
		metrics.recordRegionRead(654L);
		metrics.setSelectionParallelism(8);
		metrics.recordRenderPreparation(55L, 66L);
		metrics.recordInitialVboPreparation(70L);
		metrics.recordBufferUpload(77L);
		metrics.recordAuxiliaryVboUpdates(88L, 60L, 28L, 2);
		metrics.recordAuxiliaryVboUpdates(44L, 30L, 14L, 1);
		metrics.setVisibleNeighborCounts(3, 5);

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
		check(snapshot.lastDiscardedLayerCount() == 4096L, "discarded layer count");
		check(snapshot.lastRegionReadMicros() == 654L, "region read time");
		check(snapshot.selectionParallelism() == 8, "selection parallelism");
		check(snapshot.lastBufferUploadMicros() == 77L, "buffer upload time");
		check(snapshot.lastGeometryPreparationMicros() == 55L, "geometry preparation time");
		check(snapshot.lastUniformUploadMicros() == 66L, "uniform upload time");
		check(snapshot.initialVboPreparationMicros() == 70L, "initial VBO preparation time");
		check(snapshot.auxiliaryVboUpdateMicros() == 44L, "last auxiliary VBO update time");
		check(snapshot.peakAuxiliaryVboUpdateMicros() == 88L, "peak auxiliary VBO update time");
		check(snapshot.totalAuxiliaryVboUpdateMicros() == 132L, "total auxiliary VBO update time");
		check(snapshot.auxiliaryVboUpdateCount() == 3, "auxiliary VBO update count");
		check(snapshot.auxiliaryVboPreparationMicros() == 30L, "last auxiliary VBO preparation time");
		check(snapshot.peakAuxiliaryVboPreparationMicros() == 60L, "peak auxiliary VBO preparation time");
		check(snapshot.totalAuxiliaryVboPreparationMicros() == 90L, "total auxiliary VBO preparation time");
		check(snapshot.auxiliaryVboUploadMicros() == 14L, "last auxiliary VBO upload time");
		check(snapshot.peakAuxiliaryVboUploadMicros() == 28L, "peak auxiliary VBO upload time");
		check(snapshot.totalAuxiliaryVboUploadMicros() == 42L, "total auxiliary VBO upload time");
		check(snapshot.visibleNeighbors() == 3, "visible neighbor count");
		check(snapshot.visibleNeighborLimit() == 5, "visible neighbor limit");
		check(PerformanceMetrics.formatReport(snapshot).contains("loadedLayerCount=128"),
				"loaded layer report");
		check(PerformanceMetrics.formatReport(snapshot).contains("lastRegionReadMicros=654"),
				"region read report");
		check(PerformanceMetrics.formatReport(snapshot).contains("lastGeodataParseMicros=654"),
				"geodata parsing report");
		check(PerformanceMetrics.formatReport(snapshot).contains("lastDiscardedLayerCount=4096"),
				"discarded layer report");
		check(PerformanceMetrics.formatReport(snapshot).contains("lastGeometryPreparationMicros=55"),
				"geometry preparation report");
		check(PerformanceMetrics.formatReport(snapshot).contains("initialVboUploadMicros=77"),
				"VBO upload report");
		check(PerformanceMetrics.formatReport(snapshot).contains("auxiliaryVboUpdateMicros=44"),
				"auxiliary VBO report");
		check(PerformanceMetrics.formatReport(snapshot).contains("peakAuxiliaryVboUpdateMicros=88"),
				"peak auxiliary VBO report");
		check(PerformanceMetrics.formatReport(snapshot).contains("peakAuxiliaryVboPreparationMicros=60"),
				"peak auxiliary VBO preparation report");
		check(PerformanceMetrics.formatReport(snapshot).contains("peakAuxiliaryVboUploadMicros=28"),
				"peak auxiliary VBO upload report");
		check(PerformanceMetrics.formatReport(snapshot).contains("visibleNeighbors=3"),
				"visible neighbor report");
		check(snapshot.peakMemoryBytes() > 0L, "peak memory");
		check(snapshot.heapCommittedBytes() >= snapshot.peakMemoryBytes(), "committed heap");
		check(snapshot.heapMaxBytes() >= snapshot.heapCommittedBytes(), "maximum heap");
		check(MemoryUsage.formatHud(new MemoryUsage.Snapshot(3_792L * 1024L * 1024L,
				8_192L * 1024L * 1024L, 12_288L * 1024L * 1024L,
				7_626L * 1024L * 1024L)).equals("Memory: 7626mb"), "process memory HUD label");
		check(PerformanceMetrics.formatReport(snapshot).contains("heapCommittedBytes="),
				"committed heap report");
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
		check(deterministic.snapshot().lastDiscardedLayerCount() == 0L, "reset discarded layer count");
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
