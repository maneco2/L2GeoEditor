<p align="center">
  <img src="Geo_Editor/src/assets/branding/readme-banner.png" alt="GeoEditor - Lineage II Geodata Editor">
</p>

<p align="center">
  A desktop editor for inspecting, visualizing and modifying Lineage II geodata.
</p>

<p align="center">
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white">
  <img alt="JOGL" src="https://img.shields.io/badge/Renderer-JOGL-00AEEF">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Windows-0078D4?logo=windows">
  <img alt="License" src="https://img.shields.io/badge/License-GPLv3-blue">
</p>

## Overview

GeoEditor provides a visual workflow for working with Lineage II geodata. It renders terrain through JOGL, supports the L2J binary geodata format and keeps the active region editable while neighboring regions are streamed incrementally for context.

The project focuses on predictable editing, responsive navigation and compatibility with existing L2J geodata.

## Features

- 3D geodata visualization using VBOs and GLSL shaders.
- Support for `FLAT`, `COMPLEX` and `MULTILAYER` blocks.
- Incremental Full Map streaming of neighboring regions.
- Circular selectable geodata grid limited to the active region.
- Region minimap with player marker and transition fade.
- Map selector synchronized with the active region and camera.
- Block conversion, layer editing, restoration and saving.
- Runtime performance and memory instrumentation.
- Parallel block selection using the available processors.
- Deterministic tests for parsing, geometry and state transitions.

## Requirements

- Windows 10 or newer.
- OpenJDK 25 available through `JAVA_HOME` or `PATH`.
- PowerShell 5.1 or newer.
- An OpenGL-compatible graphics driver.
- Launch4j 3.50 extracted locally to `Geo_Editor/tools/launch4j` when generating the portable EXE.

JOGL and GlueGen dependencies required by the editor are provided in `Geo_Editor/libs`.
The local Launch4j toolchain is intentionally ignored by Git and is not part of the application distribution.

## Build

Generate the portable Windows executable from `Geo_Editor`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\package-exe.ps1
```

The build generates:

```text
Geo_Editor/build/GeoEditor.exe
```

Application code is bundled into `GeoEditor.exe`. Runtime libraries, configuration and data remain external. Existing files under `build/data/geodata` are always preserved.

## Run

After packaging:

```powershell
cd .\build
.\GeoEditor.exe
```

The runtime geodata directory is:

```text
Geo_Editor/build/data/geodata
```

## Tests

Run the complete deterministic test suite:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\test-fast.ps1
```

The suite covers:

- Geodata coordinates, heights, NSWE and anti-corner-cut behavior.
- Binary parsing and serialization of all supported block types.
- Complete 65,536-block region reads.
- Region streaming and cancellation state.
- Map selector state and world-map transformations.
- Circular cell-grid masking and performance metrics.

## Project structure

```text
Geo_Editor/
├── java/          Java source code
├── tests/         Deterministic Java tests
├── src/assets/    Shaders, icons and terrain textures
├── libs/          JOGL, GlueGen and native libraries
├── tools/         Build, test and packaging scripts
└── build.xml      Ant build definition
```

## Geodata safety

- The active region is the only editable region.
- Neighboring Full Map regions are loaded for visualization.
- Modified regions retain the confirmation flow before switching.
- Circular grid masking never changes neighboring region data.

## License

GeoEditor is distributed under the terms of the GNU General Public License, version 3 or any later version. See [LICENSE.md](LICENSE.md).

## Credits

Original author: **Forsaiken (Patrick)**.
Maintained and modernized by **Maneco2**, with thanks for the original work.
