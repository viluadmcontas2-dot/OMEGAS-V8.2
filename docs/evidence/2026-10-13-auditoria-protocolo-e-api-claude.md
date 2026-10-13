# Auditoria do protocolo AutoCal (2026-10-13) e proposta de uso da API do Claude

## 1. O app "só observar" — havia erro antes?
Não. O AutoCal é da ECU: ela faz o AutoMatch e a aquisição. O app lê o estado (`48 0B`, bloco A do doc 17) e só envia ação quando o dono toca em Reaprender (`02 24 04 <modo>`). "Só observa" é o comportamento correto, não uma mudança.

## 2. Verificação de função (feature verification)
Ferramenta: `tools/sessoes/verificar_protocolo_autocal.py` (teste: `tests/test_verificar_protocolo_autocal.py`).
- COMPROVADO (estático): o monitor lê todo o bloco A (0x15B–0x163, 0x16F/0x170, 0x18D/0x18E) e o status `48 0B`; não há nenhuma escrita no monitor.
- Diferenças em relação ao original (observação, não erro):
  1. lemos as zonas dos DOIS combustíveis (original: só do combustível em uso);
  2. aquisição a cada 1 s (original ≈ 2 s) → mais tráfego em 9600 baud;
  3. modos 1 (gasolina) e 2 (GNV) do Reaprender: bytes iguais ao decompilado, mas só o modo 4 foi visto no fio → DESCONHECIDO.
- Limites honestos: comparação estática com o doc 17 e o decompilado. Replay do log bruto (160 MB) NÃO foi feito; só a fixture `autocal-captura-ac-t1000.json`. O MCP do REA estoura 60 s no ProgBase.
- Recomendações (NÃO aplicadas, mudam o `NativeAutoCalMonitor`, exigem decisão do dono): aquisição a 2 s; ler só a zona do combustível em uso.
- Teste no carro (dono): Reaprender GNV, rodar no GNV com carga variada, mandar a sessão ao Drive; olhar `NUM_BUF_UPD_GAS`.

## 3. Export Curva K → Mapa K já refinado
`core/curve-to-map.js`: `refinementTargets(points, proposal)` + `balance({refine})`. Durante o AutoCal o piloto de refino já sabe onde a curva ficou serrilhada; ao exportar, a suavização usa esses alvos (só aplica se `factorRaw === currentRaw`, isto é, se a ECU ainda tem o valor medido). Sem esperar nova aquisição. Escrita continua manual (ACK + readback). Medir: engasgos/h antes e depois (PENDENTE, precisa do carro).

## 4. Medidor de uso (benchmark de botões)
`core/usage-meter.js`: conta toques por aba, botão, célula do Mapa K, ponto da Curva K, ponto do gráfico e tempo por aba. Sem rede, sem valores digitados. Grava na sessão como `ui_usage` (`recordUiUsage` na ponte JS). Útil para podar telas e, no futuro comercial, entender o uso. Teste: `tests/ui/usage-meter.test.cjs`, `tools/ci/usage_meter.cjs`.
Lado Kotlin (`HubJavascriptBridge.recordUiUsage`): sem prova local, depende da CI.

## 5. Uso inteligente da API do Claude (proposta; NADA implementado)
Custo: a API é paga e separada da assinatura Max. Só com autorização específica do dono.
Desenho recomendado (barato e seguro):
1. **Assistente pós-sessão** (opt-in): ao fechar uma sessão, o app monta um JSON pequeno (engasgos por faixa, estado do AutoCal, taxa/h, uso de abas) — sem dados do veículo identificáveis — e pede ao modelo um resumo em português simples: "o que melhorou, o que olhar, próximo passo". Uma chamada por sessão, modelo pequeno, resultado em cache.
2. **"Explique esta tela"**: botão único que envia só o nome da tela e os números visíveis; resposta de 3 linhas.
3. **Triagem de logs no PC**, fora do app: script que lê a sessão do Drive e gera o relatório (usa a assinatura via Claude Code, sem API).
Regras: offline-first (o app funciona sem), nunca escreve na ECU por sugestão da IA, chave fora do APK (proxy ou digitada pelo dono), limite mensal de chamadas.
Recomendação: começar pelo item 3 (zero custo de API) e só depois decidir o 1.
