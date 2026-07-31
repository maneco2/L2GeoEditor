/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.editor;

import java.util.Arrays;

import com.geo.core.DetailedRegionSnapshot;
import com.geo.core.RegionSnapshot;

/**
 * Allocation-free CPU visibility plan for the future shared-geometry renderer.
 * It owns no OpenGL resources and does not change the active renderer.
 */
final class GridVisibilitySelector {
	private static final int REGION_BLOCKS = 256;
	private static final int CELLS_PER_BLOCK = 8;
	private final float[] minimumHeights = new float[REGION_BLOCKS * REGION_BLOCKS];
	private final float[] maximumHeights = new float[REGION_BLOCKS * REGION_BLOCKS];
	private final int[] visibleBlocks = new int[REGION_BLOCKS * REGION_BLOCKS];
	private DetailedRegionSnapshot preparedDetails;
	private int visibleCount;

	long prepare(final RegionSnapshot region, final DetailedRegionSnapshot details) {
		if (preparedDetails == details)
			return 0L;
		final long started = System.nanoTime();
		for (int blockY = 0; blockY < REGION_BLOCKS; blockY++) {
			for (int blockX = 0; blockX < REGION_BLOCKS; blockX++) {
				final int index = blockY * REGION_BLOCKS + blockX;
				float minimum = Float.POSITIVE_INFINITY;
				float maximum = Float.NEGATIVE_INFINITY;
				if (region.blockType(blockX, blockY) == 0) {
					final float height = details.height(blockX * CELLS_PER_BLOCK,
							blockY * CELLS_PER_BLOCK, 0) / 16f;
					minimum = height - 0.2f;
					maximum = height;
				} else {
					for (int cellY = 0; cellY < CELLS_PER_BLOCK; cellY++) {
						for (int cellX = 0; cellX < CELLS_PER_BLOCK; cellX++) {
							final int localX = blockX * CELLS_PER_BLOCK + cellX;
							final int localY = blockY * CELLS_PER_BLOCK + cellY;
							final int layers = details.layerCount(localX, localY);
							for (int layer = 0; layer < layers; layer++) {
								final float height = details.height(localX, localY, layer) / 16f;
								minimum = Math.min(minimum, height - 0.2f);
								maximum = Math.max(maximum, height);
							}
						}
					}
				}
				minimumHeights[index] = minimum == Float.POSITIVE_INFINITY ? -0.2f : minimum;
				maximumHeights[index] = maximum == Float.NEGATIVE_INFINITY ? 0f : maximum;
			}
		}
		preparedDetails = details;
		return (System.nanoTime() - started) / 1_000L;
	}

	long select(final float[][] frustum, final float cameraX,
			final float cameraZ, final int configuredRange) {
		final long started = System.nanoTime();
		final int range = Math.max(8, Math.min(256, configuredRange));
		final int centerX = Math.max(0, Math.min(255, (int) cameraX >> 3));
		final int centerY = Math.max(0, Math.min(255, (int) cameraZ >> 3));
		final int minimumX = Math.max(0, centerX - range);
		final int maximumX = Math.min(255, centerX + range);
		final int minimumY = Math.max(0, centerY - range);
		final int maximumY = Math.min(255, centerY + range);
		visibleCount = 0;
		for (int blockY = minimumY; blockY <= maximumY; blockY++) {
			for (int blockX = minimumX; blockX <= maximumX; blockX++) {
				final int index = blockY * REGION_BLOCKS + blockX;
				if (isVisible(frustum, blockX * 8f, blockY * 8f,
						(blockX + 1) * 8f, (blockY + 1) * 8f,
						minimumHeights[index], maximumHeights[index]))
					visibleBlocks[visibleCount++] = index;
			}
		}
		return (System.nanoTime() - started) / 1_000L;
	}

	int visibleCount() {
		return visibleCount;
	}

	int visibleBlockAt(final int index) {
		if (index < 0 || index >= visibleCount)
			throw new IndexOutOfBoundsException("Visible block: " + index);
		return visibleBlocks[index];
	}

	int[] copyVisibleBlocks() {
		return Arrays.copyOf(visibleBlocks, visibleCount);
	}

	private static boolean isVisible(final float[][] frustum,
			final float minimumX, final float minimumZ,
			final float maximumX, final float maximumZ,
			final float minimumY, final float maximumY) {
		for (final float[] plane : frustum) {
			final float x = plane[0] >= 0f ? maximumX : minimumX;
			final float y = plane[1] >= 0f ? maximumY : minimumY;
			final float z = plane[2] >= 0f ? maximumZ : minimumZ;
			if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] <= 0f)
				return false;
		}
		return true;
	}
}
