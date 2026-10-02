# CHECKPOINTS — diário retomável

Regra: depois de cada commit, este arquivo é atualizado com **feito / falta / próxima ação exata**. Outra sessão deve conseguir retomar lendo só isto e o `README.md`.

## Commit 0 — esqueleto e mapa do que já está provado

**Feito**
- Branch `evidence/progbase-original` criada a partir de `origin/OmegasPlatina` (`b185e80`).
- Lidas e cruzadas as fontes: `docs/reference/progbase/*` (branch `claude/brave-darwin-wuliyo`), `docs/evidence/2026-09-2*` e fixtures `ORIGINAL_DERIVED` (Platina/Verde), código do protocolo em `app/src/main/java/com/omegas/prohub/ecu/`, DUMP no Drive (`TAUTOCALDM`, `TFORMVISUALIZZA` em texto, `README_RELATORIO.md`), capturas Portmon pequenas (`1.LOG`, `2.LOG`, `3.LOG`).
- Esqueleto da pasta e arquivos-fonte preservados em `fontes/`.

**Mapa inicial: já provado antes desta branch (fontes versionadas em Platina)**
- Quadro do protocolo (eco + `53` + len + payload + checksum soma mod 256): `evidence/portmon/full-corpus-manifest.json` (36.457 de 36.463 transações válidas).
- Telemetria `48 01 49`, payload 34 bytes, offsets 0/6/8/11/13/14/16/17/24/28 e consumidores: `tests/fixtures/progbase-autocal-consumer-map-v1.json`.
- Família AutoCal SC 0x014A–0x018E: comandos, cadências, consumidores, ações `02 24 04 xx`, Reset K, Finish, maturidade: fixtures `progbase-autocal-*.json`, `portmon-lognovo-*.json`, `docs/evidence/2026-09-21-*`, `docs/platinum/evidence/*`.
- Escalas DFM dos vetores AutoCal (`/512`, `/1024`, `/16384`): `tests/fixtures/progbase-autocal-scale-dfm-v1.json`.
- Nível: canal `0x0E`, min/max, regra `step = |max-min|·0,2`: `progbase-autocal-consumer-map-v1.json#levels`.

**Lacunas identificadas para cavar nesta branch**
- Sequência de conexão/desconexão do ProgBase na fiação (`00 02`, `01 00 3A`, `00 25`, `00 01`): só corroborada pelo código do OMEGAS.
- Escrita do Mapa K no original (bloco de 144 escritas, flag de inserção em `FLAG_CONF1` SC 3): citado em incidente, sem bytes versionados.
- Escalas da telemetria rápida (pressão `/800`, água `109-raw`, temp. gás `raw-20`, injeção `0,00256 ms`): constantes do app, origem no original ainda não fechada.
- Eixos do Mapa K (`GIRI_PER_K` SC 61, `TEMPI_PER_K` SC 55): o OMEGAS não os lê da ECU.
- Level: tabela por tipo de sensor e conversão raw→nível exibido.

**Próxima ação exata**
- Escrever `protocolo.md` (tema 1) e fazer o commit 1. Em paralelo aguardam três subagentes: parse do `PortmonLOGNOVO.zip` (6 MB), parse de `1/2/3.LOG`, decifração dos `.lec` do carro.
