# OMEGAS 8.2 — Matriz funcional e evidência (INICIAL)

> Estado **PARCIAL**, não homologação. Fonte estruturada: `docs/qualidade/matriz-funcional.json`. A matriz contém **12 fluxos iniciais** e **15 linhas APP_ONLY de rastreabilidade dinâmica** (27 linhas no total, ainda incompleta) e precisa crescer até cobrir **cada ação realmente exposta** no app. Atualizada em 2026-11-17 no PR #195. Não usar a quantidade de linhas como percentual de produto completo.

## Como ler

Cada linha liga uma tarefa do usuário a um dos cinco destinos, à sua classe (`ECU_READ`, `ECU_MANUAL_WRITE`, `APP_ONLY`), à origem documentada (`EV-...`), ao código atual, ao teste e às lacunas. Nas ações da central, `capability=NOT_REVALIDATED_ON_PHYSICAL_ECU_WITH_THIS_BUILD` significa que não existe **homologação física desta compilação**; os vetores observados em capturas do original **não** equivalem a uma autorização para novos comandos. Recursos `APP_ONLY` usam `NOT_APPLICABLE_ECU` por não corresponderem a comandos da central. A barra ao vivo usa a superfície `global`, que não cria uma sexta aba.

O catálogo atual contém **98 evidências**, das quais **59 possuem ligações `usado_por`**. Itens sem ligação podem ser apenas pesquisa ou backlog, não necessariamente falha do produto. A cobertura de funções visíveis é **não calculada** até fechar o inventário de todas as interfaces e pontes.

## Primeira triagem por tarefa

| Fluxo | Classe | Ponto de atenção |
| --- | --- | --- |
| `tools.anti-stall-switch` | `ECU_MANUAL_WRITE` | Comprovar no carro o resultado após reinício e recuperação USB; não inferir efeito do motor pelo ACK. |
| `tools.anti-stall-values` | `ECU_MANUAL_WRITE` | Efeito físico dos quatro campos extras e escala de queda ainda não comprovados; conservar incerteza visível. |
| `tools.petrol-return-time` | `ECU_MANUAL_WRITE` | Verificar na central física limites e estado confirmado, sem testar alteração enquanto o carro estiver em movimento. |
| `tools.petrol-return-pressure` | `ECU_MANUAL_WRITE` | Escrita manual de pressão já habilitada no app, mas condicionada à consistência da tabela lida com a fórmula do original; ACK dos 30 quadros e efeito físico na central NÃO COMPROVADOS (EV-VOLTAGASOLINA-015). Não ampliar nem acionar sem homologação autorizada. |
| `tools.idle-mode` | `ECU_READ` | Papel de campos e a variante de máscara por perfil são inferidos; não adicionar writer pela simples presença no original. |
| `autocal.live-learning` | `ECU_READ` | Parte dos parâmetros, escala por perfil e resultados físicos permanecem sem prova. |
| `refino.proposals` | `APP_ONLY` | É análise e proposta do OMEGAS, não comando de refino autônomo da ECU; revisar comunicação ao usuário. |
| `map.read` | `ECU_READ` | Distinguir o mapa lido nesta sessão da cópia local; não deduzir eixos desconhecidos. |
| `map.manual-write` | `ECU_MANUAL_WRITE` | Contrato histórico 'Gravado após ACK' conflita com plano 'Gravado após readback'; bloqueado para nova decisão, sem mudar writer. |
| `curve.manual-write` | `ECU_MANUAL_WRITE` | EV-BINS-008 é DESCONHECIDO: aceitar vetor 0x37 na ECU física não está provado; proibir ampliação de capacidades. |
| `tools.sessions` | `APP_ONLY` | Arquivo local do aplicativo; não apresentar como dado persistido na ECU ou sincronizado entre aparelhos. |
| `global.live-bar` | `ECU_READ` | Provas citadas cobrem transporte, não calibração de cada grandeza; dados RAW/ausentes ficam explícitos. |

## Bloqueios de segurança

- **Mapa K, confirmação:** a decisão registrada em 2026-10-19 permite texto “Gravado” após todos os ACKs e saída segura do modo K insertion, com conferência/correção posteriormente. O novo Plano Mestre tem regra distinta: “Gravado somente após readback”. O código atual foi mantido. **Não mudar comando, rótulo ou correção automática até haver nova decisão explícita do dono**; relatórios técnicos separam ACK de readback.
- **Anti-apagão, parâmetros extras:** os cinco campos existem no original e foram autorizados anteriormente, mas o efeito dos quatro extras e a escala de queda não estão plenamente demonstrados; não prometer efeito físico certo na tela.
- **Pressão de retorno à gasolina:** a escrita manual JÁ está habilitada por `VoltaGasolinaCore.PRESSURE_WRITE_ENABLED=true`, e `VoltaGasolinaWriter` verifica a tabela com `consistentWithOriginal` antes de gravar. A matriz anterior dizia, incorretamente, que a função era só leitura. `EV-VOLTAGASOLINA-015` mantém DESCONHECIDO o ACK físico dos 30 quadros e o efeito sobre o veículo; não mudar nem acionar o writer sem confirmação autorizada.
- **Curva K:** aceite físico de gravação do vetor estendido não demonstrado; não inventar outro comando nem abrir configuração de escrita irrestrita.
- **Capacidade real da central:** exige evidência por perfil, leitura, escrita autorizada e verificação; uma captura do programa legado não prova suporte universal.
- **UX:** verificação de tela 9:16/16:9 não substitui WebView Android, toque/teclado real e confirmação do que aconteceu.

## Inventário dinâmico parcial — fontes e regras

- `components/advanced.js`: **17 tags de botão em templates**, **14 seletores distintos**; ações de escrita já existentes ou navegação local, sem habilitar capabilities novas.
- `components/drawers.js`: **25 tags de botão em templates**, **19 seletores distintos**; sessões, exportações, permissões Android e navegação. Teclado de Sessões: handler global identificado em `app.js`, retorno físico do teclado Android ainda pendente.
- `dynamicButtonInventory` em `matriz-funcional.json` referencia seletor, classe da interação, marcador do handler e linha(s) da matriz. `OBS-COD` **não é** prova de funcionamento em dispositivo nem de suporte da central.
- **Não somar 33 seletores aos 116 métodos e chamar isso de 100%**: elementos condicionais, controles não-button, demais módulos JS, bridges secundárias, efeitos reais e testes por ação ainda são lacunas. Os 15 itens APP_ONLY novos usam o teste de inventário como **checagem estática**, não teste de execução de cada clique.
- Os controles `data-volta-*` agregam pressão/tempo no JS; ficam ligados às duas linhas existentes da matriz, sem deduzir endereço, ACK ou aceite real na ECU.

## Varredura estrutural ampliada — ainda PARCIAL

- **35 arquivos JavaScript** em `app.js`, `components/`, `core/` e `screens/` têm conferência automática de **82 sítios de criação de botões**. São sítios de código, não 82 comandos nem 82 ações independentes.
- `dynamicButtonInventory.sources` mapeia **33 seletores** dos dois componentes de Ferramentas para classes de interação e linhas da matriz; `jsSurfaceScan` detecta inclusão/remoção de fontes ou sítios sem atualização do catálogo. O teclado de Sessões utiliza listener global em `app.js`.
- Foram verificados **116 métodos realmente anotados** com `@JavascriptInterface` nas quatro pontes `Hub`, `V7`, `Power` e `AutoCal`. Um método Kotlin não anotado, `readKFactorCurve`, era contado falsamente pelo grafo devido a um comentário; o parser agora ignora essa ocorrência (CI #1981).
- **BLOQUEIO DE SEGURANÇA P0 PARA APK, OBS-COD:** a aba AutoCal ainda expõe `data-autocal-action="RESET_ALL"` no menu Ações avançadas, e a bridge permite preparar essa ação; não abrir mão da proibição de reset geral. A tela técnica está protegida, portanto não alterar unilateralmente botão, semântica ou writer: requer decisão específica e teste para retirar a exposição de release.
- **Cobertura de ações: DESCONHECIDA.** Faltam testar cada clique efetivo, vínculos exatos com API/bridge/manager, efeitos e recuperação, permissões de perfil e validação física. Não confundir 27 entradas, 82 sítios e 116 métodos com percentuais.
- Evidências de orientação por tela em `docs/qualidade/INVENTARIO-TELAS-9x16-16x9.md`; limpeza por escopo em `docs/qualidade/LIMPEZA-ESCOPO-OMEGAS82.md`.

## Próxima expansão (Tarefa 3 do Programa Mestre)

1. Enumerar as ações do HTML e os métodos expostos das pontes Kotlin; agrupar por tarefa real, sem transformar métodos internos em funcionalidades.
2. Para cada ação da central, adicionar origem DFM/rotina/captura, endereço/código/unidade/escala, pedido/resposta, sessão/geração, estado de readback, efeito e tratamento de falhas.
3. Tratar capacidades desconhecidas como desconhecidas; nunca promover método sem prova a botão de escrita. Preservar as marcas `OBS-FIO`, `OBS-COD`, `DERIVADO`, `INFERIDO` e `DESCONHECIDO` do catálogo.
4. Acrescentar testes que falhem quando rota, arquivo, evidência ou classificação se perder. Só marcar 100% depois de inventário fechado, CI no SHA e homologação física autorizada.

O teste estático da matriz vive em `tests/ui/adaptive-premium-layout.test.cjs`, evitando uma suíte paralela ou nó novo no grafo de PRODUÇÃO.
