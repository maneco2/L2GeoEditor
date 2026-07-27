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
import java.awt.Window;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.ListCellRenderer;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicMenuUI;
import javax.swing.text.JTextComponent;

/**
 * Applies the fixed light and dark GeoEditor themes.
 *
 * @author Maneco2
 * @version 1.8
 */
public final class ThemeManager {
	public static final String LIGHT = "LIGHT";
	public static final String DARK = "DARK";
	private static final String NIMBUS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";

	private static ColorUIResource _background;
	private static ColorUIResource _surface;
	private static ColorUIResource _border;
	private static ColorUIResource _foreground;
	private static ColorUIResource _muted;
	private static ColorUIResource _accent;
	private static ColorUIResource _selectionForeground;
	private static ColorUIResource _hover;
	private static ColorUIResource _pressed;
	private static ColorUIResource _disabledSurface;
	private static ColorUIResource _menuActiveForeground;
	private static ColorUIResource _menuDisabledForeground;

	private ThemeManager() {
	}

	public static void apply(final String theme) {
		final boolean dark = DARK.equalsIgnoreCase(theme);
		Config.UI_THEME = dark ? DARK : LIGHT;

		_background = color(dark ? 0x171B21 : 0xF4F6F8);
		_surface = color(dark ? 0x242B34 : 0xFFFFFF);
		_border = color(dark ? 0x46515E : 0xC7D0DA);
		_foreground = color(dark ? 0xFFFFFF : 0x17212B);
		_muted = color(dark ? 0xC7D0DA : 0x66727F);
		_accent = color(0x00AEEF);
		_selectionForeground = color(0x0B1820);
		_hover = color(dark ? 0x344454 : 0xE5F6FC);
		_pressed = color(dark ? 0x007CA8 : 0xB7E8F7);
		_disabledSurface = color(dark ? 0x20262E : 0xE9EDF1);
		_menuActiveForeground = color(dark ? 0xFFE08A : 0x174A6E);
		_menuDisabledForeground = color(dark ? 0x8795A5 : 0x8A96A3);

		// Nimbus derives many state colors while it is initialized. Install the
		// palette both before and after replacing the LookAndFeel so transitions
		// never inherit values from the previous theme.
		installDefaults();
		try {
			UIManager.setLookAndFeel(NIMBUS);
		} catch (final Exception exception) {
			throw new IllegalStateException("Unable to initialize the GeoEditor theme.", exception);
		}
		installDefaults();
	}

	public static void toggle() {
		apply(isDark() ? LIGHT : DARK);
		for (final Window window : Window.getWindows()) {
			SwingUtilities.updateComponentTreeUI(window);
			refresh(window);
			window.invalidate();
			window.validate();
			window.repaint();
		}
		Config.save();
	}

	public static boolean isDark() {
		return DARK.equals(Config.UI_THEME);
	}

	static void refresh(final Component component) {
		refreshComponent(component);
	}

	static void refreshDetached(final Component component) {
		SwingUtilities.updateComponentTreeUI(component);
		refresh(component);
		component.invalidate();
		if (component instanceof Container)
			((Container) component).validate();
		component.repaint();
	}

	public static void refreshButtonState(final AbstractButton button) {
		if (!button.isEnabled()) {
			button.setBackground(_disabledSurface);
			button.setForeground(_muted);
		} else if (button.getModel().isPressed()) {
			button.setBackground(_pressed);
			button.setForeground(_foreground);
		} else if (button.getModel().isRollover()) {
			button.setBackground(_hover);
			button.setForeground(_foreground);
		} else {
			button.setBackground(_surface);
			button.setForeground(_foreground);
		}
	}

	public static void preparePopupMenu(final JPopupMenu popup) {
		SwingUtilities.updateComponentTreeUI(popup);
		popup.setBackground(_surface);
		popup.setBorder(BorderFactory.createLineBorder(_border));
		refreshPopupChildren(popup);
	}

	private static void refreshPopupChildren(final Container container) {
		for (final Component child : container.getComponents()) {
			if (child instanceof JMenuItem) {
				final JMenuItem item = (JMenuItem) child;
				if (item instanceof JMenu)
					((JMenu) item).setUI(new BasicMenuUI());
				else
					item.setUI(new BasicMenuItemUI());
				item.setOpaque(true);
				item.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 18));
				item.getModel().addChangeListener(event -> refreshMenuItemState(item));
				refreshMenuItemState(item);
				if (item instanceof JMenu) {
					final JPopupMenu submenu = ((JMenu) item).getPopupMenu();
					submenu.setBackground(_surface);
					submenu.setBorder(BorderFactory.createLineBorder(_border));
					refreshPopupChildren(submenu);
				}
			} else {
				child.setBackground(_surface);
				child.setForeground(_foreground);
				if (child instanceof Container)
					refreshPopupChildren((Container) child);
			}
		}
	}

	private static void refreshMenuItemState(final JMenuItem item) {
		if (!item.isEnabled()) {
			item.setBackground(_disabledSurface);
			item.setForeground(_menuDisabledForeground);
		} else if (item.getModel().isArmed() || item.getModel().isSelected()) {
			item.setBackground(_accent);
			item.setForeground(_selectionForeground);
		} else {
			item.setBackground(_surface);
			item.setForeground(_menuActiveForeground);
		}
	}

	private static void installDefaults() {
		UIManager.put("control", _surface);
		UIManager.put("controlText", _foreground);
		UIManager.put("desktop", _background);
		UIManager.put("info", _surface);
		UIManager.put("text", _foreground);
		UIManager.put("textText", _foreground);
		UIManager.put("textForeground", _foreground);
		UIManager.put("textInactiveText", _muted);
		UIManager.put("menuText", _foreground);
		UIManager.put("window", _background);
		UIManager.put("windowText", _foreground);

		UIManager.put("nimbusBase", _border);
		UIManager.put("nimbusBlueGrey", _border);
		UIManager.put("nimbusFocus", _accent);
		UIManager.put("nimbusLightBackground", _surface);
		UIManager.put("nimbusSelectionBackground", _accent);
		UIManager.put("nimbusSelectedText", _selectionForeground);
		UIManager.put("nimbusDisabledText", _muted);

		putColors("Button", _surface, _foreground);
		putColors("CheckBox", _background, _foreground);
		putColors("ComboBox", _surface, _foreground);
		putColors("EditorPane", _surface, _foreground);
		putColors("FormattedTextField", _surface, _foreground);
		putColors("Label", _background, _foreground);
		putColors("List", _surface, _foreground);
		putColors("Menu", _surface, _foreground);
		putColors("MenuItem", _surface, _foreground);
		putColors("OptionPane", _background, _foreground);
		putColors("Panel", _background, _foreground);
		putColors("PasswordField", _surface, _foreground);
		putColors("RadioButton", _background, _foreground);
		putColors("ScrollPane", _background, _foreground);
		putColors("Slider", _background, _foreground);
		putColors("Spinner", _surface, _foreground);
		putColors("TabbedPane", _background, _foreground);
		putColors("Table", _surface, _foreground);
		putColors("TableHeader", _background, _foreground);
		putColors("TextArea", _surface, _foreground);
		putColors("TextField", _surface, _foreground);
		putColors("TextPane", _surface, _foreground);
		putColors("ToggleButton", _surface, _foreground);
		putColors("ToolTip", _surface, _foreground);
		putColors("Tree", _surface, _foreground);
		putColors("Viewport", _surface, _foreground);

		UIManager.put("Dialog.background", _background);
		UIManager.put("FileChooser.background", _background);
		UIManager.put("FileChooser.foreground", _foreground);
		UIManager.put("PopupMenu.background", _surface);
		UIManager.put("PopupMenu.foreground", _foreground);
		UIManager.put("MenuItem.selectionBackground", _accent);
		UIManager.put("MenuItem.selectionForeground", _selectionForeground);
		UIManager.put("MenuItem.disabledForeground", _menuDisabledForeground);
		UIManager.put("MenuItem.acceleratorForeground", _menuDisabledForeground);
		UIManager.put("MenuItem.acceleratorSelectionForeground", _selectionForeground);
		UIManager.put("Menu.selectionBackground", _accent);
		UIManager.put("Menu.selectionForeground", _selectionForeground);
		UIManager.put("Menu.disabledForeground", _menuDisabledForeground);
		UIManager.put("ComboBox.selectionBackground", _accent);
		UIManager.put("ComboBox.selectionForeground", _selectionForeground);
		UIManager.put("ComboBox.disabledBackground", _disabledSurface);
		UIManager.put("ComboBox.disabledForeground", _muted);
		UIManager.put("List.selectionBackground", _accent);
		UIManager.put("List.selectionForeground", _selectionForeground);
		UIManager.put("TabbedPane.selected", _surface);
		UIManager.put("TabbedPane.selectedForeground", _foreground);
		UIManager.put("TabbedPane.contentAreaColor", _surface);
		UIManager.put("Table.gridColor", _border);
		UIManager.put("Table.selectionBackground", _accent);
		UIManager.put("Table.selectionForeground", _selectionForeground);
		UIManager.put("TextField.caretForeground", _foreground);
		UIManager.put("TextField.disabledBackground", _disabledSurface);
		UIManager.put("FormattedTextField.disabledBackground", _disabledSurface);
		UIManager.put("Button.disabledBackground", _disabledSurface);
		UIManager.put("Slider.foreground", _accent);
		UIManager.put("Slider.disabledForeground", _muted);
		UIManager.put("GeoEditor.buttonHover", _hover);
		UIManager.put("GeoEditor.buttonPressed", _pressed);
		UIManager.put("TitledBorder.titleColor", _foreground);

		for (final String prefix : new String[] { "Button", "CheckBox", "ComboBox", "Label", "RadioButton",
				"Slider", "TabbedPane", "Table", "TextField", "ToggleButton", "Tree" }) {
			UIManager.put(prefix + ".disabledForeground", _muted);
			UIManager.put(prefix + "[Disabled].textForeground", _muted);
		}
		UIManager.put("Slider.tickColor", _muted);
	}

	private static void putColors(final String prefix, final Color background, final Color foreground) {
		UIManager.put(prefix + ".background", background);
		UIManager.put(prefix + ".foreground", foreground);
	}

	private static void refreshComponent(final Component component) {
		if (component instanceof JComponent)
			component.setForeground(component.isEnabled() ? _foreground : _muted);

		if (component instanceof JTextComponent) {
			component.setBackground(component.isEnabled() ? _surface : _disabledSurface);
		} else if (component instanceof JTable || component instanceof JList<?>
				|| component instanceof JViewport) {
			component.setBackground(_surface);
		} else if (component instanceof JPanel || component instanceof JScrollPane || component instanceof Window) {
			component.setBackground(_background);
		} else if (component instanceof AbstractButton) {
			refreshButtonState((AbstractButton) component);
		}

		if (component instanceof JComponent)
			refreshBorder(((JComponent) component).getBorder());
		if (component instanceof JList<?>)
			refreshListRenderer((JList<?>) component);
		if (component instanceof Container) {
			for (final Component child : ((Container) component).getComponents())
				refreshComponent(child);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void refreshListRenderer(final JList<?> list) {
		final ListCellRenderer renderer = list.getCellRenderer();
		if (renderer == null)
			return;
		if (renderer instanceof ThemeListCellRenderer) {
			((ThemeListCellRenderer) renderer).refreshColors();
		} else {
			((JList) list).setCellRenderer(new ThemeListCellRenderer(renderer));
		}
	}

	private static final class ThemeListCellRenderer implements ListCellRenderer<Object> {
		private final ListCellRenderer<Object> _delegate;

		@SuppressWarnings("unchecked")
		private ThemeListCellRenderer(final ListCellRenderer<?> delegate) {
			_delegate = (ListCellRenderer<Object>) delegate;
		}

		private void refreshColors() {
			// Colors are read from the current palette on every render.
		}

		@Override
		public Component getListCellRendererComponent(final JList<? extends Object> list, final Object value,
				final int index, final boolean isSelected, final boolean cellHasFocus) {
			final Component rendered = _delegate.getListCellRendererComponent(list, value, index, isSelected,
					cellHasFocus);
			applyRendererColors(rendered, isSelected);
			return rendered;
		}
	}

	private static void applyRendererColors(final Component component, final boolean selected) {
		component.setForeground(selected ? _selectionForeground : _foreground);
		component.setBackground(selected ? _accent : _surface);
		if (component instanceof JLabel)
			((JLabel) component).setOpaque(selected);
		if (component instanceof Container) {
			for (final Component child : ((Container) component).getComponents())
				applyRendererColors(child, selected);
		}
	}

	private static void refreshBorder(final Border border) {
		if (border instanceof TitledBorder) {
			((TitledBorder) border).setTitleColor(_foreground);
		} else if (border instanceof CompoundBorder) {
			refreshBorder(((CompoundBorder) border).getOutsideBorder());
			refreshBorder(((CompoundBorder) border).getInsideBorder());
		}
	}

	private static ColorUIResource color(final int rgb) {
		return new ColorUIResource(new Color(rgb));
	}
}
