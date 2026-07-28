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

import com.jogamp.common.nio.Buffers;
import com.jogl.GLTerrain;
import com.util.GeoStreamReader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ShortBuffer;

/** Reproducible parser and layer workload probe for real L2J regions. @author Maneco2 @version 1.0 */
public final class RealRegionBenchmark {
	public static void main(final String[] args) throws Exception {
		if (args.length == 0)
			throw new IllegalArgumentException("Provide one or more .l2j region files.");
		for (final String argument : args)
			measure(new File(argument));
	}

	private static void measure(final File file) throws Exception {
		final String[] coordinates = file.getName().replaceFirst("\\.l2j$", "").split("_");
		final int regionX = Integer.parseInt(coordinates[0]) - 10;
		final int regionY = Integer.parseInt(coordinates[1]) - 10;
		final long memoryBefore = usedMemory();
		final long parseStart = System.nanoTime();
		final GeoRegion region;
		try (FileInputStream input = new FileInputStream(file);
				BufferedInputStream buffered = new BufferedInputStream(input)) {
			region = new GeoRegion(regionX, regionY, GeoStreamReader.wrap(buffered), true, file);
		}
		final long parseMicros = (System.nanoTime() - parseStart) / 1_000L;

		final long[] blocks = new long[3];
		for (final GeoBlock[] row : region.getGeoBlocks())
			for (final GeoBlock block : row)
				blocks[block.getType()]++;

		final long flatLayers = region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_FLAT);
		final long complexLayers = region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_COMPLEX);
		final long multilayerLayers = region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER);
		final TerrainTiming terrainTiming = measureTerrain(region.getGeoBlocks());
		final long memoryBytes = Math.max(0L, usedMemory() - memoryBefore);
		final long releaseStart = System.nanoTime();
		region.releaseForDiscard();
		final long releaseMicros = (System.nanoTime() - releaseStart) / 1_000L;

		System.out.println("region=" + file.getName()
				+ ",fileBytes=" + file.length()
				+ ",parseMicros=" + parseMicros
				+ ",memoryDeltaBytes=" + memoryBytes
				+ ",flatBlocks=" + blocks[GeoEngine.GEO_BLOCK_TYPE_FLAT]
				+ ",complexBlocks=" + blocks[GeoEngine.GEO_BLOCK_TYPE_COMPLEX]
				+ ",multilayerBlocks=" + blocks[GeoEngine.GEO_BLOCK_TYPE_MULTILAYER]
				+ ",flatLayers=" + flatLayers
				+ ",complexLayers=" + complexLayers
				+ ",multilayerLayers=" + multilayerLayers
				+ ",totalLayers=" + (flatLayers + complexLayers + multilayerLayers)
				+ ",legacyTerrainMicros=" + terrainTiming.legacyMicros()
				+ ",optimizedTerrainMicros=" + terrainTiming.optimizedMicros()
				+ ",terrainSpeedup=" + String.format("%.2f", terrainTiming.speedup())
				+ ",discardReleaseMicros=" + releaseMicros);
	}

	private static TerrainTiming measureTerrain(final GeoBlock[][] blocks) {
		final GLTerrain.TerrainDetailLevel detailLevel = GLTerrain.TerrainDetailLevel.MEDIUM;
		final GLTerrain.TerrainBuilderXDetail optimized = new GLTerrain.TerrainBuilderXDetail(detailLevel);
		final int factor = detailLevel.getFactor();
		final int width = GeoEngine.GEO_REGION_SIZE * factor;
		final int capacity = width * width * 3;
		final ShortBuffer legacyBuffer = Buffers.newDirectShortBuffer(capacity);
		final ShortBuffer optimizedBuffer = Buffers.newDirectShortBuffer(capacity);

		optimized.updateImpl(optimizedBuffer, blocks, false);
		legacyBuffer.clear();
		legacyTerrainUpdate(legacyBuffer, blocks, factor, false);

		legacyBuffer.clear();
		final long legacyStart = System.nanoTime();
		legacyTerrainUpdate(legacyBuffer, blocks, factor, false);
		final long legacyMicros = Math.max(1L, (System.nanoTime() - legacyStart) / 1_000L);

		optimizedBuffer.clear();
		final long optimizedStart = System.nanoTime();
		optimized.updateImpl(optimizedBuffer, blocks, false);
		final long optimizedMicros = Math.max(1L, (System.nanoTime() - optimizedStart) / 1_000L);

		for (int index = 0; index < capacity; index++) {
			if (legacyBuffer.get(index) != optimizedBuffer.get(index))
				throw new AssertionError("Terrain output changed at short index " + index + '.');
		}
		return new TerrainTiming(legacyMicros, optimizedMicros);
	}

	private static void legacyTerrainUpdate(final ShortBuffer vertexBuffer, final GeoBlock[][] blocks,
			final int factor, final boolean flipped) {
		final int cells = GeoEngine.GEO_BLOCK_SHIFT / factor;
		final int width = GeoEngine.GEO_REGION_SIZE * factor;
		for (int blockX = GeoEngine.GEO_REGION_SIZE, blockY, vertexX, vertexY, factorX, factorY; blockX-- > 0;) {
			for (blockY = GeoEngine.GEO_REGION_SIZE; blockY-- > 0;) {
				for (factorX = factor; factorX-- > 0;) {
					vertexX = blockX * factor + factorX;
					for (factorY = factor; factorY-- > 0;) {
						vertexY = blockY * factor + factorY;
						vertexBuffer.position((vertexY * width + vertexX) * 3);
						vertexBuffer.put((short) vertexX);
						vertexBuffer.put((short) (legacyVertexHeight(blocks, blockX, blockY,
								factorX * cells, factorY * cells, cells, flipped) / 16f - 0.5f));
						vertexBuffer.put((short) vertexY);
					}
				}
			}
		}
		vertexBuffer.position(vertexBuffer.capacity());
	}

	private static int legacyVertexHeight(final GeoBlock[][] blocks, final int blockX, final int blockY,
			final int cellX, final int cellY, final int cells, final boolean flipped) {
		int height = Integer.MAX_VALUE;
		height = Math.min(height, legacyGroupHeight(blocks, blockX, blockY, cellX - cells, cellY - cells, cells, flipped));
		height = Math.min(height, legacyGroupHeight(blocks, blockX, blockY, cellX - cells, cellY, cells, flipped));
		height = Math.min(height, legacyGroupHeight(blocks, blockX, blockY, cellX, cellY - cells, cells, flipped));
		return Math.min(height, legacyGroupHeight(blocks, blockX, blockY, cellX, cellY, cells, flipped));
	}

	private static int legacyGroupHeight(final GeoBlock[][] blocks, int blockX, int blockY, int cellX, int cellY,
			final int cells, final boolean flipped) {
		if (cellX < 0) {
			blockX--;
			cellX = GeoEngine.GEO_BLOCK_SHIFT - cells;
		}
		if (cellY < 0) {
			blockY--;
			cellY = GeoEngine.GEO_BLOCK_SHIFT - cells;
		}
		if (blockX < 0 || blockY < 0 || blockX >= GeoEngine.GEO_REGION_SIZE || blockY >= GeoEngine.GEO_REGION_SIZE)
			return Short.MAX_VALUE;

		int height = Integer.MAX_VALUE;
		final GeoBlock block = blocks[blockX][blockY];
		for (int x = cells; x-- > 0;) {
			for (int y = cells; y-- > 0;) {
				height = Math.min(height, (flipped
						? block.nGetCellByLayer(cellX + x, cellY + y, block.nGetLayerCount(cellX + x, cellY + y) - 1)
						: block.nGetCellByLayer(cellX + x, cellY + y, 0)).getHeight());
			}
		}
		return height;
	}

	private record TerrainTiming(long legacyMicros, long optimizedMicros) {
		private double speedup() {
			return (double) legacyMicros / optimizedMicros;
		}
	}

	private static long usedMemory() {
		final Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}
}
