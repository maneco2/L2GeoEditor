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

/** Rules shared by keyboard cell-selection navigation and its tests.
 * @author Maneco2
 * @version 1.0
 */
public final class SelectionNavigationPolicy {
	private SelectionNavigationPolicy() {
	}

	public static int nextCoordinate(final int coordinate, final int direction, final byte blockType) {
		if (direction == 0)
			return coordinate;
		if (blockType != GeoEngine.GEO_BLOCK_TYPE_FLAT)
			return coordinate + direction;
		return direction > 0 ? coordinate + GeoEngine.GEO_BLOCK_SHIFT : coordinate - 1;
	}

	public static boolean isHeightAllowed(final int sourceHeight, final int targetHeight, final int maxDifference) {
		return Math.abs((long) targetHeight - sourceHeight) <= Math.max(0, maxDifference);
	}
}
