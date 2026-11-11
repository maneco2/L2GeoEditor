<!-- BEGIN PROJECTCLEANUP HANDOFF -->
# ChatCleanup Handoff

## Init

`/init`

Responder em portugues, de forma curta e direta. Usar somente o contexto deste
checkout. Nenhum worktree temporario foi usado nesta rodada. Nao criar commit,
push, publicacao ou envio sem autorizacao explicita.

## Project Scope

Este checkout e o GeoEditor Java desktop. O nucleo legado reutilizado pelo
produto esta em `java`, a aplicacao promovida esta em `src\main\java`, os
resources ativos estao em `src\main\resources` e os testes estao em `tests`.
Os scripts operacionais ficam em `tools`; `BuildEditor.bat` e o ponto de entrada
da validacao completa. O fixture real local e
`src\main\resources\geodata\23_18.l2j`, usado somente para teste e copiado ao
pacote durante a build. Nao misturar este root com outros projetos.

## Language Settings

Respostas em portugues. Manter nomes de classes, metodos, comandos, paths, logs
e identificadores tecnicos em ingles quando fizerem parte do codigo ou da
evidencia.

## Quality Targets

Manter a arvore enviada reduzida a codigo, testes, resources necessarios,
scripts e dependencias versionadas para reconstruir a build. `Build` e
`Artifacts` sao saidas regeneraveis e devem ser removidos apos a validacao.
OpenJFX, LWJGL e Launch4j ficam em `Geo_Editor\tools` e serao enviados ao
repositorio porque sao necessarios para reconstruir o EXE. O BAT deve manter a
janela aberta ate uma tecla ser pressionada.

## Current State

A auditoria estrutural encontrou 57 arquivos Java, 14 testes, 7 scripts
PowerShell operacionais, o BAT, o XML do Launch4j e resources de runtime,
incluindo 131 texturas de regiao, icones, splash, manifest e o fixture
`23_18.l2j`. Todos os 9 arquivos diretos de `tools` tem consumidores reais
no fluxo de build, teste, empacotamento ou setup. As flags de smoke e benchmark
em `editor-app.ps1` continuam sendo diagnosticos opcionais chamaveis e nao
foram confundidas com arquivos mortos.

O teste real confirma quatro fixtures, regiao `13,8`, tamanho `11.065.818` bytes
e o SHA-256 de `23_18.l2j`. A suite passou novamente com 13 testes depois dos
ajustes. O preview legado `selectMapLegacyPreview` e o import `Comparator` sem
referencias foram removidos. A build final apos esta auditoria gerou
`GeoEditor.exe` 2.0.4.31 com 334 KB, passou pelas cinco etapas e encontrou
somente 4 avisos externos no log, pertencentes ao Launch4j/XStream sob Java 25.

## Important Decisions

O handoff historico em `docs\codex\project-cleanup` foi removido e nao deve
ser recriado. O batch delega a saida para `tools\build.ps1`, mostra apenas
ambiente, testes, build, verificacao e resumo, e grava detalhes temporarios em
`Artifacts\Logs`. O empacotador sincroniza geodata a partir de
`src\main\resources\geodata`, sem manter uma segunda origem de resources.

O source tree preserva `BuildEditor.bat`, os 7 scripts PowerShell,
`launch4j.xml` e os toolchains necessários em `tools\openjfx`,
`tools\lwjgl` e `tools\launch4j`. O SDK JavaFX foi reduzido aos modulos
`base`, `controls`, `graphics` e `javafx.properties`; os demais JARs,
`src.zip` e DLLs excluidas pelo empacotador nao eram usados.

## Relevant Files

O entrypoint e `BuildEditor.bat`. A orquestracao curta esta em
`tools\build.ps1`; testes ficam em `tools\test-fast.ps1`; compilacao da camada
promovida fica em `tools\editor-app.ps1`; o empacotamento EXE fica em
`tools\package-exe.ps1`. O conjunto de fontes compilado e controlado por
`tools\core-source-files.ps1`. O teste real e
`tests\com\geo\RealGeodataFixtureTest.java`; os testes de edicao, snapshot,
coordenadas, memoria e politicas usam fixtures deterministicas temporarias.

## Validated Commands

Executar a partir de `Geo_Editor` com Java/JDK 25:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\test-fast.ps1
cmd.exe /d /c BuildEditor.bat
```

O primeiro comando deve terminar com `Tests completed and temporary classes
removed.` e `RealGeodataFixtureTest OK`. O BAT deve exibir cinco etapas,
gerar temporariamente `Build\GeoEditor.exe`, registrar avisos externos no log
e terminar com `BUILD FINALIZADA COM SUCESSO`, aguardando uma tecla. Depois
da validacao, remover `Build` e `Artifacts`. O handoff e validado pelo
`validate_agent_md.py` do skill `chat-cleanup-refresh` com `--strict-size`.

## Next Actions

1. Manter a arvore sem `Build`, `Artifacts`, logs, staging ou arquivos
   historicos descartados.
2. Quando uma build for necessaria, usar os tres toolchains versionados e
   executar `BuildEditor.bat`; os setups ficam como recuperação de checkout.
3. Antes de qualquer envio autorizado, conferir o Git real do root principal e
   decidir explicitamente a politica para o fixture binario `23_18.l2j`.

## Risks And Guardrails

Nao apagar `java`, `src`, `tests`, `tools` essenciais, resources de runtime,
manifest, `.gitattributes` ou `23_18.l2j`. Nao remover dependencias de sistema
como o JDK. Remover somente `Build`, `Artifacts` e arquivos de diagnostico
explicitamente identificados; conferir o path absoluto antes de `Remove-Item`.
Manter os toolchains versionados enquanto a build depender deles. Build verde
prova compilacao e testes, nao smoke visual ou comportamento runtime. Nao misturar
este checkout com outros roots nem usar worktree.

## Suggested Memory Note

GeoEditor e um editor JavaFX/LWJGL, com aplicativo em `Geo_Editor`, core legado,
fixture `23_18.l2j`, build em cinco etapas e handoff em `AGENTS.md`. A arvore
final remove `Build`/`Artifacts`, preserva os 9 itens operacionais de `tools`
e versiona os toolchains necessários para a build.

## Validation Checklist

- [x] Root principal fisico confirmado; nenhum worktree usado.
- [x] Auditoria de source, testes, resources, scripts e referencias concluida.
- [x] Handoff atualizado em `AGENTS.md`.
- [x] Handoff legado `docs\codex\project-cleanup` removido.
- [x] Fixture `23_18` validado por tamanho, hash e quatro fixtures.
- [x] Suite deterministica passou com 13 testes.
- [x] Build final passou pelas cinco etapas com EXE 2.0.4.31 e 13 testes.
- [x] `Build`/`Artifacts` foram removidos apos a build final.
- [x] Payload JavaFX reduzido foi validado pela build.
- [x] Nenhum commit, push ou envio realizado.

<!-- END PROJECTCLEANUP HANDOFF -->
