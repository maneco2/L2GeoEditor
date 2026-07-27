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
import com.geo.GeoDataOverview;
import com.geo.GeoRegion;
import com.jogamp.opengl.util.gl2.GLUT;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.media.opengl.GL2;

/**
 * Renders a real global geodata overview without loading all regions into the
 * editor's detailed in-memory model.
 * @author Maneco2
 * @version 1.0
 */
public final class GLGlobalMapRenderer {
	private static final int REGION_MIN = GeoDataOverview.REGION_MIN;
	private static final int REGION_COUNT_X = GeoDataOverview.REGION_COUNT_X;
	private static final int REGION_COUNT_Y = GeoDataOverview.REGION_COUNT_Y;

	private Texture _geoTexture;
	private volatile BufferedImage _overviewImage;
	private volatile boolean _loadStarted;
	private volatile String _status = "Loading geodata overview...";
	private final ExecutorService _loaderExecutor = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "GeoDataOverviewLoader");
		thread.setDaemon(true);
		return thread;
	});
	private Future<?> _loader;
	private final GLUT _glut = new GLUT();
	private float _mapRatio = (float) GeoDataOverview.WIDTH / GeoDataOverview.HEIGHT;
	private int _lastLeft;
	private int _lastTop;
	private int _lastWidth;
	private int _lastHeight;

	public final void init(final GL2 gl) {
		if (!_loadStarted) {
			_loadStarted = true;
			_loader = _loaderExecutor.submit(() -> {
				try {
					_overviewImage = GeoDataOverview.scan(new java.io.File(Config.PATH_TO_GEO_FILES),
							(completed, total) -> _status = "Scanning geodata: " + completed + "/" + total + " regions");
					_status = "Geodata overview ready";
				} catch (final Exception e) {
					if (Thread.currentThread().isInterrupted()) {
						Thread.currentThread().interrupt();
						_status = "Geodata overview cancelled";
					} else {
						_status = "Geodata overview unavailable: " + e.getMessage();
					}
					System.err.println(_status);
				}
			});
		}

		final BufferedImage image = _overviewImage;
		if (_geoTexture == null && image != null) {
			_mapRatio = (float) image.getWidth() / image.getHeight();
			_geoTexture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);
			_geoTexture.setTexParameteri(GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
			_geoTexture.setTexParameteri(GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
		}
	}

	public final void render(final GL2 gl, final GeoRegion activeRegion, final int width, final int height) {
		if (width <= 0 || height <= 0)
			return;

		final int drawWidth = Math.min(width - 24, Math.round((height - 24) * _mapRatio));
		final int drawHeight = Math.min(height - 24, Math.round((width - 24) / _mapRatio));
		_lastWidth = Math.max(1, drawWidth);
		_lastHeight = Math.max(1, drawHeight);
		_lastLeft = (width - _lastWidth) / 2;
		_lastTop = (height - _lastHeight) / 2;
		final int bottom = height - _lastTop - _lastHeight;

		GLState.setDepthTestEnabled(gl, false);
		GLState.setBlendEnabled(gl, false);
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPushMatrix();
		gl.glLoadIdentity();
		gl.glOrtho(0, width, 0, height, -1, 1);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glLoadIdentity();

		if (_geoTexture != null) {
			_geoTexture.enable();
			_geoTexture.bind();
			GLState.glColor4f(gl, GLColor.WHITE);
			gl.glBegin(GL2.GL_QUADS);
			gl.glTexCoord2f(0f, 1f); gl.glVertex2i(_lastLeft, bottom);
			gl.glTexCoord2f(1f, 1f); gl.glVertex2i(_lastLeft + _lastWidth, bottom);
			gl.glTexCoord2f(1f, 0f); gl.glVertex2i(_lastLeft + _lastWidth, bottom + _lastHeight);
			gl.glTexCoord2f(0f, 0f); gl.glVertex2i(_lastLeft, bottom + _lastHeight);
			gl.glEnd();
			_geoTexture.disable();
		} else {
			gl.glDisable(GL2.GL_TEXTURE_2D);
			GLState.glColor4f(gl, new GLColor(new Color(27, 32, 38)));
			gl.glBegin(GL2.GL_QUADS);
			gl.glVertex2i(_lastLeft, bottom);
			gl.glVertex2i(_lastLeft + _lastWidth, bottom);
			gl.glVertex2i(_lastLeft + _lastWidth, bottom + _lastHeight);
			gl.glVertex2i(_lastLeft, bottom + _lastHeight);
			gl.glEnd();
		}

		final float tileWidth = _lastWidth / (float) REGION_COUNT_X;
		final float tileHeight = _lastHeight / (float) REGION_COUNT_Y;
		gl.glDisable(GL2.GL_TEXTURE_2D);
		GLState.glColor4f(gl, new GLColor(new Color(255, 255, 255, 190)));
		gl.glBegin(GL2.GL_LINES);
		for (int column = 0; column <= REGION_COUNT_X; column++) {
			final float x = _lastLeft + column * tileWidth;
			gl.glVertex2f(x, bottom);
			gl.glVertex2f(x, bottom + _lastHeight);
		}
		for (int row = 0; row <= REGION_COUNT_Y; row++) {
			final float y = bottom + row * tileHeight;
			gl.glVertex2f(_lastLeft, y);
			gl.glVertex2f(_lastLeft + _lastWidth, y);
		}
		gl.glEnd();

		GLState.glColor4f(gl, GLColor.WHITE);
		for (int row = 0; row < REGION_COUNT_Y; row++) {
			for (int column = 0; column < REGION_COUNT_X; column++) {
				final float labelX = _lastLeft + column * tileWidth + 3f;
				final float labelY = bottom + _lastHeight - row * tileHeight - 13f;
				gl.glRasterPos2f(labelX, labelY);
				_glut.glutBitmapString(GLUT.BITMAP_HELVETICA_10,
						(REGION_MIN + column) + "_" + (REGION_MIN + row));
			}
		}

		if (activeRegion != null) {
			final float left = _lastLeft + (activeRegion.getRegionX() - REGION_MIN) * tileWidth;
			final float top = bottom + _lastHeight - (activeRegion.getRegionY() - REGION_MIN + 1) * tileHeight;
			GLState.glColor4f(gl, new GLColor(Color.YELLOW));
			gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_LINE);
			gl.glLineWidth(3f);
			gl.glBegin(GL2.GL_QUADS);
			gl.glVertex2f(left, top); gl.glVertex2f(left + tileWidth, top);
			gl.glVertex2f(left + tileWidth, top + tileHeight); gl.glVertex2f(left, top + tileHeight);
			gl.glEnd();
			gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_FILL);
		}

		if (_geoTexture == null) {
			GLState.glColor4f(gl, GLColor.WHITE);
			gl.glRasterPos2f(_lastLeft + 12f, bottom + 12f);
			_glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12, _status);
		}

		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopMatrix();
	}

	public final int[] regionAt(final int x, final int y, final int height) {
		if (_lastWidth <= 0 || x < _lastLeft || x >= _lastLeft + _lastWidth)
			return null;
		if (y < _lastTop || y >= _lastTop + _lastHeight)
			return null;
		final int regionX = REGION_MIN + (x - _lastLeft) * REGION_COUNT_X / _lastWidth;
		final int row = (y - _lastTop) * REGION_COUNT_Y / _lastHeight;
		final int regionY = REGION_MIN + Math.max(0, Math.min(REGION_COUNT_Y - 1, row));
		return new int[] { regionX, regionY };
	}

	public final void dispose(final GL2 gl) {
		if (_loader != null)
			_loader.cancel(true);
		_loaderExecutor.shutdownNow();
		if (_geoTexture != null) {
			_geoTexture.destroy(gl);
			_geoTexture = null;
		}
		_overviewImage = null;
		_loadStarted = false;
		_loader = null;
	}
}
