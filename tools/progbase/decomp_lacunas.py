#!/usr/bin/env python3
"""Classifica os bytes de .text FORA de funções (lacunas entre corpos) — INFERIDO por heurística de conteúdo:
 padding (00/CC/90), texto (strings Delphi literais, ASCII/UTF-16), estrutura RTTI (VMT/typeinfo/tabelas de métodos: ponteiros para .text/.data),
 candidato a código (prólogo 55 8B EC / 53 56 57 / 83 C4 / E8..), resto.
Entrada: lacunas.tsv.gz (ExportLacunas.java: início_hex, tamanho, I/D/U). Uso: decomp_lacunas.py PB.EXE   -> docs/progbase/decomp/lacunas.json (+ candidatos a código p/ backlog)"""
import gzip, json, os, struct, sys
import pefile
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DEC = os.path.join(ROOT, 'docs', 'progbase', 'decomp')


def classe(b, base):
    if not b: return 'vazio'
    if all(x in (0, 0xCC, 0x90) for x in b): return 'padding'
    pr = sum(1 for x in b if 32 <= x < 127 or x in (9, 10, 13, 0))
    if pr / len(b) > 0.9 and sum(1 for x in b if 32 <= x < 127) / len(b) > 0.4: return 'texto'
    if len(b) >= 8:
        n = len(b) // 4
        pt = sum(1 for i in range(n) if 0x401000 <= struct.unpack_from('<I', b, 4 * i)[0] < 0xD00000 or struct.unpack_from('<I', b, 4 * i)[0] == 0)
        if pt / n > 0.7: return 'tabela_ponteiros'
    if b[:3] == b'\x55\x8b\xec' or b[:1] in (b'\x53', b'\x56', b'\x57') or b[:2] in (b'\x83\xc4', b'\x8b\xc0'):
        return 'candidato_codigo'
    return 'resto'


def main():
    pe = pefile.PE(sys.argv[1]); base = pe.OPTIONAL_HEADER.ImageBase; img = pe.get_memory_mapped_image()
    tot, cand = {}, []
    for l in gzip.open(os.path.join(DEC, 'lacunas.tsv.gz'), 'rt'):
        ini, n, est = l.split()
        ini, n = int(ini, 16), int(n)
        dados = bytes(img[ini - base:ini - base + n])
        c = ('instrucao_sem_funcao' if est == 'I' else 'dado_definido' if est == 'D' else 'indefinido') + ':' + classe(dados, base)
        d = tot.setdefault(c, [0, 0]); d[0] += 1; d[1] += n
        if est == 'I' or c.endswith(('candidato_codigo', 'resto')) and n > 16:
            cand.append({'a': '0x%08X' % ini, 'bytes': n, 'classe': c})
    cand.sort(key=lambda x: (-x['bytes'], x['a']))
    json.dump({'marca': 'INFERIDO (heurística de conteúdo)', 'por_classe': {k: {'lacunas': v[0], 'bytes': v[1]} for k, v in sorted(tot.items())},
               'maiores_candidatos': cand[:300]}, open(os.path.join(DEC, 'lacunas.json'), 'w'), indent=1, sort_keys=True)
    print(json.dumps({k: v for k, v in sorted(tot.items())}))


if __name__ == '__main__':
    main()
