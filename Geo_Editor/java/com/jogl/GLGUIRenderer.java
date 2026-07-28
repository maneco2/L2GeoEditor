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

import com.Config;
import com.geo.GeoEngine;
import com.util.FastArrayList;

import java.io.File;
import java.awt.Color;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import javax.media.opengl.GL2;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;
import com.jogamp.opengl.util.gl2.GLUT;

/**
 * L2J project source.
 * 
 * @author Forsaiken aka Patrick
 */
public final class GLGUIRenderer {
	public static final int TEXT_HEIGHT = 16;
	public static final int COMPASS_HEIGHT = 96;
	public static final int COMPASS_HALF_HEIGHT = COMPASS_HEIGHT / 2;
	public static final Charset UTF_8 = Charset.forName("UTF-8");

	private final FastArrayList<GLText> _texts;

	private int _listId;
	private Texture _fontTexture;
	private Texture _regionMapTexture;
	private Texture _playerMarkerTexture;
	private String _regionMapName;
	private float _regionMapAlpha = 1f;
	private final GLUT _glut = new GLUT();

	public GLGUIRenderer() {
		_texts = new FastArrayList<>();
	}

	public final GLText newText(final int x, final int y) {
		final GLText text = new GLText(x, y);
		_texts.add(text);
		return text;
	}

	private static final int FONT_TEX_ROWS_COLS = 16;
	private static final float FONT_TEX_BLOCK = 1f / FONT_TEX_ROWS_COLS;

	public final void init(final GL2 gl) {
		_listId = gl.glGenLists(257); // Creating 256 Display Lists
		for (int loop = 0; loop < 256; loop++) // Loop Through All 256 Lists
		{
			final float u1 = (loop % FONT_TEX_ROWS_COLS) * FONT_TEX_BLOCK;
			final float u2 = u1 + FONT_TEX_BLOCK;
			final float v1 = (loop / FONT_TEX_ROWS_COLS) * FONT_TEX_BLOCK;
			final float v2 = v1 + FONT_TEX_BLOCK;

			gl.glNewList(_listId + loop, GL2.GL_COMPILE);
			gl.glBegin(GL2.GL_TRIANGLE_STRIP);
			gl.glTexCoord2f(u1, v1);
			gl.glVertex3f(0.0f, TEXT_HEIGHT, 0.0f);
			gl.glTexCoord2f(u1, v2);
			gl.glVertex3f(0.0f, 0.0f, 0.0f);
			gl.glTexCoord2f(u2, v1);
			gl.glVertex3f(TEXT_HEIGHT, TEXT_HEIGHT, 0.0f);
			gl.glTexCoord2f(u2, v2);
			gl.glVertex3f(TEXT_HEIGHT, 0.0f, 0.0f);
			gl.glEnd();
			gl.glTranslatef(9f, 0, 0);
			gl.glEndList();
		}

		gl.glNewList(_listId + 256, GL2.GL_COMPILE);
		gl.glBegin(GL2.GL_TRIANGLE_STRIP);
		gl.glTexCoord2f(0.0f, 0.0f);
		gl.glVertex3f(-COMPASS_HALF_HEIGHT, COMPASS_HALF_HEIGHT, 0.0f);
		gl.glTexCoord2f(0.0f, 1.0f);
		gl.glVertex3f(-COMPASS_HALF_HEIGHT, -COMPASS_HALF_HEIGHT, 0.0f);
		gl.glTexCoord2f(1.0f, 0.0f);
		gl.glVertex3f(COMPASS_HALF_HEIGHT, COMPASS_HALF_HEIGHT, 0.0f);
		gl.glTexCoord2f(1.0f, 1.0f);
		gl.glVertex3f(COMPASS_HALF_HEIGHT, -COMPASS_HALF_HEIGHT, 0.0f);
		gl.glEnd();
		gl.glEndList();

		try {
			_fontTexture = TextureIO.newTexture(new File("./data/textures/font.png"), false);
			_fontTexture.enable();
			_fontTexture.setTexParameteri(GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
			_fontTexture.setTexParameteri(GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);

			_playerMarkerTexture = TextureIO.newTexture(new File("./data/icon/minimap-player-marker.png"), true);
			_playerMarkerTexture.setTexParameteri(GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
			_playerMarkerTexture.setTexParameteri(GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	public final void render(final GL2 gl) {
		GLState.setBlendEnabled(gl, true);
		// The cache may survive a pass rendered by another subsystem. Force the
		// real OpenGL state before drawing the opaque-black font atlas additively.
		gl.glBlendFunc(GL2.GL_ONE, GL2.GL_ONE);
		GLState.setBlendFunc(gl, GL2.GL_ONE, GL2.GL_ONE);
		GLState.setDepthTestEnabled(gl, false);
		gl.glColor4f(1f, 1f, 1f, 1f);
		GLState.glColor4f(gl, GLColor.WHITE);

		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPushMatrix();
		gl.glLoadIdentity();
		gl.glOrtho(0, GLDisplay.getInstance().getWidth(), 0, GLDisplay.getInstance().getHeight(), -1, 1);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glLoadIdentity();
		gl.glListBase(_listId - 32 + (128 * 0));

		gl.glEnable(GL2.GL_TEXTURE_2D);
		_fontTexture.enable();
		_fontTexture.bind();

		ByteBuffer buffer;
		GLText gltext;
		for (int i = _texts.size(); i-- > 0;) {
			gltext = _texts.getUnsafe(i);
			buffer = gltext.getBuffer();
			buffer.rewind();
			if (buffer.hasRemaining()) {
				gl.glPushMatrix();
				gl.glTranslatef(gltext.getX(), gltext.getY(), 0);
				gl.glCallLists(buffer.remaining(), GL2.GL_BYTE, buffer);
				gl.glPopMatrix();
			}
		}
		gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		GLState.setBlendFunc(gl, GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

		gl.glListBase(0);
		renderRegionMiniMap(gl);

		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopMatrix();
	}

	public final void dispose(final GL2 gl) {
		gl.glDeleteLists(_listId, 256);
		_fontTexture.destroy(gl);
		if (_regionMapTexture != null)
			_regionMapTexture.destroy(gl);
		if (_playerMarkerTexture != null)
			_playerMarkerTexture.destroy(gl);
	}

	private final void renderRegionMiniMap(final GL2 gl) {
		final com.geo.GeoRegion region = GeoEngine.getInstance().getActiveRegion();
		if (region == null)
			return;
		final String name = region.getName();
		if (!_regionMapNameEquals(name)) {
			if (_regionMapTexture != null)
				_regionMapTexture.destroy(gl);
			_regionMapTexture = null;
			_regionMapAlpha = 0f;
			try {
				_regionMapTexture = TextureIO.newTexture(new File("./data/textures/region/" + name + ".jpg"), false);
				_regionMapTexture.setTexParameteri(GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
				_regionMapTexture.setTexParameteri(GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
				_regionMapName = name;
			} catch (final Exception ignored) {
				_regionMapName = name;
			}
		}
		if (_regionMapTexture == null)
			return;
		final Rectangle bounds = MiniMapLayout.calculateBounds(GLDisplay.getInstance().getWidth(),
				GLDisplay.getInstance().getHeight(), Config.MINI_MAP_SIZE, Config.MINI_MAP_MARGIN);
		final int size = bounds.width;
		if (size <= 0)
			return;
		final int left = bounds.x;
		final int bottom = bounds.y;
		GLState.setBlendEnabled(gl, true);
		GLState.setBlendFunc(gl, GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		_regionMapTexture.enable();
		_regionMapTexture.bind();
		_regionMapAlpha = Math.min(1f, _regionMapAlpha + Config.MINI_MAP_FADE_STEP);
		gl.glColor4f(1f, 1f, 1f, _regionMapAlpha);
		gl.glBegin(GL2.GL_QUADS);
		gl.glTexCoord2f(0f, 1f); gl.glVertex2i(left, bottom);
		gl.glTexCoord2f(1f, 1f); gl.glVertex2i(left + size, bottom);
		gl.glTexCoord2f(1f, 0f); gl.glVertex2i(left + size, bottom + size);
		gl.glTexCoord2f(0f, 0f); gl.glVertex2i(left, bottom + size);
		gl.glEnd();
		_regionMapTexture.disable();
		gl.glDisable(GL2.GL_TEXTURE_2D);
		GLState.glColor4f(gl, new GLColor(new Color(90, 190, 255)));
		gl.glLineWidth(1f);
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glVertex2i(left, bottom); gl.glVertex2i(left + size, bottom);
		gl.glVertex2i(left + size, bottom + size); gl.glVertex2i(left, bottom + size);
		gl.glEnd();

		final GLCamera camera = GLDisplay.getInstance().getCamera();
		final int blockX = GeoEngine.getBlockXY(camera.getGeoX());
		final int blockY = GeoEngine.getBlockXY(camera.getGeoY());
		final float markerX = left + (blockX / 256f) * size;
		final float markerY = bottom + size - (blockY / 256f) * size;
		final double direction = Math.toRadians(camera.getRotY());
		GLState.setBlendEnabled(gl, true);
		GLState.setBlendFunc(gl, GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		if (_playerMarkerTexture != null) {
			final float markerWidth = 18f;
			final float markerHeight = 22f;
			_playerMarkerTexture.enable();
			_playerMarkerTexture.bind();
			GLState.glColor4f(gl, GLColor.WHITE);
			gl.glDisable(GL2.GL_CULL_FACE);
			gl.glPushMatrix();
			gl.glTranslatef(markerX, markerY, 0f);
			gl.glRotatef((float) Math.toDegrees(direction), 0f, 0f, 1f);
			gl.glBegin(GL2.GL_QUADS);
			gl.glTexCoord2f(0f, 1f); gl.glVertex2f(-markerWidth / 2f, -markerHeight / 2f);
			gl.glTexCoord2f(1f, 1f); gl.glVertex2f(markerWidth / 2f, -markerHeight / 2f);
			gl.glTexCoord2f(1f, 0f); gl.glVertex2f(markerWidth / 2f, markerHeight / 2f);
			gl.glTexCoord2f(0f, 0f); gl.glVertex2f(-markerWidth / 2f, markerHeight / 2f);
			gl.glEnd();
			gl.glPopMatrix();
			gl.glEnable(GL2.GL_CULL_FACE);
			_playerMarkerTexture.disable();
		}
		GLState.setBlendFunc(gl, GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		GLState.glColor4f(gl, new GLColor(Color.YELLOW));
		gl.glRasterPos2f(left + 4f, bottom - 16f);
		_glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12, name);
	}

	private final boolean _regionMapNameEquals(final String name) {
		return name.equals(_regionMapName);
	}

	public static final class GLText {
		private final ByteBuffer _buffer;
		private final int _x;
		private final int _y;

		public GLText(final int x, final int y) {
			_buffer = Buffers.newDirectByteBuffer(256);
			_x = x;
			_y = y;
		}

		public final int getX() {
			return _x;
		}

		public final int getY() {
			return _y;
		}

		public final void setText(final String text) {
			if (text != null && text.length() > _buffer.capacity())
				throw new IllegalArgumentException("Text is too long");

			_buffer.clear();
			if (text != null)
				_buffer.put(text.getBytes(UTF_8));
			_buffer.flip();
		}

		public final ByteBuffer getBuffer() {
			return _buffer;
		}
	}
}
