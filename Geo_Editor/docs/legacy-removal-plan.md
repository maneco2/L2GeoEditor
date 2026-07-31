# Remocao Swing/JOGL

Status: os 70 arquivos foram removidos com autorizacao explicita do usuario em
31/07/2026.

## Evidencia

- `editor-app.ps1` compila somente a lista explicita de `core-source-files.ps1`.
- `test-fast.ps1` compila e executa somente o core e testes principais.
- Smoke `23_18` aprovado com zero classes `com.swing`, `com.jogl` ou
  `javax.media.opengl` carregadas.
- O `GeoEditorCore.jar` nao contem `com/swing`, `com/jogl`, `com/entity` ou
  `com/Config`.

## Alvos sem consumidores

- `java/com/swing/`: 26 arquivos.
- `java/com/jogl/`: 28 arquivos.
- `tests/com/swing/`: 3 arquivos.
- `tests/com/jogl/`: 6 arquivos.
- `java/com/GeoEditor.java`.
- `java/com/Config.java`.
- `java/com/geo/GeoBlockSelector.java`.
- `java/com/entity/SelectionState.java`.
- `tests/com/ConfigTest.java`.
- `tests/com/geo/MapSelectorStateTest.java`.
- `tests/com/geo/WorldMapPanelTest.java`.

Total removido: 70 arquivos. A remocao permanece recuperavel pelo Git e pelo
backup source-only criado antes da promocao.

Validacao posterior:

- Nenhum alvo permaneceu no filesystem.
- Busca do codigo ativo sem imports Swing/JOGL ou referencias apagadas.
- Suite principal aprovada sem `libs/*`.
- Build e smoke visual `23_18` aprovados.
- `Build/GeoEditor.exe` regenerado com sucesso.
