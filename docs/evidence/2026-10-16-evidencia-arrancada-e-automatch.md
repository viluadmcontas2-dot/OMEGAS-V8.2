# Evidência — arrancada no GNV, AutoMatch da ECU e gravação (2026-10-16)

Fonte: 6 sessões do Drive "Novas" (15–16/10; 160 min; 124 min em GNV, 18 min em gasolina) e o corpus `tests/fixtures/sessoes-uteis` (85 sessões). Classe de prova: 3 (replay de sessões reais). Nada foi rodado no carro.
Ferramentas: `tools/sessoes/analise_arrancadas.py`, `analise_equivalencia_celulas.py`, `analise_janela_automatch.py`, `analise_automatch.py`, `replay_sessao.py`.

## Q1 — A ECU aplica o AutoMatch sobre a curva viva ou sobre uma foto? → **curva viva** (n = 1, forte)
- Sessão 16/10 13:42, AutoMatch 3 (min 57,96 → 66,14). Entre o início da aquisição e a escrita da ECU, o app gravou a curva 3 vezes (min 60,09; 63,13; 64,55) em 17 nós.
- Depois da ECU, a razão depois/antes por nó é suave dentro de cada zona se a base for a curva **viva** (desvio médio 0,021) e irregular se a base for a foto do início (0,033; saltos de +12 % no nó 0 e +8,5 % no nó 15 sem vizinhos).
- Nós nossos: início 1,028 → nossos 1,200 → ECU 1,151 (nó 0); 1,028 → 1,188 → 1,139 (nó 3). A ECU partiu do nosso valor.
- Conclusão: gravar logo após a escrita da ECU é **seguro e aproveitado**: o AutoMatch seguinte parte da curva refinada. Confirmar com mais AutoMatches (cada sessão com AutoCal ligado soma evidência).
- Latência de detecção já existente: `autocal_native_calibration_epoch` (FACTOR_CHANGE_CONFIRMED) ~3 s depois da escrita da ECU; leitura dos campos do AutoCal a 1 s quando a aquisição está ativa (`NativeAutoCalRefreshPlanner`). No corpus antigo a cadência era ~25 s (versões anteriores).
- Entre a escrita da ECU e a primeira zona GNV voltar: corpus mediana 32 s (mín 23 s); na sessão nova as 4 zonas voltaram em ~1 min dirigindo. A ECU altera 29–30 dos 30 nós por AutoMatch e zera só as zonas de GNV (gasolina mantida).

## Q2 — Quanto do erro é por RPM (Mapa K) e quanto por ms (Curva K)? → **há componente por RPM clara**
Equivalência por célula RPM×MAP (petrol_ms rodando em GNV ÷ petrol_ms rodando em gasolina; < 1 = a ECU da gasolina tira combustível no GNV = **rico**; > 1 = **pobre**), só quadros estáveis, ≥ 15 quadros por combustível:
- 850 rpm, 0,5 bar (lenta típica no GNV): **0,856 (−14 %) rico** (9 214 × 51 quadros; base de gasolina pequena).
- 1350 rpm: dentro de ±4 % em 6 de 8 células. 1850 rpm: rico a 0,3 bar (−8 %) e 0,5 bar (−4 %); pobre a 0,9 bar (+4,6 %).
- 2500 rpm: **pobre em 6 de 9 células (+4 a +9 %)**, pior em carga (0,8–1,0 bar).
- Mediana por faixa: 850 +2,6 % · 1350 +1,8 % · 1850 +2,5 % · 2500 +6,8 %.
- Leitura: o que separa a lenta (rica) do cruzeiro em carga (pobre) depende do RPM → é correção de **Mapa K** por faixa de RPM; a exportação Curva→Mapa não deve ser 100 % — o fator por linha vem desta tabela.
- Ressalvas: lenta compara MAPs diferentes entre combustíveis (GNV 0,5 bar × gasolina 0,4 bar); base de gasolina de uma sessão (16 min); amostragem de 290 ms.

## Q3 — O que o GNV faz na arrancada que a gasolina não faz? → **sai mais devagar, mais rico, e quase apaga em carga**
- Arrancada = lenta ≥ 1,5 s → +500 rpm em até 3 s. GNV: 88 arrancadas em 124 min; **26 (30 %) não ganharam +600 rpm em 3 s**; mediana até +600 rpm 2,3–2,6 s com MAP 0,8 bar. Gasolina: 14 arrancadas; 2 sem +600; **mediana 1,7 s** com MAP 0,5 bar. Com MAP ≥ 0,7 bar o GNV tem 21 arrancadas (8 sem +600 rpm) e a gasolina quase nunca precisa de tanto acelerador (2).
- Razão gás/gasolina (ms) na arrancada × em regime no mesmo ms de gasolina: **+3 a +9 % mais gás na arrancada** (3–4 ms +8,9 %; 7–9 ms +4,6 a +5,1 %). `dynamic_correction` da ECU é constante (220–225): não explica. `k_interpolated` do app é sempre 0 (campo morto).
- 8 quase-apagões em 160 min, todos no GNV: o padrão é rotação 850–1050 com MAP 0,75–0,88 bar (saída em carga, quebra-mola) e gás a 20–25 ms para 7,5–10 ms de gasolina (razão 2,5–2,7, acima do regime 2,37): a rotação cai para 330–530 rpm. Um deles na troca gasolina→GNV sob carga (min 1,67).
- Gás frio (< 40 °C) não mudou a razão (2,96 × 2,92 a 3–4 ms): a hipótese "primeiro uso do dia" **não** se confirmou nestes dados (poucos quadros frios; manter aberta).
- Direção da correção, por duas medidas independentes: **menos gás na lenta e na saída (RPM baixo × ms alto)**, mais gás a 2500 rpm em carga. Alvo mensurável: tempo até +600 rpm no GNV ≤ 1,15 × gasolina no mesmo MAP.

## Q4 — Por que a confirmação da escrita "trava"? → **a escrita é rápida; o que trava é o que vem depois**
- `elapsedMs` real (escrita + ACK + leitura de volta), 13 gravações, todas `readbackValid`: Curva K 437–763 ms (2–20 pontos); Mapa K 908 ms (2 células) a 3 336 ms (144 células).
- O que demora: `waitTimeoutMs = 10_000L` antes de gravar (KWriteManager:381, KFactorManager:407) para o pipeline de aprendizado drenar — "Buffer não drenou … dentro de 10000ms" aparece 4× nas sessões; checkpoint do aprendizado antes e depois; releitura do mapa inteiro depois; "Sincronização pendente" do Link (morto) a cada gravação; restauração do aprendizado leva **16–36 s** ao conectar.
- Correção: espera ≤ 1 s (fatia C), publicação/checkpoint em segundo plano (fatia D), Link apagado (fatia C).

## Achados novos (não estavam na auditoria)
1. **O app zera o aprendizado do GNV a cada gravação sua** ("Calibração confirmada: base gasolina preservada; aprendizado GNV e derivados zerados", 13× em uma sessão). Resultado: o Refino recomeça do zero depois de cada escrita e propõe com pouca evidência → em 3,5 min (26,19 → 27,24 → 29,73) gravou −6 %, depois +10 %, depois voltou tudo a 1,0. É a "tentativa e erro" vista nos dados. Fundamento da fase 2: memória com época (re-escalar pares antigos pela razão K_novo/K_velho), não zerar.
2. **"Piorou" 16 s depois de gravar** (14,88 → RESTAURAR_TRECHO 15,20): confirma o achado 5 da auditoria com caso real.
3. **A gravação da sessão anda a ~290 ms por quadro** (10 quadros crus por amostra; p90 0,6–1,1 s). Transientes de arrancada (0,5–1,5 s) viram 2–4 amostras.
4. A ECU expõe seus buffers de aquisição por combustível (`MNFLD_PRESS_BUF`, `PETR_INJ_TBUF`, `*_GAS`, `NUM_BUF_UPD_*`): são os "quadradinhos" da tela AutoCal (uma coluna de quadrados por combustível, no MAP de cada buffer). São pares (MAP, ms de gasolina) já filtrados pela ECU — evidência pronta para o Ledger.
5. Fases do Refino na sessão real: PROPOSTA_PRONTA → VERIFICANDO → COLETANDO_NOSSOS → PROPOSTA_PRONTA em ciclos de ~1 min. Falta histerese: nenhuma proposta nova até N células distintas serem remedidas sob a curva nova.

## Não provado
Comportamento no carro (classe 5); Q1 com n > 1; base de gasolina na lenta com MAP igual; qualquer lambda real (não há sonda no dado).
