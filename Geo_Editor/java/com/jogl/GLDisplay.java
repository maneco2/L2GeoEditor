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
import com.geo.GeoBlock;
import com.geo.GeoBlockSelector;
import com.geo.GeoBlockSelector.GeoBlockEntry;
import com.geo.GeoCell;
import com.geo.GeoEngine;
import com.geo.GeoRegion;
import com.geo.RegionLoadManager;
import com.jogl.GLCellRenderSelector.GLSubRenderSelector;
import com.jogl.GLGUIRenderer.GLText;
import com.jogl.renderer.IRenderer;
import com.jogl.renderer.VBOGLSLRenderer;
import com.swing.FrameMain;
import com.util.FastArrayList;
import com.util.PerformanceMetrics;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.glu.GLU;

/**
 * L2J project source.
 * 
 * @author Forsaiken aka Patrick
 * @author Maneco2
 */
public final class GLDisplay implements GLEventListener {
	private static final int NEIGHBOR_TEXTURE_MAX_SIZE = 1024;
	private static final long NANOS_IN_MILLISECOND = TimeUnit.NANOSECONDS.convert(1, TimeUnit.MILLISECONDS);
	private static final long NANOS_IN_SECOND = TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);

	private static GLDisplay _instance;

	public static final void init(final GLCanvas canvas) {
		_instance = new GLDisplay(canvas);
	}

	public static final GLDisplay getInstance() {
		return _instance;
	}

	private static final double nanosToFps(final double nanos) {
		return NANOS_IN_SECOND / nanos;
	}

	private static final double nanosToTpf(final double nanos) {
		return nanos / NANOS_IN_MILLISECOND;
	}

	private static final double roundFps(final double fps) {
		return ((int) (fps * 100f)) / 100d;
	}

	private static final float VIEW_ANGLE = 45f;
	private static final float VIEW_Z_NEAR = 1f;
	/** Covers the complete bounded 3x3 streaming window. */
	private static final float VIEW_Z_FAR = 8192f;

	private final GLCanvas _canvas;
	private final GLGUIRenderer _guiRenderer;
	private final GLCellRenderSelector _renderSelector;
	private final GLSelectionBox _selectionBox;
	private final GLCamera _camera;
	private final GLTerrain _terrain;
	private final Map<RegionLoadManager.RegionKey, GLTerrain> _neighborTerrains;
	private final GLGlobalMapRenderer _globalMap;
	private final AWTInput _input;
	private final GLText _fpsText;
	private final GLText _performanceText;
	private final GLText _callsText;
	private final GLText _memoryText;
	private final GLText _streamingText;
	private final GLText _worldPositionText;
	private final GLText _geoPositionText;
	private final PerformanceMetrics _performanceMetrics;

	private GLCellRenderer _renderer;
	private GeoCell _prevPick;

	private GLU _glu;
	private int _width;
	private int _height;
	private long _time;
	private long _timeFPS;
	private int _loopsPS;
	private int _elementsPS;
	private int _lastCallsPerFrame;
	private int _neighborPrepareCooldown;
	private boolean _neighborTexturedMode;

	private GLDisplay(final GLCanvas canvas) {
		_canvas = canvas;
		_guiRenderer = new GLGUIRenderer();
		_renderSelector = new GLCellRenderSelector();
		_selectionBox = new GLSelectionBox();
		_camera = new GLCamera();
		_terrain = new GLTerrain();
		_neighborTerrains = new LinkedHashMap<>();
		_globalMap = new GLGlobalMapRenderer();
		_input = new AWTInput();
		_performanceMetrics = new PerformanceMetrics();

		_callsText = _guiRenderer.newText(10, 10);
		_fpsText = _guiRenderer.newText(10, _callsText.getY() + GLGUIRenderer.TEXT_HEIGHT);
		_performanceText = _guiRenderer.newText(10, _fpsText.getY() + GLGUIRenderer.TEXT_HEIGHT);
		_memoryText = _guiRenderer.newText(10, _performanceText.getY() + GLGUIRenderer.TEXT_HEIGHT);
		_streamingText = _guiRenderer.newText(10, _memoryText.getY() + GLGUIRenderer.TEXT_HEIGHT);
		_geoPositionText = _guiRenderer.newText(10, _streamingText.getY() + GLGUIRenderer.TEXT_HEIGHT);
		_worldPositionText = _guiRenderer.newText(10, _geoPositionText.getY() + GLGUIRenderer.TEXT_HEIGHT);
	}

	public final GLCanvas getCanvas() {
		return _canvas;
	}

	public final void requestFocus() {
		getCanvas().requestFocus();
	}

	public final GLCellRenderer getRenderer() {
		return _renderer;
	}

	public final GLCellRenderSelector getRenderSelector() {
		return _renderSelector;
	}

	public final GLSelectionBox getSelectionBox() {
		return _selectionBox;
	}

	public final GLCamera getCamera() {
		return _camera;
	}

	public final AWTInput getInput() {
		return _input;
	}

	public final GLTerrain getTerrain() {
		return _terrain;
	}

	public final int getWidth() {
		return _width;
	}

	public final int getHeight() {
		return _height;
	}

	public final PerformanceMetrics.Snapshot getPerformanceMetrics() {
		return _performanceMetrics.snapshot();
	}

	public final PerformanceMetrics getPerformanceMetricsCollector() {
		return _performanceMetrics;
	}

	public final Path exportPerformanceReport() {
		try {
			final Path directory = Path.of("./logs");
			Files.createDirectories(directory);
			final Path report = directory.resolve("performance-last.txt");
			final String scenario = "scenario.grid=" + (Config.CIRCULAR_GEODATA_GRID ? "circular" : "square")
					+ System.lineSeparator() + "scenario.fullMap=" + Config.FULL_MAP
					+ System.lineSeparator() + "scenario.fullTexturedTerrain=" + Config.FULL_TEXTURED_TERRAIN
					+ System.lineSeparator() + "scenario.visibleGridRange=" + Config.VIS_GRID_RANGE
					+ System.lineSeparator() + "callsPerFrame=" + _lastCallsPerFrame
					+ System.lineSeparator();
			Files.writeString(report, scenario + PerformanceMetrics.formatReport(_performanceMetrics.snapshot()),
					StandardCharsets.UTF_8);
			return report;
		} catch (final Exception exception) {
			System.err.println("Unable to write performance report: " + exception.getMessage());
			return null;
		}
	}

	/**
	 * @see javax.media.opengl.GLEventListener#init(javax.media.opengl.GLAutoDrawable)
	 */
	@Override
	public final void init(final GLAutoDrawable glautodrawable) {
		final GL2 gl = glautodrawable.getGL().getGL2();

		// check again here
		Config.CELL_RENDERER = GLCellRenderer.validateRenderer(Config.CELL_RENDERER, gl);
		FrameMain.getInstance().checkAvailableRenderers();

		_glu = GLU.createGLU(gl);

		GLState.init(gl);

		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		_glu.gluPerspective(VIEW_ANGLE, 1.0f, VIEW_Z_NEAR, VIEW_Z_FAR);
		gl.glMatrixMode(GL2.GL_MODELVIEW);

		_guiRenderer.init(gl);
		_terrain.init(gl);
		_input.setEnabled(true);

		_time = System.nanoTime();
		_timeFPS = 0L;
		_loopsPS = 0;

	}

	/**
	 * @see javax.media.opengl.GLEventListener#dispose(javax.media.opengl.GLAutoDrawable)
	 */
	@Override
	public final void dispose(final GLAutoDrawable glautodrawable) {
		final GL2 gl = glautodrawable.getGL().getGL2();
		if (_renderer != null)
			_renderer.dispose(gl);
		_guiRenderer.dispose(gl);
		_terrain.dispose(gl);
		for (final GLTerrain terrain : _neighborTerrains.values())
			terrain.dispose(gl);
		_neighborTerrains.clear();
		_globalMap.dispose(gl);
	}

	/**
	 * @see javax.media.opengl.GLEventListener#display(javax.media.opengl.GLAutoDrawable)
	 */
	@Override
	public final void display(final GLAutoDrawable glautodrawable) {
		_performanceMetrics.beginFrame();
		final long currentTime = System.nanoTime();
		final double tpf = nanosToTpf(currentTime - _time);
		_timeFPS += currentTime - _time;
		_loopsPS++;
		_time = currentTime;

		if (TimeUnit.SECONDS.convert(_timeFPS, TimeUnit.NANOSECONDS) >= 1) {
			final double fps = _loopsPS == 0 ? Double.POSITIVE_INFINITY : nanosToFps(_timeFPS / _loopsPS);
			final int elements = _elementsPS / (_loopsPS == 0 ? 1 : _loopsPS);
			_lastCallsPerFrame = elements;
			_timeFPS = 0L;
			_loopsPS = 0;
			_elementsPS = 0;
			_fpsText.setText("Fps:   " + roundFps(fps));
			_callsText.setText("Calls: " + elements);
		}

		_worldPositionText.setText(
				"World-Pos XYZ: " + _camera.getWorldX() + ", " + _camera.getWorldY() + ", " + _camera.getWorldZ());
		_geoPositionText
				.setText("Geo-Pos XYZ: " + _camera.getGeoX() + ", " + _camera.getGeoY() + ", " + _camera.getGeoZ());
		_memoryText.setText("Memory: "
				+ (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024l / 1024l + "mb");
		final PerformanceMetrics.Snapshot metrics = _performanceMetrics.snapshot();
		_performanceMetrics.setSelectionParallelism(_renderSelector.getSelectionParallelism());
		if (_renderer instanceof VBOGLSLRenderer vboRenderer)
			_performanceMetrics.recordBufferUpload(vboRenderer.getLastBufferUploadMicros());
		_performanceText.setText("Frames: " + metrics.frames());
		_streamingText.setText("Regions: " + metrics.loadedRegions());

		final GL2 gl = glautodrawable.getGL().getGL2();
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
		gl.glLoadIdentity();

		GLState.setBlendEnabled(gl, Config.USE_TRANSPARENCY);
		GLState.setBlendFunc(gl, GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		GLState.setVSyncEnabled(gl, Config.V_SYNC);
		GLState.setDepthTestEnabled(gl, true);

		_input.update(tpf);
		_camera.checkPositionOrRotationChanged();
		if (Config.FULL_MAP)
			FrameMain.getInstance().checkRegionTransition(_camera.getGeoX(), _camera.getGeoY());

		// Full Map enables the bounded streaming context around the editable center.

		gl.glRotatef(360f - _camera.getRotX(), 1.0f, 0.0f, 0.0f);
		gl.glRotatef(360f - _camera.getRotY(), 0.0f, 1.0f, 0.0f);
		gl.glTranslatef(-_camera.getX(), -_camera.getY(), -_camera.getZ());

		_terrain.setRegion(GeoEngine.getInstance().getActiveRegion());
		_terrain.setEnabled(_input.getKeyTToggle() || Config.FULL_TEXTURED_TERRAIN);
		// Full textured terrain is an explicit solid mode. Do not let the legacy
		// hidden R-toggle turn only the active center back into wireframe.
		_terrain.setWireframe(!Config.FULL_TEXTURED_TERRAIN && _input.getKeyRToggle());
		_terrain.render(gl);
		renderNeighborTerrains(gl, GeoEngine.getInstance().getLoadedRegionsSnapshot(),
				GeoEngine.getInstance().getActiveRegion());

		final String effectiveRenderer = Config.CELL_RENDERER;
		if (_renderer == null) {
			_renderer = GLCellRenderer.getRenderer(effectiveRenderer);
		} else if (!_renderer.getName().equals(effectiveRenderer)) {
			final GLCellRenderer newRenderer = GLCellRenderer.getRenderer(effectiveRenderer);
			if (!newRenderer.getName().equals(_renderer.getName())) {
				_renderer.dispose(gl);
				_renderer = newRenderer;
				getRenderSelector().forceUpdateFrustum();
			} else {
				Config.CELL_RENDERER = _renderer.getName();
			}
		}

		if (Config.SHOW_GEODATA_GRID)
			_renderSelector.select(gl, _camera, _input.getKeyFToggle());

		if (_input.getKeyGToggle() && Config.SHOW_GEODATA_GRID) {
			if (!_renderer.isInitialized()) {
				if (!_renderer.init(gl)) {
					System.err.println("Renderer could not be initialized: " + _renderer.getName());
					_renderer = new IRenderer();
					Config.CELL_RENDERER = _renderer.getName();
				}
			}
			_renderer.enableRender(gl);

			GLSubRenderSelector selector;
			for (int i = _renderSelector.getElementsToRender(); i-- > 0;) {
				selector = _renderSelector.getElementToRender(i);
				_elementsPS += selector.getElementsToRender();
				_renderer.render(gl, selector);
			}

			if (Config.DRAW_OUTLINE && !(_renderer instanceof VBOGLSLRenderer)) {
				gl.glPolygonMode(GL2.GL_BACK, GL2.GL_LINE);
				gl.glCullFace(GL2.GL_FRONT);
				gl.glDepthFunc(GL2.GL_LEQUAL);
				GLState.glColor4f(gl, GLColor.BLACK);
				GLState.lockColor(true);

				GeoBlock block;
				float dx, dy, dz, distSq;
				for (int i = _renderSelector.getElementsToRender(); i-- > 0;) {
					block = _renderSelector.getElementToRender(i).getGeoBlock();
					dx = (block.getGeoX() + 4) - _camera.getX();
					dy = (block.getMinHeight() + ((block.getMaxHeight() - block.getMinHeight()) / 2)) / 16f
							- _camera.getY();
					dz = (block.getGeoY() + 4) - _camera.getZ();
					distSq = dx * dx + dy * dy + dz * dz;
					if (distSq > 4096)
						gl.glLineWidth(1f);
					else if (distSq > 1024)
						gl.glLineWidth(2f);
					else
						gl.glLineWidth(3f);

					selector = _renderSelector.getElementToRender(i);
					_elementsPS += selector.getElementsToRender();
					_renderer.render(gl, selector);
				}
				GLState.lockColor(false);
				gl.glDepthFunc(GL2.GL_LESS);
				gl.glCullFace(GL2.GL_BACK);
				gl.glPolygonMode(GL2.GL_BACK, GL2.GL_FILL);
				gl.glLineWidth(1);
			}

			_renderer.disableRender(gl);
		}

		if (!Config.SHOW_GEODATA_GRID) {
			_input.getMouseEvents().clear();
			_guiRenderer.render(gl);
			_performanceMetrics.endFrame();
			return;
		}

		final FastArrayList<MouseEvent> mouseEvents = _input.getMouseEvents();
		for (int i = 0, j; i < mouseEvents.size(); i++) {
			final MouseEvent event = mouseEvents.getUnsafe(i);
			if (event instanceof MouseWheelEvent) {
				final MouseWheelEvent scrollevent = (MouseWheelEvent) event;
				if (event.isControlDown()) {
					final short addHeight = (short) (scrollevent.getWheelRotation()
							* (_input.getKeySpace() ? -32 : -8));
					final GeoBlockSelector selector = GeoBlockSelector.getInstance();
					FastArrayList<GeoCell> cells;
					for (GeoBlockEntry entry = selector.getHead(),
							tail = selector.getTail(); (entry = entry.getNext()) != tail;) {
						cells = entry.getValue();
						for (j = cells.size(); j-- > 0;) {
							cells.getUnsafe(j).addHeight(addHeight);
						}
					}
					_renderSelector.forceUpdateFrustum();
				} else {
					_selectionBox.addHeight(scrollevent.getWheelRotation() * (_input.getKeySpace() ? -32 : -8));
				}
			} else {
				// A drag starts with the press event. Reset the last pick here so
				// the first cell is selected once, while the first drag event over
				// that same cell cannot toggle it off when Shift is held.
				if (event.getID() == MouseEvent.MOUSE_PRESSED)
					_prevPick = null;
				final GeoCell cell = _camera.pick(gl, _glu, event.getX(), event.getY());
				if (cell != null) {
					if (event.getID() == MouseEvent.MOUSE_DRAGGED) {
						if (_prevPick == cell)
							continue;

						final GeoBlock prevBlock = _prevPick != null ? _prevPick.getBlock() : null;
						_prevPick = cell;

						if (event.isAltDown() && prevBlock == cell.getBlock())
							continue;
					}

					GeoBlockSelector.getInstance().selectGeoCell(cell, event.isAltDown(), event.isShiftDown());
					_prevPick = cell;
				}
			}
		}
		mouseEvents.clear();

		_selectionBox.render(gl,
				_input.getMouseButton3() ? null : _camera.pick(gl, _glu, _input.getMouseX(), _input.getMouseY()));
		_guiRenderer.render(gl);
		_performanceMetrics.endFrame();
	}

	private static String textureProgressBar() {
		final int progress = GLTerrain.getTextureProgress();
		final int filled = Math.min(10, Math.max(0, (progress + 9) / 10));
		return "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "] " + progress + "% ("
				+ GLTerrain.getTextureStage() + ")";
	}

	private void renderNeighborTerrains(final GL2 gl,
			final Map<RegionLoadManager.RegionKey, GeoRegion> loadedRegions, final GeoRegion activeRegion) {
		_performanceMetrics.setLoadedRegions(loadedRegions.size());
		long loadedLayerCount = 0L;
		long flatLayerCount = 0L;
		long complexLayerCount = 0L;
		long multilayerLayerCount = 0L;
		for (final GeoRegion region : loadedRegions.values())
			loadedLayerCount += region.getTotalLayerCount();
		for (final GeoRegion region : loadedRegions.values()) {
			flatLayerCount += region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_FLAT);
			complexLayerCount += region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_COMPLEX);
			multilayerLayerCount += region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER);
		}
		_performanceMetrics.setLoadedLayerCount(loadedLayerCount);
		_performanceMetrics.setLayerCounts(flatLayerCount, complexLayerCount, multilayerLayerCount);
		final boolean texturedNeighbors = Config.FULL_TEXTURED_TERRAIN;
		if (_neighborTexturedMode != texturedNeighbors) {
			for (final GLTerrain terrain : _neighborTerrains.values())
				terrain.dispose(gl);
			_neighborTerrains.clear();
			_neighborPrepareCooldown = 0;
			_neighborTexturedMode = texturedNeighbors;
		}
		if (!Config.AUXILIARY_TERRAINS && !texturedNeighbors) {
			for (final GLTerrain terrain : _neighborTerrains.values())
				terrain.dispose(gl);
			_neighborTerrains.clear();
			return;
		}
		final Iterator<Map.Entry<RegionLoadManager.RegionKey, GLTerrain>> iterator =
				_neighborTerrains.entrySet().iterator();
		while (iterator.hasNext()) {
			final Map.Entry<RegionLoadManager.RegionKey, GLTerrain> entry = iterator.next();
			final GeoRegion region = loadedRegions.get(entry.getKey());
			if (region == null || region == activeRegion) {
				entry.getValue().dispose(gl);
				iterator.remove();
			}
		}

		if (_neighborPrepareCooldown > 0)
			_neighborPrepareCooldown--;
		for (final Map.Entry<RegionLoadManager.RegionKey, GeoRegion> entry : loadedRegions.entrySet()) {
			if (entry.getValue() == activeRegion)
				continue;
			GLTerrain terrain = _neighborTerrains.get(entry.getKey());
			if (terrain == null && (!texturedNeighbors || texturedNeighborCount() < Config.MAX_NEIGHBOR_TEXTURES)) {
				if (_neighborPrepareCooldown > 0)
					continue;
				terrain = new GLTerrain();
				terrain.init(gl);
				terrain.setStreamingPreview(!texturedNeighbors);
				terrain.setTextureMaxSize(texturedNeighbors ? NEIGHBOR_TEXTURE_MAX_SIZE : 0);
				_neighborTerrains.put(entry.getKey(), terrain);
				_neighborPrepareCooldown = 8;
			}
			if (terrain == null)
				continue;
			terrain.setRegion(entry.getValue());
			// Loaded neighbors are the streaming context around the editable center
			// and must remain visible independently of the active-region terrain toggle.
			terrain.setEnabled(true);
			terrain.setWireframe(!texturedNeighbors);
			terrain.render(gl);
		}
	}

	private int texturedNeighborCount() {
		int count = 0;
		for (final GLTerrain terrain : _neighborTerrains.values())
			if (!terrain.getWireframe())
				count++;
		return count;
	}

	/**
	 * @see javax.media.opengl.GLEventListener#reshape(javax.media.opengl.GLAutoDrawable,
	 *      int, int, int, int)
	 */
	@Override
	public final void reshape(final GLAutoDrawable glautodrawable, final int x, final int y, final int width,
			final int height) {
		final GL2 gl = glautodrawable.getGL().getGL2();
		gl.glViewport(0, 0, width, height);
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		_glu.gluPerspective(VIEW_ANGLE, (float) width / (float) height, VIEW_Z_NEAR, VIEW_Z_FAR);
		gl.glMatrixMode(GL2.GL_MODELVIEW);

		_width = width;
		_height = height;

		_camera.onProjectionMatrixChanged();
		_camera.onViewportChanged();
		_renderSelector.forceUpdateFrustum();
	}
}
