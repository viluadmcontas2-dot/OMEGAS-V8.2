#!/usr/bin/env python3
"""Gera docs/mapa-de-fontes/MAPA.md a partir de topicos.json (+ drive-inventario.json se existir).
Determinístico e idempotente. `--check` falha se um caminho do repo não existe ou se MAPA.md está desatualizado."""
import json, os, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
DIR = os.path.join(ROOT, 'docs', 'mapa-de-fontes')


def carregar():
    with open(os.path.join(DIR, 'topicos.json'), encoding='utf-8') as fh:
        topicos = json.load(fh)
    inv = None
    path = os.path.join(DIR, 'drive-inventario.json')
    if os.path.exists(path):
        with open(path, encoding='utf-8') as fh:
            inv = json.load(fh)
    return topicos, inv


def render(topicos, inv):
    out = ['# Mapa de fontes do OMEGAS', '', topicos['regra'], '',
           f"Repo privado do protocolo: {topicos['repo_privado_protocolo']}", '']
    for t in topicos['topicos']:
        out.append(f"## {t['assunto']}")
        for chave, rotulo in (('repo', 'No repo'), ('protocolo_landi', 'No PROTOCOLO-LANDI'), ('drive', 'No Drive')):
            for item in t.get(chave, []):
                out.append(f"- {rotulo}: {item}")
        if t.get('como_obter'):
            out.append(f"- **Como obter:** {t['como_obter']}")
        if t.get('falta'):
            out.append(f"- **Falta:** {t['falta']}")
        out.append('')
    if inv:
        itens = inv.get('arquivos', [])
        grandes = [i for i in itens if not i.get('baixavel_pelo_conector', True)]
        out += ['## Inventário do Drive', f"- {len(itens)} arquivos listados; {len(grandes)} acima do limite do conector (10 MB):"]
        for i in sorted(grandes, key=lambda x: x['caminho']):
            out.append(f"  - {i['caminho']} ({i.get('bytes')} bytes, id {i['id']})")
        dlls = [i for i in itens if str(i.get('titulo', '')).lower().endswith('.dll')]
        out.append(f"- DLLs: {len(dlls)}")
        for i in sorted(dlls, key=lambda x: x['caminho']):
            out.append(f"  - {i['caminho']} (id {i['id']}, {i.get('bytes')} bytes)")
        out.append('- Lista completa: docs/mapa-de-fontes/drive-inventario.json')
        out.append('')
    return '\n'.join(out)


def main():
    topicos, inv = carregar()
    texto = render(topicos, inv)
    destino = os.path.join(DIR, 'MAPA.md')
    if '--check' in sys.argv:
        faltando = []
        for t in topicos['topicos']:
            for item in t.get('repo', []):
                if not os.path.exists(os.path.join(ROOT, item.split(' ')[0])):
                    faltando.append(item)
        atual = open(destino, encoding='utf-8').read() if os.path.exists(destino) else ''
        if faltando:
            print('caminhos inexistentes no repo:', *faltando, sep='\n  ')
        if atual != texto:
            print('MAPA.md desatualizado: rode python3 tools/mapa_de_fontes.py')
        sys.exit(1 if faltando or atual != texto else 0)
    with open(destino, 'w', encoding='utf-8') as fh:
        fh.write(texto)
    print('MAPA.md gerado')


if __name__ == '__main__':
    main()
