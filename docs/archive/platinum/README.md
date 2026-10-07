# Ômegas Platina — product + parity charter

Branch canônica: `OmegasPlatina`

Origem congelada:
- branch de origem: `OmegasVerde`
- SHA de origem: `f02e24b5aca5e2731a5026e04dcf100b25f6e07f`
- criada em 2026-09-28

## Objetivo real

Lapidar o Ômegas Verde até que o AutoCal relevante para uso real seja:

1. fiel ao protocolo e ao estado observável da ECU/ProgBase;
2. mais claro, rápido, recuperável e didático que o ProgBase;
3. centrado na ECU como autoridade do AutoMatch nativo;
4. sem reimplementar rotinas internas do ProgBase que não mudam comportamento;
5. sem escrita automática de curva/K; toda mutação continua exigindo intenção explícita do operador.

## Fonte de verdade

Ordem de autoridade para paridade:

1. `OMEGAS/DUMP` no Drive, desde que derivado hash-bound do ProgBase canônico;
2. Portmon autorizado / frames observados;
3. comportamento e readback da ECU;
4. documentação forense remota em `viluadmcontas2-dot/progbase-forensics`;
5. código atual do Ômegas como sistema a corrigir, nunca como prova do comportamento original.

Material web só pode gerar hipótese; não substitui evidência do sistema original.

## Critério de fidelidade

A Platina busca:
- fidelidade de protocolo;
- fidelidade comportamental;
- fidelidade de resultado.

Não busca copiar a UX Delphi nem reproduzir implementação interna quando a própria ECU já executa a lógica nativa.

## Experiência-alvo

Multimídia 1280×720, sem rolagem horizontal, estados legíveis em movimento e poucas decisões por tela.

AutoCal deve expor:
- posição AGORA;
- gasolina/GNV claramente separados;
- 18 bandas e quatro zonas em tempo real;
- contadores e maturidade sem inventar semântica;
- curvas de referência e K;
- progresso AutoMatch;
- histórico antes/depois;
- ações de readquisição pontual ou múltipla;
- erros com causa, estado, próxima ação e recuperação;
- sessão/USB/readback claramente visíveis;
- nenhuma confirmação redundante.

## Workunit P-001 — aquisição e readquisição nativa

Primeiro recorte funcional:
- preservar os dois masks nativos de 18 pontos;
- permitir um ou vários pontos selecionados;
- combinar gasolina e GNV no mesmo lote;
- enviar exatamente dois masks completos e um único commit nativo;
- manter sessão/interlocks/ACK/readback/recibo;
- selecionar visualmente múltiplas bolinhas no cockpit;
- falha não apaga seleção;
- sucesso limpa seleção e dispara atualização.

Evidência de protocolo:
- GAS_POINT_2DELETE = 0x016E U8[18]
- PETROL_POINT_2DELETE = 0x016D U8[18]
- 0 = readquirir/apagar ponto, 1 = preservar
- commit final = `01 24 05 2A`

## Stop conditions

Parar e replanejar se:
- o DUMP/Portmon contradisser endereço, largura, máscara ou commit;
- a branch de origem mudar e uma migração exigir merge não auditado;
- uma mudança exigir inventar semântica ausente;
- CI provar regressão fora do AutoCal;
- qualquer ação puder disparar escrita sem intenção explícita do usuário.

## Fora de escopo inicial

- gerar APK;
- recriar AutoMatch da ECU como escritor automático;
- copiar UI do ProgBase;
- alterar calibração automaticamente por Predictor;
- perseguir funções do ProgBase sem gap concreto no produto.
