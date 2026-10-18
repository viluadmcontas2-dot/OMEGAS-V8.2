#!/usr/bin/env python3
"""Emulador da tela de configuração do ProgBase original: TFormConfig > aba 'Iniettori' (TabIniezione),
região do anti-stallo e vizinhança (PanelSubInjection, y 1020..1420).

Entradas: DFM decodificado (docs/evidence/dfm-original/TFORMCONFIG.txt), i18n (protocolo-landi/dfm/i18n_all.json)
e valores da ECU (ANTI_STALLO cód. 307, FLAG_CONF3 cód. 250, lidos do Portmon). Saída: um HTML autocontido.
Só biblioteca padrão; determinístico (mesma entrada -> mesmos bytes).

Regras (marcas como em docs/evidence/2026-10-18-emulacao-tela-config.md):
  OBS-COD  rotina 0x4ACA6B.. (TabIniezioneShow) e CheckAntiStalloClick 0x4C982C:
           CheckAntiStallo.Checked := flag(4)   onde flag(4) = bit 0x20 de [obj+0x2994]  (0x42E754 caso 4)
           CheckAntiStallo.Enabled := cap()     onde cap() = [obj+0x2A64]<>0 e ([obj+0x2A24] and 1)  (0x434AEC)
           Font.Color := 0x9B0000 se Enabled senão 0x808080
           EditAntiStalloN.Visible := flag(4) e cap();  LabelAntiStalloN/LabAntiStalloN.Visible := Edit.Visible
           EditAntiStalloN.Valor := ANTI_STALLO[N]  ([obj+0x4EC], índice N)
  DERIVADO [obj+0x2994] é gravado no ini como 'Flag5' (seção 'Generale'); Flag1..6 <-> FLAG_CONF1..3 palavras 0/1,
           logo flag(4) = FLAG_CONF3[0] and 0x20.
  INFERIDO cap() = verdadeiro (não se sabe de onde vem [obj+0x2A24]); opção --capacidade off mostra o outro caso.
  Demais controles: Visible do DFM; os Visible=False aparecem só como contorno tracejado (regra de execução DESCONHECIDA).
"""
import argparse
import html as _html
import json
import os
import re
import sys

Y0_PADRAO, Y1_PADRAO = 1020, 1420
SCROLL_W = 994  # ScrollBoxIniezione.Width (DFM)

CORES_SISTEMA = {  # Windows 10, esquema padrão (INFERIDO: o print real não mostra as cores)
    'cl3DLight': '#E3E3E3', 'clBtnFace': '#F0F0F0', 'clWindow': '#FFFFFF', 'clWindowText': '#000000',
    'clNavy': '#000080', 'clGray': '#808080', 'clWhite': '#FFFFFF', 'clBlack': '#000000', 'clRed': '#FF0000',
    'clGreen': '#008000', 'clBlue': '#0000FF', 'clSilver': '#C0C0C0', 'clMaroon': '#800000', 'clYellow': '#FFFF00',
}

ANTI_STALLO_BIT = 0x20  # bit testado por 0x42E754 caso 4: test byte [eax+0x2994], 0x20


# ----------------------------------------------------------------------------------------------- DFM
class Obj:
    def __init__(self, nome, classe, pai=None):
        self.nome, self.classe, self.pai = nome, classe, pai
        self.props, self.filhos = {}, []

    def __repr__(self):
        return '<%s: %s>' % (self.nome, self.classe)


def _str_delphi(v):
    """'abc'#13'def' -> texto. Devolve None se não for string Delphi."""
    out, i = [], 0
    v = v.strip()
    if not v or v[0] not in "'#":
        return None
    while i < len(v):
        c = v[i]
        if c == "'":
            j = i + 1
            buf = []
            while j < len(v):
                if v[j] == "'":
                    if j + 1 < len(v) and v[j + 1] == "'":
                        buf.append("'")
                        j += 2
                        continue
                    break
                buf.append(v[j])
                j += 1
            out.append(''.join(buf))
            i = j + 1
        elif c == '#':
            m = re.match(r'#(\d+)', v[i:])
            out.append(chr(int(m.group(1))))
            i += len(m.group(0))
        elif c in ' +':
            i += 1
        else:
            return None
    return ''.join(out)


def _valor(v):
    s = _str_delphi(v)
    if s is not None:
        return s
    if re.fullmatch(r'-?\d+', v):
        return int(v)
    if re.fullmatch(r'-