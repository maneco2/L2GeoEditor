/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.editor;

import java.io.File;
import java.util.Optional;

import com.geo.core.GeoCoordinates;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Bounds;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** JavaFX port of the legacy square World Map -> Region Map selector. */
final class MapSelectorDialog {
	record Selection(File file, int localGeoX, int localGeoY) {}

	private static final int WORLD_COLUMNS = 17;
	private static final int WORLD_ROWS = 16;
	private final File dataRoot;
	private final File geodataRoot;
	private final Dialog<Selection> dialog = new Dialog<>();
	private final TextField regionX = field(false);
	private final TextField regionY = field(false);
	private final TextField geoX = field(false);
	private final TextField geoY = field(false);
	private final TextField worldX = field(true);
	private final TextField worldY = field(true);
	private final Canvas canvas = new Canvas(1, 1);
	private final StackPane mapPane = new StackPane(canvas);
	private final Button openButton;
	private final Label selectionHeading = new Label("Select Region");
	private final Label availabilityLabel = new Label(" ");
	private Image worldImage;
	private Image regionImage;
	private int selectedRegionX = -1;
	private int selectedRegionY = -1;
	private int selectedLocalGeoX = 1024;
	private int selectedLocalGeoY = 1024;
	private boolean regionView;
	private boolean updating;
	private boolean resizing;

	MapSelectorDialog(final Stage owner, final File dataRoot,
			final File geodataRoot, final int size, final boolean darkTheme) {
		this.dataRoot = dataRoot;
		this.geodataRoot = geodataRoot;
		dialog.initOwner(owner);
		dialog.setTitle("Select Map");
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		/* Closing the hidden native cancel button must never leak ButtonType as
		 * Dialog<Selection>'s result. Real selections are assigned explicitly by
		 * openButton before closing. */
		dialog.setResultConverter(buttonType -> null);
		final javafx.scene.Node nativeCancel =
				dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
		nativeCancel.setVisible(false);
		nativeCancel.setManaged(false);
		openButton = new Button("Open Region");
		final Button cancelButton = new Button("Cancel");
		applyActionButtonStyle(openButton, darkTheme, true);
		applyActionButtonStyle(cancelButton, darkTheme, false);
		openButton.disabledProperty().addListener((observable, oldValue, disabled) ->
				applyActionButtonStyle(openButton, darkTheme, disabled));
		openButton.setDisable(true);
		regionX.setPromptText("10-26");
		regionY.setPromptText("10-25");
		regionX.setTooltip(new javafx.scene.control.Tooltip("Region X (10-26)"));
		regionY.setTooltip(new javafx.scene.control.Tooltip("Region Y (10-25)"));
		geoX.setTooltip(new javafx.scene.control.Tooltip("Absolute Geo X coordinate"));
		geoY.setTooltip(new javafx.scene.control.Tooltip("Absolute Geo Y coordinate"));
		openButton.setDefaultButton(true);
		openButton.setOnAction(event -> {
			if (selectedRegionX < 0 || !regionFile().isFile())
				return;
			dialog.setResult(new Selection(regionFile(),
					selectedLocalGeoX, selectedLocalGeoY));
			dialog.close();
		});
		cancelButton.setCancelButton(true);
		cancelButton.setOnAction(event -> {
			dialog.setResult(null);
			dialog.close();
		});
		dialog.setHeaderText(null);
		dialog.getDialogPane().setStyle(darkTheme
				? "-fx-base: #202830; -fx-background-color: #11171d; "
						+ "-fx-control-inner-background: #171e25; "
						+ "-fx-text-base-color: #d8e5eb; -fx-text-background-color: #d8e5eb;"
				: "-fx-base: #d6e0e5; -fx-background-color: #cbd6dc; "
						+ "-fx-control-inner-background: #e6edf0; "
						+ "-fx-text-base-color: #182126; -fx-text-background-color: #182126;");

		final GridPane coordinates = new GridPane();
		coordinates.setHgap(7);
		coordinates.setVgap(5);
		coordinates.setPadding(new Insets(10));
		coordinates.addRow(0, new Label("Region X:"), regionX,
				new Label("Region Y:"), regionY,
				new Label("Geo X:"), geoX,
				new Label("Geo Y:"), geoY,
				new Label("World X:"), worldX,
				new Label("World Y:"), worldY);
		coordinates.setStyle("-fx-border-color: " + (darkTheme ? "#28343d" : "#a8b7c0")
				+ "; -fx-border-radius: 5; -fx-background-color: "
				+ (darkTheme ? "#171e25" : "#dfe7eb")
				+ "; -fx-background-radius: 5;");

		final BorderPane content = new BorderPane(mapPane);
		selectionHeading.setStyle("-fx-text-fill: " + (darkTheme ? "#f0f7fa" : "#182126")
				+ "; -fx-font-weight: bold; "
				+ "-fx-padding: 0 0 2 2;");
		content.setTop(new VBox(5, selectionHeading, coordinates));
		final javafx.scene.layout.HBox actions = new javafx.scene.layout.HBox(
				10, availabilityLabel, openButton, cancelButton);
		actions.setAlignment(Pos.CENTER);
		actions.setPadding(new Insets(4, 0, 2, 0));
		availabilityLabel.setStyle("-fx-text-fill: "
				+ (darkTheme ? "#e9a2a2" : "#8b2525") + ";");
		content.setBottom(actions);
		BorderPane.setMargin(mapPane, new Insets(8));
		mapPane.setAlignment(Pos.CENTER);
		mapPane.setMinSize(0, 0);
		mapPane.setPrefSize(760, 600);
		mapPane.setStyle("-fx-background-color: "
				+ (darkTheme ? "#11171d" : "#dfe7eb") + ";");
		dialog.getDialogPane().setContent(content);
		final int square = Math.max(720, size);
		content.setMinSize(0, 0);
		content.setPrefSize(square - 32, square - 150);
		dialog.getDialogPane().setPrefSize(square, square);
		dialog.setOnShown(event -> {
			final javafx.stage.Window window =
					dialog.getDialogPane().getScene().getWindow();
			window.setWidth(square);
			window.setHeight(square);
			window.widthProperty().addListener((observable, oldValue, newValue) -> {
				if (resizing)
					return;
				resizing = true;
				window.setHeight(newValue.doubleValue());
				resizing = false;
			});
			window.heightProperty().addListener((observable, oldValue, newValue) -> {
				if (resizing)
					return;
				resizing = true;
				window.setWidth(newValue.doubleValue());
				resizing = false;
			});
			if (Boolean.getBoolean("geoeditor.mapSelectorSmoke")) {
				Platform.runLater(() -> {
					dialog.getDialogPane().applyCss();
					dialog.getDialogPane().layout();
					final Bounds bounds = openButton.localToScene(
							openButton.getBoundsInLocal());
					final double sceneHeight = dialog.getDialogPane()
							.getScene().getHeight();
					final boolean visible = openButton.isVisible()
							&& bounds.getMinY() >= 0d
							&& bounds.getMaxY() <= sceneHeight;
					System.out.println("GEOEDITOR_MAP_SELECTOR=OPEN_VISIBLE:"
							+ visible + ",OPEN_ENABLED:" + !openButton.isDisabled()
							+ ",BOTTOM:" + Math.round(bounds.getMaxY())
							+ ",SCENE_HEIGHT:" + Math.round(sceneHeight));
					if (visible && !openButton.isDisabled())
						openButton.fire();
					else
						dialog.close();
				});
			}
		});

		final File worldMap = new File(dataRoot, "icon/map.jpg");
		worldImage = worldMap.isFile()
				? new Image(worldMap.toURI().toString(), false) : null;
		draw();

		mapPane.widthProperty().addListener((o, oldValue, newValue) -> resizeCanvas());
		mapPane.heightProperty().addListener((o, oldValue, newValue) -> resizeCanvas());
		canvas.setOnMousePressed(event -> {
			if (event.getButton() == MouseButton.PRIMARY)
				selectAt(event.getX(), event.getY());
		});
		canvas.setOnMouseDragged(event -> {
			if (regionView && event.isPrimaryButtonDown())
				selectAt(event.getX(), event.getY());
		});
		regionX.textProperty().addListener((o, oldValue, newValue) ->
				updateFromRegionFields());
		regionY.textProperty().addListener((o, oldValue, newValue) ->
				updateFromRegionFields());
		geoX.textProperty().addListener((o, oldValue, newValue) ->
				updateFromGeoFields());
		geoY.textProperty().addListener((o, oldValue, newValue) ->
				updateFromGeoFields());
		dialog.getDialogPane().setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.ESCAPE) {
				handleEscape();
				event.consume();
			}
		});
	}

	private static void applyActionButtonStyle(final Button button,
			final boolean darkTheme, final boolean disabled) {
		final String background = darkTheme
				? (disabled ? "#1b242b" : "#2c3942")
				: (disabled ? "#d0d9de" : "#e5ecef");
		final String text = darkTheme
				? (disabled ? "#84949e" : "#eef7fa")
				: (disabled ? "#74828a" : "#182126");
		button.setStyle("-fx-background-color: " + background
				+ "; -fx-text-fill: " + text
				+ "; -fx-border-color: " + (darkTheme ? "#465761" : "#9eafb8")
				+ "; -fx-border-radius: 3; -fx-background-radius: 3;"
				+ " -fx-opacity: 1;");
	}

	private void handleEscape() {
		if (regionView) {
			regionView = false;
			selectedRegionX = -1;
			selectedRegionY = -1;
			regionImage = null;
			selectionHeading.setText("Select Region");
			openButton.setDisable(true);
			openButton.setTooltip(null);
			availabilityLabel.setText(" ");
			updating = true;
			regionX.clear(); regionY.clear(); geoX.clear(); geoY.clear();
			worldX.clear(); worldY.clear();
			updating = false;
			draw();
			return;
		}
		dialog.setResult(null);
		dialog.close();
	}

	private void resizeCanvas() {
		final double side = Math.max(1d,
				Math.min(mapPane.getWidth(), mapPane.getHeight()));
		canvas.setWidth(side);
		canvas.setHeight(side);
		draw();
	}

	Optional<Selection> showAndWait() {
		return dialog.showAndWait();
	}

	void openAt(final int regionXValue, final int regionYValue,
			final int localGeoX, final int localGeoY) {
		showRegion(regionXValue, regionYValue);
		selectedLocalGeoX = Math.max(0, Math.min(2047, localGeoX));
		selectedLocalGeoY = Math.max(0, Math.min(2047, localGeoY));
		updateFields();
		draw();
	}

	private static TextField field(final boolean readOnly) {
		final TextField field = new TextField();
		field.setPrefColumnCount(readOnly ? 7 : 5);
		field.setEditable(!readOnly);
		field.setFocusTraversable(!readOnly);
		return field;
	}

	private void selectAt(final double x, final double y) {
		final double width = Math.max(1d, canvas.getWidth());
		final double height = Math.max(1d, canvas.getHeight());
		if (!regionView) {
			showRegion(Math.min(WORLD_COLUMNS - 1,
					Math.max(0, (int) (x / width * WORLD_COLUMNS))),
					Math.min(WORLD_ROWS - 1,
							Math.max(0, (int) (y / height * WORLD_ROWS))));
			return;
		}
		selectedLocalGeoX = Math.min(2047,
				Math.max(0, (int) (x / width * 2048d)));
		selectedLocalGeoY = Math.min(2047,
				Math.max(0, (int) (y / height * 2048d)));
		updateFields();
		draw();
	}

	private void showRegion(final int x, final int y) {
		selectedRegionX = x;
		selectedRegionY = y;
		selectedLocalGeoX = 1024;
		selectedLocalGeoY = 1024;
		final File image = new File(dataRoot, "textures/region/"
				+ (x + 10) + "_" + (y + 10) + ".jpg");
		regionImage = image.isFile()
				? new Image(image.toURI().toString(), false) : null;
		regionView = true;
		updateAvailability();
		selectionHeading.setText("Selected Location");
		updateFields();
		draw();
	}

	private File regionFile() {
		return new File(geodataRoot, (selectedRegionX + 10)
				+ "_" + (selectedRegionY + 10) + ".l2j");
	}

	private void updateFields() {
		updating = true;
		final int absoluteGeoX = (selectedRegionX
				<< GeoCoordinates.REGION_GEO_SHIFT) + selectedLocalGeoX;
		final int absoluteGeoY = (selectedRegionY
				<< GeoCoordinates.REGION_GEO_SHIFT) + selectedLocalGeoY;
		regionX.setText(Integer.toString(selectedRegionX + 10));
		regionY.setText(Integer.toString(selectedRegionY + 10));
		geoX.setText(Integer.toString(absoluteGeoX));
		geoY.setText(Integer.toString(absoluteGeoY));
		worldX.setText(Integer.toString(GeoCoordinates.worldXFromGeo(absoluteGeoX)));
		worldY.setText(Integer.toString(GeoCoordinates.worldYFromGeo(absoluteGeoY)));
		updating = false;
	}

	private void updateFromRegionFields() {
		if (updating)
			return;
		try {
			final int x = Integer.parseInt(regionX.getText()) - 10;
			final int y = Integer.parseInt(regionY.getText()) - 10;
			if (x >= 0 && x < WORLD_COLUMNS && y >= 0 && y < WORLD_ROWS)
				showRegion(x, y);
		} catch (final NumberFormatException ignored) {
			// Partial input is valid while the user is typing.
		}
	}

	private void updateFromGeoFields() {
		if (updating)
			return;
		try {
			final int absoluteX = Integer.parseInt(geoX.getText());
			final int absoluteY = Integer.parseInt(geoY.getText());
			final int x = absoluteX >> GeoCoordinates.REGION_GEO_SHIFT;
			final int y = absoluteY >> GeoCoordinates.REGION_GEO_SHIFT;
			if (x < 0 || x >= WORLD_COLUMNS || y < 0 || y >= WORLD_ROWS)
				return;
			selectedRegionX = x;
			selectedRegionY = y;
			selectedLocalGeoX = absoluteX & 2047;
			selectedLocalGeoY = absoluteY & 2047;
			final File image = new File(dataRoot, "textures/region/"
					+ (x + 10) + "_" + (y + 10) + ".jpg");
			regionImage = image.isFile()
					? new Image(image.toURI().toString(), false) : null;
			regionView = true;
			updateAvailability();
			selectionHeading.setText("Selected Location");
			updateFields();
			draw();
		} catch (final NumberFormatException ignored) {
			// Partial input is valid while the user is typing.
		}
	}

	private void draw() {
		final GraphicsContext graphics = canvas.getGraphicsContext2D();
		final double width = canvas.getWidth();
		final double height = canvas.getHeight();
		graphics.clearRect(0, 0, width, height);
		final Image image = regionView ? regionImage : worldImage;
		if (image != null)
			graphics.drawImage(image, 0, 0, width, height);
		else if (regionView) {
			graphics.setFill(Color.color(0.5, 0.58, 0.62, 0.22));
			graphics.fillRect(0, 0, width, height);
			graphics.setFill(Color.color(0.85, 0.9, 0.93, 0.9));
			graphics.fillText("Region preview image not available",
					Math.max(12d, width / 2d - 95d), height / 2d);
		}
		if (!regionView) {
			graphics.setStroke(Color.color(1, 1, 1, 0.22));
			graphics.setFill(Color.WHITE);
			for (int y = 0; y < WORLD_ROWS; y++) {
				for (int x = 0; x < WORLD_COLUMNS; x++) {
					final double left = x * width / WORLD_COLUMNS;
					final double top = y * height / WORLD_ROWS;
					graphics.strokeRect(left, top, width / WORLD_COLUMNS,
							height / WORLD_ROWS);
					graphics.fillText((x + 10) + "_" + (y + 10),
							left + 3, top + 13);
				}
			}
			return;
		}
		final double markerX = Math.round(selectedLocalGeoX / 2048d
				* Math.max(0d, width - 1d));
		final double markerY = Math.round(selectedLocalGeoY / 2048d
				* Math.max(0d, height - 1d));
		graphics.setFill(Color.color(1, 0, 0, 0.45));
		graphics.fillOval(markerX - 12, markerY - 12, 24, 24);
		graphics.setStroke(Color.WHITE);
		graphics.strokeOval(markerX - 12, markerY - 12, 24, 24);
		graphics.setStroke(Color.RED);
		graphics.strokeOval(markerX - 8, markerY - 8, 16, 16);
		graphics.setFill(Color.RED);
		graphics.fillOval(markerX - 3, markerY - 3, 6, 6);
	}

	private void updateAvailability() {
		final File file = regionFile();
		final boolean available = file.isFile();
		openButton.setDisable(!available);
		openButton.setTooltip(new javafx.scene.control.Tooltip(available
				? "Open region " + (selectedRegionX + 10) + "_"
						+ (selectedRegionY + 10)
				: "Missing geodata: " + file.getPath()));
		availabilityLabel.setText(available ? " "
				: "Geodata file not available");
	}
}
