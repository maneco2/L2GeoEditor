# Dependencias de runtime do GeoEditor

## Java

- Requer Java 25 x64 instalado no sistema.
- A JRE/JDK nao e duplicada em `Build/`.
- O Launch4j procura `%JAVA_HOME%` e `%PATH%` e exige versao minima 25 x64.

## JavaFX

Modulos usados e empacotados:

- `javafx.base.jar`
- `javafx.graphics.jar`
- `javafx.controls.jar`
- DLLs graficas e runtimes nativos necessarios no Windows.

Nao sao empacotados FXML, Media, Web, Swing, SWT, incubators, `jfxmedia.dll`,
`jfxwebkit.dll`, GStreamer ou plugins de midia.

## LWJGL

JARs usados e empacotados:

- LWJGL core e natives Windows.
- GLFW e natives Windows.
- OpenGL e natives Windows.

## Dependencias legadas removidas

Depois da desacoplagem das etapas 21 e 22, a busca estatica, a compilacao sem
`libs/*`, o smoke completo e o executavel empacotado provaram que JOGL, GlueGen e
NativeWindow nao possuem consumidores no produto final. Os tres JARs, quatro
DLLs e a pasta `Build/libs/legacy` foram removidos na Fase 5, Etapa 24.

## Validacao

- Auditoria de classes: `-Xlog:class+load=info` durante o smoke completo.
- Smoke completo aprovado com geodata `23_18`, renderizacao, edicao, streaming,
  transicao Full Map e encerramento limpo.
- Pacote sem dependencias legadas validado pelo `Build/GeoEditor.exe`, com frame
  gerado, transicao de mapas e `GEOEDITOR_SHUTDOWN=PASS`.
