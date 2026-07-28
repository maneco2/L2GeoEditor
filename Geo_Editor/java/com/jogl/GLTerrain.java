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

import com.geo.GeoBlock;
import com.geo.GeoEngine;
import com.geo.GeoRegion;
import com.util.Util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.awt.ImageUtil;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

/**
 * L2J source.
 *
 * @author Forsaiken aka Patrick
 * @author Maneco2
 * @version 1.1
 */
public final class GLTerrain {
	private static volatile int TEXTURE_PROGRESS;
	private static volatile String TEXTURE_STAGE = "idle";
	private static final ThreadPoolExecutor TEXTURE_LOADER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<>(), runnable -> {
				final Thread thread = new Thread(runnable, "TerrainTextureLoader");
				thread.setDaemon(true);
				return thread;
			});

	public static void shutdownTextureLoader() {
		TEXTURE_LOADER.shutdownNow();
		TEXTURE_LOADER.purge();
	}

	public static int getTextureProgress() {
		return TEXTURE_PROGRESS;
	}

	public static String getTextureStage() {
		return TEXTURE_STAGE;
	}

	static final int genTex(final GL2 gl, final int width, final int height, final ShortBuffer buf, int id) {
		if (id == 0) {
			final int[] temp = new int[1];
			gl.glGenTextures(1, temp, 0);
			id = temp[0];
		}

		gl.glBindTexture(GL2.GL_TEXTURE_2D, id);
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, 1, width, height, 0, GL2.GL_RED, GL2.GL_SHORT, buf);
		gl.glTexEnvf(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
		gl.glTexParameterf(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_NEAREST);
		gl.glTexParameterf(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_NEAREST);
		gl.glTexParameterf(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
		gl.glTexParameterf(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
		return id;
	}

	private TerrainBuilder _builder;
	private int _vboIndex;
	private int _vboVertex;
	private int _vboTexture;

	private GeoRegion _region;
	private Texture _texture;
	private volatile Future<TextureImage> _textureLoad;
	private volatile int _textureGeneration;
	private volatile boolean _textureReady;
	private volatile boolean _discardTexture;

	private boolean _needUpdateTexture;
	private boolean _needUpdateVBO;
	private long _lastVboPreparationMicros;
	private long _lastVboUploadMicros;

	private boolean _wireframe;
	private boolean _enabled;
	private boolean _streamingPreview;
	private int _textureMaxSize;
	private final TerrainDetailLevel _detailLevel;

	public GLTerrain() {
		this(TerrainDetailLevel.LOW);
	}

	public GLTerrain(final TerrainDetailLevel detailLevel) {
		_detailLevel = detailLevel == null ? TerrainDetailLevel.LOW : detailLevel;
		_vboIndex = -1;
		_vboVertex = -1;
		_vboTexture = -1;
	}

	public final void setRegion(final GeoRegion region) {
		if (_region != region) {
			_region = region;
			invalidateTexture(true);
			_needUpdateVBO = true;
		}
	}

	public final void setEnabled(final boolean enabled) {
		_enabled = enabled;
	}

	public final GeoRegion getRegion() {
		return _region;
	}

	public final boolean getWireframe() {
		return _wireframe;
	}

	public final void setWireframe(final boolean wireframe) {
		_wireframe = wireframe;
	}

	public record VboUpdateSample(long preparationMicros, long uploadMicros) { }

	/** Returns and clears the latest terrain VBO preparation and GPU upload durations. */
	public final VboUpdateSample consumeLastVboUpdate() {
		final VboUpdateSample sample = new VboUpdateSample(_lastVboPreparationMicros, _lastVboUploadMicros);
		_lastVboPreparationMicros = 0L;
		_lastVboUploadMicros = 0L;
		return sample;
	}

	/** Renders a lightweight geodata-style mesh without loading raster textures. */
	public final void setStreamingPreview(final boolean streamingPreview) {
		if (_streamingPreview == streamingPreview)
			return;
		_streamingPreview = streamingPreview;
		invalidateTexture(!streamingPreview);
	}

	public final void setTextureMaxSize(final int textureMaxSize) {
		if (_textureMaxSize != textureMaxSize) {
			_textureMaxSize = Math.max(0, textureMaxSize);
			invalidateTexture(!_streamingPreview);
		}
	}

	private void invalidateTexture(final boolean reload) {
		TEXTURE_PROGRESS = reload ? 1 : 0;
		TEXTURE_STAGE = reload ? "queued" : "idle";
		_textureGeneration++;
		_textureReady = false;
		_discardTexture = true;
		_needUpdateTexture = reload;

		final Future<TextureImage> textureLoad = _textureLoad;
		_textureLoad = null;
		if (textureLoad != null) {
			textureLoad.cancel(true);
			TEXTURE_LOADER.purge();
		}
	}

	private static TextureImage loadTextureImage(final int regionX, final int regionY, final int textureMaxSize,
			final int generation) {
		File file = new File("./data/textures/region/" + (regionX + 10) + "_" + (regionY + 10) + ".jpg");
		if (!file.isFile())
			file = new File("./data/textures/region/water.jpg");
		if (!file.isFile())
			return new TextureImage(generation, null);

		TEXTURE_PROGRESS = 20;
		TEXTURE_STAGE = "decode RGB";
		BufferedImage image = Util.loadImage(file);
		if (Thread.currentThread().isInterrupted())
			throw new CancellationException();
		if (textureMaxSize > 0 && (image.getWidth() > textureMaxSize || image.getHeight() > textureMaxSize)) {
			TEXTURE_PROGRESS = 50;
			TEXTURE_STAGE = "resize RGB";
			final double scale = Math.min((double) textureMaxSize / image.getWidth(),
					(double) textureMaxSize / image.getHeight());
			image = Util.scaleImage(image, Math.max(1, (int) Math.round(image.getWidth() * scale)),
					Math.max(1, (int) Math.round(image.getHeight() * scale)), 2);
		}
		if (Thread.currentThread().isInterrupted())
			throw new CancellationException();
		TEXTURE_PROGRESS = 72;
		TEXTURE_STAGE = "flip RGB";
		ImageUtil.flipImageVertically(image);
		return new TextureImage(generation, image);
	}

	private static final class TextureImage {
		private final int _generation;
		private final BufferedImage _image;

		private TextureImage(final int generation, final BufferedImage image) {
			_generation = generation;
			_image = image;
		}
	}

	public final void checkNeedUpdateVBO(final boolean blockMinHeightChanged, final boolean blockMaxHeightChanged) {
		if (blockMinHeightChanged || blockMaxHeightChanged)
			_needUpdateVBO = true;
	}

	public final void init(final GL2 gl) {
		if (_vboIndex != -1)
			return;

		final int[] temp = new int[3];
		gl.glGenBuffers(3, temp, 0);
		_vboIndex = temp[0];
		_vboVertex = temp[1];
		_vboTexture = temp[2];

		_builder = _detailLevel.newBuilder();
		_builder.init(gl, _vboIndex, _vboTexture);
	}

	public final void render(final GL2 gl) {
		if (_vboIndex == -1 || !_enabled || _region == null)
			return;

		if (_discardTexture) {
			_discardTexture = false;
			if (_texture != null) {
				_texture.destroy(gl);
				_texture = null;
			}
		}

		if (_needUpdateTexture && !_streamingPreview && _textureLoad == null) {
			_needUpdateTexture = false;
			final int regionX = _region.getRegionX();
			final int regionY = _region.getRegionY();
			final int textureMaxSize = _textureMaxSize;
			final int generation = _textureGeneration;
			_textureLoad = TEXTURE_LOADER
					.submit(() -> loadTextureImage(regionX, regionY, textureMaxSize, generation));
		}

		final Future<TextureImage> textureLoad = _textureLoad;
		if (textureLoad != null && textureLoad.isDone()) {
			_textureLoad = null;
			try {
				final TextureImage loaded = textureLoad.get();
				if (loaded._generation == _textureGeneration && loaded._image != null && !_streamingPreview) {
					TEXTURE_PROGRESS = 88;
					TEXTURE_STAGE = "upload GPU";
					_texture = AWTTextureIO.newTexture(gl.getGLProfile(), loaded._image, false);
					_texture.enable();
					_texture.setTexParameteri(GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
					_texture.setTexParameteri(GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
					_textureReady = true;
					TEXTURE_PROGRESS = 100;
					TEXTURE_STAGE = "ready";
				}
			} catch (final CancellationException e) {
				// Expected when the active region or texture mode changes.
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (final ExecutionException e) {
				_texture = null;
				final Throwable cause = e.getCause();
				(cause != null ? cause : e).printStackTrace();
			}
		}

		if (_needUpdateVBO) {
			_needUpdateVBO = false;
			_builder.update(gl, _vboVertex, _region.getGeoBlocks(), false);
			_lastVboPreparationMicros = _builder.getLastPreparationMicros();
			_lastVboUploadMicros = _builder.getLastUploadMicros();
		}

		if (!_streamingPreview && !_textureReady)
			return;

		if (_streamingPreview) {
			gl.glDisable(GL2.GL_TEXTURE_2D);
			GLState.glColor4f(gl, new GLColor(0.05f, 0.9f, 0.15f, 1f));
		} else if (_texture != null) {
			gl.glActiveTexture(GL2.GL_TEXTURE0);
			gl.glEnable(GL2.GL_TEXTURE_2D);
			_texture.enable();
			_texture.bind();
			gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
			GLState.glColor4f(gl, GLColor.WHITE);
		}

		if (_wireframe || _streamingPreview)
			gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL2.GL_LINE);

		if (!_streamingPreview)
			gl.glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
		gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);

		if (!_streamingPreview) {
			gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, _vboTexture);
			gl.glTexCoordPointer(2, GL2.GL_FLOAT, 0, 0);
		}

		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, _vboVertex);
		gl.glVertexPointer(3, GL2.GL_SHORT, 0, 0);

		gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, _vboIndex);

		gl.glPushMatrix();
		if (!_streamingPreview)
			GLState.glColor4f(gl, GLColor.WHITE);
		gl.glTranslatef(GeoEngine.getGeoXY(_region.getRegionX(), 0), -0.3f,
				GeoEngine.getGeoXY(_region.getRegionY(), 0));
		final int scale = _builder.getDetailLevel().getScaleXZ();
		gl.glScalef(scale, 1f, scale);
		gl.glDrawElements(GL2.GL_TRIANGLES, _builder.getIndexBufferLen(), GL2.GL_UNSIGNED_INT, 0);
		gl.glPopMatrix();

		gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);

		gl.glDisableClientState(GL2.GL_VERTEX_ARRAY);
		if (!_streamingPreview)
			gl.glDisableClientState(GL2.GL_TEXTURE_COORD_ARRAY);

		gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL2.GL_FILL);
		if (_streamingPreview) {
			gl.glEnable(GL2.GL_TEXTURE_2D);
			GLState.glColor4f(gl, GLColor.WHITE);
		}

		gl.glUseProgram(0);
	}

	public final void dispose(final GL2 gl) {
		invalidateTexture(false);
		if (_texture != null) {
			_texture.destroy(gl);
			_texture = null;
		}
		_discardTexture = false;
		_region = null;
		_enabled = false;
		_needUpdateVBO = false;
		_lastVboPreparationMicros = 0L;
		_lastVboUploadMicros = 0L;

		if (_vboIndex == -1)
			return;

		gl.glDeleteBuffers(3, new int[] { _vboIndex, _vboVertex, _vboTexture }, 0);
		_vboIndex = -1;
		_vboVertex = -1;
		_vboTexture = -1;
	}

	public static enum TerrainDetailLevel {
		LOW(TerrainBuilderLowDetail.class), MEDIUM(TerrainBuilderXDetail.class), HIGH(TerrainBuilderXDetail.class),
		ULTRA(TerrainBuilderXDetail.class);

		private final Class<?> _builder;

		private TerrainDetailLevel(final Class<?> builder) {
			_builder = builder;
		}

		public final int getScaleXZ() {
			return ULTRA.getFactor() / getFactor();
		}

		public final int getFactor() {
			return 1 << ordinal();
		}

		public final TerrainBuilder newBuilder() {
			try {
				return (TerrainBuilder) _builder.getConstructor(TerrainDetailLevel.class).newInstance(this);
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static abstract class TerrainBuilder {
		private final TerrainDetailLevel _detailLevel;
		private ShortBuffer _vertexBuffer;
		private boolean _initialized;
		private int _indexBufferLen;
		private long _lastPreparationMicros;
		private long _lastUploadMicros;

		public TerrainBuilder(final TerrainDetailLevel detailLevel) {
			_detailLevel = detailLevel;
		}

		public final TerrainDetailLevel getDetailLevel() {
			return _detailLevel;
		}

		public final int getIndexBufferLen() {
			return _indexBufferLen;
		}

		public final long getLastPreparationMicros() {
			return _lastPreparationMicros;
		}

		public final long getLastUploadMicros() {
			return _lastUploadMicros;
		}

		public final void init(final GL2 gl, final int vboIndex, final int vboTexture) {
			if (_initialized)
				return;

			_initialized = true;

			final int factor = getDetailLevel().getFactor();
			final int xUp = GeoEngine.GEO_REGION_SIZE * factor;
			final int zUp = GeoEngine.GEO_REGION_SIZE * factor;

			final int xUnit = xUp - 1;
			final int zUnit = zUp - 1;
			final IntBuffer indexBuffer = Buffers.newDirectIntBuffer(xUnit * zUnit * 6);
			for (int i = 0, j; i < zUnit; i++) {
				for (j = 0; j < xUnit; j++) {
					indexBuffer.put(i * xUp + j);
					indexBuffer.put(i * xUp + xUp + j);
					indexBuffer.put(i * xUp + xUp + j + 1);
					indexBuffer.put(i * xUp + j);
					indexBuffer.put(i * xUp + xUp + j + 1);
					indexBuffer.put(i * xUp + j + 1);
				}
			}

			indexBuffer.flip();

			final FloatBuffer textureBuffer = Buffers.newDirectFloatBuffer(xUp * zUp * 2);
			final float xStep = 1F / (xUp);
			final float zStep = 1F / (zUp);
			for (int i = 0, j; i < zUp; i++) {
				for (j = 0; j < xUp; j++) {
					textureBuffer.put(j * xStep);
					textureBuffer.put(1F - i * zStep);
				}
			}
			textureBuffer.flip();

			_vertexBuffer = Buffers.newDirectShortBuffer(xUp * zUp * 3);
			_indexBufferLen = indexBuffer.remaining();

			gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, vboIndex);
			gl.glBufferData(GL2.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.remaining() * Buffers.SIZEOF_INT, indexBuffer,
					GL2.GL_STATIC_DRAW);

			gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vboTexture);
			gl.glBufferData(GL2.GL_ARRAY_BUFFER, textureBuffer.remaining() * Buffers.SIZEOF_FLOAT, textureBuffer,
					GL2.GL_STATIC_DRAW);
		}

		public final void update(final GL2 gl, final int vboVertex, final GeoBlock[][] blocks, final boolean flipped) {
			final long preparationStart = System.nanoTime();
			_vertexBuffer.clear();
			updateImpl(_vertexBuffer, blocks, flipped);
			_vertexBuffer.flip();
			_lastPreparationMicros = Math.max(1L, (System.nanoTime() - preparationStart) / 1_000L);

			final long uploadStart = System.nanoTime();
			gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vboVertex);
			// call with null buffer first to tell VGA-driver that we give a shit about the
			// old content
			gl.glBufferData(GL2.GL_ARRAY_BUFFER, 0, null, GL2.GL_DYNAMIC_DRAW);
			gl.glBufferData(GL2.GL_ARRAY_BUFFER, _vertexBuffer.remaining() * Buffers.SIZEOF_SHORT, _vertexBuffer,
					GL2.GL_DYNAMIC_DRAW);
			_lastUploadMicros = Math.max(1L, (System.nanoTime() - uploadStart) / 1_000L);
		}

		public abstract void updateImpl(final ShortBuffer vertexBuffer, final GeoBlock[][] blocks,
				final boolean flipped);
	}

	public static final class TerrainBuilderLowDetail extends TerrainBuilder {
		public TerrainBuilderLowDetail(final TerrainDetailLevel detailLevel) {
			super(detailLevel);
		}

		/**
		 * @see g3deditor.jogl.GLTerrain.TerrainBuilder#updateImpl(java.nio.FloatBuffer,
		 *      g3deditor.geo.GeoBlock[][], boolean)
		 */
		@Override
		public final void updateImpl(final ShortBuffer vertexBuffer, final GeoBlock[][] blocks, final boolean flipped) {
			for (int blockY = 0, blockX; blockY < GeoEngine.GEO_REGION_SIZE; blockY++) {
				for (blockX = 0; blockX < GeoEngine.GEO_REGION_SIZE; blockX++) {
					vertexBuffer.put((short) blockX);
					vertexBuffer.put((short) (getNeighboursHeight(blocks, blockX, blockY, flipped) / 16f - 0.5f));
					vertexBuffer.put((short) blockY);
				}
			}
		}

		private final int getNeighboursHeight(final GeoBlock[][] blocks, final int blockX, final int blockY,
				final boolean flipped) {
			if (flipped) {
				if (blockX > 0) {
					if (blockY > 0) {
						return Math.min(
								Math.min(
										Math.min(blocks[blockX][blockY].getMaxHeight(),
												blocks[blockX - 1][blockY].getMaxHeight()),
										blocks[blockX][blockY - 1].getMaxHeight()),
								blocks[blockX - 1][blockY - 1].getMaxHeight());
					}

					return Math.min(blocks[blockX][blockY].getMaxHeight(), blocks[blockX - 1][blockY].getMaxHeight());
				} else {
					if (blockY > 0) {
						return Math.min(blocks[blockX][blockY].getMaxHeight(),
								blocks[blockX][blockY - 1].getMaxHeight());
					}

					return blocks[blockX][blockY].getMaxHeight();
				}
			} else {
				if (blockX > 0) {
					if (blockY > 0) {
						return Math.min(
								Math.min(
										Math.min(blocks[blockX][blockY].getMinHeight(),
												blocks[blockX - 1][blockY].getMinHeight()),
										blocks[blockX][blockY - 1].getMinHeight()),
								blocks[blockX - 1][blockY - 1].getMinHeight());
					}

					return Math.min(blocks[blockX][blockY].getMinHeight(), blocks[blockX - 1][blockY].getMinHeight());
				} else {
					if (blockY > 0) {
						return Math.min(blocks[blockX][blockY].getMinHeight(),
								blocks[blockX][blockY - 1].getMinHeight());
					}

					return blocks[blockX][blockY].getMinHeight();
				}
			}
		}
	}

	public static final class TerrainBuilderXDetail extends TerrainBuilder {
		private short[] _sampledHeights = new short[0];

		public TerrainBuilderXDetail(final TerrainDetailLevel detailLevel) {
			super(detailLevel);
		}

		/**
		 * @see g3deditor.jogl.GLTerrain.TerrainBuilder#updateImpl(java.nio.FloatBuffer,
		 *      g3deditor.geo.GeoBlock[][], boolean)
		 */
		@Override
		public final void updateImpl(final ShortBuffer vertexBuffer, final GeoBlock[][] blocks, final boolean flipped) {
			final int factor = getDetailLevel().getFactor();
			final int cells = GeoEngine.GEO_BLOCK_SHIFT / factor;
			final int xzUp = GeoEngine.GEO_REGION_SIZE * factor;
			final int sampleCount = xzUp * xzUp;
			if (_sampledHeights.length != sampleCount)
				_sampledHeights = new short[sampleCount];

			// Each cell group is shared by up to four neighboring vertices. Cache its
			// height once instead of rescanning the same geodata groups repeatedly.
			for (int blockY = 0; blockY < GeoEngine.GEO_REGION_SIZE; blockY++) {
				for (int factorY = 0; factorY < factor; factorY++) {
					for (int blockX = 0; blockX < GeoEngine.GEO_REGION_SIZE; blockX++) {
						for (int factorX = 0; factorX < factor; factorX++) {
							final int sampleX = blockX * factor + factorX;
							final int sampleY = blockY * factor + factorY;
							_sampledHeights[sampleY * xzUp + sampleX] = (short) getBlockHeightOfGroup(
									blocks[blockX][blockY], factorX * cells, factorY * cells, cells, flipped);
						}
					}
				}
			}

			for (int vertexY = 0; vertexY < xzUp; vertexY++) {
				for (int vertexX = 0; vertexX < xzUp; vertexX++) {
					vertexBuffer.put((short) vertexX);
					vertexBuffer.put((short) (getVertexHeight(_sampledHeights, vertexX, vertexY, xzUp) / 16f - 0.5f));
					vertexBuffer.put((short) vertexY);
				}
			}
		}

		static int getVertexHeight(final short[] sampledHeights, final int vertexX, final int vertexY,
				final int xzUp) {
			int height = Integer.MAX_VALUE;
			if (vertexX > 0 && vertexY > 0)
				height = Math.min(height, sampledHeights[(vertexY - 1) * xzUp + vertexX - 1]);
			if (vertexY > 0)
				height = Math.min(height, sampledHeights[(vertexY - 1) * xzUp + vertexX]);
			if (vertexX > 0)
				height = Math.min(height, sampledHeights[vertexY * xzUp + vertexX - 1]);
			height = Math.min(height, sampledHeights[vertexY * xzUp + vertexX]);
			return height;
		}

		private final int getBlockHeightOfGroup(final GeoBlock block, final int cellX, final int cellY, final int cells,
				final boolean flipped) {
			if (block.getType() == GeoEngine.GEO_BLOCK_TYPE_FLAT)
				return block.getMinHeight();

			int height = Integer.MAX_VALUE, x, y;
			for (x = cells; x-- > 0;) {
				for (y = cells; y-- > 0;) {
					height = Math.min(height,
							(flipped ? block.nGetCellByLayer(cellX + x, cellY + y,
									block.nGetLayerCount(cellX + x, cellY + y) - 1)
									: block.nGetCellByLayer(cellX + x, cellY + y, 0)).getHeight());
				}
			}
			return height;
		}
	}
}
