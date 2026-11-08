"""Gera 06-Ajuste-local.dc.html no layout v3: sem barra de ações; controles no painel; grade 12×12 de 496 px
(células maiores, mais fáceis de tocar). Reaproveita a lógica (script) da versão anterior.
Uso: python3 -I ajuste_local_v3.py
"""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import retopbar as RT
import autocal_v3 as A

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.join(HERE, '..', 'project')
ico = A.ico

def casca(t):
    i = t.index('<div style="--bg:'); j = t.index('>', i) + 1
    a = t.index('<div style="position:absolute;left:0;top:0;width:1280px;height:56px')
    b = RT.balanced(t, a)
    n0 = t.index('<nav aria-label="Áreas do aplicativo"'); n1 = t.index('</nav>') + 6
    return dict(root=t[i:j], topbar=t[a:b].rstrip() + '\n', nav=t[n0:n1], helmet=t[t.index('<helmet>'):t.index('</helmet>') + 9])

def main():
    old = open(os.path.join(PROJ, '06-Ajuste-local.dc.html'), encoding='utf-8').read()
    sh = casca(old)
    script = re.search(r'<script type="text/x-dc"[^>]*>(.*?)</script>', old, re.S).group(1)
    # lógica: mostra "Desfazer" no lugar de "Gravar" quando já gravou e nada está pendente; selo no topo
    if 'showUndo' not in script:
        script = script.replace("      canUndo: !!st.prev,", "      canUndo: !!st.prev,\n      showUndo: !!st.prev && changed === 0, showWrite: !(st.prev && changed === 0),\n      seloOk: key === 'saved' || key === 'undone', seloTexto: key === 'undone' ? 'Desfeito e conferido' : 'Gravado e conferido',")
    assert 'showUndo' in script
    BTN = 'flex:1;box-sizing:border-box;height:58px;border:1px solid var(--line);border-radius:12px;background:var(--surface2);color:var(--text);font:600 18px Inter,Roboto,Arial,sans-serif;cursor:pointer'
    html = r'''<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Ajuste local</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
@@HELMET@@
@@ROOT@@

@@TOPBAR@@
<sc-if value="{{ seloOk }}" hint-placeholder-val="{{ false }}"><div role="status" style="position:absolute;right:16px;top:10px;height:36px;box-sizing:border-box;padding:0 12px;display:flex;align-items:center;gap:7px;border-radius:10px;background:var(--ok-soft);border:1px solid #1d5a46;color:var(--ok);font-size:15px;font-weight:600;white-space:nowrap">@@CK@@{{ seloTexto }}</div></sc-if>
<div style="position:absolute;left:32px;top:64px;width:1216px;height:56px;display:flex;align-items:center;gap:16px">
<div style="flex:none;display:flex;align-items:baseline;gap:12px"><h1 style="margin:0;font-size:30px;font-weight:700;letter-spacing:-.01em">Ajuste local</h1><span style="font-size:20px;color:var(--muted)">Mapa K</span></div>
<div style="flex:1;min-width:0;box-sizing:border-box;height:48px;padding:0 18px;display:flex;align-items:center;gap:12px;border-radius:12px;background:{{msgBg}};border:1px solid {{msgBd}};font-size:18px;font-weight:600;white-space:nowrap">
<sc-if value="{{ icoInfo }}" hint-placeholder-val="{{ true }}">@@I_INFO@@</sc-if>
<sc-if value="{{ icoZap }}" hint-placeholder-val="{{ false }}">@@I_ZAP@@</sc-if>
<sc-if value="{{ icoOk }}" hint-placeholder-val="{{ false }}">@@I_OK@@</sc-if>
<sc-if value="{{ icoUndo }}" hint-placeholder-val="{{ false }}">@@I_UNDO@@</sc-if>
<span>{{ msgText }}</span>
</div>
</div>

<section aria-label="Mapa K" style="position:absolute;left:418px;top:128px;width:830px;height:496px;box-sizing:border-box;padding:8px 8px 6px;display:flex;flex-direction:column;gap:2px;background:var(--surface);border:1px solid var(--line);border-radius:14px">
<div style="display:grid;grid-template-columns:86px repeat(12,minmax(0,1fr));gap:2px;height:34px;flex:none">
<div style="display:flex;align-items:center;font-size:14px;font-weight:600;color:var(--dim);line-height:1.1">ms ↓<br>RPM →</div>
<sc-for list="{{ cols }}" as="c" hint-placeholder-count="12">
<button type="button" onClick="{{ c.pick }}" aria-label="Selecionar a coluna {{ c.label }} giros" style="padding:0;border:0;border-radius:6px;background:{{c.bg}};color:{{c.fg}};font:600 15px Inter,Roboto,Arial,sans-serif;font-variant-numeric:tabular-nums;cursor:pointer">{{ c.label }}</button>
</sc-for>
</div>
<sc-for list="{{ rows }}" as="r" hint-placeholder-count="12">
<div style="display:grid;grid-template-columns:86px repeat(12,minmax(0,1fr));gap:2px;height:33px;flex:none">
<button type="button" onClick="{{ r.pick }}" style="padding:0 6px;border:0;border-radius:6px;background:{{r.bg}};color:{{r.fg}};text-align:left;font:600 16px Inter,Roboto,Arial,sans-serif;font-variant-numeric:tabular-nums;white-space:nowrap;cursor:pointer">{{ r.label }}</button>
<sc-for list="{{ r.cells }}" as="c" hint-placeholder-count="12">
<button type="button" onClick="{{ c.pick }}" aria-label="{{ c.aria }}" aria-pressed="{{ c.pressed }}" style="position:relative;padding:0;border:0;border-radius:6px;background:{{c.bg}};color:{{c.fg}};box-shadow:{{c.ring}};outline:{{c.live}};outline-offset:-4px;font:700 19px Inter,Roboto,Arial,sans-serif;font-variant-numeric:tabular-nums;cursor:pointer">{{ c.t }}<sc-if value="{{ c.stall }}" hint-placeholder-val="{{ false }}"><span aria-hidden="true" style="position:absolute;right:2px;top:2px;width:0;height:0;border-left:9px solid transparent;border-top:9px solid #f3ba59"></span></sc-if></button>
</sc-for>
</div>
</sc-for>
<div style="margin-top:auto;display:flex;align-items:center;gap:22px;font-size:15px;color:var(--muted);white-space:nowrap;height:24px">
<span style="display:flex;align-items:center;gap:8px"><span>menos gás</span><span style="width:120px;height:10px;border-radius:5px;background:linear-gradient(90deg,#16222f,#2d6db5)"></span><span>mais gás</span></span>
<span style="display:flex;align-items:center;gap:8px"><span style="width:16px;height:16px;box-sizing:border-box;border-radius:4px;border:2px dashed #f4f7fb"></span>Agora: 1.850 giros · 3,12 ms</span>
<span style="display:flex;align-items:center;gap:8px"><span style="width:0;height:0;border-left:12px solid transparent;border-top:12px solid #f3ba59"></span>Região do engasgo</span>
</div>
</section>

<aside aria-label="Seleção e ajuste" style="position:absolute;left:32px;top:128px;width:370px;height:496px;box-sizing:border-box;padding:14px 16px;display:flex;flex-direction:column;gap:8px;background:var(--surface);border:1px solid var(--line);border-radius:14px">
<div style="display:flex;flex-direction:column;gap:1px;flex:none">
<div style="font-size:14px;font-weight:700;letter-spacing:.12em;color:var(--dim)">SELEÇÃO</div>
<div style="font-size:24px;font-weight:700">{{ selTitle }}</div>
<div style="font-size:15px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ selHint }}</div>
</div>
<button type="button" onClick="{{ pickStall }}" style="flex:none;box-sizing:border-box;height:62px;padding:0 14px;display:flex;align-items:center;gap:12px;border:1px solid #6b5420;border-radius:12px;background:var(--warn-soft);color:var(--text);text-align:left;cursor:pointer">@@I_STALL@@<span style="display:flex;flex-direction:column;gap:1px"><span style="font:700 19px Inter,Roboto,Arial,sans-serif">Região do engasgo</span><span style="font:500 15px Inter,Roboto,Arial,sans-serif;color:var(--muted)">O app sugere +5 % nessas 6 células</span></span></button>
<div style="flex:none;display:flex;gap:8px">
<button type="button" onClick="{{ pickAll }}" style="@@BTN@@">Selecionar tudo</button>
<button type="button" onClick="{{ clearSel }}" style="@@BTN@@">Limpar</button>
</div>
<div style="flex:1;min-height:0;display:flex;flex-direction:column;gap:3px;padding-top:2px">
<div style="font-size:18px;line-height:1.3;font-weight:600">{{ effectMain }}</div>
<div style="font-size:15px;line-height:1.3;color:var(--muted)">{{ effectSub }}</div>
</div>
<div style="flex:none;display:flex;height:52px;box-sizing:border-box;border-radius:12px;background:var(--surface2);border:1px solid var(--line);overflow:hidden">
<button type="button" onClick="{{ modePct }}" style="flex:1;padding:0;border:0;background:{{bgPct}};color:{{fgPct}};font:700 18px Inter,Roboto,Arial,sans-serif;cursor:pointer">Percentual</button>
<button type="button" onClick="{{ modeAdd }}" style="flex:1;padding:0;border:0;border-left:1px solid var(--line);background:{{bgAdd}};color:{{fgAdd}};font:700 18px Inter,Roboto,Arial,sans-serif;cursor:pointer">Somar</button>
<button type="button" onClick="{{ modeSet }}" style="flex:1;padding:0;border:0;border-left:1px solid var(--line);background:{{bgSet}};color:{{fgSet}};font:700 18px Inter,Roboto,Arial,sans-serif;cursor:pointer">Definir</button>
</div>
<div style="flex:none;display:flex;gap:6px">
<button type="button" onClick="{{ minus5 }}" aria-label="Menos 5" style="width:54px;height:58px;padding:0;border:1px solid var(--line);border-radius:12px;background:var(--surface2);color:var(--text);font:700 21px Inter,Roboto,Arial,sans-serif;cursor:pointer">−5</button>
<button type="button" onClick="{{ minus1 }}" aria-label="Menos 1" style="width:54px;height:58px;padding:0;border:1px solid var(--line);border-radius:12px;background:var(--surface2);color:var(--text);font:700 21px Inter,Roboto,Arial,sans-serif;cursor:pointer">−1</button>
<button type="button" aria-label="Digitar o valor" style="flex:1;min-width:0;box-sizing:border-box;height:58px;padding:0 8px;display:flex;align-items:center;justify-content:center;gap:6px;border-radius:12px;background:var(--bg);border:1px solid var(--line);color:var(--text);font:800 20px Inter,Roboto,Arial,sans-serif;font-variant-numeric:tabular-nums;white-space:nowrap;cursor:pointer"><span>{{ readout }}</span><span style="display:flex;color:var(--muted)">@@I_PEN@@</span></button>
<button type="button" onClick="{{ plus1 }}" aria-label="Mais 1" style="width:54px;height:58px;padding:0;border:1px solid var(--line);border-radius:12px;background:var(--surface2);color:var(--text);font:700 21px Inter,Roboto,Arial,sans-serif;cursor:pointer">+1</button>
<button type="button" onClick="{{ plus5 }}" aria-label="Mais 5" style="width:54px;height:58px;padding:0;border:1px solid var(--line);border-radius:12px;background:var(--surface2);color:var(--text);font:700 21px Inter,Roboto,Arial,sans-serif;cursor:pointer">+5</button>
</div>
<sc-if value="{{ showWrite }}" hint-placeholder-val="{{ true }}">
<sc-if value="{{ canWrite }}" hint-placeholder-val="{{ false }}"><button type="button" onClick="{{ write }}" style="flex:none;width:100%;height:58px;display:flex;align-items:center;justify-content:center;gap:10px;border:0;border-radius:12px;background:var(--accent-btn);color:#fff;font:700 20px Inter,Roboto,Arial,sans-serif;cursor:pointer">@@I_CK2@@{{ writeLabel }}</button></sc-if>
<sc-if value="{{ cannotWrite }}" hint-placeholder-val="{{ true }}"><button type="button" disabled style="flex:none;width:100%;height:58px;border:1px solid var(--line);border-radius:12px;background:var(--surface3);color:var(--muted);font:700 19px Inter,Roboto,Arial,sans-serif">Escolha células e ajuste</button></sc-if>
</sc-if>
<sc-if value="{{ showUndo }}" hint-placeholder-val="{{ false }}"><button type="button" onClick="{{ undo }}" style="flex:none;width:100%;height:58px;display:flex;align-items:center;justify-content:center;gap:10px;border:1px solid var(--line);border-radius:12px;background:var(--surface2);color:var(--text);font:700 20px Inter,Roboto,Arial,sans-serif;cursor:pointer">@@I_UN2@@Desfazer</button></sc-if>
</aside>

@@NAV@@

</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":1280,"height":720}}'>
@@SCRIPT@@
</script>
</body>
</html>
'''
    INFO = '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>'
    ZAP = '<path d="M13 2 3 14h9l-1 8 10-12h-9z"/>'
    PEN = '<path d="M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z"/>'
    rep = {'@@HELMET@@': sh['helmet'], '@@ROOT@@': sh['root'], '@@TOPBAR@@': sh['topbar'], '@@NAV@@': sh['nav'], '@@SCRIPT@@': script,
           '@@BTN@@': BTN, '@@CK@@': ico(A.I_CHECK, '#38d39f', 3, 20), '@@I_INFO@@': ico(INFO, '#2f8cff', 2, 24), '@@I_ZAP@@': ico(ZAP, '#2f8cff', 2, 24),
           '@@I_OK@@': ico(A.I_CHECK, '#38d39f', 3, 24), '@@I_UNDO@@': ico(A.I_UNDO, '#9aa9bc', 2, 24), '@@I_PEN@@': ico(PEN, 'currentColor', 2, 20),
           '@@I_STALL@@': ico(ZAP, '#f3ba59', 2, 24), '@@I_CK2@@': ico(A.I_CHECK, 'currentColor', 2.5, 22), '@@I_UN2@@': ico(A.I_UNDO, 'currentColor', 2, 22)}
    for k in ('@@SCRIPT@@', '@@BTN@@'):
        html = html.replace(k, rep[k])
    for k, v in rep.items():
        html = html.replace(k, v)
    assert '@@' not in html, re.findall(r'@@\w+@@', html)
    open(os.path.join(PROJ, '06-Ajuste-local.dc.html'), 'w', encoding='utf-8').write(html)
    print('escrito 06-Ajuste-local.dc.html', len(html))

if __name__ == '__main__':
    main()
