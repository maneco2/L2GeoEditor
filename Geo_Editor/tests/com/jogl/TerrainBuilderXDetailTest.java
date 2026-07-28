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
package com.jogl;

/**
 * Verifies the shared-height cache used by detailed auxiliary terrains.
 *
 * @author Maneco2
 * @version 1.0
 */
public final class TerrainBuilderXDetailTest {
	private TerrainBuilderXDetailTest() {
	}

	public static void main(final String[] args) {
		final int width = 4;
		final short[] heights = {
				64, 48, 32, 16,
				80, 40, 24, 8,
				96, 56, 20, 4,
				112, 72, 36, 12
		};

		for (int y = 0; y < width; y++) {
			for (int x = 0; x < width; x++) {
				final int expected = legacyMinimum(heights, x, y, width);
				final int actual = GLTerrain.TerrainBuilderXDetail.getVertexHeight(heights, x, y, width);
				check(actual == expected, "shared height at " + x + "," + y);
			}
		}

		System.out.println("TerrainBuilderXDetailTest passed");
	}

	private static int legacyMinimum(final short[] heights, final int x, final int y, final int width) {
		int minimum = Integer.MAX_VALUE;
		for (int offsetY = -1; offsetY <= 0; offsetY++) {
			for (int offsetX = -1; offsetX <= 0; offsetX++) {
				final int sampleX = x + offsetX;
				final int sampleY = y + offsetY;
				if (sampleX >= 0 && sampleY >= 0 && sampleX < width && sampleY < width)
					minimum = Math.min(minimum, heights[sampleY * width + sampleX]);
			}
		}
		return minimum;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
