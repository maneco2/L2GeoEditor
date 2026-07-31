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

import com.util.GeoStreamReader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

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
				+ ",discardReleaseMicros=" + releaseMicros);
	}

	private static long usedMemory() {
		final Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}
}
