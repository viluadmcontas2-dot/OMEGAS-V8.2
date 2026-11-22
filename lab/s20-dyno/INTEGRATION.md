# Integração OBD + MP48 — o Ômegas como produto completo

Referências canônicas: `AGENTS.md`, `docs/REGRAS-DO-DONO.md`, `docs/grafo/PRODUCAO.md`, `docs/handoff/PENDENTE.md`, `PROTOCOLO-LANDI/18-FORMULAS-ESCALA.md`. Esta página descreve uma hipótese de integração, **não uma alteração do aplicativo principal**.

## Autoridade dos dados e da comunicação
- **Uma só dona da serial MP48:** o serviço de telemetria do Ômegas principal continua controlando USB/protocolo. O laboratório é consumidor assíncrono das mensagens já recebidas; nunca cria segundo leitor competindo pelo USB.
- **OBD separado e somente-leitura:** adaptador Bluetooth ELM327, resposta por PID. Não é sniffer completo da rede CAN, nem permite diagnosticar mensagens que o adaptador não entrega.
- **Sempre reter o original:** payload de 34 bytes + comando, tempo monotônico de recepção, fonte, confiabilidade, perdas e versões do decodificador. Reinterpretações futuras não reescrevem o original.
- **Motor de evidências:** CONFIRMADO (corroborado), INFERIDO (hipótese), DESCONHECIDO. Não publicar descoberta técnica como certeza até replay de captura e comparação com fontes oficiais.
- **Escritas da central continuam proibidas por automação:** sugestões não alteram Mapa K, Curva K, AutoCal, anti-apagão ou limites. Ação de alteração é do proprietário, com ACK e leitura de volta.

## Onde os sinais ajudam o Ômegas inteiro

| Área real do Ômegas | Telemetria necessária | Questão investigável | Regra de segurança |
|---|---|---|---|
| AutoCal | combustível real, RPM, MAP, Tinj gasolina/GNV, água, zonas e contadores nativos | aquisição de zonas, maturidade, desvio gasolina/GNV, AutoMatch real da ECU | não reimplementar nem acionar AutoMatch/Reset sozinho |
| Refino | referência gasolina, injeção, MAP, RPM, STFT/LTFT, estado GNV, repetição de condições | onde refinar sem confundir regime, aquecimento e pressão baixa | só recomendar, nunca gravar |
| Ajuste local / Mapa K | células lidas da central, bins RPM×injeção, condições do motor, pressão | desvio localizado por célula, regiões de apagão, efeito de ajustes manuais | escrever somente por toque + readback |
| Ajuste global / Curva K | nós K lidos, resposta do motor sob condições comparáveis, histórico | identificar correções globais e distinguir de erros locais | limites canônicos e sem escrita automática |
| Ferramentas — anti-apagão | eventos RPM/velocidade/TPS, corte, estado combustível, pressão GNV, temperatura | diferenciar apagão, freio/embreagem, corte em desaceleração, retorno gasolina e falha de sensor | hipóteses com evidência; controle permanece manual |
| Ferramentas — marcha lenta | rotação real, estabilidade, combustível e condições do motor | estabilidade parado, quedas, efeito da gasolina/GNV | sem aplicar parâmetros |
| Ferramentas — comutação/pressão/nível | bytes de estado, pulsações GNV, temperatura, pressão absoluta/diferencial, LEVELS RAW | entrada do gás, espera de comutação, retorno por pressão baixa, nível e possíveis escalas erradas | não converter níveis sem calibração física |
| Sessões e Diagnóstico | telemetria completa, eventos e respostas não reconhecidas, clocks, qualidade, desconexões | auditoria reproduzível de hipóteses e regressões, sem perder sessões | persistência e exportação confiáveis |

Os dados necessários vão muito além de resposta da aceleração. **O ponto central é entender o estado real da central e a relação entre o controle GNV e a ECU original**, para corrigir interpretações e melhorar funções existentes.

## Evidência cruzada da primeira sessão real S20 (2026-11-21)

Backup local íntegro: `/sdcard/Download/Omegas/dyno-evidencias/dinamometro-omegas-1795307992224.zip`, SHA-256 `0c926a353cdacd5e5686cca5ab364aa927306ceab8d03b9b066e4ea37efda0b2`. **Não versionar o ZIP nem seus bytes brutos**.

- 3.904 observações; 1.130 OBD (7 PIDs conhecidos) e 2.774 MP48; 139 s de janela.
- Para leituras OBD emparelhadas à MP48 no mesmo relógio dentro de 200 ms:
  - RPM: 145 pares, diferença absoluta mediana de **6 RPM**; 90º percentil 37 RPM.
  - MAP: 146 pares, diferença absoluta mediana de **1,4 kPa**; 90º percentil 3,1 kPa. Suporte forte a essas escalas.
  - Água/temperatura: 147 pares, interpretação MP48 `109 - byte[12]` apresentou diferença mediana de **-48 °C** frente a ECT do OBD. **Inconclusivo:** escalas, offset, sensor diferente, captura física ou suposição antiga podem explicar. Não chamar a conversão de validada.
- Byte [11] da MP48: `0x88` em 2.113 amostras consecutivas sem pulso GNV e `0x90` nas 661 seguintes (636 com pulso GNV). `PROTOCOLO-LANDI/18-FORMULAS-ESCALA.md` classifica `0x88` como **transição inferida**; o decodificador principal dispõe do estado mais explicativo `AGUARDANDO_COMUTACAO_GNV`. Não reclassificar como gasolina sem teste físico.
- Byte [19] bruto MP48: permaneceu **0** nas amostras `0x88`; nas `0x90` ficou majoritariamente em 219–225. O aplicativo principal exibe uma tensão estimada **somente em GNV**. Essa correlação não prova a natureza física do campo: requer validação independente antes de automações.
- A UI de Sessões do laboratório mostrou `0 amostras` durante uma gravação que continha milhares de observações no ZIP; revisar cache/refresh de `CaptureVault.listing`, e criar teste de progresso de captura.

## Sincronização necessária para engenharia

O MP48 recebeu frames a uma cadência mediana de 44,7 ms; o OBD emite respostas de PIDs sequenciais, mediana global de 115,4 ms **entre PIDs diferentes**. Isso é heterogêneo. Armazenar, por amostra, timestamp monotônico, consulta original, resposta, duração da troca, estado e qualidade.

Para comparação entre ECU e MP48, emparelhar pelo tempo e pelo significado do sinal, explicitar o limite máximo do intervalo e registrar a incerteza. Não tratar um `STFT` de quase um segundo atrás como contemporâneo a um novo pulso MP48 só porque está no mesmo quadro renderizado.

## Continuidade e atualização sem perda de dados

O protótipo tem `applicationId = com.omegas.dyno.lab`, banco privado `dyno-vault.db` e assinatura local estável. Atualizações 7→8→9 foram efetuadas por **PackageManager `-r`**, sem desinstalar. A chave foi copiada do cache para diretório privado persistente no S20 `~/.local/share/omegas-signing/`, modo 0600; NUNCA enviar chave/senha ao repositório público.

Gate para **cada** nova versão: backup ZIP válido antes de instalar; mesmo ID e certificado; `versionCode` crescente; migração de banco explícita e testada com registros antigos; atualização `-r` sem apagar dados; comparar total de observações/sessões antes e depois. O `onUpgrade` existente ainda falha para schema >1; **não aumentar `VERSION` antes de implementar e testar a migração**. Não instalar nova versão durante sessão ativa.

## Próximo corte de código

1. Validador de compatibilidade para atualizar APK sem limpeza, com teste in-place.
2. Contadores de gravação ao vivo (sem consulta SQLite a cada frame) e exportação segura.
3. Scanner offline de todos os 34 bytes MP48 com comparação de PIDs OBD e indicação de hipóteses, sem upload de dados brutos.
4. Consumidor read-only no serviço canônico MP48 e transporte opcional, com buffer local e consentimento explícito.
5. Contratos e replay para AutoCal, Refino, Mapa/Curva K, Ferramentas e Sessões; sem tocar telas canônicas sem autorização.

**Linha vermelha:** nunca alterar a ECU, adaptar mapa ou interromper a conexão USB do veículo para testar uma hipótese; investigação de campo só leitura.
