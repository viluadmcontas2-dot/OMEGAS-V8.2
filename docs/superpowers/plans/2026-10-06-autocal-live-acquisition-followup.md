# Continuação AutoCal vivo + aquisição inteligente — 2026-10-06

Base remota: `OmegasDiamante@79afa70549e005ec7208ddca59fe62a04636d7a0`
Branch: `work/platina-live-acquisition`

## Objetivo real

Manter AutoCal/Refino estáveis e compreensíveis em 1280×720 enquanto a ECU muda estado, sem esconder dados úteis, sem reapresentar aquisição velha como atual e sem introduzir nenhuma escrita automática na ECU.

## Invariantes

- ECU e readback continuam autoridade. Intenção pendente pode aparecer imediatamente; sucesso só após confirmação nativa.
- Protocolos e escritores nativos não mudam nesta rodada.
- AutoMatch continua sendo executado pela ECU.
- Ruído pode ser rejeitado como evidência local; isso nunca implica apagar ponto/escrever na ECU.
- A arquitetura atual prevalece sobre textos antigos: não reintroduzir portão temporal de visitas.
- Compilação entregue `9fa7be38` permanece identificável e intocada.

## Evidência inicial

1. `BackgroundMemo.get()` pode executar `compute()` na chamada JavaScript quando cache está vazio, inválido ou vencido. A bridge é síncrona para a WebView, portanto esse caminho ainda pode congelar a UI.
2. `NativeAutoCalMonitor.autoMatchProgressJson()` memoriza aquisição por identidade do snapshot + booleano `blocked`. Duas épocas diferentes podem ter `blocked=true` e exigir máscaras diferentes, mas reutilizar o memo anterior.
3. Durante RESET_PETROL/RESET_GAS pendente, a frase já mostra “conferindo”, porém a geometria ainda depende da época confirmada. A curva alvo antiga pode continuar parecendo aquisição atual até o readback.
4. A versão Platina conhecida (`e895751f`) e a atual preservam o princípio: AutoMatch nativo, sem AutoMatch manual. Não copiar regressivamente UI/algoritmos antigos.

## Execução e stop conditions

### A. AutoCal / ponte
1. Escrever regressões para: memo de aquisição sensível à máscara completa da época; acesso de projeção não bloquear a WebView; RESET pendente mascarar visualmente apenas o combustível alvo.
2. Confirmar RED em CI antes da correção.
3. Corrigir minimamente. Se qualquer mudança exigir alterar bytes/protocolo da ECU, parar essa linha e manter apenas diagnóstico.
4. Rodar JVM + contratos + lint no SHA da branch; depois matriz HTML real e WebView Android 1280×720.

### B. Aquisição / ruído
1. Medir no código/corpus onde marcha lenta e pontos isolados entram no ledger/Refino.
2. Criar classificação de qualidade local por contexto, repetição, regime, vizinhança e confiança.
3. Rejeitar somente evidência local do cálculo; nunca chamar exclusão/escrita na ECU.
4. Expor motivo/contagem na UI. Se o corpus não demonstrar ganho, não promover o filtro.

### C. Backups
Provar separadamente salvar, listar, restaurar e cenário de instalação nova. Não confundir “arquivo público em Downloads” com “importável após reinstalação”.

### D. Fechamento
Matriz: gasolina/GNV × pausado/ativo × zonas desconhecidas/parciais/completas × reinício × AutoMatch × falha × latência × reconexão. Cliques reais, alcance, sobreposição, persistência do gráfico, CI, render WebView e build. Hardware físico continua não provado.
