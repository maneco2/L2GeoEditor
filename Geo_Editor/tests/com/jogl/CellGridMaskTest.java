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

public final class CellGridMaskTest {
	public static void main(final String[] args) {
		check(CellGridMask.contains(0, 0, 0, 0, 0), "center");
		check(CellGridMask.contains(2, 0, 0, 0, 2), "edge");
		check(!CellGridMask.contains(2, 2, 0, 0, 2), "corner outside circle");
		check(!CellGridMask.contains(0, 0, 0, 0, -1), "negative radius");
		check(CellGridMask.containsCellCenter(0, 0, 0, 0, 1), "cell center inside");
		check(!CellGridMask.containsCellCenter(2, 2, 0, 0, 2), "cell center outside");
		System.out.println("CellGridMaskTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
