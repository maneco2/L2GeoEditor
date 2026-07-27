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

/**
 * Small allocation-free frame metrics collector used by the OpenGL loop.
 * @author Maneco2
	 * @version 1.4
 */
public final class PerformanceMetrics {
	public record Snapshot(long frames, long lastFrameMicros, long peakMemoryBytes,
			long heapCommittedBytes, long heapMaxBytes,
			long lastCentralLoadMicros, long neighborLoadMicros, int loadedRegions,
			long loadedLayerCount, long flatLayerCount, long complexLayerCount, long multilayerLayerCount,
			long averageFrameMicros, long worstFrameMicros, int slowFrames, long lastRegionDiscardMicros,
			long lastDiscardedLayerCount, long lastRegionReadMicros, int selectionParallelism,
			long lastGeometryPreparationMicros, long lastUniformUploadMicros,
			long initialVboPreparationMicros, long lastBufferUploadMicros,
			long auxiliaryVboUpdateMicros, long peakAuxiliaryVboUpdateMicros,
			long totalAuxiliaryVboUpdateMicros, int auxiliaryVboUpdateCount,
			long auxiliaryVboPreparationMicros, long peakAuxiliaryVboPreparationMicros,
			long totalAuxiliaryVboPreparationMicros,
			long auxiliaryVboUploadMicros, long peakAuxiliaryVboUploadMicros,
			long totalAuxiliaryVboUploadMicros,
			int visibleNeighbors, int visibleNeighborLimit) { }

	private static final int FRAME_WINDOW_SIZE = 120;
	private static final long SLOW_FRAME_MICROS = 33_333L;

	private long _frameStart;
	private long _frames;
	private long _lastFrameMicros;
	private long _peakMemoryBytes;
	private volatile long _lastCentralLoadMicros;
	private volatile long _neighborLoadMicros;
	private volatile int _loadedRegions;
	private volatile long _loadedLayerCount;
	private volatile long _flatLayerCount;
	private volatile long _complexLayerCount;
	private volatile long _multilayerLayerCount;
	private volatile long _lastRegionDiscardMicros;
	private volatile long _lastDiscardedLayerCount;
	private volatile long _lastRegionReadMicros;
	private volatile int _selectionParallelism;
	private volatile long _lastGeometryPreparationMicros;
	private volatile long _lastUniformUploadMicros;
	private volatile long _initialVboPreparationMicros;
	private volatile long _lastBufferUploadMicros;
	private volatile long _auxiliaryVboUpdateMicros;
	private volatile long _peakAuxiliaryVboUpdateMicros;
	private volatile long _totalAuxiliaryVboUpdateMicros;
	private volatile int _auxiliaryVboUpdateCount;
	private volatile long _auxiliaryVboPreparationMicros;
	private volatile long _peakAuxiliaryVboPreparationMicros;
	private volatile long _totalAuxiliaryVboPreparationMicros;
	private volatile long _auxiliaryVboUploadMicros;
	private volatile long _peakAuxiliaryVboUploadMicros;
	private volatile long _totalAuxiliaryVboUploadMicros;
	private volatile int _visibleNeighbors;
	private volatile int _visibleNeighborLimit;
	private final long[] _frameWindow = new long[FRAME_WINDOW_SIZE];
	private int _frameWindowIndex;
	private int _frameWindowCount;
	private long _frameWindowTotal;

	/** Clears frame and streaming samples while keeping the collector reusable. */
	public final void reset() {
		_frameStart = 0L;
		_frames = 0L;
		_lastFrameMicros = 0L;
		_peakMemoryBytes = 0L;
		_lastCentralLoadMicros = 0L;
		_neighborLoadMicros = 0L;
		_loadedRegions = 0;
		_loadedLayerCount = 0L;
		_flatLayerCount = 0L;
		_complexLayerCount = 0L;
		_multilayerLayerCount = 0L;
		_lastRegionDiscardMicros = 0L;
		_lastDiscardedLayerCount = 0L;
		_lastRegionReadMicros = 0L;
		_selectionParallelism = 0;
		_lastGeometryPreparationMicros = 0L;
		_lastUniformUploadMicros = 0L;
		_initialVboPreparationMicros = 0L;
		_lastBufferUploadMicros = 0L;
		_auxiliaryVboUpdateMicros = 0L;
		_peakAuxiliaryVboUpdateMicros = 0L;
		_totalAuxiliaryVboUpdateMicros = 0L;
		_auxiliaryVboUpdateCount = 0;
		_auxiliaryVboPreparationMicros = 0L;
		_peakAuxiliaryVboPreparationMicros = 0L;
		_totalAuxiliaryVboPreparationMicros = 0L;
		_auxiliaryVboUploadMicros = 0L;
		_peakAuxiliaryVboUploadMicros = 0L;
		_totalAuxiliaryVboUploadMicros = 0L;
		_visibleNeighbors = 0;
		_visibleNeighborLimit = 0;
		_frameWindowIndex = 0;
		_frameWindowCount = 0;
		_frameWindowTotal = 0L;
		java.util.Arrays.fill(_frameWindow, 0L);
	}

	public final void beginFrame() {
		_frameStart = System.nanoTime();
	}

	public final void endFrame() {
		recordFrameDurationMicros((System.nanoTime() - _frameStart) / 1000L);
	}

	public final void recordFrameDurationMicros(final long durationMicros) {
		_lastFrameMicros = Math.max(0L, durationMicros);
		_frames++;
		if (_frameWindowCount == FRAME_WINDOW_SIZE)
			_frameWindowTotal -= _frameWindow[_frameWindowIndex];
		else
			_frameWindowCount++;
		_frameWindow[_frameWindowIndex] = _lastFrameMicros;
		_frameWindowTotal += _lastFrameMicros;
		_frameWindowIndex = (_frameWindowIndex + 1) % FRAME_WINDOW_SIZE;
		final Runtime runtime = Runtime.getRuntime();
		final long used = runtime.totalMemory() - runtime.freeMemory();
		if (used > _peakMemoryBytes)
			_peakMemoryBytes = used;
	}

	public final Snapshot snapshot() {
		final MemoryUsage.Snapshot memory = MemoryUsage.capture();
		int slowFrames = 0;
		long worstFrameMicros = 0L;
		for (int i = 0; i < _frameWindowCount; i++) {
			if (_frameWindow[i] >= SLOW_FRAME_MICROS)
				slowFrames++;
			if (_frameWindow[i] > worstFrameMicros)
				worstFrameMicros = _frameWindow[i];
		}
		return new Snapshot(_frames, _lastFrameMicros, _peakMemoryBytes,
				memory.committedBytes(), memory.maxBytes(),
				_lastCentralLoadMicros, _neighborLoadMicros, _loadedRegions, _loadedLayerCount,
				_flatLayerCount, _complexLayerCount, _multilayerLayerCount,
				_frameWindowCount == 0 ? 0L : _frameWindowTotal / _frameWindowCount,
				worstFrameMicros, slowFrames, _lastRegionDiscardMicros, _lastDiscardedLayerCount,
				_lastRegionReadMicros,
				_selectionParallelism, _lastGeometryPreparationMicros, _lastUniformUploadMicros,
				_initialVboPreparationMicros, _lastBufferUploadMicros,
				_auxiliaryVboUpdateMicros, _peakAuxiliaryVboUpdateMicros,
				_totalAuxiliaryVboUpdateMicros, _auxiliaryVboUpdateCount,
				_auxiliaryVboPreparationMicros, _peakAuxiliaryVboPreparationMicros,
				_totalAuxiliaryVboPreparationMicros,
				_auxiliaryVboUploadMicros, _peakAuxiliaryVboUploadMicros,
				_totalAuxiliaryVboUploadMicros,
				_visibleNeighbors, _visibleNeighborLimit);
	}

	public static String formatReport(final Snapshot snapshot) {
		final long fps = snapshot.averageFrameMicros() <= 0L ? 0L : 1_000_000L / snapshot.averageFrameMicros();
		return "frames=" + snapshot.frames() + System.lineSeparator()
				+ "averageFps=" + fps + System.lineSeparator()
				+ "lastFrameMicros=" + snapshot.lastFrameMicros() + System.lineSeparator()
				+ "averageFrameMicros=" + snapshot.averageFrameMicros() + System.lineSeparator()
				+ "worstFrameMicros=" + snapshot.worstFrameMicros() + System.lineSeparator()
				+ "slowFrames=" + snapshot.slowFrames() + System.lineSeparator()
				+ "peakMemoryBytes=" + snapshot.peakMemoryBytes() + System.lineSeparator()
				+ "heapCommittedBytes=" + snapshot.heapCommittedBytes() + System.lineSeparator()
				+ "heapMaxBytes=" + snapshot.heapMaxBytes() + System.lineSeparator()
				+ "loadedRegions=" + snapshot.loadedRegions() + System.lineSeparator()
				+ "loadedLayerCount=" + snapshot.loadedLayerCount() + System.lineSeparator()
				+ "flatLayerCount=" + snapshot.flatLayerCount() + System.lineSeparator()
				+ "complexLayerCount=" + snapshot.complexLayerCount() + System.lineSeparator()
				+ "multilayerLayerCount=" + snapshot.multilayerLayerCount() + System.lineSeparator()
				+ "lastCentralLoadMicros=" + snapshot.lastCentralLoadMicros() + System.lineSeparator()
				+ "neighborLoadMicros=" + snapshot.neighborLoadMicros() + System.lineSeparator()
				+ "lastRegionDiscardMicros=" + snapshot.lastRegionDiscardMicros() + System.lineSeparator()
				+ "lastDiscardedLayerCount=" + snapshot.lastDiscardedLayerCount() + System.lineSeparator()
				+ "lastRegionReadMicros=" + snapshot.lastRegionReadMicros() + System.lineSeparator()
				+ "lastGeodataParseMicros=" + snapshot.lastRegionReadMicros() + System.lineSeparator()
				+ "selectionParallelism=" + snapshot.selectionParallelism() + System.lineSeparator()
				+ "lastGeometryPreparationMicros=" + snapshot.lastGeometryPreparationMicros() + System.lineSeparator()
				+ "lastUniformUploadMicros=" + snapshot.lastUniformUploadMicros() + System.lineSeparator()
				+ "initialVboPreparationMicros=" + snapshot.initialVboPreparationMicros() + System.lineSeparator()
				+ "lastBufferUploadMicros=" + snapshot.lastBufferUploadMicros() + System.lineSeparator()
				+ "initialVboUploadMicros=" + snapshot.lastBufferUploadMicros() + System.lineSeparator()
				+ "auxiliaryVboUpdateMicros=" + snapshot.auxiliaryVboUpdateMicros() + System.lineSeparator()
				+ "peakAuxiliaryVboUpdateMicros=" + snapshot.peakAuxiliaryVboUpdateMicros() + System.lineSeparator()
				+ "totalAuxiliaryVboUpdateMicros=" + snapshot.totalAuxiliaryVboUpdateMicros() + System.lineSeparator()
				+ "auxiliaryVboUpdateCount=" + snapshot.auxiliaryVboUpdateCount() + System.lineSeparator()
				+ "auxiliaryVboPreparationMicros=" + snapshot.auxiliaryVboPreparationMicros() + System.lineSeparator()
				+ "peakAuxiliaryVboPreparationMicros=" + snapshot.peakAuxiliaryVboPreparationMicros() + System.lineSeparator()
				+ "totalAuxiliaryVboPreparationMicros=" + snapshot.totalAuxiliaryVboPreparationMicros() + System.lineSeparator()
				+ "auxiliaryVboUploadMicros=" + snapshot.auxiliaryVboUploadMicros() + System.lineSeparator()
				+ "peakAuxiliaryVboUploadMicros=" + snapshot.peakAuxiliaryVboUploadMicros() + System.lineSeparator()
				+ "totalAuxiliaryVboUploadMicros=" + snapshot.totalAuxiliaryVboUploadMicros() + System.lineSeparator()
				+ "visibleNeighbors=" + snapshot.visibleNeighbors() + System.lineSeparator()
				+ "visibleNeighborLimit=" + snapshot.visibleNeighborLimit() + System.lineSeparator();
	}

	public final void recordRegionDiscard(final long durationMicros) {
		recordRegionDiscard(durationMicros, 0L);
	}

	public final void recordRegionDiscard(final long durationMicros, final long layerCount) {
		_lastRegionDiscardMicros = Math.max(0L, durationMicros);
		_lastDiscardedLayerCount = Math.max(0L, layerCount);
	}

	public final void recordRegionRead(final long durationMicros) {
		_lastRegionReadMicros = Math.max(0L, durationMicros);
	}

	public final void setSelectionParallelism(final int parallelism) {
		_selectionParallelism = Math.max(0, parallelism);
	}

	public final void recordBufferUpload(final long durationMicros) {
		_lastBufferUploadMicros = Math.max(0L, durationMicros);
	}

	public final void recordAuxiliaryVboUpdates(final long durationMicros, final long preparationMicros,
			final long uploadMicros, final int count) {
		if (durationMicros <= 0L || count <= 0)
			return;
		_auxiliaryVboUpdateMicros = durationMicros;
		_peakAuxiliaryVboUpdateMicros = Math.max(_peakAuxiliaryVboUpdateMicros, durationMicros);
		_totalAuxiliaryVboUpdateMicros += durationMicros;
		_auxiliaryVboUpdateCount += count;
		_auxiliaryVboPreparationMicros = Math.max(0L, preparationMicros);
		_peakAuxiliaryVboPreparationMicros = Math.max(_peakAuxiliaryVboPreparationMicros,
				_auxiliaryVboPreparationMicros);
		_totalAuxiliaryVboPreparationMicros += _auxiliaryVboPreparationMicros;
		_auxiliaryVboUploadMicros = Math.max(0L, uploadMicros);
		_peakAuxiliaryVboUploadMicros = Math.max(_peakAuxiliaryVboUploadMicros, _auxiliaryVboUploadMicros);
		_totalAuxiliaryVboUploadMicros += _auxiliaryVboUploadMicros;
	}

	public final void recordRenderPreparation(final long geometryMicros, final long uniformUploadMicros) {
		_lastGeometryPreparationMicros = Math.max(0L, geometryMicros);
		_lastUniformUploadMicros = Math.max(0L, uniformUploadMicros);
	}

	public final void recordInitialVboPreparation(final long durationMicros) {
		_initialVboPreparationMicros = Math.max(0L, durationMicros);
	}

	public final void recordRegionLoad(final long durationMicros, final boolean central) {
		if (central) {
			_lastCentralLoadMicros = durationMicros;
			_neighborLoadMicros = 0L;
		} else {
			_neighborLoadMicros += durationMicros;
		}
	}

	public final void setLoadedRegions(final int loadedRegions) {
		_loadedRegions = loadedRegions;
	}

	public final void setLoadedLayerCount(final long loadedLayerCount) {
		_loadedLayerCount = Math.max(0L, loadedLayerCount);
	}

	public final void setLayerCounts(final long flat, final long complex, final long multilayer) {
		_flatLayerCount = Math.max(0L, flat);
		_complexLayerCount = Math.max(0L, complex);
		_multilayerLayerCount = Math.max(0L, multilayer);
	}

	public final void setVisibleNeighborCounts(final int count, final int limit) {
		_visibleNeighbors = Math.max(0, count);
		_visibleNeighborLimit = Math.max(0, limit);
	}
}
