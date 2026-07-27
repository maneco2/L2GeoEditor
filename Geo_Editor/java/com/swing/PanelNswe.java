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
package com.swing;

import com.geo.GeoBlockSelector;
import com.geo.GeoBlockSelector.GeoBlockEntry;
import com.geo.GeoCell;
import com.geo.GeoEngine;
import com.jogl.GLDisplay;
import com.swing.defaults.DefaultToggleButton;
import com.util.FastArrayList;
import com.util.Util;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * L2J source.
 *
 * @author Forsaiken aka Patrick
 * @author Maneco2
 */
@SuppressWarnings("serial")
public final class PanelNswe extends JPanel implements ActionListener {
	private final NsweButton[] _buttonsNswe;

	public PanelNswe() {
		_buttonsNswe = new NsweButton[GeoEngine.NSWE_MASK + 1];
		for (int i = _buttonsNswe.length; i-- > 0;) {
			_buttonsNswe[i] = new NsweButton(i);
			_buttonsNswe[i].addActionListener(this);
		}
		initLayout();
	}

	private final void initLayout() {
		setLayout(new FlowLayout(FlowLayout.CENTER, 2, 3));
		setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
		add(_buttonsNswe[GeoEngine.NSWE_NONE]);
		add(_buttonsNswe[GeoEngine.NORTH]);
		add(_buttonsNswe[GeoEngine.SOUTH]);
		add(_buttonsNswe[GeoEngine.WEST]);
		add(_buttonsNswe[GeoEngine.EAST]);
		add(_buttonsNswe[GeoEngine.NORTH | GeoEngine.WEST]);
		add(_buttonsNswe[GeoEngine.SOUTH | GeoEngine.EAST]);
		add(_buttonsNswe[GeoEngine.NORTH | GeoEngine.EAST]);
		add(_buttonsNswe[GeoEngine.SOUTH | GeoEngine.WEST]);
		add(_buttonsNswe[GeoEngine.NORTH | GeoEngine.SOUTH]);
		add(_buttonsNswe[GeoEngine.WEST | GeoEngine.EAST]);
		add(_buttonsNswe[GeoEngine.NNORTH]);
		add(_buttonsNswe[GeoEngine.NSOUTH]);
		add(_buttonsNswe[GeoEngine.NWEST]);
		add(_buttonsNswe[GeoEngine.NEAST]);
		add(_buttonsNswe[GeoEngine.NSWE_ALL]);
	}

	private final void setButtonsEnabled(final boolean enabled) {
		for (final NsweButton buttonNswe : _buttonsNswe) {
			buttonNswe.setEnabled(enabled);
		}
	}

	private final void setButtonSelected(final NsweButton selected) {
		for (final NsweButton buttonNswe : _buttonsNswe) {
			if (buttonNswe == selected)
				buttonNswe.setSelected(true);
			else
				buttonNswe.setSelected(false);
		}
	}

	public final void onSelectedCellUpdated() {
		final GeoCell cell = FrameMain.getInstance().getSelectedGeoCell();
		if (cell == null) {
			setButtonSelected(null);
			setButtonsEnabled(false);
		} else {
			setButtonsEnabled(true);
			setButtonSelected(_buttonsNswe[GeoEngine.invertNSWE(cell.getNSWE())]);
		}
	}

	/**
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public final void actionPerformed(final ActionEvent e) {
		final NsweButton nsweButton = (NsweButton) e.getSource();
		setButtonSelected(nsweButton);

		final GeoBlockSelector selector = GeoBlockSelector.getInstance();
		FastArrayList<GeoCell> cells;
		int i;
		for (GeoBlockEntry entry = selector.getHead(), tail = selector.getTail(); (entry = entry.getNext()) != tail;) {
			cells = entry.getValue();
			for (i = cells.size(); i-- > 0;) {
				cells.getUnsafe(i).setNswe(GeoEngine.invertNSWE(nsweButton.getNswe()));
			}
		}

		GLDisplay.getInstance().requestFocus();
	}

	private static final class NsweButton extends DefaultToggleButton {
		private final short _nswe;

		public NsweButton(final int nswe) {
			_nswe = (short) nswe;
			setToolTipText("Block directions: " + GeoEngine.nameOfNSWE(nswe));
			setFocusPainted(false);
			setMargin(new Insets(2, 2, 2, 2));

			final BufferedImage img = updateThemeIcon();
			if (img != null) {
				final int width = img.getWidth() + 12;
				final int height = img.getHeight() + 12;
				setPreferredSize(new Dimension(width, height));
				setMaximumSize(new Dimension(width, height));
				setMinimumSize(new Dimension(width, height));
			} else {
				setText(GeoEngine.nameOfNSWE(nswe));
			}
		}

		@Override
		public void updateUI() {
			super.updateUI();
			updateThemeIcon();
		}

		private BufferedImage updateThemeIcon() {
			final BufferedImage source = Util.loadImage("./data/icon/nswe-" + _nswe + ".png");
			if (source == null)
				return null;
			final ImageIcon imageIcon = new ImageIcon(source);
			setIcon(imageIcon);
			// Nimbus darkens generated disabled icons too aggressively. Keeping the
			// themed glyph preserves direction readability before a cell is selected.
			setDisabledIcon(imageIcon);
			return source;
		}

		public final short getNswe() {
			return _nswe;
		}
	}
}
