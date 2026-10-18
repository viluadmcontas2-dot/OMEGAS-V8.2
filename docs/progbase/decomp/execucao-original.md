# Execução do ProgBase.exe original (Wine) — 2026-10-18

Marcas: **OBS-FIO** = observado na execução real; **DESCONHECIDO** = não observado.

## Resultado
| Passo | Resultado | Marca |
|---|---|---|
| Sem arquivo de licença (Wine, prefix `wineprog/prefix`) | Tela "License": Request a new license / Load a new license, Base code `HUZUXXBDQC`, Unlock code vazio. Print: `execucao/01-licenca-sem-arquivo.png` | OBS-FIO |
| Com o `License.dat` **do dono** (Drive: `ARQUIVOS OMEGAS/Landi Renzo Omegas/License.dat`, id `1yx61_JOgDxKBVTJVUlIPnYrArmqR8UNd`, 280 bytes; baixado pelo conector `download_file_content`, que devolve base64; SHA-256 do arquivo decodificado `b4827f0b…d655`) copiado **ao lado do exe**, que é o mecanismo normal do programa | O programa PASSA da licença e abre a janela principal "Landi Renzo Omegas" (F1..F6, "Centralina non connessa", "Config: StandardLREvo#000"). Print: `02-principal-com-licenca.png` | OBS-FIO |
| F1 | Janela "Configurazione veicolo - Config: StandardLREvo#000" (`03-F1-configurazione.png`) | OBS-FIO |
| F2 | "Visualizzazione parametri vettura" (`04-F2-visualizzazione.png`) | OBS-FIO |
| F3 | "Diagnosi centralina" (`05-F3-diagnosi.png`): grade "Controlli di diagnosi / Stato" vazia (sem ECU), abas "Tempi di funzionamento / Esclusione iniettori gas / Collegamento iniettori benzina", botões "Azzera errori" e "Esci" | OBS-FIO |
| F4 / F5 | "Scegli la configurazione da caricare in centralina" / "Seleziona file di programmazione" (`06-F5-programmazione.png`) | OBS-FIO |

## O que NÃO foi feito (regras)
Nenhum patch no exe, nenhum keygen, nenhum "Base code" forjado, nenhuma verificação contornada. O `License.dat` é o arquivo do próprio dono, usado como o programa espera. **O arquivo de licença NÃO é versionado** (só o hash).

## Limites
- Sem ECU/serial: "Centralina non connessa"; as telas de dados ficam vazias. Comunicação real só por captura (portmon) — ver `docs/progbase/protocolo`. DESCONHECIDO: comportamento com ECU conectada sob Wine (exigiria porta serial emulada/pty; não tentado nesta rodada).
- A licença de 2016 foi aceita mesmo com Base code diferente do exibido sem arquivo: o vínculo máquina→licença não foi investigado (DESCONHECIDO; não é necessário para o objetivo).
- Resolução 1280x720 Xvfb; fontes do Wine diferem do Windows (usar só como layout, ver `docs/progbase/abas`).
- Reproduzir: `tools/progbase/executar_original.sh` (opcional; requer exe, Resources.dll e License.dat do dono em pasta indicada por variável).
