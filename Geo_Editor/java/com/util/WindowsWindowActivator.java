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
import java.nio.charset.StandardCharsets;

/** Restores and activates the editor window after the fullscreen splash closes. */
public final class WindowsWindowActivator {
	private static final int SW_SHOW = 5;

	private WindowsWindowActivator() {
	}

	public static void activate(final String windowTitle) {
		if (!System.getProperty("os.name", "").startsWith("Windows"))
			return;
		try {
			final Linker linker = Linker.nativeLinker();
			final SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
			final SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
			final MethodHandle findWindow = downcall(linker, user32, "FindWindowW",
					FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
			final MethodHandle getForegroundWindow = downcall(linker, user32, "GetForegroundWindow",
					FunctionDescriptor.of(ValueLayout.ADDRESS));
			final MethodHandle getWindowThreadProcessId = downcall(linker, user32, "GetWindowThreadProcessId",
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
			final MethodHandle getCurrentThreadId = downcall(linker, kernel32, "GetCurrentThreadId",
					FunctionDescriptor.of(ValueLayout.JAVA_INT));
			final MethodHandle attachThreadInput = downcall(linker, user32, "AttachThreadInput",
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
							ValueLayout.JAVA_INT));
			final MethodHandle showWindow = downcall(linker, user32, "ShowWindow",
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
			final MethodHandle bringWindowToTop = downcall(linker, user32, "BringWindowToTop",
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
			final MethodHandle setForegroundWindow = downcall(linker, user32, "SetForegroundWindow",
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
			final MethodHandle flashWindowEx = downcall(linker, user32, "FlashWindowEx",
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

			try (Arena arena = Arena.ofConfined()) {
				final byte[] titleBytes = (windowTitle + "\0").getBytes(StandardCharsets.UTF_16LE);
				final MemorySegment nativeTitle = arena.allocate(titleBytes.length, 2);
				nativeTitle.copyFrom(MemorySegment.ofArray(titleBytes));
				final MemorySegment editor = (MemorySegment) findWindow.invoke(MemorySegment.NULL, nativeTitle);
				if (editor.equals(MemorySegment.NULL))
					return;

				final MemorySegment foreground = (MemorySegment) getForegroundWindow.invoke();
				final int currentThread = (int) getCurrentThreadId.invoke();
				final int foregroundThread = foreground.equals(MemorySegment.NULL) ? 0
						: (int) getWindowThreadProcessId.invoke(foreground, MemorySegment.NULL);
				final boolean attached = foregroundThread != 0 && foregroundThread != currentThread
						&& (int) attachThreadInput.invoke(currentThread, foregroundThread, 1) != 0;
				try {
					showWindow.invoke(editor, SW_SHOW);
					bringWindowToTop.invoke(editor);
					setForegroundWindow.invoke(editor);
					stopAttentionFlash(arena, flashWindowEx, editor);
				} finally {
					if (attached)
						attachThreadInput.invoke(currentThread, foregroundThread, 0);
				}
			}
		} catch (final Throwable throwable) {
			System.err.println("Unable to activate the GeoEditor window: " + throwable.getMessage());
		}
	}

	private static void stopAttentionFlash(final Arena arena, final MethodHandle flashWindowEx,
			final MemorySegment window) throws Throwable {
		// FLASHWINFO on Windows x64: UINT, padding, HWND, DWORD, UINT, DWORD, padding.
		final MemorySegment flashInfo = arena.allocate(32, 8);
		flashInfo.set(ValueLayout.JAVA_INT, 0, 32);
		flashInfo.set(ValueLayout.ADDRESS, 8, window);
		flashInfo.set(ValueLayout.JAVA_INT, 16, 0); // FLASHW_STOP
		flashInfo.set(ValueLayout.JAVA_INT, 20, 0);
		flashInfo.set(ValueLayout.JAVA_INT, 24, 0);
		flashWindowEx.invoke(flashInfo);
	}

	private static MethodHandle downcall(final Linker linker, final SymbolLookup lookup, final String symbol,
			final FunctionDescriptor descriptor) {
		return linker.downcallHandle(lookup.find(symbol)
				.orElseThrow(() -> new IllegalStateException("Windows API not found: " + symbol)), descriptor);
	}
}
