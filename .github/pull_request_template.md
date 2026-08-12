## Objetivo observável

Descreva o que muda para o usuário e o que permanece intocado.

## Escopo

- [ ] Uma mudança observável por vez
- [ ] Sem alteração paralela de matemática, protocolo, persistência ou UI
- [ ] Sem escrita automática na ECU
- [ ] Curva K e Mapa K continuam separados
- [ ] OBD continua observacional

## Evidência

- [ ] Gate rápido: `python -B tools/run_checks.py`
- [ ] Testes Kotlin/JVM: `testDebugUnitTest`
- [ ] Android Lint: `lintDebug`
- [ ] Build debug: `assembleDebug`
- [ ] Relatórios revisados
- [ ] Artifact e SHA-256 ligados ao commit

## Mapa K, quando afetado

- [ ] Leitura antes da seleção
- [ ] Leitura automática não inicia escrita
- [ ] Editor visível ao tocar
- [ ] 1 a 16 células; 17ª bloqueada sem apagar seleção
- [ ] Linha técnica protegida
- [ ] Revisão antes/depois
- [ ] Cancelar não escreve
- [ ] Uma chamada de lote
- [ ] Falha de ACK não é sucesso
- [ ] Readback divergente não é sucesso
- [ ] Tela atualizada pelo readback real

## Validação externa

- [ ] Não se aplica
- [ ] Aguardando validação no celular
- [ ] Aguardando validação física no veículo
- [ ] Validado no aparelho identificado no comentário

## Risco e rollback

Informe riscos residuais e o commit/estratégia de rollback.
