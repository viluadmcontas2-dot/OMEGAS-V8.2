# Work Unit — OMEGAS-BLUE-OBD-INDEPENDENT-002

- Issue: #29
- Branch: `work/omegas-blue-causal-engine`
- Objetivo: dois motores autônomos, confiança OBD→MP48 opcional e não bloqueante, sugestão OBD fail-closed e APK verificável.

## Evidência

- Base: `e340709908f74a7e0181e81ad665aae2dcbcd408`
- RED engine: `a3c830e69f9391296fa1fa10d16747c4fb35ce26` / run `34520184617`
- RED integration: `f4742ec70fe00518b0dd2ecf13047a1554f15916` / run `34521120066`
- GREEN verified: `a69af677d06b37096ba689f15feea260a1912008` / tree `e2aaae78723a354799ab92b41f322732af388035` / run `34522976601`
- FAST: success
- FULL JVM/unit/lint: success
- APK: pending final manual gate

## Limites

- Sem validação em veículo.
- OBD padrão não fornece Petrol Inj.; endereço exato do Mapa K exige resolvedor confiável.
- Nenhuma escrita automática.
