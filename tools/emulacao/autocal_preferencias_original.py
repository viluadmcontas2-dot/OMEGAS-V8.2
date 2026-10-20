#!/usr/bin/env python3
"""Preferências AutoCal: escritores/leitores originais, CPU x86 Unicorn.

Não escreve arquivos nem abre serial. SetDataInEcu recebe valores RAW explícitos;
não simula um clique nem prova a conversão física ou o firmware. GetEcuData roda
até terminar a requisição em WriteFile; resposta/readback NÃO são simulados.
ACK das escritas e objetos Delphi são artificiais. DFM/RTTI são versionados.
Sem executável: SKIP, exit 0. Dependências opcionais só ao emular.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[2]
DFM = ROOT / "docs/progbase/inventario/dfm"
SHA256 = "8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4"
DEFAULT_EXE = Path("/tmp/claude-0/-home-user-OMEGAS-V8-2/ce19e461-e70c-54b2-8118-09ced10d40b6/scratchpad/progbase/ProgBase.exe")
# Entrada RAW de demonstração, não recomendação nem valor lido de uma central.
CASES = (
    ("VECT_AUTOCAL_U8_2", 3), ("MAX_RPM_FOR_AUTOCAL", 3000),
    ("DISABLE_ACQ_BAND", 1), ("AUTO_CAL_ENABLE", 1),
    ("LIMIT_PRESSURE_MIN", 51), ("LIMIT_PRESSURE_MAX", 972),
    ("EN_CDN_T_THD", 1024), ("DIFF_ENG_SPD_THD", 400),
    ("DELTA_ENG_SPD_THD", 200), ("DIFF_MNFLD_PRESS_THD", 512),
    ("DELTA_MNFLD_PRESS_THD", 51), ("DIFF_PETR_TINJ_T_THD", 2048),
    ("DELTA_PETR_INJ_T_THD", 512), ("VECT_AUTOCAL_U8_0", 1),
    ("VECT_AUTOCAL_U8_1", 6),
)
GRID = ("VECT_AUTOCAL_U8_0", "VECT_AUTOCAL_U8_1", 0, 4, 5, 6, 1, 2, 3, 7, 8, 9)


def objects():
    """Somente propriedades explícitas; ausência não vira valor de fábrica."""
    text = (DFM / "TAUTOCALDM.txt").read_text()
    out = {}
    for name, kind, body in re.findall(r"^  object (\w+): (\w+)\n(.*?)^  end$", text, re.M | re.S):
        props = dict(re.findall(r"^    (\w+) = (.*)$", body, re.M))
        out[name] = dict(kind=kind, props=props)
    return out


def exe_path(request=None):
    if request:  # caminho explícito ausente deve pular, sem fallback silencioso
        return Path(request) if Path(request).is_file() else None
    candidate = Path(os.environ.get("PROGBASE_EXE", str(DEFAULT_EXE)))
    return candidate if candidate.is_file() else None


def emular(exe):
    if hashlib.sha256(Path(exe).read_bytes()).hexdigest() != SHA256:
        raise ValueError("SHA-256 diferente do original documentado")
    sys.path[:0] = [str(ROOT / "tools/emulacao"), str(ROOT / "tools/progbase")]
    import cpu_setter_original as C
    from antiapagao_original import _ecu_classe, _instalar_heap

    m = C._Maquina(str(exe))
    m.montar((0, 0), 2, 1, 0, 1)
    _instalar_heap(SimpleNamespace(m=m))
    base_ecu = _ecu_classe()

    class Capture(base_ecu):
        def escreve(self, dados):
            # OBS-COD: pedidos 09 (número), 0A (elemento), 29 (vetor).
            first = self.bruto[0] if self.bruto else dados[0]
            if first in (0x09, 0x0A, 0x29):
                self.bruto += bytes(dados)
                self.rx += bytes(dados)  # eco permite chegar ao checksum
                if len(self.bruto) == (5 if first == 0x0A else 4):
                    m.uc.emu_stop()
                return
            super().escreve(dados)

    def reset():
        m.ecu = Capture(True)
        m.erro = None
        m.instr = 0

    def call(addr, regs=None, stack=()):
        m.chamar(addr, regs, stack)
        if m.erro:
            raise RuntimeError(m.erro)
        return bytes(m.ecu.bruto).hex(" ").upper()

    rows = []
    obs = objects()
    cases = [(name, value, None) for name, value in CASES]
    cases += [("CALIBRATION_VAL_1", i + 1, i) for i in range(10)]
    cases += [(name, raw, i) for name, raw in (("MNFLD_PRESS_THD", 512),
                                             ("PETR_INJ_TBP", 1024))
              for i in range(18)]
    for name, value, index in cases:
        p = obs[name]["props"]
        sc, width = int(p["SerialCode"]), int(p.get("DataLength", 1))
        dim = int(p.get("ArrayDimension", 1))
        v = m.criar_vetor(sc, dim=dim, comp=width)
        vector = obs[name]["kind"] == "TAebVector"
        if not vector:
            m.w32(v, 0xAC9CAC)
            # ntVectorElement sem RowIndex explícito usa zero; escalar usa -1.
            m.w32(v + 0x50, int(p.get("RowIndex", 0)))
        reset()
        if vector:
            write = call(0x9796B8, stack=(v, value, index))
        else:
            write = call(0x977014, stack=(v, value))
        reset()
        read = call(0x978DCC if vector else 0x976D98,
                    stack=(v, m.alloc(max(4, dim * 4)), 1))
        for frame in (write, read):
            q = bytes.fromhex(frame)
            if not q or sum(q[:-1]) & 255 != q[-1]:
                raise RuntimeError("captura incompleta/checksum inválido: " + frame)
        if bytes.fromhex(write)[0] not in (0x12, 0x13, 0x14):
            raise RuntimeError("o escritor não chegou à gravação: " + write)
        rows.append(dict(nome=name, sc=sc, indice=index if vector else int(p.get("RowIndex", 0)),
                         raw=value, gravacao=write, leitura=read))
    return dict(sha256=SHA256, marca="OBS-COD", resposta_leitura="não executada",
                casos=rows)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--exe")
    args = ap.parse_args(argv)
    exe = exe_path(args.exe)
    if exe is None:
        print("SKIP: ProgBase.exe ausente; dados versionados continuam disponíveis.")
        return 0
    print(json.dumps(emular(exe), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
