# O que são os quadradinhos, as bolinhas e os ✕ do gráfico do AutoCal (pergunta do dono, 2026-10-13)

Fonte: emulador do original (`tools/orig_emulator.py` do PROTOCOLO-LANDI, regras marcadas OBS = observadas no código decompilado) + nossos dados reais.

## Painel da esquerda ("Acquisition") — os quadradinhos
- A ECU guarda 18 **faixas de aquisição** (buffers) por combustível: contador `NUM_BUF_UPD` (quantas amostras entraram), tempo `PETR_INJ_TBUF` e pressão `MNFLD_PRESS_BUF` (médias da faixa).
- **1 quadradinho = 1 faixa "pronta"**: contador ≥ limiar (faixas 0–5 usam um limiar, 6–17 outro; os limiares vêm da própria ECU: `CALIBRATION_VAL_1` e `VECT_AUTOCAL_U8_1`) e MAP guardado > 0.
- Coluna rosa/esquerda = gasolina; coluna verde/direita = GNV. **Altura = o MAP médio guardado naquela faixa.** A linha cinza = o MAP de agora.
- Cada quadradinho é a mesma faixa de uma bolinha do gráfico grande (mesma altura; a bolinha mostra também o tempo de injeção no eixo X).

## Por que se mexem (e a ECU ajusta em tempo real?)
- A ECU **não corrige a curva em tempo real**. Ela vai **acumulando amostras** nas faixas (os quadradinhos e bolinhas se mexem porque a média da faixa muda a cada amostra) e só **recalcula a Curva K no AutoMatch** (no máximo 3).
- Prova nos seus dados: em 160 pares de snapshots consecutivos, a Curva K ficou **igual em 149**, e **em 143 desses a posição (MAP) de algum quadradinho de GNV mudou** (ex.: sessão 003, faixa 4: contador 10→10, MAP bruto 446→448). Nas 5 vezes em que a ECU mexeu na curva, foi 28–30 pontos de uma vez, junto com o contador de AutoMatch.
- Inferência (não vista no firmware): a média da faixa é um filtro/média móvel.
- Quando o GNV "se iguala à gasolina na frente do dono": ou são as médias das faixas convergindo, ou são as gravações de K (do Refino/manuais); a ECU sozinha só muda a linha no AutoMatch.

## Gráfico grande
- **Bolinhas** (vermelha/gasolina, verde/GNV): as faixas prontas, na posição (tempo de injeção médio, MAP médio).
- **Bolinhas claras e transparentes** (no original `GasPointPrev`): o GNV da aquisição ANTERIOR (`..._GAS_PREV`), para ver o quanto mudou.
- **✕ na linha:** os **30 pontos da curva da ECU** (`PETR_INJ_TBP`), os mesmos 30 pontos da Curva K, e a linha liga os ✕. Era o que a legenda não dizia (agora: "Curva da ECU (✕ = pontos)").
- **#n/m à direita das faixas de zona** (ex. #4/3): n = quantas faixas daquela zona estão prontas; m = quantas a ECU exige para dar a zona como adquirida. Conta FAIXAS, não amostras (o texto "Amostras por zona" estava errado; agora "Prontas por zona").
- **Fundo verde/amarelo:** zona adquirida / não adquirida (`ACQUIRED_ZONES_*`). **Faixa clara horizontal:** o MAP de agora. **Linhas tracejadas pretas:** divisas entre zonas. **Vermelha/azul tracejadas:** limites máximo/mínimo de MAP.
- Tocar numa bolinha mostra o detalhe ("GNV · ponto 12 · zona 3 · pronto, 6,22 ms · 0,779 bar · 7 medições (mínimo 3)"): a faixa 12 tem 7 amostras e o mínimo é 3.

## Não provado
- Fórmula exata da média da faixa na ECU; significado de cada limiar além da regra de "faixa pronta" do decompilado.
