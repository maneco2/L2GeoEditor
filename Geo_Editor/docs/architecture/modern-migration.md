# JavaFX and LWJGL migration

The official migration target is OpenJFX 25.0.4 with LWJGL 3.4.1 on OpenJDK
25. The existing Swing and JOGL editor is frozen as a compatibility reference.
All active development, compilation and packaging now target `Build`.

## Migration rules

- Keep `.l2j`, configuration and coordinate formats compatible; UNR/MAPS tooling is outside this product.
- Migrate read-only rendering before editing or saving.
- Keep each phase runnable and reversible.
- Never maintain two independent copies of geodata business rules.
- Do not make the modern executable official until real-map parity tests pass.

## Planned phases

1. Integrated JavaFX application shell and deterministic LWJGL lifecycle.
2. LWJGL viewport hosted by the modern application.
3. Shared region loading, camera and coordinate services.
4. Textured terrain, geodata grid, minimap and overlays.
5. Selection and navigation without writes.
6. Editing, undo, conversion and save parity.
7. Portable JavaFX/LWJGL executable and removal of the legacy fallback.

The first phase now owns a hidden OpenGL 2.1 compatibility context, renders a
real LWJGL frame, reads it as BGRA and presents it inside a JavaFX `ImageView`.
This validates lifecycle and framebuffer transfer before real geodata and a
continuous render loop are introduced.

The modern editor is not feature-complete. It mirrors the main legacy layout,
supports real `Open`, visual `Select Map`, camera navigation, cell selection
and the first height, NSWE, conversion and save operations.

Renderer input is an immutable `RegionSnapshot`. Coordinate conversion lives
in `GeoCoordinates`; neither type depends on Swing, JOGL, JavaFX or LWJGL.

The LWJGL context is persistent. Right-drag controls the camera look; WASD and
Q/E move it. Arrow keys and the bottom direction buttons move the selected
geodata cell. UI controls do not share focus or shortcuts with the viewport.

The world viewport intentionally renders only the colored Geo Grid over a
black background, matching the geodata-editing view. Region JPGs are restricted
to the minimap and map selector and never become a terrain floor.

Modern camera, theme and visualization preferences use `MODERN_*` keys in
`Build/configs/GeoEditor.xml`. Saving preserves every legacy property,
and loading clamps camera values before applying them to the renderer.

Read-only block selection uses a second deterministic OpenGL picking pass.
Each visible terrain block receives an internal color ID, allowing JavaFX
mouse coordinates to resolve Block, Geo, World, type and height without
retaining the editable legacy region graph. The selected block is outlined
in the normal framebuffer and no geodata is modified.

The modern HUD reuses the existing UI-neutral `PerformanceMetrics` collector.
It reports frame/load timing, FPS, heap, process resident memory and loaded
region count, so metric calculations remain shared with the legacy editor.

`RegionSnapshotWindow` now reuses `RegionLoadManager`, `RegionWindowPlan` and
`RegionStreamingPolicy` for bounded background loading. Its immutable snapshot
cache retains overlap between equal/adjacent windows, removes regions outside
the selected plan and rejects results from obsolete generations.

Loaded snapshots use the real 2,048-cell region scale and offsets.
The active region also owns a compact detailed snapshot with offsets for all
2,048 x 2,048 logical cells and packed height/NSWE values for every layer.
Neighboring regions intentionally retain only lightweight block summaries.
The active region keeps full cell geometry and picking. Auxiliary height
terrain is not drawn behind the Geo Grid.

The modern geodata overlay follows the legacy renderer structure: Flat blocks
use one large cell, Complex blocks use 64 cells, and MultiLayer blocks draw
every stored layer. Colors and the selected NSWE atlas are rendered at real
cell heights inside a camera-bounded range. The NSWE atlas uses trilinear
mipmaps, legacy 70% color intensity and alternating Complex/MultiLayer tones
to avoid false selection-like patterns at distance. UNR maps are not part of
this path.

Depth testing now orders real multilayers correctly. Block picking is rendered
only on demand when the user clicks, instead of adding a full 65,536-block pass
to every camera frame. Nearby cells include the thin side geometry used by the
legacy renderer. The visible grid now uses one interleaved OpenGL VBO containing
position, color, alpha and NSWE UV data. Frustum culling and the legacy
visible-element selector remain required for complete renderer parity.
Transparent cells are sorted from farthest to nearest using the current camera
position and use the same 0.7 alpha blend as the legacy renderer.
Flat blocks and regular cells now share the fixed 0.1 world-unit border from
`GLCellRenderer`; grid heights no longer receive a modern-only vertical offset.
Every rendered element now carries the complete legacy volume: textured top,
bottom and four side faces. Geometry no longer changes between near and far
cells while the frustum selector is still being ported.
The NSWE V coordinate compensates for the bottom-up ImageIO upload, preserving
the top-down atlas row selected by each real cell value in the legacy renderer.
Normal Flat, Complex and MultiLayer colors now come from the legacy `Config`.
Block and cell picking renders the same detailed layer heights and 0.1 margins
as the visible VBO instead of an approximate block-height surface.
The picking color now encodes block, cell and logical layer. The active layer
is rendered with the legacy yellow color and its own NSWE atlas coordinates,
rather than an oversized outline above the highest layer.
Flat selections cover their complete 8 x 8 block. Renderer colors, the NSWE
atlas, minimap size and initial window geometry come from the shared XML so the
modern and legacy interfaces use the same configured presentation.
The modern sidebar also crops the configured NSWE atlas into the same clickable
128 x 128 directional panel; its four triangular hit areas mutate the selected
layer through the shared geodata editing session.
Picking samples the nearest valid GPU id within three pixels of the cursor.
This covers the intentional 0.1-unit visual borders without broadening a click
into a neighboring or unrelated block.

Neighbor summaries now parse the L2J stream directly instead of materializing
editable region graphs. The 9-region smoke heap dropped from about 1.2 GB to
347 MB while preserving block type and maximum-height parity.

The active center is no longer an immutable preview. `EditableRegionSession`
owns the real `GeoRegion`, while renderer snapshots are derived views of that
same model. Height, NSWE and block conversions mutate the legacy geodata graph;
Save serializes it atomically as L2J and marks the session clean.

The read-only sidebar exposes the real layers of the selected cell. A
second bounded picking pass resolves one of the 64 cells inside the selected
block, and layer changes synchronize height and Direct NSWE. Arrow keys and
directional buttons navigate logical cells across block boundaries.

The earlier orthographic orbit/pan approximation was removed. The modern
renderer now uses the legacy 45-degree perspective and the exact GLCamera
forward, sideways, vertical and rotation math. Input follows AWTInput:
right-drag looks, WASD moves relative to heading, Q/E moves vertically and
Space accelerates. The bottom bar now uses the same 16 NSWE preset order and
icons as `PanelNswe`; each preset updates the selected editable layer.

## Active build layout

- `Build/` is the only active JavaFX/LWJGL build.
- No parallel `BuildModern/` output is allowed.
- Staging, compilation and packaging stay inside `Build/`.
- `Build/` contains `GeoEditor.exe` plus external `configs`, `data` and
  `libs` folders.
- `Build/libs` is grouped into `core`, `javafx`, `lwjgl` and `legacy` until the legacy dependency is removed.
- The LWJGL frame uses the live JavaFX viewport dimensions, so the 45-degree
  perspective is not stretched when the application window changes size.
- Local ignored datasets, when explicitly supplied, stay under `Build/data`
  without modification during packaging.
