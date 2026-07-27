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

import com.jogl.shader.GLUniform;

import java.nio.IntBuffer;

import javax.media.opengl.GL2;

import com.jogamp.common.nio.Buffers;

/** Buffered ivec2 array uniform. @author Maneco2 @version 1.0 */
public final class GLUniformVec2iv extends GLUniform {
	private final IntBuffer _buffer;

	public GLUniformVec2iv(final String uniformName, final int numElements) {
		super(uniformName);
		_buffer = Buffers.newDirectIntBuffer(numElements * 2);
	}

	public void clear() {
		_buffer.clear();
	}

	public void put(final int x, final int y) {
		_buffer.put(x).put(y);
	}

	public boolean hasRemaining() {
		return _buffer.remaining() >= 2;
	}

	public boolean isEmpty() {
		return _buffer.position() == 0;
	}

	/** Returns the number of complete ivec2 elements available after flip. */
	public int getRemaining() {
		return _buffer.remaining() / 2;
	}

	@Override
	public void update(final GL2 gl) {
		_buffer.flip();
		gl.glUniform2iv(getUniformLocation(), _buffer.remaining() / 2, _buffer);
	}
}
