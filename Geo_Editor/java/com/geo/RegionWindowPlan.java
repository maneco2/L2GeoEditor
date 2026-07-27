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

/** Discrete square-region coverage used by auxiliary streaming. @author Maneco2 @version 1.0 */
public final class RegionWindowPlan {
	public static final int[] NEIGHBOR_OPTIONS = { 0, 8, 24, 48, 80, 120 };

	private RegionWindowPlan() {
	}

	public static int normalizeNeighborCount(final int count) {
		// Migrate the former 1..8 slider without unexpectedly disabling neighbors.
		if (count > 0 && count < 8)
			return 8;
		int nearest = NEIGHBOR_OPTIONS[0];
		int distance = Math.abs(count - nearest);
		for (final int option : NEIGHBOR_OPTIONS) {
			final int optionDistance = Math.abs(count - option);
			if (optionDistance < distance) {
				nearest = option;
				distance = optionDistance;
			}
		}
		return nearest;
	}

	public static int radiusForNeighborCount(final int count) {
		final int normalized = normalizeNeighborCount(count);
		for (int radius = 0; radius < NEIGHBOR_OPTIONS.length; radius++)
			if (((radius * 2 + 1) * (radius * 2 + 1)) - 1 == normalized)
				return radius;
		throw new IllegalArgumentException("Unsupported neighbor count: " + count);
	}

	public static int neighborCountForRadius(final int radius) {
		if (radius < 0 || radius >= NEIGHBOR_OPTIONS.length)
			throw new IllegalArgumentException("Unsupported radius: " + radius);
		return ((radius * 2 + 1) * (radius * 2 + 1)) - 1;
	}

	/** Returns whether a region is beyond every region covered by the current window. */
	public static boolean isOutsideWindow(final int centerX, final int centerY, final int regionX,
			final int regionY, final int radius) {
		if (centerX == Integer.MIN_VALUE || centerY == Integer.MIN_VALUE)
			return true;
		return Math.max(Math.abs(regionX - centerX), Math.abs(regionY - centerY)) > Math.max(0, radius);
	}
}
