#!/usr/bin/env python3
"""RTTI Delphi do ProgBase.exe (32 bits): classes, métodos publicados, métodos dinâmicos/mensagens, métodos virtuais,
getters/setters de propriedades publicadas e DFM (formulário -> controle -> evento -> handler).
Lê SÓ o binário (pefile, sem Ghidra); determinístico. Marca: OBS-COD (leitura direta do binário).
Uso: decomp_rtti.py PB.EXE [saida.json]   (saida padrão: docs/progbase/decomp/rtti.json)"""
import json, os, struct, sys
import pefile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))


class Img:
    def __init__(self, caminho):
        self.pe = pefile.PE(caminho)
        self.base = self.pe.OPTIONAL_HEADER.ImageBase
        self.img = self.pe.get_memory_mapped_image()
        self.secs = {s.Name.rstrip(b'\0').decode(): (self.base + s.VirtualAddress, s.Misc_VirtualSize) for s in self.pe.sections}
        t0, tn = self.secs['.text']
        self.text = (t0, t0 + tn)
        self.fim = self.base + len(self.img)

    def ok(self, a):
        return self.base <= a < self.fim - 4

    def u32(self, a):
        return struct.unpack_from('<I', self.img, a - self.base)[0] if self.ok(a) else 0

    def u16(self, a):
        return struct.unpack_from('<H', self.img, a - self.base)[0]

    def u8(self, a):
        return self.img[a - self.base]

    def pstr(self, a):
        n = self.u8(a)
        return self.img[a - self.base + 1:a - self.base + 1 + n].decode('latin1')

    def em_texto(self, a):
        return self.text[0] <= a < self.text[1]


def nome_ok(s):
    return bool(s) and all(c.isalnum() or c in '_.<>$@' for c in s) and not s[0].isdigit()


def achar_classes(im):
    """Procura vmtSelfPtr (-88 Delphi 2010+, -76 Delphi 7) em .text/.data e valida pelo nome da classe."""
    achados = {}
    for nome in ('.text', '.data'):
        ini, tam = im.secs[nome]
        for a in range(ini, ini + tam - 4, 4):
            v = im.u32(a)
            for off in (88, 76):
                if v == a + off:
                    vmt = v
                    cn = im.u32(vmt - (56 if off == 88 else 44))
                    if im.ok(cn) and nome_ok(im.pstr(cn)) and vmt not in achados:
                        achados[vmt] = off
    return achados


def ler_classe(im, vmt, off):
    d = (lambda k: im.u32(vmt - k))
    if off == 88:
        o = dict(intf=84, auto=80, init=76, tinfo=72, field=68, meth=64, dyn=60, name=56, size=52, parent=48)
    else:  # Delphi 7
        o = dict(intf=72, auto=68, init=64, tinfo=60, field=56, meth=52, dyn=48, name=44, size=40, parent=36)
    c = {'vmt': vmt, 'nome': im.pstr(d(o['name'])), 'tamanho_instancia': d(o['size']),
         'pai_vmt': im.u32(d(o['parent'])) if d(o['parent']) else 0, 'unidade': '', 'publicados': [], 'dinamicos': [],
         'virtuais': [], 'propriedades': [], 'vmt_base_off': off}
    # unidade via TypeInfo (tkClass)
    ti = d(o['tinfo'])
    if im.ok(ti) and im.u8(ti) == 7:
        p = ti + 1
        p += 1 + im.u8(p)               # nome
        p += 4                            # ClassType
        p += 4                            # ParentInfo
        p += 2                            # PropCount (total)
        c['unidade'] = im.pstr(p)
        p += 1 + im.u8(p)
        n = im.u16(p)
        p += 2
        for _ in range(min(n, 400)):
            try:
                getp, setp = im.u32(p + 4), im.u32(p + 8)
                nome = im.pstr(p + 26)
                c['propriedades'].append({'nome': nome, 'get': getp if im.em_texto(getp) else 0, 'set': setp if im.em_texto(setp) else 0})
                p += 26 + 1 + len(nome.encode('latin1'))
            except Exception:
                break
    mt = d(o['meth'])
    if im.ok(mt):
        n = im.u16(mt)
        p = mt + 2
        for _ in range(min(n, 2000)):
            sz = im.u16(p)
            end = im.u32(p + 2)
            nm = im.pstr(p + 6)
            if im.em_texto(end) and nome_ok(nm):
                c['publicados'].append({'nome': nm, 'end': end})
            p += sz if sz >= 7 else 6 + 1 + len(nm)
    dt = d(o['dyn'])
    if im.ok(dt):
        n = im.u16(dt)
        if 0 < n < 2000:
            idx = [im.u16(dt + 2 + 2 * i) for i in range(n)]
            for i in range(n):
                end = im.u32(dt + 2 + 2 * n + 4 * i)
                if im.em_texto(end):
                    c['dinamicos'].append({'indice': idx[i], 'end': end})
    a = vmt
    while im.em_texto(im.u32(a)) and len(c['virtuais']) < 600:
        c['virtuais'].append(im.u32(a))
        a += 4
    return c


def ler_dfm(im):
    """Todos os recursos RCDATA que começam com TPF0 -> árvore de componentes."""
    pe = im.pe
    forms = {}
    pe.parse_data_directories([pefile.DIRECTORY_ENTRY['IMAGE_DIRECTORY_ENTRY_RESOURCE']])
    for ent in pe.DIRECTORY_ENTRY_RESOURCE.entries:
        if ent.id != pefile.RESOURCE_TYPE['RT_RCDATA']:
            continue
        for e2 in ent.directory.entries:
            nome = e2.name.decode() if e2.name else str(e2.id)
            for e3 in e2.directory.entries:
                rd = e3.data.struct
                dados = pe.get_data(rd.OffsetToData, rd.Size)
                if dados[:4] == b'TPF0':
                    try:
                        forms[nome] = ParseDFM(dados).raiz()
                    except Exception as ex:  # DFM malformado: registrar, não esconder
                        forms[nome] = {'erro': str(ex)}
    return forms


class ParseDFM:
    def __init__(self, b):
        self.b, self.p = b, 4

    def u8(self):
        v = self.b[self.p]; self.p += 1; return v

    def n(self, fmt, k):
        v = struct.unpack_from(fmt, self.b, self.p)[0]; self.p += k; return v

    def s(self):
        k = self.u8(); v = self.b[self.p:self.p + k].decode('latin1'); self.p += k; return v

    def val(self):
        t = self.u8()
        if t == 0: return None
        if t == 1:
            out = []
            while self.b[self.p] != 0: out.append(self.val())
            self.p += 1; return out
        if t == 2: return self.n('<b', 1)
        if t == 3: return self.n('<h', 2)
        if t == 4: return self.n('<i', 4)
        if t == 5: self.p += 10; return '<ext>'
        if t == 6: return self.s()
        if t == 7: return {'id': self.s()}
        if t == 8: return False
        if t == 9: return True
        if t == 10:
            k = self.n('<I', 4); d = self.b[self.p:self.p + k]; self.p += k; return {'bin': k, 'hex': d[:16].hex()}
        if t == 11:
            out = []
            while True:
                x = self.s()
                if not x: break
                out.append(x)
            return {'set': out}
        if t == 12:
            k = self.n('<I', 4); v = self.b[self.p:self.p + k].decode('latin1'); self.p += k; return v
        if t == 13: return None
        if t == 14:
            itens = []
            while self.b[self.p] != 0:
                if self.b[self.p] in (2, 3, 4): self.val()
                self.u8()
                pr = {}
                while self.b[self.p] != 0:
                    k = self.s(); pr[k] = self.val()
                self.p += 1; itens.append(pr)
            self.p += 1; return itens
        if t == 15: return self.n('<f', 4)
        if t == 16: return self.n('<q', 8) / 10000
        if t == 17: return self.n('<d', 8)
        if t == 18:
            k = self.n('<I', 4); v = self.b[self.p:self.p + 2 * k].decode('utf-16le'); self.p += 2 * k; return v
        if t == 19: return self.n('<q', 8)
        if t == 20:
            k = self.n('<I', 4); v = self.b[self.p:self.p + k].decode('utf-8', 'replace'); self.p += k; return v
        if t == 21: return self.n('<d', 8)
        raise ValueError(f'tipo DFM {t} em {self.p}')

    def comp(self):
        if self.b[self.p] & 0xF0 == 0xF0:  # prefixo: bit 1 = ffChildPos (segue um inteiro)
            fl = self.u8()
            if fl & 2:
                self.val()
        classe = self.s(); nome = self.s()
        props = {}
        while self.b[self.p] != 0:
            k = self.s(); props[k] = self.val()
        self.p += 1
        filhos = []
        while self.b[self.p] != 0:
            filhos.append(self.comp())
        self.p += 1
        return {'classe': classe, 'nome': nome, 'props': props, 'filhos': filhos}

    def raiz(self):
        return self.comp()


def eventos(c, caminho=''):
    """Achata a árvore: (controle, classe, propriedade 'OnXxx', handler)."""
    out = []
    cam = (caminho + '.' if caminho else '') + c['nome']
    for k, v in c['props'].items():
        if k.startswith('On') and isinstance(v, dict) and 'id' in v:
            out.append({'controle': cam, 'classe': c['classe'], 'evento': k, 'handler': v['id']})
    for f in c['filhos']:
        out += eventos(f, cam)
    return out


def montar(caminho_exe):
    im = Img(caminho_exe)
    achadas = achar_classes(im)
    classes = {v: ler_classe(im, v, off) for v, off in sorted(achadas.items())}
    # funções nomeadas: end -> [(nome, fonte)]
    nomes = {}

    def add(end, nome, fonte, classe):
        nomes.setdefault(end, []).append({'nome': nome, 'fonte': fonte, 'classe': classe})

    for c in classes.values():
        pai = classes.get(c['pai_vmt'])
        for m in c['publicados']:
            add(m['end'], f"{c['nome']}.{m['nome']}", 'metodo_publicado', c['nome'])
        for m in c['dinamicos']:
            add(m['end'], f"{c['nome']}.dyn_{m['indice'] & 0xFFFF:04X}", 'metodo_dinamico', c['nome'])
        for pr in c['propriedades']:
            if pr['get']: add(pr['get'], f"{c['nome']}.get_{pr['nome']}", 'propriedade_get', c['nome'])
            if pr['set']: add(pr['set'], f"{c['nome']}.set_{pr['nome']}", 'propriedade_set', c['nome'])
        for i, end in enumerate(c['virtuais']):
            if pai and i < len(pai['virtuais']) and pai['virtuais'][i] == end:
                continue
            add(end, f"{c['nome']}.vmt_{4 * i:03X}", 'metodo_virtual', c['nome'])
    forms = ler_dfm(im)
    ev = {}
    for nome, raiz in sorted(forms.items()):
        if 'erro' not in raiz:
            ev[nome] = eventos(raiz)
    return im, classes, nomes, forms, ev


def main():
    exe = sys.argv[1]
    saida = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'docs', 'progbase', 'decomp', 'rtti.json')
    im, classes, nomes, forms, ev = montar(exe)
    prio = {'metodo_publicado': 0, 'propriedade_get': 1, 'propriedade_set': 1, 'metodo_dinamico': 2, 'metodo_virtual': 3}
    funcs = {}
    for end, lst in nomes.items():
        lst.sort(key=lambda x: (prio[x['fonte']], x['nome']))
        funcs['0x%08X' % end] = lst
    doc = {'marca': 'OBS-COD', 'origem': 'RTTI Delphi lida do binário (decomp_rtti.py)', 'n_classes': len(classes),
           'classes': [{k: (('0x%08X' % v) if k in ('vmt', 'pai_vmt') else v) for k, v in c.items() if k not in ('virtuais', 'publicados', 'dinamicos', 'propriedades')} |
                       {'publicados': [{'nome': m['nome'], 'end': '0x%08X' % m['end']} for m in c['publicados']],
                        'n_dinamicos': len(c['dinamicos']), 'n_virtuais': len(c['virtuais']),
                        'propriedades_com_acessor': [p['nome'] for p in c['propriedades'] if p['get'] or p['set']]}
                       for c in classes.values()],
           'nomes_por_endereco': funcs, 'formularios': {k: v for k, v in forms.items()}, 'eventos': ev}
    os.makedirs(os.path.dirname(saida), exist_ok=True)
    with open(saida, 'w', encoding='utf-8') as fh:
        json.dump(doc, fh, ensure_ascii=False, sort_keys=True, indent=None, separators=(',', ':'))
    print(f'{len(classes)} classes, {len(funcs)} endereços nomeados, {len(forms)} formulários DFM, {sum(len(v) for v in ev.values())} eventos')


if __name__ == '__main__':
    main()
