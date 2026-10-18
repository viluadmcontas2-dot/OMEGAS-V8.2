# Mapa de telas (aba → cartões → controle)

Alvo: multimídia de carro em paisagem (1280x720 canônico, 1024x600, 800x480). Alvos de toque ≥ 56 px, nada rola. Cada cartão: título, estado escrito, no máximo uma linha de apoio e UM controle.

Barra do topo (todas as abas): Tensão · MAP · Inj. gasolina · Rotação. Navegação inferior: AutoCal · Refino · Ajuste local · Ajuste global · **Sessões** · Ferramentas. Estado da central: "Central conectada" / "Sem central".

## Ferramentas (sem abas; duas páginas)
Página 1, "Motor e combustível" (`components/advanced.js`, `core/cartoes-motor.js`)
- Anti-apagão → **interruptor** Ligado/Desligado → janela "Ligar anti-apagão? Desligado → Ligado" [Ligar anti-apagão] [Agora não] → "Pronto" [Fechar] [Desfazer].
- Valores do anti-apagão → estado De fábrica/Mudados → **5 campos com lápis** (Rotação máxima para agir · Queda de rotação para agir · Tempo antes de voltar ao gás (ms) · Folga para não ficar trocando · Queda para parar de agir), cada um com unidade, "De X a Y" e "Valor de fábrica: Z" → [Gravar valores] [Restaurar fábrica] [Desfazer] → mesma janela de confirmação.
- Saída do corte (quando solta o acelerador) · Marcha lenta (Só gás / Segurar com gasolina na parada / Sempre gasolina, só leitura + sugestão) · Troca de gasolina para gás · Injetores · Volta para gasolina → estado escrito → "Ver ›" abre a lista de leitura. "Ver detalhes técnicos" recolhido no fim (único lugar de bit/código).
Página 2, "App e arquivos" (`components/drawers.js`)
- Cópia de segurança [Salvar cópia] · Aprendizado [Salvar aprendizado] · Aprendizado de outro arquivo [Carregar aprendizado] · Refino automático (interruptor) · Teste de gravação [Testar] (2 toques) · Bateria [Permitir] · Balão sobre outros apps (interruptor / [Permitir balão]) · Registro do app → lista, [Salvar registro], [Testar o app].

## Sessões (aba própria, `components/drawers.js`)
- Gravação → Gravando/Parada → [Começar a gravar] / [Parar de gravar].
- Gravações salvas (N) → 3 por página, [Salvar arquivo] em cada.
- Tamanho das gravações → [Mudar ›] abre a configuração (salvar a cada, tamanho máximo, quantas guardar, gravar ao conectar, gravar tudo do cabo) → [Aplicar].
