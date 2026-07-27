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
package com;

/** Checks persisted selection-height normalization. @author Maneco2 @version 1.0 */
public final class ConfigTest {
	public static void main(final String[] args) {
		check(Config.normalizeSelectionMoveMaxZ(0) == 0, "zero remains disabled");
		check(Config.normalizeSelectionMoveMaxZ(8) == 8, "eight remains eight");
		check(Config.normalizeSelectionMoveMaxZ(30) == 32, "legacy thirty migrates to thirty-two");
		check(Config.normalizeSelectionMoveMaxZ(31) == 32, "thirty-one rounds to thirty-two");
		check(Config.normalizeSelectionMoveMaxZ(32767) == 32760, "maximum is a multiple of eight");
		check(Config.normalizeCameraSpeed(10) == 25, "minimum camera speed");
		check(Config.normalizeCameraSpeed(112) == 100, "camera speed rounds to twenty-five");
		check(Config.normalizeCameraSpeed(500) == 400, "maximum camera speed");
		System.out.println("ConfigTest passed");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
