/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.editor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import com.geo.core.RegionSnapshot;
import com.geo.core.DetailedRegionSnapshot;
import com.geo.RegionLoadManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;

/** Owns the LWJGL/GLFW lifecycle used by the JavaFX application. */
public final class LwjglRenderBackend implements AutoCloseable {
	private static final double CAMERA_NEAR_PLANE = 0.2d;
	private static final float ACTIVE_GRID_ALPHA = 0.52f;
	public record GridSelection(int blockX, int blockY, int cellX,
			int cellY, int layer) {}
	private GLFWErrorCallback errorCallback;
	private boolean initialized;
	private long window;
	private int framebufferWidth;
	private int framebufferHeight;
	private int framebufferSamples;
	private int actualFramebufferSamples;
	private int maximumTextureSize;
	private String openGlVersion = "Not initialized";
	private String gpuRenderer = "Not initialized";
	private boolean graphicsSmokeReported;
	private ByteBuffer readbackPixels;
	private byte[] readbackBgra;
	private final int[] readbackPbos = new int[2];
	private int readbackPboBytes;
	private int readbackWriteIndex;
	private boolean readbackPboPrimed;
	private boolean readbackPboReported;
	private boolean synchronousReadbackRequested;
	private static final int GRID_CHUNK_BLOCKS = 8;
	private static final int MAX_GRID_CHUNKS = 192;
	private DetailedRegionSnapshot cachedGridDetails;
	private final LinkedHashMap<Integer, GridChunk> gridChunks =
			new LinkedHashMap<>(64, 0.75f, true);
	private long gridFrame;
	private int gridShaderProgram;
	private int gridCircularUniform;
	private int gridCenterUniform;
	private int gridRadiusUniform;
	private int gridTexturedUniform;
	private int gridOutlineUniform;
	private int skyStyle;
	private int skyShaderProgram;
	private int skyStyleUniform;
	private int skyPitchUniform;
	private int skyYawUniform;
	private int skyViewportUniform;
	private int starsTextureUniform;
	private int starsTextureReadyUniform;
	private int moonTextureUniform;
	private int moonTextureReadyUniform;
	private int moonStarsTextureUniform;
	private int moonStarsTextureReadyUniform;
	private File skyTextureFile;
	private int skyTextureId;
	private File moonTextureFile;
	private int moonTextureId;
	private File moonStarsTextureFile;
	private int moonStarsTextureId;
	private boolean skyShaderFailed;
	private final FloatBuffer frustumProjection = BufferUtils.createFloatBuffer(16);
	private final FloatBuffer frustumModelView = BufferUtils.createFloatBuffer(16);
	private final float[] frustumClip = new float[16];
	private final float[][] frustumPlanes = new float[6][4];
	private final GridVisibilitySelector futureVisibilitySelector =
			new GridVisibilitySelector();
	private final SharedGridRenderer sharedGridRenderer = new SharedGridRenderer();
	private final TerrainVboRenderer terrainVboRenderer = new TerrainVboRenderer();
	private boolean visibilityPlanSmokeDone;
	private final Map<String, Integer> textures = new HashMap<>();
	private final ArrayDeque<Integer> deferredTextureDeletes = new ArrayDeque<>();
	private static final int MAX_AUXILIARY_TEXTURES = 120;
	private static final int MAX_PENDING_AUXILIARY_TEXTURES = 16;
	private static final int AUXILIARY_TEXTURE_SIZE = 1024;
	private final AsyncTextureLoader auxiliaryTextureLoader =
			new AsyncTextureLoader("GeoEditor-terrain-texture-loader", 2);
	private final AsyncTextureLoader skyTextureLoader =
			new AsyncTextureLoader("GeoEditor-sky-texture-loader", 1);
	private int textureAnisotropy = 8;
	private boolean textureFilteringDirty = true;
	private int auxiliaryTextureUploads;
	private int textureUploadsThisFrame;
	private int textureDeletesThisFrame;
	private int desiredAuxiliaryTextures;
	private int residentAuxiliaryTextures;
	private boolean bulkTextureLoading;
	private boolean auxiliaryTextureSmokeReported;
	private String preparedRegionTexturePath;
	private long lastElements;
	private long lastDrawCalls;
	private List<GridSelection> gridSelections = List.of();

	public void setGridSelections(final List<GridSelection> selections) {
		gridSelections = selections == null ? List.of() : List.copyOf(selections);
	}

	public void initialize() {
		if (initialized)
			return;
		errorCallback = GLFWErrorCallback.createPrint(System.err);
		errorCallback.set();
		if (!GLFW.glfwInit()) {
			close();
			throw new IllegalStateException("Unable to initialize the LWJGL GLFW backend.");
		}
		initialized = true;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public GridStreamStats gridStreamStats() {
		return new GridStreamStats(sharedGridRenderer.streamReuseCount(),
				sharedGridRenderer.streamRebuildCount());
	}

	public void invalidateGridBlock(final int blockX, final int blockY) {
		sharedGridRenderer.invalidateBlock(blockX, blockY);
		// Force the non-instanced fallback to rebuild its cache when used.
		cachedGridDetails = null;
	}

	public QueueStats queueStats() {
		return new QueueStats(auxiliaryTextureLoader.pendingCount()
				+ skyTextureLoader.pendingCount(),
				terrainVboRenderer.pendingPreparationCount(),
				textureUploadsThisFrame + terrainVboRenderer.uploadsThisFrame(),
				deferredTextureDeletes.size() + terrainVboRenderer.pendingDeleteCount(),
				textureDeletesThisFrame + terrainVboRenderer.deletesThisFrame());
	}

	public boolean hasDeferredReadback() {
		return readbackPboPrimed;
	}

	public boolean hasPendingTextureWork() {
		return auxiliaryTextureLoader.hasPending()
				|| skyTextureLoader.hasPending()
				|| terrainVboRenderer.hasPendingWork();
	}

	/** Starts CPU texture decoding before a region becomes active. */
	public void prepareRegionTexture(final File file) {
		if (file != null && file.isFile() && !textures.containsKey(file.getAbsolutePath())
				&& !auxiliaryTextureLoader.isRequested(file))
			auxiliaryTextureLoader.request(file, 4096);
		if (file != null && file.isFile())
			preparedRegionTexturePath = file.getAbsolutePath();
	}

	public boolean isTextureResident(final File file) {
		return file != null && textures.containsKey(file.getAbsolutePath());
	}

	public int desiredAuxiliaryTextureCount() {
		return desiredAuxiliaryTextures;
	}

	public int residentAuxiliaryTextureCount() {
		return residentAuxiliaryTextures;
	}

	public void setBulkTextureLoading(final boolean enabled) {
		bulkTextureLoading = enabled;
	}

	public void setNeighborVisualStyle(final int style) {
		terrainVboRenderer.setVisualStyle(style);
	}

	public void setSkyStyle(final int style) {
		skyStyle = Math.max(0, Math.min(4, style));
	}

	public void setSkyTexture(final File file) {
		skyTextureFile = file;
	}

	public void setMoonTexture(final File file) {
		moonTextureFile = file;
	}

	public void setMoonStarsTexture(final File file) {
		moonStarsTextureFile = file;
	}

	public void setTextureAnisotropy(final int requested) {
		final int normalized = requested >= 16 ? 16 : requested >= 8 ? 8
				: requested >= 4 ? 4 : requested >= 2 ? 2 : 1;
		if (textureAnisotropy != normalized) {
			textureAnisotropy = normalized;
			textureFilteringDirty = true;
		}
	}

	public RenderStats renderStats() {
		return new RenderStats(lastElements, lastDrawCalls);
	}

	public int actualFramebufferSamples() {
		return actualFramebufferSamples;
	}

	public int maximumTextureSize() {
		return maximumTextureSize;
	}

	public boolean isSkyTextureReady() {
		return switch (skyStyle) {
		case 1, 2, 3 -> skyTextureId != 0;
		case 4 -> moonTextureId != 0 && moonStarsTextureId != 0;
		default -> true;
		};
	}

	public boolean hasCompleteSkyTextureSet() {
		return switch (skyStyle) {
		case 1, 2, 3 -> skyTextureFile != null && skyTextureFile.isFile();
		case 4 -> moonTextureFile != null && moonTextureFile.isFile()
				&& moonStarsTextureFile != null && moonStarsTextureFile.isFile();
		default -> true;
		};
	}

	public int readySkyTextureCount() {
		return skyStyle == 4
				? (moonTextureId == 0 ? 0 : 1)
						+ (moonStarsTextureId == 0 ? 0 : 1)
				: (skyStyle >= 1 && skyStyle <= 3) && skyTextureId != 0 ? 1 : 0;
	}

	public int requiredSkyTextureCount() {
		return skyStyle == 4 ? 2 : skyStyle >= 1 && skyStyle <= 3 ? 1 : 0;
	}

	public String openGlVersion() {
		return openGlVersion;
	}

	public String gpuRenderer() {
		return gpuRenderer;
	}

	public RenderedFrame renderRegion(final RegionSnapshot region,
			final int width, final int height) {
		return renderRegion(region, width, height, 58f, 42f, 1f);
	}

	public RenderedFrame renderRegion(final RegionSnapshot region,
			final int width, final int height, final float pitch,
			final float yaw, final float zoom) {
		return renderRegion(region, null, width, height, pitch, yaw, zoom);
	}

	public RenderedFrame renderRegion(final RegionSnapshot region, final File textureFile,
			final int width, final int height, final float pitch,
			final float yaw, final float zoom) {
		return renderRegion(region, textureFile, width, height, pitch, yaw, zoom, -1, -1);
	}

	public RenderedFrame renderRegion(final RegionSnapshot region, final File textureFile,
			final int width, final int height, final float pitch,
			final float yaw, final float zoom, final int selectedX, final int selectedY) {
		final Map<RegionLoadManager.RegionKey, RegionSnapshot> single = Map.of(
				new RegionLoadManager.RegionKey(region.regionX(), region.regionY()), region);
		return renderWindow(region, null, single,
				textureFile == null ? null : textureFile.getParentFile(),
				textureFile != null, false, width, height, pitch, yaw, zoom,
				0f, 0f, selectedX, selectedY, -1, -1, -1, 24, false);
	}

	public RenderedFrame renderWindow(final RegionSnapshot center,
			final DetailedRegionSnapshot details,
			final Map<RegionLoadManager.RegionKey, RegionSnapshot> regions,
			final File textureDirectory, final boolean textured, final boolean geodataGrid,
			final int width, final int height, final float pitch,
			final float yaw, final float zoom, final float panX, final float panY,
			final int selectedX, final int selectedY,
			final int selectedCellX, final int selectedCellY,
			final int selectedLayer, final int visibleGridRange,
			final boolean circularGrid) {
		return renderWindow(center, details, regions, textureDirectory,
				textured, geodataGrid, width, height, pitch, yaw, zoom, panX, panY,
				selectedX, selectedY, selectedCellX, selectedCellY, selectedLayer,
				visibleGridRange, circularGrid, 0);
	}

	public RenderedFrame renderWindow(final RegionSnapshot center,
			final DetailedRegionSnapshot details,
			final Map<RegionLoadManager.RegionKey, RegionSnapshot> regions,
			final File textureDirectory, final boolean textured, final boolean geodataGrid,
			final int width, final int height, final float pitch,
			final float yaw, final float zoom, final float panX, final float panY,
			final int selectedX, final int selectedY,
			final int selectedCellX, final int selectedCellY,
			final int selectedLayer, final int visibleGridRange,
			final boolean circularGrid, final int antialiasingSamples) {
		return renderWindow(center, details, regions, textureDirectory, textured,
				false, geodataGrid, width, height, pitch, yaw, zoom, panX, panY,
				selectedX, selectedY, selectedCellX, selectedCellY, selectedLayer,
				visibleGridRange, circularGrid, antialiasingSamples, true, false);
	}

	public RenderedFrame renderWindow(final RegionSnapshot center,
			final DetailedRegionSnapshot details,
			final Map<RegionLoadManager.RegionKey, RegionSnapshot> regions,
			final File textureDirectory, final boolean textured,
			final boolean auxiliaryTextured, final boolean geodataGrid,
			final int width, final int height, final float pitch,
			final float yaw, final float zoom, final float panX, final float panY,
			final int selectedX, final int selectedY,
			final int selectedCellX, final int selectedCellY,
			final int selectedLayer, final int visibleGridRange,
			final boolean circularGrid, final int antialiasingSamples,
			final boolean transparency, final boolean drawOutline) {
		if (!initialized)
			throw new IllegalStateException("LWJGL backend is not initialized.");
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("Viewport dimensions must be positive.");

		ensureWindow(width, height, antialiasingSamples);
		textureUploadsThisFrame = 0;
		textureDeletesThisFrame = 0;
		for (int deleted = 0; deleted < 2
				&& !deferredTextureDeletes.isEmpty(); deleted++) {
			GL11.glDeleteTextures(deferredTextureDeletes.removeFirst());
			textureDeletesThisFrame++;
		}
		applyTextureFilteringToCache();
		/*
		 * Sky assets are demand-loaded for the selected style only. Switching away
		 * releases the large Milky Way texture on this render thread.
		 */
		ensureSkyTextures();
		GL11.glViewport(0, 0, width, height);
		GL11.glClearColor(0f, 0f, 0f, 1f);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		renderSky(width, height, pitch, yaw);
		terrainVboRenderer.beginFrame();
		final int radius = regions.keySet().stream().mapToInt(key -> Math.max(
				Math.abs(key.x() - center.regionX()),
				Math.abs(key.y() - center.regionY()))).max().orElse(0);
		configureProjection(width, height, pitch, yaw, zoom, radius, panX, panY);
		final float[][] terrainFrustum = readFrustum();
		final Set<String> activeTextures = new HashSet<>();
		if (skyStyle >= 1 && skyStyle <= 3 && skyTextureFile != null)
			activeTextures.add(skyTextureFile.getAbsolutePath());
		if (skyStyle == 4 && moonTextureFile != null)
			activeTextures.add(moonTextureFile.getAbsolutePath());
		if (skyStyle == 4 && moonStarsTextureFile != null)
			activeTextures.add(moonStarsTextureFile.getAbsolutePath());
		final Set<RegionSnapshot> activeTerrainRegions = new HashSet<>();
		int desiredAuxiliaryTextures = 0;
		int residentAuxiliaryTextures = 0;
		if (textured || regions.size() > 1) {
			final ArrayList<Map.Entry<RegionLoadManager.RegionKey, RegionSnapshot>>
					orderedRegions = new ArrayList<>(regions.entrySet());
			/*
			 * The camera can be close to a region border or already crossing it during
			 * Full Map. Prioritize auxiliary meshes and async textures by the camera's
			 * real local position instead of the center region alone.
			 */
			orderedRegions.sort(Comparator
					.comparingDouble((Map.Entry<RegionLoadManager.RegionKey,
							RegionSnapshot> entry) -> auxiliaryDistanceSquared(
									entry.getKey(), center, panX, panY))
					.thenComparingInt(entry -> entry.getKey().x())
					.thenComparingInt(entry -> entry.getKey().y()));
			int uploadsRemaining = bulkTextureLoading ? 4 : 1;
			int auxiliaryIndex = 0;
			for (final Map.Entry<RegionLoadManager.RegionKey, RegionSnapshot> entry
					: orderedRegions) {
				final RegionLoadManager.RegionKey key = entry.getKey();
				final boolean central = key.x() == center.regionX()
						&& key.y() == center.regionY();
				final float terrainOffsetX = (key.x() - center.regionX()) * 2048f;
				final float terrainOffsetZ = (key.y() - center.regionY()) * 2048f;
				activeTerrainRegions.add(entry.getValue());
				final File textureFile = textureDirectory == null ? null
						: new File(textureDirectory, entry.getValue().name() + ".jpg");
				// Auxiliary textures are decoded off the JavaFX thread; until resident,
				// the same terrain remains available through its height mesh.
				int textureId = 0;
				if (central && textureFile != null && textureFile.isFile()) {
					textureId = ensureTexture(textureFile, 4096, activeTextures);
				} else if (!central && auxiliaryTextured && textureFile != null
						&& textureFile.isFile()) {
					desiredAuxiliaryTextures++;
					final String path = textureFile.getAbsolutePath();
					activeTextures.add(path);
					final Integer cached = textures.get(path);
					if (cached != null) {
						textureId = cached.intValue();
						residentAuxiliaryTextures++;
					} else {
						if (!auxiliaryTextureLoader.isRequested(textureFile)
								&& auxiliaryTextureLoader.pendingCount()
								< MAX_PENDING_AUXILIARY_TEXTURES)
							auxiliaryTextureLoader.request(textureFile,
									AUXILIARY_TEXTURE_SIZE);
						if (uploadsRemaining > 0) {
							final AsyncTextureLoader.Prepared prepared =
									auxiliaryTextureLoader.pollReady(textureFile,
											AUXILIARY_TEXTURE_SIZE);
							if (prepared != null) {
								textureId = uploadTexture(prepared.path(), prepared.width(),
										prepared.height(), prepared.pixels());
								uploadsRemaining--;
								auxiliaryTextureUploads++;
								textureUploadsThisFrame++;
							}
						}
					}
				}
				/*
				 * Texture residency follows the loaded neighbor window, not the current
				 * frustum. Camera motion therefore cannot evict and decode the same map
				 * repeatedly; only a real window replacement changes this working set.
				 */
				if (!central && !isTerrainRegionVisible(terrainFrustum,
						terrainOffsetX, terrainOffsetZ))
					continue;
				if (central && !textured)
					continue;
				/*
				 * Adaptive LOD keeps the closest auxiliary region at double density
				 * without multiplying a full 80/120-region window into giant VBOs.
				 */
				final int auxiliaryLod = auxiliaryIndex++;
				final int terrainStep = central ? 1
						: auxiliaryLod < 8 ? 2 : 4;
				terrainVboRenderer.render(entry.getValue(), textureId,
						terrainOffsetX, terrainOffsetZ, terrainStep);
			}
		}
		this.desiredAuxiliaryTextures = desiredAuxiliaryTextures;
		this.residentAuxiliaryTextures = residentAuxiliaryTextures;
		terrainVboRenderer.retain(activeTerrainRegions);
		if (preparedRegionTexturePath != null)
			activeTextures.add(preparedRegionTexturePath);
		auxiliaryTextureLoader.retain(activeTextures);
		skyTextureLoader.retain(activeTextures);
		if (Boolean.getBoolean("geoeditor.streamingSmoke")
				&& desiredAuxiliaryTextures > 0
				&& residentAuxiliaryTextures == desiredAuxiliaryTextures
				&& !auxiliaryTextureLoader.hasPending()
				&& !auxiliaryTextureSmokeReported) {
			System.out.println("GEOEDITOR_AUX_TEXTURES=RESIDENT:"
					+ residentAuxiliaryTextures + ",DESIRED:"
					+ desiredAuxiliaryTextures + ",SIZE:"
					+ AUXILIARY_TEXTURE_SIZE + ",LIMIT:"
					+ MAX_AUXILIARY_TEXTURES);
			auxiliaryTextureSmokeReported = true;
		}
		int nsweTexture = 0;
		if (geodataGrid && details != null && textureDirectory != null) {
			final File nsweFile = new File(textureDirectory.getParentFile(),
					"nswe_" + EditorSettings.NSWE_TEXTURE_ID + ".png");
			if (nsweFile.isFile())
				nsweTexture = ensureTexture(nsweFile, 1024, activeTextures);
			if (nsweTexture != 0) {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, nsweTexture);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
						GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
						GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
				applyTextureAnisotropy();
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
			}
		}
		retainTextures(activeTextures);
		if (geodataGrid && details != null)
			renderGeodataGrid(center, details, pitch, yaw, zoom, panX, panY,
					nsweTexture, visibleGridRange, circularGrid,
					transparency, drawOutline);
		final boolean selectionBlendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
		if (selectionBlendEnabled)
			GL11.glDisable(GL11.GL_BLEND);
		if (gridSelections.isEmpty()) {
			renderCellSelection(center, details, selectedX, selectedY,
					selectedCellX, selectedCellY, selectedLayer, nsweTexture,
					EditorSettings.COLOR_GUI_SELECTED, 0.06f);
		} else {
			final Set<Integer> selectedBlocks = new HashSet<>();
			for (final GridSelection selection : gridSelections)
				selectedBlocks.add((selection.blockY() << 8) | selection.blockX());
			for (final int block : selectedBlocks)
				renderBlockSelectionBackground(center, details, block & 0xff,
						block >>> 8, nsweTexture);
			for (final GridSelection selection : gridSelections)
					renderCellSelection(center, details, selection.blockX(),
						selection.blockY(), selection.cellX(), selection.cellY(),
						selection.layer(), nsweTexture, selectedColor(center,
								selection.blockX(), selection.blockY()), 0.04f);
			renderCellSelection(center, details, selectedX, selectedY,
					selectedCellX, selectedCellY, selectedLayer, nsweTexture,
					EditorSettings.COLOR_GUI_SELECTED, 0.06f);
		}
		if (selectionBlendEnabled)
			GL11.glEnable(GL11.GL_BLEND);
		lastElements = terrainVboRenderer.elements()
				+ (geodataGrid && details != null ? sharedGridRenderer.instanceCount() : 0L);
		lastDrawCalls = terrainVboRenderer.drawCalls()
				+ (geodataGrid && details != null ? 1L : 0L)
				+ (gridSelections.isEmpty() ? (selectedX >= 0 ? 1L : 0L)
						: gridSelections.size());
		return readFramebuffer(width, height);
	}

	private static double auxiliaryDistanceSquared(
			final RegionLoadManager.RegionKey key, final RegionSnapshot center,
			final float cameraX, final float cameraZ) {
		final double regionCenterX =
				(key.x() - center.regionX()) * 2048d + 1024d;
		final double regionCenterZ =
				(key.y() - center.regionY()) * 2048d + 1024d;
		final double dx = regionCenterX - cameraX;
		final double dz = regionCenterZ - cameraZ;
		return dx * dx + dz * dz;
	}

	private static boolean isTerrainRegionVisible(final float[][] frustum,
			final float offsetX, final float offsetZ) {
		final float minimumX = offsetX;
		final float maximumX = offsetX + 2048f;
		final float minimumY = -2048f;
		final float maximumY = 2048f;
		final float minimumZ = offsetZ;
		final float maximumZ = offsetZ + 2048f;
		for (final float[] plane : frustum) {
			final float x = plane[0] >= 0f ? maximumX : minimumX;
			final float y = plane[1] >= 0f ? maximumY : minimumY;
			final float z = plane[2] >= 0f ? maximumZ : minimumZ;
			if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] <= 0f)
				return false;
		}
		return true;
	}

	private RenderedFrame readFramebuffer(final int width, final int height) {
		final int stride = width * 4;
		final int requiredBytes = Math.multiplyExact(stride, height);
		if (readbackBgra == null || readbackBgra.length != requiredBytes)
			readbackBgra = new byte[requiredBytes];
		if (synchronousReadbackRequested) {
			synchronousReadbackRequested = false;
			return readFramebufferSynchronously(width, height);
		}
		if (supportsPixelBufferObjects()) {
			ensureReadbackPbos(requiredBytes);
			final int writeIndex = readbackWriteIndex;
			final int readIndex = readbackPboPrimed ? 1 - writeIndex : writeIndex;
			GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, readbackPbos[writeIndex]);
			GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA,
					GL11.GL_UNSIGNED_BYTE, 0L);
			GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, readbackPbos[readIndex]);
			final ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER,
					GL15.GL_READ_ONLY, requiredBytes, null);
			if (mapped != null) {
				copyFlippedRows(mapped, readbackBgra, width, height);
				GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
			} else {
				GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
				return readFramebufferSynchronously(width, height);
			}
			GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
			readbackPboPrimed = true;
			readbackWriteIndex = 1 - writeIndex;
			if (!readbackPboReported) {
				System.out.println("GEOEDITOR_PBO_READBACK=DOUBLE");
				readbackPboReported = true;
			}
			return new RenderedFrame(width, height, readbackBgra);
		}
		return readFramebufferSynchronously(width, height);
	}

	public void requestSynchronousReadback() {
		synchronousReadbackRequested = true;
	}

	private RenderedFrame readFramebufferSynchronously(final int width,
			final int height) {
		/* A synchronous presentation supersedes every queued PBO frame. Keeping
		 * the old PBO marked as primed would present stale selection state on the
		 * next animation pulse (visible as selection flicker/reappearance). */
		readbackPboPrimed = false;
		final int requiredBytes = Math.multiplyExact(width * 4, height);
		if (readbackPixels == null || readbackPixels.capacity() != requiredBytes)
			readbackPixels = BufferUtils.createByteBuffer(requiredBytes);
		readbackPixels.clear();
		GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA,
				GL11.GL_UNSIGNED_BYTE, readbackPixels);
		copyFlippedRows(readbackPixels, readbackBgra, width, height);
		return new RenderedFrame(width, height, readbackBgra);
	}

	private static void copyFlippedRows(final ByteBuffer source,
			final byte[] destination, final int width, final int height) {
		final int stride = width * 4;
		for (int row = 0; row < height; row++) {
			source.position((height - row - 1) * stride);
			source.get(destination, row * stride, stride);
		}
	}

	private boolean supportsPixelBufferObjects() {
		return GL.getCapabilities().OpenGL21
				|| GL.getCapabilities().GL_ARB_pixel_buffer_object;
	}

	private void ensureReadbackPbos(final int requiredBytes) {
		if (readbackPbos[0] == 0) {
			readbackPbos[0] = GL15.glGenBuffers();
			readbackPbos[1] = GL15.glGenBuffers();
		}
		if (readbackPboBytes == requiredBytes)
			return;
		for (final int pbo : readbackPbos) {
			GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
			GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER,
					requiredBytes, GL15.GL_STREAM_READ);
		}
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
		readbackPboBytes = requiredBytes;
		readbackPboPrimed = false;
		readbackWriteIndex = 0;
	}

	private void renderGeodataGrid(final RegionSnapshot region,
			final DetailedRegionSnapshot details,
			final float pitch, final float yaw, final float cameraY,
			final float cameraX, final float cameraZ,
			final int texture, final int configuredRange,
			final boolean circularGrid, final boolean transparency,
			final boolean drawOutline) {
		final boolean textured = texture != 0;
		if (textured) {
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		} else {
			GL11.glDisable(GL11.GL_TEXTURE_2D);
		}
		/*
		 * Layers must participate in the depth buffer. Disabling depth makes lower
		 * multilayers overwrite upper geometry and creates the irregular patches
		 * seen in the early renderer.
		 */
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		if (transparency) {
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		} else {
			GL11.glDisable(GL11.GL_BLEND);
		}
		final int radius = Math.max(8, Math.min(256, configuredRange));
		if (Boolean.parseBoolean(System.getProperty(
				"geoeditor.sharedGridRenderer", "true"))
				&& sharedGridRenderer.isSupported()) {
			final float[][] sharedFrustum = readFrustum();
			validateFutureVisibilityPlan(region, details, sharedFrustum,
					cameraX, cameraZ);
			sharedGridRenderer.render(region, details, sharedFrustum,
					cameraX, cameraZ, radius, circularGrid, textured,
					transparency, drawOutline);
			if (Boolean.getBoolean("geoeditor.sharedGridSmoke")
					&& !Boolean.getBoolean("geoeditor.range96BenchmarkSmoke"))
				System.out.println("GEOEDITOR_SHARED_GRID=INSTANCES:"
						+ sharedGridRenderer.instanceCount() + ",PREP_US:"
						+ sharedGridRenderer.preparationMicros() + ",UPLOAD_US:"
						+ sharedGridRenderer.uploadMicros() + ",REUSED:"
						+ sharedGridRenderer.streamReused());
			if (textured) {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
				GL11.glDisable(GL11.GL_TEXTURE_2D);
			}
			GL11.glDisable(GL11.GL_BLEND);
			return;
		}
		configureGridShader(textured, circularGrid, cameraX, cameraZ, radius);
		final int cameraBlockX = (int) cameraX >> 3;
		final int cameraBlockY = (int) cameraZ >> 3;
		final int minimumBlockX = Math.max(0, cameraBlockX - radius);
		final int maximumBlockX = Math.min(255, cameraBlockX + radius);
		final int minimumBlockY = Math.max(0, cameraBlockY - radius);
		final int maximumBlockY = Math.min(255, cameraBlockY + radius);
		if (cachedGridDetails != details) {
			clearGridChunks();
			cachedGridDetails = details;
		}
		final long frame = ++gridFrame;
		final float[][] frustum = readFrustum();
		validateFutureVisibilityPlan(region, details, frustum, cameraX, cameraZ);
		final ArrayList<GridChunk> visibleChunks = new ArrayList<>();
		final int minimumChunkX = minimumBlockX / GRID_CHUNK_BLOCKS;
		final int maximumChunkX = maximumBlockX / GRID_CHUNK_BLOCKS;
		final int minimumChunkY = minimumBlockY / GRID_CHUNK_BLOCKS;
		final int maximumChunkY = maximumBlockY / GRID_CHUNK_BLOCKS;
		for (int chunkY = minimumChunkY; chunkY <= maximumChunkY; chunkY++) {
			for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
				final GridChunk chunk = ensureGridChunkBounds(
						region, details, chunkX, chunkY);
				if (!isBoxVisible(frustum, chunk.minimumX, chunk.minimumZ,
						chunk.maximumX, chunk.maximumZ,
						chunk.minimumY, chunk.maximumY))
					continue;
				ensureGridChunkVbo(region, details, chunk);
				chunk.lastVisibleFrame = frame;
				visibleChunks.add(chunk);
			}
		}
		visibleChunks.sort(Comparator.comparingDouble(chunk ->
				-chunk.distanceSquared(cameraX, cameraY, cameraZ)));
		pruneGridChunks(frame);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
		if (textured) {
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}
		for (final GridChunk chunk : visibleChunks) {
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, chunk.vbo);
			GL11.glVertexPointer(3, GL11.GL_FLOAT, 36, 0L);
			GL11.glColorPointer(4, GL11.GL_FLOAT, 36, 12L);
			if (textured)
				GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 36, 28L);
			GL11.glDrawArrays(GL11.GL_QUADS, 0, chunk.vertexCount);
		}
		if (drawOutline) {
			GL20.glUniform1i(gridOutlineUniform, 1);
			GL20.glUniform1i(gridTexturedUniform, 0);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glDepthFunc(GL11.GL_LEQUAL);
			GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
			GL11.glLineWidth(1f);
			for (final GridChunk chunk : visibleChunks) {
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, chunk.vbo);
				GL11.glVertexPointer(3, GL11.GL_FLOAT, 36, 0L);
				GL11.glColorPointer(4, GL11.GL_FLOAT, 36, 12L);
				GL11.glDrawArrays(GL11.GL_QUADS, 0, chunk.vertexCount);
			}
			GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
			GL11.glDepthFunc(GL11.GL_LESS);
			if (transparency)
				GL11.glEnable(GL11.GL_BLEND);
			GL20.glUniform1i(gridOutlineUniform, 0);
			GL20.glUniform1i(gridTexturedUniform, textured ? 1 : 0);
		}
		if (textured)
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL20.glUseProgram(0);
		if (textured) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
		}
		GL11.glDisable(GL11.GL_BLEND);
	}

	private void validateFutureVisibilityPlan(final RegionSnapshot region,
			final DetailedRegionSnapshot details, final float[][] frustum,
			final float cameraX, final float cameraZ) {
		if (visibilityPlanSmokeDone
				|| !Boolean.getBoolean("geoeditor.visibilityPlanSmoke"))
			return;
		visibilityPlanSmokeDone = true;
		final long preparationMicros = futureVisibilitySelector.prepare(region, details);
		final int samples = 200;
		final long[] timings = new long[samples];
		long total = 0L;
		long maximum = 0L;
		for (int sample = 0; sample < samples; sample++) {
			final float sampleX = Math.max(0f, Math.min(2047f,
					cameraX - 320f + sample * 3.2f));
			final long elapsed = futureVisibilitySelector.select(
					frustum, sampleX, cameraZ, 96);
			timings[sample] = elapsed;
			total += elapsed;
			maximum = Math.max(maximum, elapsed);
		}
		java.util.Arrays.sort(timings);
		final long p95 = timings[(int) Math.floor((samples - 1) * 0.95d)];
		System.out.println("GEOEDITOR_VISIBILITY_PLAN=RANGE:96,PREP_US:"
				+ preparationMicros + ",SAMPLES:" + samples
				+ ",AVG_US:" + total / samples + ",P95_US:" + p95
				+ ",MAX_US:" + maximum + ",VISIBLE:"
				+ futureVisibilitySelector.visibleCount());
	}

	private void ensureGridShader() {
		if (gridShaderProgram != 0)
			return;
		final String vertexSource = """
				#version 120
				varying vec2 worldXZ;
				varying vec4 vertexColor;
				varying vec2 textureCoordinates;
				void main() {
					worldXZ = gl_Vertex.xz;
					vertexColor = gl_Color;
					textureCoordinates = gl_MultiTexCoord0.xy;
					gl_Position = ftransform();
				}
				""";
		final String fragmentSource = """
				#version 120
				uniform int circularGrid;
				uniform vec2 gridCenter;
				uniform float gridRadius;
				uniform int textured;
				uniform int outline;
				uniform sampler2D atlas;
				varying vec2 worldXZ;
				varying vec4 vertexColor;
				varying vec2 textureCoordinates;
				void main() {
					if (circularGrid != 0
							&& distance(worldXZ, gridCenter) > gridRadius)
						discard;
					vec4 color = outline != 0
							? vec4(0.015, 0.015, 0.015, 1.0)
							: vertexColor;
					if (outline == 0 && textured != 0)
						color *= texture2D(atlas, textureCoordinates);
					gl_FragColor = color;
				}
				""";
		final int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
		final int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
		gridShaderProgram = GL20.glCreateProgram();
		GL20.glAttachShader(gridShaderProgram, vertexShader);
		GL20.glAttachShader(gridShaderProgram, fragmentShader);
		GL20.glLinkProgram(gridShaderProgram);
		if (GL20.glGetProgrami(gridShaderProgram, GL20.GL_LINK_STATUS)
				== GL11.GL_FALSE)
			throw new IllegalStateException("Grid shader link failed: "
					+ GL20.glGetProgramInfoLog(gridShaderProgram));
		GL20.glDeleteShader(vertexShader);
		GL20.glDeleteShader(fragmentShader);
		gridCircularUniform =
				GL20.glGetUniformLocation(gridShaderProgram, "circularGrid");
		gridCenterUniform =
				GL20.glGetUniformLocation(gridShaderProgram, "gridCenter");
		gridRadiusUniform =
				GL20.glGetUniformLocation(gridShaderProgram, "gridRadius");
		gridTexturedUniform =
				GL20.glGetUniformLocation(gridShaderProgram, "textured");
		gridOutlineUniform =
				GL20.glGetUniformLocation(gridShaderProgram, "outline");
		GL20.glUseProgram(gridShaderProgram);
		GL20.glUniform1i(GL20.glGetUniformLocation(gridShaderProgram, "atlas"), 0);
		GL20.glUseProgram(0);
	}

	private void ensureSkyShader() {
		if (skyShaderProgram != 0 || skyShaderFailed)
			return;
		final String vertexSource = """
				#version 120
				varying vec2 screenPosition;
				void main() {
					screenPosition = gl_Vertex.xy;
					gl_Position = gl_Vertex;
				}
				""";
		final String fragmentSource = """
				#version 120
				uniform int skyStyle;
				uniform float cameraPitch;
				uniform float cameraYaw;
				uniform vec2 viewport;
				uniform sampler2D starsTexture;
				uniform int starsTextureReady;
				uniform sampler2D moonTexture;
				uniform int moonTextureReady;
				uniform sampler2D moonStarsTexture;
				uniform int moonStarsTextureReady;
				varying vec2 screenPosition;
				float hash(vec2 p) {
					return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
				}
				float valueNoise(vec2 p) {
					vec2 cell = floor(p);
					vec2 local = fract(p);
					local = local * local * (3.0 - 2.0 * local);
					return mix(mix(hash(cell), hash(cell + vec2(1.0, 0.0)), local.x),
							mix(hash(cell + vec2(0.0, 1.0)),
									hash(cell + vec2(1.0, 1.0)), local.x), local.y);
				}
				vec2 cubeSkyUv(vec3 direction) {
					vec3 axis = abs(direction);
					if (axis.y >= axis.x && axis.y >= axis.z)
						return direction.xz / max(axis.y, 0.0001) * 0.5 + 0.5;
					if (axis.x >= axis.z)
						return direction.zy / max(axis.x, 0.0001) * 0.5 + 0.5;
					return direction.xy / max(axis.z, 0.0001) * 0.5 + 0.5;
				}
				void main() {
					const float tanHalfFov = 0.41421356237;
					const float pi = 3.14159265359;
					float pitch = radians(cameraPitch);
					float yaw = radians(cameraYaw);
					float aspect = viewport.x / max(viewport.y, 1.0);
					vec3 viewRay = normalize(vec3(screenPosition.x * aspect * tanHalfFov,
							screenPosition.y * tanHalfFov, -1.0));
					float cp = cos(pitch);
					float sp = sin(pitch);
					vec3 pitchedRay = vec3(viewRay.x,
							viewRay.y * cp - viewRay.z * sp,
							viewRay.y * sp + viewRay.z * cp);
					float cy = cos(yaw);
					float sy = sin(yaw);
					vec3 worldRay = normalize(vec3(
							pitchedRay.x * cy + pitchedRay.z * sy,
							pitchedRay.y,
							-pitchedRay.x * sy + pitchedRay.z * cy));
					float rayUp = worldRay.y;
					if (rayUp <= 0.0 && skyStyle != 2) {
						gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
						return;
					}
					if (skyStyle == 2 && rayUp <= -0.24) {
						gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
						return;
					}
					float horizonFade = skyStyle == 2
							? smoothstep(-0.12, 0.035, rayUp)
							: smoothstep(0.0, 0.045, rayUp);
					vec2 skyUv = vec2(atan(worldRay.z, worldRay.x) / (2.0 * pi) + 0.5,
							asin(clamp(worldRay.y, -1.0, 1.0)) / pi + 0.5);
					float altitude = clamp(rayUp * 2.4, 0.0, 1.0);
					vec3 color = mix(vec3(0.003, 0.008, 0.018),
							vec3(0.020, 0.055, 0.115), altitude);
					if (skyStyle == 1) {
						if (starsTextureReady != 0) {
							vec3 deepTexture = texture2D(starsTexture,
									vec2(fract(skyUv.x),
											clamp(skyUv.y, 0.002, 0.998))).rgb;
							float deepSignal = max(deepTexture.r,
									max(deepTexture.g, deepTexture.b));
							float deepStar = smoothstep(0.08, 0.55, deepSignal);
							float deepLuminance = dot(deepTexture,
									vec3(0.2126, 0.7152, 0.0722));
							float deepNebula = smoothstep(0.035, 0.20, deepLuminance)
									* (1.0 - deepStar * 0.80);
							vec3 navyPhoto = deepTexture * vec3(0.060, 0.115, 0.205)
									+ deepTexture * deepStar * vec3(0.45, 0.55, 0.72)
									+ deepTexture * deepNebula * vec3(0.018, 0.035, 0.070)
									+ vec3(deepStar) * vec3(0.12, 0.19, 0.31);
							color = vec3(0.0025, 0.0090, 0.0220) + navyPhoto;
						}
					} else if (skyStyle == 2) {
						if (starsTextureReady != 0) {
							/*
							 * Rotate the panorama so its equator centre points at
							 * world-up. Its poles stay on the horizon, keeping the
							 * galactic core stable at the zenith without pinching.
							 */
							vec3 starsRay = normalize(vec3(worldRay.y,
									-worldRay.x, worldRay.z));
							vec2 starsUv = vec2(
									atan(starsRay.z, starsRay.x) / (2.0 * pi) + 0.5,
									asin(clamp(starsRay.y, -1.0, 1.0)) / pi + 0.5);
							vec2 wrappedStarsUv = vec2(fract(starsUv.x),
									clamp(starsUv.y, 0.002, 0.998));
							vec3 textureColor = texture2D(starsTexture,
									wrappedStarsUv).rgb;
							float starSignal = max(textureColor.r,
									max(textureColor.g, textureColor.b));
							float starCore = smoothstep(0.42, 0.90, starSignal);
							float luminance = dot(textureColor,
									vec3(0.2126, 0.7152, 0.0722));
							float milkyWayGlow = smoothstep(0.025, 0.20, luminance)
									* (1.0 - starCore * 0.72);
							vec3 photographicSky = textureColor * vec3(0.92, 0.96, 1.04)
									+ textureColor * starCore * 0.08
									+ textureColor * milkyWayGlow * vec3(0.05, 0.08, 0.14);
							color = mix(vec3(0.0015, 0.0035, 0.0080),
									photographicSky, horizonFade);
						} else {
							vec2 starGrid = skyUv * vec2(1400.0, 700.0);
							vec2 cell = floor(starGrid);
							vec2 local = fract(starGrid);
							vec2 center = vec2(hash(cell + 7.1), hash(cell + 29.7));
							float point = 1.0 - smoothstep(0.025, 0.11,
									length(local - center));
							color += vec3(step(0.996, hash(cell + 53.2)) * point * 0.62);
						}
					} else if (skyStyle == 3) {
						if (starsTextureReady != 0) {
							vec3 horizonTexture = texture2D(starsTexture,
									vec2(fract(skyUv.x),
											clamp(skyUv.y, 0.002, 0.998))).rgb;
							float horizonSignal = max(horizonTexture.r,
									max(horizonTexture.g, horizonTexture.b));
							float horizonStar = smoothstep(0.32, 0.86, horizonSignal);
							color += horizonTexture * horizonStar
									* vec3(0.14, 0.55, 0.65);
							color += vec3(horizonStar) * vec3(0.04, 0.17, 0.21);
						}
						float band = exp(-rayUp * 10.0);
						color += vec3(0.0, 0.12, 0.18)
								* smoothstep(0.0, 0.92, band);
					} else if (skyStyle == 4) {
						if (moonStarsTextureReady != 0) {
							vec3 sparseTexture = texture2D(moonStarsTexture,
									vec2(fract(skyUv.x),
											clamp(skyUv.y, 0.002, 0.998))).rgb;
							float sparseSignal = max(sparseTexture.r,
									max(sparseTexture.g, sparseTexture.b));
							float sparseCore = smoothstep(0.48, 0.89, sparseSignal);
							color += sparseTexture * sparseCore * 0.48;
						}
						vec3 moonDirection = normalize(vec3(-0.65, 0.55, -0.52));
						float moonDot = dot(worldRay, moonDirection);
						float halo = smoothstep(0.99915, 0.99982, moonDot) * 0.040;
						float moon = smoothstep(0.99976, 0.99984, moonDot);
						vec3 moonRight = normalize(cross(vec3(0.0, 1.0, 0.0), moonDirection));
						vec3 moonUp = normalize(cross(moonDirection, moonRight));
						vec2 moonUv = vec2(dot(worldRay, moonRight), dot(worldRay, moonUp)) / 0.0195;
						float detail = 0.84 + valueNoise(moonUv * 3.2) * 0.11
								+ valueNoise(moonUv * 9.0) * 0.05;
						float craters = (1.0 - smoothstep(0.09, 0.18,
								distance(moonUv, vec2(-0.28, 0.18)))) * 0.10;
						craters += (1.0 - smoothstep(0.055, 0.13,
								distance(moonUv, vec2(0.34, -0.20)))) * 0.08;
						craters += (1.0 - smoothstep(0.045, 0.11,
								distance(moonUv, vec2(0.08, 0.38)))) * 0.06;
						color += vec3(0.48, 0.52, 0.58) * halo;
						vec3 lunarColor = vec3(0.68, 0.69, 0.68)
								* max(0.58, detail - craters);
						if (moonTextureReady != 0) {
							float lunarRadius = dot(moonUv, moonUv);
							float lunarDepth = sqrt(max(0.0, 1.0 - lunarRadius));
							vec3 lunarNormal = normalize(vec3(
									moonUv.x, moonUv.y, lunarDepth));
							vec2 lunarUv = vec2(
									atan(lunarNormal.x, lunarNormal.z)
											/ (2.0 * pi) + 0.5,
									asin(clamp(lunarNormal.y, -1.0, 1.0))
											/ pi + 0.5);
							lunarColor = texture2D(moonTexture, lunarUv).rgb * 0.82;
						}
						color = mix(color, lunarColor, moon * 0.90);
					}
					float dither = (hash(floor(skyUv * vec2(8192.0, 4096.0))) - 0.5)
							/ 255.0;
					color += vec3(dither);
					if (skyStyle == 2) {
						float lowerFade = smoothstep(-0.24, -0.08, rayUp);
						gl_FragColor = vec4(color * lowerFade, 1.0);
					} else {
						gl_FragColor = vec4(color * horizonFade, 1.0);
					}
				}
				""";
		try {
			final int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
			final int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
			skyShaderProgram = GL20.glCreateProgram();
			GL20.glAttachShader(skyShaderProgram, vertexShader);
			GL20.glAttachShader(skyShaderProgram, fragmentShader);
			GL20.glLinkProgram(skyShaderProgram);
			GL20.glDeleteShader(vertexShader);
			GL20.glDeleteShader(fragmentShader);
			if (GL20.glGetProgrami(skyShaderProgram, GL20.GL_LINK_STATUS)
					== GL11.GL_FALSE)
				throw new IllegalStateException("Sky shader link failed: "
						+ GL20.glGetProgramInfoLog(skyShaderProgram));
			skyStyleUniform = GL20.glGetUniformLocation(skyShaderProgram, "skyStyle");
			skyPitchUniform = GL20.glGetUniformLocation(skyShaderProgram, "cameraPitch");
			skyYawUniform = GL20.glGetUniformLocation(skyShaderProgram, "cameraYaw");
			skyViewportUniform = GL20.glGetUniformLocation(skyShaderProgram, "viewport");
			starsTextureUniform = GL20.glGetUniformLocation(
					skyShaderProgram, "starsTexture");
			starsTextureReadyUniform = GL20.glGetUniformLocation(
					skyShaderProgram, "starsTextureReady");
			moonTextureUniform = GL20.glGetUniformLocation(skyShaderProgram,
					"moonTexture");
			moonTextureReadyUniform = GL20.glGetUniformLocation(
					skyShaderProgram, "moonTextureReady");
			moonStarsTextureUniform = GL20.glGetUniformLocation(skyShaderProgram,
					"moonStarsTexture");
			moonStarsTextureReadyUniform = GL20.glGetUniformLocation(
					skyShaderProgram, "moonStarsTextureReady");
		} catch (final RuntimeException exception) {
			if (skyShaderProgram != 0) {
				GL20.glDeleteProgram(skyShaderProgram);
				skyShaderProgram = 0;
			}
			skyShaderFailed = true;
			System.err.println("Sky disabled; using Black fallback: "
					+ exception.getMessage());
		}
	}

	private void renderSky(final int width, final int height, final float pitch,
			final float yaw) {
		if (skyStyle == 0 || skyShaderFailed)
			return;
		ensureSkyShader();
		if (skyShaderProgram == 0)
			return;
		final boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
		final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
		final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();
		GL20.glUseProgram(skyShaderProgram);
		GL20.glUniform1i(skyStyleUniform, skyStyle);
		GL20.glUniform1f(skyPitchUniform, pitch);
		GL20.glUniform1f(skyYawUniform, yaw);
		GL20.glUniform2f(skyViewportUniform, width, height);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, skyTextureId);
		GL20.glUniform1i(starsTextureUniform, 0);
		GL20.glUniform1i(starsTextureReadyUniform, skyTextureId == 0 ? 0 : 1);
		final int moonTextureUnit = 1;
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + moonTextureUnit);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, moonTextureId);
		GL20.glUniform1i(moonTextureUniform, moonTextureUnit);
		GL20.glUniform1i(moonTextureReadyUniform, moonTextureId == 0 ? 0 : 1);
		final int moonStarsTextureUnit = 2;
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + moonStarsTextureUnit);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, moonStarsTextureId);
		GL20.glUniform1i(moonStarsTextureUniform, moonStarsTextureUnit);
		GL20.glUniform1i(moonStarsTextureReadyUniform,
				moonStarsTextureId == 0 ? 0 : 1);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glVertex2f(-1f, -1f);
		GL11.glVertex2f(1f, -1f);
		GL11.glVertex2f(1f, 1f);
		GL11.glVertex2f(-1f, 1f);
		GL11.glEnd();
		for (int textureUnit = moonStarsTextureUnit; textureUnit > 0; textureUnit--) {
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		}
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		GL20.glUseProgram(0);
		GL11.glDepthMask(depthWrite);
		if (depthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
		if (blend) GL11.glEnable(GL11.GL_BLEND);
		if (texture) GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1f, 1f, 1f, 1f);
	}

	private void ensureSkyTextures() {
		if (skyStyle < 1 || skyStyle > 3) {
			skyTextureId = 0;
		} else if (skyTextureId == 0 && skyTextureFile != null
				&& skyTextureFile.isFile()) {
			final int maximum = Math.max(1024,
					Math.min(16384, maximumTextureSize));
			if (!textures.containsKey(skyTextureFile.getAbsolutePath())
					&& !skyTextureLoader.isRequested(skyTextureFile))
				skyTextureLoader.request(skyTextureFile, maximum);
			skyTextureId = ensureTexture(skyTextureFile, maximum,
					new HashSet<>(), skyTextureLoader);
			if (skyTextureId != 0) {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, skyTextureId);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
						GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
						GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			}
		}
		if (skyStyle != 4) {
			moonTextureId = 0;
			moonStarsTextureId = 0;
		} else if (moonTextureId == 0 && moonTextureFile != null
				&& moonTextureFile.isFile()) {
			final int maximum = Math.max(1024,
					Math.min(16384, maximumTextureSize));
			if (!textures.containsKey(moonTextureFile.getAbsolutePath())
					&& !skyTextureLoader.isRequested(moonTextureFile))
				skyTextureLoader.request(moonTextureFile, maximum);
			moonTextureId = ensureTexture(moonTextureFile, maximum,
					new HashSet<>(), skyTextureLoader);
			if (moonTextureId != 0) {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, moonTextureId);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
						GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
						GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			}
		}
		if (skyStyle == 4 && moonStarsTextureId == 0 && moonStarsTextureFile != null
				&& moonStarsTextureFile.isFile()) {
			final int maximum = Math.max(1024,
					Math.min(16384, maximumTextureSize));
			if (!textures.containsKey(moonStarsTextureFile.getAbsolutePath())
					&& !skyTextureLoader.isRequested(moonStarsTextureFile))
				skyTextureLoader.request(moonStarsTextureFile, maximum);
			moonStarsTextureId = ensureTexture(moonStarsTextureFile, maximum,
					new HashSet<>(), skyTextureLoader);
			if (moonStarsTextureId != 0) {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, moonStarsTextureId);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
						GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
						GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			}
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	private void configureGridShader(final boolean textured,
			final boolean circularGrid, final float cameraX,
			final float cameraZ, final int range) {
		ensureGridShader();
		GL20.glUseProgram(gridShaderProgram);
		GL20.glUniform1i(gridCircularUniform, circularGrid ? 1 : 0);
		GL20.glUniform2f(gridCenterUniform, (int) cameraX, (int) cameraZ);
		GL20.glUniform1f(gridRadiusUniform, range * 8f);
		GL20.glUniform1i(gridTexturedUniform, textured ? 1 : 0);
		GL20.glUniform1i(gridOutlineUniform, 0);
	}

	private static int compileShader(final int type, final String source) {
		final int shader = GL20.glCreateShader(type);
		GL20.glShaderSource(shader, source);
		GL20.glCompileShader(shader);
		if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
			throw new IllegalStateException("Grid shader compile failed: "
					+ GL20.glGetShaderInfoLog(shader));
		return shader;
	}

	private GridChunk ensureGridChunkBounds(final RegionSnapshot region,
			final DetailedRegionSnapshot details, final int chunkX, final int chunkY) {
		final int key = chunkY * 32 + chunkX;
		final GridChunk cached = gridChunks.get(key);
		if (cached != null)
			return cached;
		final int minimumBlockX = chunkX * GRID_CHUNK_BLOCKS;
		final int minimumBlockY = chunkY * GRID_CHUNK_BLOCKS;
		final int maximumBlockX = Math.min(255,
				minimumBlockX + GRID_CHUNK_BLOCKS - 1);
		final int maximumBlockY = Math.min(255,
				minimumBlockY + GRID_CHUNK_BLOCKS - 1);
		float minimum = Float.POSITIVE_INFINITY;
		float maximum = Float.NEGATIVE_INFINITY;
		for (int blockY = minimumBlockY; blockY <= maximumBlockY; blockY++) {
			for (int blockX = minimumBlockX; blockX <= maximumBlockX; blockX++) {
				final byte type = region.blockType(blockX, blockY);
				if (type == 0) {
					final int localX = blockX * 8;
					final int localY = blockY * 8;
					final float height = details.height(localX, localY, 0) / 16f;
					minimum = Math.min(minimum, height - 0.2f);
					maximum = Math.max(maximum, height);
					continue;
				}
				for (int cellY = 0; cellY < 8; cellY++) {
					for (int cellX = 0; cellX < 8; cellX++) {
						final int localX = blockX * 8 + cellX;
						final int localY = blockY * 8 + cellY;
						for (int layer = 0;
								layer < details.layerCount(localX, localY); layer++) {
							final float height = details.height(localX, localY, layer) / 16f;
							minimum = Math.min(minimum, height - 0.2f);
							maximum = Math.max(maximum, height);
						}
					}
				}
			}
		}
		final GridChunk chunk = new GridChunk(chunkX, chunkY,
				minimumBlockX * 8f, (maximumBlockX + 1) * 8f,
				minimumBlockY * 8f, (maximumBlockY + 1) * 8f,
				minimum == Float.POSITIVE_INFINITY ? -0.2f : minimum,
				maximum == Float.NEGATIVE_INFINITY ? 0f : maximum);
		gridChunks.put(key, chunk);
		return chunk;
	}

	private static void ensureGridChunkVbo(final RegionSnapshot region,
			final DetailedRegionSnapshot details, final GridChunk chunk) {
		if (chunk.vbo != 0)
			return;
		final int minimumBlockX = chunk.chunkX * GRID_CHUNK_BLOCKS;
		final int minimumBlockY = chunk.chunkY * GRID_CHUNK_BLOCKS;
		final int maximumBlockX = Math.min(255,
				minimumBlockX + GRID_CHUNK_BLOCKS - 1);
		final int maximumBlockY = Math.min(255,
				minimumBlockY + GRID_CHUNK_BLOCKS - 1);
		final ArrayList<GridCell> cells = new ArrayList<>();
		for (int blockY = minimumBlockY; blockY <= maximumBlockY; blockY++) {
			for (int blockX = minimumBlockX; blockX <= maximumBlockX; blockX++) {
				final byte type = region.blockType(blockX, blockY);
				if (type == 0) {
					final int localX = blockX * 8;
					final int localY = blockY * 8;
					cells.add(new GridCell(blockX * 8f, blockY * 8f, 8f,
							details.height(localX, localY, 0) / 16f,
							details.nswe(localX, localY, 0), type,
							blockX, blockY));
					continue;
				}
				for (int cellY = 0; cellY < 8; cellY++) {
					for (int cellX = 0; cellX < 8; cellX++) {
						final int localX = blockX * 8 + cellX;
						final int localY = blockY * 8 + cellY;
						for (int layer = 0;
								layer < details.layerCount(localX, localY); layer++)
							cells.add(new GridCell(localX, localY, 1f,
									details.height(localX, localY, layer) / 16f,
									details.nswe(localX, localY, layer), type,
									blockX, blockY));
					}
				}
			}
		}
		final int vertexCount = cells.size() * 24;
		final FloatBuffer vertices = BufferUtils.createFloatBuffer(vertexCount * 9);
		for (final GridCell cell : cells)
			putCell(vertices, cell);
		vertices.flip();
		chunk.vbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, chunk.vbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		chunk.vertexCount = vertexCount;
	}

	private void pruneGridChunks(final long frame) {
		if (gridChunks.size() <= MAX_GRID_CHUNKS)
			return;
		final var iterator = gridChunks.entrySet().iterator();
		while (gridChunks.size() > MAX_GRID_CHUNKS && iterator.hasNext()) {
			final GridChunk chunk = iterator.next().getValue();
			if (chunk.lastVisibleFrame == frame)
				continue;
			if (chunk.vbo != 0)
				GL15.glDeleteBuffers(chunk.vbo);
			iterator.remove();
		}
	}

	private void clearGridChunks() {
		for (final GridChunk chunk : gridChunks.values())
			if (chunk.vbo != 0)
				GL15.glDeleteBuffers(chunk.vbo);
		gridChunks.clear();
	}

	private float[][] readFrustum() {
		frustumProjection.clear();
		frustumModelView.clear();
		GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, frustumProjection);
		GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, frustumModelView);
		multiplyMatrices(frustumProjection, frustumModelView, frustumClip);
		extractPlane(frustumPlanes[0], frustumClip, 3, 0, 1f);
		extractPlane(frustumPlanes[1], frustumClip, 3, 0, -1f);
		extractPlane(frustumPlanes[2], frustumClip, 3, 1, 1f);
		extractPlane(frustumPlanes[3], frustumClip, 3, 1, -1f);
		extractPlane(frustumPlanes[4], frustumClip, 3, 2, 1f);
		extractPlane(frustumPlanes[5], frustumClip, 3, 2, -1f);
		return frustumPlanes;
	}

	private static void multiplyMatrices(final FloatBuffer left,
			final FloatBuffer right, final float[] result) {
		java.util.Arrays.fill(result, 0f);
		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				for (int index = 0; index < 4; index++)
					result[column * 4 + row] += left.get(index * 4 + row)
							* right.get(column * 4 + index);
			}
		}
	}

	private static void extractPlane(final float[] plane, final float[] clip,
			final int baseRow, final int axisRow, final float direction) {
		float lengthSquared = 0f;
		for (int column = 0; column < 4; column++) {
			plane[column] = clip[column * 4 + baseRow]
					+ direction * clip[column * 4 + axisRow];
			if (column < 3)
				lengthSquared += plane[column] * plane[column];
		}
		final float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
		for (int i = 0; i < 4; i++)
			plane[i] *= inverseLength;
	}

	private static boolean isBoxVisible(final float[][] frustum,
			final float minimumX, final float minimumZ,
			final float maximumX, final float maximumZ,
			final float minimumY, final float maximumY) {
		for (final float[] plane : frustum) {
			final float x = plane[0] >= 0f ? maximumX : minimumX;
			final float y = plane[1] >= 0f ? maximumY : minimumY;
			final float z = plane[2] >= 0f ? maximumZ : minimumZ;
			if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] <= 0f)
				return false;
		}
		return true;
	}

	private static void putCell(final FloatBuffer target, final GridCell cell) {
		final float normal = 1f;
		final float alternate = 0.85f;
		final float intensity = cell.blockX() % 2 != cell.blockY() % 2
				&& cell.type() != 0 ? alternate : normal;
		final int legacyColor = switch (cell.type()) {
		case 0 -> EditorSettings.COLOR_FLAT_NORMAL;
		case 1 -> EditorSettings.COLOR_COMPLEX_NORMAL;
		default -> EditorSettings.COLOR_MULTILAYER_NORMAL;
		};
		final float red = ((legacyColor >>> 16) & 0xff) / 255f * intensity;
		final float green = ((legacyColor >>> 8) & 0xff) / 255f * intensity;
		final float blue = (legacyColor & 0xff) / 255f * intensity;
		final float x = cell.x();
		final float z = cell.z();
		final float size = cell.size();
		final float height = cell.height();
		/*
		 * Match GLCellRenderer exactly: both 1x1 cells and 8x8 Flat blocks use a
		 * fixed 0.1 world-unit border. Scaling the inset with the block size made
		 * Flat blocks shrink to 6.72 units and produced the large irregular gaps.
		 */
		final float inset = 0.1f;
		final float x0 = x + inset;
		final float z0 = z + inset;
		final float x1 = x + size - inset;
		final float z1 = z + size - inset;
		final float atlasSize = 0.25f;
		final short nswe = cell.nswe();
		final float u0 = (nswe / 4) * atlasSize;
		final float u1 = u0 + atlasSize;
		/*
		 * ImageIO rows are uploaded bottom-up by ensureTexture. The legacy
		 * TextureIO path keeps PNG rows top-down, so its raw V coordinates must
		 * be mirrored here. Without this compensation every NSWE value sampled
		 * the wrong atlas row and cells appeared to carry the same marking.
		 */
		final float legacyV0 = (nswe % 4) * atlasSize;
		final float legacyV1 = legacyV0 + atlasSize;
		final float v0 = 1f - legacyV0;
		final float v1 = 1f - legacyV1;
		final float bottom = height - 0.2f;
		putQuad(target, red, green, blue, x0, bottom, z0, x0, height, z0,
				x1, height, z0, x1, bottom, z0, 0f, 0f, 0f, 0f);
		putQuad(target, red, green, blue, x1, bottom, z0, x1, height, z0,
				x1, height, z1, x1, bottom, z1, 0f, 0f, 0f, 0f);
		putQuad(target, red, green, blue, x1, bottom, z1, x1, height, z1,
				x0, height, z1, x0, bottom, z1, 0f, 0f, 0f, 0f);
		putQuad(target, red, green, blue, x0, bottom, z1, x0, height, z1,
				x0, height, z0, x0, bottom, z0, 0f, 0f, 0f, 0f);
		putQuad(target, red, green, blue, x0, bottom, z1, x0, bottom, z0,
				x1, bottom, z0, x1, bottom, z1, 0f, 0f, 0f, 0f);
		putQuad(target, red, green, blue, x0, height, z0, x0, height, z1,
				x1, height, z1, x1, height, z0, u0, v0, u1, v1);
	}

	private static void putQuad(final FloatBuffer target,
			final float red, final float green, final float blue,
			final float x0, final float y0, final float z0,
			final float x1, final float y1, final float z1,
			final float x2, final float y2, final float z2,
			final float x3, final float y3, final float z3,
			final float u0, final float v0, final float u1, final float v1) {
		putVertex(target, x0, y0, z0, red, green, blue, u0, v0);
		putVertex(target, x1, y1, z1, red, green, blue, u0, v1);
		putVertex(target, x2, y2, z2, red, green, blue, u1, v1);
		putVertex(target, x3, y3, z3, red, green, blue, u1, v0);
	}

	private static void putVertex(final FloatBuffer target,
			final float x, final float y, final float z,
			final float red, final float green, final float blue,
			final float u, final float v) {
		target.put(x).put(y).put(z);
		target.put(red).put(green).put(blue).put(ACTIVE_GRID_ALPHA);
		target.put(u).put(v);
	}

	public int pickBlock(final RegionSnapshot region,
			final DetailedRegionSnapshot details,
			final int pixelX, final int pixelY, final int width, final int height,
			final float pitch, final float yaw, final float cameraHeight,
			final float cameraX, final float cameraZ, final int visibleGridRange,
			final boolean circularGrid) {
		ensureWindow(width, height, framebufferSamples);
		GL11.glViewport(0, 0, width, height);
		configureProjection(width, height, pitch, yaw, cameraHeight, 0,
				cameraX, cameraZ);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_DITHER);
		GL11.glDisable(GL13.GL_MULTISAMPLE);
		GL11.glClearColor(0f, 0f, 0f, 1f);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		final int range = Math.max(8, Math.min(256, visibleGridRange));
		configureGridShader(false, circularGrid, cameraX, cameraZ, range);
		futureVisibilitySelector.prepare(region, details);
		futureVisibilitySelector.select(readFrustum(), cameraX, cameraZ, range);
		for (int visible = 0; visible < futureVisibilitySelector.visibleCount(); visible++) {
			final int block = futureVisibilitySelector.visibleBlockAt(visible);
			final int x = block & 255;
			final int y = block >>> 8;
			final int id = block + 1;
			GL11.glColor3ub((byte) (id & 0xff),
					(byte) ((id >>> 8) & 0xff), (byte) ((id >>> 16) & 0xff));
			renderPickingBlock(region, details, x, y);
		}
		GL20.glUseProgram(0);
		final int id = readNearestPickId(pixelX, pixelY, width, height, 3);
		GL11.glEnable(GL11.GL_DITHER);
		if (actualFramebufferSamples > 0)
			GL11.glEnable(GL13.GL_MULTISAMPLE);
		return id;
	}

	private static void renderPickingBlock(final RegionSnapshot region,
			final DetailedRegionSnapshot details, final int blockX, final int blockY) {
		if (details == null)
			return;
		if (region.blockType(blockX, blockY) == 0) {
			renderPickingTop(blockX * 8f, blockY * 8f, 8f,
					details.height(blockX * 8, blockY * 8, 0) / 16f);
			return;
		}
		for (int cellY = 0; cellY < 8; cellY++) {
			for (int cellX = 0; cellX < 8; cellX++) {
				final int localX = blockX * 8 + cellX;
				final int localY = blockY * 8 + cellY;
				for (int layer = 0; layer < details.layerCount(localX, localY); layer++)
					renderPickingTop(localX, localY, 1f,
							details.height(localX, localY, layer) / 16f);
			}
		}
	}

	public int pickCell(final RegionSnapshot region,
			final DetailedRegionSnapshot details, final int blockX, final int blockY,
			final int pixelX, final int pixelY, final int width, final int height,
			final float pitch, final float yaw, final float cameraHeight,
			final float cameraX, final float cameraZ,
			final int visibleGridRange, final boolean circularGrid) {
		if (blockX < 0 || blockY < 0)
			return -1;
		if (region.blockType(blockX, blockY) == 0)
			return 0;
		ensureWindow(width, height, framebufferSamples);
		GL11.glViewport(0, 0, width, height);
		configureProjection(width, height, pitch, yaw, cameraHeight, 0,
				cameraX, cameraZ);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_DITHER);
		GL11.glDisable(GL13.GL_MULTISAMPLE);
		GL11.glClearColor(0f, 0f, 0f, 1f);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		configureGridShader(false, circularGrid, cameraX, cameraZ,
				Math.max(8, Math.min(256, visibleGridRange)));
		for (int cellY = 0; cellY < 8; cellY++) {
			for (int cellX = 0; cellX < 8; cellX++) {
				final int localX = blockX * 8 + cellX;
				final int localY = blockY * 8 + cellY;
				for (int layer = 0; layer < details.layerCount(localX, localY); layer++) {
					final int id = (layer << 6) | (cellY * 8 + cellX);
					final int encoded = id + 1;
					GL11.glColor3ub((byte) (encoded & 0xff),
							(byte) ((encoded >>> 8) & 0xff),
							(byte) ((encoded >>> 16) & 0xff));
					renderPickingTop(localX, localY, 1f,
							details.height(localX, localY, layer) / 16f);
				}
			}
		}
		GL20.glUseProgram(0);
		final int id = readNearestPickId(pixelX, pixelY, width, height, 3);
		GL11.glEnable(GL11.GL_DITHER);
		if (actualFramebufferSamples > 0)
			GL11.glEnable(GL13.GL_MULTISAMPLE);
		return id == 0 ? -1 : id - 1;
	}

	private static int readNearestPickId(final int pixelX, final int pixelY,
			final int width, final int height, final int radius) {
		final int x0 = Math.max(0, pixelX - radius);
		final int x1 = Math.min(width - 1, pixelX + radius);
		final int glCenterY = height - pixelY - 1;
		final int y0 = Math.max(0, glCenterY - radius);
		final int y1 = Math.min(height - 1, glCenterY + radius);
		final int readWidth = x1 - x0 + 1;
		final int readHeight = y1 - y0 + 1;
		final ByteBuffer pixels = BufferUtils.createByteBuffer(
				readWidth * readHeight * 3);
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glReadPixels(x0, y0, readWidth, readHeight,
				GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
		int nearestId = 0;
		int nearestDistance = Integer.MAX_VALUE;
		for (int y = 0; y < readHeight; y++) {
			for (int x = 0; x < readWidth; x++) {
				final int offset = (y * readWidth + x) * 3;
				final int id = (pixels.get(offset) & 0xff)
						| ((pixels.get(offset + 1) & 0xff) << 8)
						| ((pixels.get(offset + 2) & 0xff) << 16);
				if (id == 0)
					continue;
				final int dx = x0 + x - pixelX;
				final int dy = y0 + y - glCenterY;
				final int distance = dx * dx + dy * dy;
				if (distance < nearestDistance) {
					nearestDistance = distance;
					nearestId = id;
				}
			}
		}
		return nearestId;
	}

	private static void renderPickingTop(final float x, final float z,
			final float size, final float height) {
		final float x0 = x + 0.1f;
		final float x1 = x + size - 0.1f;
		final float z0 = z + 0.1f;
		final float z1 = z + size - 0.1f;
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glVertex3f(x0, height, z0);
		GL11.glVertex3f(x0, height, z1);
		GL11.glVertex3f(x1, height, z1);
		GL11.glVertex3f(x1, height, z0);
		GL11.glEnd();
	}

	private static void renderCellSelection(final RegionSnapshot region,
			final DetailedRegionSnapshot details,
			final int blockX, final int blockY,
			final int cellX, final int cellY, final int selectedLayer,
			final int texture, final int color, final float lift) {
		if (details == null || blockX < 0 || blockY < 0
				|| cellX < 0 || cellY < 0)
			return;
		final boolean flat = region.blockType(blockX, blockY) == 0;
		final int localX = blockX * 8 + (flat ? 0 : cellX);
		final int localY = blockY * 8 + (flat ? 0 : cellY);
		final int layerCount = details.layerCount(localX, localY);
		if (layerCount == 0)
			return;
		final int layer = Math.min(Math.max(0, selectedLayer), layerCount - 1);
		final float y = details.height(localX, localY, layer) / 16f + lift;
		final float x0 = localX + 0.1f;
		final float x1 = localX + (flat ? 7.9f : 0.9f);
		final float z0 = localY + 0.1f;
		final float z1 = localY + (flat ? 7.9f : 0.9f);
		final short nswe = details.nswe(localX, localY, layer);
		final float atlas = 0.25f;
		final float u0 = (nswe / 4) * atlas;
		final float u1 = u0 + atlas;
		final float legacyV0 = (nswe % 4) * atlas;
		final float legacyV1 = legacyV0 + atlas;
		final float v0 = 1f - legacyV0;
		final float v1 = 1f - legacyV1;
		if (texture != 0) {
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		}
		final float legacyAlpha = 0.7f;
		GL11.glColor4f(((color >>> 16) & 0xff) / 255f * legacyAlpha,
				((color >>> 8) & 0xff) / 255f * legacyAlpha,
				(color & 0xff) / 255f * legacyAlpha, 1f);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(u0, v0);
		GL11.glVertex3f(x0, y, z0);
		GL11.glTexCoord2f(u0, v1);
		GL11.glVertex3f(x0, y, z1);
		GL11.glTexCoord2f(u1, v1);
		GL11.glVertex3f(x1, y, z1);
		GL11.glTexCoord2f(u1, v0);
		GL11.glVertex3f(x1, y, z0);
		GL11.glEnd();
		if (texture != 0) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
		}
	}

	private static int selectedColor(final RegionSnapshot region,
			final int blockX, final int blockY) {
		return switch (region.blockType(blockX, blockY)) {
		case 0 -> EditorSettings.COLOR_FLAT_SELECTED;
		case 1 -> EditorSettings.COLOR_COMPLEX_SELECTED;
		case 2 -> EditorSettings.COLOR_MULTILAYER_SELECTED;
		default -> EditorSettings.COLOR_GUI_SELECTED;
		};
	}

	private static int highlightedColor(final RegionSnapshot region,
			final int blockX, final int blockY) {
		return switch (region.blockType(blockX, blockY)) {
		case 0 -> EditorSettings.COLOR_FLAT_HIGHLIGHTED;
		case 1 -> EditorSettings.COLOR_COMPLEX_HIGHLIGHTED;
		case 2 -> EditorSettings.COLOR_MULTILAYER_HIGHLIGHTED;
		default -> EditorSettings.COLOR_COMPLEX_HIGHLIGHTED;
		};
	}

	private static void renderBlockSelectionBackground(final RegionSnapshot region,
			final DetailedRegionSnapshot details, final int blockX, final int blockY,
			final int texture) {
		final boolean flat = region.blockType(blockX, blockY) == 0;
		final int color = highlightedColor(region, blockX, blockY);
		final int size = flat ? 1 : 8;
		for (int cellY = 0; cellY < size; cellY++) {
			for (int cellX = 0; cellX < size; cellX++) {
				final int localX = blockX * 8 + cellX;
				final int localY = blockY * 8 + cellY;
				final int layers = details.layerCount(localX, localY);
				if (layers > 0)
					renderCellSelection(region, details, blockX, blockY,
							cellX, cellY, layers - 1, texture, color, 0.02f);
			}
		}
	}

	private void ensureWindow(final int width, final int height,
			final int requestedSamples) {
		final int samples = requestedSamples == 2 || requestedSamples == 4
				|| requestedSamples == 8 || requestedSamples == 16
				? requestedSamples : 0;
		if (window != 0L && framebufferWidth == width && framebufferHeight == height
				&& framebufferSamples == samples) {
			GLFW.glfwMakeContextCurrent(window);
			return;
		}
		final long previousWindow = window;
		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 2);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1);
		GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, samples);
		int attemptedSamples = samples;
		long replacementWindow = GLFW.glfwCreateWindow(width, height,
				"GeoEditor offscreen viewport", 0L, previousWindow);
		while (replacementWindow == 0L && attemptedSamples > 0) {
			attemptedSamples /= 2;
			GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, attemptedSamples);
			replacementWindow = GLFW.glfwCreateWindow(width, height,
					"GeoEditor offscreen viewport", 0L, previousWindow);
		}
		if (replacementWindow == 0L)
			throw new IllegalStateException("Unable to create the LWJGL OpenGL viewport.");
		window = replacementWindow;
		framebufferWidth = width;
		framebufferHeight = height;
		framebufferSamples = samples;
		GLFW.glfwMakeContextCurrent(window);
		GL.createCapabilities();
		if (previousWindow != 0L)
			GLFW.glfwDestroyWindow(previousWindow);
		/* PBO objects are shared, but queued pixels belong to the old default framebuffer. */
		readbackPboBytes = 0;
		readbackPboPrimed = false;
		readbackWriteIndex = 0;
		openGlVersion = String.valueOf(GL11.glGetString(GL11.GL_VERSION));
		gpuRenderer = String.valueOf(GL11.glGetString(GL11.GL_RENDERER));
		maximumTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
		textureFilteringDirty = true;
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		actualFramebufferSamples = GL11.glGetInteger(GL13.GL_SAMPLES);
		if (actualFramebufferSamples > 0)
			GL11.glEnable(GL13.GL_MULTISAMPLE);
		if (Boolean.getBoolean("geoeditor.graphicsSmoke")
				&& !graphicsSmokeReported) {
			final float maximumAnisotropy = GL.getCapabilities()
					.GL_EXT_texture_filter_anisotropic
					? GL11.glGetFloat(EXTTextureFilterAnisotropic
							.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT) : 1f;
			System.out.println("GEOEDITOR_GRAPHICS=MSAA_REQUESTED:" + samples
					+ ",MSAA_ACTUAL:" + actualFramebufferSamples
					+ ",ANISO_REQUESTED:" + textureAnisotropy
					+ ",ANISO_MAX:" + Math.round(maximumAnisotropy)
					+ ",MAX_TEXTURE:" + maximumTextureSize);
			graphicsSmokeReported = true;
		}
	}

	private static void configureProjection(final int width, final int height,
			final float pitch, final float yaw, final float cameraHeight, final int radius,
			final float cameraX, final float cameraZ) {
		final float aspect = width / (float) height;
		final double top = Math.tan(Math.toRadians(45d / 2d));
		final double farPlane = Math.max(8192d, (radius + 2d) * 2048d);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glFrustum(-top * aspect * CAMERA_NEAR_PLANE,
				top * aspect * CAMERA_NEAR_PLANE,
				-top * CAMERA_NEAR_PLANE, top * CAMERA_NEAR_PLANE,
				CAMERA_NEAR_PLANE, farPlane);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();
		GL11.glRotatef(360f - pitch, 1f, 0f, 0f);
		GL11.glRotatef(360f - yaw, 0f, 1f, 0f);
		GL11.glTranslatef(-cameraX, -cameraHeight, -cameraZ);
	}

	private int ensureTexture(final File file, final int maximumSize,
			final Set<String> activeTextures) {
		return ensureTexture(file, maximumSize, activeTextures,
				auxiliaryTextureLoader);
	}

	private int ensureTexture(final File file, final int maximumSize,
			final Set<String> activeTextures, final AsyncTextureLoader loader) {
		final String path = file.getAbsolutePath();
		activeTextures.add(path);
		final Integer cached = textures.get(path);
		if (cached != null)
			return cached.intValue();
		if (loader.isRequested(file)) {
			final AsyncTextureLoader.Prepared prepared =
					loader.pollReady(file, maximumSize);
			if (prepared == null)
				return 0;
			preparedRegionTexturePath = null;
			return uploadTexture(prepared.path(), prepared.width(),
					prepared.height(), prepared.pixels());
		}
		try {
			final BufferedImage image = ImageIO.read(file);
			if (image == null)
				throw new IllegalArgumentException("Unsupported region texture: " + file);
			final int width = Math.min(maximumSize, image.getWidth());
			final int height = Math.min(maximumSize, image.getHeight());
			final ByteBuffer pixels =
					BufferUtils.createByteBuffer(width * height * 4);
			for (int y = height - 1; y >= 0; y--) {
				final int sourceY = y * image.getHeight() / height;
				for (int x = 0; x < width; x++) {
					final int sourceX = x * image.getWidth() / width;
					final int argb = image.getRGB(sourceX, sourceY);
					pixels.put((byte) (argb >> 16));
					pixels.put((byte) (argb >> 8));
					pixels.put((byte) argb);
					pixels.put((byte) (argb >> 24));
				}
			}
			pixels.flip();
			return uploadTexture(path, width, height, pixels);
		} catch (final Exception exception) {
			throw new IllegalStateException("Unable to load region texture: " + file, exception);
		}
	}

	private int uploadTexture(final String path, final int width,
			final int height, final ByteBuffer pixels) {
		final int texture = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		applyTextureAnisotropy();
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
				width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		textures.put(path, texture);
		return texture;
	}

	private void applyTextureFilteringToCache() {
		if (!textureFilteringDirty)
			return;
		for (final int texture : textures.values()) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			applyTextureAnisotropy();
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		textureFilteringDirty = false;
	}

	private void applyTextureAnisotropy() {
		if (!GL.getCapabilities().GL_EXT_texture_filter_anisotropic)
			return;
		final float maximum = GL11.glGetFloat(
				EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
		GL11.glTexParameterf(GL11.GL_TEXTURE_2D,
				EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
				Math.min(textureAnisotropy, maximum));
	}

	private void retainTextures(final Set<String> activeTextures) {
		textures.entrySet().removeIf(entry -> {
			if (activeTextures.contains(entry.getKey()))
				return false;
			deferredTextureDeletes.addLast(entry.getValue());
			return true;
		});
	}

	private void renderTerrain(final RegionSnapshot region, final int texture,
			final float offsetX, final float offsetY, final int step) {
		final boolean textured = texture != 0;
		if (textured) {
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		}
		GL11.glBegin(GL11.GL_TRIANGLES);
		for (int y = 0; y < 255; y += step) {
			for (int x = 0; x < 255; x += step) {
				final int nextX = Math.min(255, x + step);
				final int nextY = Math.min(255, y + step);
				vertex(region, x, y, textured, offsetX, offsetY);
				vertex(region, x, nextY, textured, offsetX, offsetY);
				vertex(region, nextX, y, textured, offsetX, offsetY);
				vertex(region, nextX, y, textured, offsetX, offsetY);
				vertex(region, x, nextY, textured, offsetX, offsetY);
				vertex(region, nextX, nextY, textured, offsetX, offsetY);
			}
		}
		GL11.glEnd();
		if (textured) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
		}
	}

	private static void vertex(final RegionSnapshot region, final int x,
			final int y, final boolean textured, final float offsetX, final float offsetY) {
		final float height = region.maximumHeight(x, y) / 16f;
		final float normalized = Math.max(0f, Math.min(1f, (height + 80f) / 240f));
		if (textured) {
			final float light = 0.72f + normalized * 0.28f;
			GL11.glColor3f(light, light, light);
			GL11.glTexCoord2f(x / 255f, y / 255f);
		} else {
			GL11.glColor3f(0.04f + normalized * 0.12f,
					0.28f + normalized * 0.58f,
					0.34f + normalized * 0.30f);
		}
		GL11.glVertex3f(x * 8f + offsetX, height, y * 8f + offsetY);
	}

	@Override
	public void close() {
		destroyWindow();
		auxiliaryTextureLoader.close();
		skyTextureLoader.close();
		if (initialized) {
			GLFW.glfwTerminate();
			initialized = false;
		}
		if (errorCallback != null) {
			errorCallback.free();
			errorCallback = null;
		}
	}

	private void destroyWindow() {
		if (window == 0L)
			return;
		GLFW.glfwMakeContextCurrent(window);
		clearGridChunks();
		cachedGridDetails = null;
		if (gridShaderProgram != 0) {
			GL20.glDeleteProgram(gridShaderProgram);
			gridShaderProgram = 0;
		}
		if (skyShaderProgram != 0) {
			GL20.glDeleteProgram(skyShaderProgram);
			skyShaderProgram = 0;
		}
		skyTextureId = 0;
		moonTextureId = 0;
		moonStarsTextureId = 0;
		sharedGridRenderer.close();
		terrainVboRenderer.close();
		if (readbackPbos[0] != 0) {
			GL15.glDeleteBuffers(readbackPbos[0]);
			GL15.glDeleteBuffers(readbackPbos[1]);
			readbackPbos[0] = 0;
			readbackPbos[1] = 0;
		}
		for (final int texture : textures.values())
			GL11.glDeleteTextures(texture);
		textures.clear();
		while (!deferredTextureDeletes.isEmpty())
			GL11.glDeleteTextures(deferredTextureDeletes.removeFirst());
		GL.setCapabilities(null);
		GLFW.glfwMakeContextCurrent(0L);
		GLFW.glfwDestroyWindow(window);
		window = 0L;
		framebufferWidth = 0;
		framebufferHeight = 0;
		framebufferSamples = 0;
		actualFramebufferSamples = 0;
		maximumTextureSize = 0;
		readbackPixels = null;
		readbackBgra = null;
		readbackPboBytes = 0;
		readbackPboPrimed = false;
		readbackWriteIndex = 0;
	}

	public record RenderedFrame(int width, int height, byte[] bgra) {}

	public record GridStreamStats(long reusedFrames, long rebuiltFrames) {}
	public record QueueStats(int texturePreparations, int terrainPreparations,
			int uploadsThisFrame, int pendingDeletes, int deletesThisFrame) {}

	public record RenderStats(long elements, long drawCalls) {}

	private record GridCell(float x, float z, float size, float height,
			short nswe, byte type, int blockX, int blockY) {}

	private static final class GridChunk {
		private final int chunkX;
		private final int chunkY;
		private int vbo;
		private int vertexCount;
		private final float minimumX;
		private final float maximumX;
		private final float minimumZ;
		private final float maximumZ;
		private final float minimumY;
		private final float maximumY;
		private long lastVisibleFrame;

		private GridChunk(final int chunkX, final int chunkY,
				final float minimumX, final float maximumX,
				final float minimumZ, final float maximumZ,
				final float minimumY, final float maximumY) {
			this.chunkX = chunkX;
			this.chunkY = chunkY;
			this.minimumX = minimumX;
			this.maximumX = maximumX;
			this.minimumZ = minimumZ;
			this.maximumZ = maximumZ;
			this.minimumY = minimumY;
			this.maximumY = maximumY;
		}

		private double distanceSquared(final float cameraX,
				final float cameraY, final float cameraZ) {
			final double dx = cameraX - (minimumX + maximumX) * 0.5;
			final double dy = cameraY - (minimumY + maximumY) * 0.5;
			final double dz = cameraZ - (minimumZ + maximumZ) * 0.5;
			return dx * dx + dy * dy + dz * dz;
		}
	}
}
