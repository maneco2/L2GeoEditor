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
package com.util;

/** Deterministic checks for the region-window reclamation policy. @author Maneco2 @version 1.0 */
public final class MemoryReclaimerTest {
	public static void main(final String[] args) {
		check(!MemoryReclaimer.shouldCollect(MemoryReclaimer.COLLECTION_THRESHOLD_BYTES - 1, 20_000_000_000L,
				Long.MIN_VALUE), "small heaps must not trigger collection");
		check(MemoryReclaimer.shouldCollect(MemoryReclaimer.COLLECTION_THRESHOLD_BYTES, 20_000_000_000L,
				Long.MIN_VALUE), "large discarded windows must trigger collection");
		check(!MemoryReclaimer.shouldCollect(MemoryReclaimer.COLLECTION_THRESHOLD_BYTES, 20_000_000_000L,
				15_000_000_000L), "collection requests must be rate limited");
		check(MemoryReclaimer.shouldCollect(MemoryReclaimer.COLLECTION_THRESHOLD_BYTES, 25_000_000_000L,
				15_000_000_000L), "collection must resume after the cooldown");
		check(MemoryReclaimer.shouldCollect(true, 1L, 25_000_000_000L, 15_000_000_000L),
				"large window reduction forces collection after cooldown");
		check(!MemoryReclaimer.shouldCollect(true, 1L, 20_000_000_000L, 15_000_000_000L),
				"forced collection still respects cooldown");
		System.out.println("MemoryReclaimerTest passed");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
