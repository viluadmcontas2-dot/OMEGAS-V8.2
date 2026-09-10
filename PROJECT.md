# Projeto OMEGAS V8.2 Blue

## Objetivo humano

Regular o GNV com pouco esforço humano e máxima rastreabilidade, mantendo dois motores científicos autônomos e nenhuma escrita automática.

## Contrato científico atual — Issue #29

### MP48

- Coleta gasolina e GNV normalmente.
- Calcula o erro pelo método original: Petrol Inj. da gasolina comparado ao Petrol Inj. observado no GNV sob RPM × MAP equivalentes.
- Funciona sem OBD.
- STFT OBD concordante pode aumentar confiança; ausência ou conflito nunca altera o erro, o alvo ou a disponibilidade MP48.

### OBD

- Aprende somente STFT Bank 1 no GNV.
- Adquire RPM (010C), MAP (010B) e STFT (0106) no mesmo ciclo.
- Não depende de combustível, Petrol Inj., telemetria ou disponibilidade MP48 para medir.
- Exige declaração GNV a cada vida do serviço.
- Persiste amostras por RPM × MAP e época; usa mediana/MAD, mínimo de 5 amostras e qualidade mínima 0,55.
- Produz correção própria. O endereçamento exato do Mapa K é fail-closed porque o eixo físico é RPM × Petrol Inj.; sem resolvedor confiável, mantém o percentual e retorna `ADDRESS_UNRESOLVED`.

## Aplicação e segurança

- Resultados MP48 e OBD são identificados separadamente.
- Nenhum motor bloqueia o outro.
- OBD nunca escreve K; seu adaptador prepara no máximo uma célula.
- Toda mutação segue preparar → revisar → confirmar → ACK → readback.
- Leitura, aprendizado e proposta não escrevem na ECU.

## Rastreabilidade

A linha ativa é Issue #29 → branch `work/omegas-blue-causal-engine` → especificação/plano datados → RED → GREEN → revisão → CI exata → APK manual. Validação física continua separada da prova de software.
