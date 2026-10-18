#!/usr/bin/env python3
"""Funcionalidades do ProgBase.exe que o app OMEGAS ainda não tem, classificadas, com evidência verificável.

Entrada curada: tools/progbase/dados/funcionalidades.json. Cada evidência é conferida contra a extração:
  H:Classe.Handler  -> handler existe em telas.json (endereço sai de lá)
  C:Classe.Componente -> componente existe no DFM da classe
  I:CHAVE -> chave existe em i18n.json
Se a referência não existe, o gerador FALHA (evita evidência inventada).
Saída: docs/progbase/inventario/funcionalidades.md (+ funcionalidades.json)."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inventario_comum import AQUI, SAIDA, escrever, escrever_json  # noqa: E402

ENTRADA = os.path.join(AQUI, 'dados', 'funcionalidades.json')
CATS = [('util_leigo', 'Útil para leigo'), ('avancado', 'Avançado'), ('instalacao', 'Instalação / oficina'), ('perigoso', 'Perigoso (escreve na ECU ou age no carro)')]


def resolver(ref, telas, dfms, chaves):
    tipo, _, alvo = ref.partition(':')
    if tipo == 'H':
        cls, _, h = alvo.partition('.')
        t = telas.get(cls)
        if not t or h not in t['handlers']:
            raise SystemExit('evidência inexistente: ' + ref)
        return '`%s.%s` (%s)' % (cls, h, t['handlers'][h]['endereco'])
    if tipo == 'C':
        cls, _, comp = alvo.partition('.')
        if comp not in dfms.get(cls, set()):
            raise SystemExit('componente inexistente: ' + ref)
        return 'componente `%s.%s`' % (cls, comp)
    if tipo == 'I':
        if alvo not in chaves:
            raise SystemExit('chave i18n inexistente: ' + ref)
        return 'texto `%s`' % alvo
    raise SystemExit('tipo de evidência desconhecido: ' + ref)


def carregar(saida):
    telas = {t['classe']: t for t in json.load(open(os.path.join(saida, 'telas.json'), encoding='utf-8'))['telas']}
    chaves = {k['id'] for k in json.load(open(os.path.join(saida, 'i18n.json'), encoding='utf-8'))['chaves']}
    dfms = {}
    for t in telas.values():
        comps = set()
        for k in ('menus', 'acoes', 'controles', 'dialogos', 'timers'):
            comps |= {c['componente'] for c in t[k]}
        dfms[t['classe']] = comps
    return telas, dfms, chaves


def gerar(saida=SAIDA):
    telas, dfms, chaves = carregar(saida)
    itens = json.load(open(ENTRADA, encoding='utf-8'))
    out = []
    for it in itens:
        it = dict(it)
        it['evidencia_resolvida'] = [resolver(r, telas, dfms, chaves) for r in it['evidencia']]
        out.append(it)
    escrever_json(os.path.join(saida, 'funcionalidades.json'), out)
    L = ['# Funcionalidades do ProgBase.exe que o app OMEGAS ainda não tem', '',
         'Gerado por `tools/progbase/inventario_funcionalidades.py` a partir de `tools/progbase/dados/funcionalidades.json` (curado) com '
         'evidências conferidas contra a extração (handler, componente ou texto inexistente faz o gerador falhar).', '',
         '- **Marcas por item:** OBS-COD = a função está no executável; DERIVADO = conclusão tirada de nomes/legendas/chamadas; INFERIDO = dedução; DESCONHECIDO = sem prova.',
         '- **Coluna "No app?":** `não` / `parcial` com a base da afirmação. Para "não", a base é uma busca por palavras-chave no código do app (INFERIDO, não prova de ausência).',
         '- Itens de licença/registro estão aqui só para **descrever a função**; nada é contornado.',
         '- Itens "perigoso" **não devem** ir para o app sem decisão explícita do dono (ver regra do app: gravar é sempre manual, com confirmação e leitura de volta).', '']
    L.append('| Categoria | Itens |')
    L.append('|---|---:|')
    for c, nome in CATS:
        L.append('| %s | %d |' % (nome, sum(1 for i in out if i['categoria'] == c)))
    L.append('')
    for c, nome in CATS:
        sel = [i for i in out if i['categoria'] == c]
        if not sel:
            continue
        L += ['## %s (%d)' % (nome, len(sel)), '']
        for i in sel:
            L += ['### %s — %s' % (i['id'], i['nome']), '',
                  '- **Tela:** `%s` · **Marca:** %s · **No app?** %s' % (i['tela'], i['marca'], i['app_tem']),
                  '- **O que é:** %s' % i['o_que_e'],
                  '- **Base da coluna "No app?":** %s' % i['base_app']]
            if i.get('risco'):
                L.append('- **Risco:** %s' % i['risco'])
            L.append('- **Evidência:** ' + '; '.join(i['evidencia_resolvida']))
            L.append('')
    escrever(os.path.join(saida, 'funcionalidades.md'), '\n'.join(L))
    return out


if __name__ == '__main__':
    print(len(gerar()))
