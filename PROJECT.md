# Projeto OMEGAS V8.0 RED Performance

## Objetivo

Reduzir tempo, combustível e esforço até aproximar de zero a diferença entre a injeção de gasolina de referência e a injeção comandada quando o veículo opera em GNV.

## Contrato científico

- `(RPM, MAP)` identifica a condição física comparável.
- Gasolina constrói `Tinj_ref(RPM, MAP)`; GNV observa `Tinj_petrol_on_CNG(RPM, MAP)`.
- O erro nasce do par comparável, não de dois agregados exibidos na mesma célula.
- Cada evidência válida atualiza imediatamente uma superfície contínua.
- O modelo separa `tendência global Curva K(Tinj_ref)` de `residual local RPM×MAP`.
- A grade RPM×Petrol Inj. apenas projeta a previsão para o Mapa K físico.

## Produto e segurança

- Aprender mostra evidência direta, previsão, procedência e incerteza.
- Curva K recebe tendência global; Mapa K recebe residual local.
- Auto-Cal é manual e interoperável com o ajuste global.
- Escrita ECU é manual: preparar → revisar → confirmar → ACK → readback.

## Governança ativa

- WorkUnit: `docs/workunits/OMEGAS-RED-WU-001.md`
- Issue: `#9`
- Branch: `hotfix/v8.0-red-performance`
- Spec: `docs/superpowers/specs/2026-08-30-red-continuous-fast-learning-design.md`
- Plano: `docs/superpowers/plans/2026-08-30-red-continuous-fast-learning.md`
