# OMEGAS Verde — Status

## Active control surface

- Spec Kit: `OMEGAS-SK-001`
- WorkUnit: `OMEGAS-WU-006`
- Epic: #81
- Branch: `OmegasVerde`
- Estado: `ACTIVE — DISCOVERY/PARITY`

Sempre resolver o HEAD remoto antes de agir. Este status registra evidência, não fixa o HEAD para sempre.

## Baseline de evidência

- Baseline científico/runtime antes deste pacote documental: `6049a6f4d9b56aa6d380500a475567ec6f1ad4bc`
- Fast contracts: PASS nesse baseline
- OMEGAS VERDE CI: PASS nesse baseline
- Fixture real MP48: `tests/fixtures/portmon-autocal-cycle-v1.json`
- Origem do fixture: Issue #68 / Portmon real

## Descobertas confirmadas que orientam a próxima execução

### ProgBase original

Executável:
`G:\Meu Drive\OMEGAS\Copy of ProgBase (3).exe`

SHA-256:
`8A2D297C8C21FF3B4F7A47F7FE64593B0FEC9014DD938BD91022DC0C68AC36F4`

Estruturas VCL observadas:
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

Portmon bruto observado:
- `48 01 49`: mediana ~46,57 ms;
- família AutoCal `0x015B..0x0163`: ~2,01 s;
- `0x018D/0x018E`: ~4,05 s.

### OMEGAS atual

Hipótese forte já sustentada por leitura de código:
- `NativeAutoCalMonitor` trabalha por probe/material-change e não mantém por si só a mesma renovação contínua observada no ProgBase;
- o tick do monitor passa pelo health loop de serviço;
- essa diferença precisa ser fechada por mapa producer/consumer + RED visual/runtime antes de qualquer correção.

## Relatos físicos do owner que precisam virar RED

- curva AutoCal não aparece como no ProgBase;
- cursor AGORA pode desaparecer;
- estado pode ficar em “sem referência / snapshot parcial / 18 de 22”;
- LEVELS não está no Dashboard/Agora;
- AutoCal parece manter fluxo/sessão separado em vez de integrar a sessão canônica.

Estes itens são requisitos de investigação/teste, não prova automática de causa.

## Próximo passo único

Concluir #82: mapa byte/consumer ProgBase. Depois preencher #83 com classificação MATCH / INTENTIONAL IMPROVEMENT / MISSING / WRONG / INCONCLUSIVE e abrir REDs apenas para divergências confirmadas.

## NON-GOAL

Nenhum port SIL/CIU nesta WorkUnit.
