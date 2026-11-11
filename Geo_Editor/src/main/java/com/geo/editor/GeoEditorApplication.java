/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.editor;

import java.awt.image.BufferedImage;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import com.geo.core.GeoCoordinates;
import com.geo.core.CameraState;
import com.geo.core.DetailedRegionSnapshot;
import com.geo.core.EditableRegionSession;
import com.geo.core.RegionSnapshot;
import com.geo.core.RegionSnapshotWindow;
import com.geo.RegionLoadManager;
import com.geo.RegionStreamingPolicy;
import com.geo.RegionWindowPlan;
import com.geo.SelectionNavigationPolicy;
import com.geo.core.InputBindings;
import com.util.PerformanceMetrics;

import javafx.animation.PauseTransition;
import javafx.animation.AnimationTimer;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.robot.Robot;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/** JavaFX/LWJGL geodata editor under staged functional migration. */
public final class GeoEditorApplication extends Application {
	private record CellSelection(int blockX, int blockY, int cellX,
			int cellY, int layer) {}
	private record EditHistoryEntry(String label,
			EditableRegionSession.BlockCheckpoint before,
			EditableRegionSession.BlockCheckpoint after,
			long beforeRevision, long afterRevision, long recordedAtNanos) {}
	private record StartupSplash(StackPane overlay, Node uiRoot,
			StackPane applicationRoot) {}
	private record AboutDetail(String property, String value) {}
	private record AboutControl(String action, String control, String type,
			InputBindings.Action binding) {}
	private final LwjglRenderBackend renderer = new LwjglRenderBackend();
	private final ImageView viewportImage = new ImageView();
	private WritableImage viewportWritableImage;
	private final StackPane viewport = new StackPane();
	private final ImageView miniMapImage = new ImageView();
	private final ImageView miniMapPreviousImage = new ImageView();
	private final StackPane miniMap = new StackPane();
	private final ImageView miniMapMarker = new ImageView();
	private final Label overlay = uiLabel("No region loaded");
	private final VBox mapHud = new VBox();
	private final Label status = uiLabel("Initializing...");
	private final Label editState = uiLabel("No map");
	private final Label metricsHud = uiLabel("Metrics: waiting for first frame");
	private final Label transitionOverlay = uiLabel("");
	private final Label neighborLoadingTitle = uiLabel("Loading neighboring maps");
	private final Label neighborLoadingPercent = uiLabel("0%");
	private final Label neighborLoadingCount = uiLabel("0 / 0 maps");
	private final Label neighborLoadingPhase = uiLabel("Preparing geodata");
	private final ProgressBar neighborLoadingProgress = new ProgressBar(0d);
	private final StackPane neighborLoadingOverlay = new StackPane();
	private boolean streamingUiBlocked;
	private Node editorUiRoot;
	private boolean neighborTexturesPending;
	private boolean skyTexturesPending;
	private boolean streamingDeferredForSky;
	private boolean skyCompletionHold;
	private boolean mapCompletionHold;
	private String loadingSequenceSmokeLast = "";
	private final TextField regionXValue = coordinateField();
	private final TextField regionYValue = coordinateField();
	private final TextField worldXValue = coordinateField();
	private final TextField worldYValue = coordinateField();
	private final TextField geoXValue = coordinateField();
	private final TextField geoYValue = coordinateField();
	private final TextField blockXValue = coordinateField();
	private final TextField blockYValue = coordinateField();
	private final TextField cellXValue = coordinateField();
	private final TextField cellYValue = coordinateField();
	private final TextField typeValue = coordinateField();
	private final TextField layersValue = coordinateField();
	private final Label heightValue = uiLabel("-");
	private final TextField heightEditor = new TextField();
	private final ListView<String> layerList = new ListView<>();
	private final CheckBox nsweNorth = new CheckBox("N");
	private final CheckBox nsweSouth = new CheckBox("S");
	private final CheckBox nsweWest = new CheckBox("W");
	private final CheckBox nsweEast = new CheckBox("E");
	private final ImageView directNsweImage = new ImageView();
	private Image directNsweAtlas;
	private final ToggleButton[] nswePresets = new ToggleButton[16];
	private final ToggleGroup nswePresetGroup = new ToggleGroup();
	private final Button convertFlatButton = new Button("Flat");
	private final Button convertComplexButton = new Button("Complex");
	private final Button convertMultiButton = new Button("Multi");
	private final Button restoreBlockButton = new Button("Restore from base");
	private final Set<InputBindings.Action> pressedActions = new HashSet<>();
	private ContextMenu selectedBlockMenu;
	private long lastHudUpdateNanos;
	private String cachedHudPerformance = "\nRegions: 0\nMemory: 0mb"
			+ "\nFrames: 0\nFps: 0\nElements/Draws: 0/0";
	private final PerformanceMetrics metrics = new PerformanceMetrics();
	private final RegionSnapshotWindow snapshotWindow = new RegionSnapshotWindow();
	private final CameraState camera = new CameraState();
	private Stage stage;
	private Button saveButton;
	private Button undoButton;
	private Button redoButton;
	private Button clearHistoryButton;
	private final Label historyState = uiLabel("H 0/100 | R 0");
	private static final int EDIT_HISTORY_LIMIT = 100;
	private final Deque<EditHistoryEntry> undoHistory = new ArrayDeque<>();
	private final Deque<EditHistoryEntry> redoHistory = new ArrayDeque<>();
	private long currentRevision;
	private long savedRevision;
	private long nextRevision;
	private Scene scene;
	private ToolBar mainToolbar;
	private VBox editorSidebar;
	private BorderPane editorBottomBar;
	private File dataRoot;
	private File geodataRoot;
	private EditorPreferences preferences;
	private RegionSnapshot currentSnapshot;
	private DetailedRegionSnapshot currentDetails;
	private EditableRegionSession currentSession;
	private File currentTexture;
	private LwjglRenderBackend.RenderedFrame currentFrame;
	private Map<RegionLoadManager.RegionKey, RegionSnapshot> visibleRegions = Map.of();
	private RegionLoadManager.RegionKey streamingCenterKey;
	private int selectedBlockX = -1;
	private int selectedBlockY = -1;
	private int selectedCellX = -1;
	private int selectedCellY = -1;
	private int selectedLayer = -1;
	private final LinkedHashSet<CellSelection> selectedCells = new LinkedHashSet<>();
	private boolean texturedTerrain;
	private boolean fullTexturedTerrain;
	private boolean darkTheme = true;
	private boolean auxiliaryTerrains;
	private boolean fullMap;
	private boolean geodataGrid;
	private boolean circularGrid;
	private boolean transparency = true;
	private boolean drawOutline;
	private int neighborCount = 8;
	private int cameraSpeedPercent = 100;
	private CompletableFuture<RegionSnapshotWindow.Snapshot> streamingTask =
			CompletableFuture.completedFuture(null);
	private float cameraPitch = -35f;
	private float cameraYaw = 360f;
	private double dragX;
	private double dragY;
	private double cameraPointerAnchorX;
	private double cameraPointerAnchorY;
	private Robot cameraPointerRobot;
	private boolean cameraPointerCaptured;
	private boolean cameraDragged;
	private boolean cellSelectionGesture;
	private boolean selectionGestureFullBlock;
	private boolean selectionGestureAppend;
	private final Set<CellSelection> gestureSelections = new HashSet<>();
	private boolean cameraRenderPending;
	private boolean selectionRenderPending;
	private boolean rangeBenchmarkRunning;
	private boolean fullMapSmokeTriggered;
	private boolean neighborStyleComparisonTriggered;
	private int neighborResizeSmokeStep;
	private int longTraversalTransitions;
	private boolean regionTransitionPending;
	private boolean regionVisualTransitionPending;
	private Animation miniMapFade;
	private long miniMapLoadGeneration;
	private RegionLoadManager.RegionKey transitionCandidate;
	private int transitionCandidateFrames;
	private long previousCameraTick;
	private int miniMapSize = EditorSettings.MINI_MAP_SIZE;
	private double miniMapResizeStartX;
	private double miniMapResizeStartY;
	private int miniMapResizeStartSize;
	private boolean miniMapResizing;
	private int visibleGridRange = 24;
	private int mapSelectorSize = 896;
	private int antialiasingSamples = 2;
	private int anisotropicFiltering = 8;
	private int neighborVisualStyle = 1;
	private int skyStyle;

	@Override
	public void start(final Stage primaryStage) {
		stage = primaryStage;
		dataRoot = new File(System.getProperty("geoeditor.data", "data"));
		final File configFile = new File(System.getProperty("geoeditor.config",
				new File(dataRoot.getParentFile(), "configs/GeoEditor.xml").getPath()));
		preferences = new EditorPreferences(configFile);
		preferences.load();
		final File defaultGeodataRoot = new File(dataRoot, "geodata");
		final String geodataOverride = System.getProperty("geoeditor.geodata", "");
		final File configuredGeodataRoot = geodataOverride.isBlank()
				? new File(preferences.getShared(
						"PATH_TO_GEO_FILES", defaultGeodataRoot.getPath()))
				: new File(geodataOverride);
		geodataRoot = configuredGeodataRoot.isDirectory()
				? configuredGeodataRoot : defaultGeodataRoot;
		EditorSettings.PATH_TO_GEO_FILES = geodataRoot.getPath();
		com.geo.GeoEngine.setGeodataDirectory(geodataRoot);
		loadInputBindings();
		loadLegacyRenderPreferences();
		miniMapSize = Math.max(96, preferences.getSharedInt(
				"MINI_MAP_SIZE", EditorSettings.MINI_MAP_SIZE));
		visibleGridRange = Math.max(8, Math.min(256,
				preferences.getSharedInt("VIS_GRID_RANGE", 24)));
		if (System.getProperty("geoeditor.visibleGridRange") != null)
			visibleGridRange = Math.max(8, Math.min(256,
					Integer.getInteger("geoeditor.visibleGridRange", 96)));
		mapSelectorSize = Math.max(640, Math.max(
				preferences.getSharedInt("MAP_SELECTOR_WIDTH", 896),
				preferences.getSharedInt("MAP_SELECTOR_HEIGHT", 896)));
		cameraPitch = preferences.getFloat("CAMERA_PITCH", -35f, -90f, 90f);
		if (System.getProperty("geoeditor.cameraPitch") != null)
			cameraPitch = Math.max(-90f, Math.min(90f,
					Float.parseFloat(System.getProperty("geoeditor.cameraPitch"))));
		cameraYaw = preferences.getFloat("CAMERA_YAW", 42f,
				-1_000_000f, 1_000_000f);
		/*
		 * The geodata editor world is the geodata grid itself. Region JPGs remain
		 * restricted to the minimap and map selector; they are not a world floor.
		 */
		texturedTerrain = preferences.getBoolean("TEXTURED_TERRAIN", false);
		fullTexturedTerrain = preferences.getBoolean("FULL_TEXTURED_TERRAIN", false);
		darkTheme = !"LIGHT".equalsIgnoreCase(preferences.get("UI_THEME", "DARK"));
		final String forcedTheme = System.getProperty("geoeditor.theme");
		if (forcedTheme != null)
			darkTheme = !"LIGHT".equalsIgnoreCase(forcedTheme);
		auxiliaryTerrains = preferences.getBoolean("AUXILIARY_TERRAINS", false);
		fullMap = preferences.getBoolean("FULL_MAP", false);
		geodataGrid = preferences.getBoolean("GEODATA_GRID", true);
		circularGrid = preferences.getSharedBoolean(
				"CIRCULAR_GEODATA_GRID", EditorSettings.CIRCULAR_GEODATA_GRID);
		transparency = preferences.getSharedBoolean(
				"USE_TRANSPARENCY", EditorSettings.USE_TRANSPARENCY);
		drawOutline = preferences.getSharedBoolean(
				"DRAW_OUTLINE", EditorSettings.DRAW_OUTLINE);
		if (System.getProperty("geoeditor.circularGrid") != null)
			circularGrid = Boolean.getBoolean("geoeditor.circularGrid");
		neighborCount = RegionWindowPlan.normalizeNeighborCount(
				(int) preferences.getFloat("NEIGHBOR_COUNT", 8f, 8f, 120f));
		cameraSpeedPercent = (int) preferences.getFloat(
				"CAMERA_SPEED_PERCENT", 100f, 25f, 400f);
		antialiasingSamples = normalizeAntialiasing((int) preferences.getFloat(
				"ANTIALIASING_SAMPLES", 2f, 0f, 16f));
		anisotropicFiltering = normalizeTextureFiltering((int) preferences.getFloat(
				"ANISOTROPIC_FILTERING", 8f, 1f, 16f));
		neighborVisualStyle = Math.max(1, Math.min(10, (int) preferences.getFloat(
				"NEIGHBOR_VISUAL_STYLE", 1f, 1f, 10f)));
		skyStyle = Math.max(0, Math.min(4, (int) preferences.getFloat(
				"SKY_STYLE", 0f, 0f, 4f)));
		if (System.getProperty("geoeditor.skyStyle") != null)
			skyStyle = Math.max(0, Math.min(4,
					Integer.getInteger("geoeditor.skyStyle", 0)));
		if (Boolean.getBoolean("geoeditor.configApplySmoke"))
			neighborCount = 8;
		if (Boolean.getBoolean("geoeditor.streamingSmoke")) {
			auxiliaryTerrains = true;
			fullMap = true;
			fullTexturedTerrain = true;
			neighborCount = RegionWindowPlan.normalizeNeighborCount(
					Integer.getInteger("geoeditor.streamingRegions", 8));
		}
		if (Boolean.getBoolean("geoeditor.neighborStyleSmoke")) {
			auxiliaryTerrains = true;
			fullMap = false;
			fullTexturedTerrain = false;
			neighborCount = 8;
		}
		if (Boolean.getBoolean("geoeditor.neighborResizeSmoke")) {
			auxiliaryTerrains = true;
			fullMap = false;
			fullTexturedTerrain = false;
			neighborCount = 8;
		}
		if (Boolean.getBoolean("geoeditor.regionVisualSmoke")) {
			texturedTerrain = true;
			geodataGrid = false;
		}
		renderer.initialize();
		renderer.setNeighborVisualStyle(neighborVisualStyle);
		renderer.setSkyStyle(skyStyle);
		renderer.setSkyTexture(new File(dataRoot,
				"textures/sky/nasa-deep-star-map-16k.png"));
		renderer.setMoonTexture(new File(dataRoot,
				"textures/sky/nasa-lroc-moon-16k.png"));
		renderer.setMoonStarsTexture(new File(dataRoot,
				"textures/sky/nasa-deep-star-map-16k.png"));
		if (!renderer.hasCompleteSkyTextureSet())
			throw new IllegalStateException(
					"The selected 16K sky texture set is required.");
		renderer.setTextureAnisotropy(anisotropicFiltering);

		final BorderPane root = new BorderPane();
		editorUiRoot = root;
		root.setStyle("-fx-background-color: #11171d;");
		root.setTop(createToolbar());
		root.setRight(createSidebar());
		root.setCenter(createViewport());
		root.setBottom(createBottomBar());

		stage.setTitle("GeoEditor 2.0.4.31");
		final File applicationIcon = new File(dataRoot, "icon/geoeditor.png");
		if (applicationIcon.isFile())
			stage.getIcons().add(new Image(applicationIcon.toURI().toString()));
		final int configuredWidth = Math.max(1100,
				preferences.getSharedInt("WINDOW_WIDTH", 1440));
		final int configuredHeight = Math.max(700,
				preferences.getSharedInt("WINDOW_HEIGHT", 900));
		final StackPane applicationRoot = new StackPane(root);
		final StartupSplash startupSplash = createStartupSplash(
				applicationRoot, root);
		scene = new Scene(applicationRoot, configuredWidth, configuredHeight);
			scene.setOnKeyPressed(event -> {
			if (streamingUiBlocked) {
				event.consume();
				return;
			}
			if (matchesBinding(event, InputBindings.Action.OPEN_BLOCK_ACTIONS)) {
				toggleSelectedBlockMenu(viewport.getWidth() / 2d,
						viewport.getHeight() / 2d);
				event.consume();
				return;
			}
			if (isUiControlFocused())
				return;
			final InputBindings.Action action = matchingAction(event);
			if (action == InputBindings.Action.UNDO_EDIT) {
				undoEdit();
				event.consume();
			} else if (action == InputBindings.Action.REDO_EDIT) {
				redoEdit();
				event.consume();
			} else if (navigateSelection(action))
				event.consume();
			else if (isCameraMovementAction(action))
				pressedActions.add(action);
			else if (action == InputBindings.Action.TOGGLE_TEXTURED_TERRAIN) {
				texturedTerrain = !texturedTerrain;
				savePreferences();
				renderCurrent();
			} else if (action == InputBindings.Action.TOGGLE_GEODATA_GRID) {
				geodataGrid = !geodataGrid;
				savePreferences();
				renderCurrent();
			}
		});
		scene.setOnKeyReleased(event -> {
			pressedActions.removeIf(action -> action.getKeyCode() == event.getCode().getCode());
			if (currentSnapshot != null)
				renderCurrent();
		});
		stage.setScene(scene);
		applyTheme();
		stage.setMinWidth(1100);
		stage.setMinHeight(700);
		final int configuredX = preferences.getSharedInt("WINDOW_X", -1);
		final int configuredY = preferences.getSharedInt("WINDOW_Y", -1);
		if (configuredX >= 0)
			stage.setX(configuredX);
		if (configuredY >= 0)
			stage.setY(configuredY);
		stage.setMaximized(preferences.getSharedBoolean("WINDOW_MAXIMIZED", false));
		stage.focusedProperty().addListener((observable, wasFocused, focused) -> {
			if (!focused) {
				pressedActions.clear();
				releaseCameraPointer();
			}
		});
		stage.show();
		playStartupSplash(startupSplash);
		startCameraTimer();

		final String initialPath = System.getProperty("geoeditor.region");
		if (initialPath != null && !initialPath.isBlank())
			loadRegion(new File(initialPath));
		else
			status.setText("Open or select a geodata region.");
		if (Boolean.getBoolean("geoeditor.startupSmoke"))
			System.out.println("GEOEDITOR_STARTUP=REGION:"
					+ (currentSnapshot == null ? "NONE" : currentSnapshot.name())
					+ ",MINIMAP_VISIBLE:" + mapHud.isVisible()
					+ ",MINIMAP_MANAGED:" + mapHud.isManaged());
		if (Boolean.getBoolean("geoeditor.typePickingSmoke"))
			validateTypePickingSmoke();
		if (Boolean.getBoolean("geoeditor.gridModesSmoke"))
			validateGridModesSmoke();
		if (Boolean.getBoolean("geoeditor.transparencySmoke"))
			validateTransparencySmoke();
		if (Boolean.getBoolean("geoeditor.outlineSmoke"))
			validateOutlineSmoke();
		if (Boolean.getBoolean("geoeditor.cameraSmoke")) {
			camera.rotate(-20f, 12f);
			camera.moveForward(-24d);
			camera.moveSideways(12d);
			renderCurrent();
			System.out.println("GEOEDITOR_CAMERA=XYZ:" + camera.x() + ","
					+ camera.y() + "," + camera.z() + ",ROT:"
					+ camera.rotationX() + "," + camera.rotationY());
		}
		if (Boolean.getBoolean("geoeditor.range96BenchmarkSmoke"))
			validateRange96BenchmarkSmoke();
		if (Boolean.getBoolean("geoeditor.skyStyleSmoke"))
			compareSkyStylesSmoke();
		if (Boolean.getBoolean("geoeditor.skyMotionSmoke"))
			validateSkyWorldLockSmoke();
		if (Boolean.getBoolean("geoeditor.graphicsComparisonSmoke"))
			validateGraphicsQualityBenchmarkSmoke();
		if (Boolean.getBoolean("geoeditor.selectionSmoke"))
			selectBlockAt(viewport.getWidth() / 2d, viewport.getHeight() / 2d,
					viewport.getWidth(), viewport.getHeight());
		if (Boolean.getBoolean("geoeditor.cellLayersThemeSmoke"))
			validateCellLayersThemeSmoke();
		if (Boolean.getBoolean("geoeditor.inputSmoke"))
			validateInputSmoke();
		final String frameOutput = System.getProperty("geoeditor.frameOutput");
		if (frameOutput != null && !frameOutput.isBlank()) {
			if (Boolean.getBoolean("geoeditor.regionVisualSmoke")) {
				final PauseTransition capture = new PauseTransition(Duration.millis(
						Long.getLong("geoeditor.frameDelayMillis", 3000L)));
				capture.setOnFinished(event -> writeFrameSmoke(new File(frameOutput)));
				capture.play();
			} else {
				writeFrameSmoke(new File(frameOutput));
			}
		}
		if (Boolean.getBoolean("geoeditor.mapSelectorSmoke"))
			selectMap();
		if (Boolean.getBoolean("geoeditor.configSmoke")
				|| Boolean.getBoolean("geoeditor.configApplySmoke"))
			showSettings();
		if (Boolean.getBoolean("geoeditor.aboutSmoke"))
			showAbout();

		final long autoCloseMillis =
				Long.getLong("geoeditor.autocloseMillis", 0L);
		if (autoCloseMillis > 0) {
			final PauseTransition close =
					new PauseTransition(Duration.millis(autoCloseMillis));
			close.setOnFinished(event -> {
				if (Boolean.getBoolean("geoeditor.streamingSmoke"))
					System.out.println("GEOEDITOR_AUX_TEXTURES_FINAL=RESIDENT:"
							+ renderer.residentAuxiliaryTextureCount() + ",DESIRED:"
							+ renderer.desiredAuxiliaryTextureCount() + ",PENDING:"
							+ renderer.hasPendingTextureWork());
				stage.close();
			});
			close.play();
		}
	}

	private StartupSplash createStartupSplash(final StackPane applicationRoot,
			final Node uiRoot) {
		if (Long.getLong("geoeditor.autocloseMillis", 0L) > 0L
				&& !Boolean.getBoolean("geoeditor.startupSplashSmoke"))
			return null;
		final File splashFile = new File(dataRoot, "image/geoeditor-splash.png");
		if (!splashFile.isFile())
			return null;
		final ImageView image = new ImageView(new Image(splashFile.toURI().toString()));
		image.setPreserveRatio(false);
		final StackPane splash = new StackPane(image);
		splash.setStyle("-fx-background-color: black;");
		image.fitWidthProperty().bind(applicationRoot.widthProperty());
		image.fitHeightProperty().bind(applicationRoot.heightProperty());
		uiRoot.setOpacity(0d);
		applicationRoot.getChildren().add(splash);
		return new StartupSplash(splash, uiRoot, applicationRoot);
	}

	private void playStartupSplash(final StartupSplash splash) {
		if (splash == null)
			return;
		final PauseTransition hold = new PauseTransition(Duration.millis(1400));
		final FadeTransition fadeOut = new FadeTransition(
				Duration.millis(1800), splash.overlay());
		fadeOut.setFromValue(1d);
		fadeOut.setToValue(0d);
		fadeOut.setOnFinished(event -> {
			splash.applicationRoot().getChildren().remove(splash.overlay());
		});
		final SequentialTransition sequence = new SequentialTransition(hold, fadeOut);
		hold.setOnFinished(event -> splash.uiRoot().setOpacity(1d));
		sequence.play();
	}

	private void writeFrameSmoke(final File output) {
		if (currentFrame == null)
			return;
		final BufferedImage image = new BufferedImage(currentFrame.width(),
				currentFrame.height(), BufferedImage.TYPE_INT_ARGB);
		final byte[] bgra = currentFrame.bgra();
		final int[] argb = new int[currentFrame.width() * currentFrame.height()];
		for (int pixel = 0, offset = 0; pixel < argb.length; pixel++, offset += 4)
			argb[pixel] = (bgra[offset + 3] & 0xff) << 24
					| (bgra[offset + 2] & 0xff) << 16
					| (bgra[offset + 1] & 0xff) << 8
					| bgra[offset] & 0xff;
		image.setRGB(0, 0, currentFrame.width(), currentFrame.height(),
				argb, 0, currentFrame.width());
		try {
			ImageIO.write(image, "png", output);
			System.out.println("GEOEDITOR_FRAME_OUTPUT=" + output.getAbsolutePath());
		} catch (final IOException exception) {
			throw new IllegalStateException("Unable to write smoke frame", exception);
		}
	}

	private void validateGridModesSmoke() {
		final boolean configured = circularGrid;
		final int configuredRange = visibleGridRange;
		final float configuredX = camera.x();
		final float configuredY = camera.y();
		final float configuredZ = camera.z();
		final float configuredPitch = camera.rotationX();
		final float configuredYaw = camera.rotationY();
		long square = 0L;
		long circular = 0L;
		try {
			/*
			 * A normal editing camera can see only the common center of both masks,
			 * producing equal frames even when the circular shader is correct. The
			 * top-down view includes the range-96 boundary and makes the comparison
			 * deterministic on the real 23_18 smoke region.
			 */
			visibleGridRange = 96;
			camera.set(1024f, configuredY + 2048f, 1024f, -90f, 0f);
			circularGrid = false;
			renderCurrent();
			renderCurrent();
			square = frameChecksum();
			circularGrid = true;
			renderCurrent();
			renderCurrent();
			circular = frameChecksum();
			if (square == circular)
				throw new IllegalStateException(
						"Square and circular range-96 grid frames are equal.");
		} finally {
			circularGrid = configured;
			visibleGridRange = configuredRange;
			camera.set(configuredX, configuredY, configuredZ,
					configuredPitch, configuredYaw);
			renderCurrent();
			renderCurrent();
		}
		System.out.println("GEOEDITOR_GRID_MODES=CHECK_RANGE:96,SQUARE_CRC:"
				+ Long.toHexString(square)
				+ ",CIRCULAR_CRC:" + Long.toHexString(circular));
	}

	private void validateTransparencySmoke() {
		final boolean configured = transparency;
		transparency = true;
		renderCurrent();
		renderCurrent();
		final long transparent = frameChecksum();
		transparency = false;
		renderCurrent();
		renderCurrent();
		final long opaque = frameChecksum();
		if (transparent == opaque)
			throw new IllegalStateException(
					"Transparency did not change the rendered framebuffer.");
		transparency = configured;
		renderCurrent();
		renderCurrent();
		System.out.println("GEOEDITOR_TRANSPARENCY=PASS,TRANSPARENT_CRC:"
				+ Long.toHexString(transparent) + ",OPAQUE_CRC:"
				+ Long.toHexString(opaque));
	}

	private void validateOutlineSmoke() {
		final boolean configured = drawOutline;
		drawOutline = false;
		renderCurrent();
		renderCurrent();
		final long plain = frameChecksum();
		drawOutline = true;
		renderCurrent();
		renderCurrent();
		final long outlined = frameChecksum();
		if (plain == outlined)
			throw new IllegalStateException(
					"Draw Outline did not change the rendered framebuffer.");
		drawOutline = configured;
		renderCurrent();
		renderCurrent();
		System.out.println("GEOEDITOR_DRAW_OUTLINE=PASS,PLAIN_CRC:"
				+ Long.toHexString(plain) + ",OUTLINE_CRC:"
				+ Long.toHexString(outlined));
	}

	private void validateRange96BenchmarkSmoke() {
		final int samples = 160;
		final long[] frameMicros = new long[samples];
		final int configuredRange = visibleGridRange;
		visibleGridRange = 96;
		final LwjglRenderBackend.GridStreamStats before = renderer.gridStreamStats();
		rangeBenchmarkRunning = true;
		try {
			for (int sample = 0; sample < samples; sample++) {
				camera.rotate(0f, 0.08f);
				camera.moveForward(sample % 32 < 16 ? 0.25d : -0.25d);
				final long started = System.nanoTime();
				renderCurrent();
				frameMicros[sample] = (System.nanoTime() - started) / 1_000L;
			}
		} finally {
			rangeBenchmarkRunning = false;
			visibleGridRange = configuredRange;
		}
		final LwjglRenderBackend.GridStreamStats after = renderer.gridStreamStats();
		final long[] sorted = frameMicros.clone();
		Arrays.sort(sorted);
		long total = 0L;
		for (final long micros : frameMicros)
			total += micros;
		final int p95Index = Math.min(samples - 1,
				Math.max(0, (int) Math.ceil(samples * 0.95d) - 1));
		System.out.println("GEOEDITOR_RANGE96=FRAMES:" + samples
				+ ",AVG_US:" + total / samples
				+ ",P95_US:" + sorted[p95Index]
				+ ",MAX_US:" + sorted[samples - 1]
				+ ",STREAM_REUSED:"
				+ (after.reusedFrames() - before.reusedFrames())
				+ ",STREAM_REBUILT:"
				+ (after.rebuiltFrames() - before.rebuiltFrames()));
	}

	private void validateGraphicsQualityBenchmarkSmoke() {
		final int configuredSamples = antialiasingSamples;
		final int configuredRange = visibleGridRange;
		visibleGridRange = 96;
		rangeBenchmarkRunning = true;
		try {
			for (final int requested : new int[] { 2, 4, 8, 16 }) {
				antialiasingSamples = requested;
				for (int warmup = 0; warmup < 5; warmup++)
					renderCurrent();
				final int samples = 60;
				final long[] timings = new long[samples];
				for (int sample = 0; sample < samples; sample++) {
					camera.rotate(0f, 0.06f);
					camera.moveSideways(sample % 20 < 10 ? 0.2d : -0.2d);
					final long started = System.nanoTime();
					renderCurrent();
					timings[sample] = (System.nanoTime() - started) / 1_000L;
				}
				final long[] sorted = timings.clone();
				Arrays.sort(sorted);
				long total = 0L;
				for (final long timing : timings)
					total += timing;
				System.out.println("GEOEDITOR_MSAA=REQUESTED:" + requested
						+ ",ACTUAL:" + renderer.actualFramebufferSamples()
						+ ",FRAMES:" + samples
						+ ",AVG_US:" + total / samples
						+ ",P95_US:" + sorted[56]
						+ ",MAX_US:" + sorted[samples - 1]
						+ ",CRC:" + Long.toHexString(frameChecksum()));
			}
		} finally {
			antialiasingSamples = configuredSamples;
			visibleGridRange = configuredRange;
			rangeBenchmarkRunning = false;
			renderCurrent();
			renderCurrent();
		}
	}

	private void compareSkyStylesSmoke() {
		final int configuredStyle = skyStyle;
		final int configuredRange = visibleGridRange;
		final float configuredPitch = camera.rotationX();
		final float configuredYaw = camera.rotationY();
		final File outputDirectory = new File(System.getProperty(
				"geoeditor.skyStyleOutput",
				"Build/diagnostics/sky-comparison"));
		if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs())
			throw new IllegalStateException("Unable to create sky style output directory.");
		final String[] names = { "Black", "Deep Navy", "Milky Way",
				"Cyan Horizon", "Moon" };
		final StringBuilder report = new StringBuilder("style,name,frames,avg_us,p95_us,max_us,crc\n");
		visibleGridRange = 96;
		camera.set(camera.x(), camera.y(), camera.z(), 32f, 51f);
		rangeBenchmarkRunning = true;
		try {
			for (int style = 0; style < names.length; style++) {
				skyStyle = style;
				renderer.setSkyStyle(style);
				if (style == 2)
					waitForSkyTextureSmoke();
				for (int warmup = 0; warmup < 6; warmup++)
					renderCurrent();
				final int samples = 80;
				final long[] timings = new long[samples];
				for (int sample = 0; sample < samples; sample++) {
					final long started = System.nanoTime();
					renderCurrent();
					timings[sample] = (System.nanoTime() - started) / 1_000L;
				}
				final long[] sorted = timings.clone();
				Arrays.sort(sorted);
				long total = 0L;
				for (final long timing : timings)
					total += timing;
				final long average = total / samples;
				final long p95 = sorted[75];
				final long maximum = sorted[samples - 1];
				final long crc = frameChecksum();
				writeFrameSmoke(new File(outputDirectory,
						String.format("sky-style-%02d.png", style)));
				report.append(style).append(',').append(names[style]).append(',')
						.append(samples).append(',').append(average).append(',')
						.append(p95).append(',').append(maximum).append(',')
						.append(Long.toHexString(crc)).append('\n');
				System.out.println("GEOEDITOR_SKY_STYLE=STYLE:" + style
						+ ",NAME:" + names[style] + ",FRAMES:" + samples
						+ ",AVG_US:" + average + ",P95_US:" + p95
						+ ",MAX_US:" + maximum + ",CRC:"
						+ Long.toHexString(crc));
			}
			java.nio.file.Files.writeString(new File(outputDirectory,
					"sky-benchmark.csv").toPath(), report,
					java.nio.charset.StandardCharsets.UTF_8);
		} catch (final IOException exception) {
			throw new IllegalStateException("Unable to write sky benchmark: "
					+ exception.getMessage(), exception);
		} finally {
			skyStyle = configuredStyle;
			renderer.setSkyStyle(configuredStyle);
			visibleGridRange = configuredRange;
			camera.set(camera.x(), camera.y(), camera.z(), configuredPitch,
					configuredYaw);
			rangeBenchmarkRunning = false;
			renderCurrent();
		}
	}

	private void validateSkyWorldLockSmoke() {
		final File outputDirectory = new File(System.getProperty(
				"geoeditor.skyMotionOutput",
				"Build/.staging/sky-world-lock"));
		if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs())
			throw new IllegalStateException("Unable to create sky world-lock output directory.");
		final int configuredStyle = skyStyle;
		final boolean configuredGrid = geodataGrid;
		final boolean configuredTextured = texturedTerrain;
		final Map<RegionLoadManager.RegionKey, RegionSnapshot> configuredRegions =
				visibleRegions;
		final float configuredX = camera.x();
		final float configuredY = camera.y();
		final float configuredZ = camera.z();
		final float configuredPitch = camera.rotationX();
		final float configuredYaw = camera.rotationY();
		try {
			geodataGrid = false;
			texturedTerrain = false;
			visibleRegions = Map.of(new RegionLoadManager.RegionKey(
					currentSnapshot.regionX(), currentSnapshot.regionY()), currentSnapshot);

			skyStyle = 0;
			renderer.setSkyStyle(skyStyle);
			renderCurrent();
			renderCurrent();
			if (renderer.isSkyTextureReady())
				throw new IllegalStateException(
						"Milky Way texture loaded while Black was selected.");

			skyStyle = 2;
			renderer.setSkyStyle(skyStyle);
			camera.set(configuredX, configuredY, configuredZ, 10f, 360f);
			waitForSkyTextureSmoke();
			renderCurrent();
			renderCurrent();
			writeFrameSmoke(new File(outputDirectory, "milky-way-default-view.png"));
			camera.set(configuredX, configuredY, configuredZ, 90f, 42f);
			renderCurrent();
			renderCurrent();
			writeFrameSmoke(new File(outputDirectory, "milky-way-zenith.png"));
			camera.set(configuredX, configuredY, configuredZ, 10f, 42f);
			renderCurrent();
			renderCurrent();
			final long starsPositionA = frameChecksum();
			writeFrameSmoke(new File(outputDirectory, "milky-way-world-locked.png"));
			camera.set(configuredX, configuredY + 4096f, configuredZ, 48f, 42f);
			renderCurrent();
			renderCurrent();
			writeFrameSmoke(new File(outputDirectory, "milky-way-high-camera.png"));
			camera.set(configuredX + 768f, configuredY + 256f,
					configuredZ - 512f, 10f, 42f);
			renderCurrent();
			renderCurrent();
			final long starsPositionB = frameChecksum();
			camera.set(configuredX + 768f, configuredY + 256f,
					configuredZ - 512f, 10f, 57f);
			renderCurrent();
			renderCurrent();
			final long starsRotated = frameChecksum();
			if (starsPositionA != starsPositionB || starsPositionA == starsRotated)
				throw new IllegalStateException("Milky Way is not locked to world direction.");

			skyStyle = 4;
			renderer.setSkyStyle(skyStyle);
			camera.set(configuredX, configuredY, configuredZ, 32f, 51f);
			renderCurrent();
			renderCurrent();
			final long moonPositionA = frameChecksum();
			writeFrameSmoke(new File(outputDirectory, "moon-world-locked.png"));
			camera.set(configuredX - 640f, configuredY + 128f,
					configuredZ + 384f, 32f, 51f);
			renderCurrent();
			renderCurrent();
			final long moonPositionB = frameChecksum();
			camera.set(configuredX - 640f, configuredY + 128f,
					configuredZ + 384f, 32f, 63f);
			renderCurrent();
			renderCurrent();
			final long moonRotated = frameChecksum();
			if (moonPositionA != moonPositionB || moonPositionA == moonRotated)
				throw new IllegalStateException("Moon is not locked to world direction.");

			skyStyle = 0;
			renderer.setSkyStyle(skyStyle);
			renderCurrent();
			renderCurrent();
			if (renderer.isSkyTextureReady())
				throw new IllegalStateException(
						"Milky Way texture remained resident after leaving the style.");
			System.out.println("GEOEDITOR_SKY_WORLD_LOCK=PASS,MILKY_WAY_STATIC_CRC:"
					+ Long.toHexString(starsPositionA) + ",MILKY_WAY_ROTATED_CRC:"
					+ Long.toHexString(starsRotated) + ",MOON_STATIC_CRC:"
					+ Long.toHexString(moonPositionA) + ",MOON_ROTATED_CRC:"
					+ Long.toHexString(moonRotated)
					+ ",MILKY_WAY_DEMAND_LOAD:PASS");
		} finally {
			skyStyle = configuredStyle;
			renderer.setSkyStyle(configuredStyle);
			geodataGrid = configuredGrid;
			texturedTerrain = configuredTextured;
			visibleRegions = configuredRegions;
			camera.set(configuredX, configuredY, configuredZ, configuredPitch,
					configuredYaw);
			renderCurrent();
		}
	}

	private void waitForSkyTextureSmoke() {
		final long deadline = System.nanoTime() + 240_000_000_000L;
		while (!renderer.isSkyTextureReady() && System.nanoTime() < deadline) {
			renderCurrent();
			try {
				Thread.sleep(50L);
			} catch (final InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(
						"Interrupted while waiting for the Milky Way texture.",
						exception);
			}
		}
		if (!renderer.isSkyTextureReady())
			throw new IllegalStateException(
					"Milky Way 16K texture was not ready within 240 seconds.");
	}

	private void compareNeighborStylesSmoke() {
		if (neighborStyleComparisonTriggered || visibleRegions.size() <= 1)
			return;
		neighborStyleComparisonTriggered = true;
		final int configuredStyle = neighborVisualStyle;
		final File outputDirectory = new File(System.getProperty(
				"geoeditor.neighborStyleOutput", "Build/.staging/neighbor-styles"));
		if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs())
			throw new IllegalStateException("Unable to create neighbor style output directory.");
		rangeBenchmarkRunning = true;
		try {
			for (int style = 1; style <= 10; style++) {
				neighborVisualStyle = style;
				renderer.setNeighborVisualStyle(style);
				for (int warmup = 0; warmup < 3; warmup++)
					renderCurrent();
				final int samples = 20;
				final long[] timings = new long[samples];
				for (int sample = 0; sample < samples; sample++) {
					final long started = System.nanoTime();
					renderCurrent();
					timings[sample] = (System.nanoTime() - started) / 1_000L;
				}
				final long[] sorted = timings.clone();
				Arrays.sort(sorted);
				long total = 0L;
				for (final long timing : timings)
					total += timing;
				writeFrameSmoke(new File(outputDirectory,
						String.format("neighbor-style-%02d.png", style)));
				System.out.println("GEOEDITOR_NEIGHBOR_STYLE=STYLE:" + style
						+ ",FRAMES:" + samples + ",AVG_US:" + total / samples
						+ ",P95_US:" + sorted[18] + ",MAX_US:"
						+ sorted[samples - 1] + ",CRC:"
						+ Long.toHexString(frameChecksum()));
			}
		} finally {
			neighborVisualStyle = configuredStyle;
			renderer.setNeighborVisualStyle(configuredStyle);
			rangeBenchmarkRunning = false;
			renderCurrent();
		}
	}

	private long frameChecksum() {
		if (currentFrame == null)
			return 0L;
		final CRC32 checksum = new CRC32();
		checksum.update(currentFrame.bgra());
		return checksum.getValue();
	}

	private void loadLegacyRenderPreferences() {
		EditorSettings.COLOR_GUI_SELECTED = preferences.getSharedInt(
				"COLOR_GUI_SELECTED", EditorSettings.COLOR_GUI_SELECTED);
		EditorSettings.COLOR_FLAT_NORMAL = preferences.getSharedInt(
				"COLOR_FLAT_NORMAL", EditorSettings.COLOR_FLAT_NORMAL);
		EditorSettings.COLOR_FLAT_HIGHLIGHTED = preferences.getSharedInt(
				"COLOR_FLAT_HIGHLIGHTED", EditorSettings.COLOR_FLAT_HIGHLIGHTED);
		EditorSettings.COLOR_FLAT_SELECTED = preferences.getSharedInt(
				"COLOR_FLAT_SELECTED", EditorSettings.COLOR_FLAT_SELECTED);
		EditorSettings.COLOR_COMPLEX_NORMAL = preferences.getSharedInt(
				"COLOR_COMPLEX_NORMAL", EditorSettings.COLOR_COMPLEX_NORMAL);
		EditorSettings.COLOR_COMPLEX_HIGHLIGHTED = preferences.getSharedInt(
				"COLOR_COMPLEX_HIGHLIGHTED", EditorSettings.COLOR_COMPLEX_HIGHLIGHTED);
		EditorSettings.COLOR_COMPLEX_SELECTED = preferences.getSharedInt(
				"COLOR_COMPLEX_SELECTED", EditorSettings.COLOR_COMPLEX_SELECTED);
		EditorSettings.COLOR_MULTILAYER_NORMAL = preferences.getSharedInt(
				"COLOR_MULTILAYER_NORMAL", EditorSettings.COLOR_MULTILAYER_NORMAL);
		EditorSettings.COLOR_MULTILAYER_HIGHLIGHTED = preferences.getSharedInt(
				"COLOR_MULTILAYER_HIGHLIGHTED", EditorSettings.COLOR_MULTILAYER_HIGHLIGHTED);
		EditorSettings.COLOR_MULTILAYER_SELECTED = preferences.getSharedInt(
				"COLOR_MULTILAYER_SELECTED", EditorSettings.COLOR_MULTILAYER_SELECTED);
		EditorSettings.COLOR_MULTILAYER_NORMAL_SPECIAL = preferences.getSharedInt(
				"COLOR_MULTILAYER_NORMAL_SPECIAL", EditorSettings.COLOR_MULTILAYER_NORMAL_SPECIAL);
		EditorSettings.COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL = preferences.getSharedInt(
				"COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL",
				EditorSettings.COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL);
		EditorSettings.COLOR_MULTILAYER_SELECTED_SPECIAL = preferences.getSharedInt(
				"COLOR_MULTILAYER_SELECTED_SPECIAL",
				EditorSettings.COLOR_MULTILAYER_SELECTED_SPECIAL);
		EditorSettings.NSWE_TEXTURE_ID = preferences.getSharedInt(
				"NSWE_TEXTURE_ID", EditorSettings.NSWE_TEXTURE_ID);
		EditorSettings.SELECTION_MOVE_MAX_Z = EditorSettings.normalizeSelectionMoveMaxZ(
				preferences.getSharedInt("SELECTION_MOVE_MAX_Z",
						EditorSettings.SELECTION_MOVE_MAX_Z));
		EditorSettings.SELECTION_HEIGHT_STEP = normalizeHeightStep(
				preferences.getSharedInt("SELECTION_HEIGHT_STEP",
						EditorSettings.SELECTION_HEIGHT_STEP));
	}

	private ToolBar createToolbar() {
		final Button settings = toolbarButton("config.png", "Settings");
		settings.setOnAction(event -> showSettings());
		final Button selectMap = toolbarButton("search.png", "Select map");
		selectMap.setOnAction(event -> selectMap());
		final Button open = toolbarButton("open.png", "Open geodata file");
		open.setOnAction(event -> openRegion());
		saveButton = toolbarButton("save.png", "Save active region");
		saveButton.setDisable(true);
		saveButton.setOnAction(event -> saveRegion());
		undoButton = new Button("Undo");
		undoButton.setDisable(true);
		undoButton.setOnAction(event -> undoEdit());
		redoButton = new Button("Redo");
		redoButton.setDisable(true);
		redoButton.setOnAction(event -> redoEdit());
		clearHistoryButton = new Button("Clear History");
		clearHistoryButton.setDisable(true);
		clearHistoryButton.setOnAction(event -> clearSavedHistory());
		final Button about = toolbarButton("help.png", "About L2GeoEditor");
		about.setOnAction(event -> showAbout());
		final Button theme = toolbarButton("theme-toggle.png", "Toggle theme");
		theme.setOnAction(event -> {
			darkTheme = !darkTheme;
			applyTheme();
			savePreferences();
		});
		final ImageView logo = iconView("l2jserverlogo.png", 124, 32);
		editState.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
		historyState.setStyle("-fx-font-size: 10px; -fx-font-family: monospace;");
		final HBox centered = new HBox(6, settings, selectMap, open,
				logo, saveButton, about, theme);
		centered.setAlignment(Pos.CENTER);
		centered.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(centered, Priority.ALWAYS);
		mainToolbar = new ToolBar(centered);
		mainToolbar.setMinHeight(48);
		mainToolbar.setPrefHeight(48);
		mainToolbar.setMaxHeight(48);
		return mainToolbar;
	}

	private Button toolbarButton(final String icon, final String tooltip) {
		final Button button = new Button();
		button.setFocusTraversable(false);
		button.setGraphic(iconView(icon, 32, 32));
		button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
		button.setMinSize(54, 38);
		button.setPrefSize(54, 38);
		button.setMaxSize(54, 38);
		return button;
	}

	private ImageView iconView(final String fileName,
			final double width, final double height) {
		final File file = new File(dataRoot, "icon/" + fileName);
		final ImageView image = new ImageView(new Image(file.toURI().toString(),
				width, height, true, true, true));
		image.setFitWidth(width);
		image.setFitHeight(height);
		image.setPreserveRatio(true);
		return image;
	}

	private StackPane createViewport() {
		viewportImage.setPreserveRatio(false);
		updateMiniMapSize(miniMapSize);
		miniMapImage.setPreserveRatio(false);
		final File markerFile = new File(dataRoot, "icon/minimap-player-marker.png");
		if (markerFile.isFile())
			miniMapMarker.setImage(new Image(markerFile.toURI().toString(),
					18, 22, true, true, true));
		miniMapMarker.setFitWidth(18);
		miniMapMarker.setFitHeight(22);
		/* Legacy HUD deliberately fits the marker into an 18x22 quad. */
		miniMapMarker.setPreserveRatio(false);
		miniMapMarker.setMouseTransparent(true);
		miniMap.setStyle("-fx-border-color: #5abeff; -fx-border-width: 1; "
				+ "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 5, 0.25, 1, 1);");
		miniMapPreviousImage.setMouseTransparent(true);
		miniMapImage.setMouseTransparent(true);
		miniMap.getChildren().setAll(miniMapPreviousImage, miniMapImage, miniMapMarker);
		miniMap.setOnMousePressed(event -> {
			if (event.getX() >= miniMapSize - 18 && event.getY() >= miniMapSize - 18) {
				miniMapResizing = true;
				miniMapResizeStartX = event.getScreenX();
				miniMapResizeStartY = event.getScreenY();
				miniMapResizeStartSize = miniMapSize;
				event.consume();
			}
		});
		miniMap.setOnMouseDragged(event -> {
			if (!miniMapResizing)
				return;
			final double delta = Math.max(event.getScreenX() - miniMapResizeStartX,
					event.getScreenY() - miniMapResizeStartY);
			updateMiniMapSize((int) Math.round(miniMapResizeStartSize + delta));
			event.consume();
		});
		miniMap.setOnMouseReleased(event -> {
			if (!miniMapResizing)
				return;
			miniMapResizing = false;
			preferences.putShared("MINI_MAP_SIZE", miniMapSize);
			preferences.save();
			event.consume();
		});

		overlay.setStyle("-fx-text-fill: #ffff00; -fx-background-color: transparent; "
				+ "-fx-padding: 0 0 0 4; -fx-font-family: Arial; "
				+ "-fx-font-size: 12px; -fx-font-weight: normal;");
		mapHud.setSpacing(0);
		mapHud.getChildren().setAll(miniMap, overlay);
		mapHud.setPadding(Insets.EMPTY);
		setMiniMapActive(false);
		StackPane.setAlignment(mapHud, Pos.TOP_LEFT);
		metricsHud.setStyle("-fx-text-fill: #ffffff; -fx-padding: 0 0 8 10; "
				+ "-fx-font-family: Consolas; -fx-font-size: 12px; "
				+ "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.95), 3, 0.8, 1, 1);");
		StackPane.setAlignment(metricsHud, Pos.BOTTOM_LEFT);
		StackPane.setMargin(metricsHud, new Insets(0, 0, 8, 8));
		transitionOverlay.setStyle("-fx-text-fill: white; -fx-font-size: 16px; "
				+ "-fx-font-weight: bold; -fx-background-color: rgba(8,16,22,0.88); "
				+ "-fx-border-color: #37c8ff; -fx-border-radius: 5; "
				+ "-fx-background-radius: 5; -fx-padding: 14 22;");
		transitionOverlay.setVisible(false);
		transitionOverlay.setManaged(false);
		transitionOverlay.setMouseTransparent(true);
		StackPane.setAlignment(transitionOverlay, Pos.CENTER);
		neighborLoadingTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; "
				+ "-fx-text-fill: white;");
		neighborLoadingPercent.setStyle("-fx-font-size: 23px; -fx-font-weight: bold; "
				+ "-fx-text-fill: #19d7ff;");
		neighborLoadingProgress.setPrefWidth(334);
		neighborLoadingCount.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; "
				+ "-fx-text-fill: #e9f7ff; -fx-effect: dropshadow(gaussian, #001018, 2, 0.7, 0, 1);");
		neighborLoadingPhase.setStyle("-fx-font-size: 12px; -fx-text-fill: #7fe9ff; "
				+ "-fx-effect: dropshadow(gaussian, #001018, 2, 0.7, 0, 1);");
		final VBox loadingContent = new VBox(7, neighborLoadingTitle,
				neighborLoadingPercent, neighborLoadingProgress,
				neighborLoadingCount, neighborLoadingPhase);
		loadingContent.setAlignment(Pos.CENTER);
		loadingContent.setPadding(new Insets(26));
		final StackPane loadingCard = new StackPane();
		loadingCard.setMaxSize(430, 250);
		final File loadingImageFile = new File(dataRoot,
				"image/neighbor-loading-background.png");
		if (loadingImageFile.isFile()) {
			final ImageView loadingImage = new ImageView(new Image(
					loadingImageFile.toURI().toString(), 430, 250, false, true, true));
			loadingImage.setFitWidth(430); loadingImage.setFitHeight(250);
			loadingCard.getChildren().add(loadingImage);
		}
		loadingCard.getChildren().add(loadingContent);
		loadingCard.setStyle("-fx-background-color: rgba(7,21,29,0.56); "
				+ "-fx-border-color: #286074; -fx-border-radius: 10; "
				+ "-fx-background-radius: 10;");
		neighborLoadingOverlay.getChildren().setAll(loadingCard);
		neighborLoadingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.58);");
		neighborLoadingOverlay.setVisible(false);
		neighborLoadingOverlay.setManaged(false);

		viewport.getChildren().setAll(viewportImage, mapHud, metricsHud,
				transitionOverlay, neighborLoadingOverlay);
		viewport.setFocusTraversable(true);
		viewportImage.fitWidthProperty().bind(viewport.widthProperty());
		viewportImage.fitHeightProperty().bind(viewport.heightProperty());
		viewport.widthProperty().addListener((observable, oldValue, newValue) ->
				scheduleViewportResizeRender());
		viewport.heightProperty().addListener((observable, oldValue, newValue) ->
				scheduleViewportResizeRender());
		viewport.setStyle("-fx-background-color: #02070a;");
		viewport.setOnMousePressed(event -> {
			viewport.requestFocus();
			dragX = event.getX();
			dragY = event.getY();
			cameraDragged = false;
			if (event.getButton() == MouseButton.SECONDARY)
				captureCameraPointer(event.getScreenX(), event.getScreenY());
			cellSelectionGesture = event.getButton() == MouseButton.PRIMARY;
			selectionGestureFullBlock = cellSelectionGesture && event.isAltDown();
			selectionGestureAppend = cellSelectionGesture && event.isShiftDown();
			gestureSelections.clear();
			if (cellSelectionGesture)
				selectBlockAt(event.getX(), event.getY(), viewport.getWidth(),
						viewport.getHeight(), selectionGestureFullBlock,
						selectionGestureAppend, false, false);
		});
		viewport.setOnMouseDragged(event -> {
			final double deltaX = cameraPointerCaptured
					? event.getScreenX() - cameraPointerAnchorX
					: event.getX() - dragX;
			final double deltaY = cameraPointerCaptured
					? event.getScreenY() - cameraPointerAnchorY
					: event.getY() - dragY;
			if (!cameraPointerCaptured) {
				dragX = event.getX();
				dragY = event.getY();
			}
			if (Math.abs(deltaX) + Math.abs(deltaY) < 0.5)
				return;
			if (event.isSecondaryButtonDown()) {
				cameraDragged = true;
				camera.rotate((float) deltaX, (float) deltaY);
				cameraRenderPending = true;
				if (cameraPointerCaptured)
					cameraPointerRobot.mouseMove(cameraPointerAnchorX,
							cameraPointerAnchorY);
			} else if (event.isPrimaryButtonDown() && cellSelectionGesture
					&& selectionGestureAppend) {
				selectBlockAt(event.getX(), event.getY(), viewport.getWidth(),
						viewport.getHeight(), selectionGestureFullBlock,
						true, true, true);
			}
		});
		viewport.setOnMouseClicked(event -> {
			/* Primary selection is handled on press so drag never re-toggles its
			 * first or last cell when JavaFX emits the final click event. */
		});
		viewport.setOnMouseReleased(event -> {
			if (event.getButton() == MouseButton.SECONDARY) {
				releaseCameraPointer();
				if (cameraDragged && currentSnapshot != null)
					renderCurrent();
			}
			if (event.getButton() == MouseButton.PRIMARY) {
				finishCellSelectionGesture();
			}
		});
		viewport.setOnScroll(event -> {
			viewport.requestFocus();
			if (!event.isControlDown() || selectedCellX < 0)
				return;
			final int direction = event.getDeltaY() > 0d ? 1 : -1;
			final int step = pressedActions.contains(InputBindings.Action.MOVE_FASTER)
					? 32 : EditorSettings.SELECTION_HEIGHT_STEP;
			adjustSelectedHeight(direction * step);
			event.consume();
		});
		return viewport;
	}

	private void captureCameraPointer(final double screenX, final double screenY) {
		cameraPointerAnchorX = screenX;
		cameraPointerAnchorY = screenY;
		try {
			if (cameraPointerRobot == null)
				cameraPointerRobot = new Robot();
			cameraPointerCaptured = true;
			viewport.setCursor(Cursor.NONE);
		} catch (final RuntimeException exception) {
			cameraPointerCaptured = false;
			viewport.setCursor(Cursor.DEFAULT);
		}
	}

	private void releaseCameraPointer() {
		cameraPointerCaptured = false;
		viewport.setCursor(Cursor.DEFAULT);
	}

	private void toggleSelectedBlockMenu(final double localX, final double localY) {
		if (selectedBlockMenu != null && selectedBlockMenu.isShowing()) {
			selectedBlockMenu.hide();
			return;
		}
		if (selectedBlockX < 0)
			return;
		final Label title = uiLabel("Selected blocks (" + selectedBlockCount() + ")");
		title.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
		final Label hint = uiLabel("Alt + 1");
		hint.setStyle("-fx-font-size: 10px; -fx-text-fill: #82909a;");
		final CustomMenuItem header = new CustomMenuItem(new VBox(2, title, hint), false);
		final Menu convert = new Menu("Convert block type");
		convert.getItems().addAll(menuAction("Flat", convertFlatButton),
				menuAction("Complex", convertComplexButton),
				menuAction("MultiLayer", convertMultiButton));
		final MenuItem addLayer = new MenuItem("Add Layer...");
		addLayer.setDisable(currentSession == null || selectedCellX < 0
				|| currentSnapshot.blockType(selectedBlockX, selectedBlockY)
						!= com.geo.GeoEngine.GEO_BLOCK_TYPE_MULTILAYER);
		addLayer.setOnAction(event -> showAddLayerDialog());
		final MenuItem removeLayer = new MenuItem("Remove Layer...");
		removeLayer.setDisable(addLayer.isDisable());
		removeLayer.setOnAction(event -> showRemoveLayerDialog());
		final ContextMenu menu = new ContextMenu(header, new SeparatorMenuItem(),
				convert, addLayer, removeLayer, new SeparatorMenuItem(),
				menuAction("Restore from base", restoreBlockButton));
		selectedBlockMenu = menu;
		menu.setOnHidden(event -> {
			if (selectedBlockMenu == menu)
				selectedBlockMenu = null;
		});
		final javafx.geometry.Point2D screen = viewport.localToScreen(localX, localY);
		if (screen != null)
			menu.show(viewport, screen.getX(), screen.getY());
	}

	private void showAddLayerDialog() {
		if (currentSession == null || selectedCellX < 0)
			return;
		final Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Add Layer(s)");
		dialog.initOwner(stage);
		final CheckBox fullBlock = new CheckBox("Apply to full block");
		final CheckBox allowDuplicate = new CheckBox("Add if already exists");
		final TextField height = new TextField(heightValue.getText());
		final GridPane content = new GridPane();
		content.setHgap(8); content.setVgap(7); content.setPadding(new Insets(12));
		content.add(fullBlock, 0, 0, 2, 1);
		content.add(allowDuplicate, 0, 1, 2, 1);
		content.add(uiLabel("Height:"), 0, 2); content.add(height, 1, 2);
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		applyTheme(dialog.getDialogPane());
		dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> {
			try {
				int value = Integer.parseInt(height.getText().trim());
				if (value < com.geo.GeoEngine.HEIGHT_MIN_VALUE
						|| value > com.geo.GeoEngine.HEIGHT_MAX_VALUE)
					throw new NumberFormatException();
				value -= Math.floorMod(value, 8);
				final int[][] historyBlocks = selectedBlockCoordinates();
				final EditableRegionSession.BlockCheckpoint before =
						currentSession.captureBlocks(historyBlocks[0], historyBlocks[1]);
				final int[][] coordinates = selectedMultilayerCoordinates();
				final int added = currentSession.addLayers(coordinates[0], coordinates[1],
						(short) value, fullBlock.isSelected(), allowDuplicate.isSelected());
				refreshFromEditableSession();
				updateSelectedCellDetails(selectedCellX, selectedCellY);
				if (added > 0)
					recordEdit("Add layer", before, historyBlocks);
				status.setText("Added " + added + " layer(s) | unsaved changes");
			} catch (final NumberFormatException exception) {
				status.setText("Height must be between " + com.geo.GeoEngine.HEIGHT_MIN_VALUE
						+ " and " + com.geo.GeoEngine.HEIGHT_MAX_VALUE + ".");
			}
		});
	}

	private void showRemoveLayerDialog() {
		if (currentSession == null || selectedCellX < 0 || selectedLayer < 0)
			return;
		final Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Remove Layer(s)");
		dialog.initOwner(stage);
		final ChoiceBox<String> mode = new ChoiceBox<>(FXCollections.observableArrayList(
				"Remove selected", "Remove layer(s)", "Remove height(s)"));
		mode.getSelectionModel().selectFirst();
		final CheckBox fullBlock = new CheckBox("Remove from full block");
		final TextField minimum = new TextField(Integer.toString(selectedLayer));
		final TextField maximum = new TextField(Integer.toString(selectedLayer));
		final GridPane content = new GridPane();
		content.setHgap(8); content.setVgap(7); content.setPadding(new Insets(12));
		content.add(uiLabel("Mode:"), 0, 0); content.add(mode, 1, 0);
		content.add(fullBlock, 0, 1, 2, 1);
		content.add(uiLabel("Min:"), 0, 2); content.add(minimum, 1, 2);
		content.add(uiLabel("Max:"), 0, 3); content.add(maximum, 1, 3);
		final Runnable updateMode = () -> {
			final boolean selectedOnly = mode.getSelectionModel().getSelectedIndex() == 0;
			fullBlock.setDisable(selectedOnly);
			minimum.setDisable(selectedOnly); maximum.setDisable(selectedOnly);
		};
		mode.getSelectionModel().selectedIndexProperty().addListener(
				(observable, oldValue, newValue) -> updateMode.run());
		updateMode.run();
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		applyTheme(dialog.getDialogPane());
		dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> {
			try {
				final int selectedMode = mode.getSelectionModel().getSelectedIndex();
				final int min = selectedMode == 0 ? selectedLayer
						: Integer.parseInt(minimum.getText().trim());
				final int max = selectedMode == 0 ? selectedLayer
						: Integer.parseInt(maximum.getText().trim());
				if (min > max)
					throw new NumberFormatException();
				final int[][] historyBlocks = selectedBlockCoordinates();
				final EditableRegionSession.BlockCheckpoint before =
						currentSession.captureBlocks(historyBlocks[0], historyBlocks[1]);
				final int[][] coordinates = selectedMultilayerCoordinates();
				final int removed = selectedMode == 0
						? currentSession.removeSelectedLayers(coordinates[0], coordinates[1],
								selectedMultilayerLayers())
						: currentSession.removeLayers(coordinates[0], coordinates[1],
								selectedMode, min, max, fullBlock.isSelected());
				refreshFromEditableSession();
				updateSelectedCellDetails(selectedCellX, selectedCellY);
				if (removed > 0)
					recordEdit("Remove layer", before, historyBlocks);
				status.setText("Removed " + removed + " layer(s) | unsaved changes");
			} catch (final NumberFormatException exception) {
				status.setText("Invalid layer or height range.");
			}
		});
	}

	private static MenuItem menuAction(final String text, final Button action) {
		final MenuItem item = new MenuItem(text);
		item.setDisable(action.isDisabled());
		item.setOnAction(event -> action.fire());
		return item;
	}

	private int[][] selectedMultilayerCoordinates() {
		final List<CellSelection> multilayer = selectedCells.stream()
				.filter(selection -> currentSnapshot.blockType(
						selection.blockX(), selection.blockY())
						== com.geo.GeoEngine.GEO_BLOCK_TYPE_MULTILAYER)
				.toList();
		if (multilayer.isEmpty())
			return new int[][] {
				{ selectedBlockX * 8 + selectedCellX },
				{ selectedBlockY * 8 + selectedCellY }
			};
		final int[] xs = new int[multilayer.size()];
		final int[] ys = new int[multilayer.size()];
		for (int i = 0; i < multilayer.size(); i++) {
			xs[i] = multilayer.get(i).blockX() * 8 + multilayer.get(i).cellX();
			ys[i] = multilayer.get(i).blockY() * 8 + multilayer.get(i).cellY();
		}
		return new int[][] { xs, ys };
	}

	private int[] selectedMultilayerLayers() {
		final int[] layers = selectedCells.stream()
				.filter(selection -> currentSnapshot.blockType(
						selection.blockX(), selection.blockY())
						== com.geo.GeoEngine.GEO_BLOCK_TYPE_MULTILAYER)
				.mapToInt(CellSelection::layer).toArray();
		return layers.length == 0 ? new int[] { selectedLayer } : layers;
	}

	private void updateMiniMapSize(final int requestedSize) {
		miniMapSize = Math.max(96, Math.min(320, requestedSize));
		miniMapImage.setFitWidth(miniMapSize);
		miniMapImage.setFitHeight(miniMapSize);
		miniMapPreviousImage.setFitWidth(miniMapSize);
		miniMapPreviousImage.setFitHeight(miniMapSize);
		miniMap.setMinSize(miniMapSize, miniMapSize);
		miniMap.setPrefSize(miniMapSize, miniMapSize);
		miniMap.setMaxSize(miniMapSize, miniMapSize);
		updateMiniMapMarker();
	}

	private void setMiniMapActive(final boolean active) {
		mapHud.setVisible(active);
		mapHud.setManaged(active);
		miniMap.setDisable(!active);
	}

	private void fadeOutMiniMap() {
		if (!mapHud.isVisible())
			return;
		if (miniMapFade != null)
			miniMapFade.stop();
		final FadeTransition fade = new FadeTransition(Duration.millis(220), mapHud);
		fade.setFromValue(mapHud.getOpacity());
		fade.setToValue(0.45d);
		miniMapFade = fade;
		miniMapFade.play();
	}

	private void showMiniMapForRegion(final File texture) {
		if (miniMapFade != null)
			miniMapFade.stop();
		final Image previous = miniMapImage.getImage();
		miniMapPreviousImage.setImage(previous);
		miniMapPreviousImage.setOpacity(previous == null ? 0d : 1d);
		setMiniMapActive(true);
		final long generation = ++miniMapLoadGeneration;
		if (texture == null || !texture.isFile()) {
			startMiniMapCrossFade(null, generation);
			return;
		}
		final Image next = new Image(texture.toURI().toString(), miniMapSize,
				miniMapSize, false, true, true);
		if (next.getProgress() >= 1d) {
			startMiniMapCrossFade(next, generation);
		} else {
			next.progressProperty().addListener((observable, oldValue, newValue) -> {
				if (newValue.doubleValue() >= 1d)
					startMiniMapCrossFade(next, generation);
			});
		}
	}

	private void startMiniMapCrossFade(final Image next, final long generation) {
		if (generation != miniMapLoadGeneration)
			return;
		miniMapImage.setImage(next);
		miniMapImage.setOpacity(0d);
		final FadeTransition oldMapFade = new FadeTransition(
				Duration.millis(420), miniMapPreviousImage);
		oldMapFade.setFromValue(miniMapPreviousImage.getOpacity());
		oldMapFade.setToValue(0d);
		final FadeTransition newMapFade = new FadeTransition(
				Duration.millis(420), miniMapImage);
		newMapFade.setFromValue(0d);
		newMapFade.setToValue(1d);
		final FadeTransition hudFade = new FadeTransition(
				Duration.millis(320), mapHud);
		hudFade.setFromValue(mapHud.getOpacity());
		hudFade.setToValue(1d);
		miniMapFade = new ParallelTransition(oldMapFade, newMapFade, hudFade);
		miniMapFade.setOnFinished(event -> miniMapPreviousImage.setImage(null));
		miniMapFade.play();
	}

	private void finishRegionVisualTransition() {
		regionVisualTransitionPending = false;
		hideTransitionOverlay();
	}

	private void showTransitionOverlay(final String message, final boolean error) {
		transitionOverlay.setText(message);
		transitionOverlay.setStyle("-fx-text-fill: white; -fx-font-size: 16px; "
				+ "-fx-font-weight: bold; -fx-background-color: rgba(8,16,22,0.88); "
				+ "-fx-border-color: " + (error ? "#ff625c" : "#37c8ff") + "; "
				+ "-fx-border-radius: 5; -fx-background-radius: 5; "
				+ "-fx-padding: 14 22;");
		transitionOverlay.setManaged(true);
		transitionOverlay.setVisible(true);
		if (error) {
			final PauseTransition hide = new PauseTransition(Duration.seconds(3));
			hide.setOnFinished(event -> hideTransitionOverlay());
			hide.play();
		}
	}

	private void hideTransitionOverlay() {
		transitionOverlay.setVisible(false);
		transitionOverlay.setManaged(false);
	}

	private void showNeighborLoading(final int total, final String phase) {
		streamingUiBlocked = true;
		if (editorUiRoot != null)
			editorUiRoot.setMouseTransparent(true);
		updateNeighborLoading(0, Math.max(1, total), phase);
		neighborLoadingOverlay.setOpacity(1d);
		neighborLoadingOverlay.setManaged(true);
		neighborLoadingOverlay.setVisible(true);
	}

	private void updateNeighborLoading(final int completed, final int total,
			final String phase) {
		final int safeTotal = Math.max(1, total);
		final int safeCompleted = Math.max(0, Math.min(safeTotal, completed));
		final int percent = (int) Math.round(safeCompleted * 100d / safeTotal);
		final String normalizedPhase = phase == null ? "" : phase.toLowerCase();
		final boolean skyTexture = normalizedPhase.contains("sky")
				|| normalizedPhase.contains("milky way");
		final boolean textures = skyTexture || normalizedPhase.contains("texture");
		neighborLoadingTitle.setText(skyTexture
				? "Loading high-resolution sky"
				: textures ? "Loading terrain textures"
				: "Loading neighboring maps");
		neighborLoadingProgress.setProgress(safeCompleted / (double) safeTotal);
		neighborLoadingPercent.setText(percent + "%");
		neighborLoadingCount.setText(safeCompleted + " / " + safeTotal
				+ (skyTexture ? " texture" : textures ? " textures" : " maps"));
		neighborLoadingPhase.setText(phase == null ? "Preparing geodata" : phase);
		if (Boolean.getBoolean("geoeditor.loadingSequenceSmoke")) {
			final String smoke = (skyTexture ? "SKY" : textures ? "TEXTURES" : "MAPS")
					+ ":" + safeCompleted + "/" + safeTotal;
			if (!smoke.equals(loadingSequenceSmokeLast)) {
				loadingSequenceSmokeLast = smoke;
				System.out.println("GEOEDITOR_LOADING_SEQUENCE=" + smoke);
			}
		}
	}

	private void hideNeighborLoading() {
		final FadeTransition fade = new FadeTransition(
				Duration.millis(350), neighborLoadingOverlay);
		fade.setFromValue(1d);
		fade.setToValue(0d);
		fade.setOnFinished(event -> {
			neighborLoadingOverlay.setVisible(false);
			neighborLoadingOverlay.setManaged(false);
			streamingUiBlocked = false;
			if (editorUiRoot != null)
				editorUiRoot.setMouseTransparent(false);
			viewport.requestFocus();
		});
		fade.play();
	}

	private void updateMiniMapMarker() {
		if (currentSnapshot == null)
			return;
		final int blockX = Math.max(0, Math.min(255, (int) camera.x() >> 3));
		final int blockY = Math.max(0, Math.min(255, (int) camera.z() >> 3));
		miniMapMarker.setTranslateX((blockX / 256f - 0.5f) * miniMapSize);
		miniMapMarker.setTranslateY((blockY / 256f - 0.5f) * miniMapSize);
		/*
		 * JavaFX uses a downward Y axis, so its positive rotation is clockwise.
		 * The legacy OpenGL marker is counter-clockwise for the same camera yaw.
		 */
		miniMapMarker.setRotate(-camera.rotationY());
	}

	private VBox createSidebar() {
		final GridPane coordinates = new GridPane();
		coordinates.setHgap(6);
		coordinates.setVgap(4);
		coordinates.addRow(0, uiLabel("Region X/Y:"),
				coordinatePair(regionXValue, regionYValue));
		coordinates.addRow(1, uiLabel("World X/Y:"),
				coordinatePair(worldXValue, worldYValue));
		coordinates.addRow(2, uiLabel("Geo X/Y:"),
				coordinatePair(geoXValue, geoYValue));
		coordinates.addRow(3, uiLabel("Block X/Y:"),
				coordinatePair(blockXValue, blockYValue));
		coordinates.addRow(4, uiLabel("Cell X/Y:"),
				coordinatePair(cellXValue, cellYValue));
		coordinates.addRow(5, uiLabel("Type/Layers:"),
				coordinatePair(typeValue, layersValue));
		heightEditor.setPrefColumnCount(8);
		heightEditor.setDisable(true);
		heightEditor.setOnAction(event -> applyHeightEdit());
		coordinates.addRow(6, uiLabel("Height:"), heightEditor);

		convertFlatButton.setOnAction(event -> convertSelectedBlock((byte) 0));
		convertComplexButton.setOnAction(event -> convertSelectedBlock((byte) 1));
		convertMultiButton.setOnAction(event -> convertSelectedBlock((byte) 2));
		restoreBlockButton.setOnAction(event -> restoreSelectedBlock());
		final HBox conversions = new HBox(4,
				convertFlatButton, convertComplexButton, convertMultiButton);
		for (final Button button : List.of(convertFlatButton,
				convertComplexButton, convertMultiButton, restoreBlockButton)) {
			button.setFocusTraversable(false);
			button.setMaxWidth(Double.MAX_VALUE);
			button.setDisable(true);
			button.disabledProperty().addListener((observable, oldValue, newValue) ->
					applyConversionButtonTheme(button));
			applyConversionButtonTheme(button);
		}
		HBox.setHgrow(convertFlatButton, Priority.ALWAYS);
		HBox.setHgrow(convertComplexButton, Priority.ALWAYS);
		HBox.setHgrow(convertMultiButton, Priority.ALWAYS);
		final VBox conversionActions = new VBox(4,
				conversions, restoreBlockButton);
		layerList.setPlaceholder(uiLabel("Select a block"));
		layerList.setMinHeight(180);
		layerList.setPrefHeight(420);
		layerList.setFocusTraversable(false);
		layerList.setCellFactory(view -> new ListCell<>() {
			@Override
			protected void updateItem(final String item, final boolean empty) {
				super.updateItem(item, empty);
				setText(empty ? null : item);
				setStyle(layerCellStyle(isSelected(), empty));
			}

			@Override
			public void updateSelected(final boolean selected) {
				super.updateSelected(selected);
				setStyle(layerCellStyle(selected, isEmpty()));
			}
		});
		layerList.getSelectionModel().selectedIndexProperty().addListener(
				(observable, previous, selected) ->
						updateSelectedLayerNswe(selected.intValue()));
		nsweNorth.setOnAction(event -> applyNsweEdit());
		nsweSouth.setOnAction(event -> applyNsweEdit());
		nsweWest.setOnAction(event -> applyNsweEdit());
		nsweEast.setOnAction(event -> applyNsweEdit());
		nsweNorth.setFocusTraversable(false);
		nsweSouth.setFocusTraversable(false);
		nsweWest.setFocusTraversable(false);
		nsweEast.setFocusTraversable(false);
		final File nsweFile = new File(dataRoot,
				"textures/nswe_" + EditorSettings.NSWE_TEXTURE_ID + ".png");
		if (nsweFile.isFile())
			directNsweAtlas = loadTransparentNsweAtlas(nsweFile);
		directNsweImage.setFitWidth(128);
		directNsweImage.setFitHeight(128);
		directNsweImage.setPreserveRatio(false);
		directNsweImage.setOpacity(0.28);
		directNsweImage.setOnMouseClicked(event -> {
			if (selectedCellX < 0 || selectedLayer < 0)
				return;
			final double x = event.getX() / directNsweImage.getBoundsInLocal().getWidth();
			final double y = event.getY() / directNsweImage.getBoundsInLocal().getHeight();
			if (y <= x && y <= 1d - x)
				nsweNorth.setSelected(!nsweNorth.isSelected());
			else if (y >= x && y >= 1d - x)
				nsweSouth.setSelected(!nsweSouth.isSelected());
			else if (x <= y && x <= 1d - y)
				nsweWest.setSelected(!nsweWest.isSelected());
			else
				nsweEast.setSelected(!nsweEast.isSelected());
			applyNsweEdit();
		});
		final StackPane directNswe = new StackPane(directNsweImage);
		directNswe.setMinHeight(136);
		directNswe.setAlignment(Pos.CENTER);

		editorSidebar = new VBox(10,
				section("Block/Cell Info"), coordinates,
				new Separator(), section("Block Convert"), conversionActions,
				new Separator(), section("Cell Layers"),
				layerList,
				new Separator(), section("Direct NSWE"),
				directNswe);
		VBox.setVgrow(layerList, Priority.ALWAYS);
		editorSidebar.setPadding(new Insets(12));
		editorSidebar.setMinWidth(252);
		editorSidebar.setPrefWidth(252);
		editorSidebar.setMaxWidth(252);
		return editorSidebar;
	}

	private static TextField coordinateField() {
		final TextField field = new TextField();
		field.setEditable(false);
		field.setFocusTraversable(false);
		field.setPrefColumnCount(5);
		field.setMinWidth(0);
		field.setPrefHeight(25);
		return field;
	}

	private static HBox coordinatePair(final TextField first,
			final TextField second) {
		final HBox pair = new HBox(4, first, second);
		HBox.setHgrow(first, Priority.ALWAYS);
		HBox.setHgrow(second, Priority.ALWAYS);
		first.setMaxWidth(Double.MAX_VALUE);
		second.setMaxWidth(Double.MAX_VALUE);
		return pair;
	}

	private static void setPair(final TextField first, final TextField second,
			final int x, final int y) {
		first.setText(Integer.toString(x));
		second.setText(Integer.toString(y));
	}

	private static void clearPair(final TextField first, final TextField second) {
		first.clear();
		second.clear();
	}

	private static Image loadTransparentNsweAtlas(final File file) {
		final Image source = new Image(file.toURI().toString(), false);
		final int width = (int) source.getWidth();
		final int height = (int) source.getHeight();
		final WritableImage transparent = new WritableImage(width, height);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int argb = source.getPixelReader().getArgb(x, y);
				final int red = (argb >>> 16) & 0xff;
				final int green = (argb >>> 8) & 0xff;
				final int blue = argb & 0xff;
				transparent.getPixelWriter().setArgb(x, y,
						red > 248 && green > 248 && blue > 248
								? 0 : argb);
			}
		}
		return transparent;
	}

	private BorderPane createBottomBar() {
		final HBox navigation = new HBox(5);
		navigation.setAlignment(Pos.CENTER);
		final int[] order = {0, 1, 2, 4, 8, 5, 10, 9, 6, 3, 12,
				14, 13, 11, 7, 15};
		for (final int preset : order) {
			final ToggleButton button = new ToggleButton();
			nswePresets[preset] = button;
			button.setToggleGroup(nswePresetGroup);
			button.setFocusTraversable(false);
			button.setDisable(true);
			button.setGraphic(iconView("nswe-" + preset + ".png", 36, 36));
			button.setMinSize(48, 48);
			button.setPrefSize(48, 48);
			button.setMaxSize(48, 48);
			button.setTooltip(new javafx.scene.control.Tooltip(
					"Blocked directions preset " + preset));
			button.setOnAction(event -> applyNswePreset(preset));
			navigation.getChildren().add(button);
		}
		if (Boolean.getBoolean("geoeditor.graphicsSmoke"))
			verifyNswePresetIcons();
		undoButton.setMinWidth(82);
		redoButton.setMinWidth(82);
		clearHistoryButton.setMinWidth(94);
		for (final Button button : List.of(undoButton, redoButton, clearHistoryButton)) {
			button.setFocusTraversable(false);
			button.setMinHeight(26);
			button.setStyle("-fx-font-weight: bold; -fx-background-radius: 4;"
					+ " -fx-border-radius: 4; -fx-border-color: #3d5665;");
		}
		final HBox editActions = new HBox(6, editState, historyState,
				undoButton, redoButton, clearHistoryButton);
		editActions.setAlignment(Pos.CENTER);
		editorBottomBar = new BorderPane();
		editorBottomBar.setCenter(navigation);
		editorBottomBar.setRight(editActions);
		BorderPane.setAlignment(editActions, Pos.CENTER_RIGHT);
		BorderPane.setMargin(editActions, new Insets(0, 6, 0, 10));
		return editorBottomBar;
	}

	private void verifyNswePresetIcons() {
		int validIcons = 0;
		for (int preset = 0; preset < nswePresets.length; preset++) {
			final File file = new File(dataRoot,
					"icon/nswe-" + preset + ".png");
			if (!file.isFile() || file.length() == 0)
				continue;
			final Image image = new Image(file.toURI().toString(),
					36, 36, true, true, false);
			if (!image.isError() && image.getWidth() > 0
					&& image.getHeight() > 0)
				validIcons++;
		}
		System.out.println("GEOEDITOR_NSWE_PRESET_ICONS=" + validIcons
				+ "/" + nswePresets.length);
		if (validIcons != nswePresets.length)
			throw new IllegalStateException("Missing or invalid NSWE preset icons: "
					+ validIcons + "/" + nswePresets.length);
		validateNsweLayerLabels();
	}

	private static void validateNsweLayerLabels() {
		final String[] singleDirectionLabels = {"E", "W", "S", "N"};
		for (int bit = 0; bit < singleDirectionLabels.length; bit++) {
			final int blocked = 1 << bit;
			final String actual = nsweText((short) (~blocked & 0x0f));
			if (!singleDirectionLabels[bit].equals(actual))
				throw new IllegalStateException("NSWE single-direction label mismatch: "
						+ blocked + " expected " + singleDirectionLabels[bit]
						+ " but was " + actual);
		}
		for (int blocked = 0; blocked < 16; blocked++) {
			final short nswe = (short) (~blocked & 0x0f);
			final StringBuilder expected = new StringBuilder(4);
			if ((blocked & com.geo.GeoEngine.NORTH) != 0)
				expected.append('N');
			if ((blocked & com.geo.GeoEngine.SOUTH) != 0)
				expected.append('S');
			if ((blocked & com.geo.GeoEngine.WEST) != 0)
				expected.append('W');
			if ((blocked & com.geo.GeoEngine.EAST) != 0)
				expected.append('E');
			final String expectedText = expected.isEmpty() ? "-" : expected.toString();
			if (!expectedText.equals(nsweText(nswe)))
				throw new IllegalStateException("NSWE layer label mismatch for preset "
						+ blocked + ": " + nsweText(nswe));
		}
		System.out.println("GEOEDITOR_NSWE_LAYER_LABELS=16/16");
	}

	private void openRegion() {
		final FileChooser chooser = new FileChooser();
		chooser.setTitle("Open L2J geodata region");
		chooser.getExtensionFilters().add(
				new FileChooser.ExtensionFilter("L2J geodata", "*.l2j"));
		final File directory = geodataRoot;
		if (directory.isDirectory())
			chooser.setInitialDirectory(directory);
		final File selected = chooser.showOpenDialog(stage);
		if (selected != null)
			loadRegion(selected);
	}

	private void selectMap() {
		final MapSelectorDialog selector =
				new MapSelectorDialog(stage, dataRoot, geodataRoot,
						mapSelectorSize, darkTheme);
		if (currentSnapshot != null)
			selector.openAt(currentSnapshot.regionX(), currentSnapshot.regionY(),
					Math.max(0, Math.min(2047, (int) camera.x())),
					Math.max(0, Math.min(2047, (int) camera.z())));
		selector.showAndWait().ifPresent(selection -> {
					final boolean opened = loadRegion(selection.file(),
							selection.localGeoX(), selection.localGeoY());
					if (Boolean.getBoolean("geoeditor.mapSelectorSmoke"))
						System.out.println("GEOEDITOR_MAP_SELECTOR_SELECTION=OPENED:"
								+ opened + ",LOCAL:" + Math.round(camera.x()) + ","
								+ Math.round(camera.z()) + ",ROT:"
								+ camera.rotationX() + "," + camera.rotationY());
				});
	}

	private boolean loadRegion(final File file) {
		return loadRegion(file, 1024, 1024);
	}

	private boolean loadRegion(final File file, final int requestedLocalGeoX,
			final int requestedLocalGeoY) {
		if (!confirmDiscardChanges())
			return false;
		status.setText("Loading " + file.getName() + "...");
		final long loadStarted = System.nanoTime();
		try {
			final float preservedPitch = currentSnapshot == null
					? cameraPitch : camera.rotationX();
			final float preservedYaw = currentSnapshot == null
					? cameraYaw : camera.rotationY();
			final EditableRegionSession session = EditableRegionSession.load(file);
			final DetailedRegionSnapshot details = session.snapshot();
			final RegionSnapshot snapshot = details.summary();
			final long loadMicros = (System.nanoTime() - loadStarted) / 1_000L;
			metrics.recordRegionRead(loadMicros);
			metrics.recordRegionLoad(loadMicros, true);
			metrics.setLoadedRegions(1);
			final EditableRegionSession previousSession = currentSession;
			currentSession = session;
			clearEditHistory();
			currentSnapshot = snapshot;
			currentDetails = details;
			if (previousSession != null)
				previousSession.close();
			saveButton.setDisable(false);
			final int localGeoX = Math.max(0, Math.min(2047, requestedLocalGeoX));
			final int localGeoY = Math.max(0, Math.min(2047, requestedLocalGeoY));
			final int absoluteGeoX = (snapshot.regionX()
					<< GeoCoordinates.REGION_GEO_SHIFT) + localGeoX;
			final int absoluteGeoY = (snapshot.regionY()
					<< GeoCoordinates.REGION_GEO_SHIFT) + localGeoY;
			camera.set(localGeoX, currentSession.region()
					.nGetCell(absoluteGeoX, absoluteGeoY, 0).getHeight() / 16f + 31.25f,
					localGeoY, preservedPitch, preservedYaw);
			visibleRegions = Map.of(new RegionLoadManager.RegionKey(
					snapshot.regionX(), snapshot.regionY()), snapshot);
			streamingCenterKey = new RegionLoadManager.RegionKey(
					snapshot.regionX(), snapshot.regionY());
			currentTexture = new File(dataRoot,
					"textures/region/" + snapshot.name() + ".jpg");
			selectedBlockX = -1;
			selectedBlockY = -1;
			selectedCellX = -1;
			selectedCellY = -1;
			selectedLayer = -1;
			selectedCells.clear();
			clearSelectionInfo();
			miniMapImage.setImage(currentTexture.isFile()
					? new Image(currentTexture.toURI().toString(),
							miniMapSize, miniMapSize, false, true, true) : null);
			overlay.setText(snapshot.name());
			setMiniMapActive(true);
			updateCoordinates(snapshot);
			skyTexturesPending = skyStyle >= 1 && skyStyle <= 4
					&& !renderer.isSkyTextureReady();
			streamingDeferredForSky = skyTexturesPending;
			if (skyTexturesPending && !streamingUiBlocked)
				showNeighborLoading(renderer.requiredSkyTextureCount(),
						skyLoadingLabel());
			renderer.setBulkTextureLoading(skyTexturesPending);
			renderCurrent();
			if (!streamingDeferredForSky)
				updateStreamingWindow();
			return true;
		} catch (final Exception exception) {
			status.setText("Load failed: " + file.getName());
			showError(exception.getMessage());
			return false;
		}
	}

	private void updateStreamingWindow() {
		if (currentSnapshot == null)
			return;
		final boolean enabled = RegionStreamingPolicy.shouldLoadNeighbors(
				fullMap, auxiliaryTerrains);
		final RegionSnapshot streamingCenter = streamingCenterKey == null
				? currentSnapshot : visibleRegions.getOrDefault(
						streamingCenterKey, currentSnapshot);
		if (streamingUiBlocked && enabled)
			updateNeighborLoading(0, neighborCount,
					"Loading neighboring maps");
		streamingTask = snapshotWindow.select(streamingCenter,
				geodataRoot, enabled, neighborCount,
				progress -> Platform.runLater(() -> {
					if (!streamingUiBlocked)
						showNeighborLoading(progress.total(), progress.phase());
					status.setText(progress.phase() + " " + progress.completed() + "/"
							+ progress.total());
					updateNeighborLoading(progress.completed(), progress.total(),
							progress.phase());
				}));
		streamingTask.thenAccept(window -> Platform.runLater(() -> {
			if (window == null)
				return;
			if (streamingUiBlocked) {
				updateNeighborLoading(neighborCount, neighborCount,
						"Loading neighboring maps");
				mapCompletionHold = true;
				final PauseTransition hold = new PauseTransition(Duration.seconds(1));
				hold.setOnFinished(event -> {
					mapCompletionHold = false;
					completeStreamingWindow(window);
				});
				hold.play();
				return;
			}
			completeStreamingWindow(window);
		}));
		streamingTask.exceptionally(exception -> {
			Platform.runLater(() -> {
				status.setText("Neighbor loading failed: " + exception.getMessage());
				if (streamingUiBlocked && !skyTexturesPending)
					hideNeighborLoading();
			});
			return null;
		});
	}

	private void completeStreamingWindow(
			final RegionSnapshotWindow.Snapshot window) {
			metrics.setLoadedRegions(window.regions().size());
			metrics.recordRegionLoad(window.loadMicros(), false);
			visibleRegions = window.regions();
			updateMetricsHud();
			status.setText("Region window ready | " + window.regions().size()
					+ " loaded");
			neighborTexturesPending = auxiliaryTerrains && fullTexturedTerrain
					&& window.regions().size() > 1;
			renderer.setBulkTextureLoading(neighborTexturesPending
					|| skyTexturesPending);
			if (streamingUiBlocked && !neighborTexturesPending
					&& !skyTexturesPending)
				hideNeighborLoading();
			if (Boolean.getBoolean("geoeditor.streamingSmoke"))
				System.out.println("GEOEDITOR_STREAMING=REGIONS:"
						+ window.regions().size() + ",LOAD_US:" + window.loadMicros());
			if (Boolean.getBoolean("geoeditor.configApplySmoke"))
				System.out.println("GEOEDITOR_CONFIG_APPLY_STREAMING=REGIONS:"
						+ window.regions().size() + ",REQUESTED:" + neighborCount);
			renderCurrent();
			if (Boolean.getBoolean("geoeditor.configApplySmoke")
					&& window.regions().size() > 1) {
				final String output = System.getProperty(
						"geoeditor.configApplyOutput");
				if (output != null && !output.isBlank())
					writeFrameSmoke(new File(output));
			}
			if (Boolean.getBoolean("geoeditor.neighborResizeSmoke"))
				advanceNeighborResizeSmoke(window);
			if (Boolean.getBoolean("geoeditor.neighborStyleSmoke"))
				compareNeighborStylesSmoke();
			if (Boolean.getBoolean("geoeditor.longTraversalSmoke")) {
				scheduleLongTraversal();
			} else if (Boolean.getBoolean("geoeditor.streamingSmoke")
					&& fullMap && !fullMapSmokeTriggered) {
				final RegionLoadManager.RegionKey east = new RegionLoadManager.RegionKey(
						currentSnapshot.regionX() + 1, currentSnapshot.regionY());
				if (visibleRegions.containsKey(east)) {
					fullMapSmokeTriggered = true;
					camera.set(2052f, camera.y(), 1024f,
							camera.rotationX(), camera.rotationY());
					updateFullMapStreamingCenter();
					updateFullMapStreamingCenter();
					updateFullMapStreamingCenter();
				}
			}
	}

	private void advanceNeighborResizeSmoke(final RegionSnapshotWindow.Snapshot window) {
		final int expectedNeighbors = neighborResizeSmokeStep == 1 ? 24 : 8;
		final int actualNeighbors = Math.max(0, window.regions().size() - 1);
		System.out.println("GEOEDITOR_NEIGHBOR_RESIZE=STEP:" + neighborResizeSmokeStep
				+ ",EXPECTED:" + expectedNeighbors + ",VISIBLE:" + actualNeighbors
				+ ",REUSED:" + window.reusedNeighbors() + ",LOADED:"
				+ window.loadedNeighbors() + ",DISCARDED:" + window.discardedNeighbors());
		if (neighborResizeSmokeStep == 0) {
			neighborResizeSmokeStep = 1;
			neighborCount = 24;
			updateStreamingWindow();
			return;
		}
		if (neighborResizeSmokeStep == 1) {
			neighborResizeSmokeStep = 2;
			neighborCount = 8;
			updateStreamingWindow();
			return;
		}
		final boolean passed = actualNeighbors == 8
				&& window.loadedNeighbors() == 0
				&& window.discardedNeighbors() == 16;
		System.out.println("GEOEDITOR_NEIGHBOR_RESIZE=" + (passed ? "PASS" : "FAIL"));
	}

	private void scheduleLongTraversal() {
		if (!fullMap || currentSnapshot == null || regionTransitionPending
				|| longTraversalTransitions >= 6)
			return;
		final RegionLoadManager.RegionKey west = new RegionLoadManager.RegionKey(
				currentSnapshot.regionX() - 1, currentSnapshot.regionY());
		if (!visibleRegions.containsKey(west))
			return;
		longTraversalTransitions++;
		camera.set(-4f, camera.y(), 1024f,
				camera.rotationX(), camera.rotationY());
		System.out.println("GEOEDITOR_LONG_TRAVERSAL=STEP:"
				+ longTraversalTransitions + ",TARGET:" + west);
		updateFullMapStreamingCenter();
		updateFullMapStreamingCenter();
	}

	private void updateFullMapStreamingCenter() {
		if (!fullMap || currentSnapshot == null || visibleRegions.isEmpty())
			return;
		final int offsetX = Math.floorDiv((int) Math.floor(camera.x()), 2048);
		final int offsetY = Math.floorDiv((int) Math.floor(camera.z()), 2048);
		final RegionLoadManager.RegionKey target = new RegionLoadManager.RegionKey(
				currentSnapshot.regionX() + offsetX,
				currentSnapshot.regionY() + offsetY);
		final RegionLoadManager.RegionKey active = new RegionLoadManager.RegionKey(
				currentSnapshot.regionX(), currentSnapshot.regionY());
		if (target.equals(active)) {
			transitionCandidate = null;
			transitionCandidateFrames = 0;
			return;
		}
		if (!target.equals(transitionCandidate)) {
			transitionCandidate = target;
			transitionCandidateFrames = 1;
			return;
		}
		if (transitionCandidateFrames < 0)
			return;
		if (++transitionCandidateFrames < 3)
			return;
		if (regionTransitionPending)
			return;
		if (isEditorModified()) {
			status.setText("Save changes before Full Map changes region.");
			showTransitionOverlay("Save changes before changing region", true);
			return;
		}
		transitionToRegion(target);
	}

	private void transitionToRegion(final RegionLoadManager.RegionKey target) {
		final File file = new File(geodataRoot, (target.x() + 10)
				+ "_" + (target.y() + 10) + ".l2j");
		if (!file.isFile()) {
			status.setText("Full Map region is unavailable: " + file.getName());
			showTransitionOverlay("Region unavailable: " + file.getName(), true);
			transitionCandidateFrames = -1;
			return;
		}
		regionTransitionPending = true;
		regionVisualTransitionPending = true;
		showTransitionOverlay("Loading region " + file.getName() + "...", false);
		final File nextTexture = new File(dataRoot,
				"textures/region/" + (target.x() + 10) + "_" + (target.y() + 10) + ".jpg");
		renderer.prepareRegionTexture(nextTexture);
		fadeOutMiniMap();
		final int absoluteGeoX = (currentSnapshot.regionX()
				<< GeoCoordinates.REGION_GEO_SHIFT) + (int) Math.floor(camera.x());
		final int absoluteGeoY = (currentSnapshot.regionY()
				<< GeoCoordinates.REGION_GEO_SHIFT) + (int) Math.floor(camera.z());
		final float preservedY = camera.y();
		final float preservedPitch = camera.rotationX();
		final float preservedYaw = camera.rotationY();
		final long started = System.nanoTime();
		CompletableFuture.supplyAsync(() -> {
			try {
				return EditableRegionSession.load(file);
			} catch (final IOException exception) {
				throw new CompletionException(exception);
			}
		}).whenComplete((session, exception) -> Platform.runLater(() -> {
			try {
				if (exception != null || session == null) {
					status.setText("Full Map transition failed: " + file.getName());
					showTransitionOverlay("Unable to load " + file.getName(), true);
					regionVisualTransitionPending = false;
					showMiniMapForRegion(currentTexture);
					return;
				}
				final EditableRegionSession previous = currentSession;
				currentSession = session;
				clearEditHistory();
				currentDetails = session.snapshot();
				currentSnapshot = currentDetails.summary();
				if (previous != null)
					previous.close();
				metrics.recordRegionRead((System.nanoTime() - started) / 1_000L);
				final int localX = absoluteGeoX
						- (target.x() << GeoCoordinates.REGION_GEO_SHIFT);
				final int localY = absoluteGeoY
						- (target.y() << GeoCoordinates.REGION_GEO_SHIFT);
				camera.set(localX, preservedY, localY, preservedPitch, preservedYaw);
				final Map<RegionLoadManager.RegionKey, RegionSnapshot> updated =
						new HashMap<>(visibleRegions);
				updated.put(target, currentSnapshot);
				visibleRegions = Map.copyOf(updated);
				final RegionLoadManager.RegionKey previousWindowCenter = streamingCenterKey;
				currentTexture = new File(dataRoot,
						"textures/region/" + currentSnapshot.name() + ".jpg");
				showMiniMapForRegion(currentTexture);
				overlay.setText(currentSnapshot.name());
				setMiniMapActive(true);
				selectedBlockX = selectedBlockY = selectedCellX = selectedCellY = -1;
				selectedLayer = -1;
				selectedCells.clear();
				clearSelectionInfo();
				updateCoordinates(currentSnapshot);
				renderer.requestSynchronousReadback();
				renderCurrent();
				if (isCurrentRegionTextureReady())
					finishRegionVisualTransition();
				final int radius = RegionWindowPlan.radiusForNeighborCount(neighborCount);
				if (RegionWindowPlan.isOutsideWindow(
						previousWindowCenter == null ? Integer.MIN_VALUE : previousWindowCenter.x(),
						previousWindowCenter == null ? Integer.MIN_VALUE : previousWindowCenter.y(),
						target.x(), target.y(), radius)) {
					streamingCenterKey = target;
					updateStreamingWindow();
				}
				if (Boolean.getBoolean("geoeditor.longTraversalSmoke"))
					Platform.runLater(this::scheduleLongTraversal);
				System.out.println("GEOEDITOR_FULL_MAP_TRANSITION="
						+ currentSnapshot.name() + ",LOCAL:" + localX + "," + localY);
			} finally {
				regionTransitionPending = false;
				transitionCandidate = null;
				transitionCandidateFrames = 0;
			}
		}));
	}

	private void renderCurrent() {
		renderCurrent(false);
	}

	private boolean isCurrentRegionTextureReady() {
		return !texturedTerrain || currentTexture == null || !currentTexture.isFile()
				|| renderer.isTextureResident(currentTexture);
	}

	private void renderCurrent(final boolean interactiveCamera) {
		if (currentSnapshot == null)
			return;
		try {
			renderer.setGridSelections(selectedCells.stream()
					.map(selection -> new LwjglRenderBackend.GridSelection(
							selection.blockX(), selection.blockY(), selection.cellX(),
							selection.cellY(), selection.layer()))
					.toList());
			final int fullWidth = viewport.getWidth() >= 320
					? (int) Math.round(viewport.getWidth()) : 960;
			final int fullHeight = viewport.getHeight() >= 240
					? (int) Math.round(viewport.getHeight()) : 600;
			/* Keep full viewport quality during camera movement. */
			final int renderWidth = fullWidth;
			final int renderHeight = fullHeight;
			metrics.beginFrame();
			final Map<RegionLoadManager.RegionKey, RegionSnapshot> renderRegions =
					auxiliaryTerrains ? visibleRegions : Map.of(
							new RegionLoadManager.RegionKey(
									currentSnapshot.regionX(),
									currentSnapshot.regionY()),
							currentSnapshot);
			final LwjglRenderBackend.RenderedFrame frame = renderer.renderWindow(
					currentSnapshot, currentDetails, renderRegions,
					new File(dataRoot, "textures/region"), texturedTerrain,
					fullTexturedTerrain, geodataGrid,
					renderWidth, renderHeight,
					camera.rotationX(), camera.rotationY(), camera.y(),
					camera.x(), camera.z(), selectedBlockX, selectedBlockY,
					selectedCellX, selectedCellY, selectedLayer,
					visibleGridRange, circularGrid, antialiasingSamples,
					transparency, drawOutline);
			currentFrame = frame;
			if (viewportWritableImage == null
					|| (int) viewportWritableImage.getWidth() != frame.width()
					|| (int) viewportWritableImage.getHeight() != frame.height())
				viewportWritableImage = new WritableImage(frame.width(), frame.height());
			viewportWritableImage.getPixelWriter().setPixels(
					0, 0, frame.width(), frame.height(),
					PixelFormat.getByteBgraInstance(), frame.bgra(), 0, frame.width() * 4);
			if (viewportImage.getImage() != viewportWritableImage)
				viewportImage.setImage(viewportWritableImage);
			status.setText("Loaded " + currentSnapshot.name()
					+ (texturedTerrain && currentTexture.isFile() ? " | textured" : " | height")
					+ " | camera " + Math.round(camera.rotationX()) + "°/"
					+ Math.round(camera.rotationY()) + "°"
					+ " | XYZ " + Math.round(camera.x()) + ","
					+ Math.round(camera.y()) + "," + Math.round(camera.z()));
			if (Boolean.getBoolean("geoeditor.cameraSmoke")
					&& !rangeBenchmarkRunning) {
				final CRC32 checksum = new CRC32();
				checksum.update(frame.bgra());
				final String crc = HexFormat.of().withUpperCase()
						.toHexDigits(checksum.getValue());
				System.out.println("GEOEDITOR_VIEWPORT=" + frame.width() + "x"
						+ frame.height() + ",REGION=" + currentSnapshot.name()
						+ ",CRC32=" + crc);
			}
			metrics.endFrame();
			updateMiniMapMarker();
			updateMetricsHud();
		} catch (final RuntimeException exception) {
			status.setText("Render failed: " + exception.getMessage());
		}
	}

	private void scheduleViewportResizeRender() {
		if (currentSnapshot == null)
			return;
		Platform.runLater(() -> {
			if (currentSnapshot != null
					&& viewport.getWidth() >= 320 && viewport.getHeight() >= 240)
				renderCurrent();
		});
	}

	private void updateMetricsHud() {
		final long now = System.nanoTime();
		final PerformanceMetrics.Snapshot snapshot = metrics.snapshot();
		final boolean metricsRefreshed = lastHudUpdateNanos == 0L
				|| now - lastHudUpdateNanos >= 500_000_000L;
		if (metricsRefreshed) {
			lastHudUpdateNanos = now;
			final LwjglRenderBackend.RenderStats renderStats = renderer.renderStats();
			final long fps = snapshot.averageFrameMicros() <= 0L ? 0L
					: 1_000_000L / snapshot.averageFrameMicros();
			final long displayedMemory = snapshot.processResidentBytes() > 0L
					? snapshot.processResidentBytes() : snapshot.heapUsedBytes();
			cachedHudPerformance = "\nRegions: " + snapshot.loadedRegions()
					+ "\nMemory: " + formatMegabytes(displayedMemory) + "mb"
					+ "\nFrames: " + snapshot.frames()
					+ "\nFps: " + fps
					+ "\nElements/Draws: " + renderStats.elements()
					+ "/" + renderStats.drawCalls();
		}
		final String world = currentSnapshot == null ? "-"
				: GeoCoordinates.worldXFromGeo((currentSnapshot.regionX()
						<< GeoCoordinates.REGION_GEO_SHIFT) + (int) camera.x())
						+ ", " + GeoCoordinates.worldYFromGeo((currentSnapshot.regionY()
								<< GeoCoordinates.REGION_GEO_SHIFT) + (int) camera.z())
						+ ", " + Math.round(camera.y() * 16f);
		final String geo = currentSnapshot == null ? "-"
				: ((currentSnapshot.regionX() << GeoCoordinates.REGION_GEO_SHIFT)
						+ (int) camera.x()) + ", "
						+ ((currentSnapshot.regionY() << GeoCoordinates.REGION_GEO_SHIFT)
								+ (int) camera.z()) + ", "
						+ Math.round(camera.y() * 16f);
		metricsHud.setText("World-Pos XYZ: " + world
				+ "\nGeo-Pos XYZ: " + geo
				+ cachedHudPerformance);
		if (metricsRefreshed && Boolean.getBoolean("geoeditor.metricsSmoke")
				&& !rangeBenchmarkRunning) {
			final long fps = snapshot.averageFrameMicros() <= 0L ? 0L
					: 1_000_000L / snapshot.averageFrameMicros();
			System.out.println("GEOEDITOR_METRICS=FRAMES:" + snapshot.frames()
					+ ",FPS:" + fps
					+ ",FRAME_US:" + snapshot.lastFrameMicros()
					+ ",LOAD_US:" + snapshot.lastRegionReadMicros()
					+ ",HEAP:" + snapshot.heapUsedBytes()
					+ ",RESIDENT:" + snapshot.processResidentBytes());
		}
	}

	private static String formatMillis(final long micros) {
		return String.format(java.util.Locale.ROOT, "%.2f", micros / 1_000.0);
	}

	private static long formatMegabytes(final long bytes) {
		return Math.max(0L, Math.round(bytes / (1024.0 * 1024.0)));
	}

	private void selectBlockAt(final double mouseX, final double mouseY,
			final double viewportWidth, final double viewportHeight) {
		selectBlockAt(mouseX, mouseY, viewportWidth, viewportHeight, false, false);
	}

	private void selectBlockAt(final double mouseX, final double mouseY,
			final double viewportWidth, final double viewportHeight,
			final boolean fullBlock, final boolean append) {
		selectBlockAt(mouseX, mouseY, viewportWidth, viewportHeight,
				fullBlock, append, false);
	}

	private void selectBlockAt(final double mouseX, final double mouseY,
			final double viewportWidth, final double viewportHeight,
			final boolean fullBlock, final boolean append,
			final boolean protectGestureSelection) {
		selectBlockAt(mouseX, mouseY, viewportWidth, viewportHeight, fullBlock,
				append, protectGestureSelection, false);
	}

	private void selectBlockAt(final double mouseX, final double mouseY,
			final double viewportWidth, final double viewportHeight,
			final boolean fullBlock, final boolean append,
			final boolean protectGestureSelection, final boolean deferPresentation) {
		if (currentSnapshot == null || currentFrame == null
				|| viewportWidth <= 0 || viewportHeight <= 0)
			return;
		final int pixelX = (int) Math.floor(mouseX * currentFrame.width() / viewportWidth);
		final int pixelY = (int) Math.floor(mouseY * currentFrame.height() / viewportHeight);
		final int id = renderer.pickBlock(currentSnapshot, currentDetails,
				pixelX, pixelY, currentFrame.width(), currentFrame.height(),
				camera.rotationX(), camera.rotationY(), camera.y(),
				camera.x(), camera.z(), visibleGridRange, circularGrid);
		if (id == 0) {
			if (protectGestureSelection)
				return;
			selectedCells.clear();
			selectedBlockX = -1;
			selectedBlockY = -1;
			selectedCellX = -1;
			selectedCellY = -1;
			selectedLayer = -1;
			clearSelectionInfo();
			renderSelectionImmediately();
			return;
		}
		final int zeroBased = id - 1;
		selectedBlockX = zeroBased & 0xff;
		selectedBlockY = zeroBased >>> 8;
		final int cellId = renderer.pickCell(currentSnapshot, currentDetails,
				selectedBlockX, selectedBlockY, pixelX, pixelY,
				currentFrame.width(), currentFrame.height(),
				camera.rotationX(), camera.rotationY(), camera.y(),
				camera.x(), camera.z(), visibleGridRange, circularGrid);
		if (cellId >= 0) {
			final int cell = cellId & 0x3f;
			selectedCellX = cell & 7;
			selectedCellY = cell >>> 3;
			selectedLayer = cellId >>> 6;
		} else {
			selectedCellX = 0;
			selectedCellY = 0;
			selectedLayer = 0;
		}
		if (!updateMultipleSelection(fullBlock, append, protectGestureSelection))
			return;
		if (selectedCells.isEmpty()) {
			selectedBlockX = selectedBlockY = selectedCellX = selectedCellY = -1;
			selectedLayer = -1;
			clearSelectionInfo();
			renderSelectionImmediately();
			return;
		}
		final int geoX = GeoCoordinates.geoFromRegionBlock(
				currentSnapshot.regionX(), selectedBlockX);
		final int geoY = GeoCoordinates.geoFromRegionBlock(
				currentSnapshot.regionY(), selectedBlockY);
		setPair(geoXValue, geoYValue, geoX, geoY);
		setPair(worldXValue, worldYValue,
				GeoCoordinates.worldXFromGeo(geoX),
				GeoCoordinates.worldYFromGeo(geoY));
		setPair(blockXValue, blockYValue, selectedBlockX, selectedBlockY);
		typeValue.setText(blockTypeName(
				currentSnapshot.blockType(selectedBlockX, selectedBlockY)));
		updateSelectedCellDetails(selectedCellX, selectedCellY);
		status.setText("Selected blocks " + selectedBlockCount()
				+ " | cells " + selectedCells.size()
				+ " | active block " + selectedBlockX + ", " + selectedBlockY
				+ " | cell " + selectedCellX + ", " + selectedCellY
				+ " | read-only");
		System.out.println("GEOEDITOR_SELECTION=BLOCK:" + selectedBlockX + ","
				+ selectedBlockY + ",TYPE:"
				+ blockTypeName(currentSnapshot.blockType(selectedBlockX, selectedBlockY))
				+ ",CELL:" + selectedCellX + "," + selectedCellY
				+ ",HEIGHT:"
				+ currentSnapshot.maximumHeight(selectedBlockX, selectedBlockY));
		if (deferPresentation)
		{
			selectionRenderPending = true;
			cameraRenderPending = true;
		}
		else
			renderSelectionImmediately();
	}

	private void finishCellSelectionGesture() {
		if (!cellSelectionGesture)
			return;
		cellSelectionGesture = false;
		selectionGestureFullBlock = false;
		selectionGestureAppend = false;
		gestureSelections.clear();
		renderSelectionImmediately();
	}

	private void updateMultipleSelection(final boolean fullBlock,
			final boolean append) {
		updateMultipleSelection(fullBlock, append, false);
	}

	private boolean updateMultipleSelection(final boolean fullBlock,
			final boolean append, final boolean protectGestureSelection) {
		final LinkedHashSet<CellSelection> picked = new LinkedHashSet<>();
		final boolean flat = currentSnapshot.blockType(selectedBlockX, selectedBlockY)
				== com.geo.GeoEngine.GEO_BLOCK_TYPE_FLAT;
		if (fullBlock && !flat) {
			for (int cellY = 0; cellY < 8; cellY++) {
				for (int cellX = 0; cellX < 8; cellX++) {
					final int localX = selectedBlockX * 8 + cellX;
					final int localY = selectedBlockY * 8 + cellY;
					final int layers = currentDetails.layerCount(localX, localY);
					if (layers > 0)
						picked.add(new CellSelection(selectedBlockX, selectedBlockY,
								cellX, cellY, Math.min(selectedLayer, layers - 1)));
				}
			}
		} else {
			picked.add(new CellSelection(selectedBlockX, selectedBlockY,
					flat ? 0 : selectedCellX, flat ? 0 : selectedCellY,
					selectedLayer));
		}
		if (protectGestureSelection) {
			picked.removeAll(gestureSelections);
			if (picked.isEmpty())
				return false;
		}
		gestureSelections.addAll(picked);
		if (!append) {
			selectedCells.clear();
			selectedCells.addAll(picked);
		} else if (selectedCells.containsAll(picked)) {
			selectedCells.removeAll(picked);
		} else {
			selectedCells.addAll(picked);
		}
		if (!selectedCells.isEmpty() && !selectedCells.contains(
				new CellSelection(selectedBlockX, selectedBlockY,
						flat ? 0 : selectedCellX, flat ? 0 : selectedCellY,
						selectedLayer))) {
			final CellSelection active = selectedCells.stream().reduce((a, b) -> b).orElseThrow();
			selectedBlockX = active.blockX(); selectedBlockY = active.blockY();
			selectedCellX = active.cellX(); selectedCellY = active.cellY();
			selectedLayer = active.layer();
		}
		return true;
	}

	private long selectedBlockCount() {
		return selectedCells.stream().map(selection ->
				(selection.blockY() << 8) | selection.blockX()).distinct().count();
	}

	private void renderSelectionImmediately() {
		renderer.requestSynchronousReadback();
		renderCurrent();
	}

	private void validateTypePickingSmoke() {
		if (currentSnapshot == null || currentDetails == null)
			throw new IllegalStateException("Type picking smoke requires a region.");
		boolean immediateHighlightValidated = false;
		for (byte type = 0; type <= 2; type++) {
			int targetX = -1;
			int targetY = -1;
			for (int y = 16; y < 240 && targetX < 0; y++) {
				for (int x = 16; x < 240; x++) {
					if (currentSnapshot.blockType(x, y) == type) {
						targetX = x;
						targetY = y;
						break;
					}
				}
			}
			if (targetX < 0)
				throw new IllegalStateException(
						"No real block found for type " + blockTypeName(type));
			final float cameraX = targetX * 8f + 4f;
			final float cameraZ = targetY * 8f + 4f;
			final float cameraY = currentSnapshot.maximumHeight(targetX, targetY)
					/ 16f + 64f;
			camera.set(cameraX, cameraY, cameraZ, -90f, 0f);
			renderCurrent();
			final long frameBeforeClick = frameChecksum();
			selectBlockAt(viewport.getWidth() / 2d, viewport.getHeight() / 2d,
					viewport.getWidth(), viewport.getHeight());
			if (selectedBlockX != targetX || selectedBlockY != targetY)
				throw new IllegalStateException("Picking mismatch for "
						+ blockTypeName(type) + ": expected " + targetX + ","
						+ targetY + " but got " + selectedBlockX + ","
						+ selectedBlockY);
			System.out.println("GEOEDITOR_TYPE_PICKING=" + blockTypeName(type)
					+ ":" + targetX + "," + targetY);
			if (frameChecksum() != frameBeforeClick)
				immediateHighlightValidated = true;
		}
		if (!immediateHighlightValidated)
			throw new IllegalStateException("Single-click highlight was not presented.");
		System.out.println("GEOEDITOR_SINGLE_CLICK_PICKING=PASS");
		final int centerGeoX = (currentSnapshot.regionX()
				<< GeoCoordinates.REGION_GEO_SHIFT) + 1024;
		final int centerGeoY = (currentSnapshot.regionY()
				<< GeoCoordinates.REGION_GEO_SHIFT) + 1024;
		camera.set(1024f, currentSession.region()
				.nGetCell(centerGeoX, centerGeoY, 0).getHeight() / 16f + 31.25f,
				1024f, cameraPitch, cameraYaw);
		clearSelectionInfo();
		renderCurrent();
	}

	private void updateSelectedCellDetails(final int cellX, final int cellY) {
		if (currentDetails == null || selectedBlockX < 0 || selectedBlockY < 0)
			return;
		updateBlockConvertButtons(
				currentSnapshot.blockType(selectedBlockX, selectedBlockY));
		final int localGeoX = selectedBlockX * 8 + cellX;
		final int localGeoY = selectedBlockY * 8 + cellY;
		updateCoordinates(currentSnapshot, localGeoX, localGeoY);
		final int count = currentDetails.layerCount(localGeoX, localGeoY);
		final java.util.ArrayList<String> rows = new java.util.ArrayList<>(count);
		for (int layer = 0; layer < count; layer++) {
			final short height = currentDetails.height(localGeoX, localGeoY, layer);
			final short nswe = currentDetails.nswe(localGeoX, localGeoY, layer);
			rows.add("Layer " + layer + " | Z " + height
					+ " | NSWE " + nsweText(nswe));
		}
		setPair(cellXValue, cellYValue, cellX, cellY);
		layersValue.setText(Integer.toString(count));
		heightValue.setText(count == 0 ? "-" : Short.toString(
				currentDetails.height(localGeoX, localGeoY, count - 1)));
		layerList.setItems(FXCollections.observableArrayList(rows));
		if (!rows.isEmpty()) {
			final int activeLayer = Math.min(Math.max(0, selectedLayer), rows.size() - 1);
			layerList.getSelectionModel().select(activeLayer);
			/* Selecting another cell may keep the same ListView index (usually 0),
			 * so the selection listener is not guaranteed to run. Refresh the
			 * layer-dependent controls explicitly for every cell click. */
			updateSelectedLayerNswe(activeLayer);
		}
	}

	private void updateSelectedLayerNswe(final int layer) {
		if (currentDetails == null || selectedBlockX < 0 || selectedCellX < 0
				|| layer < 0)
			return;
		final int localGeoX = selectedBlockX * 8 + selectedCellX;
		final int localGeoY = selectedBlockY * 8 + selectedCellY;
		if (layer >= currentDetails.layerCount(localGeoX, localGeoY))
			return;
		final boolean layerChanged = selectedLayer != layer;
		selectedLayer = layer;
		final short nswe = currentDetails.nswe(localGeoX, localGeoY, layer);
		nsweNorth.setSelected((nswe & com.geo.GeoEngine.NORTH) != 0);
		nsweSouth.setSelected((nswe & com.geo.GeoEngine.SOUTH) != 0);
		nsweWest.setSelected((nswe & com.geo.GeoEngine.WEST) != 0);
		nsweEast.setSelected((nswe & com.geo.GeoEngine.EAST) != 0);
		updateDirectNsweImage(nswe);
		final short height = currentDetails.height(localGeoX, localGeoY, layer);
		heightValue.setText(Short.toString(height));
		heightEditor.setText(Short.toString(height));
		heightEditor.setDisable(false);
		nsweNorth.setDisable(false);
		nsweSouth.setDisable(false);
		nsweWest.setDisable(false);
		nsweEast.setDisable(false);
		for (final ToggleButton button : nswePresets) {
			if (button != null)
				button.setDisable(false);
		}
		final int preset = (~nswe) & 0x0f;
		if (nswePresets[preset] != null)
			nswePresetGroup.selectToggle(nswePresets[preset]);
		if (layerChanged)
			renderCurrent();
	}

	private void updateDirectNsweImage(final int nswe) {
		if (directNsweAtlas == null || nswe < 0 || nswe > 15) {
			directNsweImage.setImage(null);
			directNsweImage.setOpacity(0.28);
			return;
		}
		final double tileWidth = directNsweAtlas.getWidth() / 4d;
		final double tileHeight = directNsweAtlas.getHeight() / 4d;
		directNsweImage.setImage(directNsweAtlas);
		directNsweImage.setViewport(new Rectangle2D(
				(nswe / 4) * tileWidth, (nswe % 4) * tileHeight,
				tileWidth, tileHeight));
		directNsweImage.setOpacity(1d);
	}

	private void applyHeightEdit() {
		if (currentSession == null || selectedCellX < 0)
			return;
		final int layer = layerList.getSelectionModel().getSelectedIndex();
		if (layer < 0)
			return;
		try {
			final int[][] historyBlocks = selectedBlockCoordinates();
			final EditableRegionSession.BlockCheckpoint before =
					currentSession.captureBlocks(historyBlocks[0], historyBlocks[1]);
			int height = Integer.parseInt(heightEditor.getText().trim());
			height = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, height));
			height -= Math.floorMod(height, 8);
			if (selectedCells.isEmpty()) {
				currentSession.setHeight(selectedBlockX * 8 + selectedCellX,
						selectedBlockY * 8 + selectedCellY, layer, (short) height);
				renderer.invalidateGridBlock(selectedBlockX, selectedBlockY);
			} else {
				for (final CellSelection selection : selectedCells) {
					currentSession.setHeight(selection.blockX() * 8 + selection.cellX(),
							selection.blockY() * 8 + selection.cellY(),
							selection.layer(), (short) height);
					renderer.invalidateGridBlock(selection.blockX(), selection.blockY());
				}
			}
			refreshFromEditableSession();
			updateSelectedCellDetails(selectedCellX, selectedCellY);
			layerList.getSelectionModel().select(
					Math.min(layer, layerList.getItems().size() - 1));
			recordEdit("Height", before, historyBlocks);
			status.setText("Height updated | unsaved changes");
		} catch (final NumberFormatException exception) {
			heightEditor.setText(heightValue.getText());
			status.setText("Height must be an integer.");
		}
	}

	private void applyNsweEdit() {
		if (currentSession == null || selectedCellX < 0)
			return;
		final int layer = layerList.getSelectionModel().getSelectedIndex();
		if (layer < 0)
			return;
		short nswe = 0;
		if (nsweNorth.isSelected())
			nswe |= com.geo.GeoEngine.NORTH;
		if (nsweSouth.isSelected())
			nswe |= com.geo.GeoEngine.SOUTH;
		if (nsweWest.isSelected())
			nswe |= com.geo.GeoEngine.WEST;
		if (nsweEast.isSelected())
			nswe |= com.geo.GeoEngine.EAST;
		final int[][] historyBlocks = selectedBlockCoordinates();
		final EditableRegionSession.BlockCheckpoint before =
				currentSession.captureBlocks(historyBlocks[0], historyBlocks[1]);
		if (selectedCells.isEmpty()) {
			currentSession.setNswe(selectedBlockX * 8 + selectedCellX,
					selectedBlockY * 8 + selectedCellY, layer, nswe);
			renderer.invalidateGridBlock(selectedBlockX, selectedBlockY);
		} else {
			for (final CellSelection selection : selectedCells) {
				currentSession.setNswe(selection.blockX() * 8 + selection.cellX(),
						selection.blockY() * 8 + selection.cellY(),
						selection.layer(), nswe);
				renderer.invalidateGridBlock(selection.blockX(), selection.blockY());
			}
		}
		refreshFromEditableSession();
		updateSelectedCellDetails(selectedCellX, selectedCellY);
		layerList.getSelectionModel().select(
				Math.min(layer, layerList.getItems().size() - 1));
		recordEdit("NSWE", before, historyBlocks);
		status.setText("NSWE updated | unsaved changes");
	}

	private void applyNswePreset(final int preset) {
		final short nswe = (short) (~preset & 0x0f);
		nsweNorth.setSelected((nswe & com.geo.GeoEngine.NORTH) != 0);
		nsweSouth.setSelected((nswe & com.geo.GeoEngine.SOUTH) != 0);
		nsweWest.setSelected((nswe & com.geo.GeoEngine.WEST) != 0);
		nsweEast.setSelected((nswe & com.geo.GeoEngine.EAST) != 0);
		applyNsweEdit();
	}

	private void convertSelectedBlock(final byte type) {
		if (currentSession == null || selectedBlockX < 0)
			return;
		final int[][] blocks = selectedBlockCoordinates();
		final EditableRegionSession.BlockCheckpoint before =
				currentSession.captureBlocks(blocks[0], blocks[1]);
		final int converted = currentSession.convertBlocks(blocks[0], blocks[1], type);
		if (converted == 0)
			return;
		selectedCells.clear();
		selectedBlockX = selectedBlockY = selectedCellX = selectedCellY = -1;
		selectedLayer = -1;
		refreshFromEditableSession();
		recordEdit("Convert block", before, blocks);
		typeValue.setText(blockTypeName(type));
		clearSelectionInfo();
		status.setText(converted + " block(s) converted to " + blockTypeName(type)
				+ " | unsaved changes");
	}

	private void restoreSelectedBlock() {
		if (currentSession == null || selectedBlockX < 0)
			return;
		final Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
				"Restore the selected block from the loaded base geodata?",
				ButtonType.YES, ButtonType.CANCEL);
		confirmation.initOwner(stage);
		confirmation.setHeaderText("Restore from base");
		if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES)
			return;
		final int[][] blocks = selectedBlockCoordinates();
		final EditableRegionSession.BlockCheckpoint before =
				currentSession.captureBlocks(blocks[0], blocks[1]);
		final int restored = currentSession.restoreBlocks(blocks[0], blocks[1]);
		if (restored == 0)
			return;
		selectedCells.clear();
		selectedBlockX = selectedBlockY = selectedCellX = selectedCellY = -1;
		selectedLayer = -1;
		refreshFromEditableSession();
		recordEdit("Restore block", before, blocks);
		clearSelectionInfo();
		status.setText(restored + " block(s) restored from base | unsaved changes");
	}

	private int[][] selectedBlockCoordinates() {
		final List<Integer> keys = selectedCells.stream()
				.map(selection -> (selection.blockY() << 8) | selection.blockX())
				.distinct().toList();
		if (keys.isEmpty())
			return new int[][] { { selectedBlockX }, { selectedBlockY } };
		final int[] xs = new int[keys.size()];
		final int[] ys = new int[keys.size()];
		for (int i = 0; i < keys.size(); i++) {
			xs[i] = keys.get(i) & 0xff;
			ys[i] = keys.get(i) >>> 8;
		}
		return new int[][] { xs, ys };
	}

	private void recordEdit(final String label,
			final EditableRegionSession.BlockCheckpoint before,
			final int[][] blocks) {
		final EditableRegionSession.BlockCheckpoint after =
				currentSession.captureBlocks(blocks[0], blocks[1]);
		final long now = System.nanoTime();
		final EditHistoryEntry previous = undoHistory.peekLast();
		if (previous != null && previous.label().equals(label)
				&& now - previous.recordedAtNanos() <= 750_000_000L
				&& sameBlocks(previous.after(), after)
				&& redoHistory.isEmpty()) {
			undoHistory.removeLast();
			undoHistory.addLast(new EditHistoryEntry(label, previous.before(), after,
					previous.beforeRevision(), previous.afterRevision(), now));
			currentRevision = previous.afterRevision();
		} else {
			final long beforeRevision = currentRevision;
			currentRevision = ++nextRevision;
			undoHistory.addLast(new EditHistoryEntry(label, before, after,
					beforeRevision, currentRevision, now));
		}
		while (undoHistory.size() > EDIT_HISTORY_LIMIT)
			undoHistory.removeFirst();
		redoHistory.clear();
		updateHistoryControls();
	}

	private void undoEdit() {
		if (currentSession == null || undoHistory.isEmpty())
			return;
		final EditHistoryEntry entry = undoHistory.removeLast();
		currentSession.restoreCheckpoint(entry.before(), isValueOnlyEdit(entry));
		currentRevision = entry.beforeRevision();
		redoHistory.addLast(entry);
		refreshAfterHistory(entry.before());
		status.setText("Undo: " + entry.label() + " | unsaved changes");
	}

	private void redoEdit() {
		if (currentSession == null || redoHistory.isEmpty())
			return;
		final EditHistoryEntry entry = redoHistory.removeLast();
		currentSession.restoreCheckpoint(entry.after(), isValueOnlyEdit(entry));
		currentRevision = entry.afterRevision();
		undoHistory.addLast(entry);
		refreshAfterHistory(entry.after());
		status.setText("Redo: " + entry.label() + " | unsaved changes");
	}

	private void refreshAfterHistory(
			final EditableRegionSession.BlockCheckpoint checkpoint) {
		for (int i = 0; i < checkpoint.blockXs().length; i++)
			renderer.invalidateGridBlock(checkpoint.blockXs()[i], checkpoint.blockYs()[i]);
		selectedCells.clear();
		selectedBlockX = selectedBlockY = selectedCellX = selectedCellY = -1;
		selectedLayer = -1;
		refreshFromEditableSession();
		clearSelectionInfo();
		updateHistoryControls();
	}

	private void clearEditHistory() {
		undoHistory.clear();
		redoHistory.clear();
		currentRevision = savedRevision = nextRevision = 0L;
		updateHistoryControls();
	}

	private void updateHistoryControls() {
		if (undoButton != null) {
			undoButton.setDisable(undoHistory.isEmpty());
			undoButton.setText(undoHistory.isEmpty() ? "Undo"
					: "Undo " + undoHistory.getLast().label());
		}
		if (redoButton != null) {
			redoButton.setDisable(redoHistory.isEmpty());
			redoButton.setText(redoHistory.isEmpty() ? "Redo"
					: "Redo " + redoHistory.getLast().label());
		}
		if (historyState != null)
			historyState.setText("H " + undoHistory.size() + "/"
					+ EDIT_HISTORY_LIMIT + " | R " + redoHistory.size());
		if (clearHistoryButton != null)
			clearHistoryButton.setDisable(currentSession == null
					|| isEditorModified()
					|| (undoHistory.isEmpty() && redoHistory.isEmpty()));
		if (editState != null) {
			editState.setText(currentSession == null ? "No map"
					: isEditorModified() ? "Modified" : "Saved");
			final String stateColor = isEditorModified()
					? (darkTheme ? "#ffb74d" : "#914600")
					: (darkTheme ? "#b8d9c1" : "#17633a");
			editState.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: "
					+ stateColor + ";");
		}
	}

	private static boolean sameBlocks(
			final EditableRegionSession.BlockCheckpoint first,
			final EditableRegionSession.BlockCheckpoint second) {
		return Arrays.equals(first.blockXs(), second.blockXs())
				&& Arrays.equals(first.blockYs(), second.blockYs());
	}

	private static boolean isValueOnlyEdit(final EditHistoryEntry entry) {
		return entry.label().equals("Height") || entry.label().equals("NSWE");
	}

	private boolean isEditorModified() {
		return currentSession != null && currentRevision != savedRevision;
	}

	private void clearSavedHistory() {
		if (currentSession == null || isEditorModified()
				|| (undoHistory.isEmpty() && redoHistory.isEmpty()))
			return;
		final Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
				"Clear the saved Undo/Redo history? Geodata will not be changed.",
				ButtonType.YES, ButtonType.CANCEL);
		confirmation.initOwner(stage);
		confirmation.setHeaderText("Clear edit history");
		applyTheme(confirmation.getDialogPane());
		if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES)
			return;
		clearEditHistory();
		status.setText("Saved edit history cleared. Geodata was not changed.");
	}

	private boolean confirmDiscardChanges() {
		if (!isEditorModified())
			return true;
		final Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
				"Discard unsaved changes and open another map?",
				ButtonType.YES, ButtonType.CANCEL);
		confirmation.initOwner(stage);
		confirmation.setHeaderText("Unsaved geodata changes");
		applyTheme(confirmation.getDialogPane());
		return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.YES;
	}

	private void refreshFromEditableSession() {
		currentDetails = currentSession.snapshot();
		currentSnapshot = currentDetails.summary();
		final java.util.HashMap<RegionLoadManager.RegionKey, RegionSnapshot> updated =
				new java.util.HashMap<>(visibleRegions);
		updated.put(new RegionLoadManager.RegionKey(
				currentSnapshot.regionX(), currentSnapshot.regionY()), currentSnapshot);
		visibleRegions = Map.copyOf(updated);
		renderCurrent();
		/* Present the second PBO frame once; the first readback contains the prior edit. */
		cameraRenderPending = true;
	}

	private void saveRegion() {
		if (currentSession == null || !isEditorModified()) {
			status.setText("No changes to save.");
			return;
		}
		final Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
				"Overwrite " + currentSession.region().getFile().getName()
						+ " with the current geodata changes?",
				ButtonType.YES, ButtonType.CANCEL);
		confirmation.initOwner(stage);
		confirmation.setHeaderText("Save active L2J region");
		if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES)
			return;
		try {
			currentSession.save();
			savedRevision = currentRevision;
			updateHistoryControls();
			status.setText("Saved " + currentSession.region().getFile().getName());
		} catch (final IOException exception) {
			showError("Save failed: " + exception.getMessage());
		}
	}

	private static String nsweText(final short nswe) {
		final int blocked = (~nswe) & 0x0f;
		final StringBuilder result = new StringBuilder(4);
		if ((blocked & com.geo.GeoEngine.NORTH) != 0)
			result.append('N');
		if ((blocked & com.geo.GeoEngine.SOUTH) != 0)
			result.append('S');
		if ((blocked & com.geo.GeoEngine.WEST) != 0)
			result.append('W');
		if ((blocked & com.geo.GeoEngine.EAST) != 0)
			result.append('E');
		return result.isEmpty() ? "-" : result.toString();
	}

	private void clearSelectionInfo() {
		updateBlockConvertButtons(-1);
		clearPair(blockXValue, blockYValue);
		clearPair(cellXValue, cellYValue);
		typeValue.clear();
		layersValue.clear();
		heightValue.setText("-");
		updateDirectNsweImage(-1);
		heightEditor.setText("");
		heightEditor.setDisable(true);
		layerList.getItems().clear();
		nsweNorth.setSelected(false);
		nsweSouth.setSelected(false);
		nsweWest.setSelected(false);
		nsweEast.setSelected(false);
		nsweNorth.setDisable(true);
		nsweSouth.setDisable(true);
		nsweWest.setDisable(true);
		nsweEast.setDisable(true);
		nswePresetGroup.selectToggle(null);
		for (final ToggleButton button : nswePresets) {
			if (button != null)
				button.setDisable(true);
		}
		if (currentSnapshot != null)
			updateCoordinates(currentSnapshot);
	}

	private void updateBlockConvertButtons(final int type) {
		final boolean selected = type >= 0;
		convertFlatButton.setDisable(!selected || type == 0);
		convertComplexButton.setDisable(!selected || type == 1);
		convertMultiButton.setDisable(!selected || type == 2);
		boolean equalToBase = true;
		if (selected && currentSession != null)
			equalToBase = currentSession.region().dataEqualFor(
					currentSession.region().getGeoBlocks()
							[selectedBlockX][selectedBlockY]);
		restoreBlockButton.setDisable(!selected || equalToBase);
	}

	private void loadInputBindings() {
		InputBindings.beginConfigurationLoad();
		for (final InputBindings.Action action : InputBindings.Action.values())
			InputBindings.deserialize(action, preferences.getShared(
					InputBindings.propertyName(action), null));
		InputBindings.finishConfigurationLoad();
	}

	private static InputBindings.Action matchingAction(
			final javafx.scene.input.KeyEvent event) {
		for (final InputBindings.Action action : supportedInputActions())
			if (matchesBinding(event, action))
				return action;
		return null;
	}

	private static InputBindings.Action[] supportedInputActions() {
		return java.util.Arrays.stream(InputBindings.Action.values())
				.filter(action -> action != InputBindings.Action.TOGGLE_TERRAIN_WIREFRAME)
				.filter(action -> action != InputBindings.Action.FREEZE_GEODATA_GRID)
				.toArray(InputBindings.Action[]::new);
	}

	private static boolean matchesBinding(final javafx.scene.input.KeyEvent event,
			final InputBindings.Action action) {
		return InputBindings.matches(action, event.getCode().getCode(),
				modifierMask(event));
	}

	private static int modifierMask(final javafx.scene.input.KeyEvent event) {
		int modifiers = 0;
		if (event.isAltDown()) modifiers |= InputEvent.ALT_DOWN_MASK;
		if (event.isControlDown()) modifiers |= InputEvent.CTRL_DOWN_MASK;
		if (event.isShiftDown()) modifiers |= InputEvent.SHIFT_DOWN_MASK;
		if (event.isMetaDown()) modifiers |= InputEvent.META_DOWN_MASK;
		return modifiers;
	}

	private boolean navigateSelection(final InputBindings.Action action) {
		if (action == null)
			return false;
		return switch (action) {
		case SELECT_WEST -> {
			moveSelectedCell(-1, 0);
			yield true;
		}
		case SELECT_EAST -> {
			moveSelectedCell(1, 0);
			yield true;
		}
		case SELECT_NORTH -> {
			moveSelectedCell(0, -1);
			yield true;
		}
		case SELECT_SOUTH -> {
			moveSelectedCell(0, 1);
			yield true;
		}
		case RAISE_SELECTED_HEIGHT -> {
			adjustSelectedHeight(EditorSettings.SELECTION_HEIGHT_STEP);
			yield true;
		}
		case LOWER_SELECTED_HEIGHT -> {
			adjustSelectedHeight(-EditorSettings.SELECTION_HEIGHT_STEP);
			yield true;
		}
		default -> false;
		};
	}

	private void adjustSelectedHeight(final int delta) {
		if (currentSession == null || selectedCellX < 0 || selectedLayer < 0)
			return;
		if (!selectedCells.isEmpty()) {
			final int[][] historyBlocks = selectedBlockCoordinates();
			final EditableRegionSession.BlockCheckpoint before =
					currentSession.captureBlocks(historyBlocks[0], historyBlocks[1]);
			for (final CellSelection selection : selectedCells) {
				final int localX = selection.blockX() * 8 + selection.cellX();
				final int localY = selection.blockY() * 8 + selection.cellY();
				final int current = currentDetails.height(localX, localY, selection.layer());
				final short updated = (short) Math.max(Short.MIN_VALUE,
						Math.min(Short.MAX_VALUE, current + delta));
				currentSession.setHeight(localX, localY, selection.layer(), updated);
				renderer.invalidateGridBlock(selection.blockX(), selection.blockY());
			}
			refreshFromEditableSession();
			updateSelectedCellDetails(selectedCellX, selectedCellY);
			recordEdit("Height", before, historyBlocks);
			status.setText("Adjusted " + selectedCells.size()
					+ " selected cell layer(s) | unsaved changes");
			return;
		}
		final int current = currentDetails.height(
				selectedBlockX * 8 + selectedCellX,
				selectedBlockY * 8 + selectedCellY, selectedLayer);
		heightEditor.setText(Integer.toString(Math.max(Short.MIN_VALUE,
				Math.min(Short.MAX_VALUE, current + delta))));
		applyHeightEdit();
	}

	private void validateInputSmoke() {
		if (currentSession == null || selectedCellX < 0 || selectedLayer < 0)
			throw new IllegalStateException("Input smoke requires a selected cell.");
		final int localX = selectedBlockX * 8 + selectedCellX;
		final int localY = selectedBlockY * 8 + selectedCellY;
		final int smokeLayer = selectedLayer;
		final CellSelection original = new CellSelection(selectedBlockX, selectedBlockY,
				selectedCellX, selectedCellY, selectedLayer);
		final int before = currentDetails.height(localX, localY, smokeLayer);
		final long heightStarted = System.nanoTime();
		final int historyBefore = undoHistory.size();
		adjustSelectedHeight(8);
		final int raised = currentDetails.height(localX, localY, smokeLayer);
		adjustSelectedHeight(8);
		if (undoHistory.size() != historyBefore + 1)
			throw new IllegalStateException("Repeated height edits were not grouped.");
		undoEdit();
		if (currentDetails.height(localX, localY, smokeLayer) != before)
			throw new IllegalStateException("Grouped height undo failed.");
		redoEdit();
		if (currentDetails.height(localX, localY, smokeLayer) != before + 16)
			throw new IllegalStateException("Grouped height redo failed.");
		undoEdit();
		clearEditHistory();
		selectedBlockX = original.blockX(); selectedBlockY = original.blockY();
		selectedCellX = original.cellX(); selectedCellY = original.cellY();
		selectedLayer = original.layer();
		selectedCells.clear(); selectedCells.add(original);
		updateSelectedCellDetails(selectedCellX, selectedCellY);
		final long heightMicros = (System.nanoTime() - heightStarted) / 1_000L;
		if (raised != before + 8)
			throw new IllegalStateException("Page Up/Down height adjustment failed.");
		toggleSelectedBlockMenu(viewport.getWidth() / 2d, viewport.getHeight() / 2d);
		final boolean opened = selectedBlockMenu != null && selectedBlockMenu.isShowing();
		toggleSelectedBlockMenu(viewport.getWidth() / 2d, viewport.getHeight() / 2d);
		final boolean closed = selectedBlockMenu == null || !selectedBlockMenu.isShowing();
		if (!opened || !closed)
			throw new IllegalStateException("Alt+1 menu toggle failed.");
		for (int blockY = 0; blockY < 256; blockY++) {
			for (int blockX = 0; blockX < 256; blockX++) {
				if (currentSnapshot.blockType(blockX, blockY) != 0
						&& currentDetails.layerCount(blockX * 8, blockY * 8) > 0
						&& currentDetails.layerCount(blockX * 8 + 1, blockY * 8) > 0) {
					selectedBlockX = blockX; selectedBlockY = blockY;
					selectedCellX = 0; selectedCellY = 0; selectedLayer = 0;
					blockX = blockY = 256;
				}
			}
		}
		selectedCells.clear();
		selectedCells.add(new CellSelection(selectedBlockX, selectedBlockY,
				selectedCellX, selectedCellY, selectedLayer));
		final int selectionHistoryBefore = undoHistory.size();
		final int adjacentCellX = selectedCellX == 7 ? 6 : selectedCellX + 1;
		final int adjacentLocalX = selectedBlockX * 8 + adjacentCellX;
		final int adjacentLocalY = selectedBlockY * 8 + selectedCellY;
		if (currentSnapshot.blockType(selectedBlockX, selectedBlockY) != 0
				&& currentDetails.layerCount(adjacentLocalX, adjacentLocalY) > 0) {
			selectedCellX = adjacentCellX;
			selectedLayer = Math.min(selectedLayer,
					currentDetails.layerCount(adjacentLocalX, adjacentLocalY) - 1);
			updateMultipleSelection(false, true);
			if (selectedCells.size() != 2)
				throw new IllegalStateException("Shift append selection failed.");
			gestureSelections.clear();
			gestureSelections.addAll(selectedCells);
			updateMultipleSelection(false, true, true);
			if (selectedCells.size() != 2)
				throw new IllegalStateException("Drag selection toggled a visited cell.");
			cellSelectionGesture = true;
			selectionGestureAppend = true;
			final int selectedBeforeRelease = selectedCells.size();
			finishCellSelectionGesture();
			if (selectedCells.size() != selectedBeforeRelease)
				throw new IllegalStateException("Shift release reset the drag selection.");
			gestureSelections.clear();
			updateMultipleSelection(false, true);
			if (selectedCells.size() != 1)
				throw new IllegalStateException("Shift toggle deselection failed.");
		} else throw new IllegalStateException("No multi-selection smoke fixture found.");
		if (undoHistory.size() != selectionHistoryBefore)
			throw new IllegalStateException("Selection polluted Undo/Redo history.");
		validateArrowNavigationHeightSmoke();
		selectedBlockX = original.blockX(); selectedBlockY = original.blockY();
		selectedCellX = original.cellX(); selectedCellY = original.cellY();
		selectedLayer = original.layer();
		selectedCells.clear(); selectedCells.add(original);
		System.out.println("GEOEDITOR_INPUT=PAGE_UP_DOWN:PASS,ALT1_TOGGLE:PASS"
				+ ",ARROW_HEIGHT_LIMIT:PASS"
				+ ",MULTI_SELECTION:PASS,DRAG_SELECTION:PASS,SHIFT_RELEASE:PASS"
				+ ",SELECTION_HISTORY_ISOLATION:PASS"
				+ ",HISTORY_GROUPING:PASS,UNDO_REDO:PASS"
				+ ",HEIGHT_ROUNDTRIP_US:" + heightMicros);
	}

	private void validateArrowNavigationHeightSmoke() {
		final int configuredMaxZ = EditorSettings.SELECTION_MOVE_MAX_Z;
		for (int localY = 0; localY < GeoCoordinates.REGION_GEO_SIZE; localY++) {
			for (int localX = 0; localX < GeoCoordinates.REGION_GEO_SIZE;
					localX++) {
				if (currentDetails.layerCount(localX, localY) <= 0)
					continue;
				final byte blockType = currentSnapshot.blockType(
						localX >> 3, localY >> 3);
				final int targetX = SelectionNavigationPolicy.nextCoordinate(
						localX, 1, blockType);
				if (targetX < 0 || targetX >= GeoCoordinates.REGION_GEO_SIZE
						|| currentDetails.layerCount(targetX, localY) <= 0)
					continue;
				final int sourceHeight = currentDetails.height(localX, localY, 0);
				int targetLayer = 0;
				long difference = Math.abs((long) currentDetails.height(
						targetX, localY, 0) - sourceHeight);
				for (int layer = 1;
						layer < currentDetails.layerCount(targetX, localY); layer++) {
					final long candidate = Math.abs((long) currentDetails.height(
							targetX, localY, layer) - sourceHeight);
					if (candidate < difference) {
						difference = candidate;
						targetLayer = layer;
					}
				}
				if (difference <= 0 || difference > 32760)
					continue;
				selectedBlockX = localX >> 3;
				selectedBlockY = localY >> 3;
				selectedCellX = localX & 7;
				selectedCellY = localY & 7;
				selectedLayer = 0;
				selectedCells.clear();
				selectedCells.add(new CellSelection(selectedBlockX, selectedBlockY,
						selectedCellX, selectedCellY, selectedLayer));
				EditorSettings.SELECTION_MOVE_MAX_Z = 0;
				moveSelectedCell(1, 0);
				if (selectedBlockX * 8 + selectedCellX != localX)
					throw new IllegalStateException(
							"Arrow height limit did not block the target cell.");
				EditorSettings.SELECTION_MOVE_MAX_Z = 32760;
				moveSelectedCell(1, 0);
				if (selectedBlockX * 8 + selectedCellX != targetX
						|| selectedLayer != targetLayer)
					throw new IllegalStateException(
							"Arrow navigation did not select the nearest allowed layer.");
				EditorSettings.SELECTION_MOVE_MAX_Z = configuredMaxZ;
				return;
			}
		}
		EditorSettings.SELECTION_MOVE_MAX_Z = configuredMaxZ;
		throw new IllegalStateException(
				"No Arrow navigation height-difference fixture was found.");
	}

	private boolean isUiControlFocused() {
		final Node focusOwner = scene == null ? null : scene.getFocusOwner();
		return focusOwner instanceof Control
				&& focusOwner != viewportImage.getParent();
	}

	private static boolean isCameraMovementAction(final InputBindings.Action action) {
		return action == InputBindings.Action.MOVE_FORWARD
				|| action == InputBindings.Action.MOVE_BACKWARD
				|| action == InputBindings.Action.MOVE_LEFT
				|| action == InputBindings.Action.MOVE_RIGHT
				|| action == InputBindings.Action.MOVE_UP
				|| action == InputBindings.Action.MOVE_DOWN
				|| action == InputBindings.Action.MOVE_FASTER;
	}

	private void moveSelectedCell(final int deltaX, final int deltaY) {
		if (selectedBlockX < 0 || selectedCellX < 0 || selectedCells.size() > 1)
			return;
		final int sourceLocalX = selectedBlockX * 8 + selectedCellX;
		final int sourceLocalY = selectedBlockY * 8 + selectedCellY;
		if (selectedLayer < 0
				|| selectedLayer >= currentDetails.layerCount(
						sourceLocalX, sourceLocalY))
			return;
		final int sourceHeight = currentDetails.height(
				sourceLocalX, sourceLocalY, selectedLayer);
		final byte sourceBlockType =
				currentSnapshot.blockType(selectedBlockX, selectedBlockY);
		final int localX = SelectionNavigationPolicy.nextCoordinate(
				sourceLocalX, deltaX, sourceBlockType);
		final int localY = SelectionNavigationPolicy.nextCoordinate(
				sourceLocalY, deltaY, sourceBlockType);
		if (localX < 0 || localY < 0
				|| localX >= GeoCoordinates.REGION_GEO_SIZE
				|| localY >= GeoCoordinates.REGION_GEO_SIZE)
			return;
		final int targetLayers = currentDetails.layerCount(localX, localY);
		if (targetLayers <= 0)
			return;
		int targetLayer = 0;
		int targetHeight = currentDetails.height(localX, localY, 0);
		long nearestDifference = Math.abs((long) targetHeight - sourceHeight);
		for (int layer = 1; layer < targetLayers; layer++) {
			final int height = currentDetails.height(localX, localY, layer);
			final long difference = Math.abs((long) height - sourceHeight);
			if (difference < nearestDifference) {
				targetLayer = layer;
				targetHeight = height;
				nearestDifference = difference;
			}
		}
		if (!SelectionNavigationPolicy.isHeightAllowed(sourceHeight,
				targetHeight, EditorSettings.SELECTION_MOVE_MAX_Z)) {
			status.setText("Arrow move blocked | height difference "
					+ nearestDifference + " exceeds "
					+ EditorSettings.SELECTION_MOVE_MAX_Z);
			return;
		}
		selectedBlockX = localX >> 3;
		selectedBlockY = localY >> 3;
		selectedCellX = localX & 7;
		selectedCellY = localY & 7;
		selectedLayer = targetLayer;
		setPair(blockXValue, blockYValue, selectedBlockX, selectedBlockY);
		typeValue.setText(blockTypeName(
				currentSnapshot.blockType(selectedBlockX, selectedBlockY)));
		final int geoX = (currentSnapshot.regionX()
				<< GeoCoordinates.REGION_GEO_SHIFT) + localX;
		final int geoY = (currentSnapshot.regionY()
				<< GeoCoordinates.REGION_GEO_SHIFT) + localY;
		setPair(geoXValue, geoYValue, geoX, geoY);
		setPair(worldXValue, worldYValue,
				GeoCoordinates.worldXFromGeo(geoX),
				GeoCoordinates.worldYFromGeo(geoY));
		updateSelectedCellDetails(selectedCellX, selectedCellY);
		layerList.getSelectionModel().select(targetLayer);
		selectedCells.clear();
		selectedCells.add(new CellSelection(selectedBlockX, selectedBlockY,
				selectedCellX, selectedCellY, selectedLayer));
		status.setText("Selected cell " + selectedCellX + ", " + selectedCellY
				+ " in block " + selectedBlockX + ", " + selectedBlockY);
		renderCurrent();
	}

	private static String blockTypeName(final byte type) {
		return switch (type) {
		case 0 -> "Flat";
		case 1 -> "Complex";
		case 2 -> "MultiLayer";
		default -> "Unknown (" + type + ")";
		};
	}

	private void startCameraTimer() {
		new AnimationTimer() {
			@Override
			public void handle(final long now) {
				if (previousCameraTick == 0L) {
					previousCameraTick = now;
					return;
				}
				if (pressedActions.isEmpty() && !cameraRenderPending
						&& !renderer.hasPendingTextureWork()
						&& !neighborTexturesPending
						&& !skyTexturesPending) {
					previousCameraTick = now;
					return;
				}
				final double elapsedMillis = (now - previousCameraTick) / 1_000_000d;
				previousCameraTick = now;
				final boolean cameraInputEnabled = !regionTransitionPending;
				final double move = elapsedMillis
						* (pressedActions.contains(InputBindings.Action.MOVE_FASTER) ? 0.1d : 0.02d)
						* cameraSpeedPercent / 100d;
				boolean changed = cameraRenderPending || renderer.hasPendingTextureWork()
						|| neighborTexturesPending || skyTexturesPending;
				cameraRenderPending = false;
				if (cameraInputEnabled && pressedActions.contains(InputBindings.Action.MOVE_FORWARD)
						!= pressedActions.contains(InputBindings.Action.MOVE_BACKWARD)) {
					camera.moveForward(pressedActions.contains(InputBindings.Action.MOVE_FORWARD) ? -move : move);
					changed = true;
				}
				if (cameraInputEnabled && pressedActions.contains(InputBindings.Action.MOVE_LEFT)
						!= pressedActions.contains(InputBindings.Action.MOVE_RIGHT)) {
					camera.moveSideways(pressedActions.contains(InputBindings.Action.MOVE_LEFT) ? move : -move);
					changed = true;
				}
				if (cameraInputEnabled && pressedActions.contains(InputBindings.Action.MOVE_UP)
						!= pressedActions.contains(InputBindings.Action.MOVE_DOWN)) {
					camera.moveUp(pressedActions.contains(InputBindings.Action.MOVE_UP) ? move : -move);
					changed = true;
				}
				if (changed) {
					if (selectionRenderPending) {
						selectionRenderPending = false;
						renderer.requestSynchronousReadback();
					}
					renderCurrent(true);
					if (regionVisualTransitionPending && isCurrentRegionTextureReady())
						finishRegionVisualTransition();
					if (skyTexturesPending) {
						final int required = renderer.requiredSkyTextureCount();
						final int ready = renderer.readySkyTextureCount();
						updateNeighborLoading(ready, required, skyLoadingLabel());
						if (ready >= required && !skyCompletionHold) {
							skyCompletionHold = true;
							final PauseTransition hold = new PauseTransition(
									Duration.seconds(1));
							hold.setOnFinished(event -> finishSkyTextureLoading());
							hold.play();
						}
					}
					if (neighborTexturesPending) {
						final int desired = renderer.desiredAuxiliaryTextureCount();
						final int resident = renderer.residentAuxiliaryTextureCount();
						updateNeighborLoading(resident, Math.max(1, desired),
								"Loading terrain textures");
						if (resident >= desired) {
							neighborTexturesPending = false;
							renderer.setBulkTextureLoading(false);
							if (streamingUiBlocked)
								hideNeighborLoading();
						}
					}
					updateFullMapStreamingCenter();
				}
			}
		}.start();
	}

	private void finishSkyTextureLoading() {
		skyCompletionHold = false;
		skyTexturesPending = false;
		renderer.setBulkTextureLoading(neighborTexturesPending);
		if (streamingDeferredForSky) {
			streamingDeferredForSky = false;
			updateStreamingWindow();
		} else {
			final boolean streamingPending = streamingTask != null
					&& !streamingTask.isDone();
			if (streamingUiBlocked && !neighborTexturesPending
					&& !streamingPending && !mapCompletionHold)
				hideNeighborLoading();
		}
	}

	private String skyLoadingLabel() {
		return switch (skyStyle) {
		case 1 -> "Loading 16K Deep Navy";
		case 3 -> "Loading 16K Cyan Horizon";
		case 4 -> "Loading 16K Moon sky";
		default -> "Loading 16K Milky Way";
		};
	}

	private void updateCoordinates(final RegionSnapshot snapshot) {
		final int centerGeo = (snapshot.size() * 8) / 2;
		updateCoordinates(snapshot, centerGeo, centerGeo);
	}

	private void updateCoordinates(final RegionSnapshot snapshot,
			final int localGeoX, final int localGeoY) {
		final int geoX = GeoCoordinates.geoFromRegionLocal(
				snapshot.regionX(), localGeoX);
		final int geoY = GeoCoordinates.geoFromRegionLocal(
				snapshot.regionY(), localGeoY);
		setPair(regionXValue, regionYValue,
				snapshot.regionX() + 10, snapshot.regionY() + 10);
		setPair(geoXValue, geoYValue, geoX, geoY);
		setPair(worldXValue, worldYValue,
				GeoCoordinates.worldXFromGeo(geoX),
				GeoCoordinates.worldYFromGeo(geoY));
	}

	private void savePerformanceReport() {
		try {
			final File logs = new File(dataRoot.getParentFile(), "logs");
			java.nio.file.Files.createDirectories(logs.toPath());
			final String timestamp = java.time.LocalDateTime.now().format(
					java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss"));
			final File report = new File(logs, "performance-" + timestamp
					+ "_neighbors-" + neighborCount + ".txt");
			final LwjglRenderBackend.QueueStats queues = renderer.queueStats();
			final LwjglRenderBackend.GridStreamStats grid = renderer.gridStreamStats();
			final String content = "region=" + (currentSnapshot == null ? "none"
					: currentSnapshot.name()) + System.lineSeparator()
					+ "visibleGridRange=" + visibleGridRange + System.lineSeparator()
					+ PerformanceMetrics.formatReport(metrics.snapshot())
					+ "texturePreparationQueue=" + queues.texturePreparations()
					+ System.lineSeparator()
					+ "terrainPreparationQueue=" + queues.terrainPreparations()
					+ System.lineSeparator()
					+ "gpuUploadsLastFrame=" + queues.uploadsThisFrame()
					+ System.lineSeparator()
					+ "gpuDeleteQueue=" + queues.pendingDeletes()
					+ System.lineSeparator()
					+ "gpuDeletesLastFrame=" + queues.deletesThisFrame()
					+ System.lineSeparator()
					+ "gridChunkReusedFrames=" + grid.reusedFrames()
					+ System.lineSeparator()
					+ "gridChunksCreated=" + grid.rebuiltFrames()
					+ System.lineSeparator();
			java.nio.file.Files.writeString(report.toPath(), content,
					java.nio.charset.StandardCharsets.UTF_8);
			status.setText("Performance report saved: " + report.getName());
		} catch (final Exception exception) {
			showError("Unable to save performance report: " + exception.getMessage());
		}
	}

	private void showSettings() {
		final CheckBox textured = new CheckBox("Show textured terrain");
		textured.setSelected(texturedTerrain);
		final CheckBox transparency = new CheckBox("Transparency");
		transparency.setSelected(this.transparency);
		transparency.setTooltip(new javafx.scene.control.Tooltip(
				"Use the legacy 70% alpha blend on the geodata grid."));
		final CheckBox outline = new CheckBox("Draw Outline");
		outline.setSelected(drawOutline);
		outline.setTooltip(new javafx.scene.control.Tooltip(
				"Draw an opaque dark border around every visible geodata cell."));
		final CheckBox fullTextured = new CheckBox("Show full textured terrain");
		fullTextured.setSelected(fullTexturedTerrain);
		textured.setTooltip(new javafx.scene.control.Tooltip(
				"Show texture only on the active region terrain."));
		fullTextured.setTooltip(new javafx.scene.control.Tooltip(
				"Show textures on every loaded Full Map or Auxiliary region."));
		final Slider cameraSpeed = settingSlider(25, 400,
				cameraSpeedPercent, 25, true);
		final ChoiceBox<String> antialiasing = new ChoiceBox<>(
				FXCollections.observableArrayList(
						"Off", "2x MSAA", "4x MSAA", "8x MSAA", "16x MSAA"));
		antialiasing.getSelectionModel().select(switch (antialiasingSamples) {
		case 2 -> 1;
		case 4 -> 2;
		case 8 -> 3;
		case 16 -> 4;
		default -> 0;
		});
		final ChoiceBox<String> textureFiltering = new ChoiceBox<>(
				FXCollections.observableArrayList(
						"1x", "2x", "4x", "8x", "16x"));
		textureFiltering.getSelectionModel().select(switch (anisotropicFiltering) {
		case 2 -> 1;
		case 4 -> 2;
		case 8 -> 3;
		case 16 -> 4;
		default -> 0;
		});
		final ChoiceBox<String> sky = new ChoiceBox<>(FXCollections.observableArrayList(
				"Black", "Deep Navy", "Milky Way", "Cyan Horizon", "Moon"));
		sky.getSelectionModel().select(skyStyle);
		sky.setMaxWidth(Double.MAX_VALUE);
		sky.setTooltip(new javafx.scene.control.Tooltip(
				"Sky is drawn only above the camera horizon; the lower hemisphere remains black."));
		final GridPane visualization = new GridPane();
		visualization.setHgap(42);
		visualization.setVgap(8);
		visualization.add(textured, 0, 0);
		visualization.add(transparency, 1, 0);
		visualization.add(outline, 0, 1);
		visualization.add(fullTextured, 1, 1);
		visualization.add(uiLabel("Camera movement speed (%):"), 0, 2);
		visualization.add(cameraSpeed, 1, 2);
		visualization.add(uiLabel("Geometry antialiasing:"), 0, 3);
		visualization.add(antialiasing, 1, 3);
		visualization.add(uiLabel("Texture filtering:"), 0, 4);
		visualization.add(textureFiltering, 1, 4);
		visualization.add(uiLabel("Sky style:"), 0, 5);
		visualization.add(sky, 1, 5);

		final CheckBox grid = new CheckBox("Show Geodata Grid");
		grid.setSelected(geodataGrid);
		final CheckBox auxiliary = new CheckBox("Auxiliary terrains (visualization)");
		auxiliary.setSelected(auxiliaryTerrains);
		auxiliary.setTooltip(new javafx.scene.control.Tooltip(
				"Show neighboring regions as untextured wireframe context."));
		final CheckBox full = new CheckBox("Full Map (camera transitions)");
		full.setSelected(fullMap);
		full.setTooltip(new javafx.scene.control.Tooltip(
				"Change the active editable region when the camera crosses its border."));
		final Slider neighbors = settingSlider(0,
				RegionWindowPlan.NEIGHBOR_OPTIONS.length - 1,
				RegionWindowPlan.optionIndexForNeighborCount(neighborCount), 1, true);
		neighbors.setLabelFormatter(new javafx.util.StringConverter<>() {
			@Override public String toString(final Double value) {
				final int index = Math.max(0, Math.min(
						RegionWindowPlan.NEIGHBOR_OPTIONS.length - 1,
						(int) Math.round(value.doubleValue())));
				return Integer.toString(RegionWindowPlan.NEIGHBOR_OPTIONS[index]);
			}
			@Override public Double fromString(final String value) {
				return 0d;
			}
		});
		final ChoiceBox<String> neighborStyle = new ChoiceBox<>(
				FXCollections.observableArrayList(
						"01 - Thin Cyan Wireframe", "02 - Dark Solid + Edges",
						"03 - Height Gradient", "04 - Major Lines",
						"05 - Distance Fade", "06 - Solid + Contours",
						"07 - Blueprint Grid", "08 - Ridge Highlight",
						"09 - Depth Soft", "10 - Hybrid LOD"));
		neighborStyle.getSelectionModel().select(neighborVisualStyle - 1);
		neighborStyle.setMaxWidth(Double.MAX_VALUE);
		final Button resetMetrics = new Button("Reset metrics");
		resetMetrics.setMaxWidth(Double.MAX_VALUE);
		resetMetrics.setOnAction(event -> {
			metrics.reset();
			updateMetricsHud();
		});
		final Button saveMetrics = new Button("Save performance report");
		saveMetrics.setMaxWidth(Double.MAX_VALUE);
		saveMetrics.setOnAction(event -> savePerformanceReport());
		HBox.setHgrow(resetMetrics, Priority.ALWAYS);
		HBox.setHgrow(saveMetrics, Priority.ALWAYS);
		final HBox metricActions = new HBox(12, resetMetrics, saveMetrics);
		final GridPane performance = new GridPane();
		performance.setHgap(42);
		performance.setVgap(8);
		performance.add(full, 0, 0);
		performance.add(auxiliary, 1, 0);
		performance.add(uiLabel("Max visible neighbors:"), 0, 1, 2, 1);
		performance.add(neighbors, 0, 2, 2, 1);
		performance.add(uiLabel("Neighbor terrain style:"), 0, 3);
		performance.add(neighborStyle, 1, 3);
		performance.add(metricActions, 0, 4, 2, 1);
		final Runnable updateStreamingControls = () -> {
			final boolean enabled = full.isSelected() || auxiliary.isSelected();
			neighbors.setDisable(!enabled);
			fullTextured.setDisable(!enabled);
		};
		full.selectedProperty().addListener((o, oldValue, newValue) ->
				updateStreamingControls.run());
		auxiliary.selectedProperty().addListener((o, oldValue, newValue) ->
				updateStreamingControls.run());
		updateStreamingControls.run();

		final Slider gridRange = settingSlider(8, 96,
				Math.min(96, visibleGridRange), 8, true);
		final CheckBox fullRegionGrid = new CheckBox("Full Region (beyond 96)");
		fullRegionGrid.setSelected(visibleGridRange > 96);
		gridRange.setDisable(fullRegionGrid.isSelected());
		fullRegionGrid.selectedProperty().addListener((o, oldValue, newValue) ->
				gridRange.setDisable(newValue || !grid.isSelected()));
		final CheckBox circular = new CheckBox("Circular geodata grid");
		circular.setSelected(circularGrid);
		final javafx.scene.control.Spinner<Integer> arrowMaxZ =
				new javafx.scene.control.Spinner<>(0, 32760,
						EditorSettings.SELECTION_MOVE_MAX_Z, 8);
		arrowMaxZ.setEditable(true);
		arrowMaxZ.setMaxWidth(Double.MAX_VALUE);
		arrowMaxZ.setTooltip(new javafx.scene.control.Tooltip(
				"Arrow navigation cannot cross a greater height difference; 0 requires an equal height."));
		final javafx.scene.control.Spinner<Integer> heightStep =
				new javafx.scene.control.Spinner<>(8, 4096,
						EditorSettings.SELECTION_HEIGHT_STEP, 8);
		heightStep.setEditable(true);
		heightStep.setMaxWidth(Double.MAX_VALUE);
		heightStep.setTooltip(new javafx.scene.control.Tooltip(
				"Height changed by Page Up/Down, in multiples of 8."));
		final java.util.ArrayList<Integer> nsweTextureIds =
				new java.util.ArrayList<>();
		for (int id = 1; ; id++) {
			if (!new File(dataRoot, "textures/nswe_" + id + ".png").isFile())
				break;
			nsweTextureIds.add(id);
		}
		if (!nsweTextureIds.contains(EditorSettings.NSWE_TEXTURE_ID))
			nsweTextureIds.add(EditorSettings.NSWE_TEXTURE_ID);
		nsweTextureIds.sort(Integer::compareTo);
		final ChoiceBox<Integer> nsweTexture = new ChoiceBox<>(
				FXCollections.observableArrayList(nsweTextureIds));
		nsweTexture.getSelectionModel().select(
				Integer.valueOf(EditorSettings.NSWE_TEXTURE_ID));
		nsweTexture.setMaxWidth(Double.MAX_VALUE);
		final TextField geodataPath = new TextField(geodataRoot.getPath());
		geodataPath.setEditable(false);
		final Button browseGeodata = new Button("Browse...");
		browseGeodata.setOnAction(event -> {
			final javafx.stage.DirectoryChooser chooser =
					new javafx.stage.DirectoryChooser();
			chooser.setTitle("Choose the folder that contains Geodata files");
			final File current = new File(geodataPath.getText());
			if (current.isDirectory())
				chooser.setInitialDirectory(current);
			final File selected = chooser.showDialog(stage);
			if (selected != null)
				geodataPath.setText(selected.getAbsolutePath());
		});
		HBox.setHgrow(geodataPath, Priority.ALWAYS);
		final HBox geodataPathControl =
				new HBox(6, geodataPath, browseGeodata);
		final GridPane geodata = new GridPane();
		geodata.setHgap(12);
		geodata.setVgap(8);
		geodata.add(grid, 0, 0, 2, 1);
		geodata.add(circular, 0, 1, 2, 1);
		geodata.add(uiLabel("Visible grid range:"), 0, 2);
		geodata.add(gridRange, 0, 3, 2, 1);
		geodata.add(fullRegionGrid, 0, 4, 2, 1);
		geodata.add(uiLabel("Arrow navigation max height difference (Z):"), 0, 5);
		geodata.add(arrowMaxZ, 1, 5);
		geodata.add(uiLabel("Page Up/Down height step:"), 0, 6);
		geodata.add(heightStep, 1, 6);
		geodata.add(uiLabel("NSWE Texture:"), 0, 7);
		geodata.add(nsweTexture, 1, 7);
		geodata.add(uiLabel("Path to Geodata:"), 0, 8);
		geodata.add(geodataPathControl, 1, 8);
		final Runnable updateGridControls = () -> {
			circular.setDisable(!grid.isSelected());
			gridRange.setDisable(!grid.isSelected() || fullRegionGrid.isSelected());
			fullRegionGrid.setDisable(!grid.isSelected());
		};
		grid.selectedProperty().addListener((o, oldValue, newValue) ->
				updateGridControls.run());
		updateGridControls.run();

		final Dialog<Boolean> dialog = new Dialog<>();
		final boolean[] confirmed = { false };
		final boolean[] smokeTriggered = { false };
		dialog.initOwner(stage);
		dialog.setTitle("Config");
		dialog.setResizable(true);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		final Node nativeCancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
		nativeCancel.setVisible(false);
		nativeCancel.setManaged(false);
		dialog.setResultConverter(button -> Boolean.FALSE);
		final Button ok = new Button("Confirm");
		final Button cancel = new Button("Cancel");
		ok.setDefaultButton(true);
		cancel.setCancelButton(true);
		ok.setMaxWidth(Double.MAX_VALUE);
		cancel.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(ok, Priority.ALWAYS);
		HBox.setHgrow(cancel, Priority.ALWAYS);
		final HBox actions = new HBox(12, ok, cancel);
		actions.setAlignment(Pos.CENTER);
		ok.setOnAction(event -> {
			confirmed[0] = true;
			dialog.setResult(Boolean.TRUE);
			dialog.close();
		});
		cancel.setOnAction(event -> {
			confirmed[0] = false;
			dialog.setResult(Boolean.FALSE);
			dialog.close();
		});
		dialog.getDialogPane().setContent(new VBox(12,
				settingsSection("Visualization", visualization),
				settingsSection("Streaming and performance", performance),
				settingsSection("Geodata", geodata), actions));
		dialog.getDialogPane().setPrefSize(760, 740);
		dialog.setOnShown(event -> {
			if (!Boolean.getBoolean("geoeditor.configSmoke")
					&& !Boolean.getBoolean("geoeditor.configApplySmoke"))
				return;
			if (smokeTriggered[0]) {
				Platform.runLater(dialog::close);
				return;
			}
			smokeTriggered[0] = true;
			Platform.runLater(() -> {
				dialog.getDialogPane().applyCss();
				dialog.getDialogPane().layout();
				final boolean visible = ok.isVisible() && cancel.isVisible()
						&& actions.getBoundsInParent().getMaxY()
						<= dialog.getDialogPane().getHeight();
				System.out.println("GEOEDITOR_CONFIG_UI=ACTIONS_VISIBLE:" + visible
						+ ",WIDTH:" + Math.round(dialog.getDialogPane().getWidth())
						+ ",HEIGHT:" + Math.round(dialog.getDialogPane().getHeight()));
				if (Boolean.getBoolean("geoeditor.configApplySmoke")) {
					final PauseTransition apply = new PauseTransition(Duration.millis(150));
					apply.setOnFinished(applyEvent -> {
						full.setSelected(true);
						auxiliary.setSelected(true);
						sky.getSelectionModel().select(1);
						neighbors.setValue(1);
						arrowMaxZ.getValueFactory().setValue(64);
						heightStep.getValueFactory().setValue(16);
						nsweTexture.getSelectionModel().select(
								Integer.valueOf(1));
						camera.set(camera.x(), camera.y(), camera.z(), 0f,
								camera.rotationY());
						ok.fire();
					});
					apply.play();
				} else {
					final javafx.stage.Window window = dialog.getDialogPane()
							.getScene().getWindow();
					javafx.event.Event.fireEvent(window, new javafx.stage.WindowEvent(
							window, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
				}
			});
		});
		dialog.showAndWait();
		final boolean accepted = confirmed[0];
		if (Boolean.getBoolean("geoeditor.configSmoke"))
			System.out.println("GEOEDITOR_CONFIG_CLOSE=RESULT:" + accepted);
		if (accepted) {
			final int previousNeighborCount = neighborCount;
			final File previousGeodataRoot = geodataRoot;
			final boolean previousAuxiliaryTerrains = auxiliaryTerrains;
			final boolean previousFullMap = fullMap;
			texturedTerrain = textured.isSelected();
			this.transparency = transparency.isSelected();
			drawOutline = outline.isSelected();
			fullTexturedTerrain = fullTextured.isSelected();
			auxiliaryTerrains = auxiliary.isSelected();
			fullMap = full.isSelected();
			geodataGrid = grid.isSelected();
			circularGrid = circular.isSelected();
			EditorSettings.SELECTION_MOVE_MAX_Z = EditorSettings.normalizeSelectionMoveMaxZ(
					arrowMaxZ.getValue());
			EditorSettings.SELECTION_HEIGHT_STEP =
					normalizeHeightStep(heightStep.getValue());
			final Integer selectedNsweTexture =
					nsweTexture.getSelectionModel().getSelectedItem();
			if (selectedNsweTexture != null)
				EditorSettings.NSWE_TEXTURE_ID = selectedNsweTexture;
			final File selectedGeodataRoot = new File(geodataPath.getText());
			if (selectedGeodataRoot.isDirectory()) {
				geodataRoot = selectedGeodataRoot;
				EditorSettings.PATH_TO_GEO_FILES = geodataRoot.getPath();
				com.geo.GeoEngine.setGeodataDirectory(geodataRoot);
			}
			final int neighborIndex = Math.max(0, Math.min(
					RegionWindowPlan.NEIGHBOR_OPTIONS.length - 1,
					(int) Math.round(neighbors.getValue())));
			neighborCount = RegionWindowPlan.NEIGHBOR_OPTIONS[neighborIndex];
			cameraSpeedPercent = (int) Math.round(cameraSpeed.getValue());
			antialiasingSamples = switch (antialiasing.getSelectionModel()
					.getSelectedIndex()) {
			case 1 -> 2;
			case 2 -> 4;
			case 3 -> 8;
			case 4 -> 16;
			default -> 0;
			};
			anisotropicFiltering = switch (textureFiltering.getSelectionModel()
					.getSelectedIndex()) {
			case 1 -> 2;
			case 2 -> 4;
			case 3 -> 8;
			case 4 -> 16;
			default -> 1;
			};
			skyStyle = Math.max(0, sky.getSelectionModel().getSelectedIndex());
			renderer.setSkyStyle(skyStyle);
			final boolean streamingUpdateRequired =
					neighborCount != previousNeighborCount
					|| !geodataRoot.equals(previousGeodataRoot)
					|| auxiliaryTerrains != previousAuxiliaryTerrains
					|| fullMap != previousFullMap;
			skyTexturesPending = currentSnapshot != null
					&& skyStyle >= 1 && skyStyle <= 4
					&& !renderer.isSkyTextureReady();
			streamingDeferredForSky = skyTexturesPending
					&& streamingUpdateRequired;
			if (skyTexturesPending) {
				showNeighborLoading(renderer.requiredSkyTextureCount(),
						skyLoadingLabel());
			}
			renderer.setBulkTextureLoading(neighborTexturesPending
					|| skyTexturesPending);
			renderer.setTextureAnisotropy(anisotropicFiltering);
			neighborVisualStyle = neighborStyle.getSelectionModel().getSelectedIndex() + 1;
			renderer.setNeighborVisualStyle(neighborVisualStyle);
			final File nsweFile = new File(dataRoot,
					"textures/nswe_" + EditorSettings.NSWE_TEXTURE_ID + ".png");
			directNsweAtlas = nsweFile.isFile()
					? loadTransparentNsweAtlas(nsweFile) : null;
			if (selectedLayer >= 0 && selectedCellX >= 0)
				updateSelectedLayerNswe(selectedLayer);
			visibleGridRange = fullRegionGrid.isSelected() ? 256
					: Math.max(8, Math.min(96,
							(int) Math.round(gridRange.getValue())));
			applyTheme();
			savePreferences();
			renderCurrent();
			if (streamingUpdateRequired && !streamingDeferredForSky)
				updateStreamingWindow();
			if (Boolean.getBoolean("geoeditor.configApplySmoke"))
				System.out.println("GEOEDITOR_CONFIG_APPLY=RESULT:true,SKY:"
						+ skyStyle + ",NEIGHBORS:" + neighborCount
						+ ",ARROW_MAX_Z:" + EditorSettings.SELECTION_MOVE_MAX_Z
						+ ",HEIGHT_STEP:" + EditorSettings.SELECTION_HEIGHT_STEP
						+ ",NSWE_TEXTURE:" + EditorSettings.NSWE_TEXTURE_ID
						+ ",GEODATA_PATH:" + geodataRoot.getPath());
		}
	}

	private static Slider settingSlider(final double minimum,
			final double maximum, final double value,
			final double majorTick, final boolean labels) {
		final Slider slider = new Slider(minimum, maximum, value);
		slider.setShowTickLabels(labels);
		slider.setShowTickMarks(true);
		slider.setMajorTickUnit(majorTick);
		slider.setMinorTickCount(0);
		slider.setSnapToTicks(true);
		slider.setMaxWidth(Double.MAX_VALUE);
		return slider;
	}

	private static int normalizeHeightStep(final int value) {
		return Math.max(8, Math.min(4096, (value / 8) * 8));
	}

	private static int normalizeAntialiasing(final int samples) {
		return samples >= 16 ? 16 : samples >= 8 ? 8
				: samples >= 4 ? 4 : samples >= 2 ? 2 : 0;
	}

	private static int normalizeTextureFiltering(final int samples) {
		return samples >= 16 ? 16 : samples >= 8 ? 8
				: samples >= 4 ? 4 : samples >= 2 ? 2 : 1;
	}

	private static VBox settingsSection(final String title, final Node content) {
		final Label heading = uiLabel(title);
		heading.setStyle("-fx-font-weight: bold;");
		final VBox section = new VBox(8, heading, content);
		section.setPadding(new Insets(10));
		section.setStyle("-fx-border-color: derive(-fx-base, -25%); "
				+ "-fx-border-radius: 5; -fx-background-radius: 5;");
		return section;
	}

	private void applyTheme() {
		if (scene == null)
			return;
		final String base = darkTheme
				? "-fx-base: #202830; -fx-background-color: #11171d; "
						+ "-fx-control-inner-background: #171e25; "
						+ "-fx-text-base-color: #d8e5eb; -fx-text-background-color: #d8e5eb;"
				: "-fx-base: #d6e0e5; -fx-background-color: #cbd6dc; "
						+ "-fx-control-inner-background: #e6edf0; "
						+ "-fx-text-base-color: #182126; -fx-text-background-color: #182126;";
		scene.getRoot().setStyle(base);
		if (mainToolbar != null)
			mainToolbar.setStyle(darkTheme
					? "-fx-background-color: linear-gradient(#33414b, #202a31);"
					: "-fx-background-color: linear-gradient(#d3dde2, #aab8c1);");
		if (editorSidebar != null)
			editorSidebar.setStyle(darkTheme
					? "-fx-background-color: #171e25;"
					: "-fx-background-color: #e1e8ec;");
		if (editorBottomBar != null)
			editorBottomBar.setStyle(darkTheme
					? "-fx-background-color: #151b21; -fx-padding: 5;"
					: "-fx-background-color: #c5d1d8; -fx-padding: 5;");
		layerList.setStyle(darkTheme
				? "-fx-background-color: #171e25; -fx-control-inner-background: #171e25; "
						+ "-fx-border-color: #35434d;"
				: "-fx-background-color: #e6edf0; -fx-control-inner-background: #e6edf0; "
						+ "-fx-border-color: #9aaab3;");
		layerList.refresh();
		historyState.setStyle("-fx-font-size: 10px; -fx-font-family: monospace; "
				+ "-fx-text-fill: " + (darkTheme ? "#d8e5eb" : "#25343c") + ";");
		for (final Button button : List.of(convertFlatButton,
				convertComplexButton, convertMultiButton, restoreBlockButton))
			applyConversionButtonTheme(button);
		updateHistoryControls();
	}

	private String layerCellStyle(final boolean selected, final boolean empty) {
		if (empty)
			return "-fx-background-color: transparent;";
		final String background = darkTheme
				? (selected ? "#245f7a" : "#171e25")
				: (selected ? "#78adc5" : "#e6edf0");
		final String text = darkTheme
				? (selected ? "#ffffff" : "#d8e5eb")
				: (selected ? "#10232d" : "#182126");
		return "-fx-background-color: " + background + "; -fx-text-fill: " + text
				+ "; -fx-font-family: Consolas; -fx-padding: 4 7 4 7;";
	}

	private void validateCellLayersThemeSmoke() {
		if (layerList.getItems().isEmpty()
				|| layerList.getSelectionModel().getSelectedIndex() < 0)
			throw new IllegalStateException("Cell Layers smoke requires a selected layer");
		layerList.applyCss();
		layerList.layout();
		final String expected = layerCellStyle(true, false);
		final boolean selectedCellStyled = layerList.lookupAll(".list-cell").stream()
				.filter(ListCell.class::isInstance)
				.map(ListCell.class::cast)
				.anyMatch(cell -> cell.isSelected() && expected.equals(cell.getStyle()));
		if (!selectedCellStyled)
			throw new IllegalStateException("Cell Layers selected-row theme was not applied");
		System.out.println("GEOEDITOR_CELL_LAYERS_THEME=PASS");
	}

	private void applyConversionButtonTheme(final Button button) {
		final boolean disabled = button.isDisabled();
		final String background = darkTheme
				? (disabled ? "#28333b" : "#657681")
				: (disabled ? "#d5dde1" : "#aab9c2");
		final String text = darkTheme
				? (disabled ? "#8799a3" : "#f4f8fa")
				: (disabled ? "#78858c" : "#182126");
		button.setStyle("-fx-background-color: " + background
				+ "; -fx-text-fill: " + text
				+ "; -fx-background-insets: 0; -fx-background-radius: 3;"
				+ " -fx-border-width: 0; -fx-opacity: 1;");
	}

	private void applyTheme(final Node root) {
		root.setStyle(darkTheme
				? "-fx-base: #202830; -fx-background-color: #11171d; "
						+ "-fx-control-inner-background: #171e25; -fx-text-fill: #d8e5eb;"
				: "-fx-base: #d6e0e5; -fx-background-color: #cbd6dc; "
						+ "-fx-control-inner-background: #e6edf0; -fx-text-fill: #182126;");
	}

	private void savePreferences() {
		if (preferences == null)
			return;
		preferences.put("CAMERA_PITCH", camera.rotationX());
		preferences.put("CAMERA_YAW", camera.rotationY());
		preferences.put("TEXTURED_TERRAIN", texturedTerrain);
		preferences.put("FULL_TEXTURED_TERRAIN", fullTexturedTerrain);
		preferences.put("UI_THEME", darkTheme ? "DARK" : "LIGHT");
		preferences.put("AUXILIARY_TERRAINS", auxiliaryTerrains);
		preferences.put("FULL_MAP", fullMap);
		preferences.put("GEODATA_GRID", geodataGrid);
		preferences.put("NEIGHBOR_COUNT", neighborCount);
		preferences.put("CAMERA_SPEED_PERCENT", cameraSpeedPercent);
		preferences.put("ANTIALIASING_SAMPLES", antialiasingSamples);
		preferences.put("ANISOTROPIC_FILTERING", anisotropicFiltering);
		preferences.put("NEIGHBOR_VISUAL_STYLE", neighborVisualStyle);
		preferences.put("SKY_STYLE", skyStyle);
		preferences.putShared("VIS_GRID_RANGE", visibleGridRange);
		preferences.putShared("CIRCULAR_GEODATA_GRID", circularGrid);
		preferences.putShared("USE_TRANSPARENCY", transparency);
		preferences.putShared("DRAW_OUTLINE", drawOutline);
		preferences.putShared("SELECTION_MOVE_MAX_Z",
				EditorSettings.SELECTION_MOVE_MAX_Z);
		preferences.putShared("SELECTION_HEIGHT_STEP",
				EditorSettings.SELECTION_HEIGHT_STEP);
		preferences.putShared("NSWE_TEXTURE_ID", EditorSettings.NSWE_TEXTURE_ID);
		preferences.putShared("PATH_TO_GEO_FILES", geodataRoot.getPath());
		preferences.putShared("MINI_MAP_SIZE", miniMapSize);
		preferences.save();
	}

	private void showAbout() {
		final Dialog<ButtonType> dialog = new Dialog<>();
		dialog.initOwner(stage);
		dialog.setTitle("About L2GeoEditor");
		dialog.setResizable(true);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
		final Label title = uiLabel("L2GeoEditor");
		title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
		final Label version = uiLabel("GeoEditor 2.0.4.31");
		final String aboutTextColor = darkTheme ? "#d8e5eb" : "#25343c";
		version.setStyle("-fx-text-fill: " + aboutTextColor + ";");
		final VBox header = new VBox(4, iconView("l2jserverlogo.png", 194, 50),
				title, version);
		header.setAlignment(Pos.CENTER);
		header.setPadding(new Insets(10, 0, 8, 0));

		final VBox aboutPage = new VBox(12,
				aboutSection("Visualization and editing",
						"Explore Lineage II terrain and edit geodata cells with an OpenGL renderer."),
				aboutSection("Geodata compatibility",
						"Reads and writes the original L2J geodata formats while preserving untouched map data."),
				aboutSection("Project",
						"JavaFX/LWJGL architecture developed independently from the legacy UI."),
				aboutSection("Credits",
						"Original author: Forsaiken (Patrick).\nMaintained by Maneco2.\nMilky Way sky: NASA/GSFC SVS; Gaia DR2: ESA/Gaia/DPAC.\nThanks to the original project and the L2J community."),
				aboutSection("License", "GNU General Public License v3 or later."));
		aboutPage.setPadding(new Insets(16));
		final ScrollPane aboutScroll = new ScrollPane(aboutPage);
		aboutScroll.setFitToWidth(true);

		final TableView<AboutDetail> details = new TableView<>();
		final TableColumn<AboutDetail, String> propertyColumn = new TableColumn<>("Property");
		propertyColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().property()));
		propertyColumn.setPrefWidth(240);
		final TableColumn<AboutDetail, String> valueColumn = new TableColumn<>("Value");
		valueColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().value()));
		valueColumn.setPrefWidth(470);
		details.getColumns().add(propertyColumn);
		details.getColumns().add(valueColumn);
		details.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		details.setItems(FXCollections.observableArrayList(
				new AboutDetail("File description", "Lineage II Geodata Editor"),
				new AboutDetail("Type", "JavaFX desktop application"),
				new AboutDetail("File version", "2.0.4.31"),
				new AboutDetail("Product name", "L2GeoEditor"),
				new AboutDetail("Product version", "2.0.4.31"),
				new AboutDetail("Original author", "Forsaiken (Patrick)"),
				new AboutDetail("Maintainer", "Maneco2"),
				new AboutDetail("Java runtime", System.getProperty("java.version")),
				new AboutDetail("JavaFX", System.getProperty("javafx.runtime.version", "25.0.4")),
				new AboutDetail("LWJGL", org.lwjgl.Version.getVersion()),
				new AboutDetail("OpenGL", renderer.openGlVersion()),
				new AboutDetail("GPU", renderer.gpuRenderer()),
				new AboutDetail("Max texture", renderer.maximumTextureSize() + " px"),
				new AboutDetail("Milky Way sky",
						"NASA/GSFC SVS 32768x16384; Gaia DR2"),
				new AboutDetail("License", "GNU GPL v3 or later"),
				new AboutDetail("Original filename", "GeoEditor.exe")));

		final TableView<AboutControl> controls = new TableView<>();
		final TableColumn<AboutControl, String> actionColumn = new TableColumn<>("Action");
		actionColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().action()));
		actionColumn.setPrefWidth(260);
		final TableColumn<AboutControl, String> controlColumn = new TableColumn<>("Control");
		controlColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().control()));
		controlColumn.setPrefWidth(360);
		final TableColumn<AboutControl, String> typeColumn = new TableColumn<>("Type");
		typeColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().type()));
		typeColumn.setPrefWidth(90);
		controls.getColumns().add(actionColumn);
		controls.getColumns().add(controlColumn);
		controls.getColumns().add(typeColumn);
		controls.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		final Runnable refreshControls = () -> {
			final List<AboutControl> rows = new java.util.ArrayList<>();
			rows.add(new AboutControl("Look around", "Hold right mouse button and move", "Fixed", null));
			rows.add(new AboutControl("Select one cell", "Left-click a cell", "Fixed", null));
			rows.add(new AboutControl("Select multiple cells", "Hold Shift while selecting", "Fixed", null));
			rows.add(new AboutControl("Select a whole block", "Hold Alt while selecting", "Fixed", null));
			rows.add(new AboutControl("Unselect a cell", "Shift + left-click the selected cell", "Fixed", null));
			rows.add(new AboutControl("Change selection radius", "Mouse wheel or middle mouse button", "Fixed", null));
			rows.add(new AboutControl("Change radius faster", "Space + mouse wheel", "Fixed", null));
			rows.add(new AboutControl("Change selected height", "Ctrl + mouse wheel", "Fixed", null));
			rows.add(new AboutControl("Change height faster", "Ctrl + Space + mouse wheel", "Fixed", null));
			rows.add(new AboutControl("Resize minimap", "Drag its lower-right corner", "Fixed", null));
			for (final InputBindings.Action action : supportedInputActions())
				rows.add(new AboutControl(action.getLabel(), action.getDisplayText(), "Editable", action));
			controls.setItems(FXCollections.observableArrayList(rows));
		};
		refreshControls.run();
		final Button edit = new Button("Edit selected");
		edit.disableProperty().bind(controls.getSelectionModel().selectedItemProperty().isNull()
				.or(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
					final AboutControl selected = controls.getSelectionModel().getSelectedItem();
					return selected == null || selected.binding() == null;
				}, controls.getSelectionModel().selectedItemProperty())));
		edit.setOnAction(event -> {
			final AboutControl selected = controls.getSelectionModel().getSelectedItem();
			if (selected != null && selected.binding() != null && captureBinding(selected.binding()))
				refreshControls.run();
		});
		controls.setOnMouseClicked(event -> {
			if (event.getClickCount() == 2 && !edit.isDisabled())
				edit.fire();
		});
		final Button restore = new Button("Restore defaults");
		restore.setOnAction(event -> {
			InputBindings.resetAll();
			saveInputBindings();
			refreshControls.run();
		});
		final HBox controlActions = new HBox(10, edit, restore);
		controlActions.setAlignment(Pos.CENTER_RIGHT);
		final VBox controlsPage = new VBox(8, controls, controlActions);
		controlsPage.setPadding(new Insets(10));
		VBox.setVgrow(controls, Priority.ALWAYS);

		final TabPane tabs = new TabPane(
				new Tab("About", aboutScroll),
				new Tab("Details", details),
				new Tab("Controls", controlsPage));
		for (final Tab tab : tabs.getTabs())
			tab.setClosable(false);
		VBox.setVgrow(tabs, Priority.ALWAYS);
		final VBox content = new VBox(5, header, tabs);
		content.setPadding(new Insets(8, 12, 4, 12));
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().setPrefSize(800, 660);
		dialog.getDialogPane().setMinSize(680, 500);
		applyTheme(dialog.getDialogPane());
		if (Boolean.getBoolean("geoeditor.aboutSmoke")) {
			dialog.setOnShown(event -> {
				dialog.getDialogPane().applyCss();
				dialog.getDialogPane().layout();
				System.out.println("GEOEDITOR_ABOUT=TABS:" + tabs.getTabs().size()
						+ ",DETAILS:" + details.getItems().size()
						+ ",CONTROLS:" + controls.getItems().size()
						+ ",SIZE:" + Math.round(dialog.getDialogPane().getWidth())
						+ "x" + Math.round(dialog.getDialogPane().getHeight()));
				final PauseTransition close = new PauseTransition(Duration.millis(
						Long.getLong("geoeditor.aboutSmokeCloseMillis", 2500L)));
				close.setOnFinished(closeEvent -> dialog.close());
				close.play();
			});
		}
		dialog.showAndWait();
	}

	private VBox aboutSection(final String heading, final String text) {
		final Label headingLabel = uiLabel(heading);
		headingLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: "
				+ (darkTheme ? "#20cdeb" : "#006c86") + ";");
		final Label body = uiLabel(text);
		body.setWrapText(true);
		body.setStyle("-fx-text-fill: " + (darkTheme ? "#d8e5eb" : "#25343c") + ";");
		return new VBox(4, headingLabel, body);
	}

	private void showKeyBindingsDialog() {
		final Dialog<ButtonType> dialog = new Dialog<>();
		dialog.initOwner(stage);
		dialog.setTitle("Configure keys");
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
		final ListView<InputBindings.Action> actions = new ListView<>(
				FXCollections.observableArrayList(supportedInputActions()));
		actions.setCellFactory(list -> new ListCell<>() {
			@Override protected void updateItem(final InputBindings.Action action,
					final boolean empty) {
				super.updateItem(action, empty);
				setText(empty || action == null ? null
						: action.getLabel() + "    [" + action.getDisplayText() + "]");
			}
		});
		actions.getSelectionModel().selectFirst();
		final Button edit = new Button("Edit selected");
		edit.setOnAction(event -> {
			final InputBindings.Action action = actions.getSelectionModel().getSelectedItem();
			if (action != null && captureBinding(action))
				actions.refresh();
		});
		final Button restore = new Button("Restore defaults");
		restore.setOnAction(event -> {
			InputBindings.resetAll();
			saveInputBindings();
			actions.refresh();
		});
		dialog.getDialogPane().setContent(new VBox(10, actions,
				new HBox(10, edit, restore)));
		dialog.getDialogPane().setPrefSize(580, 560);
		applyTheme(dialog.getDialogPane());
		dialog.showAndWait();
	}

	private boolean captureBinding(final InputBindings.Action action) {
		final Dialog<Boolean> capture = new Dialog<>();
		capture.initOwner(stage);
		capture.setTitle("Edit " + action.getLabel());
		final Label message = uiLabel("Press the new key combination. Esc cancels.");
		message.setPadding(new Insets(28));
		capture.getDialogPane().setContent(message);
		capture.setOnShown(event -> capture.getDialogPane().getScene().addEventFilter(
				javafx.scene.input.KeyEvent.KEY_PRESSED, key -> {
			if (key.getCode() == KeyCode.ESCAPE) {
				capture.setResult(Boolean.FALSE);
				capture.close();
				key.consume();
				return;
			}
			final InputBindings.Action conflict = InputBindings.findConflict(action,
					key.getCode().getCode(), modifierMask(key));
			if (conflict != null) {
				message.setText("Already used by: " + conflict.getLabel());
				key.consume();
				return;
			}
			if (InputBindings.set(action, key.getCode().getCode(), modifierMask(key))) {
				saveInputBindings();
				capture.setResult(Boolean.TRUE);
				capture.close();
			} else {
				message.setText("Press a non-modifier key.");
			}
			key.consume();
		}));
		applyTheme(capture.getDialogPane());
		return capture.showAndWait().orElse(Boolean.FALSE);
	}

	private void saveInputBindings() {
		for (final InputBindings.Action action : InputBindings.Action.values())
			preferences.putShared(InputBindings.propertyName(action),
					InputBindings.serialize(action));
		preferences.save();
	}

	private void showError(final String message) {
		final Alert alert = new Alert(Alert.AlertType.ERROR,
				message == null ? "Unknown error" : message, ButtonType.OK);
		alert.initOwner(stage);
		alert.setTitle("GeoEditor");
		alert.setHeaderText("Unable to complete the operation");
		alert.showAndWait();
	}

	private static Label section(final String text) {
		final Label label = uiLabel(text);
		label.setStyle("-fx-font-weight: bold;");
		return label;
	}

	private static Label uiLabel(final String text) {
		return new Label(text);
	}

	@Override
	public void stop() {
		try {
			savePreferences();
		} finally {
			snapshotWindow.close();
			renderer.close();
			if (currentSession != null)
				currentSession.close();
		}
		if (Boolean.getBoolean("geoeditor.lifecycleSmoke"))
			System.out.println("GEOEDITOR_SHUTDOWN=PASS");
	}

	public static void main(final String[] args) {
		launch(args);
	}
}
