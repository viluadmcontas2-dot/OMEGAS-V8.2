# Manuais PÚBLICOS Landi Renzo / AEB / Omegas — o que confirmam (2026-10-18)

Pesquisa web (só leitura). Marcas: CONFIRMADO-MANUAL / CONTRADIZ / NÃO ENCONTRADO.

## Fontes e confiança

- **FONTE-A (fabricante, ALTA):** "Software Manual Landi Renzo Omegas for LRE 184-188-194-198-EVO ECUs", versão 2.16.4 C, jul/2012. https://landi.pl/assets/files/OMEGAS/MANUALE_SW_2164C_gb1.pdf (baixei: 15 852 900 bytes, idêntico em tamanho ao `sw.txt` já extraído; mesmo documento também em pliki.elektroda.pl/373321 e pdfcoffee). Páginas abaixo = número impresso no rodapé do PDF. Linhas = `sw.txt`.
- **Limite:** esse manual é de 2012, para ECUs LRE 184-198/EVO e software 2.16.x. O ProgBase/MP48 atual é mais novo. O que o manual 2.16.4 não traz NÃO prova ausência no ProgBase.
- Outros públicos conferidos, sem nada sobre os temas: `fahrmitgas.de/.../Landirenzo-Kits-Omegas-3.0-Evo.pdf` (manual de instalação/componentes, 0 ocorrências de stall/progressioni/cut-off) e `hybridsupply.de/.../hsd-16433-landi-renzo-omegas-direct-1-0.pdf` (instalação Direct; só "soglia giri minimo" na lista de leituras, linha 153). Release notes 4.2.0.85 (hybridsupply.uk, N.131) já estavam em `n131/`.
- Inacessíveis (403/503/login): Scribd (manual SW 4.1.0 "S", Technical Info 55), CourseHero, metanoauto.com, `riservato.landi.it` (área reservada). O 4.1.0 "S" em italiano é o candidato mais provável de conter progressioni/anti-stallo, mas não consegui ler.
- Buscas por "un-stalling", "unstalling", "anti-stallo", "antistallo", "Abilita progressioni", "PARAM_PROGRESSIONI", "MP48", "ProgBase" em manuais: zero resultados relevantes. Resultados de domínios .gov.nz / .gv.at / .rcc.int são texto gerado automaticamente: IGNORADOS (confiança nula).

## Pontos

### 1. Anti-stallo / un-stalling (rpm máx, delta rpm, atraso, histerese, delta p/ desativar)
**NÃO ENCONTRADO.** Nenhuma ocorrência de "stall", "un-stalling" ou "anti-stallo" no manual 2.16.4 (grep em `sw.txt`) nem nos outros PDFs. Unidades e ordem dos 5 campos seguem DESCONHECIDOS; nada contradiz o que `2026-10-18-anti-stallo.md` já diz. O manual só trata o *sintoma* por outros caminhos (pontos 3, 5): apagão ao voltar à lenta.

### 2. Abilita progressioni / PARAM_PROGRESSIONI
**NÃO ENCONTRADO** como função com esse nome. Mais próximo no manual (não é a mesma tela):
- "Sequential fuel changeover: this function allows the switch from petrol to gas to be performed gradually. The petrol/gas injectors will be gradually deactivated one by one every 5 injections." (FONTE-A, p.31, sw.txt:1585-1589). CONFIRMADO-MANUAL só da troca sequencial (FLAG_CONF1 bit 0x1000, linha 13 do catálogo), NÃO da tabela 4×3 (saída do corte/mínimo/troca/outras). Significado de n.inj.min / n.inj.prog / dt benz: segue INFERIDO.

### 3. Funcionamento no mínimo: Gás / Volta à gasolina / Gasolina
**CONFIRMADO-MANUAL** (FONTE-A, p.31, sw.txt:1599-1616), F9 Gas/petrol:
- "Gas: At idle speed, the runs on just gas."
- "Return to petrol: on returning to high rpm, the system feeds the engine with 20 injections of petrol then goes back to gas automatically."
- "Petrol: under the rpm settings set below, the engine will always be powered with petrol. The strategy has an in-built delay system to prevent continuous changes in fuel."
- "These two strategies are usually implemented when the engine has a tendency to cut out on returning to idle."
- "'RPM for idle identifying' (range 0;2000 rpm) the engine speed below which one of the two above-mentioned strategies cuts in."
Confirma: faixa 0-2000 rpm; "Volta à gasolina" = 20 injeções de gasolina e depois gás sozinho; "Gasolina" tem atraso embutido anti-oscilação. Prova direta de INFERIDO "(rpm 0 = Gás)" NÃO existe no manual (é do RE do .exe). Nota: o manual descreve a volta à gasolina como "ao voltar a rpm alto" ("on returning to high rpm"), texto que parece trocado com "idle"; ler com cautela.

### 4. Injeções de gasolina na saída do corte (Num inj exit cutoff)
**NÃO ENCONTRADO** como parâmetro próprio. Só o número 20 do ponto 3 (que é "volta à gasolina", não saída do corte). A relação NUMERO_INJ_BENZINA_CUTOFF(106) ↔ rótulo continua INFERIDO.

### 5. Empobrecimento na saída do corte
**CONFIRMADO-MANUAL** (FONTE-A, p.17, sw.txt:812-823):
- "A Exit cut-off leaning (range: -100;100), parameter associated with 'Leaning number injections'. The number is expressed as a percentage of the lean/rich mapping system readings when the ECU exits from the engine cut-off stage."
- "B Leaning number injections (range: 0; 255), this is the number of injectors undergoing percentage changes compared with mapping system values."
- "this function can be useful when the engine stops or tends to stop on when going back to idle due to a mixture that is too rich or too lean. It can also be used to rectify emissions in the event of excessive 'HC'."
Novo: o manual diz que é PERCENTUAL sobre a leitura do mapa, faixa -100..100 (o catálogo dizia "100 = DESCONHECIDO"). Contagem 0-255 em "injeções" (o texto diz "injectors", provável erro de redação do manual; INFERIDO que seja injeções). Sinal/sentido de 100 vs 0: não explicitado (100 pode ser "sem alteração" ou +100%): continua DESCONHECIDO.

### 6. Extra-injeções
**CONFIRMADO-MANUAL** (FONTE-A, p.28, sw.txt:1428-1441; p.24-25 sw.txt:1308-1326):
- "Gas extra-injection minimum opening time (range 0-20 ms): minimum time that a gas injector remains open during extra injection... If you want the strategy to take effect, the time to set must be equal to or greater than..." (sic).
- "With 184-188 ECUs, set '1.4' in the 'extra injections' F7 box to disable the extra injection function."
- "Extra injection cutting: (for 194-198 ECUs) this function is enabled when there are frequent extra petrol injections that compromise the efficiency of the gas system."
Novo: 1,4 ms é o valor que DESLIGA a função em 184/188; "Corte de extra-injeções" é só para 194-198 e serve quando há extra-injeções de gasolina frequentes. O padrão 0,10/1,40 ms do catálogo é coerente com isso (1,4 = desligado). Isso enfraquece a hipótese INFERIDA do catálogo (linha 9) de que 1,4 ms causa pobreza transitória: pelo manual, 1,4 é o "desligado" documentado.

### 7. Volta automática por pressão baixa
**CONFIRMADO-MANUAL** (FONTE-A, p.19, sw.txt:892-912, 929-945):
- "Automatic change back to petrol: ... 'Low pressure time for change back' (range 0;20 s). ...time that must elapse before switching to petrol when the pressure drops below the value set in 'Pressure for change back to petrol'."
- "by not selecting 'Automatic return to petrol' when the gas tank is empty, the engine will run irregularly until it cuts out."
- "Low pressure value for change back: (range 0.4;2 bar), the threshold below which the ECU activates returning to petrol with ... buzzer (intermittent fast buzzer) and display (petrol and gas LEDs lit)." Pode ser ajustado independente do item anterior.
- "Pressure for change back to petrol": por faixa de tempo de injeção, modos Absolute / Linear / Percentage; usável com injetores "Landi" e "Keihin".
Unidades: tempo em segundos (0-20 s), limiar em bar (0,4-2).

### 8. AutoCal / automatch e Curva K / K factor
- **AutoCal: CONFIRMADO-MANUAL** (FONTE-A, p.44-45, sw.txt:2255-2330): motor a 80-90 °C; 3000 rpm estável em neutro (comutador gás/gasolina irrelevante, o software troca sozinho); depois soltar o acelerador e deixar em lenta, sem ar-condicionado/luzes/volante; "the program loads a baseline map to the ECU"; "calibration data from a similar engine ... will lead to much simpler auto-calibration". "The automatic calibration stage serves to set the ECU baseline".
- **K factor: CONFIRMADO-MANUAL** (p.25, sw.txt:1274-1286): "lets you multiply the map K values by 1.5 or 2 ... applied when the median values of the mapping system appear very high (over 230-240) following auto-calibration and despite this, the fuel mix remains lean... auto-calibration MUST be repeated." Ou seja, K factor é multiplicador discreto (1,5 ou 2), não ajuste fino, e exige refazer a autocalibração.
- "Automatch" como termo: NÃO ENCONTRADO (o termo é do OMEGAS-V8.2).

### Extras relevantes
- Gás no corte (p.28-29, sw.txt:1467-1500): "Giri per pilotaggio gas in cutoff (range 0; 2200 rpm)" é a soglia abaixo da qual, durante o cutoff de gasolina, injeta gás sem injeção de gasolina; "Tempo di attivazione inj gas in cutoff (range 0; 4 ms)" é o tempo aplicado em todos os injetores. Notas: "non utilizzare questa strategia in presenza di sonde Lambda pilotate in corrente"; "Per risolvere problemi di spegnimento (durante il rientro al minimo) preferire a questa strategia le impostazioni Gas–Benzina" (a referência do catálogo MAN:1488-1500 está correta).
- Arricchimento em aceleração (p.29, sw.txt:1503-1511): 50 deixa o mapa inalterado; abaixar diminui os tempos de gasolina lidos nos transitórios. Confirma a marca do catálogo (linha 10).
- Tipo de troca (p.12, sw.txt:543-552): aceleração exige rpm acima de "Rev threshold for change-over"; desaceleração exige rpm acima de "Fuel change rpm threshold" E condição de cut-off de gasolina.
- Tempo mínimo de abertura (p.27, sw.txt:1405-1409): "Setting a medium/high value might result in the wrong fuel mix at idle." Padrão muda com tipo de injetor.

## Tabela: o que o manual confirma que antes era INFERIDO

| Item | Antes | Agora | Fonte |
|---|---|---|---|
| Faixa "RPM for idle identifying" | só RE | 0-2000 rpm, CONFIRMADO-MANUAL | p.31 |
| "Volta à gasolina" = 20 injeções de gasolina e volta a gás | manual já citado | CONFIRMADO (texto exato) | p.31 |
| "Gasolina" no mínimo tem atraso embutido | não citado | CONFIRMADO | p.31 |
| Exit cut-off leaning = % sobre mapa, -100..100; nº 0-255 | "100 = DESCONHECIDO" | % CONFIRMADO; sentido de 100 ainda DESCONHECIDO | p.17 |
| 1,4 ms em extra-injeções = função desligada (184/188) | hipótese de causar pobreza | desligado documentado; hipótese ENFRAQUECIDA | p.28 |
| Corte de extra-injeções só ECU 194-198 | INFERIDO | CONFIRMADO | p.28 |
| Volta por pressão: 0-20 s, 0,4-2 bar, aviso sonoro + LEDs | não detalhado | CONFIRMADO | p.19 |
| K factor ×1,5 ou ×2 e refazer AutoCal | não detalhado | CONFIRMADO | p.25 |
| Anti-stallo (5 campos), PARAM_PROGRESSIONI (4×3), Num inj exit cutoff | INFERIDO/DESCONHECIDO | segue NÃO ENCONTRADO | — |

## Avisos do fabricante (quando NÃO usar)

- Gás no corte: não usar com sonda lambda pilotada em corrente; para apagão ao voltar à lenta, prefira Gás–Gasolina (p.28-29).
- Sem "Automatic return to petrol" com tanque vazio, o motor funciona irregular até apagar (p.19).
- Eletroválvulas abertas antecipadamente (~5 s): pode dar problema com processadores de avanço de ignição na troca (p.19).
- Alterar pontos de referência do mapa só antes de calibrar; depois pode causar carburação incorreta (p.24-25).
- K factor: só se as medianas do mapa passam de 230-240 e a mistura segue pobre; refazer AutoCal (p.25).
- Tempo mínimo do injetor médio/alto pode dar mistura errada na lenta (p.27).
- Parâmetros em "vermelho" alterados com o motor ligado só são gravados após desligar o motor (manual Omegas, citado por resultado de busca; não verificado no PDF).
