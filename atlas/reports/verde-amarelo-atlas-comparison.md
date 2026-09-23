# Verde × Amarelo × Atlas — refino pós-closure

Snapshot: 2026-09-23 01:03 BRT

## Resultado

**Atlas é a autoridade científica e o gate de handoff.** A reversão nativa está fechada em **1417/1417 alvos semânticos** e **14/14 gates comportamentais**. O Atlas não vira uma quarta UI: ele absorve os melhores mecanismos dos outros ramos sem permitir que um consumidor redefina o ProgBase.

**Amarelo é o challenger de runtime/replay.** O Atlas validou **964 transações** do replay contra o LOGNOVO canônico, byte a byte e com a regra temporal corrigida. A evidência de cockpit/runtime é valiosa para integração, mas não ganha autoridade semântica sobre o EXE/logs.

**Verde é o consumidor de produto e fonte de leads.** Ele está mais avançado em operação humana, Map K/Curve K/Predictor, segurança e simplificação de writes. Porém o fixture atual capturado em `d120dc4...` ainda possui **5 conflitos materiais** com o mapa de ações provado pelo Atlas; portanto o handoff permanece fail-closed.

## Crivo Notion resolvido

Leitura viva do Governance EntryPoint confirmou:

- `OME-STATE-HUMAN-UI`: ACTIVE; binding `OMEGAS_V8_2` = **APPLIES**.
- `UIUX-CUSTOMROM`: ACTIVE; binding `OMEGAS_V8_2` = **APPLIES**.
- `UIUX-OMEGADEV`: ACTIVE; binding `OMEGAS_V8_2` = **APPLIES**.
- `GLOBAL-LEDGER-001`: ACTIVE e exige audit epoch/run provenance-based + meta-audit distinto.

O Atlas reutiliza `docs/contracts/transversal-pass-fail-gate.json`; não cria uma segunda governança.

## O que foi promovido

Do **Amarelo**:
- replay canônico como challenger de integração;
- prova runtime/WebView/cockpit;
- captura limpa da janela do app;
- detalhe técnico sob demanda.

Do **Verde**:
- uma ação humana final mais direta para writes;
- preview/preparo antes do commit;
- checkpoint/validação/ACK/readback/recovery automáticos;
- backup/recovery;
- regressões reais como sinais de drift.

Do **CUSTOMROM + Omega Dev**:
- intenção humana → consequência → detalhe;
- UI projeta typed state, nunca cria ciência;
- normalidade compacta, problema ganha espaço;
- falha + recuperação visíveis;
- rollback visível;
- `data-intent`/intent estável;
- sem silent clamp;
- sem porcentagem inventada;
- screen/route lifecycle não governa aquisição;
- uma única autoridade Store/Router/Scheduler.

## O que foi rejeitado

- fixture/oracle consumidor acima do EXE/logs;
- 75 ms de apresentação como paridade do scheduler nativo;
- ciência/readiness/confidence inventada em JS;
- lifecycle de tela controlando serial;
- replay sendo promovido a oracle;
- duplicação de autoridades;
- write sem preview, ACK/readback e recovery.

## Refinamento de UX adotado

**Síntese Atlas, não citação literal do Notion:** uma única confirmação humana final é aceitável quando o preview preparado já está visível. Isso remove desgaste sem retirar os gates automáticos de ciência e segurança.

## Estado do handoff

`ATLAS_SCIENCE=CLOSED`

`PRODUCT_HANDOFF=HOLD`

O HOLD é correto: ainda existe drift do consumidor Verde e um snapshot de repositório não pode autoafirmar Contract Registry/auditoria/meta-auditoria vivos. O gate falha fechado até que esses requisitos sejam satisfeitos num audit epoch independente.
