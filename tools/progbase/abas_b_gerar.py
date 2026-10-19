#!/usr/bin/env python3
"""Inventário auditável da frente B; não declara fórmulas/runtime não observados."""
import argparse
import hashlib
import json
from pathlib import Path
import abas_b_re as A

ABAS = ('TabKVarie TabAdvanced TabCalibration TabIniezione TabInjSettings '
        'TabInjSettingsSt TabTaratura TabVariatore TabBenzina TabAdatta '
        'TabConsumometro TabFrest TabSwitchToPetrol TabDhlpAgaslev TabTest '
        'TabAutoCalNew TabEsci').split()


def inventario(ctx, idx):
    resultado = []
    for nome in ABAS:
        raiz = idx[nome]
        controles = []
        for obj in A.indexa(raiz).values():
            eventos = []
            for evento, handler in sorted(obj.props.items()):
                if evento.startswith('On'):
                    addr = ctx.metodos_inv.get(handler)
                    eventos.append(dict(evento=evento, handler=handler,
                                        endereco=A.h(addr) if addr else None,
                                        marca='OBS-COD' if addr else 'DESCONHECIDO'))
            controles.append(dict(nome=obj.nome, classe=obj.classe,
                                  propriedades=dict(sorted(obj.props.items())), eventos=eventos,
                                  marca='OBS-COD', formula='DESCONHECIDO',
                                  efeito_runtime='DESCONHECIDO'))
        resultado.append(dict(aba=nome, marca='OBS-COD',
                              evidence_id='MAN-ABAS-B-' + nome,
                              fonte='DFM TFORMCONFIG + RTTI TFormConfig', controles=controles,
                              limites=['Propriedades e eventos não provam a fórmula nem o efeito do clique.',
                                       'Combos/grades: itens dinâmicos e conteúdo da ECU não inferidos do DFM.',
                                       'Não é captura de tela nem execução do aplicativo Windows.']))
    return resultado


def gerar(exe, destino):
    digest = hashlib.sha256(Path(exe).read_bytes()).hexdigest()
    if digest != A.SHA256_ESPERADO:
        raise ValueError('Executável diferente da evidência conhecida')
    ctx = A.Ctx(exe)
    dados = inventario(ctx, A.indexa(A.dfm_arvore()))
    destino = Path(destino)
    destino.mkdir(parents=True, exist_ok=True)
    for d in dados:
        d['sha256_exe'] = digest
        (destino / (d['aba'] + '.json')).write_text(json.dumps(d, ensure_ascii=False, indent=2) + '\n')
        linhas = ['# ' + d['aba'], '', 'OBS-COD — ' + d['evidence_id'], '',
                  'Inventário dos controles e vínculos de eventos no original. Fórmulas e efeitos ainda DESCONHECIDOS.', '',
                  '| Controle | Classe | Eventos/endereço |', '|---|---|---|']
        for c in d['controles']:
            ev = '; '.join(e['evento'] + ': ' + str(e['handler']) + ' @ ' + str(e['endereco']) for e in c['eventos'])
            linhas.append('| ' + c['nome'] + ' | ' + c['classe'] + ' | ' + ev + ' |')
        linhas += ['', '## Limites', ''] + ['- ' + x for x in d['limites']]
        (destino / (d['aba'] + '.md')).write_text('\n'.join(linhas) + '\n')
    return dados


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--exe')
    p.add_argument('--out', default=str(Path(A.ROOT) / 'docs/progbase/abas'))
    args = p.parse_args()
    exe = A.achar_exe(args.exe)
    if not exe:
        p.error('ProgBase.exe ausente: forneça --exe ou PROGBASE_EXE')
    dados = gerar(exe, args.out)
    print(f'{len(dados)} abas; {sum(len(d["controles"]) for d in dados)} controles (inclui contêineres aninhados).')
