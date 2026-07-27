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

/** Describes Java heap usage without confusing it with total process memory. @author Maneco2 @version 1.0 */
public final class MemoryUsage {
	private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

	public record Snapshot(long usedBytes, long committedBytes, long maxBytes, long processResidentBytes) { }

	private MemoryUsage() {
	}

	public static Snapshot capture() {
		final Runtime runtime = Runtime.getRuntime();
		return new Snapshot(runtime.totalMemory() - runtime.freeMemory(), runtime.totalMemory(), runtime.maxMemory(),
				WindowsProcessMemory.residentBytes());
	}

	public static String formatHud(final Snapshot memory) {
		final long displayedBytes = memory.processResidentBytes() > 0L ? memory.processResidentBytes()
				: memory.usedBytes();
		return "Memory: " + toMegabytes(displayedBytes) + "mb";
	}

	public static long toMegabytes(final long bytes) {
		return Math.max(0L, bytes) / BYTES_PER_MEGABYTE;
	}
}
