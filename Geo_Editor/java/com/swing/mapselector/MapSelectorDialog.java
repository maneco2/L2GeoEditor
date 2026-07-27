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
package com.swing.mapselector;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.JTextField;
import javax.swing.BorderFactory;
import com.Config;
import com.geo.GeoEngine;
import com.util.Util;

/** New map selector entry point. The legacy JumpTo dialog is not used here. */
@SuppressWarnings("serial")
public final class MapSelectorDialog extends JDialog {
    @FunctionalInterface public interface RegionOpenListener { void open(int regionX, int regionY, int geoX, int geoY); }
    private final MapSelectorState state = new MapSelectorState();
    private final JPanel cards = new JPanel(new CardLayout());
    private final WorldMapPanel worldMap;
    private final JTextField regionXField = new JTextField(4);
    private final JTextField regionYField = new JTextField(4);
    private final JTextField geoXField = new JTextField(7);
    private final JTextField geoYField = new JTextField(7);
    private final JTextField worldXField = new JTextField(7);
    private final JTextField worldYField = new JTextField(7);
    private boolean updatingFields;
    private int selectedGeoX = -1, selectedGeoY = -1;
    private final JButton openButton = new JButton("Open Region");
    private final RegionOpenListener listener;
    private RegionMapPanel regionView;

    public MapSelectorDialog(final Frame owner, final RegionOpenListener listener) {
        super(owner, "Select Map", true);
        this.listener = listener;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        final BufferedImage image = Util.scaleImage(Util.loadImage("./data/icon/map.jpg"), 629, 592, 2);
        worldMap = new WorldMapPanel(image, 17, 16, this::showRegion);
        openButton.setEnabled(false);
        openButton.addActionListener((event) -> {
            if (state.getView() == MapSelectorState.View.REGION && selectedGeoX >= 0) {
                listener.open(state.getRegionX(), state.getRegionY(), selectedGeoX, selectedGeoY);
                setVisible(false);
            }
        });
        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener((event) -> setVisible(false));
        final JPanel buttons = new JPanel();
        buttons.add(openButton); buttons.add(cancelButton);
        worldMap.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        cards.add(worldMap, "world");
        add(cards, BorderLayout.CENTER); add(buttons, BorderLayout.SOUTH);
        regionXField.setToolTipText("Region X (10-26)"); regionYField.setToolTipText("Region Y (10-25)");
        geoXField.setEditable(false); geoYField.setEditable(false); worldXField.setEditable(false); worldYField.setEditable(false);
        final JPanel coordinates = new JPanel();
        coordinates.setBorder(BorderFactory.createTitledBorder("Selected Location"));
        coordinates.add(new JLabel("Region X:")); coordinates.add(regionXField);
        coordinates.add(new JLabel("Region Y:")); coordinates.add(regionYField);
        coordinates.add(new JLabel("Geo X:")); coordinates.add(geoXField);
        coordinates.add(new JLabel("Geo Y:")); coordinates.add(geoYField);
        coordinates.add(new JLabel("World X:")); coordinates.add(worldXField);
        coordinates.add(new JLabel("World Y:")); coordinates.add(worldYField);
        add(coordinates, BorderLayout.NORTH);
        regionXField.addActionListener(event -> updateFromFields());
        regionYField.addActionListener(event -> updateFromFields());
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-selector");
        getRootPane().getActionMap().put("close-selector", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                if (state.getView() == MapSelectorState.View.REGION) {
                    state.escape();
                    ((CardLayout) cards.getLayout()).show(cards, "world");
                    openButton.setEnabled(false);
                } else setVisible(false);
            }
        });
        setSize(Config.MAP_SELECTOR_WIDTH, Config.MAP_SELECTOR_HEIGHT); setLocationRelativeTo(owner);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                Config.MAP_SELECTOR_WIDTH = getWidth(); Config.MAP_SELECTOR_HEIGHT = getHeight(); Config.save();
            }
        });
    }

    private void showRegion(final int regionX, final int regionY) {
        state.selectRegion(regionX, regionY);
        final int centerCellX = GeoEngine.GEO_REGION_SIZE / 2;
        final int centerCellY = GeoEngine.GEO_REGION_SIZE / 2;
        final int centerGeoX = GeoEngine.getGeoXY(regionX, centerCellX);
        final int centerGeoY = GeoEngine.getGeoXY(regionY, centerCellY);
        selectedGeoX = centerGeoX;
        selectedGeoY = centerGeoY;
        updatingFields = true;
        regionXField.setText(String.valueOf(regionX + 10)); regionYField.setText(String.valueOf(regionY + 10));
        geoXField.setText(String.valueOf(centerGeoX)); geoYField.setText(String.valueOf(centerGeoY));
        worldXField.setText(String.valueOf(GeoEngine.getWorldX(centerGeoX)));
        worldYField.setText(String.valueOf(GeoEngine.getWorldY(centerGeoY)));
        updatingFields = false;
        final String name = (regionX + 10) + "_" + (regionY + 10);
        final java.awt.image.BufferedImage region = Util.loadImage("./data/textures/region/" + name + ".jpg");
        final String status = region == null ? "Region image not found" : "Region image loaded";
        final String info = "Region: " + name + "   Geo: " + centerGeoX + ", " + centerGeoY + "   " + status;
        final RegionMapPanel view = region == null ? null : new RegionMapPanel(region, (cellX, cellY) -> updateGeoSelection(regionX, regionY, cellX, cellY));
        regionView = view;
        if (view == null) { cards.add(new JLabel(info, SwingConstants.CENTER), "region"); }
        else {
            view.setMarkerToCenter();
            cards.add(view, "region");
        }
        ((CardLayout) cards.getLayout()).show(cards, "region");
        // One click on the world grid selects the region and its center. Enter
        // or Open Region can therefore be used without a second click inside
        // the preview image.
        openButton.setEnabled(true);
    }

    /** Opens the selector at the region and block currently used by the editor. */
    public void openAt(final int regionX, final int regionY, final int geoX, final int geoY) {
        showRegion(regionX, regionY);
        final int blockX = Math.max(0, Math.min(GeoEngine.GEO_REGION_SIZE - 1, GeoEngine.getBlockXY(geoX)));
        final int blockY = Math.max(0, Math.min(GeoEngine.GEO_REGION_SIZE - 1, GeoEngine.getBlockXY(geoY)));
        selectedGeoX = GeoEngine.getGeoXY(regionX, blockX);
        selectedGeoY = GeoEngine.getGeoXY(regionY, blockY);
        if (regionView != null) regionView.setMarkerAtCell(blockX, blockY);
        updatingFields = true;
        regionXField.setText(String.valueOf(regionX + 10));
        regionYField.setText(String.valueOf(regionY + 10));
        geoXField.setText(String.valueOf(selectedGeoX));
        geoYField.setText(String.valueOf(selectedGeoY));
        worldXField.setText(String.valueOf(GeoEngine.getWorldX(selectedGeoX)));
        worldYField.setText(String.valueOf(GeoEngine.getWorldY(selectedGeoY)));
        updatingFields = false;
        openButton.setEnabled(true);
        setVisible(true);
    }

    private void updateFromFields() {
        if (updatingFields) return;
        try {
            final int x = Integer.parseInt(regionXField.getText()) - 10;
            final int y = Integer.parseInt(regionYField.getText()) - 10;
            if (x >= 0 && x < 17 && y >= 0 && y < 16) showRegion(x, y);
        } catch (NumberFormatException ignored) { }
    }

    private void updateGeoSelection(final int regionX, final int regionY, final int cellX, final int cellY) {
        final int geoX = com.geo.GeoEngine.getGeoXY(regionX, cellX);
        final int geoY = com.geo.GeoEngine.getGeoXY(regionY, cellY);
        selectedGeoX = geoX; selectedGeoY = geoY; openButton.setEnabled(true);
        geoXField.setText(String.valueOf(geoX)); geoYField.setText(String.valueOf(geoY));
        worldXField.setText(String.valueOf(com.geo.GeoEngine.getWorldX(geoX)));
        worldYField.setText(String.valueOf(com.geo.GeoEngine.getWorldY(geoY)));
    }
}
