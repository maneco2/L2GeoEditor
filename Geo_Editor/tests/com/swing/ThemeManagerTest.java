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

import com.Config;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicMenuUI;

/**
 * Deterministic checks for the fixed GeoEditor themes.
 *
 * @author Maneco2
 * @version 1.0
 */
public final class ThemeManagerTest {
	public static void main(final String[] args) {
		ThemeManager.apply(ThemeManager.DARK);
		assertEquals(ThemeManager.DARK, Config.UI_THEME, "dark theme state");
		assertColor(0x171B21, UIManager.getColor("Panel.background"), "dark panel");
		assertColor(0xFFFFFF, UIManager.getColor("Label.foreground"), "dark text");
		assertColor(0xC7D0DA, UIManager.getColor("Label.disabledForeground"), "dark disabled text");
		assertColor(0xFFFFFF, UIManager.getColor("TitledBorder.titleColor"), "dark titled border");
		assertColor(0xFFFFFF, UIManager.getColor("FileChooser.foreground"), "dark file chooser");
		assertColor(0x00AEEF, UIManager.getColor("nimbusFocus"), "dark accent");
		assertDarkPopupStates();

		ThemeManager.apply(ThemeManager.LIGHT);
		assertEquals(ThemeManager.LIGHT, Config.UI_THEME, "light theme state");
		assertColor(0xF4F6F8, UIManager.getColor("Panel.background"), "light panel");
		assertColor(0x17212B, UIManager.getColor("Label.foreground"), "light text");
		assertColor(0x66727F, UIManager.getColor("Label.disabledForeground"), "light disabled text");
		assertColor(0x17212B, UIManager.getColor("TitledBorder.titleColor"), "light titled border");
		assertColor(0x0B1820, UIManager.getColor("Table.selectionForeground"), "table selected text");
		assertColor(0x0B1820, UIManager.getColor("ComboBox.selectionForeground"), "combo selected text");
		assertColor(0xFFFFFF, UIManager.getColor("TabbedPane.selected"), "selected tab");
		assertColor(0x00AEEF, UIManager.getColor("Slider.foreground"), "slider accent");
		assertColor(0x0B1820, UIManager.getColor("MenuItem.selectionForeground"), "popup selected text");
		assertColor(0xE9EDF1, UIManager.getColor("TextField.disabledBackground"), "disabled field");
		assertButtonStates();
		assertPopupStates();
		assertListRendererTransition();
		assertDetachedFileChooserTransition();

		ThemeManager.apply("unsupported");
		assertEquals(ThemeManager.LIGHT, Config.UI_THEME, "unsupported theme fallback");
		System.out.println("ThemeManagerTest OK");
	}

	private static void assertListRendererTransition() {
		final JList<String> list = new JList<String>(new String[] { "visible-folder-name" });
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public java.awt.Component getListCellRendererComponent(final JList<?> source, final Object value,
					final int index, final boolean selected, final boolean focus) {
				final JLabel label = (JLabel) super.getListCellRendererComponent(source, value, index, selected, focus);
				label.setForeground(Color.WHITE);
				return label;
			}
		});

		ThemeManager.apply(ThemeManager.DARK);
		ThemeManager.refresh(list);
		ThemeManager.apply(ThemeManager.LIGHT);
		ThemeManager.refresh(list);
		final java.awt.Component rendered = list.getCellRenderer().getListCellRendererComponent(list,
				"visible-folder-name", 0, false, false);
		assertColor(0x17212B, rendered.getForeground(), "list renderer after dark to light");
	}

	private static void assertButtonStates() {
		final JButton button = new JButton("State");
		ThemeManager.refreshButtonState(button);
		assertColor(0xFFFFFF, button.getBackground(), "light button");

		button.getModel().setRollover(true);
		ThemeManager.refreshButtonState(button);
		assertColor(0xE5F6FC, button.getBackground(), "light button hover");

		button.getModel().setArmed(true);
		button.getModel().setPressed(true);
		ThemeManager.refreshButtonState(button);
		assertColor(0xB7E8F7, button.getBackground(), "light button pressed");

		button.setEnabled(false);
		ThemeManager.refreshButtonState(button);
		assertColor(0xE9EDF1, button.getBackground(), "light button disabled");
	}

	private static void assertPopupStates() {
		final JPopupMenu popup = new JPopupMenu();
		final JMenuItem enabled = new JMenuItem("Enabled");
		final JMenuItem disabled = new JMenuItem("Disabled");
		disabled.setEnabled(false);
		popup.add(enabled);
		popup.add(disabled);
		ThemeManager.preparePopupMenu(popup);

		assertColor(0xFFFFFF, enabled.getBackground(), "popup normal");
		assertColor(0x174A6E, enabled.getForeground(), "light popup active text");
		enabled.getModel().setArmed(true);
		assertColor(0x00AEEF, enabled.getBackground(), "popup hover");
		assertColor(0xE9EDF1, disabled.getBackground(), "popup disabled");
		assertColor(0x8A96A3, disabled.getForeground(), "light popup disabled text");
	}

	private static void assertDarkPopupStates() {
		final JPopupMenu popup = new JPopupMenu();
		final JMenuItem enabled = new JMenuItem("Enabled");
		final JMenuItem disabled = new JMenuItem("Disabled");
		final JMenu submenu = new JMenu("Submenu");
		disabled.setEnabled(false);
		popup.add(enabled);
		popup.add(disabled);
		popup.add(submenu);
		ThemeManager.preparePopupMenu(popup);

		assertColor(0x242B34, enabled.getBackground(), "dark popup normal");
		assertColor(0xFFE08A, enabled.getForeground(), "dark popup active text");
		enabled.getModel().setArmed(true);
		assertColor(0x00AEEF, enabled.getBackground(), "dark popup hover");
		assertColor(0x20262E, disabled.getBackground(), "dark popup disabled");
		assertColor(0x8795A5, disabled.getForeground(), "dark popup disabled text");
		if (!(enabled.getUI() instanceof BasicMenuItemUI))
			throw new AssertionError("popup item does not use controlled BasicMenuItemUI");
		if (!(submenu.getUI() instanceof BasicMenuUI))
			throw new AssertionError("popup submenu does not use controlled BasicMenuUI");
	}

	private static void assertDetachedFileChooserTransition() {
		final JFileChooser chooser = new JFileChooser();
		ThemeManager.apply(ThemeManager.DARK);
		ThemeManager.refreshDetached(chooser);
		ThemeManager.apply(ThemeManager.LIGHT);
		ThemeManager.refreshDetached(chooser);

		final JList<?> fileList = findList(chooser);
		if (fileList == null)
			throw new AssertionError("detached file chooser list not found");

		final Component rendered = renderFileListCell(fileList);
		assertColor(0x17212B, rendered.getForeground(), "detached file chooser renderer after dark to light");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Component renderFileListCell(final JList<?> list) {
		final javax.swing.ListCellRenderer renderer = list.getCellRenderer();
		return renderer.getListCellRendererComponent((JList) list, new File("visible-folder-name"), 0, false, false);
	}

	private static JList<?> findList(final Container container) {
		for (final Component child : container.getComponents()) {
			if (child instanceof JList<?>)
				return (JList<?>) child;
			if (child instanceof Container) {
				final JList<?> nested = findList((Container) child);
				if (nested != null)
					return nested;
			}
		}
		return null;
	}

	private static void assertColor(final int expected, final Color actual, final String message) {
		if (actual == null || (actual.getRGB() & 0xFFFFFF) != expected)
			throw new AssertionError(message + ": expected #" + Integer.toHexString(expected) + ", actual=" + actual);
	}

	private static void assertEquals(final String expected, final String actual, final String message) {
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
	}
}
