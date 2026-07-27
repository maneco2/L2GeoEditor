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
package com.exceptions;

import java.io.File;

/**
 * L2J project source.
 * 
 * @author Forsaiken aka Patrick
 * @author Maneco2
 * @version 1.1
 */
@SuppressWarnings("serial")
public final class GeoFileNotFoundException extends RuntimeException {
	private final File _file;
	private final boolean _l2j;

	public GeoFileNotFoundException(final File file, final boolean l2j) {
		super((l2j ? "L2J" : "L2Off") + " geodata file not found: " + file.getPath());
		_file = file;
		_l2j = l2j;
	}

	public File getFile() {
		return _file;
	}

	public boolean isL2j() {
		return _l2j;
	}
}
