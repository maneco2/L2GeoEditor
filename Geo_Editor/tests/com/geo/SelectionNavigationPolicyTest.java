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
public final class SelectionNavigationPolicyTest {
	public static void main(final String[] args) {
		check(SelectionNavigationPolicy.nextCoordinate(100, 1, GeoEngine.GEO_BLOCK_TYPE_FLAT) == 108,
				"flat east crosses the whole block");
		check(SelectionNavigationPolicy.nextCoordinate(100, -1, GeoEngine.GEO_BLOCK_TYPE_FLAT) == 99,
				"flat west enters the previous block");
		check(SelectionNavigationPolicy.nextCoordinate(100, 1, GeoEngine.GEO_BLOCK_TYPE_COMPLEX) == 101,
				"complex moves one cell");
		check(SelectionNavigationPolicy.nextCoordinate(100, -1, GeoEngine.GEO_BLOCK_TYPE_MULTILAYER) == 99,
				"multilayer moves one cell");
		check(SelectionNavigationPolicy.isHeightAllowed(100, 130, 30), "height limit is inclusive");
		check(!SelectionNavigationPolicy.isHeightAllowed(100, 131, 30), "height above limit is rejected");
		System.out.println("SelectionNavigationPolicyTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
