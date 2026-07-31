# Setup e build limpa do GeoEditor

## Requisitos

- Windows x64.
- Java/JDK 25 x64 no `PATH`.
- PowerShell 5.1 ou superior.

Validacao do Java:

```powershell
java -version
javac -version
jar --version
```

Os tres comandos devem informar a versao 25. O GeoEditor usa o Java instalado no
sistema e nao inclui outra JRE em `Build/`.

## Dependencias locais

Instalar o OpenJFX 25.0.4:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\setup-javafx.ps1
```

Instalar o LWJGL 3.4.1, GLFW, OpenGL e natives Windows:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\setup-lwjgl.ps1
```

Para gerar o EXE, extrair o Launch4j 3.50 em `tools\launch4j`. Estes arquivos
sao ferramentas locais ignoradas pelo Git. A instalacao deve conter pelo menos:

```text
tools/launch4j/launch4j-3.50.jar
tools/launch4j/lib/
tools/launch4j/bin/windres.exe
```

## Build e testes

Para executar a suite e gerar `Build/GeoEditor.exe` por um unico ponto de
entrada:

```powershell
.\BuildEditor.bat
```

O batch interrompe imediatamente se os testes ou o empacotamento falharem.

Comandos separados para desenvolvimento:

Compilar o editor:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\editor-app.ps1
```

Executar a suite deterministica:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\test-fast.ps1
```

Gerar a distribuicao:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\package-exe.ps1
```

O resultado fica exclusivamente em `Build/`:

```text
Build/
  GeoEditor.exe
  configs/
  data/
  libs/
```

`Build/` e regeneravel e ignorada pelo Git. A build nao exige geodata real. Quando
`src/assets/geodata/` contiver arquivos locais para teste, eles serao copiados para
`Build/data/geodata/`; remova os `.l2j` temporarios antes de preparar o envio e
preserve o `README.md` default.

As dependencias efetivamente empacotadas e a evidencia de runtime estao em
`docs/runtime-dependencies.md`.

## Validacao obrigatoria de cada nova build

Toda nova build deve passar por:

1. Suite principal `test-fast.ps1`.
2. Smoke completo com geodata temporaria quando disponivel.
3. Troca/passagem entre regioes e Full Map.
4. Renderizacao, selecao, edicao, Undo/Redo e save/reload.
5. Inicializacao e encerramento pelo `Build/GeoEditor.exe`.
6. Confirmacao de que nenhum processo `GeoEditor`, `java` ou `javaw` ficou aberto.
7. Confirmacao de que `Build/.staging` e `Build/GeoEditor.jar` nao permaneceram no pacote.

O empacotador sincroniza assets incrementalmente para evitar recopia desnecessaria
de texturas grandes. Arquivos temporariamente bloqueados e inalterados nao impedem
a build; arquivos obsoletos continuam sendo removidos com retry. O staging e limpo
em bloco `finally`, inclusive quando compilacao, smoke ou pacote falham.
