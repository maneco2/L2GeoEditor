# ProjectCleanup Agent

## Init Prompt

```text
/init
Chat limpo criado por ProjectCleanup.
Projeto ativo: .\Geo_Editor
Git root: .
Leia este arquivo antes de editar.
Responda em portugues e confirme a pasta ativa.
```

## Project Scope

- Git root: `.`.
- Source root: `.\Geo_Editor`.
- Produto atual: editor de geodata JavaFX/LWJGL/OpenGL.
- O handoff fica em `docs\codex\project-cleanup`, fora da source.
- Nao misturar outros projetos ou roots.

## Language Settings

- Responder em portugues, curto e direto.
- Codigo, logs, comandos, classes e caminhos podem permanecer em ingles.

## Quality Targets

- Preservar os formatos FLAT, COMPLEX e MULTILAYER, layers, alturas, NSWE, edicao, Undo/Redo e salvamento L2J.
- Manter separacao entre UI JavaFX/LWJGL e core de geodata.
- Validar alteracoes com busca, testes deterministas, smoke e `git diff --check`.

## Current State

- Branch: `develop`.
- Commit local e remoto: `eff9a2a6dbf577520ee7bda221e5bcfe24a6605f`.
- A raiz contem `Geo_Editor`, `LICENSE.md`, `README.md` e `docs`.
- O `.gitignore` ativo fica na raiz Git e cobre as subpastas do projeto.
- O source contem `docs`, `java`, `src`, `tests`, `tools`, `.gitattributes`, `.gitignore` e `BuildEditor.bat`.
- `src/main/java/com/geo/editor` contem UI, renderer, mapa e preferencias.
- `src/main/java/com/geo/core` contem coordenadas, snapshots, sessoes e bindings.
- `java/com/geo` contem engine, regioes, streaming, selecao e blocos.
- `java/com/util` e `java/com/exceptions` contem IO, memoria, metricas e excecoes.
- `tests` contem testes de core, parser, streaming, selecao e metricas.
- `src/main/resources` contem icones, imagens, mapas, texturas, ceus e manifesto.
- `src/assets` contem branding e o README da pasta de geodata.
- `tools` contem setup, compilacao, testes, smoke, embalagem e auditorias.
- Existe a alteracao local `D Geo_Editor/LICENSE`; preserve-a sem reset.

## Important Decisions

- O entrypoint e `GeoEditorApplication`.
- `Geo_Editor\BuildEditor.bat` e o ponto de build.
- Java 25 e o runtime de desenvolvimento.
- Geodata de teste permanece em pasta local autorizada e fora do envio.
- Publicacao Git exige autorizacao expressa.

## Relevant Files

- `Geo_Editor\BuildEditor.bat`: build completa.
- `Geo_Editor\tools\test-fast.ps1`: suite deterministica.
- `Geo_Editor\tools\editor-app.ps1`: compilacao e smoke.
- `Geo_Editor\tools\package-exe.ps1`: embalagem Windows.
- `Geo_Editor\tools\setup-javafx.ps1` e `setup-lwjgl.ps1`: dependencias locais.
- `Geo_Editor\docs`: arquitetura, setup, inventario e desempenho.
- `Geo_Editor\src\assets\geodata\README.md`: nota da pasta default de geodata.
- `.\.gitignore`: regras locais para Build, geodata, caches, toolchains e arquivos de controle.

## Validated Commands

```powershell
git -C . status --short --branch
git -C . diff --check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Geo_Editor\tools\test-fast.ps1
cmd.exe /d /c .\Geo_Editor\BuildEditor.bat
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Geo_Editor\tools\editor-app.ps1 -SmokeTest
```

As ultimas validacoes funcionais aprovaram suite, smoke visual, labels NSWE 16/16, selecao, edicao, streaming, transicao `23_18 -> 24_18` e shutdown.

## Next Actions

1. Confirmar os dois roots no novo chat.
2. Auditar imports, resources e scripts realmente usados.
3. Classificar candidatos a remocao com evidencia de consumidor.
4. Preparar dependencias locais somente quando uma nova build for solicitada.
5. Reexecutar suite e smoke com geodata local.
6. Comparar local/remoto e pedir permissao antes de publicar.

## Risks And Guardrails

- Preservar a alteracao local da licenca.
- Nao copiar geodata privada ou binarios para areas rastreadas.
- Nao alterar encoding ou line endings em massa.
- Nao executar reset destrutivo.
- Fechar processos Java/GeoEditor apos smoke.
- Nao salvar segredos nem dumps privados.

## Suggested Memory Note

GeoEditor usa o Git root `.`, source em `Geo_Editor`, branch `develop` e commit `eff9a2a`. O handoff fica fora da source.
