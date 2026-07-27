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
 * @version 1.0
 */
public final class PerformanceMetrics {
	public record Snapshot(long frames, long lastFrameMicros, long peakMemoryBytes,
			long lastCentralLoadMicros, long neighborLoadMicros, int loadedRegions,
			long loadedLayerCount, long flatLayerCount, long complexLayerCount, long multilayerLayerCount,
			long averageFrameMicros, long worstFrameMicros, int slowFrames, long lastRegionDiscardMicros,
			long lastRegionReadMicros, int selectionParallelism, long lastBufferUploadMicros) { }

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
	private volatile long _lastRegionReadMicros;
	private volatile int _selectionParallelism;
	private volatile long _lastBufferUploadMicros;
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
		_lastRegionReadMicros = 0L;
		_selectionParallelism = 0;
		_lastBufferUploadMicros = 0L;
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
		int slowFrames = 0;
		long worstFrameMicros = 0L;
		for (int i = 0; i < _frameWindowCount; i++) {
			if (_frameWindow[i] >= SLOW_FRAME_MICROS)
				slowFrames++;
			if (_frameWindow[i] > worstFrameMicros)
				worstFrameMicros = _frameWindow[i];
		}
		return new Snapshot(_frames, _lastFrameMicros, _peakMemoryBytes,
				_lastCentralLoadMicros, _neighborLoadMicros, _loadedRegions, _loadedLayerCount,
				_flatLayerCount, _complexLayerCount, _multilayerLayerCount,
				_frameWindowCount == 0 ? 0L : _frameWindowTotal / _frameWindowCount,
				worstFrameMicros, slowFrames, _lastRegionDiscardMicros, _lastRegionReadMicros,
				_selectionParallelism, _lastBufferUploadMicros);
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
				+ "loadedRegions=" + snapshot.loadedRegions() + System.lineSeparator()
				+ "loadedLayerCount=" + snapshot.loadedLayerCount() + System.lineSeparator()
				+ "flatLayerCount=" + snapshot.flatLayerCount() + System.lineSeparator()
				+ "complexLayerCount=" + snapshot.complexLayerCount() + System.lineSeparator()
				+ "multilayerLayerCount=" + snapshot.multilayerLayerCount() + System.lineSeparator()
				+ "lastCentralLoadMicros=" + snapshot.lastCentralLoadMicros() + System.lineSeparator()
				+ "neighborLoadMicros=" + snapshot.neighborLoadMicros() + System.lineSeparator()
				+ "lastRegionDiscardMicros=" + snapshot.lastRegionDiscardMicros() + System.lineSeparator()
				+ "lastRegionReadMicros=" + snapshot.lastRegionReadMicros() + System.lineSeparator()
				+ "selectionParallelism=" + snapshot.selectionParallelism() + System.lineSeparator()
				+ "lastBufferUploadMicros=" + snapshot.lastBufferUploadMicros() + System.lineSeparator();
	}

	public final void recordRegionDiscard(final long durationMicros) {
		_lastRegionDiscardMicros = Math.max(0L, durationMicros);
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
}
