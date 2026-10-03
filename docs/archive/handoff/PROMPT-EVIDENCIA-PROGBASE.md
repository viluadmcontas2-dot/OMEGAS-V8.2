# Prompt: evidência definitiva do ProgBase original (feita uma única vez)

## Missão

Descobrir **como o ProgBase original funciona** em tudo o que o OMEGAS usa, e guardar isso como **evidência destilada, versionada e reutilizável** numa branch própria de evidência. Esta é a última vez que esse levantamento é feito. Daqui em diante, qualquer trabalho no OMEGAS **consulta** essa evidência em vez de reescavar o DUMP.

Não é para corrigir o OMEGAS agora. O contraste OMEGAS × original vem depois, usando esta evidência.

## Postura

Você é o CEO técnico. Decide o que é útil e corta o resto.

**Útil (entra):** só o que muda como o OMEGAS conversa com a ECU, lê a telemetria, interpreta curvas e mapas, ou grava. Ou seja:

1. **Transporte e protocolo:**
   - formato do quadro;
   - comandos de leitura e escrita;
   - códigos de status (ACK/NACK, ex.: `0x53`);
   - checksum;
   - tempos e retries;
   - sequência de conexão e sessão.
2. **Telemetria ao vivo:** para cada byte ou campo do quadro, levantar:
   - offset;
   - nome;
   - unidade;
   - escala e fórmula;
   - sinal;
   - validade (quando o valor é inválido ou ausente);
   - como o ProgBase exibe o campo.

   Campos: RPM, MAP, Petrol Inj., Gas Inj., combustível ou estado (gasolina/GNV/corte/transição), pressão do gás, temperaturas, level e demais.
3. **Parâmetros (endereços SC)**, só os que o OMEGAS lê, grava ou deveria ler. Para cada um:
   - SC e hex;
   - nome;
   - encoding (U8/U16/Q14…);
   - forma (escalar/vetor/indexado);
   - tamanho;
   - escala física;
   - R/W;
   - **quem consome** no ProgBase (tela, rotina, cálculo).
4. **AutoCal nativo:**
   - buffers por banda (`PETR_INJ_TBUF*`, `MNFLD_PRESS_BUF*`, `NUM_BUF_UPD_*`);
   - limiares (`CALIBRATION_VAL_1`, `VECT_AUTOCAL_*`);
   - `AUTO_CAL_ENABLE`, `MAX_AUTOMATCH`, `NUM_AUTOMATCH_EXECUTED`;
   - curvas RV;
   - `MUL_ACT`;
   - o algoritmo do AutoMatch, até onde o DUMP mostra;
   - as ações (reset GNV/gasolina/geral, AutoMatch manual) com o comando exato e o efeito observado;
   - o que o gráfico do ProgBase desenha e de onde vem cada camada.
5. **Curva K e Mapa K:**
   - endereços, encoding e eixos;
   - sequência de escrita (lotes, pausas);
   - readback;
   - backup e restauração do original.
6. **Level:**
   - `TIPO_SENSORE`, `RIF_SENSORE`, filtros, LEDs, `TANK_VOL[0]`;
   - como o original converte o valor bruto (raw) em nível exibido.

**Inútil (fica fora):**
- estética e textos de tela do ProgBase;
- licenciamento e atualização de firmware;
- parâmetros que o OMEGAS nunca vai tocar (registre só o nome numa linha de "ignorado de propósito", sem detalhar);
- teorias sem fonte.

## Fontes, nesta ordem

1. **O que já foi levantado:** `docs/reference/progbase/` na branch `claude/brave-darwin-wuliyo` (`README`, `CATALOGO`, `FORMULAS`, `LACUNAS`, `ACHADOS-DIRIGIDOS-20261002`, `OPORTUNIDADES`, `parametros.json`).
   - É o ponto de partida. **Não refaça** o que já tem fonte; **confira** e **reorganize**.
   - Onde houver lacuna (`LACUNAS.md`), é ali que você cava.
2. **Pasta "dump" no Google Drive:** DUMP do ProgBase (recursos Delphi `RT_RCDATA`, streams de componentes, rotinas, explicação de levels).
   - Leia só os arquivos relevantes para os 6 temas.
   - Use busca por nome e trechos antes de abrir arquivo grande.
3. **Logs reais do ProgBase / capturas seriais** (portmon/USB), se existirem no Drive. Eles provam o comando real na fiação. **Valem mais que inferência a partir do DUMP.**
4. **Sessões reais do OMEGAS** (pasta "sessões ômegas" no Drive). Servem só para **validar** fórmulas e escalas com números reais, não para descobrir o original.
   - **Não** leia todas: escolha poucas, que cubram gasolina, GNV, AutoMatch e escrita de curva.
   - Arquivos JSON repetidos são duplicatas.

## Regras de evidência (o que faltou nas tentativas anteriores)

- **Toda afirmação tem fonte rastreável:**
  - arquivo do Drive (nome + id) + offset/linha;
  - ou sessão + número de sequência do evento;
  - ou captura + timestamp.

  Sem fonte, não entra como fato.
- **Selo de confiança em cada item:**
  - `PROVADO`: visto na fiação/log, ou no DUMP sem ambiguidade;
  - `INFERIDO`: dedução forte, com o raciocínio em uma linha;
  - `DESCONHECIDO`: lacuna registrada, com o que provaria.
- **Sem redundância:** cada fato existe em **um** lugar; os outros arquivos apontam para ele.
- **Sem binário proprietário no repositório:** guarde só o trecho mínimo necessário (bytes em hex, poucas linhas) com a fonte. Nunca o arquivo inteiro do DUMP.
- **Números conferidos:** toda fórmula de escala é testada contra pelo menos um valor real (sessão ou log). O resultado do teste fica junto.

## Onde e como gravar

- **Branch dedicada:** `evidence/progbase-original`, criada a partir do HEAD atual de `origin/OmegasPlatina`.
  - Se esta sessão só puder dar push na branch designada dela, use a designada e escreva no topo do `README` qual é a branch de evidência.
  - Não mexa em código do app nem em outras branches.
- **Tudo dentro de `docs/evidence/progbase/`:**

  ```
  README.md            índice + como usar + mapa dos 6 temas + estado de cada um
  CHECKPOINTS.md       diário: o que foi feito, o que falta, onde parou (retomável)
  protocolo.md         transporte, quadro, comandos, status, checksum, tempos
  telemetria.md        tabela offset → campo → escala → validade → exibição
  parametros.md        tabela SC → nome → encoding → escala → R/W → consumidor
  autocal.md           buffers, limiares, AutoMatch, ações, camadas do gráfico
  curvas-mapas.md      Curva K, Mapa K, escrita, readback, backup
  level.md             sensor, referências, filtro, LEDs, conversão
  lacunas.md           só o que continua DESCONHECIDO e como provar
  registry.json        tudo acima em formato de máquina (uma entrada por endereço/campo/comando)
  ```

- **Formato de cada entrada do `registry.json`:**

  ```
  {id, tipo: comando|campo_telemetria|parametro|acao,
   endereco_ou_offset, nome, encoding, forma, tamanho, escala, unidade, rw,
   consumidor_no_progbase, observado_em: [fontes], confianca, nota}
  ```

- **Commits e checkpoints:**
  - **um commit por tema** concluído, com mensagem clara;
  - **depois de cada commit**, atualize `CHECKPOINTS.md` (feito / falta / próxima ação exata), para que outra sessão retome sem reler tudo;
  - CI não é necessário (é só documentação).

## Ordem de execução

1. Ler `docs/reference/progbase/*` e montar o esqueleto da estrutura acima. Fazer **commit 0** com o mapa do que já está provado e do que falta.
2. Seguir **protocolo → telemetria → parâmetros → AutoCal → curvas e mapas → level**, cada tema fechado antes do próximo.
3. Fechar `lacunas.md` e `registry.json`.
4. Terminar com uma **lista curta de "pontos para contrastar com o OMEGAS"**: onde vale comparar com o nosso código primeiro, por risco.

   Só a lista. **Não** faça o contraste agora.

## Entrega final

Em português simples, no máximo 12 linhas:
- o que ficou provado;
- o que continua desconhecido;
- onde está a branch;
- os 5 pontos mais importantes para contrastar com o OMEGAS.
