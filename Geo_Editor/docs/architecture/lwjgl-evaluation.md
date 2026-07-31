# TASK-220 - LWJGL evaluation

Status: JOGL inventory, equivalence map and isolated prototype boundary defined
on 28/07/2026.

Historical record: the isolated source was removed in Phase 5, Step 23 after
the LWJGL production renderer, runtime smokes and metrics superseded it.

## Current renderer boundary

- 25 Java sources import JOGL or GlueGen APIs after removing unused imports
  from `DialogConfig`.
- `GeoEditor` creates the `GLCanvas` and starts the JOGL `Animator`.
- `GLDisplay` owns lifecycle, frame rendering, reshape and disposal callbacks.
- `AWTInput` receives keyboard and mouse events directly from the AWT canvas.
- `GLTerrain` owns terrain textures, VBOs and incremental GPU disposal.
- `VBOGLSLRenderer` owns the active geodata batches and shaders.
- `GLCamera`, `GLGUIRenderer` and selection rendering still use fixed-function
  OpenGL 2 operations.

Run `tools/inventory-jogl.ps1` to refresh the coupling inventory.

## API equivalence map

| JOGL/current API | LWJGL prototype equivalent |
| --- | --- |
| `GLCanvas` | Separate GLFW window and OpenGL context |
| `GLEventListener` | Explicit init/render/resize/dispose lifecycle |
| `Animator` | Controlled render loop with `glfwPollEvents` |
| `GL2` instance calls | Static `GL11`/`GL15`/`GL20` calls |
| GlueGen `Buffers` | LWJGL `BufferUtils` or `MemoryUtil` |
| `TextureIO`/`AWTTextureIO` | Explicit pixel conversion and texture upload |
| `GLU` projection helpers | Local matrix math; JOML only if later justified |
| AWT listeners | GLFW callbacks in the isolated prototype |

GLFW is appropriate for the isolated renderer experiment, but it creates its
own native window. It is not assumed to be a drop-in replacement for the
embedded Swing `GLCanvas`.

## Legacy compatibility risks

The renderer mixes modern VBO/shader paths with compatibility-profile features:
immediate mode, display lists, matrix stacks, client arrays and fixed-function
texturing. The first prototype must request an OpenGL 2.1 compatibility context
and preserve those semantics. Converting to a core profile is a separate future
project and must not be combined with the JOGL/LWJGL binding comparison.

Texture helpers, GLU picking, GLUT font lists, taskbar/window lifecycle and AWT
input are binding-specific. Terrain parsing, mesh builders, geodata selection,
streaming and file writing are not renderer bindings and should be reused
without modification.

## Isolated prototype contract

The first LWJGL prototype will:

1. Use LWJGL 3.4.1 core, GLFW and OpenGL modules for Windows x64.
2. Open a separate window; it will never be launched by `GeoEditor.exe`.
3. Load one real region in read-only mode through existing CPU-side builders.
4. Upload one terrain mesh and draw it without editing or saving geodata.
5. Record startup, FPS, frame time, resident memory and native dependency size.
6. Shut down the GLFW context and all GPU resources deterministically.

It will not replace `GLDisplay`, `GLTerrain`, `AWTInput`, Swing, selection,
streaming, Full Map or the production JOGL libraries.

## Gate before implementation

LWJGL 3.4.1 is compatible with JDK 25 and provides FFM support, but adding its
JARs and Windows native artifacts is a new local toolchain step. Implement the
prototype only after its dependency setup is isolated and ignored in the same
way as the JavaFX experiment.

No production renderer migration is approved by this document.

## Prototype result

The isolated prototype passed its first Windows smoke test with OpenJDK 25 and
LWJGL 3.4.1:

- Official core, GLFW and OpenGL JARs plus Windows natives: 6 artifacts,
  2,694,931 bytes.
- OpenGL context: 2.1 compatibility profile in a separate GLFW window.
- Real read-only source: `build/data/geodata/22_22.l2j`.
- Mesh: 65,536 real block-height vertices and 130,050 triangles.
- First measured readiness: 1,507 ms.
- GPU path: vertex/index buffer upload and indexed wireframe drawing.
- Shutdown: buffers, window, GLFW callbacks and parsed region released.

The prototype reuses only the production CPU-side geodata parser from the
current JAR. It does not call `GLDisplay`, `GLTerrain`, selection, streaming or
save code and is not included in `GeoEditor.exe`.

## Read-only benchmark

Five-second Windows run with the real `22_22` mesh:

- Readiness: 1,432 ms.
- Frames: 717.
- FPS: 143.20.
- Average frame: 6.968 ms.
- Worst frame: 50.810 ms.
- Peak resident memory: 288 MB, including the complete parsed `GeoRegion`.

An isolated JOGL clone was intentionally discarded after its legacy AWT canvas
lifecycle did not terminate reliably outside the official launcher. It would
not be valid to compare this lightweight LWJGL window numerically with the full
JOGL editor: the production path also renders textures, geodata cells, overlays,
selection and streaming regions.

The benchmark proves only that GLFW lifecycle, real-region parsing, indexed VBO
upload and deterministic LWJGL cleanup work on the current machine. It justifies
advancing the isolated prototype to one read-only texture and shader. It does
not justify replacing JOGL.

## Textured prototype result

The final isolated prototype added the real 4096 x 4096 `22_22.jpg`, explicit
RGBA upload and a GLSL 1.20 height-lighting shader. Texture coordinates are
interleaved with the existing real-region VBO, and texture/shader resources are
released explicitly.

Final five-second measurement:

- Readiness: 3,079 ms, including JPEG decoding and texture upload.
- FPS: 143.39.
- Average frame: 6.957 ms.
- Worst frame: 38.356 ms.
- Peak resident memory: 567.3 MB.

Frame throughput remained effectively unchanged. Startup and memory increased
because the complete image is decoded and uploaded. This confirms functional
LWJGL parity for the isolated read-only path, but not a production advantage.

## Final recommendation

Keep Swing and JOGL as the official platform. Retain JavaFX and LWJGL only as
migration references. Do not migrate the UI and renderer together. Reconsider a
renderer rewrite only if JOGL becomes unsupported or a measurable production
limitation cannot be solved in the current implementation.
