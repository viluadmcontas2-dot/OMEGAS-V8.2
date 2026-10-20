// Gera os HTML dos mockups. Uso: node gen.js  (depois render.js tira os PNG)
const fs = require('fs');
const tabs = ['Agora', 'Mapa K', 'Curva K', 'AutoCal', 'Ajuste GNV', 'Sessões', 'Ferramentas', 'Diagnóstico'];
const navHtml = (active) => tabs.map((t, i) => `<div class="${t === active ? 'on' : ''}"><i>0${i + 1}</i><span>${t}</span></div>`).join('');
function page(active, body, extra = '') {
  return `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><link rel="stylesheet" href="base.css"><style>${extra}</style><body><div class="shell">
<div class="live"><span>Tensão <b>13,7 V</b></span><span>MAP <b>0,46 bar</b></span><span>Gasolina inj. <b>3,82 ms</b></span><span>Giros <b>860</b></span><span class="gas">GÁS</span></div>
<div class="screen">${body}</div><div class="nav">${navHtml(active)}</div></div></body></html>`;
}
const out = {};

// 1 Diagnóstico
const checks = [
  ['Bicos de gás', 'ok'], ['Válvula redutora', 'ok'], ['Sensor pressão do gás', 'ok'], ['Sensor pressão do ar', 'ok'],
  ['Sensor temp. do gás', 'ok'], ['Chave gás/gasolina', 'ok'], ['Sensor de alta pressão', 'ok'],
  ['Gás frio demais', 'warn'], ['Fiação baixa pressão', 'ok'], ['Fiação alta pressão', 'ok'], ['Pressão baixa', 'ok'],
  ['Pressão alta demais', 'ok'], ['Pressão confere', 'ok'], ['Pressão do gás baixa', 'bad']];
const lab = { ok: 'OK', warn: 'Atenção', bad: 'Falha' };
const sym = { ok: '✓', warn: '!', bad: '✕' };
out['01-diagnostico'] = page('Diagnóstico', `
<div class="card summ" style="display:flex;align-items:center;gap:16px;border-color:#6b3a3a"><span class="tag bad big nw">✕ 1 falha</span><span class="tag warn big nw">! 1 atenção</span><span class="hint">A central achou um problema: a pressão do gás está baixa. Ela voltou para a gasolina. O que fazer: siga na gasolina e peça revisão do gás.</span></div>
<div class="grid">${checks.map(([n, s]) => `<div class="row ${s}"><span class="n">${n}</span><span class="tag ${s}">${sym[s]} ${lab[s]}</span></div>`).join('')}</div>
<div class="btn-bar"><span class="hint sum2">Siga na gasolina e revise o gás</span><span class="sp"></span><button class="btn q">Ver detalhes</button><button class="btn p">Apagar avisos de falha</button></div>`,
`.grid{display:grid;grid-template-columns:1fr 1fr;grid-auto-flow:column;grid-template-rows:repeat(7,1fr);gap:6px 14px;flex:1;min-height:0}
.row{display:flex;align-items:center;justify-content:space-between;padding:0 16px;border-radius:9px;background:var(--surface);border:1px solid var(--line-soft);border-left:6px solid var(--ok)}
.row.warn{border-left-color:var(--warn);background:#251e12}.row.bad{border-left-color:var(--danger);background:#2a1618}
.nw{white-space:nowrap}.row .tag{font-size:22px;min-width:130px}.sum2{display:none}
@media (max-width:900px){.summ{display:none!important}.sum2{display:block;font-size:20px}.row{padding:0 10px}.row .n{font-size:21px;white-space:nowrap}.row .tag{font-size:21px;min-width:100px}.grid{gap:4px 10px}}`);

// 2 Quando o gás entra
out['02-quando-o-gas-entra'] = page('Ferramentas', `
<div class="card" style="display:flex;align-items:center;gap:18px"><span class="big">Quando o gás entra</span><span class="hint hd">O carro liga na gasolina e passa para o gás quando as 3 condições estiverem certas.</span></div>
<div class="three">
 <div class="card c"><div class="hint">1. Jeito da troca</div><div class="big">Ao acelerar</div><div class="hint s">Troca só quando você pisa no acelerador.</div><div class="st warn">✕ Ainda não: sem acelerar</div></div>
 <div class="card c"><div class="hint">2. Rotação do motor (giros por minuto)</div><div class="stepper"><button class="btn">−</button><div class="v">1200</div><button class="btn">+</button></div><div class="hint s">Só troca acima disso. Agora: 860.</div><div class="st warn">✕ Falta acelerar mais</div></div>
 <div class="card c"><div class="hint">3. Temperatura da água</div><div class="stepper"><button class="btn">−</button><div class="v">40 °C</div><button class="btn">+</button></div><div class="hint s">Motor frio não troca. Agora: 65 °C.</div><div class="st ok">✓ Água quente o bastante</div></div>
</div>
<div class="btn-bar"><span class="hint ft">Gás entra quando as 3 estiverem com ✓.</span><span class="sp"></span><button class="btn q">Desfazer</button><button class="btn p">Salvar na central</button></div>`,
`.three{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;flex:1;min-height:0}.c{display:flex;flex-direction:column;gap:10px;justify-content:space-between}.s{font-size:20px}.st{font-weight:850;font-size:22px}
@media (max-width:900px){.hd{display:none}.three{gap:8px}.c{gap:6px;padding:8px 10px}.stepper .v{min-width:0;flex:1;font-size:24px}.stepper .btn{width:52px}.s,.hint,.st{font-size:20px}.ft{font-size:20px}}`);

// 3 Nível do gás
out['03-nivel-do-gas'] = page('Ferramentas', `
<div class="card" style="display:flex;align-items:center;gap:18px"><span class="big">Nível do gás</span><span class="hint hd">Tipo do medidor:</span><span class="sel">Outro ▾</span><span class="sp"></span><span class="tag ok big">Agora: pouco mais de ¼</span></div>
<div class="card gauge"><div class="hint">O medidor dá um número. Diga ao app qual número é cada marca do tanque.</div>
<div class="scale"><div class="fill"></div><div class="now"><b>125</b><span>agora</span></div>
<div class="mk" style="left:15%"><u></u><b>Reserva</b><span>39</span></div><div class="mk" style="left:36%"><u></u><b>¼</b><span>93</span></div><div class="mk" style="left:56%"><u></u><b>2/4</b><span>143</span></div><div class="mk" style="left:86%"><u></u><b>¾</b><span>219</span></div></div></div>
<div class="btn-bar"><button class="btn">Marcar vazio</button><button class="btn">Marcar cheio</button><button class="btn">Achar marcas</button><span class="sp"></span><button class="btn p">Salvar marcas</button></div>`,
`.sel{height:58px;padding:0 18px;border:1px solid var(--line);border-radius:10px;background:var(--bg);display:inline-flex;align-items:center;font-weight:850}
.gauge{flex:1;display:flex;flex-direction:column;gap:10px;min-height:0}.scale{position:relative;flex:1;margin:50px 20px 6px;border-radius:12px;background:var(--surface-3);border:1px solid var(--line);min-height:70px}
.fill{position:absolute;left:0;top:0;bottom:0;width:49%;background:linear-gradient(90deg,#1c5a46,var(--ok));border-radius:11px 0 0 11px;opacity:.9}
.now{position:absolute;left:49%;top:-44px;transform:translateX(-50%);text-align:center;color:var(--ok);font-size:22px;line-height:1}.now b{display:block}.now span{font-size:20px}
.mk{position:absolute;top:0;bottom:0}.mk u{position:absolute;left:0;top:0;bottom:0;border-left:3px solid var(--text);opacity:.8}.mk b{position:absolute;left:8px;top:6px;font-size:24px}.mk span{position:absolute;left:8px;bottom:6px;color:var(--muted);font-size:22px}
@media (max-width:900px){.hd{display:none}.scale{margin:44px 14px 4px;min-height:50px}.now{top:-44px}.hint{font-size:20px}}`);

// 4 Volta para gasolina
out['04-volta-para-gasolina'] = page('Ferramentas', `
<div class="card" style="display:flex;align-items:center;gap:18px"><span class="big">Volta para gasolina</span><span class="sp"></span><span class="tog">Voltar sozinho <b>LIGADO</b></span></div>
<div class="card frase"><span class="big">Se a pressão do gás cair abaixo de 1,25 bar, o carro volta para a gasolina na hora.</span><span class="hint">Assim o motor nunca fica sem combustível. (bar é a unidade da pressão.)</span></div>
<div class="card meter"><div class="mrow"><span>Pressão do gás agora</span><b class="ok">1,95 bar · folga boa</b></div>
<div class="track"><div class="zone"></div><div class="lim"><b>1,25</b></div><div class="cur"></div></div><div class="ends"><span>0</span><span class="bad">volta para gasolina</span><span class="ok">gás normal</span><span>3</span></div></div>
<div class="btn-bar"><div class="stepper"><span class="hint lb">Pressão mínima</span><button class="btn">−</button><div class="v">Mín. 1,25 bar</div><button class="btn">+</button></div><div class="stepper"><span class="hint lb">Esperar</span><button class="btn">−</button><div class="v">Espera 0 s</div><button class="btn">+</button></div><span class="sp"></span><button class="btn p">Salvar na central</button></div>`,
`.tog{font-weight:800}.tog b{background:var(--ok);color:#06251b;padding:8px 14px;border-radius:8px;margin-left:8px}.frase{display:flex;flex-direction:column;gap:8px;border-left:6px solid var(--accent)}
.meter{flex:1;display:flex;flex-direction:column;justify-content:center;gap:12px}.mrow{display:flex;justify-content:space-between;align-items:baseline;font-size:24px}.mrow b{font-size:30px}
.track{position:relative;height:54px;border-radius:10px;background:var(--surface-3);border:1px solid var(--line)}.zone{position:absolute;left:0;top:0;bottom:0;width:41.6%;background:#3a1c20;border-radius:9px 0 0 9px}
.lim{position:absolute;left:41.6%;top:-8px;bottom:-8px;border-left:4px solid var(--danger)}.lim b{position:absolute;left:8px;top:-30px;font-size:22px;color:var(--danger);white-space:nowrap}
.cur{position:absolute;left:65%;top:-8px;bottom:-8px;width:8px;background:var(--ok);border-radius:4px}.ends{display:flex;justify-content:space-between;color:var(--muted);font-size:22px}
.lb{display:none}.btn-bar .stepper .v{min-width:190px;font-size:24px}
@media (max-width:900px){.lb{display:none}.btn-bar .stepper .v{min-width:0;padding:0 8px;font-size:21px}.stepper .btn{width:48px}.btn-bar{gap:6px}.frase .big{font-size:24px}.hint{font-size:20px}.meter{gap:8px}.mrow{font-size:22px}.mrow b{font-size:24px}.card{padding:6px 14px}}`);

// 5 Painel ao vivo
const sty5 = `.p5{display:grid;grid-template-columns:repeat(6,1fr);background:var(--surface);border-bottom:1px solid var(--line)}
.cell{padding:8px 14px;border-right:1px solid var(--line-soft);display:flex;flex-direction:column;justify-content:center}.cell span{font-size:20px;color:var(--muted);line-height:1.05}.cell b{font-size:28px;font-variant-numeric:tabular-nums;line-height:1.1}.cell b small{font-size:20px;color:var(--muted);font-weight:700}
.lamps{display:flex;align-items:center;gap:16px;padding:0 18px;height:52px;background:var(--surface-2);border-bottom:1px solid var(--line);font-size:22px;font-weight:800}
.lamp{display:inline-flex;align-items:center;gap:8px;color:var(--dim)}.lamp.on{color:var(--text)}.lamp i{width:18px;height:18px;border-radius:50%;background:#1c2a26;border:2px solid var(--line)}.lamp.on i{background:var(--ok);border-color:var(--ok);box-shadow:0 0 10px var(--ok)}.lamp.on.red i{background:var(--danger);border-color:var(--danger);box-shadow:0 0 10px var(--danger)}
.ghost{flex:1;border:2px dashed var(--line);border-radius:12px;display:flex;align-items:center;justify-content:center;color:var(--dim);font-size:24px;text-align:center;padding:0 20px}
.shell5{display:grid;grid-template-rows:auto minmax(0,1fr) var(--nav);width:100vw;height:100vh}.r{margin-left:auto;color:var(--muted);font-weight:650}
@media (max-width:900px){.p5{grid-template-columns:repeat(3,1fr)}.cell{padding:3px 10px;border-bottom:1px solid var(--line-soft)}.cell b{font-size:24px}.lamps{height:44px;gap:12px;font-size:20px;padding:0 10px}.ghost{font-size:22px}.r{font-size:18px}}`;
const lamp = (t, on, red) => `<span class="lamp ${on ? 'on' : ''} ${red ? 'red' : ''}"><i></i>${t}</span>`;
out['05-painel-ao-vivo'] = `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><link rel="stylesheet" href="base.css"><style>${sty5}</style><body><div class="shell5">
<div><div class="p5">
<div class="cell"><span>Pressão do gás</span><b>2,39 <small>bar</small></b></div>
<div class="cell"><span>Folga de pressão</span><b class="ok">1,93 <small>bar</small></b></div>
<div class="cell"><span>Temp. do gás</span><b>52 <small>°C</small></b></div>
<div class="cell"><span>Temp. da água</span><b>65 <small>°C</small></b></div>
<div class="cell"><span>Nível do gás</span><b>pouco mais de ¼</b></div>
<div class="cell"><span>Tensão dos bicos</span><b>13,7 <small>V</small></b></div>
</div><div class="lamps">${lamp('Chave ligada', 1, 1)}${lamp('Gás', 1)}${lamp('Gasolina', 0)}${lamp('Corte', 0)}<span class="r">MAP 0,46 bar · Giros 860</span></div></div>
<div class="screen"><div class="ghost">O conteúdo da aba continua aqui embaixo.<br>O painel fica sempre visível, em todas as abas.</div></div><div class="nav">${navHtml('Agora')}</div></div></body></html>`;

for (const [k, v] of Object.entries(out)) fs.writeFileSync(__dirname + '/' + k + '.html', v);
console.log(Object.keys(out).join('\n'));
