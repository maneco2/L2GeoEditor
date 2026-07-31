# Inventario atual de dependencias do GeoEditor

Atualizado em: `31/07/2026`

## Produto

- Entrypoint: `com.geo.editor.GeoEditorApplication`.
- Java 25, JavaFX 25.0.4, LWJGL 3.4.1 e OpenGL.
- Saida regeneravel unica: `Build/`.
- `BuildModern/`, Swing, JOGL, GlueGen e NativeWindow nao participam do produto.

## Grafo principal

```text
GeoEditorApplication
|-- UI: MapSelectorDialog, EditorPreferences, EditorSettings
|-- render: LwjglRenderBackend
|   |-- AsyncTextureLoader
|   |-- SharedGridRenderer
|   `-- TerrainVboRenderer
|-- visibilidade: GridVisibilitySelector
|-- core promovido: com.geo.core.*
|   |-- EditableRegionSession
|   |-- RegionSnapshotLoader / RegionSnapshotWindow
|   `-- CameraState, GeoCoordinates e InputBindings
|-- streaming: RegionLoadManager, RegionStreamingPolicy, RegionWindowPlan
|-- navegacao: SelectionNavigationPolicy
|-- formato L2J: GeoEngine, GeoRegion, GeoBlock, GeoCell, blocks e cells
|-- IO: GeoReader/Writer, GeoByteBuffer e streams
`-- metricas: PerformanceMetrics e WindowsProcessMemory

tools/package-exe.ps1
|-- tools/editor-app.ps1
|   |-- tools/core-source-files.ps1
|   |-- JavaFX base/graphics/controls
|   `-- LWJGL core/GLFW/OpenGL + natives Windows
|-- src/main/resources
|-- src/assets/geodata local, quando presente
`-- Launch4j 3.50 -> Build/GeoEditor.exe
```

## Fontes

- `43` fontes Java de producao, `12.106` linhas.
- `15` fontes de teste deterministico/diagnostico.
- Todas as classes de producao restantes possuem consumidor no grafo; a unica
  raiz sem referencia externa e o entrypoint.
- `core-source-files.ps1` compila somente o core necessario, nao uma arvore
  ampla de legado.

## Bibliotecas externas

Empacotadas:

- JavaFX: `javafx.base`, `javafx.graphics` e `javafx.controls`.
- LWJGL: core, GLFW e OpenGL, com os seis JARs Java/natives Windows.
- `GeoEditorCore.jar` gerado do source necessario.

Nao empacotadas: JavaFX Web/Media/FXML/Swing/SWT/incubators, JOGL, GlueGen,
NativeWindow e JRE. JavaFX, LWJGL e Launch4j baixados permanecem locais e
ignorados.

## Resources

- 27 icones, incluindo 16 presets NSWE carregados dinamicamente.
- 2 imagens de UI.
- 6 atlas NSWE selecionaveis.
- 130 mapas regionais JPG carregados por coordenadas.
- 2 texturas de ceu 16K e `ATTRIBUTION.md`.
- Manifest definitivo em `META-INF/MANIFEST.MF`.
- Banner documental em `src/assets/branding`, deliberadamente fora da build.
- `src/assets/geodata/README.md` e o unico conteudo de geodata que deve ser
  versionado; os `.l2j` atuais sao locais para smoke.

## Scripts mantidos

- Build/teste/pacote: `core-source-files.ps1`, `editor-app.ps1`,
  `test-fast.ps1`, `package-exe.ps1` e `launch4j.xml`.
- Setup: `setup-javafx.ps1` e `setup-lwjgl.ps1`.
- Paridade: `compare-geodata.ps1`, `compare-geodata-semantic.ps1`,
  `compare-performance.ps1` e `benchmark-regions.ps1`.

Os comparadores nao sao chamados pelo runtime, mas possuem finalidade direta e
reproduzivel para validar saves, semantica e desempenho; por isso nao sao lixo.

## Resultado

Nao existe fronteira Swing/JOGL alcancavel. A suite, o pacote, o smoke completo
com transicao `23_18 -> 24_18` e o smoke direto do EXE foram aprovados depois da
ultima remocao de classes sem consumidores.
