# Canonical UI Map

O shell é proprietário da telemetria persistente e da navegação. As telas consomem os contratos da ponte documentados em docs/ARCHITECTURE.md. O auditor estático cobre os módulos JavaScript das telas, componentes e ponte; markup do shell, CSS, alvos táteis e popups são verificados pelo Chromium e WebView nos fluxos remotos. Os botões usam eventos delegados nos seus proprietários.

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
| --- | --- | --- | --- | --- |
| Table Selection | screens/map.js | contexto K lido e seleção local explícita | toque, arraste, teclado | diamante-elastic.test.cjs e elastic-evidence.cjs |
| Select/Listbox | controle nativo e seleção delegada em cada tela | estado local da tela | native; filtros checkbox no gráfico | elastic-evidence.cjs |
| Form | editor de Mapa K e Curva K | rascunho separado do readback | campo vazio invalida gravação | diamante-elastic.test.cjs |
| Scrollbar | estilos globais e surfaces limitadas do shell | tokens e styles-diamante.css | listas e menus com scroll próprio | elastic-evidence.cjs alturas 720/672/648 |
| Toast | status da operação em cada tela | retorno real da ponte | aviso local de reinício do GNV | diamante-elastic.test.cjs |
| CRUD | escritores nativos e journal | foto, permissão, readback e desfazer | escrita nativa de um toque existente; reinício local sem escrita ECU | tests/test_refino_learning_reset_contract.py e Android render |

O reinício do aprendizado GNV descarta somente evidência local de GNV, mantém gasolina e calibração da ECU, e informa que a evidência descartada não pode ser desfeita. O desfazer da curva continua usando a foto nativa anterior. Não criar confirmação de escrita adicional que contradiga AGENTS.md.
