# Catálogo das funções nativas do ProgBase original que podem ajudar (2026-10-18)

Pesquisa só de leitura: nada foi gravado na ECU, nada em `app/` foi mudado, nada foi commitado.
Pergunta do dono: além do anti-stallo, que outras funções **da própria ECU** (Landi Renzo OMEGAS/MP48) ajudam em apagão no GNV, retomada depois do corte, troca gasolina↔GNV, marcha lenta e mistura pobre/rica, sem encher o app. Curva K, Mapa K e AutoCal ficam de fora porque o app já tem.

## Legenda e fontes

- **PROVADO** = visto no arquivo, no fio (log) ou no código do ProgBase.exe. **INFERIDO** = dedução (pelo nome, pelo rótulo, pela faixa ou pela coincidência com um padrão). **DESCONHECIDO** = sem prova.
- `CFG:n` = `docs/evidence/dfm-original/TFORMCONFIG.txt`, linha n (tela original). `SD:n` = `docs/evidence/dfm-original/TSTREAMDATI.txt`, linha n (objeto da ECU).
- `LOG:n` = linha do pedido no Portmon `PortmonLOGNOVO.LOG` (12/08/2026, ECU do dono). Vetores lidos com `tools/sessoes/portmon_ler_vetor.py`. Escalares `09`, indexados `0A` e linhas de matriz `2A` foram lidos com um decodificador equivalente: pedido ecoado, `53`, n, dados, checksum.
- `MAN:n` = manual público Omegas 2.16.4 C em texto (scratchpad `web/sw.txt`), linha n. Original: <https://landi.pl/assets/files/OMEGAS/MANUALE_SW_2164C_gb1.pdf>.
- `N131` = boletim Landi Renzo N.131, "LR Omegas software program 4.2.0.85" (19/02/2020): <https://hybridsupply.uk/wp-content/uploads/hsd-49504-lr-omegas-software-program-version-4-2-0-85.pdf>.
- `RE:0x…` = endereço no ProgBase.exe (sha256 8a2d297c…, cópia em scratchpad `pbcache/`). Análise estática com pefile+capstone, do mesmo jeito que em `2026-10-18-anti-stallo-caixa-re.md`.
- Textos em inglês/português = `protocolo-landi/dfm/i18n_all.json` (chave = nome do componente). Muitos "por" do original ficaram em inglês.

### Duas descobertas novas que valem para o documento inteiro

1. **Unidade dos tempos (INFERIDO forte): 1 unidade = 0,00256 ms.** É a mesma escala da telemetria `48 01`. Há três provas independentes:
   - `BASE_TEMPI_GLOBALE` (SC 121) = 2560 (LOG:836).
   - O boletim N131 diz que, no corte de extra-injeções, os padrões são 0,1 ms e 1,4 ms. Na ECU estão 39 (×0,00256 = 0,0998 ms) e 547 (×0,00256 = 1,400 ms) (LOG:6852, LOG:6884).
   - `TEMPI_PER_BENZINA` = 11719 dá 30,0 ms, que é o teto da faixa da tela (MaxValue 30, CFG:10334).
   - Todos os "ms" abaixo usam essa conta.
2. **Mapa das caixas de seleção para bits (PROVADO no código).** Cada caixa da tela grava um bit de `FLAG_CONF1` (SC 3, setter RE:0x42D300 → `TAebVector.SetValues` em `+0x90`), `FLAG_CONF2` (SC 192, RE:0x42E16C → `+0x2E8`) ou `FLAG_CONF3` (SC 250, RE:0x42E8D8 → `+0x39C`).
   - A ECU do dono devolveu `FLAG_CONF1 = 0x2486, 0x1051` (LOG:508), `FLAG_CONF2 = 0x0001, 0x0000` (LOG:752) e `FLAG_CONF3 = 0x0000, 0x0000` (LOG:808).
   - Com isso dá para dizer, caixa por caixa, como cada uma estava (tabela 2).
   - Ressalva (INFERIDO): em ECUs da família "LR" o setter do FLAG_CONF2 grava em `FLAG_CONF2_LR` (RE:0x42E6E4). Nesta ECU o original leu o SC 192 e nunca o 193, por isso usei o 192.
   - As 4 escritas `35 03 00 …` do log (LOG:31284, 58344, 468896, 468916) alternam o bit 0x0800 da palavra 0 do FLAG_CONF1. Esse bit é o do "modo taratura" (ButtonTaraturaClick/DrawGridKDblClick/LabGasClick, RE:0x4ACFE7), não uma configuração. PROVADO o bit; INFERIDO que foi o modo taratura.

## 1. Todas as abas e seções do `TFormConfig` (33 TabSheets)

| Aba (componente) | Nome na tela ita / eng / por | Para que serve, em 1 linha | Fonte |
|---|---|---|---|
| TabCambio | Cambio GAS / Gas change-over / Mudança do gás | Troca gasolina→gás: rpm e tipo de troca, atraso, temperaturas, injetor, cilindros, sobreposição, **progressões**, sequência | CFG:465 |
| TabLambda | Lambda / Lambda / Sonda Lambda | Tipo e referências da sonda lambda; diagnóstico de nível de óleo (Flash Lube) | CFG:2228 |
| TabEmissioni | Emissioni / Emissions / Emissões | Emulação lambda; **empobrecimento na saída do corte** e nº de injeções | CFG:2804 |
| TabSensore | Livello GAS / Gas level / Nível do gás | Sensor de nível; **volta automática à gasolina** (pressão baixa); EV antecipada; bomba de gasolina | CFG:3299 |
| TabPressione | Pressione | Leitura de pressão, MAP externo, limiar subsônico | CFG:4175 |
| TabTemp | Temperatura | Sensores de temperatura do motor e do gás; troca por temperatura | CFG:4629 |
| TabKCoefficienti | Coefficienti k | Mapa K e sub-abas abaixo (o app já cobre o mapa) | CFG:4984 |
| ↳ TabCorrettori | Correttori / Fuel trim | Corretores lento/rápido por bancada (só leitura) | CFG:5226 |
| ↳ TabKOpenLoop | K open loop | Tabela K em malha aberta | CFG:5417 |
| ↳ TabKFactor | K factor | Multiplicador do mapa K (×1,5/×2) | CFG:5447; MAN:1274 |
| ↳ TabKTempRid | Temperatura gas | Correção por temperatura do gás | CFG:5640 |
| ↳ TabKTempMot | Temperatura acqua | Correção por temperatura da água | CFG:5707 |
| ↳ TabKPressDiff | Pressione | Correção por pressão | CFG:5775 |
| ↳ TabPressAss | Pressione diff. | Correção por P coletor / P rail | CFG:5843 |
| ↳ TabKTempi | Tempi | **Tempos mortos** dos injetores gasolina/gás, principal e extra | CFG:5883 |
| ↳ TabKTempiEx | Tempi + | Tempos mortos dos injetores Landi Renzo | CFG:6027 |
| ↳ TabKVarie | Smagrimento / Tip IN | Empobrecimento em aceleração até X rpm (filtro) | CFG:6067; MAN:1336 |
| TabAdvanced | Advanced | **Mapa de contribuição de gasolina** (% de gasolina por célula) | CFG:6491; N131 |
| TabCalibration | Calibra inj. | Exclusão de injetores de gás e corretores (diagnóstico) | CFG:6558 |
| TabIniezione | Iniettori / Injectors | **Tempo mínimo de abertura**, gás no corte, **anti-stallo**, corte de extra-injeções, corrente máxima na troca e no corte, preaquecimento | CFG:7076 |
| ↳ TabInjSettings | Settings Inj | Corrente de pico/manutenção e impedância (driver) | CFG:8619 |
| ↳ TabInjSettingsSt | Settings Inj ST | Fases do driver (pico, hold, demag) por modo default/corte/aquecimento | CFG:8943 |
| TabTaratura | Varie / Self-calibration | Parâmetros da autocalibração antiga (água, rpm, tolerâncias) | CFG:9501 |
| TabVariatore | Variatore / STAP | Mapa de avanço (variador de avanço) | CFG:9779 |
| TabBenzina | Gas/benzina / Gás/gasolina | **Marcha lenta: Gás / Volta à gasolina / Gasolina**; contribuição em rpm alto; troca sequencial; split fuel | CFG:9833 |
| TabAdatta | Adattività OBD | Adaptatividade OBD (Ki, Kp, sensibilidade), PIDs e erros OBD | CFG:10594 |
| TabConsumometro | Consume meter | Medidor de consumo (só GLP) | CFG:11836 |
| TabFrest | Frest model | Modelo físico de vazão do injetor (família LR) | CFG:11918 |
| TabSwitchToPetrol | Switch to petrol | "Switch to petrol after cutoff" e "for short T_Inj_b" (SC 236, família LR) | CFG:12421, 12558, 12565 |
| TabDhlpAgaslev | DHLP/AGASLEV | Diagnóstico de vazamento e filtro do nível de gás | CFG:12752 |
| TabTest | Dati di test | Valores de teste (WORD/BYTE/TIME) | CFG:13543 |
| TabAutoCalNew | Auto calibrazione | AutoCal novo (o app já tem) | CFG:13673 |
| TabEsci | Esc Esci | Sair | CFG:13847 |

## 2. Funções candidatas, em ordem de prioridade (17)

Colunas: **App lê?** = está no `EcuParameterSnapshot.CATALOG`. **App edita?** = nenhum destes é gravado hoje: o app grava só Mapa K, MUL_ACT/curva, ações do AutoCal e a sonda de buffers. **FLAG_CONF1/2/3** não são lidos pelo app (grep em `app/src/main/java`, 2026-10-18).

| # | Função (eng / por) | Parâmetro (SerialCode) | Fábrica | **Na ECU do dono** (LOG) | App lê? | Risco | Por que ajuda (fonte) |
|---|---|---|---|---|---|---|---|
| 1 | Enable un-stalling strategy (anti-apagão) | caixa = FLAG_CONF3 w0 bit 0x0020 (250); números = ANTI_STALLO (307) | 1600·100·1500·2·20 (SD:3721) | **DESLIGADO** (FLAG_CONF3 = 0, LOG:808); números = fábrica (LOG:9492). PROVADO | números sim, caixa não | MÉDIO | Já documentado em `2026-10-18-anti-stallo*.md`. Tira do gás quando a rpm cai abaixo de ~1600 rpm |
| 2 | **Abilita progressioni** (progressões) + tabela 4×3: linhas "Uscita da cutoff / Minimo / Cambio / Altri funz. benz.", colunas "n.inj.min. / n.inj.prog. / dt benz." | caixa = **FLAG_CONF3 w0 bit 0x0010** (250), PROVADO RE:0x4CA570; tabela = PARAM_PROGRESSIONI (308) | [0,4,500] [20,4,500] [20,4,500] [25,4,500] (SD:3703) | **DESLIGADO** (bit 0x0010 = 0, LOG:808); tabela = fábrica (LOG:9532–9628). PROVADO | não | MÉDIO | A linha "Uscita da cutoff" (saída do corte) é exatamente o caso da retomada (CFG:2065–2143). Pelos rótulos, a ECU passa de gasolina a gás aos poucos na saída do corte, na lenta e na troca. **Significado de cada número: INFERIDO** (só rótulos; colunas 1–2 têm `Visible=False`). A ECU respondeu ao 308, então tem a função. O irmão antigo "Cambio con sovrapposizione" (SC 299, mesmo bit) deu NACK nesta ECU (LOG:9184) |
| 3 | Operation at idle: Gas / Return to petrol / Petrol (Funcionamento al minimo) + "RPM for idle identifying" | GIRI_PER_BENZINA[0] (161) + FLAG_CONF1 w1 bit 0x0400 (3). PROVADO RE:0x4BFB1E/0x43763A: Gás → rpm 0; Volta à gasolina → 1100 rpm e bit 0; Gasolina → 1100 rpm e bit 1 | Gás (rpm 0) é o que a tela grava ao escolher Gás | **Gás** (GIRI_PER_BENZINA = [0, 8500], LOG:6988; bit 0x0400 = 0, LOG:508). INFERIDO forte | não | MÉDIO (gasta gasolina na lenta) | O manual diz que essas duas estratégias "são usadas quando o motor tende a apagar ao voltar à marcha lenta" (MAN:1599–1614). Na nota do gás no corte, o fabricante diz que **para apagão ao voltar à lenta, prefira Gás–Gasolina** (MAN:1488–1492) |
| 4 | Num inj min / Progress (volta à gasolina na lenta) | INIETTATE_PER_BENZINA (163) [0]=Num inj min, [1]=Progress (DataIndex 0/1, CFG:10065/10089) | manual: 20 injeções de gasolina (MAN:1602) | [20, 5] (LOG:7016). Mapeamento INFERIDO | não | BAIXO | Quantas injeções de gasolina antes de voltar ao gás. Só vale com o modo do #3 |
| 5 | Num inj exit cutoff (injeções de gasolina na saída do corte) | provável NUMERO_INJ_BENZINA_CUTOFF (106); rótulo CFG:9991, edit CFG:10113 (0–50) | DESCONHECIDO | **0** (LOG:4878). Ligação rótulo↔SC por nome: INFERIDO | sim | BAIXO | Ataca direto a retomada: N injeções de gasolina ao sair do corte. Hoje é 0 |
| 6 | Exit cut-off leaning / Leaning number injections | SMAGRIMENTO_RIENTRO_CUTOFF (136), NUMERO_INIETTATE_SMAGRIMENTO (137) | DESCONHECIDO | 100 / **0** (LOG:6272, 6294) | sim | BAIXO | Manual: "útil quando o motor para ou tende a parar ao voltar à lenta por mistura rica ou pobre demais" (MAN:812–823). Com nº = 0 está inativo. O que 100 significa (−100…100 %; CFG:3049): DESCONHECIDO |
| 7 | Gas injector minimum opening time (Tempo mínimo de abertura do injetor) | TEMPO_APERTURA_INIETTORE (83) | por injetor: Matrix 1,5 / Landi 2,0 / Keihin 2,5 / AEB 3,2 ms (MAN:1403–1419) | 977 = **2,50 ms** (LOG:4500) | não | MÉDIO | Manual: "valor médio/alto pode dar mistura errada na lenta". A faixa de ~2 ms em que o GNV fica pobre depois do corte (`2026-10-13-retorno…md`) fica em volta desse piso. Ponto aberto: a telemetria mostra o tempo **antes ou depois** do piso? DESCONHECIDO |
| 8 | RPMs for gas injectors driving in cutoff / Gas injection time during cutoff | GIRI_TEMPO_CUTOFF (50) [rpm, tempo] | DESCONHECIDO | [**0 rpm**, 977 = 2,50 ms] (LOG:4016). rpm 0 = desligado: INFERIDO | sim | ALTO | Injeta gás durante o corte abaixo de X rpm, para não chegar "seco" na retomada (MAN:1467–1487). O manual avisa: não usar com sonda lambda pilotada em corrente; para apagão na lenta, prefira o #3 |
| 9 | Extra-injections cutting (Corte de extra-injeções) + mínimo extra gás / máximo extra gasolina | caixa = FLAG_CONF2 w0 bit 0x0001 (192), PROVADO RE:0x4C1BFA; TEMPI_EXTRAINIETTATE[2] (155); TEMPO_MAX_EXTRAINJ_BENZ (156) | 0,1 ms / 1,4 ms (N131) | **LIGADO** (FLAG_CONF2 = 1, LOG:752); 0,10 ms / 1,40 ms = padrão (LOG:6852/6884) | tempos sim, caixa não | MÉDIO | Na retomada e no tip-in a gasolina dá extra-injeções curtas. Com o corte ligado, as extra-injeções abaixo de 1,4 ms não são copiadas no gás: possível causa de pobreza transitória. INFERIDO; precisa de teste |
| 10 | Enrichment in acceleration (Arricchimento in accelerazione) | CORR_ARRICCHIMENTO (116), −50…50 | 50 = "deixa a mistura inalterada" (MAN:1503–1511) | 50 (LOG:5752) | sim | BAIXO | Abaixar empobrece só nos transitórios; o manual o indica para metano rico em aceleração. Mapeamento por nome e faixa: INFERIDO |
| 11 | Rev threshold for change-over + Type of change-over | GIRI_MIN_CAMBIO (16); tipo = FLAG_CONF1 w0 bit 0x0100 (ComboCambioKeyPress RE:0x4A9028) | 1600 rpm (MAN:553) | **1200 rpm** (LOG:3348); bit 0x0100 = 0 (qual opção é 0: DESCONHECIDO) | rpm sim | BAIXO | Troca gasolina→GNV em rpm baixa demais pode cair na zona de risco. O risco de apagão é 16× maior com retomada abaixo de 1550 rpm (`2026-10-16-sessao-18-07-apagoes.md`) |
| 12 | Overlapping time (Tempo de sobreposição) | TEMPO_SOVRAPPOSIZIONE (109), 0–2 s | 0 (MAN:688–694) | 0 (LOG:4922) | não | BAIXO | Gasolina e gás juntos na troca, "para evitar irregularidades depois da troca" |
| 13 | Sequential fuel changeover (troca sequencial) | FLAG_CONF1 w1 bit 0x1000 (CheckIstantaneoClick RE:0x4BFA99) | DESCONHECIDO | **LIGADO** (0x1051, LOG:508) | não | BAIXO | Troca cilindro a cilindro a cada 5 injeções (MAN:1586–1589). Já está ligada; serve de confirmação |
| 14 | Max petrol inj. time for gas change-over | T_INJ_BENZ_MAX_CAMBIO (146) | DESCONHECIDO | 4687 = 12,0 ms (LOG:6590) | não | BAIXO | Só troca para GNV com Tinj de gasolina abaixo desse valor (CFG:595). INFERIDO pelo rótulo |
| 15 | Incrementa max corrente al cambio gas + Tempo max corrente em uscita da cutoff | caixa = FLAG_CONF2 w1 bit 0x0400 (RE:0x4C7245); TEMPO_CORRENTE_CUTOFF (73) | DESCONHECIDO | caixa **DESLIGADA** (LOG:752); 2148 = 5,50 ms (LOG:4326) | tempo sim | MÉDIO | Abertura mais firme do injetor de gás na troca e na saída do corte (CFG:8298, 8643). Ligação com apagão: INFERIDO; mexe em corrente e calor |
| 16 | Petrol / Gas injector dead time (tempos mortos) | TEMPO_MORTO_INIETTORI_GAS (126) / _BENZINA (125) | DESCONHECIDO | 391 = **1,00 ms** / 234 = **0,60 ms** (LOG:6092/6068) | não | MÉDIO | Pesa mais nos pulsos curtos (lenta, ~2 ms). Parte se sobrepõe à Curva K. Manual: "recomendamos não alterar" (MAN:1290–1303) |
| 17 | Automatic change back with low pressure (volta automática por pressão baixa) | FLAG_CONF1 w1 bit 0x0040 (RE:0x4BCAC2); PRESS_RETROPASSAGGIO (189); TEMPO_RITORNO_BENZINA (62) | DESCONHECIDO | **LIGADA** (LOG:508); 1800…2200 (LOG:7308); tempo 0 (LOG:4238) | só o tempo | BAIXO (só leitura) | Diagnóstico: um "apagão" no GNV pode ser a ECU voltando à gasolina por pressão baixa (MAN:893–940). Ler para separar as causas, não para mexer |

Observações sobre a tabela:
- **Apagão no GNV e retomada:** os itens #1, #2, #3 e #5 são os que a ECU tem para isso, e na ECU do dono estão **todos desligados ou em zero**. Não há prova de que ligar resolve. Cada um precisa de teste no carro, um por vez, com sessão gravada.
- **Gasolina↔GNV:** #11 a #14. **Lenta:** #3, #4, #6 e #7. **Pobre/rica:** #6, #9, #10 e #16.

## 3. Não vale a pena (instalação, diagnóstico ou outra família de ECU)

| O quê | Por quê |
|---|---|
| Sonda lambda (tipo, referências, atraso), emulação lambda (níveis, atraso, "desativar em rpm baixa") | Instalação e emissão; não muda a mistura de GNV (CFG:2228–2560, 2824–3037) |
| Nível de gás, sensores de temperatura, MAP externo, limiar subsônico, pressão | Calibração de sensor na instalação (CFG:3299–4980) |
| K factor, K open loop, temperaturas, pressões da aba Coefficienti k | O app já cobre pelo Mapa K, Curva K e AutoCal; mexer duplica a correção |
| Mapa de contribuição de gasolina (Advanced; FLAG_CONF2 w0 0x4000 = DESLIGADO) e contribuição automática em rpm alto (FLAG_CONF1 w1 0x2000 = DESLIGADA) | Resolvem falta de vazão em rpm alto, não a retomada; quando tem célula pobre local, o Mapa K já resolve (N131; MAN:1590–1640) |
| Settings Inj / Settings Inj ST (corrente de pico, hold, demag, frequência), impedância, preaquecimento | Driver do injetor; errar queima o injetor (CFG:8619–9473) |
| Variatore/STAP, OBD adaptivity (DESLIGADA, FLAG_CONF1 w1 0x8000 = 0), PIDs OBD, consumômetro (só GLP), Flash Lube, DHLP/AGASLEV, buzzer, Valvetronik, Start&Stop, Flex fuel, sinal de rpm, aquisição de sequência, Test | Instalação ou diagnóstico |
| Frest model, Switch to petrol (SC 236 "after cutoff"), SC 299 "Cambio con sovrapposizione" | Família LR ou firmware antigo. O original não leu o SC 236 nesta ECU e o SC 299 deu NACK `CA` (LOG:9184). Para esta ECU: DESCONHECIDO / provavelmente sem a função |
| Calibra inj. (exclusão de injetores de gás) | Útil só para achar injetor ruim na bancada; não é ajuste |

## 4. Como organizar em Ferramentas → "Avançado" (proposta)

Regra: **ler primeiro, gravar só à mão, um item por vez.**

1. **Um cartão de leitura** ("Funções da ECU para apagão e retomada"). Lê numa conexão: FLAG_CONF1/2/3, ANTI_STALLO, PARAM_PROGRESSIONI, GIRI_PER_BENZINA, INIETTATE_PER_BENZINA, NUMERO_INJ_BENZINA_CUTOFF, GIRI_TEMPO_CUTOFF, TEMPO_APERTURA_INIETTORE, os 2 tempos mortos e os tempos de extra-injeção. Mostra como o original mostraria: caixa marcada ou não e valor em rpm/ms (×0,00256). Marca "diferente da fábrica" quando houver padrão conhecido. É só leitura e não tem risco.
2. **Três grupos, sem rolar:**
   - **Retomada e lenta:** anti-stallo, progressões, modo da lenta (Gás / Volta à gasolina / Gasolina), injeções de gasolina na saída do corte.
   - **Troca gasolina↔GNV:** rpm de troca, atraso, sobreposição, troca sequencial, Tinj máx.
   - **Injetor de gás:** tempo mínimo de abertura, tempos mortos, corte de extra-injeções.
   - O resto (seção 3) fica fora.
3. **Gravação manual, um parâmetro por vez:**
   - Antes de gravar, tira foto (leitura) do valor e, no caso das caixas, relê o vetor FLAG inteiro e muda **só o bit**.
   - Grava, relê para confirmar, registra `ecu_command` na sessão e oferece "voltar ao valor anterior".
   - Para bits de FLAG: a gravação manda o vetor de 2 palavras inteiro (RE:0x42EC4C), então ler e modificar sem dado velho é obrigatório.
   - Formato do quadro de escrita: INFERIDO (SetVector `0x30|n` / `35 03 00 …` visto só no FLAG_CONF1, LOG:31284).
   - A própria tela avisa: "parâmetros em vermelho só com a chave desligada e o comutador apagado" (CFG:579). Não gravar com o motor em GNV.
4. **Teste:** ligar uma função por vez e rodar ~20 retomadas depois do corte com a sessão ligada, comparando a taxa de quase-apagão com o histórico.
   - Ordem sugerida pelo risco: #5 (nº de injeções na saída do corte) → #1 (anti-stallo) → #2 (progressões) → #3 (modo da lenta).
   - #8 (gás no corte) só se os outros não resolverem.

## O que continua DESCONHECIDO

- Significado exato de cada número das progressões e do anti-stallo.
- Qual opção do "Tipo di cambio" corresponde ao bit 0x0100 = 0.
- A ligação EditIniettateCutoff ↔ SC 106: o handler não foi seguido.
- Se a telemetria `48 01` mostra o tempo de gás antes ou depois do piso de 2,50 ms.
- Se a ECU está assim **hoje**: o log é de 12/08/2026. Fecha com uma leitura dos FLAG_CONF no próximo `param_snapshot`.

Web: buscas por "progressioni/progressions" Omegas e pelos rótulos de progressões e anti-stallo não acharam documentação pública. Acharam só o manual 2.16.4 C e o boletim N131 citados acima. Busca: "Landi Renzo Omegas progressioni cutoff" e "\"Exit cut-off leaning\" \"Leaning number injections\"" (2026-10-18).
