# O anti-apagão estava LIGADO ou DESLIGADO na ECU do dono? (2026-10-18)

## Resposta curta
**DESLIGADO** (na ECU do dono, quando o log foi gravado, 12/08/2026). Em 3 leituras do log, `FLAG_CONF3` (SerialCode 250) veio **0x0000 0x0000**; a caixa "Abilitazione anti-stallo" do original é o bit **0x0020 da palavra 0** desse vetor. Com a palavra 0 zerada, o bit é 0.

## Como se chega nisso (cada passo é conferível)
1. **Caixa = bit de FLAG_CONF3** — engenharia reversa do ProgBase.exe (sha256 8a2d297c…): ler = `test [StreamDati+0x2994],0x20`; escrever = `or/and 0x20` no mesmo lugar; ao clicar, o programa envia FLAG_CONF3 inteiro (2 × u16) à ECU. Detalhe e endereços: `2026-10-18-anti-stallo-caixa-re.md`. [OBS-COD; o formato exato do quadro enviado é INFERIDO, ver abaixo]
2. **Valor lido da ECU** — captura Portmon `PortmonLOGNOVO.LOG` (Drive, id 1idvIhV4eFGXv2VVNsU0CT6ewdtTBqNFp; conector baixa). Pedido `29 FA 00 23` (leitura do vetor 250), resposta `53 04 00 00 00 00 57` (ok, 4 bytes de dados = 00 00 00 00, checksum 0x57 confere). Linhas 808, 435010 e 1102872 do log. Reprodutível: `python3 tools/sessoes/portmon_ler_vetor.py <log> 250`. [OBS-FIO]
3. **O leitor foi validado contra um valor conhecido**: o mesmo script, para o código 307 (ANTI_STALLO), devolve `[1600, 100, 1500, 2, 20]`, igual ao já registrado. [OBS-FIO]
4. **Nada no log grava FLAG_CONF3** (0 escritas com `FA 00`): o valor não mudou durante a captura. [OBS-FIO]

## O que NÃO está provado
- Que a ECU esteja assim HOJE: o log é de 12/08/2026. O app passa a ler FLAG_CONF3 junto de `param_snapshot` para confirmar na próxima conexão. [PENDENTE]
- Que ligar a caixa evite os apagões. As 5 posições (1600, 100, 1500 ms, 2, 20) são de fábrica e a ordem campo→rótulo é INFERIDA. O corpus de sessões mostra que a regra "deveria ter atuado" em 5 de 8 apagões em GNV e em 13 de 14 após sair do GNV (`2026-10-18-apagoes-x-anti-stallo.md`), o que é compatível com "regra desligada", mas não prova que ligar resolve.
- O quadro de escrita de FLAG_CONF3: **DERIVADO do código** (não visto ao vivo): `35 FA 00 20 00 00 00 4F` para ligar a partir de 0000 0000; detalhes e prova em `2026-10-18-anti-stallo-quadro-de-escrita.md`.
- Os outros bits de FLAG_CONF3 são desconhecidos aqui (nesta ECU são todos 0).
