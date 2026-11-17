# OMEGAS 8.2 — Matriz funcional e evidência (INICIAL)

> Estado **PARCIAL**, não homologação. Fonte estruturada: `docs/qualidade/matriz-funcional.json`. A matriz cobre **12 fluxos iniciais** e precisa crescer até cobrir **cada ação realmente exposta** no app. Atualizada em 2026-11-17 no PR #195. Não usar a quantidade de linhas como percentual de produto completo.

## Como ler

Cada linha liga uma tarefa do usuário a um dos cinco destinos, à sua classe (`ECU_READ`, `ECU_MANUAL_WRITE`, `APP_ONLY`), à origem documentada (`EV-...`), ao código atual, ao teste e às lacunas. Nas ações da central, `capability=NOT_REVALIDATED_ON_PHYSICAL_ECU_WITH_THIS_BUILD` significa que não existe **homologação física desta compilação**; os vetores observados em capturas do original **não** equivalem a uma autorização para novos comandos. Recursos `APP_ONLY` usam `NOT_APPLICABLE_ECU` por não corresponderem a comandos da central. A barra ao vivo usa a superfície `global`, que não cria uma sexta aba.

O catálogo atual contém **98 evidências**, das quais **59 possuem ligações `usado_por`**. Itens sem ligação podem ser apenas pesquisa ou backlog, não necessariamente falha do produto. A cobertura de funções visíveis é **não calculada** até fechar o inventário de todas as interfaces e pontes.

## Primeira triagem por tarefa

| Fluxo | Classe | Ponto de atenção |
| --- | --- | --- |
| `tools.anti-stall-switch` | `ECU_MANUAL_WRITE` | /C/o/m/p/r/o/v/a/r/ /n/o/ /c/a/r/r/o/ /o/ /r/e/s/u/l/t/a/d/o/ /a/p/ó/s/ /r/e/i/n/í/c/i/o/ /e/ /r/e/c/u/p/e/r/a/ç/ã/o/ /U/S/B/;/ /n/ã/o/ /i/n/f/e/r/i/r/ /e/f/e/i/t/o/ /d/o/ /m/o/t/o/r/ /p/e/l/o/ /A/C/K/./ |
| `tools.anti-stall-values` | `ECU_MANUAL_WRITE` | /E/f/e/i/t/o/ /f/í/s/i/c/o/ /d/o/s/ /q/u/a/t/r/o/ /c/a/m/p/o/s/ /e/x/t/r/a/s/ /e/ /e/s/c/a/l/a/ /d/e/ /q/u/e/d/a/ /a/i/n/d/a/ /n/ã/o/ /c/o/m/p/r/o/v/a/d/o/s/;/ /c/o/n/s/e/r/v/a/r/ /i/n/c/e/r/t/e/z/a/ /v/i/s/í/v/e/l/./ |
| `tools.petrol-return-time` | `ECU_MANUAL_WRITE` | /V/e/r/i/f/i/c/a/r/ /n/a/ /c/e/n/t/r/a/l/ /f/í/s/i/c/a/ /l/i/m/i/t/e/s/ /e/ /e/s/t/a/d/o/ /c/o/n/f/i/r/m/a/d/o/,/ /s/e/m/ /t/e/s/t/a/r/ /a/l/t/e/r/a/ç/ã/o/ /e/n/q/u/a/n/t/o/ /o/ /c/a/r/r/o/ /e/s/t/i/v/e/r/ /e/m/ /m/o/v/i/m/e/n/t/o/./ |
| `tools.petrol-return-pressure` | `ECU_READ` | /G/r/a/v/a/ç/ã/o/ /d/e/ /p/r/e/s/s/ã/o/ /d/e/s/a/b/i/l/i/t/a/d/a/ /n/o/ /a/p/p/;/ /3/0/ /r/e/s/p/o/s/t/a/s/ /r/e/a/i/s/ /d/e/ /e/s/c/r/i/t/a/ /e/ /e/f/e/i/t/o/ /n/o/ /c/a/r/r/o/ /n/ã/o/ /f/o/r/a/m/ /p/r/o/v/a/d/o/s/./ |
| `tools.idle-mode` | `ECU_READ` | /P/a/p/e/l/ /d/e/ /c/a/m/p/o/s/ /e/ /a/ /v/a/r/i/a/n/t/e/ /d/e/ /m/á/s/c/a/r/a/ /p/o/r/ /p/e/r/f/i/l/ /s/ã/o/ /i/n/f/e/r/i/d/o/s/;/ /n/ã/o/ /a/d/i/c/i/o/n/a/r/ /w/r/i/t/e/r/ /p/e/l/a/ /s/i/m/p/l/e/s/ /p/r/e/s/e/n/ç/a/ /n/o/ /o/r/i/g/i/n/a/l/./ |
| `autocal.live-learning` | `ECU_READ` | /P/a/r/t/e/ /d/o/s/ /p/a/r/â/m/e/t/r/o/s/,/ /e/s/c/a/l/a/ /p/o/r/ /p/e/r/f/i/l/ /e/ /r/e/s/u/l/t/a/d/o/s/ /f/í/s/i/c/o/s/ /p/e/r/m/a/n/e/c/e/m/ /s/e/m/ /p/r/o/v/a/./ |
| `refino.proposals` | `APP_ONLY` | /É/ /a/n/á/l/i/s/e/ /e/ /p/r/o/p/o/s/t/a/ /d/o/ /O/M/E/G/A/S/,/ /n/ã/o/ /c/o/m/a/n/d/o/ /d/e/ /r/e/f/i/n/o/ /a/u/t/ô/n/o/m/o/ /d/a/ /E/C/U/;/ /r/e/v/i/s/a/r/ /c/o/m/u/n/i/c/a/ç/ã/o/ /a/o/ /u/s/u/á/r/i/o/./ |
| `map.read` | `ECU_READ` | /D/i/s/t/i/n/g/u/i/r/ /o/ /m/a/p/a/ /l/i/d/o/ /n/e/s/t/a/ /s/e/s/s/ã/o/ /d/a/ /c/ó/p/i/a/ /l/o/c/a/l/;/ /n/ã/o/ /d/e/d/u/z/i/r/ /e/i/x/o/s/ /d/e/s/c/o/n/h/e/c/i/d/o/s/./ |
| `map.manual-write` | `ECU_MANUAL_WRITE` | /C/o/n/t/r/a/t/o/ /h/i/s/t/ó/r/i/c/o/ /'/G/r/a/v/a/d/o/ /a/p/ó/s/ /A/C/K/'/ /c/o/n/f/l/i/t/a/ /c/o/m/ /p/l/a/n/o/ /'/G/r/a/v/a/d/o/ /a/p/ó/s/ /r/e/a/d/b/a/c/k/'/;/ /b/l/o/q/u/e/a/d/o/ /p/a/r/a/ /n/o/v/a/ /d/e/c/i/s/ã/o/,/ /s/e/m/ /m/u/d/a/r/ /w/r/i/t/e/r/./ |
| `curve.manual-write` | `ECU_MANUAL_WRITE` | /E/V/-/B/I/N/S/-/0/0/8/ /é/ /D/E/S/C/O/N/H/E/C/I/D/O/:/ /a/c/e/i/t/a/r/ /v/e/t/o/r/ /0/x/3/7/ /n/a/ /E/C/U/ /f/í/s/i/c/a/ /n/ã/o/ /e/s/t/á/ /p/r/o/v/a/d/o/;/ /p/r/o/i/b/i/r/ /a/m/p/l/i/a/ç/ã/o/ /d/e/ /c/a/p/a/c/i/d/a/d/e/s/./ |
| `tools.sessions` | `APP_ONLY` | /A/r/q/u/i/v/o/ /l/o/c/a/l/ /d/o/ /a/p/l/i/c/a/t/i/v/o/;/ /n/ã/o/ /a/p/r/e/s/e/n/t/a/r/ /c/o/m/o/ /d/a/d/o/ /p/e/r/s/i/s/t/i/d/o/ /n/a/ /E/C/U/ /o/u/ /s/i/n/c/r/o/n/i/z/a/d/o/ /e/n/t/r/e/ /a/p/a/r/e/l/h/o/s/./ |
| `global.live-bar` | `ECU_READ` | /P/r/o/v/a/s/ /c/i/t/a/d/a/s/ /c/o/b/r/e/m/ /t/r/a/n/s/p/o/r/t/e/,/ /n/ã/o/ /c/a/l/i/b/r/a/ç/ã/o/ /d/e/ /c/a/d/a/ /g/r/a/n/d/e/z/a/;/ /d/a/d/o/s/ /R/A/W///a/u/s/e/n/t/e/s/ /f/i/c/a/m/ /e/x/p/l/í/c/i/t/o/s/./ |

## Bloqueios de segurança

- **Mapa K, confirmação:** a decisão registrada em 2026-10-19 permite texto “Gravado” após todos os ACKs e saída segura do modo K insertion, com conferência/correção posteriormente. O novo Plano Mestre tem regra distinta: “Gravado somente após readback”. O código atual foi mantido. **Não mudar comando, rótulo ou correção automática até haver nova decisão explícita do dono**; relatórios técnicos separam ACK de readback.
- **Anti-apagão, parâmetros extras:** os cinco campos existem no original e foram autorizados anteriormente, mas o efeito dos quatro extras e a escala de queda não estão plenamente demonstrados; não prometer efeito físico certo na tela.
- **Curva K:** aceite físico de gravação do vetor estendido não demonstrado; não inventar outro comando nem abrir configuração de escrita irrestrita.
- **Capacidade real da central:** exige evidência por perfil, leitura, escrita autorizada e verificação; uma captura do programa legado não prova suporte universal.
- **UX:** verificação de tela 9:16/16:9 não substitui WebView Android, toque/teclado real e confirmação do que aconteceu.

## Próxima expansão (Tarefa 3 do Programa Mestre)

1. Enumerar as ações do HTML e os métodos expostos das pontes Kotlin; agrupar por tarefa real, sem transformar métodos internos em funcionalidades.
2. Para cada ação da central, adicionar origem DFM/rotina/captura, endereço/código/unidade/escala, pedido/resposta, sessão/geração, estado de readback, efeito e tratamento de falhas.
3. Tratar capacidades desconhecidas como desconhecidas; nunca promover método sem prova a botão de escrita. Preservar as marcas `OBS-FIO`, `OBS-COD`, `DERIVADO`, `INFERIDO` e `DESCONHECIDO` do catálogo.
4. Acrescentar testes que falhem quando rota, arquivo, evidência ou classificação se perder. Só marcar 100% depois de inventário fechado, CI no SHA e homologação física autorizada.

O teste estático da matriz vive em `tests/ui/adaptive-premium-layout.test.cjs`, evitando uma suíte paralela ou nó novo no grafo de PRODUÇÃO.
