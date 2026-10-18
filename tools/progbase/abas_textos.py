#!/usr/bin/env python3
"""Textos didaticos (linguagem leiga) e citacoes do manual Landi Renzo Omegas SW 2.16.4 C para os controles das abas."""
import re

TEXTOS = {}


def carregar_manual(caminho):
    """Indice do manual: lista de (linha, pagina, texto_normalizado)."""
    if not caminho:
        return None
    with open(caminho, encoding='utf-8', errors='replace') as fh:
        linhas = fh.read().split('\n')
    ff, out = 0, []
    for n, l in enumerate(linhas, 1):
        ff += l.count('\f')
        t = re.sub(r'\s+', ' ', l.replace('\f', ' ')).strip()
        out.append((n, ff + 1, t))
    return out


def norm(s):
    return re.sub(r'[^a-z0-9 ]+', ' ', str(s).lower().replace('&&', ' ')).split()


def citar_manual(idx, rec):
    if not idx:
        return None
    cands = []
    for k in ('rotulo', 'rotulo_do_campo'):
        r = rec.get(k) or {}
        if r.get('eng'):
            cands.append(r['eng'])
    for e in cands:
        pal = norm(e)
        if len(pal) < 2:
            continue
        alvo = ' '.join(pal)
        for n, pg, t in idx:
            if alvo in ' '.join(norm(t)):
                trecho = ' '.join(x[2] for x in idx[n - 1:n + 3] if x[2])
                return dict(pagina=pg, linha=n, trecho=trecho[:400], chave=e, marca='OBS-COD',
                            fonte='sw.txt (manual SW 2.16.4 C), linha %d, pagina %d do PDF' % (n, pg))
    return None


def explicar(c, rec, idx, i18n):
    t = TEXTOS.get(c.nome)
    if t:
        return dict(texto=t[0], fonte=t[1], marca=t[2])
    return dict(texto=None, fonte='sem fonte', marca='DESCONHECIDO')


GENERICO_EDIT = (
    "- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto "
    "`(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. "
    "Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico "
    "`EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): "
    "o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). "
    "Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.\n"
    "- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o "
    "handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.\n"
    "- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).\n"
    "- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). "
    "Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).\n"
    "- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.")
