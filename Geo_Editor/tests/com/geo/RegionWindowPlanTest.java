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

/** @author Maneco2 @version 1.0 */
public final class RegionWindowPlanTest {
	public static void main(final String[] args) {
		for (int radius = 1; radius <= 5; radius++) {
			final int count = RegionWindowPlan.neighborCountForRadius(radius);
			check(RegionWindowPlan.radiusForNeighborCount(count) == radius, "radius " + radius);
			check(RegionWindowPlan.optionIndexForNeighborCount(count) == radius - 1,
					"option index " + radius);
		}
		check(RegionWindowPlan.normalizeNeighborCount(0) == 8, "zero migrates to eight");
		check(RegionWindowPlan.normalizeNeighborCount(2) == 8, "legacy positive value migrates to eight");
		check(RegionWindowPlan.normalizeNeighborCount(7) == 8, "nearest eight");
		check(!RegionWindowPlan.isOutsideWindow(19, 13, 20, 13, 2), "inner region");
		check(!RegionWindowPlan.isOutsideWindow(19, 13, 21, 13, 2), "east edge remains loaded");
		check(!RegionWindowPlan.isOutsideWindow(19, 13, 17, 11, 2), "corner edge remains loaded");
		check(RegionWindowPlan.isOutsideWindow(19, 13, 22, 13, 2), "first region beyond east edge");
		check(RegionWindowPlan.isOutsideWindow(Integer.MIN_VALUE, Integer.MIN_VALUE, 19, 13, 2),
				"uninitialized window");
		System.out.println("RegionWindowPlanTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
