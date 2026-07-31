/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.editor;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.geo.core.RegionSnapshot;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/** Reusable GPU terrain meshes keyed by immutable region snapshot and detail. */
final class TerrainVboRenderer implements AutoCloseable {
	private static final int FLOATS_PER_VERTEX = 8;
	private static final int STRIDE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
	private static final float REGION_SPAN = 2048f;
	private static final float TERRAIN_COORDINATE_SCALE = REGION_SPAN / 255f;
	private final IdentityHashMap<RegionSnapshot, Map<Integer, Mesh>> meshes =
			new IdentityHashMap<>();
	private final IdentityHashMap<RegionSnapshot,
			Map<Integer, CompletableFuture<PreparedMesh>>> pending =
			new IdentityHashMap<>();
	private final ExecutorService preparationExecutor =
			Executors.newFixedThreadPool(2, task -> {
				final Thread thread = new Thread(task, "GeoEditor-terrain-vbo-preparer");
				thread.setDaemon(true);
				return thread;
			});
	private long elements;
	private long drawCalls;
	private int uploadsRemaining;
	private final ArrayDeque<Integer> deferredDeletes = new ArrayDeque<>();
	private int uploadsThisFrame;
	private int deletesThisFrame;
	private int visualStyle = 1;

	void setVisualStyle(final int style) {
		visualStyle = Math.max(1, Math.min(10, style));
	}

	void beginFrame() {
		elements = 0L;
		drawCalls = 0L;
		uploadsRemaining = 1;
		uploadsThisFrame = 0;
		deletesThisFrame = 0;
		for (int deleted = 0; deleted < 2 && !deferredDeletes.isEmpty(); deleted++) {
			GL15.glDeleteBuffers(deferredDeletes.removeFirst());
			deletesThisFrame++;
		}
	}

	void render(final RegionSnapshot region, final int texture,
			final float offsetX, final float offsetZ, final int step) {
		final Map<Integer, Mesh> regionMeshes =
				meshes.computeIfAbsent(region, ignored -> new HashMap<>());
		Mesh mesh = regionMeshes.get(step);
		if (mesh == null) {
			final Map<Integer, CompletableFuture<PreparedMesh>> regionPending =
					pending.computeIfAbsent(region, ignored -> new HashMap<>());
			final CompletableFuture<PreparedMesh> future = regionPending.computeIfAbsent(
					step, ignored -> CompletableFuture.supplyAsync(
							() -> prepareMesh(region, step), preparationExecutor));
			if (!future.isDone() || uploadsRemaining <= 0)
				return;
			mesh = uploadMesh(future.join());
			regionMeshes.put(step, mesh);
			regionPending.remove(step);
			if (regionPending.isEmpty())
				pending.remove(region);
			uploadsRemaining--;
			uploadsThisFrame++;
		}
		final boolean textured = texture != 0;
		if (textured) {
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		} else {
			GL11.glDisable(GL11.GL_TEXTURE_2D);
		}
		GL11.glPushMatrix();
		GL11.glTranslatef(offsetX, 0f, offsetZ);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.vbo());
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		if (textured) {
			GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
			GL11.glColor3f(1f, 1f, 1f);
		} else if (usesHeightColor()) {
			GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
		} else {
			GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
			applyConstantStyleColor();
		}
		GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		GL11.glVertexPointer(3, GL11.GL_FLOAT, STRIDE_BYTES, 0L);
		GL11.glColorPointer(3, GL11.GL_FLOAT, STRIDE_BYTES, 12L);
		GL11.glTexCoordPointer(2, GL11.GL_FLOAT, STRIDE_BYTES, 24L);
		if (!textured && (visualStyle == 5 || visualStyle == 9))
			enableDistanceFog(visualStyle == 9);
		if (textured) {
			GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, mesh.vertexCount());
		} else {
			drawUntextured(mesh.vertexCount(), step);
		}
		GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		if (!textured && usesHeightColor())
			GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
		if (!textured && (visualStyle == 5 || visualStyle == 9))
			GL11.glDisable(GL11.GL_FOG);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL11.glPopMatrix();
		if (textured) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
		}
		elements += mesh.vertexCount() / 3L;
		drawCalls++;
	}

	private static void enableDistanceFog(final boolean softDepth) {
		final FloatBuffer fogColor = BufferUtils.createFloatBuffer(4);
		fogColor.put(0f).put(0f).put(0f).put(1f).flip();
		GL11.glEnable(GL11.GL_FOG);
		GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
		GL11.glFogfv(GL11.GL_FOG_COLOR, fogColor);
		GL11.glFogf(GL11.GL_FOG_START, softDepth ? 900f : 1800f);
		GL11.glFogf(GL11.GL_FOG_END, softDepth ? 6200f : 9000f);
	}

	private boolean usesHeightColor() {
		return visualStyle == 3 || visualStyle == 5 || visualStyle == 8
				|| visualStyle == 10;
	}

	private void applyConstantStyleColor() {
		switch (visualStyle) {
		case 2 -> GL11.glColor3f(0.05f, 0.24f, 0.28f);
		case 4 -> GL11.glColor3f(0.08f, 0.58f, 0.66f);
		case 6 -> GL11.glColor3f(0.08f, 0.42f, 0.36f);
		case 7 -> GL11.glColor3f(0.08f, 0.35f, 0.78f);
		case 9 -> GL11.glColor3f(0.10f, 0.42f, 0.50f);
		default -> GL11.glColor3f(0.06f, 0.72f, 0.82f);
		}
	}

	private void drawUntextured(final int vertexCount, final int step) {
		final boolean solidWithEdges = visualStyle == 2 || visualStyle == 6;
		if (solidWithEdges) {
			GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
			GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
			GL11.glColor3f(0.04f, 0.62f, 0.70f);
		}
		GL11.glLineWidth(switch (visualStyle) {
		case 1 -> 0.65f;
		case 4, 8 -> 1.35f;
		case 10 -> step <= 2 ? 1.05f : 0.65f;
		default -> 0.85f;
		});
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
		GL11.glLineWidth(1f);
		if (solidWithEdges)
			drawCalls++;
	}

	void retain(final Set<RegionSnapshot> activeRegions) {
		meshes.entrySet().removeIf(entry -> {
			if (activeRegions.contains(entry.getKey()))
				return false;
			for (final Mesh mesh : entry.getValue().values())
				deferredDeletes.addLast(mesh.vbo());
			return true;
		});
		pending.entrySet().removeIf(entry -> {
			if (activeRegions.contains(entry.getKey()))
				return false;
			for (final CompletableFuture<PreparedMesh> future : entry.getValue().values())
				future.cancel(false);
			return true;
		});
	}

	boolean hasPendingWork() {
		return !pending.isEmpty();
	}

	int pendingPreparationCount() {
		return pending.values().stream().mapToInt(Map::size).sum();
	}

	int pendingDeleteCount() { return deferredDeletes.size(); }
	int uploadsThisFrame() { return uploadsThisFrame; }
	int deletesThisFrame() { return deletesThisFrame; }

	long elements() {
		return elements;
	}

	long drawCalls() {
		return drawCalls;
	}

	private static PreparedMesh prepareMesh(final RegionSnapshot region,
			final int detailStep) {
		final int step = Math.max(1, detailStep);
		final int cells = (int) Math.ceil(255d / step);
		final FloatBuffer vertices = BufferUtils.createFloatBuffer(
				cells * cells * 6 * FLOATS_PER_VERTEX);
		for (int y = 0; y < 255; y += step) {
			for (int x = 0; x < 255; x += step) {
				final int nextX = Math.min(255, x + step);
				final int nextY = Math.min(255, y + step);
				putVertex(vertices, region, x, y);
				putVertex(vertices, region, x, nextY);
				putVertex(vertices, region, nextX, y);
				putVertex(vertices, region, nextX, y);
				putVertex(vertices, region, x, nextY);
				putVertex(vertices, region, nextX, nextY);
			}
		}
		vertices.flip();
		return new PreparedMesh(vertices, vertices.limit() / FLOATS_PER_VERTEX);
	}

	private static Mesh uploadMesh(final PreparedMesh prepared) {
		final int vbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, prepared.vertices(),
				GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		return new Mesh(vbo, prepared.vertexCount());
	}

	private static void putVertex(final FloatBuffer target,
			final RegionSnapshot region, final int x, final int y) {
		final float height = region.minimumHeight(x, y) / 16f;
		final float normalized = Math.max(0f, Math.min(1f, (height + 80f) / 240f));
		/*
		 * The snapshot has 256 height samples, including both region boundaries.
		 * Spreading indices 0..255 over the complete 2048-unit span makes adjacent
		 * auxiliary meshes meet exactly instead of leaving an 8-unit black seam.
		 */
		target.put(terrainCoordinate(x)).put(height).put(terrainCoordinate(y));
		target.put(0.04f + normalized * 0.12f)
				.put(0.28f + normalized * 0.58f)
				.put(0.34f + normalized * 0.30f);
		target.put(x / 255f).put(1f - y / 255f);
	}

	static float terrainCoordinate(final int sample) {
		return Math.max(0, Math.min(255, sample)) * TERRAIN_COORDINATE_SCALE;
	}

	@Override
	public void close() {
		for (final Map<Integer, CompletableFuture<PreparedMesh>> levels
				: pending.values())
			for (final CompletableFuture<PreparedMesh> future : levels.values())
				future.cancel(false);
		pending.clear();
		preparationExecutor.shutdownNow();
		for (final Map<Integer, Mesh> levels : meshes.values())
			for (final Mesh mesh : levels.values())
				GL15.glDeleteBuffers(mesh.vbo());
		meshes.clear();
		while (!deferredDeletes.isEmpty())
			GL15.glDeleteBuffers(deferredDeletes.removeFirst());
	}

	private record Mesh(int vbo, int vertexCount) {}

	private record PreparedMesh(FloatBuffer vertices, int vertexCount) {}
}
