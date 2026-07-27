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
package com.geo;

import com.geo.blocks.GeoBlockComplex;
import com.geo.blocks.GeoBlockFlat;
import com.geo.blocks.GeoBlockMultiLayer;
import com.swing.DialogSave;
import com.util.GeoByteBuffer;
import com.util.GeoReader;
import com.util.GeoStreamWriter;
import com.util.GeoWriter;
import com.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * L2J source.
 *
 * @author Forsaiken aka Patrick
 * @author Maneco2
 * @version 1.1
 */
public final class GeoRegion {
	public static final byte getType(final GeoReader reader, final boolean l2j) {
		if (l2j)
			return reader.get();

		switch (reader.getShort()) {
		case 0x0000:
			return GeoEngine.GEO_BLOCK_TYPE_FLAT;

		case 0x0040:
			return GeoEngine.GEO_BLOCK_TYPE_COMPLEX;

		default:
			return GeoEngine.GEO_BLOCK_TYPE_MULTILAYER;
		}
	}

	public static final void putType(final GeoWriter writer, final boolean l2j, final byte type) {
		if (l2j) {
			writer.put(type);
		} else {
			switch (type) {
			case GeoEngine.GEO_BLOCK_TYPE_FLAT:
				writer.putShort((short) 0x0000);
				break;

			case GeoEngine.GEO_BLOCK_TYPE_COMPLEX:
				writer.putShort((short) 0x0040);
				break;

			case GeoEngine.GEO_BLOCK_TYPE_MULTILAYER:
				writer.putShort((short) 0x0080); // TODO check this
				break;

			default:
				throw new IllegalArgumentException("Unkown type: " + type);
			}
		}
	}

	private final File _file;
	private final int _regionX;
	private final int _regionY;
	private final int _minGeoX;
	private final int _maxGeoX;
	private final int _minGeoY;
	private final int _maxGeoY;
	private GeoBlock[][] _geoBlocks;
	private volatile boolean _layerCountsDirty = true;
	private volatile long _totalLayerCount;
	private volatile long[] _layerCountsByType = new long[3];
	private volatile boolean _modified;
	private int _unloadBlockIndex;
	private long _discardedLayerCount;
	private GeoByteBuffer[][] _geoBlocksData;

	public GeoRegion(final int regionX, final int regionY, final GeoReader reader, final boolean l2j, final File file) {
		_file = file;
		_regionX = regionX;
		_regionY = regionY;
		_minGeoX = GeoEngine.getGeoXY(regionX, 0);
		_maxGeoX = GeoEngine.getGeoXY(regionX, GeoEngine.GEO_REGION_SIZE - 1);
		_minGeoY = GeoEngine.getGeoXY(regionY, 0);
		_maxGeoY = GeoEngine.getGeoXY(regionY, GeoEngine.GEO_REGION_SIZE - 1);
		_geoBlocks = new GeoBlock[GeoEngine.GEO_REGION_SIZE][GeoEngine.GEO_REGION_SIZE];
		_geoBlocksData = new GeoByteBuffer[GeoEngine.GEO_REGION_SIZE][GeoEngine.GEO_REGION_SIZE];

		GeoBlock block;
		GeoByteBuffer writer;
		for (int blockX = 0, blockY; blockX < GeoEngine.GEO_REGION_SIZE; blockX++) {
			for (blockY = 0; blockY < GeoEngine.GEO_REGION_SIZE; blockY++) {
				block = readBlock(blockX, blockY, reader, l2j);
				_geoBlocks[blockX][blockY] = block;
				writer = GeoByteBuffer.allocate(block.getRequiredCapacity(true));
				block.writeTo(writer, true);
				_geoBlocksData[blockX][blockY] = writer;
			}
		}
	}

	private final GeoBlock readBlock(final int blockX, final int blockY, final GeoReader reader, final boolean l2j) {
		final int geoX = GeoEngine.getGeoXY(_regionX, blockX);
		final int geoY = GeoEngine.getGeoXY(_regionY, blockY);
		final int type = getType(reader, l2j);
		switch (type) {
		case GeoEngine.GEO_BLOCK_TYPE_FLAT:
			return new GeoBlockFlat(reader, geoX, geoY, l2j).setRegion(this);

		case GeoEngine.GEO_BLOCK_TYPE_COMPLEX:
			return new GeoBlockComplex(reader, geoX, geoY, l2j).setRegion(this);

		case GeoEngine.GEO_BLOCK_TYPE_MULTILAYER:
			return new GeoBlockMultiLayer(reader, geoX, geoY, l2j).setRegion(this);

		default:
			throw new RuntimeException("Unknown type: " + type);
		}
	}

	public final void convertBlock(final GeoBlock block, final byte type) {
		final int blockX = block.getBlockX();
		final int blockY = block.getBlockY();
		final GeoBlock convertedBlock;

		switch (type) {
		case GeoEngine.GEO_BLOCK_TYPE_FLAT:
			convertedBlock = GeoBlockFlat.convertFrom(block).setRegion(this);
			break;

		case GeoEngine.GEO_BLOCK_TYPE_COMPLEX:
			convertedBlock = GeoBlockComplex.convertFrom(block).setRegion(this);
			break;

		case GeoEngine.GEO_BLOCK_TYPE_MULTILAYER:
			convertedBlock = GeoBlockMultiLayer.convertFrom(block).setRegion(this);
			break;

		default:
			throw new IllegalArgumentException("Unkown type: " + type);
		}

		block.unload();
		_geoBlocks[blockX][blockY] = convertedBlock;
		_layerCountsDirty = true;
		_modified = true;
	}

	public final void convertBlock(final int blockX, final int blockY, final byte type) {
		convertBlock(_geoBlocks[blockX][blockY], type);
	}

	public final void restoreBlock(final GeoBlock block) {
		final int blockX = block.getBlockX();
		final int blockY = block.getBlockY();
		final GeoByteBuffer reader = _geoBlocksData[blockX][blockY];
		reader.clear();
		block.unload();
		_geoBlocks[blockX][blockY] = readBlock(blockX, blockY, reader, true);
		_layerCountsDirty = true;
		_modified = true;
	}

	public final void restoreBlock(final int blockX, final int blockY) {
		restoreBlock(_geoBlocks[blockX][blockY]);
	}

	public final File getFile() {
		return _file;
	}

	public final GeoBlock[][] getGeoBlocks() {
		return _geoBlocks;
	}

	/** Returns the total number of cell layers currently held by this region. */
	public final long getTotalLayerCount() {
		refreshLayerCounts();
		return _totalLayerCount;
	}

	private final void refreshLayerCounts() {
		if (!_layerCountsDirty)
			return;
		long count = 0L;
		final long[] byType = new long[3];
		for (int blockX = 0; blockX < _geoBlocks.length; blockX++) {
			for (int blockY = 0; blockY < _geoBlocks[blockX].length; blockY++) {
				final GeoBlock block = _geoBlocks[blockX][blockY];
				final long layers = block.getCells().length;
				count += layers;
				if (block.getType() < byType.length)
					byType[block.getType()] += layers;
			}
		}
		_totalLayerCount = count;
		_layerCountsByType = byType;
		_layerCountsDirty = false;
	}

	/** Returns the number of cell layers belonging to blocks of the given type. */
	public final long getTotalLayerCount(final byte blockType) {
		refreshLayerCounts();
		return blockType >= 0 && blockType < _layerCountsByType.length ? _layerCountsByType[blockType] : 0L;
	}

	public final int getRegionX() {
		return _regionX;
	}

	public final int getRegionY() {
		return _regionY;
	}

	public final int getGeoX(final int blockX) {
		return GeoEngine.getGeoXY(getRegionX(), blockX);
	}

	public final int getGeoY(final int blockY) {
		return GeoEngine.getGeoXY(getRegionY(), blockY);
	}

	public final String getName() {
		return (getRegionX() + 10) + "_" + (getRegionY() + 10);
	}

	public final byte nGetType(final int geoX, final int geoY) {
		final int blockX = GeoEngine.getBlockXY(geoX);
		final int blockY = GeoEngine.getBlockXY(geoY);
		return _geoBlocks[blockX][blockY].getType();
	}

	public final GeoCell nGetCellChecked(int geoX, int geoY, final int x) {
		if (geoX < _minGeoX || geoX > _maxGeoX || geoY < _minGeoY || geoY > _maxGeoY)
			return null;

		return nGetCell(geoX, geoY, x);
	}

	public final GeoCell nGetCell(final int geoX, final int geoY, final int x) {
		final int blockX = GeoEngine.getBlockXY(geoX);
		final int blockY = GeoEngine.getBlockXY(geoY);
		return _geoBlocks[blockX][blockY].nGetCell(geoX, geoY, x);
	}

	public final GeoCell nGetCellByLayer(final int geoX, final int geoY, final int layer) {
		final int blockX = GeoEngine.getBlockXY(geoX);
		final int blockY = GeoEngine.getBlockXY(geoY);
		return _geoBlocks[blockX][blockY].nGetCellByLayer(geoX, geoY, layer);
	}

	public final GeoBlock getBlock(final int geoX, final int geoY) {
		final int blockX = GeoEngine.getBlockXY(geoX);
		final int blockY = GeoEngine.getBlockXY(geoY);
		return _geoBlocks[blockX][blockY];
	}

	public final GeoBlock getBlockByBlockXY(final int blockX, final int blockY) {
		return _geoBlocks[blockX][blockY];
	}

	public final void setBlock(final int geoX, final int geoY, final GeoBlock block) {
		final int blockX = GeoEngine.getBlockXY(geoX);
		final int blockY = GeoEngine.getBlockXY(geoY);
		_geoBlocks[blockX][blockY] = block;
		_layerCountsDirty = true;
		_modified = true;
	}

	public final int nGetLayerCount(final int geoX, final int geoY) {
		final int blockX = GeoEngine.getBlockXY(geoX);
		final int blockY = GeoEngine.getBlockXY(geoY);
		return _geoBlocks[blockX][blockY].nGetLayerCount(geoX, geoY);
	}

	public final GeoCell addLayer(final int geoX, final int geoY, final short heightAndNSWE) {
		final int blockX = GeoEngine.getBlockXY(geoX);
		final int blockY = GeoEngine.getBlockXY(geoY);
		final GeoCell added = _geoBlocks[blockX][blockY].addLayer(geoX, geoY, heightAndNSWE);
		if (added != null)
			_layerCountsDirty = true;
		if (added != null)
			_modified = true;
		return added;
	}

	public final boolean isModified() {
		return _modified;
	}

	public final void markModified() {
		_modified = true;
	}

	public final void markSaved() {
		_modified = false;
	}

	public final void refreshModifiedState() {
		_modified = !allDataEqual();
	}

	public final void saveTo(final OutputStream os, final boolean l2j, final DialogSave observ) throws IOException {
		if (!l2j) {
			Util.writeByte(_regionX + 10, os);
			Util.writeByte(_regionY + 10, os);

			// TODO put real data here
			Util.writeBytes(new byte[16], os);
		}

		final GeoWriter writer = GeoStreamWriter.wrap(os);
		for (int blockX = 0, blockY; blockX < GeoEngine.GEO_REGION_SIZE; blockX++) {
			for (blockY = 0; blockY < GeoEngine.GEO_REGION_SIZE; blockY++) {
				_geoBlocks[blockX][blockY].writeTo(writer, l2j);
				observ.updateProgressRegion(blockX * GeoEngine.GEO_REGION_SIZE + blockY,
						"[" + blockX + "-" + blockY + "]");
			}
		}
	}

	public final void unload() {
		while (!unloadStep(GeoEngine.GEO_REGION_SIZE * GeoEngine.GEO_REGION_SIZE)) {
			// A full unload intentionally consumes every remaining block.
		}
	}

	/**
	 * Releases the complete region graph in constant time after the renderer has
	 * detached it. The garbage collector can then reclaim blocks and cells without
	 * a multi-million-element clearing loop on the OpenGL thread.
	 */
	public final void releaseForDiscard() {
		if (_geoBlocks == null)
			return;
		_discardedLayerCount = _layerCountsDirty ? 0L : _totalLayerCount;
		_geoBlocks = null;
		_geoBlocksData = null;
		_unloadBlockIndex = GeoEngine.GEO_REGION_SIZE * GeoEngine.GEO_REGION_SIZE;
	}

	/**
	 * Releases at most {@code maxBlocks} blocks. This allows the render thread to
	 * spread expensive multilayer cleanup across frames.
	 *
	 * @return {@code true} when the complete region has been released
	 */
	public final boolean unloadStep(final int maxBlocks) {
		if (_geoBlocks == null)
			return true;

		final int blockCount = GeoEngine.GEO_REGION_SIZE * GeoEngine.GEO_REGION_SIZE;
		final int limit = Math.min(blockCount, _unloadBlockIndex + Math.max(1, maxBlocks));
		while (_unloadBlockIndex < limit) {
			final int blockX = _unloadBlockIndex / GeoEngine.GEO_REGION_SIZE;
			final int blockY = _unloadBlockIndex % GeoEngine.GEO_REGION_SIZE;
			final GeoBlock block = _geoBlocks[blockX][blockY];
			if (block != null) {
				_discardedLayerCount += block.getCells().length;
				block.unload();
				_geoBlocks[blockX][blockY] = null;
				_geoBlocksData[blockX][blockY] = null;
			}
			_unloadBlockIndex++;
		}
		if (_unloadBlockIndex < blockCount)
			return false;

		_geoBlocks = null;
		_geoBlocksData = null;
		return true;
	}

	public final long getDiscardedLayerCount() {
		return _discardedLayerCount;
	}

	public final boolean dataEqualFor(final GeoBlock block) {
		final GeoByteBuffer geoBlockData = _geoBlocksData[block.getBlockX()][block.getBlockY()];
		geoBlockData.clear();
		return block.dataEquals(geoBlockData);
	}

	public final boolean allDataEqual() {
		GeoBlock[] block1D;
		GeoByteBuffer[] geoBlocksData1D;
		GeoByteBuffer geoBlockData;
		for (int blockX = GeoEngine.GEO_REGION_SIZE, blockY; blockX-- > 0;) {
			block1D = _geoBlocks[blockX];
			geoBlocksData1D = _geoBlocksData[blockX];
			for (blockY = GeoEngine.GEO_REGION_SIZE; blockY-- > 0;) {
				geoBlockData = geoBlocksData1D[blockY];
				geoBlockData.clear();
				if (!block1D[blockY].dataEquals(geoBlockData))
					return false;
			}
		}
		return true;
	}
}
