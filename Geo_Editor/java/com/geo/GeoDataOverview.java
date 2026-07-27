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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Builds a compact, data-derived overview of the L2J geodata files.
 *
 * <p>This class deliberately streams one file at a time. It never creates
 * {@link GeoRegion} instances for the overview, so the global view does not
 * duplicate the complete geodata set in memory.</p>
 * @author Maneco2
 * @version 1.0
 */
public final class GeoDataOverview {
	public static final int REGION_MIN = 10;
	public static final int REGION_MAX_X = 26;
	public static final int REGION_MAX_Y = 25;
	public static final int REGION_COUNT_X = REGION_MAX_X - REGION_MIN + 1;
	public static final int REGION_COUNT_Y = REGION_MAX_Y - REGION_MIN + 1;
	/** One output pixel represents one 8x8-cell geodata block. */
	public static final int REGION_OVERVIEW_SIZE = GeoEngine.GEO_REGION_SIZE;
	public static final int WIDTH = REGION_COUNT_X * REGION_OVERVIEW_SIZE;
	public static final int HEIGHT = REGION_COUNT_Y * REGION_OVERVIEW_SIZE;
	private static final int BLOCKS_PER_OVERVIEW_PIXEL = GeoEngine.GEO_REGION_SIZE / REGION_OVERVIEW_SIZE;
	private static final int BLOCK_CELL_COUNT = GeoEngine.GEO_BLOCK_SHIFT * GeoEngine.GEO_BLOCK_SHIFT;

	@FunctionalInterface
	public interface ProgressListener {
		void update(int completed, int total);
	}

	private GeoDataOverview() {
	}

	/** Scans every available region and returns a geodata-only overview image. */
	public static BufferedImage scan(final File root) throws IOException {
		return scan(root, null);
	}

	/**
	 * Scans every available region and reports progress after each region file.
	 * Missing files remain dark, making the global coverage visible without
	 * confusing a missing region with a loaded one.
	 */
	public static BufferedImage scan(final File root, final ProgressListener progress) throws IOException {
		if (root == null || !root.isDirectory())
			throw new IOException("Geodata directory not found: " + root);

		final BufferedImage overview = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		final Graphics2D graphics = overview.createGraphics();
		try {
			graphics.setColor(new Color(27, 32, 38));
			graphics.fillRect(0, 0, WIDTH, HEIGHT);
		} finally {
			graphics.dispose();
		}

		final int total = REGION_COUNT_X * REGION_COUNT_Y;
		int completed = 0;
		for (int regionX = REGION_MIN; regionX <= REGION_MAX_X; regionX++) {
			for (int regionY = REGION_MIN; regionY <= REGION_MAX_Y; regionY++) {
				final File file = new File(root, regionX + "_" + regionY + ".l2j");
				if (file.isFile())
					scanRegion(file, overview, regionX - REGION_MIN, regionY - REGION_MIN);

				completed++;
				if (progress != null)
					progress.update(completed, total);
			}
		}
		return overview;
	}

	private static void scanRegion(final File file, final BufferedImage overview, final int regionImageX,
			final int regionImageY) throws IOException {
		final int pixelCount = REGION_OVERVIEW_SIZE * REGION_OVERVIEW_SIZE;
		final int[] flat = new int[pixelCount];
		final int[] complex = new int[pixelCount];
		final int[] multi = new int[pixelCount];
		final long[] heightSum = new long[pixelCount];
		final int[] samples = new int[pixelCount];

		try (InputStream input = new BufferedInputStream(new FileInputStream(file), 64 * 1024)) {
			for (int blockX = 0; blockX < GeoEngine.GEO_REGION_SIZE; blockX++) {
				for (int blockY = 0; blockY < GeoEngine.GEO_REGION_SIZE; blockY++) {
					final int type = readUnsignedByte(input);
					final int overviewX = blockX / BLOCKS_PER_OVERVIEW_PIXEL;
					final int overviewY = blockY / BLOCKS_PER_OVERVIEW_PIXEL;
					final int pixel = overviewX * REGION_OVERVIEW_SIZE + overviewY;
					switch (type) {
					case GeoEngine.GEO_BLOCK_TYPE_FLAT:
						flat[pixel]++;
						addHeight(input, heightSum, samples, pixel);
						break;
					case GeoEngine.GEO_BLOCK_TYPE_COMPLEX:
						complex[pixel]++;
						// One representative cell is enough for the global overview.
						addHeight(input, heightSum, samples, pixel);
						skipFully(input, (BLOCK_CELL_COUNT - 1) * 2L);
						break;
					case GeoEngine.GEO_BLOCK_TYPE_MULTILAYER:
						multi[pixel]++;
						for (int cell = 0; cell < BLOCK_CELL_COUNT; cell++) {
							final int layers = readUnsignedByte(input);
							if (!GeoEngine.layersValid(layers))
								throw new IOException("Invalid layer count " + layers + " in " + file.getName());
							addHeight(input, heightSum, samples, pixel);
							skipFully(input, (layers - 1) * 2L);
						}
						break;
					default:
						throw new IOException("Unknown block type " + type + " in " + file.getName());
					}
				}
			}
		}

		final int imageOffsetX = regionImageX * REGION_OVERVIEW_SIZE;
		final int imageOffsetY = regionImageY * REGION_OVERVIEW_SIZE;
		for (int x = 0; x < REGION_OVERVIEW_SIZE; x++) {
			for (int y = 0; y < REGION_OVERVIEW_SIZE; y++) {
				final int pixel = x * REGION_OVERVIEW_SIZE + y;
				final int blockCount = flat[pixel] + complex[pixel] + multi[pixel];
				if (blockCount == 0)
					continue;

				final int averageHeight = samples[pixel] == 0 ? 0
						: (int) (heightSum[pixel] / samples[pixel]);
				final int brightness = 80 + Math.max(0, Math.min(175,
						(averageHeight - GeoEngine.HEIGHT_MIN_VALUE) * 175
								/ (GeoEngine.HEIGHT_MAX_VALUE - GeoEngine.HEIGHT_MIN_VALUE)));
				final int red;
				final int green;
				final int blue;
				if (multi[pixel] > 0) {
					red = brightness;
					green = brightness / 2;
					blue = 45;
				} else if (complex[pixel] > 0) {
					red = 35;
					green = brightness;
					blue = 75;
				} else {
					red = 45;
					green = 105;
					blue = brightness;
				}
				overview.setRGB(imageOffsetX + x, imageOffsetY + y,
						(red << 16) | (green << 8) | blue);
			}
		}
	}

	private static void addHeight(final InputStream input, final long[] heightSum, final int[] samples,
			final int pixel) throws IOException {
		heightSum[pixel] += GeoEngine.getHeight(readShortLE(input));
		samples[pixel]++;
	}

	private static int readUnsignedByte(final InputStream input) throws IOException {
		final int value = input.read();
		if (value < 0)
			throw new EOFException("Unexpected end of geodata file");
		return value;
	}

	private static short readShortLE(final InputStream input) throws IOException {
		final int low = readUnsignedByte(input);
		final int high = readUnsignedByte(input);
		return (short) (low | high << 8);
	}

	private static void skipFully(final InputStream input, long bytes) throws IOException {
		while (bytes > 0) {
			final long skipped = input.skip(bytes);
			if (skipped > 0) {
				bytes -= skipped;
				continue;
			}
			readUnsignedByte(input);
			bytes--;
		}
	}
}
