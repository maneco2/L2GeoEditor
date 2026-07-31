# Classificacao atual do source do GeoEditor

Atualizado em: `31/07/2026`

## Metodo

A auditoria leu integralmente as `43` fontes Java de producao (`12.106` linhas),
as `15` fontes de teste e os scripts de build, pacote e diagnostico. O alcance foi
cruzado por declaracoes, imports, referencias entre classes, lista real de
compilacao, entrypoint, manifest, testes e caminhos de resources. Nomes de assets
construidos em runtime foram tratados como consumidores validos.

Resultado estrutural: nao restam arquivos de source com zero bytes nem diretorios
vazios fora das areas locais de build, backup, dependencias e geodata de teste.

## Principal

- `src/main/java/com/geo/editor/`: aplicacao JavaFX/LWJGL, UI e renderizacao.
- `src/main/java/com/geo/core/`: camera, snapshots, streaming e sessao editavel.
- `java/com/geo/`, `java/com/util/` e `java/com/exceptions/`: modelo L2J,
  leitura, escrita e metricas ainda alcancados pelo produto.
- `src/main/resources/META-INF/MANIFEST.MF`: entrypoint
  `com.geo.editor.GeoEditorApplication` e versao `2.0.4.31`.

Todas as classes de producao restantes possuem consumidor em outra fonte; a unica
raiz sem referencia externa e o proprio entrypoint `GeoEditorApplication`.

## Resources preservados

- `icon/`: 27 arquivos; 11 usados diretamente e 16 presets `nswe-0..15`
  carregados por nome dinamico.
- `image/`: splash e tela de carregamento de vizinhos.
- `textures/nswe_1..6.png`: atlas selecionavel detectado em runtime.
- `textures/region/`: 130 mapas JPG carregados dinamicamente por coordenada.
- `textures/sky/`: dois ceus 16K usados pelo renderer e sua atribuicao.
- `src/assets/branding/readme-banner.png`: banner do README, fora da build.
- `src/assets/geodata/README.md`: placeholder versionavel; os `.l2j` locais sao
  somente dados temporarios de teste e nao devem ser enviados.

Nenhuma duplicata por SHA-256 foi encontrada. Busca literal isolada nao autoriza
remover os assets dinamicos acima.

## Testes e ferramentas necessarios

- `tools/core-source-files.ps1`, `editor-app.ps1`, `test-fast.ps1`,
  `package-exe.ps1` e `launch4j.xml`: build, suite, runtime e pacote.
- `tools/setup-javafx.ps1` e `setup-lwjgl.ps1`: instaladores locais definitivos.
- `compare-geodata.ps1` e `compare-geodata-semantic.ps1`: verificacao byte a
  byte e semantica de saves L2J.
- `compare-performance.ps1`: compara os relatorios de desempenho reproduziveis.
- `benchmark-regions.ps1` e `RealRegionBenchmark`: diagnostico explicito de
  parsing em geodata real; nao fazem parte do runtime, mas possuem runner direto.

As ferramentas de paridade foram preservadas porque validam exatamente os riscos
de save, formato e desempenho do editor. Toolchains baixadas em `tools/` continuam
locais e ignoradas.

## Removido nesta auditoria final

- `FastArrayList`, `TaskExecutor` e `WindowsWindowActivator`: sem consumidor e
  fora da lista real de compilacao.
- `TaskExecutorTest`: teste isolado que nao fazia parte da suite.
- `RegionDiscardPolicy` e seu teste: policy sem chamada no renderer.
- `DetailedRegionSnapshotLoader` e seu teste: loader duplicado usado somente em
  testes; a paridade agora usa `EditableRegionSession`, o caminho real do editor.
- Diretorios vazios residuais em `docs/previews`, `java/com/entity`,
  `java/com/geo/core`, `java/com/geo/unr`, `tests/com/geo/unr` e na arvore antiga
  `src/assets/textures`.
- Sufixo legado `-prototype` dos instaladores JavaFX/LWJGL.

## Validacao

- Suite deterministica completa aprovada apos as remocoes.
- `GeoEditor.exe` 2.0.4.31 regenerado.
- Smoke completo aprovado com FLAT, COMPLEX, MULTILAYER, range 96, grades
  distintas, edicao, Undo/Redo, save/reload, 9 regioes e transicao
  `23_18 -> 24_18`.
- Smoke direto do EXE terminou automaticamente com exit code `0`.
- Nenhum processo residual, `Build/.staging` ou `Build/GeoEditor.jar`.
- `git diff --check` aprovado; permanecem apenas avisos LF/CRLF conhecidos.

## Parecer

Nao foi encontrado outro arquivo ou diretorio removivel com evidencia suficiente.
O source restante e composto pelo produto, resources dinamicos, testes, ferramentas
de reproducao/paridade e documentacao. A proxima decisao continua sendo Git:
definir posteriormente se `develop` sera criado e promovido a ramo principal.
