# OMEGASCINZA · Oficina visual interativa

Um **ambiente de desenvolvimento** para analisar e redesenhar o HTML REAL do OMEGAS com o dono, sem usar tldraw e sem alterar o APK. Não há WebView falsa nem redesenho das telas. O `index.html`, CSS, JavaScript, oito rotas e gráficos são servidos diretamente de `app/src/main/assets/ui/`, em viewport fixo **1280×720**.

## Abrir na MMMACHINE, ou em qualquer computador com o repo

```bash
python3 tools/ui_studio/server.py --port 8765
```

No navegador do **mesmo computador**: http://127.0.0.1:8765/tools/ui_studio/index.html

O servidor escuta **somente 127.0.0.1** e é somente de leitura. Não expõe uma instância pública; não possui acesso ao USB ou drivers. A ECU é substituída exclusivamente pelo mock de testes existente `tests/ui/render/mock-bridge.js`, abastecido por `fixtures/autocal/real/` via `tests/ui/render/prep.py`.

## Trabalho colaborativo

1. **Inspecionar (1):** toque na área do componente. Veja o seletor, largura, altura e posição exata na resolução real.
2. **Marcar (2):** circule, risque e destaque o que deve sair ou mudar.
3. **Reposicionar (3):** arraste o elemento na prévia. Não altera arquivo nem escrita.
4. **Interagir (4):** experimente todos os botões e estados com uma ECU **simulada**, incluindo AutoCal, Ajuste GNV, Curva K e Mapa K.
5. O campo de observação serve para registrar a razão da mudança e a regra desejada.
6. **Comparar original** revela o antes/depois instantaneamente, **Desfazer** é reversível.
7. **Exportar mudanças** gera arquivo `omegascinza-revisao-AAAA-MM-DD.json` com seletores, desenhos vetoriais, deslocamentos, cenário e comentários. Anexe esse arquivo à conversa para eu implementar a mudança **no app de verdade**, após revisão e testes.

A oficina **não aplica patches automaticamente** no app e não é distribuída no APK. Cada sugestão deve virar alteração pequena em `OMEGASCINZA` com prova de regressão visual e CI verde. A estrutura aprovada das 8 abas, rodapé único e regra 14–17 permanecem intactas.

## Prova de funcionamento (não é teste físico)

No ambiente onde Chromium e Playwright estiverem disponíveis:

```bash
export NODE_PATH=/caminho/do/playwright/node_modules
export OMEGAS_CHROMIUM=/usr/bin/chromium
node tools/ui_studio/studio-smoke.cjs
```

O teste E2E falha, **não ignora**, quando Chromium ou Playwright estão ausentes. Ele mede o carregamento das folhas de estilo originais, as oito rotas, o host verdadeiro do AutoCal, seleção, comentário, ocultação, desfazer, marcação, exportação e navegação real até Ajuste GNV.

Ao executar o `ci.yml` por `workflow_dispatch`, a mesma prova visual é executada na etapa Chromium do CI.

## Limitações declaradas

- Não mede comportamento físico da ECU, latência USB nem calibração correta.
- A prévia usa a telemetria de uma fixture de sessão real, enquanto Mapa K e certos estados do Refino são simulações documentadas no mock.
- Mudar de cenário recarrega o aplicativo, mantendo a lista de mudanças; alguns seletores dinâmicos podem não existir em todos os estados.
- O desenho é uma anotação, não um componente de produção. Uma marca não remove funcionalidade automaticamente.
- O JSON exportado não inclui dump original nem registros sensíveis do veículo.


## Inspector e AutoCal dinâmico

Os rascunhos são isolados por elemento, aba e cenário. Largura e altura são editáveis em pixels; a prévia mede o resultado real e permite Comparar original, Desfazer e exportar registros `resize`.

No cenário em aquisição, `simulator.js` simula o ciclo de escrita e readback dos botões reais de releitura. Limpa apenas o combustível solicitado e mantém aprendizado ligado. Os pontos reaparecem gradualmente (4 segundos por ponto no ritmo normal). Combustível e ritmo são controlados na oficina. A telemetria é suavizada; a aquisição e a confirmação são sintéticas e não reproduzem critérios físicos da ECU nem provam calibração. Os outros cenários preservam suas fixtures.
