# Retorno do corte no GNV (~2 ms) e a inteligência do ajuste local — evidência e proposta

Fonte: telemetria `48 01` das capturas `PortmonAUTOCAL (1).LOG` (AC) e `PortmonLOGNOVO.LOG` (LN); só leitura, nada gravado.
Razão = injeção GNV ÷ injeção gasolina (bytes 6 e 8 do `48 01`, × 0,00256 ms). Se a equivalência estivesse perfeita, a razão seria estável.

## O que os dados mostram (OBSERVADO)

**Logo depois que o motor sai do corte de desaceleração (`0x94` → `0x90`), na faixa de 1,5–2,8 ms, o GNV injeta menos do que o normal.**
Referência = razão em GNV estável na mesma faixa de ms, fora dos 1,5 s seguintes a um retorno.

| Captura | Retornos do corte | Com leitura em 1,5–2,8 ms nos 0,5 s seguintes | Desvio da razão nesses 0,5 s |
|---|---|---|---|
| AC | 51 | 36 | 28 abaixo da referência, mediana ≈ −13 %, pior −36 % |
| LN | 14 | 8 | **8 de 8 abaixo**, de −12 % a −30 %, mediana ≈ −25 % |

Isso casa com o sintoma do dono (interrompe só no GNV, perto de parar, embreagem pisada, ~2 ms). **Não prova a causa**: as capturas não têm uma interrupção desse tipo (as 3 quedas de rotação da LN são partidas em GNV com MAP 0,99).

Valores lidos da ECU do dono nesta sessão: `SMAGRIMENTO_RIENTRO_CUTOFF = 100`, `NUMERO_INIETTATE_SMAGRIMENTO = 0`, `NUMERO_INJ_BENZINA_CUTOFF = 0`, `TEMPO_RITORNO_BENZINA = 0`, `SMAGRIMENTO_EXTRA = 5`. O número de injeções de "smagrimento" (empobrecimento) no retorno do corte é 0, então o empobrecimento **não** parece vir desse parâmetro (INFERIDO; o significado exato de cada byte não foi confirmado).

## Limite do Mapa K que importa para o "Curva K → Mapa K" (OBSERVADO + conta)

O Mapa K guarda 1 byte (máx. 255). Com a curva K da captura AC (fator 1,484 de 6 a 10 ms): célula 160 → 237 (cabe); célula 220 → 326 (**passa de 255**, fica 255). Quem já subiu o mapa para 220 não consegue passar toda a curva para o mapa. A tela agora avisa quantas células saturam.

## Proposta de inteligência (para discutir antes de qualquer APK)

1. **Detector de interrupção (só observa):** se a rotação cai de ≥ 600 para < 250 em ≤ 3 s com GNV, guarda os 10 s anteriores (rpm, MAP, ms gasolina e GNV, razão, célula do Mapa K, nó da curva K) e mostra "última interrupção: célula X, razão −N %". Responde "onde mexer".
2. **Mapa de desvio por célula (rpm × ms):** razão GNV/gasolina por célula contra a mediana, com contagem de amostras; célula fora do normal fica marcada. Separa regime estável de transitório (os 0,5 s após o corte).
3. **Filtro de pontos anômalos no AutoCal (só marca):** ponto adquirido em marcha lenta/parado (rpm baixo) que cai fora da curva de cruzeiro é sinalizado como "provável anomalia", sem apagar nada; o dono decide reaprender o ponto.
4. **Exportar cada correção para o lugar certo:** curva K para o que é global; resíduo local por célula do Mapa K, sempre como proposta (prévia, um toque, leitura de volta).

Nada disso grava sozinho. Pergunta ao dono: o ajuste do transitório do corte deve ser só **aviso** ou também uma **proposta de células** (linhas 2,0–3,0 ms, colunas 850–1.350 rpm)? Um aumento nessas células também enriquece a marcha lenta estável, onde a razão já está certa.
