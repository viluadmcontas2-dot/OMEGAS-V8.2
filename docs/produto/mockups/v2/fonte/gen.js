// Gera os HTML dos mockups v2. Uso: node gen.js && node render.js (e real.js para as 4 telas travadas).
const fs = require('fs');
const path = require('path');
const TABS = ['AutoCal', 'Refino', 'Ajuste local', 'Ajuste global', 'Ferramentas'];
const LEVELS = { cheio: 'gggg', '3/4': 'ggg-', '1/2': 'gg--', '1/4': 'g---', reserva: 'R', nenhum: 'none' };

function dots(level) {
  const s = LEVELS[level];
  if (s === 'none') return '<span class="d"></span>'.repeat(5) + '<span class="none">—</span>';
  if (s === 'R') return '<span class="d res"></span>' + '<span class="d"></span>'.repeat(4);
  return '<span class="d"></span>' + [...s].map(c => `<span class="d${c === 'g' ? ' on' : ''}"></span>`).join('');
}
function lamp(fuel) {
  if (fuel === 'gas') return '<span class="lamp gas"><i></i><span class="lg">No gás</span><span class="sh">Gás</span></span>';
  if (fuel === 'pet') return '<span class="lamp pet"><i></i><span class="lg">Na gasolina</span><span class="sh">Gasolina</span></span>';
  return '<span class="lamp off"><i></i><span class="lg">Sem central</span><span class="sh">Sem central</span></span>';
}
function top(level = '1/4', fuel = 'gas') {
  const off = fuel === 'off';
  const v = (x) => (off ? '—' : x);
  return `<div class="top">
<span class="cell"><small>Tensão</small><b>${v('13,8')}</b><em>V</em></span>
<span class="cell"><small><span class="lg">Pressão da admissão</span><span class="sh">Admissão</span></small><b>${v('0,39')}</b><em>bar</em></span>
<span class="cell"><small><span class="lg">Inj. gasolina</span><span class="sh">Inj. gas.</span></small><b>${v('3,44')}</b><em>ms</em></span>
<span class="cell"><small>Rotação</small><b>${v('860')}</b></span>
<span class="lvl"><small>Nível</small>${dots(off ? 'nenhum' : level)}</span>${lamp(fuel)}</div>`;
}
const nav = (active) => `<div class="nav">${TABS.map(t => `<div class="${t === active ? 'on' : ''}">${t === 'Ajuste local' ? 'Ajuste <br class="br">local' : t === 'Ajuste global' ? 'Ajuste <br class="br">global' : t}</div>`).join('')}</div>`;
const BR = '.nav .br{display:none}@media (max-width:900px){.nav .br{display:inline}}';
function page(body, extra = '', opts = {}) {
  return `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><link rel="stylesheet" href="base.css"><style>${BR}${extra}</style><body><div class="shell">
${top(opts.level, opts.fuel)}<div class="screen">${body}</div>${nav(opts.tab || 'Ferramentas')}</div></body></html>`;
}
// Cabeçalho de tela de detalhe: grupo › título + estado em uma palavra.
const head = (group, title, chip, cls = 'ok') => `<div class="head"><span class="crumb">Ferramentas › ${group} ›</span><h1>${title}</h1>${chip ? `<span class="chip ${cls}">${chip}</span>` : ''}</div>`;
const back = '<button class="btn"><span class="lg">‹ Voltar</span><span class="sh">‹</span></button>';
const bar = (...b) => `<div class="btn-bar">${back}${b.join('')}</div>`;
const B = (t, c = '', short) => `<button class="btn ${c}">${short ? `<span class="lg">${t}</span><span class="sh">${short}</span>` : t}</button>`;
const out = {};

// ---------- Ferramentas: grupos à esquerda, cartões do grupo à direita ----------
const GROUPS = [
  { id: 'inicio', name: 'Evitar apagão', st: 'ok', sum: 'Anti-apagão ligado', cards: [
    ['Ver ›', 'Anti-apagão', 'Ligado: age abaixo de 1.600 giros.', 'ok', 'Ao pisar na embreagem, segura o motor para não apagar.'],
    ['Ver ›', 'Saída do corte', '0 injeções de gasolina ao voltar a acelerar.', '', 'Quando você tira o pé e depois acelera de novo.'],
    ['Ver ›', 'Marcha lenta', 'Só gás na marcha lenta.', '', 'Motor ligado sem acelerar, na parada ou manobra.']] },
  { id: 'gasolina-gas', name: 'Gasolina ⇄ gás', st: 'ok', sum: 'No gás agora', cards: [
    ['Ver ›', 'Quando o gás entra', 'Ao acelerar, acima de 1.200 giros, água a 40 °C.', '', 'Por que ainda está na gasolina? Veja aqui.'],
    ['Ver ›', 'Troca de gasolina para gás', 'Um cilindro por vez.', '', 'Como a central passa da gasolina para o gás.'],
    ['Ver ›', 'Volta para gasolina', 'Volta se a pressão do gás cair abaixo de 1,25 bar.', '', 'Protege o motor quando o gás acaba.']] },
  { id: 'pecas-do-gas', name: 'Peças do gás', st: 'ok', sum: 'Gás pouco mais de ¼', cards: [
    ['Ver ›', 'Nível do gás', 'Pouco mais de ¼ do tanque.', '', 'Regular as bolinhas do nível com o seu medidor.'],
    ['Ver ›', 'Injetores', 'Controle ligado, abertura mínima 2,5 ms.', '', 'Os bicos que soltam o gás no motor.']] },
  { id: 'diagnostico', name: 'Diagnóstico', st: 'bad', sum: '1 falha', cards: [
    ['Ver ›', 'Avisos da central', '1 falha: pressão do gás baixa.', 'bad', '14 verificações, cada uma com o que fazer.'],
    ['Ver ›', 'Registro do app', 'Tudo certo.', 'ok', 'Avisos e teste do próprio app.'],
    ['Testar', 'Teste de gravação', 'Só com o carro parado.', '', 'Confere se a central aceita gravar.']] },
  { id: 'sessoes-arquivos', name: 'Sessões e arquivos', st: 'ok', sum: 'Gravando agora', cards: [
    ['Ver ›', 'Sessões', 'Gravando agora. 12 viagens salvas.', 'ok', 'O app grava sozinho cada viagem.'],
    ['Guardar cópia', 'Cópia de segurança', 'Última cópia: hoje, 13:40.', '', 'Guarda o aprendizado atual e as sessões.'],
    ['Ver ›', 'Exportar e sobre', 'Tudo em um arquivo só.', '', 'Mandar as viagens para o técnico; versão do app.']] },
  { id: 'ajustes-do-app', name: 'Ajustes do app', st: 'ok', sum: 'Tudo certo', cards: [
    ['sw-off', 'Refino automático', 'Desligado. Só sugere, nunca grava sozinho.', '', 'Liga as sugestões automáticas do Refino.'],
    ['Sem limite', 'Bateria', 'O Android não pausa o app.', 'ok', 'Se aparecer "Permitir", toque para liberar.'],
    ['sw-on', 'Balão sobre outros apps', 'Ligado: leituras por cima do mapa.', 'ok', 'Mostra as leituras com o GPS aberto.']] },
];
const FCSS = `.fh{display:grid;grid-template-columns:300px minmax(0,1fr);gap:12px;flex:1;min-height:0}
.gl{display:flex;flex-direction:column;gap:8px}
.g{flex:1;display:flex;flex-direction:column;justify-content:center;gap:2px;padding:0 16px;border-radius:12px;background:var(--surface);border:1px solid var(--line);position:relative;min-height:58px}
.g b{font-size:22px;font-weight:850;display:flex;align-items:center;gap:10px;white-space:nowrap}.g b i{width:12px;height:12px;border-radius:50%;background:var(--ok);flex:0 0 auto}.g b i.bad{background:var(--danger)}
.g span{font-size:20px;color:var(--muted);padding-left:22px}
.g.on{background:var(--accent-soft);border-color:var(--accent)}.g.on::before{content:"";position:absolute;left:0;top:12px;bottom:12px;width:4px;border-radius:3px;background:var(--accent)}
.pane{min-width:0;display:flex;flex-direction:column;gap:10px;min-height:0}
.pane h2{margin:0;font-size:28px;font-weight:850}
.fc{display:grid;grid-template-columns:190px minmax(0,1fr);align-items:center;gap:18px;padding:14px 18px;border-radius:12px;background:var(--surface);border:1px solid var(--line);flex:0 0 auto}
.fc .btn,.fc .sw{width:100%;justify-content:center}.fc .tag{height:58px;display:flex;align-items:center;justify-content:center;border-radius:10px;border:1px dashed var(--line);color:var(--ok);font-weight:850}
.fc h3{margin:0;font-size:24px;font-weight:850}.fc p{margin:2px 0 0;font-size:22px;font-weight:750}.fc .x{font-size:20px;color:var(--muted);font-weight:600}
@media (max-width:900px){.fh{grid-template-columns:252px minmax(0,1fr);gap:8px}.gl{gap:3px}.g span{display:none}.g{padding:0 10px}.g b{font-size:22px;gap:8px}.g b i{width:10px;height:10px}
.pane{gap:6px}.pane h2{display:none}.fc{grid-template-columns:142px minmax(0,1fr);gap:10px;padding:8px 10px}.fc h3{font-size:22px}.fc p{font-size:22px;white-space:normal}.fc .x{display:none}.fc .sw{padding:0 10px 0 6px;gap:6px;font-size:20px}.fc .sw u{width:44px}.fc .sw.on u::after{left:16px}}`;
for (const g of GROUPS) {
  const left = GROUPS.map(x => `<div class="g ${x === g ? 'on' : ''}"><b><i class="${x.st}"></i>${x.name}</b><span>${x.sum}</span></div>`).join('');
  const cards = g.cards.map(([a, t, s, c, x]) => {
    const act = a === 'sw-on' ? '<span class="sw on"><u></u>Ligado</span>' : a === 'sw-off' ? '<span class="sw"><u></u>Desligado</span>'
      : a === 'Sem limite' ? '<span class="tag">Sem limite</span>' : `<button class="btn ${a === 'Ver ›' ? '' : 'p'}">${a}</button>`;
    return `<div class="fc">${act}<div><h3>${t}</h3><p class="${c}">${s}</p><div class="x">${x}</div></div></div>`;
  }).join('');
  const name = g.id === 'inicio' ? '02-ferramentas-inicio' : `02${'bcdef'[GROUPS.indexOf(g) - 1]}-ferramentas-${g.id}`;
  out[name] = page(`<div class="fh"><div class="gl">${left}</div><div class="pane"><h2>${g.name}</h2>${cards}</div></div>`, FCSS, { level: '1/4' });
}

// ---------- 03 Anti-apagão ----------
const TILE = `.vt{flex:1;display:flex;flex-direction:column;justify-content:center;gap:6px;padding:14px 20px;border-radius:12px;background:var(--surface);border:1px solid var(--line)}
.vt.sel{border:2px solid var(--accent);background:#0f1b2e}.vt .k{font-size:22px;color:var(--muted);font-weight:750}.vt .s{font-size:20px;color:var(--muted)}
.two{display:flex;gap:12px;flex:1;min-height:0}
@media (max-width:900px){.vt{padding:8px 12px;gap:2px}.vt .s{display:none}.two{gap:8px}}`;
out['03-anti-apagao'] = page(`${head('Evitar apagão', 'Anti-apagão', 'Ligado')}
<div class="body"><div class="card" style="display:flex;align-items:center;gap:18px"><span class="sw on"><u></u>Ligado</span><span>Ao pisar na embreagem, a central segura o motor para ele não apagar.</span></div>
<div class="two"><div class="vt sel"><span class="k">Rotação máxima (giros do motor)</span><span class="big">1.600 giros</span><span class="s">Só age abaixo disso. Ainda apaga? Aumente. Segura gasolina à toa? Diminua.</span></div>
<div class="vt w" style="flex:0 0 360px"><span class="k">De fábrica</span><span class="big mut">1.600</span><span class="s">Outros 4 valores: de fábrica (em teste).</span></div></div></div>
${bar(B('−', 'sq'), B('+', 'sq'), B('Salvar na central', 'p'), B('Voltar ao de fábrica', '', 'De fábrica'), B('Outros valores ›'))}`, TILE);

out['03b-anti-apagao-outros-valores'] = page(`${head('Evitar apagão › Anti-apagão', 'Outros valores', 'Em teste', 'warn')}
<div class="body"><div class="hint small w">Estes 4 valores ainda não têm efeito provado no carro. Mude só se o técnico pedir.</div>
<div class="grid4">${[['Queda para agir', '100 giros', 1], ['Espera para sair do gás', '1.500 ms'], ['Folga para não ficar trocando', '2 giros'], ['Queda para parar de agir', '20 giros']].map(([k, v, s]) => `<div class="vt ${s ? 'sel' : ''}"><span class="k">${k}</span><span class="big">${v}</span><span class="s">De fábrica</span></div>`).join('')}</div></div>
${bar(B('−', 'sq'), B('+', 'sq'), B('Salvar na central', 'p'), B('Voltar ao de fábrica', '', 'De fábrica'))}`, TILE + `.grid4{display:grid;grid-template-columns:1fr 1fr;gap:12px;flex:1;min-height:0}.grid4 .big{font-size:36px}@media (max-width:900px){.grid4{gap:6px}.grid4 .big{font-size:30px}.vt .k{font-size:22px;white-space:nowrap}}`);

// ---------- 04 Saída do corte ----------
const RCSS = `.rows .row .v.ok{color:var(--ok)}.rows .row .v.mut{color:var(--muted)}`;
const rows = (list) => `<div class="rows">${list.map(([l, v, c, n]) => `<div class="row"><span class="l">${l}${n ? '<span class="new">NOVO</span>' : ''}</span><span class="v ${c || ''}">${v}</span></div>`).join('')}</div>`;
out['04-saida-do-corte'] = page(`${head('Evitar apagão', 'Saída do corte', 'Só leitura', '')}
<div class="body"><div class="card"><b>Ao tirar o pé, a central corta o combustível. Ao voltar a acelerar, ela dá <span class="ok">0 injeções</span> de gasolina antes do gás.</b></div>
${rows([['Gasolina ao voltar a acelerar', '0 injeções'], ['Corte das injeções extras', 'Ligado', 'ok'], ['Gás menos forte na saída do corte', '0 %', '', 1]])}</div>
${bar(B('Ler de novo'))}`, RCSS);

// ---------- 05 Marcha lenta ----------
out['05-marcha-lenta'] = page(`${head('Evitar apagão', 'Marcha lenta', 'Só gás')}
<div class="body"><div class="card"><b>Com o motor ligado e sem acelerar, a central fica <span class="ok">só no gás</span>.</b><div class="hint small w" style="margin-top:6px">Parada no sinal, manobra, garagem.</div></div>
${rows([['Modo na marcha lenta', 'Só gás', 'ok'], ['Rotação que vale como marcha lenta', 'Não usada', 'mut']])}
<div class="card" style="border-left:6px solid var(--warn)"><b class="warn">Sugestão:</b> se apagar ao parar, segure com gasolina na parada.</div></div>
${bar(B('Ler de novo'))}`, RCSS);

// ---------- 06 Troca de gasolina para gás ----------
out['06-troca-gasolina-para-gas'] = page(`${head('Gasolina ⇄ gás', 'Troca de gasolina para gás', 'Um cilindro por vez')}
<div class="body"><div class="hint w">É a passagem normal da gasolina para o gás. Não quer dizer que o gás acabou.</div>
${rows([['Um cilindro por vez', 'Ligado', 'ok'], ['Troca suave (as duas juntas por um instante)', 'Ligado', 'ok'], ['Troca automática', 'Ligado', 'ok'], ['Troca pela temperatura da água', 'Ligado', 'ok'], ['Troca pela temperatura do gás', 'Desligado', 'mut']])}</div>
${bar(B('Quando o gás entra ›'), B('Ler de novo'))}`, RCSS + '@media (max-width:900px){.row{min-height:40px}}');

// ---------- 07 Injetores ----------
out['07-injetores'] = page(`${head('Peças do gás', 'Injetores', 'Controle ligado')}
<div class="body"><div class="cols">${rows([['Controle dos injetores', 'Ligado', 'ok'], ['Aquecer os injetores', 'Desligado', 'mut'], ['Mais corrente na troca', 'Desligado', 'mut']])}
${rows([['Abertura mínima do bico', '2,5 ms', '', 1], ['Abertura mínima extra', '0,1 ms', '', 1], ['Reforço ao acelerar', '50', '', 1]])}</div>
<div class="hint small w">Esta tela só consulta. Os 3 números (NOVO) vêm da tela "Injetores" do programa original.</div></div>
${bar(B('Ler de novo'))}`, RCSS + `.cols{display:grid;grid-template-columns:1fr 1fr;gap:12px}.cols .row{min-height:64px}@media (max-width:900px){.cols{gap:6px}.cols .row{min-height:60px;padding:0 8px;gap:6px}.cols .row .new{display:none}}`);

// ---------- 08 Volta para gasolina ----------
out['08-volta-para-gasolina'] = page(`${head('Gasolina ⇄ gás', 'Volta para gasolina', 'Ligado')}
<div class="body"><div class="card"><b>Se a pressão do gás cair abaixo de 1,25 bar, o carro volta para a gasolina na hora.</b></div>
<div class="card gauge"><div class="gl2"><span>Pressão do gás agora</span><b class="ok">1,95 bar · folga boa</b></div><div class="track"><div class="red"></div><div class="mk" style="left:41.6%"></div><div class="now" style="left:65%"></div></div>
<div class="gl2 lab"><span class="bad">abaixo de 1,25: volta para gasolina</span><span class="ok w">gás normal</span></div></div>
<div class="two"><div class="vt sel"><span class="k">Pressão mínima</span><span class="big">1,25 bar</span></div><div class="vt"><span class="k">Espera antes de voltar</span><span class="big">0 s</span></div></div></div>
${bar(B('−', 'sq'), B('+', 'sq'), B('Pressão', 'sel'), B('Espera'), B('Salvar na central', 'p'))}`, TILE + `.gauge{display:flex;flex-direction:column;gap:8px}.gl2{display:flex;justify-content:space-between;gap:10px}.track{position:relative;height:26px;border-radius:8px;background:var(--surface-3)}.track .red{position:absolute;left:0;top:0;bottom:0;width:41.6%;border-radius:8px 0 0 8px;background:#3d1b1f}.track .mk{position:absolute;top:-5px;bottom:-5px;width:4px;background:var(--danger)}.track .now{position:absolute;top:-5px;bottom:-5px;width:6px;border-radius:3px;background:var(--ok)}.lab{font-size:20px}.two .big{font-size:38px}
@media (max-width:900px){.gauge{gap:4px}.track{height:18px}.two .big{font-size:30px}.vt{flex-direction:row;align-items:center;justify-content:space-between}}`);

// ---------- 09 Quando o gás entra ----------
out['09-quando-o-gas-entra'] = page(`${head('Gasolina ⇄ gás', 'Quando o gás entra', 'Falta acelerar', 'warn')}
<div class="body"><div class="hint w">O carro liga na gasolina e passa para o gás quando as 3 condições estão com ✓.</div>
<div class="three"><div class="vt"><span class="k">1. Jeito da troca</span><span class="big">Ao acelerar</span><b class="warn">✕ Ainda não acelerou</b></div>
<div class="vt sel"><span class="k">2. Giros do motor</span><span class="big">1.200</span><b class="warn">✕ Agora: 860</b></div>
<div class="vt"><span class="k">3. Água do motor</span><span class="big">40 °C</span><b class="ok">✓ Agora: 65 °C</b></div></div></div>
${bar(B('−', 'sq'), B('+', 'sq'), B('Giros', 'sel'), B('Água'), B('Salvar na central', 'p'))}`, TILE + `.three{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;flex:1;min-height:0}.three .big{font-size:40px}.three b{font-size:22px}
@media (max-width:900px){.three{gap:6px}.three .big{font-size:30px}.three .vt{justify-content:space-around}.three .k{white-space:nowrap}}`, { fuel: 'pet' });

// ---------- 10 Nível do gás ----------
const NCSS = `.scale{position:relative;height:30px;border-radius:9px;background:var(--surface-3);margin:44px 10px 50px}.fill{position:absolute;left:0;top:0;bottom:0;width:49%;border-radius:9px 0 0 9px;background:linear-gradient(90deg,#1d5a42,#38d39f)}
.mk{position:absolute;top:-8px;bottom:-8px;width:3px;background:#c7d2df}.mk b{position:absolute;bottom:40px;left:50%;transform:translateX(-50%);font-size:22px;white-space:nowrap}.mk span{position:absolute;top:42px;left:50%;transform:translateX(-50%);font-size:20px;color:var(--muted)}
.now{position:absolute;top:-12px;bottom:-12px;width:6px;border-radius:3px;background:var(--ok);left:49%}
.prev{display:flex;align-items:center;gap:14px}.prev .d{width:20px;height:20px}
@media (max-width:900px){.scale{margin:34px 14px 34px;height:22px}.mk b{bottom:30px}.mk span{top:30px;font-size:20px}.prev .d{width:15px;height:15px}}`;
out['10-nivel-do-gas'] = page(`${head('Peças do gás', 'Nível do gás', 'Pouco mais de ¼')}
<div class="body"><div class="card prev"><span>Medidor: <b>Outro</b></span><span class="mut w">·</span><span class="w">Na barra de cima fica assim:</span><span class="lvl" style="display:inline-flex">${dots('1/4')}</span><span class="mut">Agora: 125</span></div>
<div class="card" style="flex:1;display:flex;flex-direction:column;justify-content:center"><div class="hint small w">O medidor dá um número. Diga ao app qual número é cada marca do tanque.</div>
<div class="scale"><div class="fill"></div><div class="now"></div>${[['Reserva', 39, 15], ['¼', 93, 36], ['2/4', 143, 56], ['¾', 219, 86]].map(([n, v, x]) => `<div class="mk" style="left:${x}%"><b>${n}</b><span>${v}</span></div>`).join('')}</div></div></div>
${bar(B('Trocar medidor ›', '', 'Medidor ›'), B('Marcar vazio', '', 'Vazio'), B('Marcar cheio', '', 'Cheio'), B('Achar marcas', '', 'Achar'), B('Salvar marcas', 'p', 'Salvar'))}`, NCSS);

out['10b-nivel-tipo-do-medidor'] = page(`${head('Peças do gás › Nível do gás', 'Tipo do medidor', 'Outro', '')}
<div class="body"><div class="hint w">Toque no tipo de medidor do seu tanque. Na dúvida, pergunte ao instalador.</div>
<div class="opts">${['AEB', '0 a 90 ohm', 'Landi Renzo', 'Sensata HD', 'AEB807A', 'Outro', 'Outro invertido'].map(o => `<button class="btn ${o === 'Outro' ? 'sel' : ''}">${o === 'Outro' ? '✓ ' : ''}${o}</button>`).join('')}</div></div>
${bar(B('Salvar na central', 'p'))}`, `.opts{display:flex;flex-wrap:wrap;gap:8px;align-content:flex-start}`);

// ---------- 11 Diagnóstico ----------
const checks = [['Bicos de gás', 'ok'], ['Válvula redutora', 'ok'], ['Sensor da pressão do gás', 'ok'], ['Sensor da pressão do ar', 'ok'], ['Termômetro do gás', 'ok'], ['Chave gás/gasolina', 'ok'], ['Sensor de alta pressão', 'ok'],
  ['Gás frio demais', 'warn'], ['Fios da baixa pressão', 'ok'], ['Fios da alta pressão', 'ok'], ['Pressão baixa', 'ok'], ['Pressão alta demais', 'ok'], ['Pressão faz sentido', 'ok'], ['Pressão do gás baixa', 'bad']];
const LAB = { ok: '✓ OK', warn: '! Atenção', bad: '✕ Falha' };
const DCSS = `.grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);grid-auto-flow:column;grid-template-rows:repeat(7,1fr);gap:6px 12px;flex:1;min-height:0}
.dr{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:0 14px;border-radius:9px;background:var(--surface);border:1px solid var(--line-soft);border-left:6px solid var(--ok);white-space:nowrap}
.dr.warn{border-left-color:var(--warn);background:#251e12}.dr.bad{border-left-color:var(--danger);background:#2a1618}.dr .t{font-weight:850}
.sum{display:flex;align-items:center;gap:12px}
@media (max-width:900px){.grid{gap:3px 6px}.dr{padding:0 8px;border-left-width:5px}.dh{display:none}.sum b{font-size:22px}}`;
out['11-diagnostico'] = page(`<div class="head dh"><span class="crumb">Ferramentas › Diagnóstico ›</span><h1>Avisos da central</h1><span class="chip bad">✕ 1 falha</span><span class="chip warn">! 1 atenção</span></div>
<div class="card sum" style="border-left:6px solid var(--danger);padding-block:8px"><b class="lg">Pressão do gás baixa: a central voltou para a gasolina. O que fazer: siga na gasolina e peça revisão do gás.</b><b class="sh"><span class="bad">✕ 1 falha:</span> siga na gasolina e revise o gás.</b></div>
<div class="grid">${checks.map(([n, s]) => `<div class="dr ${s}"><span>${n}</span><span class="t ${s}">${LAB[s]}</span></div>`).join('')}</div>
${bar(B('Ver detalhes'), B('Apagar avisos de falha', 'p'))}`, DCSS, { fuel: 'pet' });

out['11b-diagnostico-detalhes'] = page(`${head('Diagnóstico › Avisos da central', 'Detalhes', 'Só leitura', '')}
<div class="body"><div class="hint small w">O que a central faz sozinha quando cada verificação falha (vem do programa original).</div>
<div class="grid">${checks.map(([n, s], i) => `<div class="dr ${s}"><span>${n}</span><span class="mut">${i < 5 ? '→ Gasolina' : 'Só avisa'}</span></div>`).join('')}</div></div>
${bar(B('Apagar avisos de falha', 'p'))}`, DCSS.replace('.head h1{display:none}', '') + `@media (max-width:900px){.dr span:first-child{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis}.dr .mut{font-size:22px}}`, { fuel: 'pet' });

// ---------- 12 Sessões ----------
const SES = [['Agora', 'Gravando · 18 min', 1], ['Hoje 08:12', '42 min'], ['Ontem 18:40', '35 min'], ['Ontem 07:55', '51 min'], ['18/10 19:02', '27 min']];
const SCSS = `.sp2{display:grid;grid-template-columns:380px minmax(0,1fr);gap:12px;flex:1;min-height:0}
.sl{display:flex;flex-direction:column;gap:6px}.si{display:flex;align-items:center;justify-content:space-between;gap:10px;min-height:58px;padding:0 16px;border-radius:10px;background:var(--surface);border:1px solid var(--line);white-space:nowrap}
.si.on{background:var(--accent-soft);border-color:var(--accent)}.si .mut{font-size:20px}
.res{display:grid;grid-template-columns:1fr 1fr;gap:8px;align-content:start}.res .vt{padding:10px 16px}.res .big{font-size:34px}
@media (max-width:900px){.sp2{grid-template-columns:300px minmax(0,1fr);gap:6px}.sl{gap:4px}.si{padding:0 10px}.si:nth-child(n+5){display:none}.res{gap:5px}.res .vt{padding:4px 10px}.res .big{font-size:26px}}`;
const sesBody = `<div class="sp2"><div class="sl">${SES.map(([d, t, on], i) => `<div class="si ${i === 1 ? 'on' : ''}"><b>${d}</b><span class="${on ? 'ok' : 'mut'}">${t}</span></div>`).join('')}</div>
<div class="res"><div class="vt"><span class="k">Hoje 08:12 · 42 min</span><b class="ok">No gás 88% do tempo</b></div><div class="vt"><span class="k">Apagões</span><span class="big ok">0</span></div>
<div class="vt"><span class="k">Pontos aprendidos</span><span class="big">46</span></div><div class="vt"><span class="k">Tensão mais alta</span><span class="big">13,9 V</span></div></div></div>`;
out['12-sessoes'] = page(`${head('Sessões e arquivos', 'Sessões', 'Gravando agora')}${sesBody}
${bar(B('Exportar esta sessão', 'p', 'Exportar'), B('Apagar esta sessão', '', 'Apagar'))}`, TILE + SCSS);
out['12b-sessao-apagada'] = page(`${head('Sessões e arquivos', 'Sessões', 'Gravando agora')}${sesBody.replace('Hoje 08:12</b><span class="mut">42 min', 'Ontem 18:40</b><span class="mut">35 min').replace('<div class="si "><b>Ontem 18:40</b><span class="mut">35 min</span></div>', '')}
<div class="toast">Sessão de hoje 08:12 apagada.</div>
${bar(B('Desfazer', 'p'), B('Exportar esta sessão', '', 'Exportar'), B('Apagar esta sessão', '', 'Apagar'))}`, TILE + SCSS + '@media (max-width:900px){.si:nth-child(n+4){display:none}.toast{padding:4px 10px}}');

// ---------- 13 Exportar e sobre ----------
out['13-exportar-e-sobre'] = page(`${head('Sessões e arquivos', 'Exportar e sobre', '')}
<div class="body"><div class="two"><div class="vt"><span class="k">Exportar</span><b>Todas as viagens e o registro do app em um arquivo só, com o nome do carro e a data.</b><span class="s">Fica na pasta Downloads/OMEGAS. Mande para o técnico.</span></div>
<div class="vt"><span class="k">Sobre</span><b>OMEGAS · versão 8.2</b><span>Central: Landi Renzo Omegas</span><span class="mut">Carro: 208 · 3 cilindros · GNV</span></div></div></div>
${bar(B('Exportar tudo', 'p'), B('Salvar registro do app', '', 'Salvar registro'))}`, TILE);

// ---------- 14 Registro do app ----------
out['14-registro-do-app'] = page(`${head('Diagnóstico', 'Registro do app', 'Tudo certo')}
<div class="body">${rows([['13:57 · Central conectada', 'OK', 'ok'], ['13:40 · Cópia de segurança guardada', 'OK', 'ok'], ['13:12 · Teste do app', 'OK', 'ok'], ['08:12 · Sessão começou a gravar', 'OK', 'ok']])}</div>
${bar(B('Testar o app'), B('Salvar registro', 'p'))}`, RCSS);


// ---------- 01 Barras: topo em 6 estados do nível + navegação de 5 botões ----------
const STATES = [['cheio', 'Cheio: 4 bolinhas verdes', 'gas'], ['3/4', '¾: 3 verdes', 'gas'], ['1/2', '½: 2 verdes', 'gas'], ['1/4', '¼: 1 verde', 'gas'],
  ['reserva', 'Reserva: só a vermelha acesa (pisca)', 'pet'], ['nenhum', 'Sem leitura do nível: bolinhas apagadas e "—"', 'off']];
out['01-barras'] = `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><link rel="stylesheet" href="base.css"><style>${BR}
.sheet{height:100vh;display:flex;flex-direction:column}.st{flex:1;display:flex;flex-direction:column;justify-content:center;gap:2px;padding:0 0 2px}
.st p{margin:0 16px;font-size:20px;color:var(--muted);font-weight:700}.st .top{height:var(--bar);border:1px solid var(--line-soft)}
.cap{padding:6px 16px;font-size:20px;color:var(--muted)}.nav{height:var(--nav)}
@media (max-width:900px){.st p{font-size:16px;margin:0 8px}.cap{display:none}}</style><body><div class="sheet">
${STATES.map(([l, t, f]) => `<div class="st"><p>${t}${f === 'pet' ? ' · lâmpada "Na gasolina"' : f === 'off' ? ' · central desligada' : ''}</p>${top(l, f)}</div>`).join('')}
<div class="cap">Bolinhas da esquerda para a direita: Reserva (vermelha), ¼, ½, ¾, cheio (verdes), como no comutador. Embaixo: só 5 botões.</div>${nav('Ferramentas')}</div></body></html>`;

// ---------- 00 Mapa do fluxo (só 1280x720) ----------
const tabCol = (t, lines, shot) => `<div class="col"><div class="tab">${t}</div><div class="box lock"><b>Tela atual, sem mudança</b>${lines.map(l => `<span>${l}</span>`).join('')}<span style="margin-top:10px;color:var(--warn)">Muda só: barra de cima (bolinhas) e barra de baixo (5 botões).</span><i>${shot}</i></div></div>`;
const grp = (g, items) => `<div class="grp"><div class="gn">${g}</div>${items.map(([n, d]) => `<div class="it"><span>${n}</span>${d ? `<em>${d}</em>` : ''}</div>`).join('')}</div>`;
out['00-mapa-do-fluxo'] = `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><link rel="stylesheet" href="base.css"><style>
body{font-size:16px}.m{height:100vh;padding:10px 14px;display:flex;flex-direction:column;gap:8px}
.bar{display:block;white-space:nowrap;padding:8px 14px;border:1px dashed var(--line);border-radius:10px;color:var(--muted);font-size:17px}.bar b{color:var(--text)}
.cols{display:grid;grid-template-columns:repeat(4,150px) 1fr;gap:10px;flex:1;min-height:0}
.col{display:flex;flex-direction:column;gap:8px}.tab{height:46px;display:flex;align-items:center;justify-content:center;border-radius:10px;background:var(--accent-soft);border:1px solid var(--accent);font-weight:850;font-size:19px}
.box{flex:1;border-radius:10px;background:var(--surface);border:1px solid var(--line);padding:10px;display:flex;flex-direction:column;gap:6px;font-size:15px;color:var(--muted)}.box b{color:var(--text);font-size:16px}.box i{margin-top:auto;font-style:normal;color:#8ec1ff;font-weight:800}
.lock{border-style:dashed}
.fer{display:flex;flex-direction:column;gap:8px}.gs{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;flex:1}
.grp{border-radius:10px;background:var(--surface);border:1px solid var(--line);padding:8px 10px;display:flex;flex-direction:column;gap:5px}
.gn{font-weight:850;font-size:17px;color:var(--text)}.it{display:flex;justify-content:space-between;gap:6px;padding:5px 8px;border-radius:7px;background:var(--surface-2);font-size:15px}.it em{font-style:normal;color:#8ec1ff;font-weight:800;white-space:nowrap}
.note{font-size:15px;color:var(--muted)}.note b{color:var(--text)}</style><body><div class="m">
<div class="bar"><b>Sempre na tela, em cima:</b> Tensão · Pressão da admissão · Inj. gasolina · Rotação · <b>Nível do gás (5 bolinhas)</b> · lâmpada No gás / Na gasolina <em style="float:right;font-style:normal;color:#8ec1ff;font-weight:800">01-barras</em></div>
<div class="cols">
${tabCol('AutoCal', ['Aprende sozinho a gasolina e o gás', 'Gráfico, zonas, reaprender'], '15-autocal')}
${tabCol('Refino', ['Sugestões de ajuste fino', 'Revisar e gravar (só você)'], '16-refino')}
${tabCol('Ajuste local', ['Mapa K 12 × 12', 'Corrigir uma região'], '17-ajuste-local')}
${tabCol('Ajuste global', ['Curva K', 'Backup, suavizar, gravar'], '18-ajuste-global')}
<div class="fer"><div class="tab">Ferramentas <span style="font-weight:600;color:var(--muted);margin-left:8px">(02: grupos à esquerda, cartões à direita)</span></div>
<div class="gs">
${grp('1 · Evitar apagão (02)', [['Anti-apagão', '03 › 03b'], ['Saída do corte', '04'], ['Marcha lenta', '05']])}
${grp('2 · Gasolina ⇄ gás (02b)', [['Quando o gás entra', '09'], ['Troca de gasolina para gás', '06'], ['Volta para gasolina', '08']])}
${grp('3 · Peças do gás (02c)', [['Nível do gás', '10 › 10b'], ['Injetores', '07']])}
${grp('4 · Diagnóstico (02d)', [['Avisos da central', '11 › 11b'], ['Registro do app', '14'], ['Teste de gravação', 'no cartão']])}
${grp('5 · Sessões e arquivos (02e)', [['Sessões', '12 › 12b'], ['Cópia de segurança', 'no cartão'], ['Exportar e sobre', '13']])}
${grp('6 · Ajustes do app (02f)', [['Refino automático', 'no cartão'], ['Bateria', 'no cartão'], ['Balão sobre outros apps', 'no cartão']])}
</div></div></div>
<div class="note"><b>Embaixo, sempre: 5 botões</b> (AutoCal · Refino · Ajuste local · Ajuste global · Ferramentas). Sem "Agora", sem aba Sessões, sem aba Diagnóstico. Em toda tela de detalhe: <b>‹ Voltar</b> é o 1º botão da barra de baixo e volta ao grupo. "no cartão" = a ação acontece no próprio cartão, com um toque.</div>
</div></body></html>`;

const dir = __dirname;
for (const [n, h] of Object.entries(out)) fs.writeFileSync(path.join(dir, n + '.html'), h);
module.exports = { top, nav, dots, GROUPS };
console.log(Object.keys(out).length, 'telas');
