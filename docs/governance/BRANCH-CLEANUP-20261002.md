# Limpeza de branches — plano aprovado pelo dono (2026-10-02)

O dono pediu uma limpeza incisiva. **Ficam só `main` (padrão) e `claude/brave-darwin-wuliyo` (trabalho atual).** As outras 33 são arquivadas como tag `archive/<nome>` no mesmo SHA e depois apagadas. Com isso nada se perde: para recuperar uma branch, basta criá-la a partir da tag.

A sessão do Claude só pode dar push na própria branch (403 ao criar tags ou apagar outras). Quem executa é o GPT, pela API do GitHub.

## Procedimento (para cada linha da tabela)

1. **Criar a tag:** `POST /repos/viluadmcontas2-dot/OMEGAS-V8.2/git/refs` com `{"ref":"refs/tags/archive/<branch>","sha":"<sha>"}`. Se a tag já existir com o mesmo SHA, seguir em frente.
2. **Conferir:** a tag precisa existir com exatamente esse SHA. Se o SHA atual da branch for diferente do SHA da tabela, **não apague**: registre e pule.
3. **Apagar a branch:** `DELETE /repos/viluadmcontas2-dot/OMEGAS-V8.2/git/refs/heads/<branch>`.
4. **PRs abertos:** os PRs abertos cujo head ou base é uma dessas branches (#105–#111, #116, #117, #118) fecham sozinhos quando a branch é apagada. Isso é esperado: eram verificações e experimentos antigos, e o #118 já foi incorporado na branch de trabalho (merge `8be3892d`).
5. **Registro:** no fim, registre neste arquivo o resultado de cada linha (arquivada/apagada/pulada) e a contagem final de branches (esperado: 2).

**Nunca** apagar `main` nem `claude/brave-darwin-wuliyo`. Não apagar nenhuma tag.

| Branch | SHA |
|---|---|
| `OMEGAS-SPEED` | `0317951f7301325174ac39fd6470171119678d68` |
| `OmegasAtlas` | `2ba19ecc58a120385d68b9b296595f325d542d14` |
| `OmegasModular` | `48cb5688bc1eca27f6e7c7fbcae4c6ca3b2fa0cc` |
| `OmegasOuro` | `68a4ac7841179cafb57e74f2c872cebceda15718` |
| `OmegasPlatina` | `b185e80ab68e203e65a79806983bf4210310582c` |
| `OmegasVerde` | `76bed5b1f0f4ab0d18ea5baea3147f58edef2d0d` |
| `build/platina-apk-ac837954` | `515f2158a95241073151f9d0e40f0a4850ad002f` |
| `build/platina-final-75289b7` | `8e8811b5da9b0e6c746cf3076357a160eb8c7989` |
| `codspeed/setup-benchmarks` | `23d4b995255c61bc55b8083a06208e183a6051c3` |
| `fix/autocal-previous-gas-readonly-20261002` | `30bb570576cb0245aa23cb932366ee401508fba8` |
| `hotfix/v8.0-red-performance` | `1be2048e1ca6fc736f6bf38ddcb86aa6329144b7` |
| `research/evo-v03-runner-20260927` | `54c1ab48f2e49943414dedd09ed91c7814ebbc1c` |
| `verify/autocal-checkpoint-r7-20260928` | `893cda9277a429f6ba71b846387c952cf1a931ef` |
| `verify/autocal-finish-host-parity-20260928` | `d8e254f55d15426f5ed6f5bd8dfef61d659f0203` |
| `verify/autocal-finish-host-parity-r2-20260928` | `30af7892e01c9be330ed65b2a514e77d172720eb` |
| `verify/autocal-finish-host-parity-r3-20260928` | `d826432dda138974cc47569ffbff0a63f506430a` |
| `verify/autocal-finish-host-parity-r4-20260928` | `d09f14ff95176dc9d7ccb616616a7cfb4545722b` |
| `verify/autocal-finish-host-parity-r5-20260928` | `7005167897d92d0e649793ce27f88873939f9f03` |
| `verify/autocal-host-parity-r6-20260928` | `3151803546744a9c179a1a2fa9da087ca0e24ca9` |
| `verify/omegas-verde-final-e84f801d` | `076e736c3b77e1bc1d487dd724e4d8df792e08fc` |
| `verify/verde-final-polish-20260924` | `d95d37a3f31a903d7a9edbdfcaaf8755001fe348` |
| `work/amarelo-wu-001-autocal-ground-truth` | `0a01e7cccb4becf03c55d04bd5d38f19c9dea39d` |
| `work/amarelo-wu-001-native-autocal-ground-truth` | `1f442370f29e24de350c539d41a35099eb3fbcc8` |
| `work/autocal-mapk-teacher` | `98bf5ea130c9a63f3d2487e1417c653f5133918b` |
| `work/autocal-teacher-preacq-evidence` | `039c1b6117380c61f1afe4450cccab4ab128983e` |
| `work/omegas-amarelo-foundation` | `11b6ffd73e823a9bdb3c90026323bfcc01a6dfec` |
| `work/omegas-amarelo-wu001-autocal-ground-truth` | `ce9107671b1d4bb8dd23aa42d536aa961255d361` |
| `work/omegas-blue-causal-engine` | `08c6dc79c829851c7ee52cfdf8bea280de75df59` |
| `work/omegas-verde-sil` | `2d091faf946c29fc4d36c6db72c42c7cac936bca` |
| `work/red-v82-logic-validation-20260903` | `674a4a9449631b005fdbfd83be550ebffc570166` |
| `work/red-v82-science-blend` | `bbd589da5f53f0e3842c7db6454614c9a00a7491` |
| `work/v8.2-clean` | `8eb40406c62054e966f753b1aa1457d31d05aec5` |
| `work/wu-006-calibration-science-hardening` | `5cc5c41e28d99d05dea3c78470ba008c96f9220b` |
