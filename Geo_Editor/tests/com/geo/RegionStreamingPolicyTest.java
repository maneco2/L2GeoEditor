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
public final class RegionStreamingPolicyTest {
	public static void main(final String[] args) {
		check(!RegionStreamingPolicy.shouldLoadNeighbors(false, false), "disabled");
		check(RegionStreamingPolicy.shouldLoadNeighbors(false, true), "auxiliary visualization");
		check(RegionStreamingPolicy.shouldLoadNeighbors(true, false), "full map streaming");
		check(RegionStreamingPolicy.shouldLoadNeighbors(true, true), "both enabled");
		check(!RegionStreamingPolicy.shouldTransitionRegion(false), "auxiliary must not transition");
		check(RegionStreamingPolicy.shouldTransitionRegion(true), "full map transitions");
		System.out.println("RegionStreamingPolicyTest OK");
	}

	private static void check(final boolean condition, final String name) {
		if (!condition)
			throw new AssertionError(name);
	}
}
