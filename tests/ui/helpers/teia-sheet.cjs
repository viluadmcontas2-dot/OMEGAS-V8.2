'use strict';
// Folha de contato HTML (grade de capturas rotuladas por cenario) + indice + log Markdown/JSON da teia do AutoCal.
const fs = require('node:fs');
const path = require('node:path');
const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

function build(outDir, recs, meta) {
  const groups = [...new Set(recs.map(r => r.group))];
  const fails = recs.filter(r => r.fail.length);
  const card = r => `<figure id="${esc(r.id)}" class="${r.fail.length ? 'bad' : 'ok'}"><a href="shots/${esc(r.id)}.png"><img loading="lazy" src="shots/${esc(r.id)}.png" alt="${esc(r.title)}"></a>` +
    `<figcaption><b>${esc(r.id)}</b> ${esc(r.title)}<br><small>${esc(r.summary)}</small>${r.cmds.length ? `<br><code>${esc(r.cmds.map(c => c.fn + '(' + c.args.join(',') + ')' + (c.bytes ? ' [' + c.bytes + ']' : '')).join(' ; '))}</code>` : ''}${r.fail.length ? `<br><strong>${esc(r.fail.join(' | '))}</strong>` : ''}</figcaption></figure>`;
  const html = `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Teia AutoCal</title>
<style>:root{--bg:#10151b;--fg:#e6edf3;--mut:#8b98a6;--ok:#2ea043;--bad:#f85149}body{margin:0;padding:16px;background:var(--bg);color:var(--fg);font:14px system-ui,sans-serif}
h1{font-size:20px}h2{margin-top:28px;font-size:16px;border-bottom:1px solid #2b3540}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px}
figure{margin:0;background:#18202a;border:2px solid var(--ok);border-radius:8px;padding:6px}figure.bad{border-color:var(--bad)}img{width:100%;display:block;border-radius:4px}figcaption{font-size:12px;margin-top:4px;color:var(--fg)}small{color:var(--mut)}code{font-size:11px;color:#9cdcfe;word-break:break-all}a{color:#58a6ff}nav a{margin-right:10px}</style></head><body>
<h1>Teia de aranha · AutoCal</h1><p>${esc(meta.note)} · ${recs.length} capturas · ${groups.length} grupos · ${fails.length} com falha · ${esc(meta.when)}</p>
<nav>${groups.map(g => `<a href="#g-${esc(g)}">${esc(g)}</a>`).join('')}<a href="log.md">log.md</a><a href="log.json">log.json</a></nav>
${groups.map(g => `<h2 id="g-${esc(g)}">${esc(g)} (${recs.filter(r => r.group === g).length})</h2><div class="grid">${recs.filter(r => r.group === g).map(card).join('')}</div>`).join('')}
</body></html>`;
  fs.writeFileSync(path.join(outDir, 'contact-sheet.html'), html);
  const idx = `<!doctype html><meta charset="utf-8"><title>Indice teia</title><body style="font:14px system-ui;background:#10151b;color:#e6edf3;padding:16px"><h1>Indice</h1><ul>${groups.map(g => `<li><a style="color:#58a6ff" href="contact-sheet.html#g-${esc(g)}">${esc(g)}</a> · ${recs.filter(r => r.group === g).length} capturas · ${recs.filter(r => r.group === g && r.fail.length).length} falhas</li>`).join('')}</ul><p><a style="color:#58a6ff" href="contact-sheet.html">folha de contato</a> · <a style="color:#58a6ff" href="log.md">log.md</a> · <a style="color:#58a6ff" href="log.json">log.json</a></p></body>`;
  fs.writeFileSync(path.join(outDir, 'index.html'), idx);
  const md = ['# Teia do AutoCal: log de comandos (passo, acao, bytes, resultado)', '', meta.note, '', '| # | grupo | passo | comando(s) da ponte | bytes esperados | resultado |', '|---|---|---|---|---|---|']
    .concat(recs.map((r, i) => `| ${i + 1} | ${r.group} | ${r.id}: ${r.title.replace(/\|/g, '/')} | ${r.cmds.map(c => '`' + c.fn + '(' + c.args.join(',') + ')`').join('<br>') || 'nenhum'} | ${r.cmds.map(c => c.bytes).filter(Boolean).join('<br>') || '-'} | ${r.fail.length ? 'FALHA: ' + r.fail.join('; ').replace(/\|/g, '/') : 'ok · ' + r.summary.replace(/\|/g, '/')} |`)).join('\n');
  fs.writeFileSync(path.join(outDir, 'log.md'), md + '\n');
  fs.writeFileSync(path.join(outDir, 'log.json'), JSON.stringify({ meta, captures: recs.map(r => ({ id: r.id, group: r.group, title: r.title, summary: r.summary, commands: r.cmds, state: r.state, pixel: r.pixel, failures: r.fail })) }, null, 1));
}
module.exports = { build };
