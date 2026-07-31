# Auditoria source-only

Data: `31/07/2026`

## Conteudo local excluido do envio

- `Build/`: distribuicao gerada, bibliotecas runtime, configuracoes e dados copiados.
- Qualquer `*.l2j` ou `*.unr`, independentemente da pasta; `src/assets/geodata/README.md` permanece versionavel.
- `MAPS/` e `src/assets/MAPS/`.
- `backups/` e arquivos compactados locais.
- JavaFX, LWJGL, Launch4j, FFmpeg, ImageMagick e demais toolchains baixaveis ignorados em `tools/`.
- Binarios gerados (`*.class`, `*.jar`, `*.dll`, `*.exe`) e temporarios (`*.log`, `*.tmp`).
- Estado local do Codex e da fila: `.agents/`, `.codex/`, `docs/codex/`, `TASKS.md` e `commit.txt`.
- Metadados de IDE e sistema operacional.

Por decisao do usuario, o proprio `.gitignore` permanece ignorado e exclusivamente local; ele nao faz parte do envio. As protecoes descritas aqui valem neste workspace e precisam ser recriadas manualmente em um clone novo.

## Arquivos grandes versionaveis

Os assets ativos `nasa-deep-star-map-16k.png` (191.007.280 bytes) e `nasa-lroc-moon-16k.png` (169.547.081 bytes) excedem o limite normal por arquivo do GitHub. Eles permanecem no produto e foram configurados em `.gitattributes` para Git LFS. O Git LFS 3.7.1 esta instalado.

Os demais assets candidatos ao envio ficam abaixo de 100 MB por arquivo. Imagens de regiao, splash, icones, banner e atribuicoes sao resources necessarios ou documentais e nao foram ignorados.

## Licencas e atribuicoes

- O source atual possui 62 arquivos com cabecalho GNU GPL.
- `LICENSE` declara `GPL-3.0-or-later` e aponta para o texto integral oficial.
- `src/main/resources/textures/sky/ATTRIBUTION.md` registra fontes e creditos NASA/GSFC, Gaia e LRO/LROC.

## Git e line endings

- Branch: `master`, acompanhando `github/master`.
- Nenhum arquivo sob `Build/`, nenhuma geodata L2J/UNR, backup ou toolchain baixavel esta rastreado.
- `core.autocrlf=true`; os avisos atuais indicam conversao futura LF para CRLF em arquivos ja modificados. Nao foi feita normalizacao em massa.
- `git diff --check` nao encontrou erro de whitespace; somente os avisos conhecidos de conversao de line endings.
- Nenhum branch, commit, stage, push ou envio foi criado nesta auditoria.
