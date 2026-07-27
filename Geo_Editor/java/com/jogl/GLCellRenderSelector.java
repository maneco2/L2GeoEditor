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
import com.geo.GeoCell;
import com.geo.GeoEngine;
import com.geo.GeoRegion;
import com.jogl.renderer.VBOGLSLRenderer;
import com.util.TaskExecutor;
import com.util.Util;
import com.util.Util.FastComparator;

import javax.media.opengl.GL2;

/**
 * L2J project source.
 * 
 * @author Forsaiken aka Patrick
 */
public final class GLCellRenderSelector {
	public static final FastComparator<GLSubRenderSelector> GEO_BLOCK_COMPARATOR = new FastComparator<GLSubRenderSelector>() {
		@Override
		public final boolean compare(final GLSubRenderSelector o1, final GLSubRenderSelector o2) {
			final GLCamera camera = GLDisplay.getInstance().getCamera();
			final float dx1 = camera.getX() - o1.getGeoBlock().getGeoX();
			final float dz1 = camera.getZ() - o1.getGeoBlock().getGeoY();
			final float dx2 = camera.getX() - o2.getGeoBlock().getGeoX();
			final float dz2 = camera.getZ() - o2.getGeoBlock().getGeoY();
			return dx1 * dx1 + dz1 * dz1 > dx2 * dx2 + dz2 * dz2;
		}
	};

	public static final FastComparator<GeoCell> GEO_CELL_COMPARATOR = new FastComparator<GeoCell>() {
		@Override
		public final boolean compare(final GeoCell o1, final GeoCell o2) {
			final GLCamera camera = GLDisplay.getInstance().getCamera();
			final float dx1 = camera.getX() - o1.getRenderX();
			final float dy1 = camera.getY() - o1.getRenderY();
			final float dz1 = camera.getZ() - o1.getRenderZ();
			final float dx2 = camera.getX() - o2.getRenderX();
			final float dy2 = camera.getY() - o2.getRenderY();
			final float dz2 = camera.getZ() - o2.getRenderZ();
			return dx1 * dx1 + dy1 * dy1 + dz1 * dz1 > dx2 * dx2 + dy2 * dy2 + dz2 * dz2;
		}
	};

	public static final int MIN_VIS_GRID_RANGE = 8;
	public static final int MAX_VIS_GRID_RANGE = 96;

	private final TaskExecutor _taskExecutor;
	private float[][] _frustum;

	private GLSubRenderSelector[] _geoBlocks;
	private GLSubRenderSelector[] _geoBlocks2;
	private int _geoBlocksSize;

	private int _camBlockX;
	private int _camBlockY;
	private int _camGeoX;
	private int _camGeoY;
	private boolean _forceUpdateFrustum;
	private boolean _forceUpdateGeoBlocks;
	private boolean _freezeGrid;
	private boolean _circularGrid;
	private int _gridRange;

	private GeoRegion _region;

	public GLCellRenderSelector() {
		_geoBlocks = new GLSubRenderSelector[0];
		_geoBlocks2 = new GLSubRenderSelector[0];
		_taskExecutor = new TaskExecutor(Runtime.getRuntime().availableProcessors());
	}

	public final void forceUpdateFrustum() {
		_forceUpdateFrustum = true;
	}

	public final void forceUpdateGeoBlocks() {
		_forceUpdateGeoBlocks = true;
	}

	public final void select(final GL2 gl, final GLCamera camera, final boolean freezeGrid) {
		final GeoRegion region = GeoEngine.getInstance().getActiveRegion();
		if (_region != region) {
			_region = region;
			_forceUpdateGeoBlocks = true;
		}

		if (_region == null) {
			_geoBlocksSize = 0;
			return;
		}

		if (camera.positionXZChanged() || _freezeGrid != freezeGrid || _gridRange != Config.VIS_GRID_RANGE
				|| _circularGrid != Config.CIRCULAR_GEODATA_GRID
				|| _forceUpdateGeoBlocks) {
			if (_circularGrid != Config.CIRCULAR_GEODATA_GRID)
				_forceUpdateGeoBlocks = true;
			final int geoX = Math.max(Math.min(camera.getGeoX(), _region.getGeoX(GeoEngine.GEO_REGION_SIZE - 1)),
					_region.getGeoX(0));
			final int geoY = Math.max(Math.min(camera.getGeoY(), _region.getGeoY(GeoEngine.GEO_REGION_SIZE - 1)),
					_region.getGeoY(0));
			final int camBlockX = GeoEngine.getBlockXY(geoX);
			final int camBlockY = GeoEngine.getBlockXY(geoY);
			_camGeoX = geoX;
			_camGeoY = geoY;

			if (camBlockX != _camBlockX || camBlockY != _camBlockY || _freezeGrid != freezeGrid
					|| _gridRange != Config.VIS_GRID_RANGE || _forceUpdateGeoBlocks) {
				_camBlockX = camBlockX;
				_camBlockY = camBlockY;

				final int range = Config.VIS_GRID_RANGE;
				final int requiredSize = (range * 2 + 1) * (range * 2 + 1);
				final boolean needUpdate = _geoBlocks.length != requiredSize || _forceUpdateGeoBlocks;
				if (_geoBlocks.length != requiredSize) {
					_geoBlocks = new GLSubRenderSelector[requiredSize];
					_geoBlocks2 = new GLSubRenderSelector[requiredSize];
					for (int i = requiredSize; i-- > 0;) {
						_geoBlocks[i] = new GLSubRenderSelector();
					}
				}

				if (!freezeGrid || needUpdate) {
					final int diffBlockXNeg = camBlockX - range;
					final int diffBlockXPos = camBlockX + range;
					final int diffBlockYNeg = camBlockY - range;
					final int diffBlockYPos = camBlockY + range;

					final int minBlockX = Math.max(diffBlockXNeg + (diffBlockXPos > GeoEngine.GEO_REGION_SIZE - 1
							? GeoEngine.GEO_REGION_SIZE - 1 - diffBlockXPos
							: 0), 0);
					final int maxBlockX = Math.min(diffBlockXPos + (diffBlockXNeg < 0 ? -diffBlockXNeg : 0),
							GeoEngine.GEO_REGION_SIZE - 1);
					final int minBlockY = Math.max(diffBlockYNeg + (diffBlockYPos > GeoEngine.GEO_REGION_SIZE - 1
							? GeoEngine.GEO_REGION_SIZE - 1 - diffBlockYPos
							: 0), 0);
					final int maxBlockY = Math.min(diffBlockYPos + (diffBlockYNeg < 0 ? -diffBlockYNeg : 0),
							GeoEngine.GEO_REGION_SIZE - 1);

					GeoBlockSelector.getInstance().checkDeselection(minBlockX, maxBlockX, minBlockY, maxBlockY);

					_geoBlocksSize = 0;
					for (int x = minBlockX, y; x < maxBlockX; x++) {
						for (y = minBlockY; y < maxBlockY; y++) {
							_geoBlocks[_geoBlocksSize++].setGeoBlock(_region.getBlockByBlockXY(x, y));
						}
					}

					_forceUpdateGeoBlocks = false;
					_forceUpdateFrustum = true;
				}
			}
		}

		_freezeGrid = freezeGrid;
		_gridRange = Config.VIS_GRID_RANGE;
		_circularGrid = Config.CIRCULAR_GEODATA_GRID;

		if (camera.positionXZChanged() || camera.positionYChanged() || camera.rotationChanged()
				|| _forceUpdateFrustum) {
			_forceUpdateFrustum = false;
			_frustum = camera.getFrustum(gl);
			_taskExecutor.execute(_geoBlocks, _geoBlocksSize);

			if (Config.USE_TRANSPARENCY) {
				System.arraycopy(_geoBlocks, 0, _geoBlocks2, 0, _geoBlocksSize);
				Util.mergeSort(_geoBlocks2, _geoBlocks, _geoBlocksSize, GEO_BLOCK_COMPARATOR);
			}
		}
	}

	public final int getElementsToRender() {
		return _geoBlocksSize;
	}

	/** Returns the worker count used by visibility selection. */
	public final int getSelectionParallelism() {
		return _taskExecutor.getParallelism();
	}

	public final GLSubRenderSelector getElementToRender(final int index) {
		return _geoBlocks[index];
	}

	public final boolean isVisible(final GeoBlock block) {
		final float x1 = block.getGeoX();
		final float x2 = x1 + 8f;
		final float y1 = block.getMinHeight() / 16f;
		final float y2 = block.getMaxHeight() / 16f;
		final float z1 = block.getGeoY();
		final float z2 = z1 + 8f;

		float[] plane;
		float p, px1, px2, py1, py2, pz1, pz2;
		for (int i = 6; i-- > 0;) {
			plane = _frustum[i];
			p = plane[0];
			px1 = p * x1;
			px2 = p * x2;
			p = plane[1];
			py1 = p * y1;
			py2 = p * y2;
			p = plane[2];
			pz1 = p * z1;
			pz2 = p * z2;
			p = plane[3];

			if (px1 + py1 + pz1 + p <= 0f && px2 + py1 + pz1 + p <= 0f && px1 + py2 + pz1 + p <= 0f
					&& px2 + py2 + pz1 + p <= 0f && px1 + py1 + pz2 + p <= 0f && px2 + py1 + pz2 + p <= 0f
					&& px1 + py2 + pz2 + p <= 0f && px2 + py2 + pz2 + p <= 0f)
				return false;
		}
		return true;
	}

	public final boolean isVisible(final GeoCell cell) {
		final float x;
		final float y;
		final float z;
		final float rad;

		if (cell.isBig()) {
			x = cell.getRenderX() + 4f;
			y = cell.getRenderY() - 0.1f;
			z = cell.getRenderZ() + 4f;
			rad = 5f;
		} else {
			// TODO Implement proper (Used for heavy locations like ToI)
			// if (cell.getBlock().nGetLayerCount(cell.getGeoX(), cell.getGeoY()) > 6 &&
			// Math.abs(cell.getRenderY() - getDisplay().getCamera().getY()) >= 200)
			// return false;

			x = cell.getRenderX() + 0.5f;
			y = cell.getRenderY() - 0.1f;
			z = cell.getRenderZ() + 0.5f;
			rad = 1f;
		}

		float[] plane;
		for (int i = 6; i-- > 0;) {
			plane = _frustum[i];
			if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] <= -rad)
				return false;
		}
		return true;
	}

	private final boolean isInsideCircularCellMask(final GeoCell cell) {
		if (!Config.CIRCULAR_GEODATA_GRID)
			return true;
		return CellGridMask.containsCellCenter(cell.getGeoX(), cell.getGeoY(), _camGeoX, _camGeoY,
				getCircularRadiusInCells());
	}

	public final boolean isCellInsideCircularGrid(final GeoCell cell) {
		return isInsideCircularCellMask(cell);
	}

	private final int getCircularRadiusInCells() {
		return Config.VIS_GRID_RANGE * GeoEngine.GEO_BLOCK_SHIFT;
	}

	private final boolean isFullCircularBlock(final GeoBlock block) {
		if (!Config.CIRCULAR_GEODATA_GRID)
			return false;
		final int radius = getCircularRadiusInCells();
		final int minX = block.getGeoX();
		final int minY = block.getGeoY();
		final int maxX = minX + GeoEngine.GEO_BLOCK_SHIFT - 1;
		final int maxY = minY + GeoEngine.GEO_BLOCK_SHIFT - 1;
		return CellGridMask.containsCellCenter(minX, minY, _camGeoX, _camGeoY, radius)
				&& CellGridMask.containsCellCenter(minX, maxY, _camGeoX, _camGeoY, radius)
				&& CellGridMask.containsCellCenter(maxX, minY, _camGeoX, _camGeoY, radius)
				&& CellGridMask.containsCellCenter(maxX, maxY, _camGeoX, _camGeoY, radius);
	}

	public final class GLSubRenderSelector implements Runnable {
		private GeoBlock _block;
		private GeoCell[] _geoCells;
		private GeoCell[] _geoCells2;
		private int _count;

		public GLSubRenderSelector() {
			_geoCells = new GeoCell[0];
			_geoCells2 = new GeoCell[0];
		}

		public final void setGeoBlock(final GeoBlock block) {
			_block = block;
		}

		public final GeoBlock getGeoBlock() {
			return _block;
		}

		public final int getElementsToRender() {
			return _count;
		}

		public final GeoCell getElementToRender(final int index) {
			return _geoCells[index];
		}

		public final boolean isCellInsideCircularGrid(final GeoCell cell) {
			return GLCellRenderSelector.this.isCellInsideCircularGrid(cell);
		}

		private final void ensureCapacity(final int count) {
			if (_geoCells.length < count) {
				_geoCells = new GeoCell[count];
				_geoCells2 = new GeoCell[count];
			}
		}

		private final void addElementToRender(final GeoCell cell) {
			_geoCells[_count++] = cell;
		}

		@Override
		public final void run() {
			_count = 0;
			switch (_block.getType()) {
			case GeoEngine.GEO_BLOCK_TYPE_FLAT: {
				final GeoCell cell = _block.getCells()[0];
				if (GLCellRenderSelector.this.isVisible(cell)) {
					ensureCapacity(1);
					addElementToRender(cell);
				}
				break;
			}

			case GeoEngine.GEO_BLOCK_TYPE_COMPLEX: {
				if (GLCellRenderSelector.this.isVisible(_block)) {
					if (!(GLDisplay.getInstance().getRenderer() instanceof VBOGLSLRenderer)) {
						final GeoCell[] cells = _block.getCells();
						ensureCapacity(cells.length);
						final boolean fullBlock = GLCellRenderSelector.this.isFullCircularBlock(_block);
						for (final GeoCell cell : cells) {
							if (fullBlock || (GLCellRenderSelector.this.isVisible(cell)
									&& GLCellRenderSelector.this.isInsideCircularCellMask(cell)))
								addElementToRender(cell);
						}

						if (Config.USE_TRANSPARENCY) {
							System.arraycopy(_geoCells, 0, _geoCells2, 0, _count);
							Util.mergeSort(_geoCells2, _geoCells, _count, GEO_CELL_COMPARATOR);
						}
					} else {
						_count = 64;
					}
				}
				break;
			}

			case GeoEngine.GEO_BLOCK_TYPE_MULTILAYER: {
				if (GLCellRenderSelector.this.isVisible(_block)) {
					final GeoCell[] cells = _block.getCells();
					ensureCapacity(cells.length);
					final boolean fullBlock = GLCellRenderSelector.this.isFullCircularBlock(_block);

					for (final GeoCell cell : cells) {
						if (fullBlock || (GLCellRenderSelector.this.isVisible(cell)
								&& GLCellRenderSelector.this.isInsideCircularCellMask(cell)))
							addElementToRender(cell);
					}

					if (Config.USE_TRANSPARENCY) {
						System.arraycopy(_geoCells, 0, _geoCells2, 0, _count);
						Util.mergeSort(_geoCells2, _geoCells, _count, GEO_CELL_COMPARATOR);
					}
				}
				break;
			}
			}
		}
	}
}
