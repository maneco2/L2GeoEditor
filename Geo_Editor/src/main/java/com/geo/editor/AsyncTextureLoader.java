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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

/** Decodes and resizes region textures away from the JavaFX/OpenGL thread. */
final class AsyncTextureLoader implements AutoCloseable {
	record Prepared(String path, int width, int height, ByteBuffer pixels) {}

	private final ConcurrentHashMap<String, CompletableFuture<Prepared>> pending =
			new ConcurrentHashMap<>();
	private final ExecutorService executor;

	AsyncTextureLoader() {
		this("GeoEditor-texture-loader", 2);
	}

	AsyncTextureLoader(final String threadName, final int threadCount) {
		executor = Executors.newFixedThreadPool(Math.max(1, threadCount), task -> {
			final Thread thread = new Thread(task, threadName);
			thread.setDaemon(true);
			return thread;
		});
	}

	void request(final File file, final int maximumSize) {
		final String path = file.getAbsolutePath();
		pending.computeIfAbsent(path, ignored -> CompletableFuture.supplyAsync(
				() -> prepare(file, maximumSize), executor));
	}

	Prepared pollReady(final File file, final int maximumSize) {
		final String path = file.getAbsolutePath();
		final CompletableFuture<Prepared> future = pending.get(path);
		if (future == null || !future.isDone())
			return null;
		pending.remove(path, future);
		return future.join();
	}

	boolean hasPending() {
		return !pending.isEmpty();
	}

	int pendingCount() {
		return pending.size();
	}

	boolean isRequested(final File file) {
		return pending.containsKey(file.getAbsolutePath());
	}

	void retain(final Set<String> activePaths) {
		pending.entrySet().removeIf(entry -> {
			if (activePaths.contains(entry.getKey()))
				return false;
			entry.getValue().cancel(false);
			return true;
		});
	}

	private static Prepared prepare(final File file, final int maximumSize) {
		try {
			final BufferedImage image = ImageIO.read(file);
			if (image == null)
				throw new IllegalArgumentException("Unsupported region texture: " + file);
			final double scale = Math.min(1d, maximumSize
					/ (double) Math.max(image.getWidth(), image.getHeight()));
			final int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
			final int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
			final ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
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
			return new Prepared(file.getAbsolutePath(), width, height, pixels);
		} catch (final Exception exception) {
			throw new IllegalStateException("Unable to prepare region texture: "
					+ file, exception);
		}
	}

	@Override
	public void close() {
		for (final CompletableFuture<Prepared> future : pending.values())
			future.cancel(false);
		pending.clear();
		executor.shutdownNow();
	}
}
