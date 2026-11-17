# OMEGAS 8.2 — Matriz funcional e evidência (INICIAL)

> Estado **PARCIAL**, não homologação. Fonte estruturada: `docs/qualidade/matriz-funcional.json`. A matriz cobre **12 fluxos iniciais** e precisa crescer até cobrir **cada ação realmente exposta** no app. Atualizada em 2026-11-17 no PR #195. Não usar a quantidade de linhas como percentual de produto completo.

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
| `global.live-bar` | `ECU_READ` | /P/r/o/v/a/s/ /c/i/t/a/d/a/s/ /c/o/b/r/e/m/ /t/r/a/n/s/p/o/r/t/e/,/ /n/ã/o/ /c/a/l/i/b/r/a/ç/ã/o/ /d/e/ /c/a/d/a/ /g/r/a/n/d/e/z/a/;/ /d/a/d/o/s/ /R/A/W//ausentes ficam explícitos. |

## Bloqueios de segurança

- **Mapa K, confirmação:** a decisão registrada em 2026-10-19 permite texto “Gravado” após todos os ACKs e saída segura do modo K insertion, com conferência/correção posteriormente. O novo Plano Mestre tem regra distinta: “Gravado somente após readback”. O código atual foi mantido. **Não mudar comando, rótulo ou correção automática até haver nova decisão explícita do dono**; relatórios técnicos separam ACK de readback.
- **Anti-apagão, parâmetros extras:** os cinco campos existem no original e foram autorizados anteriormente, mas o efeito dos quatro extras e a escala de queda não estão plenamente demonstrados; não prometer efeito físico certo na tela.
- **Pressão de retorno à gasolina:** a escrita manual JÁ está habilitada por `VoltaGasolinaCore.PRESSURE_WRITE_ENABLED=true`, e `VoltaGasolinaWriter` verifica a tabela com `consistentWithOriginal` antes de gravar. A matriz anterior dizia, incorretamente, que a função era só leitura. `EV-VOLTAGASOLINA-015` mantém DESCONHECIDO o ACK físico dos 30 quadros e o efeito sobre o veículo; não mudar nem acionar o writer sem confirmação autorizada.
- **Curva K:** aceite físico de gravação do vetor estendido não demonstrado; não inventar outro comando nem abrir configuração de escrita irrestrita.
- **Capacidade real da central:** exige evidência por perfil, leitura, escrita autorizada e verificação; uma captura do programa legado não prova suporte universal.
- **UX:** verificação de tela 9:16/16:9 não substitui WebView Android, toque/teclado real e confirmação do que aconteceu.

## Próxima expansão (Tarefa 3 do Programa Mestre)

1. Enumerar as ações do HTML e os métodos expostos das pontes Kotlin; agrupar por tarefa real, sem transformar métodos internos em funcionalidades.
2. Para cada ação da central, adicionar origem DFM/rotina/captura, endereço/código/unidade/escala, pedido/resposta, sessão/geração, estado de readback, efeito e tratamento de falhas.
3. Tratar capacidades desconhecidas como desconhecidas; nunca promover método sem prova a botão de escrita. Preservar as marcas `OBS-FIO`, `OBS-COD`, `DERIVADO`, `INFERIDO` e `DESCONHECIDO` do catálogo.
4. Acrescentar testes que falhem quando rota, arquivo, evidência ou classificação se perder. Só marcar 100% depois de inventário fechado, CI no SHA e homologação física autorizada.

O teste estático da matriz vive em `tests/ui/adaptive-premium-layout.test.cjs`, evitando uma suíte paralela ou nó novo no grafo de PRODUÇÃO.
