# Auditoria de nomes antigos

Data: `31/07/2026`

## Escopo operacional

A busca cobriu codigo, testes, scripts, configuracoes, resources, nomes de arquivos e o pacote em `Build/`. Foram excluidos apenas `docs/`, `backups/`, `.git/` e `commit.txt`, que sao historico ou documentacao e nao participam do produto.

Nomes procurados:

- `ModernGeoEditor`
- `ModernPreferences`
- `BuildModern`
- `GeoEditorModern.jar`
- `launch4j-modern.xml`
- `modern-app.ps1`
- `package-modern-exe.ps1`
- `Modern Beta`

## Resultado

- Codigo, testes, scripts, resources e configuracoes: zero ocorrencias.
- Nomes de arquivos e diretorios operacionais: zero ocorrencias.
- Binarios e dados do pacote `Build/`: zero ocorrencias.
- A arvore vazia `modern/` foi removida.
- Ocorrencias restantes aparecem somente em `TASKS.md` e documentacao como registro da migracao e proibicao explicita.

Conclusao: o produto e a build usam somente o nome definitivo `GeoEditor`.
