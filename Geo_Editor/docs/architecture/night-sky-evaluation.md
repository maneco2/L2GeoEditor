# Modern night sky evaluation

## Current render path

`LwjglRenderBackend.renderWindow` clears the offscreen framebuffer to opaque
black, configures the perspective camera, renders terrain, renders the geodata
grid and finally draws selection overlays. The framebuffer is then transferred
to JavaFX through the existing double-PBO readback.

The sky must therefore be rendered immediately after the color/depth clear and
before terrain. It must disable depth writes and depth testing while drawing,
then restore both before the existing projection and terrain path. The shader
must derive the view-ray horizon and shade only the upper hemisphere; the lower
hemisphere remains the current opaque black. This keeps stars, moon and gradient
above the terrain horizon and never visible below the meshes, without changing
picking, frustum selection, terrain VBOs or the PBO presentation order.

## Evaluated styles

| Style | GPU path | Expected cost | Readability |
| --- | --- | --- | --- |
| Black | Existing clear color | Zero | Maximum contrast and exact compatibility |
| Deep Navy | Different clear color | Zero | Softer depth perception with very high contrast |
| Stars | Full-screen procedural shader | One draw call and one fragment pass | Good only with sparse, dim stars |
| Cyan Horizon | Full-screen vertical gradient | One draw call and one fragment pass | Good if cyan remains below the auxiliary wireframe intensity |
| Moon | Gradient plus shader SDF disc | Same draw call as the gradient | Safe when small, dim and outside the central editing area |

## Recommended implementation

- Preserve `Black` as the initial/default style until visual and FPS comparison
  on `23_18` with range 96 is complete.
- Implement one reusable OpenGL 2.1 / GLSL 1.20 full-screen sky program. Avoid
  textures, allocations and shader recompilation per frame.
- Draw a static clip-space quad with depth test and depth writes disabled, shade
  only view rays above the horizon, keep lower rays black, then restore the
  exact previous GL state before terrain.
- Use uniforms for style and viewport. Generate sparse stars from a cheap stable
  hash; use the same fragment pass for the gradient and moon SDF.
- Keep star brightness low because the active grid is transparent and bright
  stars would otherwise show through red, green and blue geodata cells.
- Add `Sky style` to Config, persist it in `EditorPreferences`, and expose a
  deterministic smoke that captures every style and reports frame timings.

## Acceptance gate

Compare all five styles on `23_18`, `VIS_GRID_RANGE=96`, with identical camera,
MSAA and neighbor settings. Accept a non-black default only when selection
colors remain unambiguous and average/P95 frame time does not regress by more
than 3%. `Black` remains the fallback if shader creation fails.
