/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.geo.GeoBlock;
import com.geo.GeoCell;
import com.geo.GeoEngine;
import com.geo.GeoRegion;
import com.util.GeoReader;
import com.util.GeoStreamReader;
import com.util.GeoByteBuffer;

/** Owns the single editable L2J region used by the modern application. */
public final class EditableRegionSession implements AutoCloseable {
	public record BlockCheckpoint(int[] blockXs, int[] blockYs,
			GeoByteBuffer[] blocks) {}
	private static final Pattern FILE_NAME =
			Pattern.compile("(\\d{2})_(\\d{2})\\.l2j", Pattern.CASE_INSENSITIVE);

	private final GeoRegion region;
	private DetailedRegionSnapshot snapshot;

	private EditableRegionSession(final GeoRegion region) {
		this.region = region;
		refreshSnapshot();
	}

	public static EditableRegionSession load(final File file) throws IOException {
		if (!file.isFile())
			throw new IOException("Geodata region not found: " + file);
		final Matcher matcher = FILE_NAME.matcher(file.getName());
		if (!matcher.matches())
			throw new IOException("Unsupported geodata file name: " + file.getName());
		final int regionX = Integer.parseInt(matcher.group(1)) - 10;
		final int regionY = Integer.parseInt(matcher.group(2)) - 10;
		try (FileInputStream input = new FileInputStream(file);
				BufferedInputStream buffered = new BufferedInputStream(input)) {
			final GeoReader reader = GeoStreamReader.wrap(buffered);
			return new EditableRegionSession(
					new GeoRegion(regionX, regionY, reader, true, file));
		}
	}

	public GeoRegion region() {
		return region;
	}

	public DetailedRegionSnapshot snapshot() {
		return snapshot;
	}

	public GeoCell cell(final int localGeoX, final int localGeoY,
			final int layer) {
		final int geoX = (region.getRegionX()
				<< GeoCoordinates.REGION_GEO_SHIFT) + localGeoX;
		final int geoY = (region.getRegionY()
				<< GeoCoordinates.REGION_GEO_SHIFT) + localGeoY;
		return region.nGetCellByLayer(geoX, geoY, layer);
	}

	public BlockCheckpoint captureBlocks(final int[] blockXs, final int[] blockYs) {
		if (blockXs.length != blockYs.length)
			throw new IllegalArgumentException("Mismatched block coordinates.");
		final java.util.LinkedHashSet<Integer> keys = new java.util.LinkedHashSet<>();
		for (int i = 0; i < blockXs.length; i++)
			keys.add((blockYs[i] << 8) | blockXs[i]);
		final int[] xs = new int[keys.size()];
		final int[] ys = new int[keys.size()];
		final GeoByteBuffer[] blocks = new GeoByteBuffer[keys.size()];
		int index = 0;
		for (final int key : keys) {
			xs[index] = key & 0xff;
			ys[index] = key >>> 8;
			blocks[index] = region.captureBlock(xs[index], ys[index]);
			index++;
		}
		return new BlockCheckpoint(xs, ys, blocks);
	}

	public void restoreCheckpoint(final BlockCheckpoint checkpoint) {
		restoreCheckpoint(checkpoint, false);
	}

	public void restoreCheckpoint(final BlockCheckpoint checkpoint,
			final boolean valuesOnly) {
		for (int i = 0; i < checkpoint.blocks().length; i++)
			region.restoreCapturedBlock(checkpoint.blockXs()[i],
					checkpoint.blockYs()[i], checkpoint.blocks()[i]);
		if (!valuesOnly) {
			refreshSnapshot();
			return;
		}
		for (int i = 0; i < checkpoint.blocks().length; i++) {
			final int blockX = checkpoint.blockXs()[i];
			final int blockY = checkpoint.blockYs()[i];
			final GeoBlock block = region.getBlockByBlockXY(blockX, blockY);
			for (int cellX = 0; cellX < 8; cellX++) {
				for (int cellY = 0; cellY < 8; cellY++) {
					final int localX = blockX * 8 + cellX;
					final int localY = blockY * 8 + cellY;
					final int geoX = (region.getRegionX()
							<< GeoCoordinates.REGION_GEO_SHIFT) + localX;
					final int geoY = (region.getRegionY()
							<< GeoCoordinates.REGION_GEO_SHIFT) + localY;
					final int count = block.nGetLayerCount(geoX, geoY);
					for (int layer = 0; layer < count; layer++)
						snapshot.updatePacked(localX, localY, layer,
								block.nGetCellByLayer(geoX, geoY, layer).getHeightAndNSWE());
				}
			}
			snapshot.summary().updateBlockHeights(blockX, blockY,
					block.getMaxHeight(), block.getMinHeight());
		}
	}

	public void setHeight(final int localGeoX, final int localGeoY,
			final int layer, final short height) {
		final GeoCell cell = cell(localGeoX, localGeoY, layer);
		cell.setHeightAndNSWE(GeoEngine.updateHeightOfHeightAndNSWE(
				cell.getHeightAndNSWE(), height));
		refreshEditedCell(localGeoX, localGeoY, layer, cell);
	}

	public void setNswe(final int localGeoX, final int localGeoY,
			final int layer, final short nswe) {
		final GeoCell cell = cell(localGeoX, localGeoY, layer);
		cell.setNswe(nswe);
		refreshEditedCell(localGeoX, localGeoY, layer, cell);
	}

	private void refreshEditedCell(final int localGeoX, final int localGeoY,
			final int layer, final GeoCell cell) {
		snapshot.updatePacked(localGeoX, localGeoY, layer,
				cell.getHeightAndNSWE());
		final int blockX = localGeoX >> 3;
		final int blockY = localGeoY >> 3;
		final GeoBlock block = region.getBlockByBlockXY(blockX, blockY);
		snapshot.summary().updateBlockHeights(blockX, blockY,
				block.getMaxHeight(), block.getMinHeight());
	}

	public void convertBlock(final int blockX, final int blockY,
			final byte type) {
		region.convertBlock(blockX, blockY, type);
		refreshSnapshot();
	}

	public int convertBlocks(final int[] blockXs, final int[] blockYs,
			final byte type) {
		if (blockXs.length != blockYs.length)
			throw new IllegalArgumentException("Mismatched block coordinates.");
		int converted = 0;
		for (int i = 0; i < blockXs.length; i++) {
			final GeoBlock block = region.getBlockByBlockXY(blockXs[i], blockYs[i]);
			if (block.getType() == type)
				continue;
			region.convertBlock(blockXs[i], blockYs[i], type);
			converted++;
		}
		if (converted > 0)
			refreshSnapshot();
		return converted;
	}

	public void restoreBlock(final int blockX, final int blockY) {
		region.restoreBlock(blockX, blockY);
		refreshSnapshot();
	}

	public int restoreBlocks(final int[] blockXs, final int[] blockYs) {
		if (blockXs.length != blockYs.length)
			throw new IllegalArgumentException("Mismatched block coordinates.");
		int restored = 0;
		for (int i = 0; i < blockXs.length; i++) {
			final GeoBlock block = region.getBlockByBlockXY(blockXs[i], blockYs[i]);
			if (region.dataEqualFor(block))
				continue;
			region.restoreBlock(blockXs[i], blockYs[i]);
			restored++;
		}
		if (restored > 0)
			refreshSnapshot();
		return restored;
	}

	public int addLayer(final int localGeoX, final int localGeoY,
			final short height, final boolean fullBlock,
			final boolean allowDuplicateHeight) {
		final int added = addLayerInternal(localGeoX, localGeoY, height,
				fullBlock, allowDuplicateHeight);
		if (added > 0)
			refreshSnapshot();
		return added;
	}

	public int addLayers(final int[] localGeoXs, final int[] localGeoYs,
			final short height, final boolean fullBlock,
			final boolean allowDuplicateHeight) {
		if (localGeoXs.length != localGeoYs.length)
			throw new IllegalArgumentException("Mismatched cell coordinates.");
		int added = 0;
		final java.util.HashSet<Integer> completedBlocks = new java.util.HashSet<>();
		for (int i = 0; i < localGeoXs.length; i++) {
			final int blockKey = ((localGeoYs[i] >> 3) << 8) | (localGeoXs[i] >> 3);
			if (fullBlock && !completedBlocks.add(blockKey))
				continue;
			added += addLayerInternal(localGeoXs[i], localGeoYs[i], height,
					fullBlock, allowDuplicateHeight);
		}
		if (added > 0)
			refreshSnapshot();
		return added;
	}

	private int addLayerInternal(final int localGeoX, final int localGeoY,
			final short height, final boolean fullBlock,
			final boolean allowDuplicateHeight) {
		final int blockX = localGeoX >> 3;
		final int blockY = localGeoY >> 3;
		final GeoBlock block = region.getBlockByBlockXY(blockX, blockY);
		if (block.getType() != GeoEngine.GEO_BLOCK_TYPE_MULTILAYER)
			return 0;
		final short encoded = GeoEngine.convertHeightToHeightAndNSWEALL(height);
		int added = 0;
		for (int cellX = fullBlock ? 0 : (localGeoX & 7);
				cellX <= (fullBlock ? 7 : (localGeoX & 7)); cellX++) {
			for (int cellY = fullBlock ? 0 : (localGeoY & 7);
					cellY <= (fullBlock ? 7 : (localGeoY & 7)); cellY++) {
				final int geoX = ((region.getRegionX() << GeoCoordinates.REGION_GEO_SHIFT)
						+ blockX * 8 + cellX);
				final int geoY = ((region.getRegionY() << GeoCoordinates.REGION_GEO_SHIFT)
						+ blockY * 8 + cellY);
				boolean exists = false;
				for (int layer = 0; layer < block.nGetLayerCount(geoX, geoY); layer++)
					exists |= block.nGetCellByLayer(geoX, geoY, layer).getHeight() == height;
				if (allowDuplicateHeight || !exists) {
					region.addLayer(geoX, geoY, encoded);
					added++;
				}
			}
		}
		return added;
	}

	public int removeLayers(final int localGeoX, final int localGeoY,
			final int mode, final int minimum, final int maximum,
			final boolean fullBlock) {
		final int removed = removeLayersInternal(localGeoX, localGeoY, mode,
				minimum, maximum, fullBlock);
		if (removed > 0)
			refreshSnapshot();
		return removed;
	}

	public int removeLayers(final int[] localGeoXs, final int[] localGeoYs,
			final int mode, final int minimum, final int maximum,
			final boolean fullBlock) {
		if (localGeoXs.length != localGeoYs.length)
			throw new IllegalArgumentException("Mismatched cell coordinates.");
		int removed = 0;
		final java.util.HashSet<Integer> completedBlocks = new java.util.HashSet<>();
		for (int i = 0; i < localGeoXs.length; i++) {
			final int blockKey = ((localGeoYs[i] >> 3) << 8) | (localGeoXs[i] >> 3);
			if (fullBlock && !completedBlocks.add(blockKey))
				continue;
			removed += removeLayersInternal(localGeoXs[i], localGeoYs[i], mode,
					minimum, maximum, fullBlock);
		}
		if (removed > 0)
			refreshSnapshot();
		return removed;
	}

	public int removeSelectedLayers(final int[] localGeoXs,
			final int[] localGeoYs, final int[] layers) {
		if (localGeoXs.length != localGeoYs.length
				|| localGeoXs.length != layers.length)
			throw new IllegalArgumentException("Mismatched selected layers.");
		int removed = 0;
		for (int i = 0; i < localGeoXs.length; i++)
			removed += removeLayersInternal(localGeoXs[i], localGeoYs[i], 0,
					layers[i], layers[i], false);
		if (removed > 0)
			refreshSnapshot();
		return removed;
	}

	private int removeLayersInternal(final int localGeoX, final int localGeoY,
			final int mode, final int minimum, final int maximum,
			final boolean fullBlock) {
		final int blockX = localGeoX >> 3;
		final int blockY = localGeoY >> 3;
		final GeoBlock block = region.getBlockByBlockXY(blockX, blockY);
		if (block.getType() != GeoEngine.GEO_BLOCK_TYPE_MULTILAYER)
			return 0;
		final java.util.ArrayList<GeoCell> removal = new java.util.ArrayList<>();
		for (int cellX = fullBlock ? 0 : (localGeoX & 7);
				cellX <= (fullBlock ? 7 : (localGeoX & 7)); cellX++) {
			for (int cellY = fullBlock ? 0 : (localGeoY & 7);
					cellY <= (fullBlock ? 7 : (localGeoY & 7)); cellY++) {
				final int geoX = (region.getRegionX() << GeoCoordinates.REGION_GEO_SHIFT)
						+ blockX * 8 + cellX;
				final int geoY = (region.getRegionY() << GeoCoordinates.REGION_GEO_SHIFT)
						+ blockY * 8 + cellY;
				final int count = block.nGetLayerCount(geoX, geoY);
				for (int layer = count - 1; layer >= 0; layer--) {
					final GeoCell candidate = block.nGetCellByLayer(geoX, geoY, layer);
					if ((mode == 0 && layer == minimum)
							|| (mode == 1 && layer >= minimum && layer <= maximum)
							|| (mode == 2 && candidate.getHeight() >= minimum
									&& candidate.getHeight() <= maximum))
						removal.add(candidate);
				}
			}
		}
		final int removed = block.removeCells(removal.toArray(GeoCell[]::new));
		if (removed > 0)
			region.markModified();
		return removed;
	}

	public boolean isModified() {
		return region.isModified();
	}

	public void save() throws IOException {
		final File target = region.getFile();
		final File temporary = new File(target.getParentFile(),
				target.getName() + ".modern-save.tmp");
		try {
			try (FileOutputStream output = new FileOutputStream(temporary);
					BufferedOutputStream buffered = new BufferedOutputStream(output)) {
				region.saveTo(buffered, true, null);
			}
			Files.move(temporary.toPath(), target.toPath(),
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
			region.markSaved();
		} finally {
			Files.deleteIfExists(temporary.toPath());
		}
	}

	public void refreshSnapshot() {
		final int blockSize = GeoCoordinates.REGION_SIZE;
		final short[] heights = new short[blockSize * blockSize];
		final short[] minimumHeights = new short[blockSize * blockSize];
		final byte[] types = new byte[blockSize * blockSize];
		for (int y = 0; y < blockSize; y++) {
			for (int x = 0; x < blockSize; x++) {
				final GeoBlock block = region.getBlockByBlockXY(x, y);
				final int index = y * blockSize + x;
				heights[index] = block.getMaxHeight();
				minimumHeights[index] = block.getMinHeight();
				types[index] = block.getType();
			}
		}
		final int geoSize = GeoCoordinates.REGION_GEO_SIZE;
		final int[] offsets = new int[geoSize * geoSize + 1];
		int layerTotal = 0;
		for (int localX = 0; localX < geoSize; localX++) {
			for (int localY = 0; localY < geoSize; localY++) {
				final int geoX = (region.getRegionX()
						<< GeoCoordinates.REGION_GEO_SHIFT) + localX;
				final int geoY = (region.getRegionY()
						<< GeoCoordinates.REGION_GEO_SHIFT) + localY;
				final GeoBlock block = region.getBlockByBlockXY(localX >> 3, localY >> 3);
				layerTotal = Math.addExact(layerTotal,
						block.nGetLayerCount(geoX, geoY));
				offsets[localX * geoSize + localY + 1] = layerTotal;
			}
		}
		final short[] layers = new short[layerTotal];
		int output = 0;
		for (int localX = 0; localX < geoSize; localX++) {
			for (int localY = 0; localY < geoSize; localY++) {
				final int geoX = (region.getRegionX()
						<< GeoCoordinates.REGION_GEO_SHIFT) + localX;
				final int geoY = (region.getRegionY()
						<< GeoCoordinates.REGION_GEO_SHIFT) + localY;
				final GeoBlock block = region.getBlockByBlockXY(localX >> 3, localY >> 3);
				final int count = block.nGetLayerCount(geoX, geoY);
				for (int layer = 0; layer < count; layer++)
					layers[output++] =
							block.nGetCellByLayer(geoX, geoY, layer).getHeightAndNSWE();
			}
		}
		final RegionSnapshot summary = new RegionSnapshot(region.getName(),
				region.getRegionX(), region.getRegionY(), blockSize, heights,
				minimumHeights, types);
		snapshot = new DetailedRegionSnapshot(summary, offsets, layers);
	}

	@Override
	public void close() {
		region.releaseForDiscard();
	}
}
