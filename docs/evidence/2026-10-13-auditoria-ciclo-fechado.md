# Auditoria de ponta a ponta — ciclo fechado do APK (2026-10-13)

Objetivo do dono: regular o GNV até ficar linear e equivalente à gasolina, sem engasgo, num ciclo curto (15–20 min numa conversora), sem ponta solta. Metodologia: 10x-team (SDE + QA) com prova por emulação antes de qualquer APK.

## Correção sobre os dados (o que eu disse antes estava mal formulado)
- O corpus compacto do repositório tem 93 sessões. O Drive (`00_RESUMO_SESSOES.json`, lido inteiro) tem **104 sessões consolidadas** (79 com GNV; ~11–16 min cada) + 40+ pacotes sem GNV (rank 123–177). Não são milhares.
- **Só 4 sessões têm época de calibração (1 AutoMatch cada)**; nenhuma tem 2 snapshots válidos de AutoCal. Não é filtro meu: é o que o Drive tem.
- Fontes ricas ainda não usadas em replay: `PortmonAUTOCAL.zip` e `PortmonLOGNOVO.zip` (14–15 MB, captura serial do app ORIGINAL; o conector só baixa zips ≤ ~6 MB). Só a fixture `autocal-captura-ac-t1000.json` deriva deles. Para provar "cabo de guerra" com dados reais faltam sessões longas com 3+ AutoMatch.

## O ciclo, módulo por módulo (o que fecha, o que ainda está solto)
| Etapa | Quem faz | Fecha? | Prova |
|---|---|---|---|
| Conectar e ler (curva, mapa, contadores) | app lê, automático (sem botão Reler) | sim | teste `autoSync`, fuzz |
| Adquirir gasolina/GNV em qualquer ordem | **ECU**; app só lê | sim (leitura) | monitor real × ECU falsa: 5 cenários + fuzz |
| Reaprender | app envia modo 1/2/4; ECU zera | bytes iguais ao original; modos 1/2 nunca vistos no fio | `verificar_protocolo_autocal.py` |
| Medir vai-e-volta da curva entre AutoMatch | app só mede (`kMovement`) | sim, medido; efeito real em carro = DESCONHECIDO | JUnit + cenários F/G + fuzz |
| Refino propõe | Kotlin `RefinementAutopilot` | só com ECU concluída (`ecuDone`) | ADR-001 |
| **Gravar Curva K / Mapa K (proposta do app)** | dono, 1 toque, ACK+readback | **só com a porta aberta** (ECU terminou) | `write-gate.test.cjs` |
| Exportar Curva K → Mapa K refinado | dono | porta + só pontos que a ECU não mexeu | `curva-k-para-mapa-k.test.cjs` |
| Verificar depois de gravar | Kotlin (VERIFICANDO/ESTÁVEL) | sim | testes do piloto |
| Medidor de uso | app, sem rede | sim | `usage_meter.cjs`, estresse |

## Pontas soltas encontradas e tratadas
1. **Gravação durante o AutoMatch** (proposta do reforço de engasgo e exportação não esperavam a ECU): corrigido com a porta de gravação (ADR-001).
2. 3 arquivos JS só de teste (`portmon-*.js`) iam dentro do APK: movidos para `tools/portmon/`.
3. 3 ligações de botões que não existem mais (`toolsButton`, `toolExportLogs`, `toolSelfTest`) e 4 funções mortas (`setHtml`, `actionLabel`, `nativeZoneCount`, `pct`): removidas; teste que protegia um texto morto ajustado (o texto real do botão continua testado).
4. Script `autocal_abertura.cjs` ainda clicava na aba "Refino local" (podada): corrigido.
5. Contrato JS→Android: **0 chamadas a funções inexistentes** (`tools/audit/pontas_soltas.py`).

## Defeito real achado pelo fuzz (já no APK 7cd4ff9)
- Fuzz de 3000 cenários com anomalias: **437 (14,6%) terminavam com um combustível "pendente" para sempre** (app mostra 0, a ECU já tem até 18 bandas). É o sintoma "depois de Reaprender não adquire até trocar de combustível".
- Causa: o reset guardava como base a leitura anterior, que o app zera quando o AutoMatch nativo muda de época; sem base, a regra "só zero libera" nunca liberava se o carro readquirisse antes de o app ver o zero.
- Correção (`NativeAutoCalAcquisitionEpoch`): a base passa a ser o último grupo aceito (sobrevive à troca de época) e só a leitura **idêntica** à de antes do reset é "velha"; qualquer diferença é dado vivo da ECU. Regressões JUnit novas + fuzz: antes 437/3000 violações; depois **0/3000** (sem exceção, app = ECU, ninguém preso em "pendente"). CI do commit da correção: verde. O APK `7cd4ff9` (entregue) ainda tem o defeito; a correção está em `86d9337`.

## Ainda solto (decisão/CI necessária)
- 33 funções da ponte Android sem uso no JS (ex.: `setGnvSettings`, `runProtocolLab`, `restartEngine`, `previewKMapCell`, `setGpsEnabled`): superfície exposta ao WebView sem uso. Poda recomendada no próximo ciclo, com CI (Kotlin não compila local).
- `NativeAutoCalMonitor` lê as zonas dos dois combustíveis e a cada 1 s (original: só o combustível em uso, ≈2 s): mais tráfego a 9600 baud. Mudança de comportamento: decisão do dono.
- Sessão curta para conversora: hoje a ECU só "termina" com 3 de 3 AutoMatch, aquisição completa quieta por 10 min ou parcial por 25 min. Idea: usar o AutoMatch manual (modo 8) quando houver bandas suficientes para encurtar a volta — risco e efeito DESCONHECIDOS; só com sua autorização e teste no carro.
- Cabo de guerra do GNV (pontos "fora da curva" por RPM): plano 2-D (Curva K = tendência; Mapa K = resíduo por RPM). Não implementado.

## Provas desta rodada
- `tools/jvm/rodar_aquisicao_emulada.sh` (kotlinc 2.0.21 + org.json; `NativeAutoCalMonitor` real; ECU falsa com perda de resposta, payload truncado, desconexão, troca de sessão USB, reset parcial e total, AutoMatch reescrevendo a curva): cenários A–G.
- `tools/ci/estresse_interface.cjs`: p95 ≤ 4,5 ms por quadro a 4× de lentidão, 20 e 100 Hz; medidor de uso 0,29 µs por toque.
- Não provado: aparelho; efeito físico; replay do log bruto original; sessões longas.
