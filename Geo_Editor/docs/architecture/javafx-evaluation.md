# TASK-219 - JavaFX evaluation

Status: inventory and isolated prototype boundary defined on 28/07/2026.

Historical record: the isolated source was removed in Phase 5, Step 23 after
the JavaFX implementation and its automated coverage superseded the prototype.

## Current UI

- Entry point: `com.GeoEditor`.
- Main window: `com.swing.FrameMain`.
- UI toolkit: Swing/AWT with 26 source files under `com.swing`.
- Renderer surface: heavyweight JOGL `GLCanvas`, owned by `GLDisplay`.
- Render loop: JOGL `Animator`; OpenGL callbacks stay on the JOGL render thread.
- UI updates: Swing Event Dispatch Thread through `SwingUtilities`.
- Background work: region loading and saving already return results to the EDT.
- Portable configuration: `build/configs/GeoEditor.xml`.

The Swing surface consists of one main frame, seven dialogs, six editor panels,
two map panels, the splash/loading overlay, theme management and reusable
controls. `FrameMain` currently coordinates both window widgets and application
actions, so replacing the complete frame first would mix UI migration with
renderer and streaming risk.

## JOGL integration boundary

`GeoEditor` creates the `GLCanvas`, initializes `GLDisplay`, attaches the
`GLEventListener` and starts the `Animator`. `FrameMain` embeds that canvas and
owns window lifecycle. `AWTInput` reads mouse and keyboard events from the same
AWT canvas.

The first JavaFX prototype must not move the `GLCanvas`, create a second OpenGL
context, call OpenGL from the JavaFX Application Thread or replace `AWTInput`.
Swing and JOGL remain the official editor while the prototype is evaluated.

## Toolchain finding

JavaFX is not bundled with the current Java 25 runtime and no `javafx-*.jar`
exists in the production libraries. The prototype uses the official OpenJFX
25.0.4 Windows x64 SDK under ignored `tools/openjfx`. Its modules and native
libraries are never copied to `build/libs` by the official build.

## Isolated prototype

The safest first prototype is a JavaFX-only settings window populated with
copies of the current configuration controls:

1. Light/Dark theme.
2. Camera speed.
3. Full Map and Auxiliary terrains.
4. Neighbor and visible-grid limits.
5. Read-only preview of the saved XML values.

It used the separate `com.geo.prototype.JavaFxConfigPrototype` entry point, was
not reachable from `GeoEditor.exe`, and read the production XML without writing
it. Its former runner `tools/javafx-prototype.ps1` was removed with the source in
Phase 5, Step 23. The comparison recorded startup time, resident memory,
packaged dependency size, theme quality and control behavior.

## Migration gates

1. Validate the local OpenJFX SDK and decide whether production should later use
   external modules or a custom runtime image.
2. Build the isolated settings prototype without touching `FrameMain`.
3. Compare it with `DialogConfig`.
4. Define UI-neutral configuration state/actions before migrating a real dialog.
5. Evaluate `SwingNode` or another bridge only after the dialog prototype; never
   assume that embedding the heavyweight `GLCanvas` is production-safe.
6. Keep JavaFX and LWJGL evaluations separate.

No production migration is approved by this document.

## Prototype comparison

Measured on Windows with OpenJDK 25 after aligning the prototypes to the same
settings-window size and core controls:

| Toolkit | Ready | Peak resident memory |
| --- | ---: | ---: |
| Swing baseline | 565 ms | 97.9 MB |
| JavaFX 25.0.4 | 1,004 ms | 198.1 MB |

The three core SDK jars (`javafx.base`, `javafx.graphics` and
`javafx.controls`) total 7.5 MB before the required Windows native libraries.
The measurement is an isolated window comparison; it must not be confused with
the complete editor, whose JOGL renderer and loaded maps dominate memory.

JavaFX provides cleaner scalable grouping, CSS-driven themes and more consistent
modern controls. Swing remains faster, lighter and already integrates directly
with the heavyweight JOGL canvas and AWT input.

## Decision

Keep Swing/JOGL as the production platform. JavaFX is technically compatible
with OpenJDK 25, but the current prototype does not justify a migration because
it roughly doubles resident memory for the isolated settings window and would
add a second UI thread plus renderer-embedding risk.

If a future dialog migration is approved, prefer external Windows-specific
OpenJFX modules beside the existing libraries. Do not introduce a custom runtime
image while the portable application intentionally relies on the user's
OpenJDK 25 installation. Revisit a runtime image only if the distribution model
itself changes.
