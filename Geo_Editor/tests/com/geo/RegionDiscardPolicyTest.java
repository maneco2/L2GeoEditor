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

/** Deterministic checks for incremental renderer disposal. @author Maneco2 @version 1.0 */
public final class RegionDiscardPolicyTest {
	public static void main(final String[] args) {
		check(RegionDiscardPolicy.frameBudget(8) == 16, "small queue");
		check(RegionDiscardPolicy.frameBudget(24) == 8, "medium queue");
		check(RegionDiscardPolicy.frameBudget(80) == 4, "large queue");
		check(RegionDiscardPolicy.frameBudget(120) == 4, "maximum queue");
		System.out.println("RegionDiscardPolicyTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
