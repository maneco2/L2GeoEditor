/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.editor;

import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.geo.core.DetailedRegionSnapshot;
import com.geo.core.RegionSnapshot;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBDrawInstanced;
import org.lwjgl.opengl.ARBInstancedArrays;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/** Shared unit-cell geometry with one compact per-cell instance stream. */
final class SharedGridRenderer implements AutoCloseable {
	private static final float ACTIVE_GRID_ALPHA = 0.52f;
	private static final int CHUNK_BLOCKS = 8;
	private static final int VERTICES_PER_CELL = 12;
	private static final int INDICES_PER_CELL = 36;
	private static final int OUTLINE_INDICES_PER_CELL = 8;
	private static final int INSTANCE_FLOATS = 8;
	private final GridVisibilitySelector selector =
			new GridVisibilitySelector();
	private FloatBuffer instances = BufferUtils.createFloatBuffer(65_536);
	private int geometryVbo;
	private int geometryIndexVbo;
	private int geometryOutlineIndexVbo;
	private int instanceVbo;
	private int shader;
	private int instance0Attribute;
	private int instance1Attribute;
	private int texturedUniform;
	private int outlineUniform;
	private int circularUniform;
	private int centerUniform;
	private int radiusUniform;
	private int flatColorUniform;
	private int complexColorUniform;
	private int multiColorUniform;
	private int instanceCount;
	private long preparationMicros;
	private long uploadMicros;
	private int[] cachedVisibleBlocks = new int[0];
	private RegionSnapshot cachedRegion;
	private DetailedRegionSnapshot cachedDetails;
	private int cachedRange;
	private int cachedCameraX;
	private int cachedCameraZ;
	private boolean cachedCircular;
	private boolean streamReady;
	private boolean streamReused;
	private long streamReuseCount;
	private long streamRebuildCount;
	private final Map<Integer, InstanceChunk> chunks = new HashMap<>();
	private final Set<Integer> invalidatedChunks = new java.util.HashSet<>();

	private static final class InstanceChunk {
		private final int vbo;
		private final int instances;

		private InstanceChunk(final int vbo, final int instances) {
			this.vbo = vbo;
			this.instances = instances;
		}
	}

	boolean isSupported() {
		return GL.getCapabilities().GL_ARB_draw_instanced
				&& GL.getCapabilities().GL_ARB_instanced_arrays;
	}

	void render(final RegionSnapshot region, final DetailedRegionSnapshot details,
			final float[][] frustum, final float cameraX, final float cameraZ,
			final int range, final boolean circular, final boolean textured,
			final boolean transparency, final boolean drawOutline) {
		if (!isSupported())
			throw new IllegalStateException("OpenGL instancing extensions are unavailable.");
		ensureResources();
		if (cachedRegion != region || cachedDetails != details) {
			clearChunks();
			cachedRegion = region;
			cachedDetails = details;
		}
		for (final int key : invalidatedChunks) {
			final InstanceChunk removed = chunks.remove(key);
			if (removed != null)
				GL15.glDeleteBuffers(removed.vbo);
		}
		invalidatedChunks.clear();
		final long preparationStarted = System.nanoTime();
		final long rebuildsBefore = streamRebuildCount;
		uploadMicros = 0L;
		final ArrayList<InstanceChunk> visibleChunks = new ArrayList<>();
		int visibleInstanceCount = 0;
		final int centerBlockX = Math.max(0, Math.min(255, (int) cameraX >> 3));
		final int centerBlockY = Math.max(0, Math.min(255, (int) cameraZ >> 3));
		final int minimumChunkX = Math.max(0, centerBlockX - range) / CHUNK_BLOCKS;
		final int maximumChunkX = Math.min(255, centerBlockX + range) / CHUNK_BLOCKS;
		final int minimumChunkY = Math.max(0, centerBlockY - range) / CHUNK_BLOCKS;
		final int maximumChunkY = Math.min(255, centerBlockY + range) / CHUNK_BLOCKS;
		for (int chunkY = minimumChunkY; chunkY <= maximumChunkY; chunkY++) {
			for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
				if (!isChunkVisible(frustum, chunkX, chunkY))
					continue;
				final int selectedChunkX = chunkX;
				final int selectedChunkY = chunkY;
				final InstanceChunk chunk = chunks.computeIfAbsent(
						(chunkY << 8) | chunkX,
						ignored -> buildChunk(region, details,
								selectedChunkX, selectedChunkY));
				visibleChunks.add(chunk);
				visibleInstanceCount += chunk.instances;
			}
		}
		instanceCount = visibleInstanceCount;
		streamReused = streamRebuildCount == rebuildsBefore;
		if (streamReused)
			streamReuseCount++;
		preparationMicros = (System.nanoTime() - preparationStarted) / 1_000L;
		GL20.glUseProgram(shader);
		GL20.glUniform1i(texturedUniform, textured ? 1 : 0);
		GL20.glUniform1i(circularUniform, circular ? 1 : 0);
		GL20.glUniform2f(centerUniform, (int) cameraX, (int) cameraZ);
		GL20.glUniform1f(radiusUniform, range * 8f);
		final float alpha = transparency ? ACTIVE_GRID_ALPHA : 1f;
		setColor(flatColorUniform, EditorSettings.COLOR_FLAT_NORMAL, alpha);
		setColor(complexColorUniform, EditorSettings.COLOR_COMPLEX_NORMAL, alpha);
		setColor(multiColorUniform, EditorSettings.COLOR_MULTILAYER_NORMAL, alpha);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, geometryVbo);
		GL11.glVertexPointer(3, GL11.GL_FLOAT, 20, 0L);
		GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 20, 12L);
		GL20.glEnableVertexAttribArray(instance0Attribute);
		GL20.glEnableVertexAttribArray(instance1Attribute);
		ARBInstancedArrays.glVertexAttribDivisorARB(instance0Attribute, 1);
		ARBInstancedArrays.glVertexAttribDivisorARB(instance1Attribute, 1);
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, geometryIndexVbo);
		for (final InstanceChunk chunk : visibleChunks) {
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, chunk.vbo);
			GL20.glVertexAttribPointer(instance0Attribute, 4, GL11.GL_FLOAT, false, 32, 0L);
			GL20.glVertexAttribPointer(instance1Attribute, 4, GL11.GL_FLOAT, false, 32, 16L);
			ARBDrawInstanced.glDrawElementsInstancedARB(GL11.GL_TRIANGLES,
					INDICES_PER_CELL, GL11.GL_UNSIGNED_BYTE, 0L, chunk.instances);
		}
		if (drawOutline) {
			GL20.glUniform1i(outlineUniform, 1);
			GL20.glUniform1i(texturedUniform, 0);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glDepthFunc(GL11.GL_LEQUAL);
			GL11.glLineWidth(1f);
			GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,
					geometryOutlineIndexVbo);
			for (final InstanceChunk chunk : visibleChunks) {
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, chunk.vbo);
				GL20.glVertexAttribPointer(instance0Attribute, 4,
						GL11.GL_FLOAT, false, 32, 0L);
				GL20.glVertexAttribPointer(instance1Attribute, 4,
						GL11.GL_FLOAT, false, 32, 16L);
				ARBDrawInstanced.glDrawElementsInstancedARB(GL11.GL_LINES,
						OUTLINE_INDICES_PER_CELL, GL11.GL_UNSIGNED_BYTE,
						0L, chunk.instances);
			}
			GL11.glDepthFunc(GL11.GL_LESS);
			if (transparency)
				GL11.glEnable(GL11.GL_BLEND);
			GL20.glUniform1i(outlineUniform, 0);
			GL20.glUniform1i(texturedUniform, textured ? 1 : 0);
		}
		ARBInstancedArrays.glVertexAttribDivisorARB(instance0Attribute, 0);
		ARBInstancedArrays.glVertexAttribDivisorARB(instance1Attribute, 0);
		GL20.glDisableVertexAttribArray(instance1Attribute);
		GL20.glDisableVertexAttribArray(instance0Attribute);
		GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
		GL20.glUseProgram(0);
	}

	private InstanceChunk buildChunk(final RegionSnapshot region,
			final DetailedRegionSnapshot details, final int chunkX, final int chunkY) {
		streamRebuildCount++;
		instances.clear();
		instanceCount = 0;
		final int firstBlockX = chunkX * CHUNK_BLOCKS;
		final int firstBlockY = chunkY * CHUNK_BLOCKS;
		for (int blockY = firstBlockY; blockY < Math.min(256, firstBlockY + CHUNK_BLOCKS); blockY++) {
			for (int blockX = firstBlockX; blockX < Math.min(256, firstBlockX + CHUNK_BLOCKS); blockX++) {
				final byte type = region.blockType(blockX, blockY);
				if (type == 0) {
					putInstance(blockX * 8f, blockY * 8f, 8f,
							details.height(blockX * 8, blockY * 8, 0) / 16f,
							type, details.nswe(blockX * 8, blockY * 8, 0), blockX, blockY);
					continue;
				}
				for (int cellY = 0; cellY < 8; cellY++) {
					for (int cellX = 0; cellX < 8; cellX++) {
						final int localX = blockX * 8 + cellX;
						final int localY = blockY * 8 + cellY;
						for (int layer = 0; layer < details.layerCount(localX, localY); layer++)
							putInstance(localX, localY, 1f,
									details.height(localX, localY, layer) / 16f, type,
									details.nswe(localX, localY, layer), blockX, blockY);
					}
				}
			}
		}
		final int count = instanceCount;
		instances.flip();
		final long uploadStarted = System.nanoTime();
		final int vbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STATIC_DRAW);
		uploadMicros += (System.nanoTime() - uploadStarted) / 1_000L;
		return new InstanceChunk(vbo, count);
	}

	private static boolean isChunkVisible(final float[][] frustum,
			final int chunkX, final int chunkY) {
		final float minimumX = chunkX * CHUNK_BLOCKS * 8f;
		final float minimumZ = chunkY * CHUNK_BLOCKS * 8f;
		final float maximumX = minimumX + CHUNK_BLOCKS * 8f;
		final float maximumZ = minimumZ + CHUNK_BLOCKS * 8f;
		for (final float[] plane : frustum) {
			final float x = plane[0] >= 0f ? maximumX : minimumX;
			final float y = plane[1] >= 0f ? 2048f : -2048f;
			final float z = plane[2] >= 0f ? maximumZ : minimumZ;
			if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] <= 0f)
				return false;
		}
		return true;
	}

	private void clearChunks() {
		for (final InstanceChunk chunk : chunks.values())
			GL15.glDeleteBuffers(chunk.vbo);
		chunks.clear();
		invalidatedChunks.clear();
	}

	void invalidateBlock(final int blockX, final int blockY) {
		invalidatedChunks.add(((blockY / CHUNK_BLOCKS) << 8)
				| (blockX / CHUNK_BLOCKS));
	}

	int instanceCount() {
		return instanceCount;
	}

	long preparationMicros() {
		return preparationMicros;
	}

	long uploadMicros() {
		return uploadMicros;
	}

	boolean streamReused() {
		return streamReused;
	}

	long streamReuseCount() {
		return streamReuseCount;
	}

	long streamRebuildCount() {
		return streamRebuildCount;
	}

	private boolean matchesCachedSelection(final RegionSnapshot region,
			final DetailedRegionSnapshot details, final int range,
			final boolean circular, final int cameraX, final int cameraZ) {
		if (!streamReady || cachedRegion != region || cachedDetails != details
				|| cachedRange != range || cachedCircular != circular
				|| circular && (cachedCameraX != cameraX || cachedCameraZ != cameraZ)
				|| cachedVisibleBlocks.length != selector.visibleCount())
			return false;
		for (int index = 0; index < cachedVisibleBlocks.length; index++) {
			if (cachedVisibleBlocks[index] != selector.visibleBlockAt(index))
				return false;
		}
		return true;
	}

	private void rememberSelection(final RegionSnapshot region,
			final DetailedRegionSnapshot details, final int range,
			final boolean circular, final int cameraX, final int cameraZ) {
		if (cachedVisibleBlocks.length != selector.visibleCount())
			cachedVisibleBlocks = new int[selector.visibleCount()];
		for (int index = 0; index < cachedVisibleBlocks.length; index++)
			cachedVisibleBlocks[index] = selector.visibleBlockAt(index);
		cachedRegion = region;
		cachedDetails = details;
		cachedRange = range;
		cachedCircular = circular;
		cachedCameraX = cameraX;
		cachedCameraZ = cameraZ;
		streamReady = true;
	}

	private static boolean insideCircle(final int cellX, final int cellY,
			final float cameraX, final float cameraZ, final int range) {
		final float dx = cellX + 0.5f - (int) cameraX;
		final float dz = cellY + 0.5f - (int) cameraZ;
		final float radius = range * 8f;
		return dx * dx + dz * dz <= radius * radius;
	}

	/** 0 outside, 1 boundary, 2 fully inside. */
	private static int classifyCircularBlock(final int blockX, final int blockY,
			final float cameraX, final float cameraZ, final int range) {
		final int minimumX = blockX * 8;
		final int minimumY = blockY * 8;
		int inside = 0;
		if (insideCircle(minimumX, minimumY, cameraX, cameraZ, range)) inside++;
		if (insideCircle(minimumX + 7, minimumY, cameraX, cameraZ, range)) inside++;
		if (insideCircle(minimumX, minimumY + 7, cameraX, cameraZ, range)) inside++;
		if (insideCircle(minimumX + 7, minimumY + 7, cameraX, cameraZ, range)) inside++;
		return inside == 4 ? 2 : inside == 0 ? 0 : 1;
	}

	private static boolean isCellVisible(final float[][] frustum,
			final float x, final float z, final float height) {
		final float minimumX = x + 0.1f;
		final float maximumX = x + 0.9f;
		final float minimumY = height - 0.2f;
		final float minimumZ = z + 0.1f;
		final float maximumZ = z + 0.9f;
		for (final float[] plane : frustum) {
			final float px = plane[0] >= 0f ? maximumX : minimumX;
			final float py = plane[1] >= 0f ? height : minimumY;
			final float pz = plane[2] >= 0f ? maximumZ : minimumZ;
			if (plane[0] * px + plane[1] * py + plane[2] * pz + plane[3] <= 0f)
				return false;
		}
		return true;
	}

	private void putInstance(final float x, final float z, final float size,
			final float height, final byte type, final short nswe,
			final int blockX, final int blockY) {
		ensureInstanceCapacity(INSTANCE_FLOATS);
		instances.put(x).put(z).put(size).put(height);
		instances.put(type).put(nswe).put(blockX).put(blockY);
		instanceCount++;
	}

	private void ensureInstanceCapacity(final int additional) {
		if (instances.remaining() >= additional)
			return;
		final int required = instances.position() + additional;
		int capacity = instances.capacity();
		while (capacity < required)
			capacity = Math.multiplyExact(capacity, 2);
		final FloatBuffer replacement = BufferUtils.createFloatBuffer(capacity);
		instances.flip();
		replacement.put(instances);
		instances = replacement;
	}

	private void ensureResources() {
		if (shader != 0)
			return;
		final FloatBuffer geometry = BufferUtils.createFloatBuffer(VERTICES_PER_CELL * 5);
		putVertex(geometry, 0f, -0.2f, 1f, -1f, -1f);
		putVertex(geometry, 1f, -0.2f, 1f, -1f, -1f);
		putVertex(geometry, 1f, 0f, 1f, -1f, -1f);
		putVertex(geometry, 0f, 0f, 1f, -1f, -1f);
		putVertex(geometry, 0f, -0.2f, 0f, -1f, -1f);
		putVertex(geometry, 1f, -0.2f, 0f, -1f, -1f);
		putVertex(geometry, 1f, 0f, 0f, -1f, -1f);
		putVertex(geometry, 0f, 0f, 0f, -1f, -1f);
		putVertex(geometry, 0f, 0f, 1f, 0f, 1f);
		putVertex(geometry, 1f, 0f, 1f, 1f, 1f);
		putVertex(geometry, 1f, 0f, 0f, 1f, 0f);
		putVertex(geometry, 0f, 0f, 0f, 0f, 0f);
		geometry.flip();
		final byte[] indices = { 0, 1, 2, 2, 3, 0, 1, 5, 6, 6, 2, 1,
				7, 6, 5, 5, 4, 7, 4, 0, 3, 3, 7, 4,
				0, 5, 1, 5, 0, 4, 8, 9, 10, 10, 11, 8 };
		final byte[] outlineIndices = { 8, 9, 9, 10, 10, 11, 11, 8 };
		final ByteBuffer indexBuffer = BufferUtils.createByteBuffer(indices.length);
		indexBuffer.put(indices).flip();
		final ByteBuffer outlineIndexBuffer =
				BufferUtils.createByteBuffer(outlineIndices.length);
		outlineIndexBuffer.put(outlineIndices).flip();
		geometryVbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, geometryVbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, geometry, GL15.GL_STATIC_DRAW);
		geometryIndexVbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, geometryIndexVbo);
		GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);
		geometryOutlineIndexVbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, geometryOutlineIndexVbo);
		GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER,
				outlineIndexBuffer, GL15.GL_STATIC_DRAW);
		instanceVbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
		createShader();
	}

	private static void putVertex(final FloatBuffer target, final float x,
			final float y, final float z, final float u, final float v) {
		target.put(x).put(y).put(z).put(u).put(v);
	}

	private void createShader() {
		final String vertex = """
			#version 120
			attribute vec4 instance0;
			attribute vec4 instance1;
			uniform vec4 flatColor;
			uniform vec4 complexColor;
			uniform vec4 multiColor;
			varying vec2 worldXZ;
			varying vec2 instanceCenter;
			varying vec2 atlasUV;
			varying vec4 cellColor;
			void main() {
				float size = instance0.z;
				vec4 position = gl_Vertex;
				position.x = instance0.x + mix(0.1, size - 0.1, position.x);
				position.z = instance0.y + mix(0.1, size - 0.1, position.z);
				position.y += instance0.w;
				worldXZ = position.xz;
				instanceCenter = instance0.xy + vec2(size * 0.5);
				float nswe = instance1.y;
				if (gl_MultiTexCoord0.x < 0.0) atlasUV = vec2(0.0);
				else {
					float u0 = floor(nswe / 4.0) * 0.25;
					float v0 = 1.0 - mod(nswe, 4.0) * 0.25;
					atlasUV = vec2(u0 + gl_MultiTexCoord0.x * 0.25,
						v0 - gl_MultiTexCoord0.y * 0.25);
				}
				cellColor = instance1.x < 0.5 ? flatColor
					: (instance1.x < 1.5 ? complexColor : multiColor);
				if (mod(instance1.z, 2.0) != mod(instance1.w, 2.0)
						&& instance1.x > 0.5) cellColor.rgb *= 0.85;
				gl_Position = gl_ModelViewProjectionMatrix * position;
			}
			""";
		final String fragment = """
			#version 120
			uniform int textured;
			uniform int outline;
			uniform int circularGrid;
			uniform vec2 gridCenter;
			uniform float gridRadius;
			uniform sampler2D atlas;
			varying vec2 worldXZ;
			varying vec2 instanceCenter;
			varying vec2 atlasUV;
			varying vec4 cellColor;
			void main() {
				vec2 delta = abs(instanceCenter - gridCenter);
				if (circularGrid != 0) {
					if (length(instanceCenter - gridCenter) > gridRadius) discard;
				} else if (delta.x > gridRadius || delta.y > gridRadius) discard;
				vec4 color = outline != 0
						? vec4(0.015, 0.015, 0.015, 1.0)
						: cellColor;
				if (outline == 0 && textured != 0)
					color *= texture2D(atlas, atlasUV);
				gl_FragColor = color;
			}
			""";
		final int vertexShader = compile(GL20.GL_VERTEX_SHADER, vertex);
		final int fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, fragment);
		shader = GL20.glCreateProgram();
		GL20.glAttachShader(shader, vertexShader);
		GL20.glAttachShader(shader, fragmentShader);
		GL20.glLinkProgram(shader);
		if (GL20.glGetProgrami(shader, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
			throw new IllegalStateException("Shared grid shader link failed: "
					+ GL20.glGetProgramInfoLog(shader));
		GL20.glDeleteShader(vertexShader);
		GL20.glDeleteShader(fragmentShader);
		instance0Attribute = GL20.glGetAttribLocation(shader, "instance0");
		instance1Attribute = GL20.glGetAttribLocation(shader, "instance1");
		texturedUniform = GL20.glGetUniformLocation(shader, "textured");
		outlineUniform = GL20.glGetUniformLocation(shader, "outline");
		circularUniform = GL20.glGetUniformLocation(shader, "circularGrid");
		centerUniform = GL20.glGetUniformLocation(shader, "gridCenter");
		radiusUniform = GL20.glGetUniformLocation(shader, "gridRadius");
		flatColorUniform = GL20.glGetUniformLocation(shader, "flatColor");
		complexColorUniform = GL20.glGetUniformLocation(shader, "complexColor");
		multiColorUniform = GL20.glGetUniformLocation(shader, "multiColor");
		GL20.glUseProgram(shader);
		GL20.glUniform1i(GL20.glGetUniformLocation(shader, "atlas"), 0);
		GL20.glUseProgram(0);
	}

	private static int compile(final int type, final String source) {
		final int compiled = GL20.glCreateShader(type);
		GL20.glShaderSource(compiled, source);
		GL20.glCompileShader(compiled);
		if (GL20.glGetShaderi(compiled, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
			throw new IllegalStateException("Shared grid shader compile failed: "
					+ GL20.glGetShaderInfoLog(compiled));
		return compiled;
	}

	private static void setColor(final int uniform, final int color,
			final float alpha) {
		GL20.glUniform4f(uniform, ((color >>> 16) & 0xff) / 255f,
				((color >>> 8) & 0xff) / 255f, (color & 0xff) / 255f, alpha);
	}

	@Override
	public void close() {
		clearChunks();
		if (geometryVbo != 0)
			GL15.glDeleteBuffers(geometryVbo);
		if (geometryIndexVbo != 0)
			GL15.glDeleteBuffers(geometryIndexVbo);
		if (geometryOutlineIndexVbo != 0)
			GL15.glDeleteBuffers(geometryOutlineIndexVbo);
		if (instanceVbo != 0)
			GL15.glDeleteBuffers(instanceVbo);
		if (shader != 0)
			GL20.glDeleteProgram(shader);
		geometryVbo = 0;
		geometryIndexVbo = 0;
		geometryOutlineIndexVbo = 0;
		instanceVbo = 0;
		streamReady = false;
		cachedRegion = null;
		cachedDetails = null;
		cachedVisibleBlocks = new int[0];
		shader = 0;
	}
}
