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
package com.jogl.shader.uniform;

/** Verifies ivec2 batching capacity without an OpenGL context. @author Maneco2 @version 1.0 */
public final class GLUniformVec2ivTest {
	public static void main(final String[] args) {
		final GLUniformVec2iv uniform = new GLUniformVec2iv("test", 64);
		check(uniform.isEmpty(), "new uniform empty");
		for (int i = 0; i < 64; i++) {
			check(uniform.hasRemaining(), "capacity before element " + i);
			uniform.put(i, i * 2);
		}
		check(!uniform.hasRemaining(), "full uniform has no complete element remaining");
		check(!uniform.isEmpty(), "filled uniform not empty");
		uniform.clear();
		check(uniform.isEmpty(), "clear resets uniform");
		check(uniform.hasRemaining(), "clear restores capacity");
		System.out.println("GLUniformVec2ivTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
