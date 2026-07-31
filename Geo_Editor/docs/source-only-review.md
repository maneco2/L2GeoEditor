# GeoEditor - revisao source-only

Data: `31/07/2026`

## Estado da promocao

- Produto definitivo: `GeoEditor 2.0.4.31`.
- Implementacao principal: Java 25, JavaFX 25.0.4 e LWJGL 3.4.1.
- Entry point: `com.geo.editor.GeoEditorApplication`.
- Saida local unica: `Build/GeoEditor.exe`.
- Swing, JOGL, GlueGen, NativeWindow, prototipos e scripts legados foram removidos depois dos testes de paridade.
- Fases 1 a 6, etapas 1 a 33, concluidas. A estrategia Git permanece pendente e exige autorizacao do usuario.

## Validacao funcional

- Suite deterministica recompilada e aprovada.
- Startup sem mapa, Config, About e Map Selector aprovados.
- Smoke real aprovado com `23_18`, range 96, 9 regioes, Full Map e transicao `23_18 -> 24_18`.
- FLAT, COMPLEX, MULTILAYER, selecao, altura, NSWE, conversao, Undo/Redo, save/reload e encerramento foram exercitados.
- Os 16 icones Direct NSWE foram restaurados e o smoke exige `16/16` arquivos validos.
- `Build/GeoEditor.exe` possui versao `2.0.4.31`; o teste pelo executavel terminou com exit code zero.
- Nenhum processo `GeoEditor`, `java` ou `javaw` permanece aberto.
- `Build/.staging` e `Build/GeoEditor.jar` nao permanecem depois da build ou dos smokes.
- A auditoria integral final deixou `43` fontes de producao e `15` testes; todas as
  classes de producao restantes possuem consumidor no grafo do editor.
- Nao restam diretorios vazios nem arquivos source-only com zero bytes.

## Desempenho conhecido

A comparacao real com o source anterior reconstruido do backup foi executada tres vezes. A mediana atual apresentou `73,36 FPS` contra `78,52 FPS`, ou `+7,03%` de frame time. Heap ficou `+2,07%`, memoria residente `-0,83%` e VRAM dedicada `+0,90%`. A variacao e a regressao observada nao foram ocultadas; detalhes estao em `docs/performance-comparison.md`.

## Mudancas candidatas

O worktree possui:

- `260` arquivos rastreados removidos, principalmente Swing/JOGL, bibliotecas antigas, ferramentas sem consumidor e assets movidos.
- `18` arquivos rastreados modificados.
- `213` arquivos novos e versionaveis.
- `0` arquivos staged.

O conteudo novo nao ignorado soma aproximadamente `1.364,14 MiB`:

- `src/main/resources/textures/region`: 130 mapas visuais, aproximadamente `1.001,85 MiB`.
- `src/main/resources/textures/sky`: 3 arquivos, aproximadamente `343,85 MiB`.
- Codigo principal: 18 fontes, aproximadamente `0,35 MiB`.
- Testes, ferramentas source-only, documentacao, icones, splash e banner completam o restante.

Os dois PNGs de ceu acima de 100 MB usam Git LFS por `.gitattributes`. Todos os demais arquivos candidatos ficam abaixo de 100 MB individualmente.

## Conteudo estritamente local

Nao deve participar de commit ou envio:

- `Build/` e qualquer distribuicao gerada.
- Todos os `203` arquivos locais `*.l2j`, qualquer `*.unr` e `MAPS/`.
- `backups/`.
- JavaFX, LWJGL, Launch4j, FFmpeg, ImageMagick e demais toolchains baixaveis.
- Executaveis, JARs, DLLs, classes compiladas, archives e temporarios.
- `.gitignore`, que permanece local por decisao expressa do usuario.
- `TASKS.md`, `commit.txt`, `docs/codex/`, `.agents/` e `.codex/`.

O placeholder `src/assets/geodata/README.md`, o banner documental, o source, os testes, os resources ativos, `LICENSE`, atribuicoes e `.gitattributes` permanecem candidatos ao envio.

O README principal do Git root foi atualizado para JavaFX/LWJGL, `Build/`, Java
25, scripts definitivos e regras de exclusao de geodata real.

## Git, licencas e qualidade

- Branch atual: `master`, acompanhando `github/master`.
- Remotes configurados: `github` e `origin` Bitbucket.
- Nenhum `Build/`, L2J, UNR ou backup esta rastreado.
- Git LFS 3.7.1 esta instalado e configurado para os dois ceus 16K.
- O projeto declara `GPL-3.0-or-later`; 62 fontes atuais possuem cabecalho GPL e os assets NASA possuem atribuicao dedicada.
- `git diff --check` retorna sucesso. Permanecem apenas avisos conhecidos de futura conversao LF para CRLF com `core.autocrlf=true`; nenhuma normalizacao em massa foi aplicada.
- Busca operacional e no pacote encontrou zero nomes antigos proibidos.

## Riscos antes de qualquer envio

1. O conjunto e grande; Git LFS precisa estar habilitado no remoto e nos clones.
2. Como `.gitignore` nao sera versionado, novos clones nao receberao automaticamente as protecoes locais contra build/geodata.
3. A regressao mediana de frame time de 7,03% permanece registrada.
4. O worktree combina remocao do legado, promocao do editor, assets e documentacao; a separacao em quatro grupos deve ser preservada antes de commits.
5. Nenhuma operacao Git publicadora esta autorizada. Nao criar branch, stage, commit, tag ou push sem nova autorizacao explicita.

## Parecer

O source e a build local estao funcionalmente prontos para revisao do usuario. O projeto ainda nao esta autorizado para envio: faltam a revisao da separacao das mudancas, a decisao de branch e a autorizacao expressa para cada operacao Git posterior.
