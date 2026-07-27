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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Verifies the streaming overview parser without loading production geodata. */
public final class GeoDataOverviewTest {
	public static void main(final String[] args) throws Exception {
		final Path root = Files.createTempDirectory("geo-overview-test");
		try {
			writeFlatRegion(root.resolve("10_10.l2j"));
			final int[] progress = { 0 };
			final java.awt.image.BufferedImage image = GeoDataOverview.scan(root.toFile(),
					(completed, total) -> progress[0] = completed);
			check(image.getWidth() == GeoDataOverview.WIDTH, "overview width");
			check(image.getHeight() == GeoDataOverview.HEIGHT, "overview height");
			check(progress[0] == GeoDataOverview.REGION_COUNT_X * GeoDataOverview.REGION_COUNT_Y,
					"scan progress");
			final int dataPixel = image.getRGB(0, 0);
			final int missingPixel = image.getRGB(GeoDataOverview.REGION_OVERVIEW_SIZE * 2, 0);
			check((dataPixel & 0xFF) > ((dataPixel >> 16) & 0xFF), "flat geodata color");
			check(dataPixel != missingPixel, "missing region remains distinct");
			System.out.println("GeoDataOverviewTest OK");
		} finally {
			try (var paths = Files.walk(root)) {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (final IOException ignored) { }
				});
			}
		}
	}

	private static void writeFlatRegion(final Path file) throws IOException {
		try (OutputStream output = Files.newOutputStream(file)) {
			for (int block = 0; block < GeoEngine.GEO_REGION_SIZE * GeoEngine.GEO_REGION_SIZE; block++) {
				output.write(GeoEngine.GEO_BLOCK_TYPE_FLAT);
				output.write(0x80);
				output.write(0x00);
			}
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
