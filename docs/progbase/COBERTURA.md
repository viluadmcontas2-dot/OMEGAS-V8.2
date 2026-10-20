# Cobertura da engenharia reversa (ProgBase)

Gerado por `tools/progbase/cobertura_re.py` (dados até 2026-10-18). Detalhe: `cobertura.json`. Cobertura = média de: % provado (OBS-FIO+OBS-COD+DERIVADO sobre as evidências), abas com vetor e vetores no fio com unidade.

Total: 89 evidências, **76.4% provado**; 33 abas, 389 vetores (222 no fio); 1713 funções do programa sem dono.

| Área | Cobertura | Provado | Ev. | Abas c/ vetor | Vetores | App usa |
|---|---|---|---|---|---|---|
| Anti-apagão | `███████░░░░░░░░░░░░░` 34.9% | 69.7% | 33 | 0/0 | 0/1 | 23/33 |
| Volta à gasolina | `██████░░░░░░░░░░░░░░` 27.5% | 82.4% | 17 | 0/48 | 0/7 | 10/17 |
| AutoCal/preferências | `███████░░░░░░░░░░░░░` 33.3% | 100.0% | 1 | 0/66 | 0/10 | 0/1 |
| Mapa K/bins | `████░░░░░░░░░░░░░░░░` 20.0% | 60.0% | 10 | 0/13 | 0/8 | 2/10 |
| Curva K | `██████░░░░░░░░░░░░░░` 28.6% | 0.0% | 0 | 4/7 | 0/7 | 0/0 |
| Marcha lenta | `███████░░░░░░░░░░░░░` 33.4% | 66.7% | 6 | 0/0 | 0/2 | 5/6 |
| Injetores/F9 | `███████░░░░░░░░░░░░░` 35.7% | 100.0% | 2 | 0/93 | 4/56 | 0/2 |
| Nível do gás | `████████░░░░░░░░░░░░` 39.0% | 100.0% | 1 | 8/47 | 0/6 | 0/1 |
| Diagnóstico | `███████░░░░░░░░░░░░░` 33.3% | 100.0% | 1 | 0/4 | 0/14 | 0/1 |
| Display | `██████████░░░░░░░░░░` 50.0% | 100.0% | 1 | 0/29 | 0/0 | 0/1 |
| Transporte | `█████████░░░░░░░░░░░` 45.9% | 91.7% | 12 | 0/0 | 0/5 | 9/12 |
| Cadastro/F1 | `█████████░░░░░░░░░░░` 42.8% | 100.0% | 2 | 27/95 | 0/42 | 0/2 |
| F6/F7/F8 | `████████░░░░░░░░░░░░` 37.9% | 100.0% | 1 | 2/22 | 4/86 | 0/1 |
| Geral (decompilação) | `██████████░░░░░░░░░░` 50.0% | 50.0% | 2 | 0/0 | 0/0 | 1/2 |

## 10 maiores DESCONHECIDOS (impacto para o usuário)
1. [Cadastro/F1] PROD-01: Quando o gás entra (F1): tipo de troca, rpm mínimo 1200, água 40 °C
2. [Volta à gasolina] PROD-02: Volta automática à gasolina por pressão baixa (F4): tempo 0 s, valor 1
3. [Diagnóstico] PROD-03: Diagnóstico (F3): 14 verificações, estado OK/erro, ação e caixa por li
4. [Display] PROD-04: Display vivo (F2) e Gráfico: pressão do gás, Press.diff., T.gás, T.águ
5. [Injetores/F9] PROD-05: Injetores (F9) com números: abertura mínima 2,5 ms, extra 0,1 ms, avan
6. [Nível do gás] PROD-06: Nível do gás (F4): 7 tipos de sensor, referências Reserva/1/4/2/4/3/4
7. [Anti-apagão] EV-ANTIAPAGAO-011: Que a central aceita o quadro 0x37 de 10 bytes para o vetor 307 nunca foi visto no fio; só a leitura de v
8. [Anti-apagão] EV-ANTIAPAGAO-014: A escala real da "queda de rotação" (quanto vale 1 rpm/ciclo na central) é desconhecida; a velocidade de
9. [Anti-apagão] EV-ANTIAPAGAO-020: O efeito exato de "Espera para sair do gás" (campo 2) e dos outros campos extras não foi provado.
10. [Anti-apagão] EV-ANTIAPAGAO-022: Se a central mantém o bit 0x20 depois de rodar o próprio ciclo e se a função evita apagões no efeito físi

## Próximo passo por área
- Anti-apagão: Provar no carro a gravação dos 5 números e a escala da queda de rotação.
- Volta à gasolina: Capturar o ACK dos quadros 14 7B|7C no carro e o efeito real.
- AutoCal/preferências: Registrar no fio cada pergunta/ação do AutoCal e as preferências salvas.
- Mapa K/bins: Descobrir qual vetor guarda as 12 linhas de ms dos bins.
- Curva K: Provar a gravação da Curva K em 1 quadro 0x37 com a central real.
- Marcha lenta: Fechar as evidências de marcha lenta em aberto.
- Injetores/F9: Ligar os números da F9 (2,5 ms, extra 0,1 ms) aos vetores no fio.
- Nível do gás: Provar os 7 tipos de sensor e as referências Reserva/1/4/2/4/3/4 no fio.
- Diagnóstico: Mapear as 14 verificações (código, caixa, estado, ação) ao vetor de diagnóstico.
- Display: Confirmar a origem de cada grandeza da barra de status na resposta 48.
- Transporte: Entender a opção +0x2BD do Connect e a perda de porta.
- Cadastro/F1: Provar o tipo de troca, rpm mínimo e temperatura de troca no fio.
- F6/F7/F8: Decodificar o mapa de contribuição de gasolina 12 x 12 (F8).
- Geral (decompilação): Nomear as funções sem dono e os bytes de .text fora de função.
