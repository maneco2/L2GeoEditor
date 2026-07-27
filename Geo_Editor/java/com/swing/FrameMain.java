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
import com.geo.GeoCell;
import com.geo.GeoEngine;
import com.geo.GeoRegion;
import com.geo.RegionLoadManager;
import com.jogl.GLDisplay;
import com.jogl.GLTerrain;
import com.swing.defaults.DefaultButton;
import com.swing.defaults.DefaultLabel;
import com.swing.mapselector.MapSelectorDialog;
import com.util.Util;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

/**
 * L2J source.
 * 
 * @author Forsaiken aka Patrick
 */
@SuppressWarnings("serial")
public final class FrameMain extends JFrame implements ActionListener {
	private static final float CAMERA_HEIGHT_OFFSET = 500f / 16f;
	private static final FileFilter GEO_FILTER = new FileFilter() {
		@Override
		public final boolean accept(final File file) {
			return file.isDirectory() || GeoEngine.GEO_FILE_FILTER.accept(file);
		}

		@Override
		public final String getDescription() {
			return "GeoData *.l2j/*.dat";
		}
	};

	private static FrameMain _instance;

	public static final void init() {
		_instance = new FrameMain();
	}

	public static final FrameMain getInstance() {
		return _instance;
	}

	private static final ImageIcon loadToolbarIcon(final String fileName) {
		final BufferedImage image = Util.loadImage("./data/icon/" + fileName);
		return new ImageIcon(Util.scaleImage(image, 32, 32, 2));
	}

	private final JPanel _panelRight;
	private final PanelNswe _panelNswe;
	private final PanelCellInfo _panelCellInfo;
	private final PanelBlockConvert _panelBlockConvert;
	private final PanelLayers _panelLayers;
	private final PanelDirectNswe _panelDirectNswe;

	private final MapSelectorDialog _mapSelector;
	private final DialogConfig _dialogConfig;

	private final JMenuBar _menuBar;
	private final DefaultButton _buttonConfig;
	private final DefaultButton _buttonOpen;
	private final DefaultButton _buttonJumpTo;
	private final DefaultButton _buttonSave;
	private final DefaultButton _buttonHelp;
	private final DefaultLabel _labelLogoL2j;

	private final JFileChooser _fileChooser;

	private GeoCell _selectedCell;
	private boolean _waitForUpdate;
	private boolean _regionTransitionPending;
	private int _transitionCandidateX = Integer.MIN_VALUE;
	private int _transitionCandidateY = Integer.MIN_VALUE;
	private int _transitionCandidateFrames;
	private boolean _maximizedState;
	private final RegionLoadManager _regionLoadManager = new RegionLoadManager();

	private FrameMain() {
		super(Config.APP_NAME + " [Beta " + Config.APP_VERSION + "]");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public final void windowClosing(final WindowEvent e) {
				saveWindowState();
				final GeoRegion region = GeoEngine.getInstance().getActiveRegion();
				if (region != null && !region.allDataEqual()) {
					final int choice = JOptionPane.showConfirmDialog(FrameMain.getInstance(),
							"Region " + region.getName() + " was modified.\nWould u like to save it before closing?",
							"Save and exit", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

					if (choice == JOptionPane.YES_OPTION) {
						new DialogSave(FrameMain.getInstance(), region, new Runnable() {
							@Override
							public final void run() {
								exitApplication();
							}
						}).setVisible(true);
						return;
					}
					if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION)
						return;
				}
				exitApplication();
			}
		});
		addWindowStateListener((WindowStateListener) e -> {
			final boolean maximized = (e.getNewState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
			if (_maximizedState != maximized) {
				_maximizedState = maximized;
				saveWindowState();
			}
		});
		setIconImage(Util.loadImage("./data/icon/l2.jpg"));

		_panelRight = new JPanel();
		_panelNswe = new PanelNswe();
		_panelCellInfo = new PanelCellInfo();
		_panelBlockConvert = new PanelBlockConvert();
		_panelLayers = new PanelLayers(this);
		_panelDirectNswe = new PanelDirectNswe();
		_mapSelector = new MapSelectorDialog(this, this::openRegionFromMapSelector);
		_dialogConfig = new DialogConfig(this);

		_menuBar = new JMenuBar();
		_buttonConfig = new DefaultButton(loadToolbarIcon("config.png"));
		_buttonConfig.setToolTipText("Settings");
		_buttonConfig.addActionListener(this);
		_buttonOpen = new DefaultButton(loadToolbarIcon("open.png"));
		_buttonOpen.setToolTipText("Open geodata file");
		_buttonOpen.addActionListener(this);
		_buttonJumpTo = new DefaultButton(loadToolbarIcon("search.png"));
		_buttonJumpTo.setToolTipText("Select map");
		_buttonJumpTo.addActionListener(this);
		_buttonSave = new DefaultButton(loadToolbarIcon("save.png"));
		_buttonSave.setToolTipText("Save active region");
		_buttonSave.addActionListener(this);
		_buttonHelp = new DefaultButton(loadToolbarIcon("help.png"));
		_buttonHelp.setToolTipText("About L2GeoEditor");
		_buttonHelp.addActionListener(this);
		BufferedImage img = Util.loadImage("./data/icon/l2jserverlogo.png");
		_labelLogoL2j = new DefaultLabel(
				new ImageIcon(Util.scaleImage(img, (int) (img.getWidth() * (32D / img.getHeight())), 32, 2)));

		_fileChooser = new JFileChooser();
		_fileChooser.setFileFilter(GEO_FILTER);
		_fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
		_fileChooser.setDialogTitle("Open an GeoData file...");
		_fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

		setJMenuBar(_menuBar);
		initLayout();

		setMinimumSize(new Dimension(1024, 768));
		if (Config.WINDOW_WIDTH >= 1024 && Config.WINDOW_HEIGHT >= 768) {
			setBounds(Config.WINDOW_X, Config.WINDOW_Y, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
			if (!isLocationOnScreen())
				setLocationRelativeTo(null);
		} else {
			setLocationRelativeTo(null);
		}
		if (Config.WINDOW_MAXIMIZED)
			setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
		_maximizedState = Config.WINDOW_MAXIMIZED;
	}

	private void saveWindowState() {
		Config.WINDOW_MAXIMIZED = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
		if (!Config.WINDOW_MAXIMIZED) {
			Config.WINDOW_X = getX();
			Config.WINDOW_Y = getY();
			Config.WINDOW_WIDTH = getWidth();
			Config.WINDOW_HEIGHT = getHeight();
		}
		Config.save();
	}

	private boolean isLocationOnScreen() {
		for (final java.awt.GraphicsDevice device : java.awt.GraphicsEnvironment
				.getLocalGraphicsEnvironment().getScreenDevices()) {
			final java.awt.GraphicsConfiguration configuration = device.getDefaultConfiguration();
			final java.awt.Rectangle bounds = configuration.getBounds();
			if (bounds.intersects(getBounds()))
				return true;
		}
		return false;
	}

	private void exitApplication() {
		if (GLDisplay.getInstance() != null)
			GLDisplay.getInstance().exportPerformanceReport();
		_regionLoadManager.close();
		DialogSave.shutdownSaveExecutor();
		GLTerrain.shutdownTextureLoader();
		System.exit(0);
	}

	private final void initLayout() {
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;

		_menuBar.setLayout(new GridBagLayout());

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		_menuBar.add(_buttonConfig, gbc);

		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		_menuBar.add(_buttonJumpTo, gbc);

		gbc.gridx = 2;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		_menuBar.add(_buttonOpen, gbc);

		gbc.gridx = 3;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.ipadx = 50;
		_menuBar.add(new DefaultLabel(), gbc);

		gbc.gridx = 4;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.ipadx = 0;
		_menuBar.add(_labelLogoL2j, gbc);

		gbc.gridx = 5;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.ipadx = 50;
		_menuBar.add(new DefaultLabel(), gbc);

		gbc.gridx = 6;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.ipadx = 0;
		_menuBar.add(_buttonSave, gbc);

		gbc.gridx = 7;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		_menuBar.add(_buttonHelp, gbc);

		gbc.gridx = 8;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;

		// gbc.insets = new Insets(2, 2, 2, 2);

		_panelRight.setLayout(new GridBagLayout());

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0;
		_panelRight.add(_panelCellInfo, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0;
		_panelRight.add(_panelBlockConvert, gbc);

		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 1;
		_panelRight.add(_panelLayers, gbc);

		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0;
		_panelRight.add(_panelDirectNswe, gbc);

		setLayout(new GridBagLayout());

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 1;
		add(GLDisplay.getInstance().getCanvas(), gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0;
		add(_panelNswe, gbc);

		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 2;
		gbc.weightx = 0;
		gbc.weighty = 1;
		add(_panelRight, gbc);
	}

	public final void checkAvailableRenderers() {
		_dialogConfig.checkAvailableRenderers();
	}

	public final void onNsweTexIdUpdated() {
		_panelDirectNswe.updateNsweTexId();
	}

	public final void setSelectedGeoCell(final GeoCell cell) {
		_selectedCell = cell;

		if (!_waitForUpdate) {
			_waitForUpdate = true;
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public final void run() {
					onSelectedCellUpdated();
				}
			});
		}
	}

	final void onSelectedCellUpdated() {
		_waitForUpdate = false;
		_panelNswe.onSelectedCellUpdated();
		_panelCellInfo.onSelectedCellUpdated();
		_panelBlockConvert.onSelectedCellUpdated();
		_panelLayers.onSelectedCellUpdated();
		_panelDirectNswe.onSelectedCellUpdated();
	}

	public final GeoCell getSelectedGeoCell() {
		return _selectedCell;
	}

	public final boolean isSelectedGeoCell(final GeoCell cell) {
		return _selectedCell == cell;
	}

	public final void openRegionFromGlobalMap(final int regionX, final int regionY) {
		final int geoX = GeoEngine.getGeoXY(regionX, GeoEngine.GEO_REGION_SIZE / 2);
		final int geoY = GeoEngine.getGeoXY(regionY, GeoEngine.GEO_REGION_SIZE / 2);
		openRegionFromMapSelector(regionX, regionY, geoX, geoY);
	}

	/** Requests loading of the region reached by camera movement. */
	public final void checkRegionTransition(final int geoX, final int geoY) {
		if (!Config.FULL_MAP) return;
		final GeoRegion current = GeoEngine.getInstance().getActiveRegion();
		if (current == null || _regionTransitionPending) return;
		final int regionX = GeoEngine.getRegionXY(geoX);
		final int regionY = GeoEngine.getRegionXY(geoY);
		if (current.getRegionX() == regionX && current.getRegionY() == regionY) {
			_transitionCandidateFrames = 0;
			return;
		}
		if (_transitionCandidateX != regionX || _transitionCandidateY != regionY) {
			_transitionCandidateX = regionX;
			_transitionCandidateY = regionY;
			_transitionCandidateFrames = 0;
		}
		if (++_transitionCandidateFrames < 3) return;
		if (!GeoEngine.getInstance().isRegionLoaded(regionX, regionY)) return;
		final RegionLoadManager.State targetState =
				_regionLoadManager.getState(new RegionLoadManager.RegionKey(regionX, regionY));
		// Never fall back to a synchronous disk load while the streaming worker is
		// still preparing the target region.
		if (targetState != RegionLoadManager.State.LOADED) return;
		_regionTransitionPending = true;
		_transitionCandidateFrames = 0;
		final float cameraY = com.jogl.GLDisplay.getInstance().getCamera().getY();
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public final void run() {
				try {
					openRegionFromMapSelector(regionX, regionY, geoX, geoY, Float.valueOf(cameraY));
				} finally {
					_regionTransitionPending = false;
				}
			}
		});
	}

	/** Updates the bounded streaming window without replacing the active region renderer. */
	private final void updateRegionLoadWindow(final int regionX, final int regionY) {
		if (!Config.FULL_MAP) {
			GeoEngine.getInstance().selectSingleRegion(regionX, regionY);
			_regionLoadManager.selectSingleCenter(regionX, regionY);
			return;
		}
		GeoEngine.getInstance().selectRegionWindow(regionX, regionY);
		_regionLoadManager.selectCenter(regionX, regionY);
		_regionLoadManager.mark(new RegionLoadManager.RegionKey(regionX, regionY), RegionLoadManager.State.LOADED);
		_regionLoadManager.checkNeighborsAsync(key -> GeoEngine.hasGeoFile(key.x(), key.y(), true))
				.thenCompose(ignored -> _regionLoadManager.loadAvailableNeighborsAsync(
						key -> {
							final long start = System.nanoTime();
							final boolean loaded = GeoEngine.getInstance().loadNeighborRegion(key.x(), key.y());
							GLDisplay.getInstance().getPerformanceMetricsCollector().recordRegionLoad(
									(System.nanoTime() - start) / 1000L, false);
							return loaded;
						}));
	}

	public final void onFullMapModeChanged() {
		final GeoRegion active = GeoEngine.getInstance().getActiveRegion();
		if (active != null)
			updateRegionLoadWindow(active.getRegionX(), active.getRegionY());
	}

	public final RegionLoadManager getRegionLoadManager() {
		return _regionLoadManager;
	}

	private final void openRegionFromMapSelector(final int regionX, final int regionY, final int geoX, final int geoY) {
		openRegionFromMapSelector(regionX, regionY, geoX, geoY, null);
	}

	private final void openRegionFromMapSelector(final int regionX, final int regionY, final int geoX, final int geoY,
			final Float preservedCameraY) {
		final GeoRegion current = GeoEngine.getInstance().getActiveRegion();
		if (current != null && (current.getRegionX() != regionX || current.getRegionY() != regionY)
				&& !current.allDataEqual()) {
			final int choice = JOptionPane.showConfirmDialog(this,
					"Region " + current.getName() + " was modified.\nWould you like to save it before changing region?",
					"Save before changing region", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) return;
			if (choice == JOptionPane.YES_OPTION) {
				new DialogSave(this, current, new Runnable() {
					@Override
					public final void run() {
						loadRegionFromSelector(regionX, regionY, geoX, geoY, preservedCameraY);
					}
				}).setVisible(true);
				return;
			}
		}
		loadRegionFromSelector(regionX, regionY, geoX, geoY, preservedCameraY);
	}

	private final void loadRegionFromSelector(final int regionX, final int regionY, final int geoX, final int geoY,
			final Float preservedCameraY) {
		try {
			final long loadStart = System.nanoTime();
			GeoEngine.getInstance().activateRegion(regionX, regionY);
			GLDisplay.getInstance().getPerformanceMetricsCollector().recordRegionDiscard(
					GeoEngine.getInstance().getLastRegionDiscardMicros());
			GLDisplay.getInstance().getPerformanceMetricsCollector().recordRegionRead(
					GeoEngine.getInstance().getLastRegionReadMicros());
			GLDisplay.getInstance().getPerformanceMetricsCollector().recordRegionLoad(
					(System.nanoTime() - loadStart) / 1000L, true);
			if (GeoEngine.getInstance().getActiveRegion() != null) {
				updateRegionLoadWindow(regionX, regionY);
				final int selectedZ = GeoEngine.getInstance().nGetCell(geoX, geoY, 0).getHeight();
				final float cameraY = preservedCameraY == null ? selectedZ / 16f + CAMERA_HEIGHT_OFFSET
						: preservedCameraY.floatValue();
				GLDisplay.getInstance().getCamera().setXYZ(geoX, cameraY, geoY);
			}
		} catch (final Exception exception) {
			exception.printStackTrace();
			JOptionPane.showMessageDialog(this, "Could not open the region.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public final void actionPerformed(final ActionEvent e) {
		if (e.getSource() == _buttonJumpTo) {
			final GeoRegion current = GeoEngine.getInstance().getActiveRegion();
			if (current == null) {
				_mapSelector.setVisible(true);
			} else {
				final com.jogl.GLCamera camera = com.jogl.GLDisplay.getInstance().getCamera();
				_mapSelector.openAt(current.getRegionX(), current.getRegionY(), camera.getGeoX(), camera.getGeoY());
			}
		} else if (e.getSource() == _buttonSave) {
			final GeoRegion region = GeoEngine.getInstance().getActiveRegion();
			if (region != null) {
				new DialogSave(this, region).setVisible(true);
			} else {
				JOptionPane.showMessageDialog(this, "I am sure you want to load a region first ;)",
						"Save Region UNKOWN?!?", JOptionPane.INFORMATION_MESSAGE);
			}
		} else if (e.getSource() == _buttonOpen) {
			if (_fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				final File file = _fileChooser.getSelectedFile();
				if (file != null && GeoEngine.GEO_FILE_FILTER.accept(file)) {
					final int[] header = GeoEngine.getHeaderOfL2jOrL2Off(file);
					final int regionX = header[0];
					final int regionY = header[1];
					final int geoX = GeoEngine.getGeoXY(regionX, 128);
					final int geoY = GeoEngine.getGeoXY(regionY, 128);

					final boolean l2j = !file.getName().toLowerCase().equals(".dat");
					final GeoRegion region = GeoEngine.getInstance().getActiveRegion();
					if (region != null) {
						if (region.getRegionX() != regionX || region.getRegionY() != regionY) {
							if (!region.allDataEqual()) {
								switch (JOptionPane.showConfirmDialog(FrameMain.getInstance(),
										"Region " + region.getName() + " was modified.\nWould u like to save it?",
										"Save...", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)) {
								case JOptionPane.YES_OPTION: {
									new DialogSave(FrameMain.getInstance(), region, new Runnable() {
										@Override
										public final void run() {
											try {
												GeoEngine.getInstance().unload();
												GeoEngine.getInstance().reloadGeo(regionX, regionY, l2j, file);

												if (GeoEngine.getInstance().getActiveRegion() != null)
													GLDisplay.getInstance().getCamera().setXYZ(geoX,
																		GeoEngine.getInstance().nGetCell(geoX, geoY, 0).getHeight()
																				/ 16f + CAMERA_HEIGHT_OFFSET,
															geoY);
											} catch (final Exception e1) {
												e1.printStackTrace();
											}
										}
									}).setVisible(true);
									setVisible(false);
									return;
								}

								case JOptionPane.NO_OPTION:
									break;

								default:
									return;
								}
							}
							GeoEngine.getInstance().unload();
							try {
								GeoEngine.getInstance().reloadGeo(regionX, regionY, l2j, file);
							} catch (final Exception e1) {
								e1.printStackTrace();
							}
						}
					} else {
						try {
							GeoEngine.getInstance().reloadGeo(regionX, regionY, l2j, file);
						} catch (final Exception e1) {
							e1.printStackTrace();
						}
					}

					if (GeoEngine.getInstance().getActiveRegion() != null)
						GLDisplay.getInstance().getCamera().setXYZ(geoX,
							GeoEngine.getInstance().nGetCell(geoX, geoY, 0).getHeight() / 16f + CAMERA_HEIGHT_OFFSET, geoY);
				}
			}
		} else if (e.getSource() == _buttonConfig) {
			_dialogConfig.setVisible(true);
		} else if (e.getSource() == _buttonHelp) {
			JOptionPane.showMessageDialog(this, "<html><b>L2GeoEditor</b><br>"
					+ "Controls:<br><table>" + "<tr><td>Look around:</td><td>Hold RIGHT-MOUSE and move</td></tr>"
					+ "<tr><td>Move around:</td><td>Use WASD and QE</td></tr>"
					+ "<tr><td>Move faster:</td><td>Hold SPACE while moving</td></tr>"
					+ "<tr><td>Select a single cell:</td><td>LEFT-MOUSE-CLICK on any cell</td></tr>"
					+ "<tr><td>Select multiple cells:</td><td>Hold SHIFT while selecting</td></tr>"
					+ "<tr><td>Select a whole block:</td><td>Hold ALT while selecting</td></tr>"
					+ "<tr><td>Unselect a cell:</td><td>Hold SHIFT and LEFT-MOUSE-CLICK on it again</td></tr>"
					+ "<tr><td>Special MultiLayer:</td><td>You see a white box when selecting</td></tr>"
					+ "<tr><td>*</td><td>This box is you selection radius, only cells in this box will be selected</td></tr>"
					+ "<tr><td>*</td><td>To change the radius use the MOUSE_WHEEL or MIDDLE_MOUSE_CLICK to (un)set radius to infinite</td></tr>"
					+ "<tr><td>*</td><td>To change it faster hold SPACE while using the MOUSE_WHEEL</td></tr>"
					+ "<tr><td>Changing height:</td><td>To change the height of the selected cells hold CTRL while using the MOUSE_WHEEL</td></tr>"
					+ "<tr><td>*</td><td>To change the height faster hold SPACE while using the MOUSE_WHEEL</td></tr>"
					+ "<tr><td>Dis/Enable terrain:</td><td>Press T (toggle)</td></tr>"
					+ "<tr><td>Dis/Enable terrain wireframe:</td><td>Press R (toggle)</td></tr>"
					+ "<tr><td>Dis/Enable grid:</td><td>Press G (toggle)</td></tr>"
					+ "<tr><td>Freeze/Unfreeze grid:</td><td>Press F (toggle)</td></tr>" + "</table><br>"
					+ "Note: The editor window need focus to accept inputs!<br><br>"
					+ "This programm is FREE and licensed under GNU GPLv3 <i>http://www.gnu.org/licenses/</i><br>"
					+ "Use it on your OWN RISC! I will not take an ANY WARRANTY!<br><br>"
					+ "<i>http://code.google.com/p/g3d-editor/</i>" + "</html>", "About",
					JOptionPane.INFORMATION_MESSAGE);
		}
	}
}
