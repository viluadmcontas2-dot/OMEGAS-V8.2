# QA — ciclo-fechado-refino (2026-10-13)
Risco: gravar durante o AutoMatch contamina a calibração; aquisição GNV/gasolina é da ECU e o app só lê.
Provas: node 234, unittest, run_checks; `tools/jvm/rodar_aquisicao_emulada.sh` (monitor real × ECU falsa: 5 cenários + cabo de guerra F/G + fuzz com anomalias); `tools/ci/estresse_interface.cjs`; `tools/audit/pontas_soltas.py`; mutação da regra de reset (falha como esperado).
Bugs achados: 3 arquivos JS mortos no APK, 3 bindings para elementos inexistentes (podados); 33 funções da ponte Android sem uso no JS (pendente de poda, CI).
Não provado: aparelho; efeito físico; sessões longas com 3+ AutoMatch (não existem no Drive: 104 sessões, 4 com 1 AutoMatch).
