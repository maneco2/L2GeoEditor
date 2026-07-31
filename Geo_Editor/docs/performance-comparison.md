# Comparacao de desempenho antes/depois

Data: `31/07/2026`

## Cenario

- Baseline: source JavaFX/LWJGL anterior a promocao, reconstruido diretamente de `backups/GeoEditor-source-before-promotion-2026-07-31_07-23-45.zip`.
- Candidato: source reorganizado atual em `src/main`.
- Mesmo computador, Java 25, NVIDIA GeForce RTX 3060 Ti, viewport `1188x794`, regiao `23_18`, visible grid range `96`, MSAA `2x` e os mesmos dados/assets em `Build/data`.
- Tres execucoes completas e alternadas de cada versao. A tabela usa a mediana.
- Frame time: marcador deterministico de 160 frames `RANGE96`.
- Heap e memoria residente: maiores amostras `METRICS` de cada smoke, seguidas da mediana entre as execucoes.
- VRAM: pico de `Dedicated Usage` do contador Windows `GPU Process Memory`, filtrado pelo PID Java e amostrado a cada 250 ms.

## Resultado

| Metrica | Antes | Depois | Delta |
| --- | ---: | ---: | ---: |
| FPS derivado da mediana | 78,52 | 73,36 | -6,57% |
| Frame medio | 12.736 us | 13.631 us | +7,03% |
| Frame P95 | 15.415 us | 16.457 us | +6,76% |
| Pior frame | 18.731 us | 19.866 us | +6,06% |
| Heap observado | 1.017,26 MiB | 1.038,33 MiB | +2,07% |
| Memoria residente | 1.705,81 MiB | 1.691,66 MiB | -0,83% |
| VRAM dedicada do processo | 444,43 MiB | 448,45 MiB | +0,90% |

## Amostras de frame medio

- Antes: `13.087`, `12.671`, `12.736` us.
- Depois: `15.339`, `13.631`, `12.686` us.

## Conclusao

A reorganizacao nao introduziu crescimento relevante de memoria residente ou VRAM; heap aumentou 2,07%. O tempo de frame mediano ficou 7,03% maior e deve permanecer registrado como regressao observada, embora o candidato tenha apresentado maior variacao entre rodadas e sua melhor execucao (`12.686 us`) tenha empatado com a baseline. Esta comparacao mede a implementacao real do backup, nao uma estimativa historica.

Todos os smokes candidatos terminaram com `GEOEDITOR_SHUTDOWN=PASS`; nenhuma JVM permaneceu aberta e `Build/.staging` foi removida automaticamente.
