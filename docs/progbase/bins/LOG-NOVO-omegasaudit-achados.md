# Log novo `omegasaudit.LOG` — primeiros achados (2026-10-19)
Fonte: Drive OMEGAS/CAPTURAS ORIGINAIS (id 1WFKrA1NpHc7yeFp-o3XnQJ1ShfniulZS; 96 939 309 bytes; 1 708 423 linhas; só ProgBase.exe↔Silabser0). Marcas: OBS-FIO = visto no fio neste log.
Contagem de escritas: 27 139 quadros enviados; opcodes 48(telemetria) 19 887 · 29(ler vetor) 5 005 · 09 1 354 · 0A 286 · **13 189 (102 distintos)** · 2A 138 · **14 40 (35 distintos)** · **35 32 (13 distintos)** · 12 10.

## 1. Anti-apagão — CONFIRMADO AO VIVO (OBS-FIO)
O original, ao marcar/desmarcar "Abilitazione anti-stallo", enviou exatamente:
- ligar: `35 FA 00 20 00 00 00 4F` (6 vezes) — idêntico ao quadro derivado por execução emulada do código (docs/evidence/2026-10-18-cpu-setter-original.md).
- desligar: `35 FA 00 00 00 00 00 2F` (5 vezes).
Isso eleva o quadro de DERIVADO para **OBS-FIO**. Também aparecem `35 C0 00 01 …` (SerialCode 192 = FLAG_CONF2) e `35 03 00 86 xx 51 10 cs` (FLAG_CONF1, várias combinações: 24/04/34/3C/2C) = as demais caixas da aba. O ACK ainda precisa ser pareado quadro a quadro (tarefa do Codex: tools/sessoes/portmon_transacoes.py).

## 2. Bins de RPM e injeção — como o original os troca (OBS-FIO + INFERIDO)
Escrita **por elemento**, comando `14`: `14 <código lo> <código hi> <índice> <valor lo> <valor hi> <checksum>`.
- **Vetor 0x3D (SerialCode 61) = bins de RPM (INFERIDO forte)**: `14 3D 00 00 52 03` (índice 0 = 0x0352 = **850**), `14 3D 00 01 46 05` (idx 1 = 0x0546 = **1350**), `14 3D 00 02 3A 07` (idx 2 = 0x073A = **1850**), idx 3 = 0x09C4 = 2500, idx 4 = 0x0BB8 = 3000. Bate exatamente com os bins 850/1350/1850 do dono. Ou seja: cada bin de RPM é gravado individualmente (valor em rpm, u16), sem comando especial de "mudar bins".
- **Vetor 0x78 (SerialCode 120)**: elementos 2–5 escritos (`14 78 00 02 64 00`=100, `…03 F4 01`=500, `…04 7D 00`=125, `…05 EE 02`=750; outra sequência idx4=35, idx5=826). Candidato a bins de injeção/tempo (INFERIDO fraco; unidade a confirmar: tempo ×0,00256 ms?).
- Vetor 0x1F (SerialCode 31): 3 elementos escritos (`14 1F 00 00 00 7B`, idx 1, idx 2).
PRÓXIMO PASSO (Codex): pareamento requisição→resposta destes quadros; confirmar com REA/Unicorn qual TFormRiferimenti/TFormCentriCelle chama cada vetor e se o primeiro bin de injeção aceita 0 ms (o original deixou 0 ms na Curva K; o limite do OMEGAS 2–18 ms no ajuste local é do app, não da central — hipótese a provar).

## 3. Escritas `13` (SetNumber escalar): 102 quadros distintos
Ex.: `13 1D 00 00 00 30` (x18), `13 1C 00 00 71 A0`… = parâmetros escalares editados na aba Configuração. Catalogar pelo SerialCode (vetores.json em docs/progbase/protocolo).
