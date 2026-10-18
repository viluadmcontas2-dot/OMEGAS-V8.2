# Dicionário interno de termos proibidos na tela (para o teste, não para o usuário)

Direção do dono (2026-10-18): **o app se autoexplica**, como a tela AutoCal (rótulo de ação com verbo + estado escrito + no máximo uma linha de apoio). Este arquivo **não** é guia para o usuário. Ele alimenta `tests/ui/linguagem-leiga.test.cjs`: cada linha da tabela abaixo é uma expressão regular (sem diferenciar maiúsculas) que **não pode aparecer em nenhum texto visível** das telas cobertas. Detalhes técnicos só existem dentro de "Ver detalhes técnicos" (recolhido, fim do cartão); o teste ignora esse bloco.

Palavra única escolhida para o aparelho: **central** (nunca "ECU", nunca "módulo").

| Padrão proibido (regex) | Vinha de | Palavra do app |
|---|---|---|
| `\brpm\b` | unidade de rotação | rotação |
| `\bciclos?\b` | "rpm por ciclo" | (sem unidade: "rotação") |
| `\bflags?\b` | FLAG_CONF | (nome humano do assunto) |
| `FLAG_CONF` | nome do vetor de ajustes | — |
| `\bbits?\b` | máscara de um bit | Ligado / Desligado |
| `\bpalavra\b` | "palavra 0 / 1" | — |
| `0x[0-9a-f]+` | hexadecimal | — |
| `\bECU\b` | sigla do aparelho | central |
| `\bmódulo\b` | segunda palavra possível | central |
| `serialcode\|código \d+` | endereço do parâmetro | — |
| `ANTI_STALLO\|GIRI_\|PARAM_\|NUMERO_INJ` | nomes internos | nome humano do cartão |
| `\bu16\b\|\bsetvector\b\|\bpayload\b\|\bhash\b\|readback` | protocolo | — |
| `\back\b\|\bnak\b\|status 0x` | resposta crua | "A central não aceitou" |
| `histerese\|\bdelta\b` | rótulos do programa original | "Folga", "Queda" |
| `^Ajustes? [123]$` | abas numeradas | grupos por assunto |
| `^Números$\|^Detalhes$\|^Avançado$` | rótulo genérico | título do assunto |

## Como ler os termos do app (para quem mantém, não para a tela)

- **Ligado / Desligado**: sempre escritos ao lado do interruptor; nunca só cor.
- **Campo com lápis**: número que o dono pode digitar; mostra unidade, "De X a Y" e "Valor de fábrica: Z".
- **Botão com verbo**: diz o que faz ("Gravar valores", "Restaurar fábrica", "Ligar anti-apagão"); a confirmação repete o verbo e tem "Agora não".
- **Cartão só de leitura**: mostra o estado e "Ver ›"; o app não altera nada ali.

## Mapa termo técnico → termo do app (fonte: legendas por/eng do `dfm/i18n_all.json`, reescritas)

| Original (eng) | Posição | No app |
|---|---|---|
| Enable un-stalling strategy | caixa (bit 0x20 da palavra 0 de FLAG_CONF3) | Anti-apagão (interruptor) |
| Max RPM to enable un-stalling | ANTI_STALLO[0] | Rotação máxima para agir |
| Delta RPM to enable un-stalling | ANTI_STALLO[1] | Queda de rotação para agir |
| Delay to exit gas mode | ANTI_STALLO[2] | Tempo antes de voltar ao gás (ms) |
| Hysteresys for enabling threshold | ANTI_STALLO[3] | Folga para não ficar trocando |
| Delta RPM to disenable un-stalling | ANTI_STALLO[4] | Queda para parar de agir |
| Sequential fuel changeover | FLAG_CONF1 palavra 1, 0x1000 | Um cilindro por vez (Troca de gasolina para gás) |
| Operation at idle | GIRI_PER_BENZINA + FLAG_CONF1 palavra 1, 0x0400 | Marcha lenta |
| Exit cut-off / Num inj exit cutoff | FLAG_CONF3 0x0010, NUMERO_INJ_BENZINA_CUTOFF | Saída do corte (quando solta o acelerador) |
| Injectors control | FLAG_CONF1 palavra 1, 0x0010 | Injetores |
| Petrol enters at high rpm | FLAG_CONF1 palavra 1, 0x2000 | Volta para gasolina |
