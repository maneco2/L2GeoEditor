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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Reads the current Windows process working set shown by Task Manager. @author Maneco2 @version 1.0 */
public final class WindowsProcessMemory {
	private static final int PROCESS_MEMORY_COUNTERS_SIZE = 72;
	private static final long WORKING_SET_OFFSET = 16L;
	private static final MemoryReader READER = createReader();

	private WindowsProcessMemory() {
	}

	public static long residentBytes() {
		try {
			return Math.max(0L, READER.read());
		} catch (final Throwable exception) {
			return 0L;
		}
	}

	private static MemoryReader createReader() {
		if (!System.getProperty("os.name", "").toLowerCase().contains("windows"))
			return () -> 0L;
		try {
			final Linker linker = Linker.nativeLinker();
			final SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
			final SymbolLookup psapi = SymbolLookup.libraryLookup("psapi.dll", Arena.global());
			final MethodHandle getCurrentProcess = linker.downcallHandle(
					kernel32.find("GetCurrentProcess").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS));
			final MemorySegment memoryInfoSymbol = kernel32.find("K32GetProcessMemoryInfo")
					.or(() -> psapi.find("GetProcessMemoryInfo")).orElseThrow();
			final MethodHandle getProcessMemoryInfo = linker.downcallHandle(
					memoryInfoSymbol, FunctionDescriptor.of(ValueLayout.JAVA_INT,
							ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
			return () -> {
				try (Arena arena = Arena.ofConfined()) {
					final MemorySegment counters = arena.allocate(PROCESS_MEMORY_COUNTERS_SIZE, 8L);
					counters.set(ValueLayout.JAVA_INT, 0L, PROCESS_MEMORY_COUNTERS_SIZE);
					final MemorySegment process = (MemorySegment) getCurrentProcess.invoke();
					final int success = (int) getProcessMemoryInfo.invoke(process, counters,
							PROCESS_MEMORY_COUNTERS_SIZE);
					return success == 0 ? 0L : counters.get(ValueLayout.JAVA_LONG, WORKING_SET_OFFSET);
				}
			};
		} catch (final Throwable exception) {
			return () -> 0L;
		}
	}

	@FunctionalInterface
	private interface MemoryReader {
		long read() throws Throwable;
	}
}
