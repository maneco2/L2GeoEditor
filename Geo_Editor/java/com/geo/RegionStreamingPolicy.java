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

/**
 * Defines the independent visualization and navigation responsibilities.
 *
 * @author Maneco2
 * @version 1.0
 */
public final class RegionStreamingPolicy {
	private RegionStreamingPolicy() {
	}

	public static boolean shouldLoadNeighbors(final boolean fullMap, final boolean auxiliaryTerrains) {
		return fullMap || auxiliaryTerrains;
	}

	public static boolean shouldTransitionRegion(final boolean fullMap) {
		return fullMap;
	}
}
