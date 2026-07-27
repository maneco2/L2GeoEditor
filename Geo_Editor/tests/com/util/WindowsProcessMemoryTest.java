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

/** Verifies the Windows working-set reader used by the HUD. @author Maneco2 @version 1.0 */
public final class WindowsProcessMemoryTest {
	public static void main(final String[] args) {
		final long residentBytes = WindowsProcessMemory.residentBytes();
		if (System.getProperty("os.name", "").toLowerCase().contains("windows") && residentBytes <= 0L)
			throw new AssertionError("Windows process working set was not available");
		System.out.println("WindowsProcessMemoryTest OK - residentMb=" + MemoryUsage.toMegabytes(residentBytes));
	}
}
