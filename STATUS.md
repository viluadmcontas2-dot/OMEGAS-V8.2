# Status — OMEGAS V8.2 Blue

- Branch: `work/omegas-blue-causal-engine`
- Issue canônica: [#29](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/issues/29)
- Base confirmada: `e340709908f74a7e0181e81ad665aae2dcbcd408`, árvore `666a9dec1addea13875c6f64163201465477478e`
- Último software verificado antes desta reconciliação: `a69af677d06b37096ba689f15feea260a1912008`
- Árvore: `e2aaae78723a354799ab92b41f322732af388035`
- CI canônica de software: run `34522976601` — `completed/success`
- Escrita automática: `FALSE`
- Validação física: `NOT VALIDATED`

## Estado implementado

- MP48 preserva o método original gasolina × GNV.
- OBD aprende independentemente por RPM 010C + MAP 010B + STFT 0106.
- OBD exige declaração GNV por vida do serviço.
- Ciclo incompleto, inválido ou maior que 750 ms não fica data-ready.
- Evidência OBD usa mediana/MAD, mínimo 5, qualidade 0,55, limites físicos, regiões e épocas isoladas.
- Evidência OBD é restaurada de snapshot atômico; persistência quente é limitada a uma gravação por 5 s e forçada no shutdown/mudança de época.
- STFT +10% produz 1,10; -10% produz 0,90; faixa 0,80..1,20; deadband 1%.
- OBD concordante pode bonificar confiança MP48; conflito/ausência nunca bloqueiam nem alteram a matemática MP48.
- Sugestão OBD é manual e no máximo uma célula. Sem Petrol Inj. confiável para o eixo físico, retorna `ADDRESS_UNRESOLVED`.
- Alterações confirmadas de Mapa K, Curva K ou calibração nativa abrem nova época OBD.
- UI identifica o OBD como sistema independente.

## Linha TDD e CI

- RED `a3c830e69f9391296fa1fa10d16747c4fb35ce26`, run `34520184617`: FULL falhou pelos novos símbolos OBD inexistentes.
- RED integração `f4742ec70fe00518b0dd2ecf13047a1554f15916`, run `34521120066`: contratos exigiram aquisição e isolamento ainda ausentes.
- GREEN principal `5c49bf21dfbcdc81a7e8140438f0fb752ae3b4ea`.
- Correções rastreadas: `bd886ce`, `54bdb4e`, `377926a`, `d55c76f`, `d5f14a0`, `9c4f0d0`, `c61a0c5`, `a69af67`.
- Run `34522976601`: FAST success; FULL JVM lint success; APK skipped conforme gate.
- A suíte executou 273 testes JVM e lint Debug, além dos contratos Python/Node/browser.

## Revisão multiperspectiva

1. Arquitetura: dependência assimétrica documentada; OBD não depende de MP48.
2. Matemática: sinais, clamp, deadband, mediana/MAD e região verificados por testes.
3. Transporte Android: prontidão separa transporte de ciclo científico completo.
4. Persistência: snapshot versionado, restauração validada e escrita atômica limitada.
5. Segurança: modo GNV explícito, épocas após calibração e zero writer no pacote OBD.
6. UX: origem e estado aparecem separados; endereço não resolvido é explícito.
7. Regressão MP48: testes de equivalência gasolina × GNV continuam verdes.
8. CI: contratos rápidos precedem Gradle completo.
9. Rastreabilidade: Issue #29 liga decisões, RED, GREEN e recibos.
10. Operação física: permanece gate humano; nenhuma economia ou compatibilidade real foi inferida.

## Gate atual

Este commit de reconciliação precisa de uma nova CI no seu SHA exato. Após sucesso, o workflow manual `blue-ci.yml` será disparado com `build_apk=true`; o artefato só será aceito após SHA-256 e tamanho baterem com o recibo.
