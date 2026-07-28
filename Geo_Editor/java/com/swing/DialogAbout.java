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
import com.jogl.InputBindings;
import com.swing.defaults.DefaultButton;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;

/**
 * Displays current application information and keyboard controls.
 *
 * @author Maneco2
 * @version 1.2
 */
@SuppressWarnings("serial")
public final class DialogAbout extends JDialog {
	private static final String[][] FIXED_CONTROLS = {
			{ "Look around", "Hold right mouse button and move" },
			{ "Select one cell", "Left-click a cell" },
			{ "Select multiple cells", "Hold Shift while selecting" },
			{ "Select a whole block", "Hold Alt while selecting" },
			{ "Unselect a cell", "Shift + left-click the selected cell" },
			{ "Change selection radius", "Mouse wheel or middle mouse button" },
			{ "Change radius faster", "Space + mouse wheel" },
			{ "Change selected height", "Ctrl + mouse wheel" },
			{ "Change height faster", "Ctrl + Space + mouse wheel" },
			{ "Resize minimap", "Drag its lower-right corner" }
	};

	private final ControlsTableModel _controlsModel = new ControlsTableModel();

	public DialogAbout(final Frame owner) {
		super(owner, "About " + Config.APP_NAME, true);
		setDefaultCloseOperation(HIDE_ON_CLOSE);
		setMinimumSize(new Dimension(680, 480));
		setLayout(new BorderLayout(0, 8));

		add(createHeader(), BorderLayout.NORTH);

		final JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("About", createAboutPanel());
		tabs.addTab("Details", createDetailsPanel());
		tabs.addTab("Controls", createControlsPanel());
		add(tabs, BorderLayout.CENTER);
		add(createButtonPanel(), BorderLayout.SOUTH);

		getRootPane().registerKeyboardAction(new AbstractAction() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				setVisible(false);
			}
		}, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

		pack();
		setLocationRelativeTo(owner);
	}

	private static final JPanel createHeader() {
		final JPanel panel = new JPanel(new GridLayout(2, 1, 0, 2));
		panel.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));

		final JLabel title = new JLabel(Config.APP_NAME, SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
		panel.add(title);

		final JLabel subtitle = new JLabel("Lineage II Geodata Editor - Version " + Config.APP_VERSION,
				SwingConstants.CENTER);
		subtitle.setFont(subtitle.getFont().deriveFont(13f));
		panel.add(subtitle);
		return panel;
	}

	private static final JPanel createAboutPanel() {
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
		panel.add(createText("<html><b>Visualization and editing</b><br><br>"
				+ "Inspect and modify L2J geodata with JOGL rendering, incremental Full Map streaming "
				+ "and focused editing of the active region.</html>"));
		panel.add(Box.createVerticalStrut(22));
		panel.add(createText("<html><b>Geodata compatibility</b><br><br>"
				+ "Supports FLAT, COMPLEX and MULTILAYER blocks while preserving the L2J binary format.</html>"));
		panel.add(Box.createVerticalStrut(22));
		panel.add(createText("<html><b>Project</b><br><br>"
				+ "Source code and documentation: github.com/maneco2/L2GeoEditor</html>"));
		panel.add(Box.createVerticalStrut(22));
		panel.add(createText("<html><b>Credits</b><br><br>"
				+ "Original author: Forsaiken (Patrick). Maintained and modernized by Maneco2.</html>"));
		panel.add(Box.createVerticalStrut(22));
		panel.add(createText("<html><b>License</b><br><br>"
				+ "GNU General Public License v3 or any later version. Distributed without warranty.</html>"));
		panel.add(Box.createVerticalGlue());
		return panel;
	}

	private static final JLabel createText(final String text) {
		final JLabel label = new JLabel(text);
		label.setVerticalAlignment(SwingConstants.TOP);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static final JScrollPane createDetailsPanel() {
		final String[][] details = {
				{ "File description", "Lineage II Geodata Editor" },
				{ "Type", "Java desktop application" },
				{ "File version", Config.APP_VERSION },
				{ "Product name", Config.APP_NAME },
				{ "Product version", Config.APP_VERSION },
				{ "Original author", "Forsaiken (Patrick)" },
				{ "Maintainer", "Maneco2" },
				{ "Java runtime", System.getProperty("java.version", "Unknown") },
				{ "Renderer", "JOGL / OpenGL" },
				{ "License", "GNU GPL v3 or later" },
				{ "Original filename", "GeoEditor.exe" }
		};
		final JTable table = new JTable(new ReadOnlyTableModel(details, new String[] { "Property", "Value" }));
		table.setRowHeight(25);
		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);
		table.getColumnModel().getColumn(0).setPreferredWidth(190);
		table.getColumnModel().getColumn(1).setPreferredWidth(430);
		final JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		return scrollPane;
	}

	private JPanel createControlsPanel() {
		final JTable table = new JTable(_controlsModel);
		table.setRowHeight(24);
		table.setFillsViewportHeight(true);
		table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		table.getTableHeader().setReorderingAllowed(false);
		table.getColumnModel().getColumn(0).setPreferredWidth(210);
		table.getColumnModel().getColumn(1).setPreferredWidth(320);
		table.getColumnModel().getColumn(2).setPreferredWidth(90);
		table.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(final java.awt.event.MouseEvent event) {
				if (event.getClickCount() == 2)
					editSelectedBinding(table);
			}
		});

		final JScrollPane scrollPane = new JScrollPane(table);
		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		final DefaultButton edit = new DefaultButton("Edit selected");
		edit.addActionListener(event -> editSelectedBinding(table));
		final DefaultButton reset = new DefaultButton("Restore defaults");
		reset.addActionListener(event -> {
			InputBindings.resetAll();
			Config.save();
			_controlsModel.fireTableDataChanged();
		});
		buttons.add(edit);
		buttons.add(reset);

		final JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
		panel.add(scrollPane, BorderLayout.CENTER);
		panel.add(buttons, BorderLayout.SOUTH);
		return panel;
	}

	private void editSelectedBinding(final JTable table) {
		final InputBindings.Action action = _controlsModel.getAction(table.getSelectedRow());
		if (action == null) {
			JOptionPane.showMessageDialog(this, "Select an editable keyboard control.", "Controls",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		final JDialog capture = new JDialog(this, "Edit " + action.getLabel(), true);
		final JLabel message = new JLabel("Press the new key combination. Esc cancels.", SwingConstants.CENTER);
		message.setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));
		capture.add(message);
		capture.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(final KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
					capture.dispose();
					return;
				}
				final InputBindings.Action conflict = InputBindings.findConflict(action, event.getKeyCode(),
						event.getModifiersEx());
				if (conflict != null) {
					message.setText("Already used by: " + conflict.getLabel());
					return;
				}
				if (!InputBindings.set(action, event.getKeyCode(), event.getModifiersEx())) {
					message.setText("Press a non-modifier key.");
					return;
				}
				Config.save();
				_controlsModel.fireTableDataChanged();
				capture.dispose();
			}
		});
		capture.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		capture.pack();
		capture.setLocationRelativeTo(this);
		capture.setVisible(true);
	}

	private final JPanel createButtonPanel() {
		final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		final DefaultButton close = new DefaultButton("Close");
		close.addActionListener(e -> setVisible(false));
		getRootPane().setDefaultButton(close);
		panel.add(close);
		return panel;
	}

	private static class ReadOnlyTableModel extends javax.swing.table.DefaultTableModel {
		private ReadOnlyTableModel(final Object[][] data, final Object[] columns) {
			super(data, columns);
		}

		@Override
		public boolean isCellEditable(final int row, final int column) {
			return false;
		}
	}

	private static final class ControlsTableModel extends AbstractTableModel {
		private static final String[] COLUMNS = { "Action", "Control", "Type" };
		private final InputBindings.Action[] actions = InputBindings.Action.values();

		@Override
		public int getRowCount() {
			return FIXED_CONTROLS.length + actions.length;
		}

		@Override
		public int getColumnCount() {
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(final int column) {
			return COLUMNS[column];
		}

		@Override
		public Object getValueAt(final int row, final int column) {
			if (row < FIXED_CONTROLS.length)
				return column == 0 ? FIXED_CONTROLS[row][0] : column == 1 ? FIXED_CONTROLS[row][1] : "Fixed";
			final InputBindings.Action action = actions[row - FIXED_CONTROLS.length];
			return column == 0 ? action.getLabel() : column == 1 ? action.getDisplayText() : "Editable";
		}

		private InputBindings.Action getAction(final int row) {
			return row < FIXED_CONTROLS.length || row >= getRowCount() ? null
					: actions[row - FIXED_CONTROLS.length];
		}
	}
}
