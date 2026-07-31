# Separacao recomendada das mudancas Git

Data: `31/07/2026`

Esta revisao organiza o worktree atual sem executar `git add`, commit, branch ou push. O objetivo e impedir que promocao do editor e remocao do legado sejam apresentados como uma unica mudanca opaca. O bloco UNR independente foi removido com autorizacao do usuario depois de confirmar zero consumidores no produto.

## Grupo 1 - Core e editor promovido

Escopo:

- `src/main/java/com/geo/core/`.
- `src/main/java/com/geo/editor/`.
- Mudancas necessarias em `java/com/geo/`, seus blocos/celulas e utilitarios de leitura, escrita e metricas ainda alcancados.
- `tests/com/geo/core/`, fixtures deterministicas e testes de carregamento, streaming, navegacao e memoria.

Motivo: este e o comportamento principal da release. O grupo contem modelo editavel, snapshots, renderer, UI JavaFX, LWJGL e integracao com o core L2J.

## Grupo 2 - Resources, build e distribuicao

Escopo:

- `src/main/resources/`, incluindo manifest, icones, mapas visuais e ceus.
- `src/assets/branding/readme-banner.png` e `src/assets/geodata/README.md`.
- `.gitattributes` com os dois ceus 16K em Git LFS.
- `tools/editor-app.ps1`, `tools/package-exe.ps1`, `tools/core-source-files.ps1`, `tools/launch4j.xml` e ajustes de teste/benchmark ligados ao produto.
- Scripts de setup JavaFX/LWJGL, sem os binarios baixados.

Motivo: separa codigo funcional de assets e empacotamento. `.gitattributes` precisa acompanhar os PNGs acima de 100 MB no mesmo commit futuro.

## Grupo 3 - Remocao do legado

Escopo das `260` exclusoes rastreadas:

- Fontes e testes Swing/JOGL.
- JARs/DLLs JOGL, GlueGen e NativeWindow.
- Assets/manifests dos caminhos antigos, depois da promocao dos consumidores validos.
- Build Ant, batch, entrypoints auxiliares e ferramentas sem consumidor.
- Ferramenta UNR independente sem consumidor.

Motivo: manter a eliminacao do legado revisavel e reversivel sem confundi-la com a implementacao nova. Este grupo deve vir depois dos grupos 1 e 2 para que o produto promovido ja exista quando os caminhos antigos forem removidos.

## Grupo 4 - Documentacao e governanca

Escopo:

- `LICENSE`.
- Documentos de arquitetura, dependencias, build, desempenho, auditoria de nomes e revisao source-only.
- Atribuicoes de assets permanecem junto aos resources correspondentes no grupo 2.

Nao incluir:

- `.gitignore`, `TASKS.md`, `commit.txt`, `docs/codex/`, `.agents/` ou `.codex/`; todos permanecem locais.

## Ordem futura recomendada

1. Core e editor promovido.
2. Resources/build com Git LFS.
3. Remocao do legado e da ferramenta UNR sem consumidor.
4. Documentacao e governanca.

`tools/test-fast.ps1` possui mudancas dos grupos 1 e 2 e exigira separacao por hunk durante um futuro staging autorizado.

## Estado

- Nenhum arquivo esta staged.
- Nenhum commit, branch ou push foi criado.
- A separacao esta pronta para a decisao de estrategia da Etapa 35.
