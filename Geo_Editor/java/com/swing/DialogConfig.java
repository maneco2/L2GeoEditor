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
import com.jogl.GLCellRenderSelector;
import com.jogl.GLCellRenderer;
import com.jogl.GLDisplay;
import com.jogl.renderer.DLLoDRenderer;
import com.jogl.renderer.VBOGLSLRenderer;
import com.swing.defaults.DefaultButton;
import com.swing.defaults.DefaultLabel;
import com.swing.defaults.DefaultTextField;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.media.opengl.GL2;
import javax.media.opengl.GLContext;
import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.filechooser.FileFilter;

/**
 * Configuration dialog for visualization, geodata and performance options.
 * @author Maneco2
 * @version 1.0
 */
@SuppressWarnings("serial")
public final class DialogConfig extends JDialog implements MouseListener, ActionListener {
	private static final FileFilter DIR_FILTER = new FileFilter() {
		public final boolean accept(final File file) {
			return file.isDirectory();
		}

		@Override
		public final String getDescription() {
			return "DIRs";
		}
	};

	private static final Integer[] getNsweTexIds() {
		final ArrayList<Integer> ids = new ArrayList<Integer>();
		ids.add(1);
		for (int i = 2;; i++) {
			if (!new File("./data/textures/nswe_" + i + ".png").isFile())
				break;
			ids.add(i);
		}
		return ids.toArray(new Integer[ids.size()]);
	}

	private final JPanel _panelMisc;
	private final DefaultLabel _labelLookAndFeel;
	private final JComboBox<Object> _comboLookAndFeel;

	private final JPanel _panelEditor;
	private final JPanel _panelCheckButtons;
	private final JCheckBox _checkTerrainDefaultOn;
	private final JCheckBox _checkTransparency;
	private final JCheckBox _checkDrawOutline;
	private final JCheckBox _checkFullMap;
	private final JCheckBox _checkAuxiliaryTerrains;
	private final JCheckBox _checkShowGeodataGrid;
	private final JCheckBox _checkCircularGeodataGrid;
	private final JCheckBox _checkFullTexturedTerrain;

	private final DefaultLabel _labelCellRenderer;
	private final JComboBox<Object> _comboCellRenderer;

	private final DefaultLabel _labelDLLoDRange;
	private final JSlider _sliderDLLoDRange;

	private final DefaultLabel _labelGridRange;
	private final JSlider _sliderGridRange;
	private final DefaultLabel _labelNeighborTextures;
	private final JSlider _sliderNeighborTextures;

	private final DialogColorChooser _dialogColor;
	private final DefaultButton _buttonChooseColors;

	private final DefaultLabel _labelNSWETexture;
	private final JComboBox<Object> _comboNSWETexture;

	private final JPanel _panelGeodata;
	private final DefaultLabel _labelGeodataPath;
	private final DefaultTextField _fieldGeodataPath;

	private final JPanel _panelButtons;
	private final DefaultButton _buttonOk;
	private final DefaultButton _buttonCancel;

	private final JFileChooser _fileChooser;

	public DialogConfig(final JFrame frame) {
		super(frame, "Config", true);
		setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

		_fileChooser = new JFileChooser();
		_fileChooser.setFileFilter(DIR_FILTER);
		_fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
		_fileChooser.setDialogTitle("Choose the folder that contains the Geodata files...");
		_fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		_panelMisc = new JPanel();
		_panelMisc.setBorder(BorderFactory.createTitledBorder("Misc"));
		_labelLookAndFeel = new DefaultLabel("LookAndFeel:");
		_comboLookAndFeel = new JComboBox<Object>(Config.getInstalledLookAndFeels());

		_panelEditor = new JPanel();
		_panelEditor.setBorder(BorderFactory.createTitledBorder("Editor"));

		_panelCheckButtons = new JPanel();
		_checkTerrainDefaultOn = new JCheckBox("Show textured terrain");
		_checkTerrainDefaultOn.setToolTipText("Show or hide the textured terrain immediately (shortcut: T).");
		_checkTransparency = new JCheckBox("Transparency");
		_checkDrawOutline = new JCheckBox("Draw Outline");
		_checkFullMap = new JCheckBox("Full Map (stream neighboring regions)");
		_checkFullMap.setToolTipText("ON: load and cross neighboring regions. OFF: keep only the active region.");
		_checkAuxiliaryTerrains = new JCheckBox("Auxiliary terrains");
		_checkAuxiliaryTerrains.setToolTipText("Show the green terrain meshes of loaded neighboring regions.");
		_checkShowGeodataGrid = new JCheckBox("Show Geodata Grid");
		_checkShowGeodataGrid.setToolTipText("Show the editable geodata grid and enable mouse editing.");
		_checkCircularGeodataGrid = new JCheckBox("Circular Geodata Grid (selectable)");
		_checkCircularGeodataGrid.setToolTipText("Show and select geodata cells inside a circular area around the camera.");
		_checkFullTexturedTerrain = new JCheckBox("Show full textured terrain");
		_checkFullTexturedTerrain.setToolTipText("Show textured terrain for every loaded Full Map region.");

		_labelCellRenderer = new DefaultLabel("CellRenderer:");
		_comboCellRenderer = new JComboBox<Object>();
		// _comboCellRenderer.setSelectedItem(Config.CELL_RENDERER);

		_labelDLLoDRange = new DefaultLabel("DisplayList LoD - max detail range:");
		_sliderDLLoDRange = new JSlider();
		_sliderDLLoDRange.setMinimum(DLLoDRenderer.MIN_DISTANCE_SQ);
		_sliderDLLoDRange.setMaximum(DLLoDRenderer.MAX_DISTANCE_SQ);
		_sliderDLLoDRange.setMinorTickSpacing(256);
		_sliderDLLoDRange.setMajorTickSpacing(1024);
		_sliderDLLoDRange.setPaintTicks(true);
		Hashtable<Integer, DefaultLabel> rangeTable = new Hashtable<Integer, DefaultLabel>();
		for (int i = DLLoDRenderer.MIN_DISTANCE_SQ; i <= DLLoDRenderer.MAX_DISTANCE_SQ; i += 2048) {
			rangeTable.put(i, new DefaultLabel(String.valueOf(i)));
		}
		_sliderDLLoDRange.setLabelTable(rangeTable);
		_sliderDLLoDRange.setPaintLabels(true);

		_labelGridRange = new DefaultLabel("Visible grid range:");
		_sliderGridRange = new JSlider();
		_sliderGridRange.setMinimum(GLCellRenderSelector.MIN_VIS_GRID_RANGE);
		_sliderGridRange.setMaximum(GLCellRenderSelector.MAX_VIS_GRID_RANGE);
		_sliderGridRange.setMinorTickSpacing(2);
		_sliderGridRange.setMajorTickSpacing(8);
		_sliderGridRange.setPaintTicks(true);
		rangeTable = new Hashtable<Integer, DefaultLabel>();
		for (int i = 8; i <= 96; i += 8) {
			rangeTable.put(i, new DefaultLabel(String.valueOf(i)));
		}
		_sliderGridRange.setLabelTable(rangeTable);
		_sliderGridRange.setPaintLabels(true);
		_labelNeighborTextures = new DefaultLabel("Max textured neighbors:");
		_sliderNeighborTextures = new JSlider(1, 8, Config.MAX_NEIGHBOR_TEXTURES);
		_sliderNeighborTextures.setMajorTickSpacing(1);
		_sliderNeighborTextures.setPaintTicks(true);
		_sliderNeighborTextures.setPaintLabels(true);

		_dialogColor = new DialogColorChooser(this);
		_buttonChooseColors = new DefaultButton("Choose cell/block colors");
		_buttonChooseColors.addActionListener(this);

		_labelNSWETexture = new DefaultLabel("NSWE Texture:");
		_comboNSWETexture = new JComboBox<Object>(getNsweTexIds());

		_panelGeodata = new JPanel();
		_panelGeodata.setBorder(BorderFactory.createTitledBorder("Geodata"));
		_labelGeodataPath = new DefaultLabel("Path to Geodata:");
		_fieldGeodataPath = new DefaultTextField(16);
		_fieldGeodataPath.setEditable(false);
		_fieldGeodataPath.addMouseListener(this);

		_panelButtons = new JPanel();
		_buttonOk = new DefaultButton("Ok");
		_buttonOk.addActionListener(this);
		_buttonCancel = new DefaultButton("Cancel");
		_buttonCancel.addActionListener(this);
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "cancel-config");
		getRootPane().getActionMap().put("cancel-config", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public final void actionPerformed(final ActionEvent event) {
				_buttonCancel.doClick();
			}
		});

		initLayout();
	}

	private final void initLayout() {
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 6, 4, 6);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.BOTH;

		_panelMisc.setLayout(new GridBagLayout());
		_panelMisc.setBorder(BorderFactory.createTitledBorder("Visualization"));

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0;
		_panelMisc.add(_labelLookAndFeel, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		_panelMisc.add(_comboLookAndFeel, gbc);

		_panelCheckButtons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		_panelCheckButtons.setLayout(new GridLayout(2, 2, 16, 6));
		_panelCheckButtons.add(_checkTerrainDefaultOn);
		_panelCheckButtons.add(_checkTransparency);
		_panelCheckButtons.add(_checkDrawOutline);
		_panelCheckButtons.add(_checkFullTexturedTerrain);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 2;
		gbc.weightx = 1;
		_panelMisc.add(_panelCheckButtons, gbc);

		_panelEditor.setLayout(new GridBagLayout());
		_panelEditor.setBorder(BorderFactory.createTitledBorder("Performance"));
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		_panelEditor.add(_checkFullMap, gbc);
		gbc.gridx = 1;
		_panelEditor.add(_checkAuxiliaryTerrains, gbc);
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 2;
		_panelEditor.add(_labelNeighborTextures, gbc);
		gbc.gridy = 2;
		_panelEditor.add(_sliderNeighborTextures, gbc);

		_panelGeodata.setLayout(new GridBagLayout());

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.weightx = 1;
		_panelGeodata.add(_checkShowGeodataGrid, gbc);

		gbc.gridx = 1;
		_panelGeodata.add(_checkCircularGeodataGrid, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 2;
		_panelGeodata.add(_labelGridRange, gbc);
		gbc.gridy = 2;
		_panelGeodata.add(_sliderGridRange, gbc);
		gbc.gridy = 3;
		_panelGeodata.add(_buttonChooseColors, gbc);

		gbc.gridy = 4;
		gbc.gridwidth = 1;
		gbc.weightx = 0;
		_panelGeodata.add(_labelNSWETexture, gbc);
		gbc.gridx = 1;
		gbc.weightx = 1;
		_panelGeodata.add(_comboNSWETexture, gbc);

		gbc.gridx = 0;
		gbc.gridy = 5;
		gbc.weightx = 0;
		_panelGeodata.add(_labelGeodataPath, gbc);
		gbc.gridx = 1;
		gbc.weightx = 1;
		_panelGeodata.add(_fieldGeodataPath, gbc);

		_panelButtons.setLayout(new GridLayout(1, 2, 8, 0));
		_panelButtons.add(_buttonOk);
		_panelButtons.add(_buttonCancel);

		setLayout(new GridBagLayout());

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.ipadx = 0;
		gbc.ipady = 0;
		add(_panelMisc, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.ipadx = 0;
		gbc.ipady = 0;
		add(_panelEditor, gbc);

		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.ipadx = 0;
		gbc.ipady = 0;
		add(_panelGeodata, gbc);

		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.ipadx = 0;
		gbc.ipady = 0;
		add(_panelButtons, gbc);

		pack();
		setMinimumSize(new Dimension(760, 600));
		if (getWidth() < 760 || getHeight() < 600)
			setSize(Math.max(getWidth(), 760), Math.max(getHeight(), 600));
		setResizable(true);
		setLocationRelativeTo(getOwner());
	}

	public final void checkAvailableRenderers() {
		_comboCellRenderer.removeAllItems();
		_comboCellRenderer.addItem(VBOGLSLRenderer.NAME);
		_comboCellRenderer.setSelectedItem(VBOGLSLRenderer.NAME);
		_comboCellRenderer.setVisible(false);
		_labelCellRenderer.setVisible(false);
		_labelDLLoDRange.setVisible(false);
		_sliderDLLoDRange.setVisible(false);
	}

	public final DialogColorChooser getColorChooser() {
		return _dialogColor;
	}

	private final void checkDLLoDSliderEnabled() {
		final boolean enabled = _comboCellRenderer.getSelectedItem() == DLLoDRenderer.NAME;
		_labelDLLoDRange.setEnabled(enabled);
		_sliderDLLoDRange.setEnabled(enabled);
	}

	@Override
	public final void setVisible(final boolean visible) {
		if (!isVisible() && visible) {
			setLocationRelativeTo(super.getOwner());

			_checkTerrainDefaultOn.setSelected(Config.TERRAIN_DEFAULT_ON);
			_checkTransparency.setSelected(Config.USE_TRANSPARENCY);
			_checkDrawOutline.setSelected(Config.DRAW_OUTLINE);
			_checkFullMap.setSelected(Config.FULL_MAP);
			_checkAuxiliaryTerrains.setSelected(Config.AUXILIARY_TERRAINS);
			_checkShowGeodataGrid.setSelected(Config.SHOW_GEODATA_GRID);
			_checkCircularGeodataGrid.setSelected(Config.CIRCULAR_GEODATA_GRID);
			_checkFullTexturedTerrain.setSelected(Config.FULL_TEXTURED_TERRAIN);
			_sliderGridRange.setValue(Config.VIS_GRID_RANGE);
			_sliderNeighborTextures.setValue(Config.MAX_NEIGHBOR_TEXTURES);
			_comboLookAndFeel
					.setSelectedItem(Config.getLookAndFeel(Config.LOOK_AND_FEEL, Config.getActiveLookAndFeel()));
			_comboCellRenderer.setSelectedItem(Config.CELL_RENDERER);
			_sliderDLLoDRange.setValue(Config.DLLoD_RANGE);
			_fieldGeodataPath.setText(Config.PATH_TO_GEO_FILES);
			_comboNSWETexture.setSelectedItem(Config.NSWE_TEXTURE_ID);
			checkDLLoDSliderEnabled();
		}

		super.setVisible(visible);

		if (!visible)
			GLDisplay.getInstance().requestFocus();
	}

	@Override
	public final void mouseClicked(final MouseEvent e) {

	}

	@Override
	public final void mousePressed(final MouseEvent e) {

	}

	@Override
	public final void mouseReleased(final MouseEvent e) {
		if (e.getSource() == _fieldGeodataPath) {
			if (_fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				final File file = _fileChooser.getSelectedFile();
				if (file != null && DIR_FILTER.accept(file)) {
					final String path = file.getAbsolutePath();
					_fieldGeodataPath.setText(path);
					Config.PATH_TO_GEO_FILES = path;
					Config.save();
				}
			}
		}
	}

	@Override
	public final void mouseEntered(final MouseEvent e) {

	}

	@Override
	public void mouseExited(final MouseEvent e) {

	}

	@Override
	public final void actionPerformed(final ActionEvent e) {
		if (e.getSource() == this._buttonChooseColors) {
			_dialogColor.setVisible(true);
		} else if (e.getSource() == _buttonOk) {
			final boolean fullMapChanged = Config.FULL_MAP != _checkFullMap.isSelected();
			Config.TERRAIN_DEFAULT_ON = _checkTerrainDefaultOn.isSelected();
			Config.USE_TRANSPARENCY = _checkTransparency.isSelected();
			Config.DRAW_OUTLINE = _checkDrawOutline.isSelected();
			Config.FULL_MAP = _checkFullMap.isSelected();
			Config.AUXILIARY_TERRAINS = _checkAuxiliaryTerrains.isSelected();
			Config.SHOW_GEODATA_GRID = _checkShowGeodataGrid.isSelected();
			Config.CIRCULAR_GEODATA_GRID = _checkCircularGeodataGrid.isSelected();
			Config.FULL_TEXTURED_TERRAIN = _checkFullTexturedTerrain.isSelected();
			Config.VIS_GRID_RANGE = _sliderGridRange.getValue();
			Config.MAX_NEIGHBOR_TEXTURES = _sliderNeighborTextures.getValue();
			Config.LOOK_AND_FEEL = ((LookAndFeelInfo) _comboLookAndFeel.getSelectedItem()).getClassName();
			Config.CELL_RENDERER = VBOGLSLRenderer.NAME;
			Config.DLLoD_RANGE = _sliderDLLoDRange.getValue();
			Config.NSWE_TEXTURE_ID = (Integer) _comboNSWETexture.getSelectedItem();
			FrameMain.getInstance().onNsweTexIdUpdated();
			if (fullMapChanged)
				FrameMain.getInstance().onFullMapModeChanged();

			Config.save();
			setVisible(false);
		} else if (e.getSource() == _buttonCancel) {
			setVisible(false);
		}
	}
}
