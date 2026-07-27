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

import com.entity.SelectionState;

/**
 * L2J source.
 *
 * @author Forsaiken aka Patrick
 * @author Maneco2
 * @version 1.1
 */
public abstract class GeoCell {
	public static final GeoCell[] EMPTY_ARRAY = new GeoCell[0];

	private GeoBlock _block;
	private SelectionState _selectionState;

	public GeoCell(final GeoBlock block) {
		_block = block;
		_selectionState = SelectionState.NORMAL;
	}

	public final GeoBlock getBlock() {
		return _block;
	}

	public final SelectionState getSelectionState() {
		return _selectionState;
	}

	public final void setSelectionState(final SelectionState selectionState) {
		_selectionState = selectionState;
	}

	public final float getRenderX() {
		return getGeoX();
	}

	/**
	 * 
	 * @return The adjusted Y to render
	 */
	public final float getRenderY() {
		return getHeight() / 16f;
	}

	public final float getRenderZ() {
		return getGeoY();
	}

	public final void unload() {
		_block = null;
	}

	protected final void markModified() {
		if (_block != null && _block.getRegion() != null)
			_block.getRegion().markModified();
	}

	public abstract boolean isBig();

	public abstract short getHeight();

	public abstract short getNSWE();

	public abstract short getHeightAndNSWE();

	public abstract void addHeight(final short height);

	public abstract int getGeoX();

	public abstract int getGeoY();

	public abstract int getCellX();

	public abstract int getCellY();

	public abstract void setHeightAndNSWE(final short heightAndNSWE);

	public abstract void setNswe(final short nswe);
}
