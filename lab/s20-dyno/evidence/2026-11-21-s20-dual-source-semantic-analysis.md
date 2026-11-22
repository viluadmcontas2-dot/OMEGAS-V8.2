# S20 — investigação semântica da primeira captura real (21/11/2026)

**Evidência bruta privada:** ZIP no S20 em `/sdcard/Download/Omegas/dyno-evidencias/dinamometro-omegas-1795307992224.zip`. SHA-256: `0c926a353cdacd5e5686cca5ab364aa927306ceab8d03b9b066e4ea37efda0b2`. **Não publicar nem versionar o ZIP.** 3.904 observações, 1.130 OBD e 2.774 MP48; período ~21:38:02–21:40:21.

## Linguagem de evidência
- **OBSERVADO**: o código ou o frame original contém o campo/valor informado.
- **CORROBORADO**: duas fontes coerentes em medidas pareadas, sujeito a relógio/latência.
- **HIPÓTESE**: semântica causal, física ou de estado ainda não independente ou repetidamente validada.
- **DESCONHECIDO**: não há prova suficiente para atribuir significado.

## Dicionário operacional

| Origem e campo | Semântica de trabalho | Valores nesta captura | Estado da evidência |
|---|---|---|---|
| OBD 0105 | Temperatura do líquido de arrefecimento da ECU original (A - 40 °C) | 48→79 °C | CORROBORADO com padrão OBD |
| MP48 byte[12] | Temperatura da água da MP48; cálculo atual `109 - byte[12]` | 10–26 °C, 22 °C na entrada do gás | **HIPÓTESE FÍSICA**: divergência da ECU OBD |
| MP48 byte[16] | Suposta temperatura de gás; cálculo `raw - 20` | 71–72 °C | HIPÓTESE: validar sensor/escala |
| OBD 010C | Rotação do motor | 827–3.111 RPM | CORROBORADO com MP48 |
| MP48 byte[0:2] | Rotação lida pela MP48, u16LE | 758–3.368 RPM | CORROBORADO com OBD |
| OBD 010B e MP48 byte[17:19] | Pressão absoluta no coletor (MAP), OBD kPa, MP48 s16LE/1000 bar | OBD 18–101 kPa; MP48 0,168–0,982 bar | CORROBORADO |
| OBD 0111 | Posição absoluta de borboleta TPS, não necessariamente pedal | 12,16–86,67 % | OBD PADRONIZADO |
| OBD 010D | Velocidade do veículo | 0–37 km/h | OBD PADRONIZADO |
| OBD 0106 / 0107 | Correções de mistura curta e longa: não medem diretamente AFR/lambda | STFT −25..+17,19%; LTFT −20,31..+6,25% | OBD PADRONIZADO, causa não identificada |
| MP48 byte[8:10] | Tempo **comandado pela ECU de gasolina** e utilizado como entrada de conversão da MP48, não prova fluxo de gasolina no modo GNV | 1,50–12,55 ms | ESCALA CORROBORADA DOCUMENTALMENTE |
| MP48 byte[6:8] | Pulso solicitado ao injetor GNV banco 1, não prova vazão efetiva | 0,11–22,62 ms nos frames com pulso | ESCALA CORROBORADA DOCUMENTALMENTE |
| MP48 byte[14:16] | Pressão absoluta do gás; /800 bar | 2,21–3,62 bar | PROVISÓRIO, comparar pressão física |
| MP48 byte[13] | Entrada bruta do nível do gás | constante 126 em 2.774 quadros | NÃO CALIBRADO: sem porcentagem possível |
| MP48 byte[19] | Campo bruto desconhecido; candidato tensão se /16 | 0 em `0x88`; 202–225 em `0x90` (~12,625–14,063 V) | HIPÓTESE; testar versus OBD 0142/multímetro |
| MP48 byte[11] | Estado de combustível | `0x88` em 2.113 frames, depois `0x90` em 661 | `0x88` = aguardando comutação HIPÓTESE; `0x90` = GNV corroborado com pulso |

## Comparações temporais

Emparelhamento por relógio monotônico do S20 dentro de ±200 ms (não significa que os sensores amostraram fisicamente no mesmo instante):
- **RPM:** 145 pares; erro absoluto mediano 6 RPM, p90 37 RPM.
- **MAP:** 146 pares; erro absoluto mediano 1,4 kPa, p90 3,1 kPa.
- **Temperatura:** 147 pares; MP48 `109 - byte[12]` versus OBD ECT: divergência mediana **−48 °C** (p10 −53 °C, p90 −39 °C). Correlação temporal **r=0,9922**, o que revela variação associada ao aquecimento, **não valida a escala**. O campo pode ser outro sensor do circuito de água/redutor, uma conversão errada ou outra semântica. NÃO reescalar por regressão de uma viagem.

## Transição e situação térmica
- Espera em estado `0x88` ~99,85 s com nenhum pulso de GNV observado.
- Primeira ocorrência de `0x90` em aproximadamente 21:39:51. OBD ECT mais próximo **74 °C**; candidata de água MP48 **22 °C**; pressão de gás ~3,32 bar no instante. Após `0x90`, 636/661 quadros tinham pulsos GNV; 25 não.
- Manual do fabricante relata que condições de comutação incluem temperatura, giros e estado de aceleração, mas sem leitura de configuração da MP48 não há como determinar o limiar que governou este evento.
- A mediana do diferencial pressão absoluta gás menos MAP foi ~2,524 bar em `0x88` e ~1,815 bar em `0x90`. Isso **não prova defeito**; depende de regime e referência física.

## Dois intervalos distintos de pulso GNV ausente (`0x90` ainda ativo)

**Evento A — compatível com corte normal na desaceleração (HIPÓTESE):**
- ~21:39:53, ~0,55 s, ~13 quadros com injeção GNV banco 1 zerada; banco 2 também quase sempre zerado (um último quadro tem valor residual).
- OBD velocidade 32–34 km/h, rotação ~2.000 RPM, MAP MP48 ~0,18 bar, comando gasolina ~1,53 ms.
- Pressão de gás aumentou sem pulsos, voltando a cair com retorno dos comandos. Não classificar como apagão.

**Evento B — candidato prioritário para diagnóstico de marcha lenta/anti-apagão (HIPÓTESE):**
- ~21:40:17, ~0,45 s, **11 quadros com ambos os bancos de injeção GNV zerados**, apesar do estado `0x90`.
- Veículo parado: OBD 0 km/h. MP48 rotação 758–925 RPM, MAP ~0,59–0,805 bar, comando original de gasolina ~5,0–9,75 ms. Pressão do gás subiu de ~2,51 bar antes até acima de 3,17 bar durante a ausência de pulsos, consistente com menor consumo no rail, mas não prova causalidade.
- OBD STFT ~+13% perto do evento, **sem relação causal estabelecida** devido a cadência e atraso do controle de mistura.
- Rotação nunca chegou a zero na captura. **Não chamar de apagão; classificar como interrupção de injeção perto da marcha lenta a investigar.**
- Diferenças frente ao Evento A (MAP alto, comando gasolina alto, velocidade zero) tornam improvável reduzir ambos ao mesmo rótulo sem estudo.

## Integração correta ao Ômegas
- **AutoCal:** relacionar eventos de aquisição/zonas e comutação ao estado térmico real da MP48 e ao OBD, sem tocar no AutoMatch.
- **Refino:** observar diferenças condicionadas por RPM, MAP, combustível, pressão, temperatura e STFT/LTFT; não chamar percentual de STFT isolado de diagnóstico.
- **Mapa/Curva K:** relacionar leituras dessas tabelas aos eventos, sem fingir que esta captura contém o Mapa K ou Curva K (não contém).
- **Ferramentas / anti-apagão e marcha lenta:** classificar separadamente corte normal, perda de pulsos com demanda, queda de rotação e motor parado. Só recomendar depois de evidência replicada; jamais escrever automaticamente.
- **Comutação:** distinguir "aguardando autorização do gás" de "transição imediata", "GNV ativo", "corte", "desconhecido"; guardar byte original.
- **Diagnóstico:** monitorar falha de comunicação, amostra perdida, interrupção de pulsos e incoerência de sensores.
- **Sessões:** preservar rawHex de ambas as fontes e oferecer exportação com metadados e versão do decoder.

## Próximas verificações (sem tocar no carro agora)
1. Teste offline reproduzível que encontre automaticamente as duas janelas de pulsos zerados, sem converter isso em causa.
2. Verificar com o produto principal as fórmulas de temperatura, gás, níveis e byte[19]. Consultar provas originais antes de correção de escala.
3. Mapear suporte do OBD PID `0142` (tensão da central) sem prejudicar ritmo de leitura; observar o mesmo evento de troca.
4. Em sessão futura, capturar `0x88`/ `0x90` com monitor de estados, temperatura, configuração de comutação e *eventos* da ECU/MP48 no mesmo relógio.
5. Integrar relatórios somente-leitura nos cartões canônicos do Ômegas após validação com corpus; NÃO modificar Mapa/Curva K nem parâmetros automaticamente.

## Referências externas
- Landi Renzo — manual de operação: https://manualzz.com/doc/5645800/landirenzo-omegas-lpg-system-user-instructions
- Landi Renzo — manual CNG de instalação: https://manualzz.com/doc/en/26787903/landirenzo-cng-system-installation-handbook
- OBD 01 PIDs e fórmulas: https://obd-data.com/pids/
- Fontes internas: `PROTOCOLO-LANDI/18-FORMULAS-ESCALA.md`, `app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt`.
