# OMEGAS Verde — ProgBase AutoCal parity design

**Date:** 2026-09-21  
**Repo:** `viluadmcontas2-dot/OMEGAS-V8.2`  
**Branch:** `OmegasVerde`  
**Spec Kit:** `OMEGAS-SK-001`  
**WorkUnit:** `OMEGAS-WU-006`  
**Epic:** #81

## 1. Problema

O AutoCal atual passou por várias correções de autoridade, temporalidade e HMI, mas o teste físico do owner revelou um problema mais fundamental: a rotina deixou de se comportar como a ferramenta original.

Sintomas reportados:
- curva de aprendizado/referência não aparece continuamente;
- AGORA pode não aparecer;
- UI fica presa em “sem referência / leitura parcial / 18 de 22”;
- LEVELS está fora da superfície principal;
- sessão AutoCal parece paralela à sessão geral.

A solução não é adicionar mais fallback nem inferir comportamento pelo OMEGAS. A referência comportamental é o **ProgBase original + Portmon + logs reais**.

## 2. Autoridade

### Fonte técnica
GitHub remoto / `OmegasVerde`.

### Referência de comportamento
ProgBase original:
- arquivo: `Copy of ProgBase (3).exe`;
- SHA-256: `8A2D297C8C21FF3B4F7A47F7FE64593B0FEC9014DD938BD91022DC0C68AC36F4`;
- versão: `4.2.0.6`.

### Referência de UX
CUSTOMROM + OMEGA DEV em modo read-only:
- intenção humana primeiro;
- superfície dominante;
- normal compacto;
- erro/contexto expandem quando necessário;
- detalhe técnico sob demanda;
- feedback imediato;
- menos cliques;
- uma autoridade de estado.

## 3. NON-GOAL vinculante

SIL/CIU é uma linha independente.

**Nenhum código SIL/CIU será portado, copiado, cherry-picked, mesclado ou usado como autoridade de implementação nesta WorkUnit.**

## 4. Evidência original confirmada

### 4.1 Objetos/consumers VCL

A extração de forms/RTTI do binário encontrou:
- `TAutoCalUI`
- `TAutoCalDM`
- `TFormRifAutocal`
- `ChartData`
- `PetrolCurve`
- `GasCurve`
- `RunPoint`
- `CurrentBand`
- `PollingPetrol`
- `PollingGas`

Esses nomes não provam toda a implementação interna, mas provam que o AutoCal original foi organizado em torno de curvas, ponto de execução e polling dedicado.

### 4.2 Cadência serial observada

No Portmon original:
- telemetria `48 01 49`: mediana ~46,57 ms;
- família `0x015B..0x0163`: ~2,01 s;
- `0x018D/0x018E`: ~4,05 s.

A leitura AutoCal aparece intercalada com a telemetria viva.

### 4.3 Implicação

O produto original não depende de o operador solicitar manualmente um snapshot para manter a tela AutoCal útil. O OMEGAS precisa ser comparado contra essa propriedade consumer por consumer antes de corrigir o monitor.

## 5. Matriz de paridade

Cada linha deve registrar:

| Campo | Conteúdo |
|---|---|
| ProgBase producer | comando/endereço/origem |
| ProgBase cadence | intervalo/ordem observados |
| ProgBase consumer | série/campo/estado |
| OMEGAS producer | classe/método |
| OMEGAS consumer | projeção/bridge/UI |
| Status | MATCH / INTENTIONAL_IMPROVEMENT / MISSING / WRONG / INCONCLUSIVE |
| Evidence | hash/log/arquivo/linha/teste |
| Next | nenhuma ação ou RED específico |

Itens prioritários:
1. PetrolCurve;
2. GasCurve;
3. RunPoint/AGORA;
4. CurrentBand;
5. PollingPetrol/PollingGas;
6. ACQUIRED_ZONES;
7. LEVELS RAW;
8. source/reference freshness;
9. session generation/persistence;
10. Start/Pause state.

## 6. Regra de implementação

Nenhuma divergência vira patch por “parecer melhor”.

Fluxo obrigatório:
`evidência original -> diferença atual -> hipótese -> RED real -> correção mínima -> GREEN -> render/replay -> CI`.

## 7. Gate visual/runtime global

Para superfícies dependentes de telemetria:
- corpus real versionado;
- replay determinístico;
- maior fronteira real aplicável do runtime;
- assets/rotas reais da WebView;
- viewport `1280x720`;
- screenshot/estado como artifact;
- assertions humanas.

O modo demo senoidal do browser não é evidência suficiente para produto.

## 8. AutoCal — critério de produto

Uma referência nativa válida deve resultar em curva visível sem exigir “consulta” operacional repetida.

AGORA:
- usa telemetria fresca;
- fica no mesmo gráfico;
- não redefine escala da referência;
- não some silenciosamente quando a telemetria aplicável está válida.

Estado parcial:
- é informação técnica;
- não deve dominar a superfície normal se a curva operacional necessária pode ser mantida por leituras válidas;
- quando realmente bloquear o gráfico, explicar causa e próxima ação.

## 9. LEVELS

`LEVELS RAW` é sinal do sistema GNV e deve existir também no Dashboard/Agora.

Não converter RAW em:
- percentual;
- litros;
- m³;
- “15 m³ cheios”.

O cilindro nominal (~15 m³) e enchimentos usuais (~11–12 m³) não criam uma função de conversão sem calibração física de pressão/temperatura.

## 10. Sessão

AutoCal é evidência da sessão OMEGAS, não um produto de sessão paralelo.

A investigação deve localizar:
- `SessionRecorder` canônico;
- arquivos do ZIP/export;
- persistência em armazenamento interno;
- writers/readers AutoCal específicos.

A correção final deve preservar compatibilidade e unificar autoridade, não apagar histórico por aparência de duplicidade.

## 11. Critério de encerramento

O AutoCal só pode ser considerado software-side PASS quando:
- paridade original está mapeada;
- curva e AGORA passam replay/render;
- estados parciais/stale não escondem dados válidos;
- LEVELS RAW está na superfície principal;
- sessão é coerente;
- suites e CI passam no mesmo SHA.

Validação física continua separada.
