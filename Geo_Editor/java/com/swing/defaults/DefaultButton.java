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
package com.swing.defaults;

import com.swing.ThemeManager;

import javax.swing.Icon;
import javax.swing.JButton;

/**
 * L2J source.
 *
 * @author Forsaiken aka Patrick
 * @author Maneco2
 * @version 1.1
 */
@SuppressWarnings("serial")
public class DefaultButton extends JButton {
	public DefaultButton() {
		initialize();
	}

	public DefaultButton(final String text) {
		super(text);
		initialize();
	}

	public DefaultButton(final Icon icon) {
		super(icon);
		initialize();
	}

	public DefaultButton(final String text, final Icon icon) {
		super(text, icon);
		initialize();
	}

	private void initialize() {
		setRolloverEnabled(true);
		getModel().addChangeListener(event -> ThemeManager.refreshButtonState(this));
	}

	@Override
	public final void setEnabled(final boolean enabled) {
		if (isEnabled() != enabled)
			super.setEnabled(enabled);
	}

	@Override
	public final void setVisible(final boolean visible) {
		if (isVisible() != visible)
			super.setVisible(visible);
	}

	@Override
	public final void setText(final String text) {
		if (text != null) {
			if (text.equals(getText()))
				return;
		} else {
			if (getText() == null)
				return;
		}
		super.setText(text);
	}

	@Override
	public final void setIcon(final Icon icon) {
		if (getIcon() != icon)
			super.setIcon(icon);
	}
}
