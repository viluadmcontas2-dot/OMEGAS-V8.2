#!/usr/bin/env python3
"""Versiona o texto do Manual de Software Landi Renzo Omegas 2.16.4 C (jul/2012, FONTE-A do fabricante) por página.
Entrada: sw.txt (pdftotext do PDF público https://landi.pl/assets/files/OMEGAS/MANUALE_SW_2164C_gb1.pdf).
Saída: docs/progbase/manual/manual-2.16.4C.txt (marcadores '=== p.N ===') e paginas.json (ids MAN-2.16.4-pNNN, linhas, sha256).
Página = número impresso no rodapé ('N  LANDI RENZO S.p.A.'); sem rodapé = índice+1 (INFERIDO).
Uso: manual_extrair.py SW.TXT"""
import hashlib, json, os, re, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
D = os.path.join(ROOT, 'docs', 'progbase', 'manual')


def main():
    t = open(sys.argv[1], encoding='utf-8', errors='replace').read()
    pags = t.split('\f')
    os.makedirs(D, exist_ok=True)
    out, meta, linha = [], {}, 1
    for i, p in enumerate(pags):
        m = re.findall(r'^\s*(\d{1,3})\s{3,}LANDI RENZO S\.p\.A\.', p, re.M)
        n, fonte = (int(m[-1]), 'rodape') if m else (i + 1, 'INFERIDO')
        corpo = '\n'.join(l.rstrip() for l in p.strip('\n').split('\n'))
        out.append(f'=== p.{n} ===\n{corpo}\n')
        meta[f'MAN-2.16.4-p{n:03d}'] = {'pagina': n, 'numeracao': fonte, 'indice_pdf': i + 1, 'sha256': hashlib.sha256(corpo.encode()).hexdigest(),
                                         'primeira_linha_texto': next((l.strip() for l in corpo.split('\n') if l.strip()), '')[:100]}
    open(os.path.join(D, 'manual-2.16.4C.txt'), 'w', encoding='utf-8').write('\n'.join(out))
    json.dump({'fonte': 'Software Manual Landi Renzo Omegas 2.16.4 C (2012-07) — FONTE-A', 'url': 'https://landi.pl/assets/files/OMEGAS/MANUALE_SW_2164C_gb1.pdf',
               'pdf_bytes': 15852900, 'limite': 'manual de 2012 para ECUs LRE 184-198/EVO; o ProgBase atual é mais novo (ausência no manual NÃO prova ausência no programa)',
               'marca': 'OBS-DOC', 'paginas': meta}, open(os.path.join(D, 'paginas.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
    print(len(meta), 'páginas')


if __name__ == '__main__':
    main()
