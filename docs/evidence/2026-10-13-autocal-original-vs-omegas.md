# AutoCal: original (capturas reais) × OMEGAS atual — 2026-10-13

Referência: `PROTOCOLO-LANDI` @ `84968e3`, docs 17–20 (capturas `PortmonAUTOCAL (1).LOG`
sha256 `4a70f5ae…` e `PortmonLOGNOVO.LOG` sha256 `43a63272…`, 75 985 transações com checksum
100 %; duas capturas de tela do original). Código conferido em `88ff8c07`.
Ordem: impacto na equivalência GNV × gasolina (curva K = base global; Mapa K = residual).
"Conferido" = lido no código nesta sessão; "relatado" = achado do agente explorador, não
reconferido linha a linha.

## Defeitos

| # | Divergência | Evidência no OMEGAS | Original | Impacto |
|---|---|---|---|---|
| 1 | Fronteiras de zona deslocadas uma banda: zonas em 0,50/0,70/0,90 bar | `assets/ui/screens/autocal-cockpit.js:353` (`edges = [0, 6, 10, 14, 17]`) e `:120` (`zoneForBand`: ≤5, ≤9, ≤13). Conferido | 0,45/0,65/0,85 = `THD[5]`, `THD[9]`, `THD[13]` (linhas pontilhadas da tela; doc 18) | Banda 5 (0,45–0,50), 9 e 13 contadas na zona errada; "zona completa" mostrada diverge da ECU |
| 2 | Estado de combustível `0x94` vira `UNKNOWN` | `java/.../ecu/Mp48Protocol.kt:101-107` aceita só `80/A0/88/A8/90/B0`. Conferido | `0x94` = GNV em corte (1 096 frames, 5 % da sessão AutoCal) | Telemetria fica "desconhecida" em desaceleração no GNV; risco de descartar ou atribuir amostras errado no Refino |
| 3 | `MUL_ACT` (curva K) e `0x018D/018E` lidos a cada 4 s | `NativeAutoCalMonitor.kt:1097-1103` (`REFERENCE_REFRESH_FIELDS`), `NativeAutoCalRefreshPlanner.kt:57`. Conferido | `0x0161` em todo bloco (~2 s); curvas em blocos alternados | A base global pode estar até 4 s atrasada depois de um automatch |
| 4 | Aquisição não é lida com o Enable desligado | `NativeAutoCalMonitor.kt:223,230`. Conferido | Polling independe do Enable (`LN:697674`) | Tela congela com Enable off |
| 5 | Lê as duas zonas sempre | `NativeAutoCalMonitor.kt:538-539`. Conferido | Só a do combustível em uso (byte 11 do `48 01`) | Custo de fio (~25 ms por bloco); dado correto |
| 6 | Mudança de `48 0B[12/13]` dispara um snapshot completo de ~34 campos | `NativeAutoCalMonitor.kt:216-219`. Conferido | Só `09 74 01` | Bloqueia a aquisição durante o snapshot |
| 7 | Eixo Y vai até 1,15 bar; eixo X auto-escalado | `autocal-cockpit.js:12,433-434,446-456`. Relatado | Y 0,15–1,10 (`0x0169/016A`), X fixo 1–22 ms (`0x014B`) | Pontos fora da posição esperada na comparação visual |
| 8 | Sem gráfico K, sem cursor vertical, sem limites tracejados, sem painel Acquisition | `autocal-cockpit.js:1455-1458`, `:1693-1702`. Relatado | Doc 20 | Experiência diferente da referência |

## Conflitos com invariantes do dono (decisão do dono, não alterado)

| Fato | Onde |
|---|---|
| `RESET_ALL` está exposto ("Recomeçar tudo") e liberado na ponte | `autocal-cockpit.js:764`, `AutoCalJavascriptBridge.kt:99`. Conferido |
| AGENTS.md diz "reset de ponto de gás 0x04", mas no código e no fio 0x04 = Reset all; GNV = 0x02 | `AutoCalProtocol.kt:22-24`; doc 19 |
| `RESET_PETROL` também exposto em um toque; AGENTS.md permite só o de GNV | `autocal-cockpit.js:756` |
| Cartão "EVIDÊNCIA CAUSAL" compara a curva K antes × depois | `autocal-cockpit.js:207-216`. Relatado; possível conflito com `autocal-sem-leitura-anterior` |

## O que já bate

Frame do Enable `12 4A 01 vv`; decodificação de `48 0B` (byte 12 concluído, 13 contador);
escalas /512, /1024, Q14; formas de `0x014B` (30) e `0x014C` (18); faixa magenta da banda atual.
