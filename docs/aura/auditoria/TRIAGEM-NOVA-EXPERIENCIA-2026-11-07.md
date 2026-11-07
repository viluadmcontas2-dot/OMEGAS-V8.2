# Triagem da nova-experiencia — 2026-11-07

Base remota conferida: Aura 2903d0d953c3f6c98e5b1f540dd6754ceaa1d60f; canônica 28afdaa74df1cb81e84d9f4ee3261d92050ad976; alternativa nova-experiencia 2903d0d953c3f6c98e5b1f540dd6754ceaa1d60f (SHA indicado pelo compare como ponta; verificar novamente antes de aplicar). Compare Aura...nova-experiencia: divergiu, +5 commits da alternativa, -75 em relação à Aura. Canvas claude/cool-curie-hs5nk9 é ancestral da Aura (+0/-70). Inventário das 14 branches: INVENTARIO-BRANCHES-2026-11-07.md.

## Candidatos e decisão

- `app/src/main/assets/ui/core/ajuda-textos.js`: **aproveitável após revisão**, somente conteúdo didático para as cinco áreas existentes. Não copiar diretamente. O texto introduz telas independentes “Regular” e “Carro” e renomeia AutoCal/Refino/Mapa/Curva para Aprendizado/Automático/Região/Geral; contraria navegação canônica. Revisar cada frase contra a implementação real e o contrato de leitura de volta.
- `components/ajuda.js` e `styles-ajuda.css`: **candidatos condicionais** a inspeção do DOM, acessibilidade, responsividade e testes; sem ativar rotas extras.
- `core/central-estado.js`: **candidato condicional**; provar que não inventa estado de central nem interfere no fluxo de aquisição.
- `core/router.js`, `screens/regular.js`, `screens/carro.js`, `styles-regular.css`, `styles-carro.css`: **não integrar diretamente**; quatro áreas/rotas alternativas conflitam com cinco áreas canônicas.
- `tests/ui/nova-ajuda.test.cjs`: **adaptar** para testes das cinco áreas, não importar asserts de quatro abas como critério.

## Riscos semânticos observados diretamente no arquivo ajuda-textos.js

1. “Mudar um valor pede confirmação” em Ferramentas contraria regra de um toque sem janela.
2. “Nada é gravado enquanto você dirige” é ambíguo: a central pode aprender automaticamente, embora o app não deva gravar correções sem toque.
3. “você pode desfazer” e “o app lê de volta” são garantias genéricas não demonstradas em todos os cartões e caminhos de erro; não publicar sem prova por fluxo.
4. O texto atribui desligamento do GNV acima de 14,1 V a uma condição como se fosse regra universal; o AGENTS.md descreve relato de campo e escala provisória, não limiar universal comprovado.

## Gate para implementação

Criar RED focado para copy, navegação em cinco áreas e ausência de promessas não provadas; integrar apenas ajuda como componente opt-in sem alterar protocolos, gravadores, roteador canônico ou telas existentes sem autorização; executar GREEN e CI no mesmo SHA; capturar WebView 1280×720, 1024×600, 800×480. Sem APK, merge ou escrita na central antes dos gates. Esta triagem é revisão de fonte, **não teste executado**.
