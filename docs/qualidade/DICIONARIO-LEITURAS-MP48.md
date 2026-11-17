# Dicionário técnico sem ambiguidade — perfil MP48 (P1 / OP-16)

> OBS-COD a partir das rotinas de leitura atuais. NÃO é validação física desta compilação nem um catálogo universal Landi Renzo. Fonte de leitura: components/live-bar.js, core/anti-apagao-valores.js e docs/progbase/protocolo/anti-apagao-quadros-original.md. Dado ausente ou velho = —, nunca zero inventado.

| Grandeza | Origem real no aplicativo / eixos | Unidade mostrada | Grau de certeza / limite | O que NÃO significa |
|---|---|---|---|---|
| Tensão da ECU | live.unknown_raw_19 (byte 19 do quadro 48 01) ÷ 16 | V | Escala PROVISÓRIA; alerta acima de 14,1 V baseado em relato de campo, não característica universal | Não usar como prova de proteção do gás em toda central |
| MAP / pressão admissão | live.load_bar, map_bar ou mapBar no Store | bar | A leitura só aparece quando telemetry.valid e idade até 2.500 ms; escala por perfil ainda não homologada | Não é pressão diferencial de gás ou pressão do cilindro |
| Injeção gasolina | live.petrol_ms / petrolMs | ms | Tempo lido quando o quadro é válido; não comparar condições de carga distintas | Não é injeção GNV nem razão de mistura por si |
| Rotação | live.rpm | rpm | Leitura observável do Store; perde validade se a telemetria envelhecer | Não prova sozinho que houve apagão |
| Combustível atual | live.fuel, valores GNV/GASOLINA | estado textual | Estado ausente vira “—”, sem dedução de troca de combustível | Não prova que a alimentação efetiva está correta |
| Nível indicado GNV | live.level_raw; perfis observados com cortes 39/93/143/219, válido a partir de 26 | bruto → faixas | Cortes derivados de sessões; depende de calibração/sensor do carro; não converter para litros ou bar | Não é pressão do cilindro nem volume exato |
| Pressão diferencial do gás | Não é alimentada pela LiveBar atual | — | DESCONHECIDO para leitura direta deste perfil/build | Não confundir com MAP nem inventar um número |
| Pressão absoluta/total do gás | Não é alimentada pela LiveBar atual | — | DESCONHECIDO; só calcular pressão absoluta com MAP+diferencial medidos e equivalentes no mesmo contexto/escala | Não é pressão de armazenamento do cilindro |
| Temperatura do gás e da água | Não exibidas pela LiveBar atual | — | Sem fonte, unidade e faixa comprovadas para o painel atual | Não completar com 0 °C |
| Queda de rotação anti-apagão | Campo s16 bruto observado no original para quadro 48 xx, offset 0x1E | unidade crua / possivelmente rpm/ciclo | OBS-COD de origem; escala/semântica firmware não comprovada | Não tratar como RPM do motor ou coeficiente de K |
| Rotação máxima anti-apagão | ANTI_STALLO, SC 0x0133, índice 0 | rpm | Nome e posição OBS-COD; dados de campo do proprietário, mas efeito exato por situação exige confirmação | Não garante ausência de apagão |
| Extras anti-apagão 1–4 | ANTI_STALLO índices 1–4 | rpm/ciclo; ms; rpm/ciclo; rpm/ciclo | Rótulos e armazenamento do original conhecidos; efeito físico por parâmetro DESCONHECIDO. Usuário relatou funções não suportadas sem nomeá-las individualmente. | Não prometer desempenho, não sugerir alterar valores sem evidência |

## Contrato por informação

- **OBS-COD:** presença de campo, nomes de propriedades, serializações e caminhos no código; não é resultado físico novo.
- **DERIVADO:** cortes de nível de sessões e comparação de eventos só valem no contexto observado; manutenção da origem e versão é obrigatória.
- **INFERIDO:** interpretação da variação de giros/efeito dos quatro extras; sempre indicar incerteza.
- **DESCONHECIDO:** sensor ausente, escala não medida, pressão total/diferencial fora da leitura, aceitação real de writer. Não publicar número fictício ou oferecer comando novo.
- **OBS-FIO histórico:** quadros citados do original não equivalem a prova de uso desta versão instalada hoje.
- **Relato do dono:** escritos/readback anteriores funcionaram; há funções do anti-apagão não suportadas no seu perfil, mas o subconjunto ainda não foi enumerado. Não excluir, trocar ou reativar individualmente sem essa prova.

## Gates antes de declarar DONE funcional

1. Associar por campo sensor → pedido real → resposta → decodificação → quadro válido/stale → UI; separar as grandezas de pressão.
2. Para cada função do anti-apagão, identificar suporte do perfil, resposta de leitura, efeito físico e reversão antes de rotular funcional.
3. Deixar unidades/RAW provisórios sinalizados, sem inferir que uma função de outra geração está disponível na MP48.
