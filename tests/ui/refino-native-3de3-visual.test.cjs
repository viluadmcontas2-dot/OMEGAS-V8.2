'use strict';
// Prova visual reproduzível: referência ECU em 3/3 e bolinhas Z1-Z4 na lateral.
// A ponte é simulada; isto não substitui teste no veículo.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {open,go,playwright,chromiumPath} = require('./render/lib.js');
const {SIM_SCRIPT} = require('./helpers/teia-sim.cjs');
const pw = playwright();
const skip = pw && chromiumPath(pw.chromium) ? false : 'Chromium indisponível';
const OUT = process.env.OMEGAS_REFINO_NATIVE_OUT || path.join(__dirname,'__teia__','out-refino-native-3de3');

test('3/3: referencia ECU permanece visivel no Refino sem curvas proprias; Z1-Z4 ficam ao lado do grafico', {skip}, async () => {
  fs.mkdirSync(OUT,{recursive:true});
  const {browser,page,errors} = await open(pw.chromium,'connected',{viewport:{width:1280,height:720}});
  try {
    await go(page,'autocal');
    await page.waitForTimeout(800);
    await page.evaluate(SIM_SCRIPT);
    await page.evaluate(() => {
      const bands = Array.from({length:18},(_,i)=>i);
      window.__sim.acquire('petrol',bands,10);
      window.__sim.acquire('gas',bands,10);
      window.__sim.automatch();window.__sim.acquire('gas',bands,10);
      window.__sim.automatch();window.__sim.acquire('gas',bands,10);
      window.OmegasOnRevision?.('tables',window.__ss.rev);
    });
    await page.waitForFunction(() => document.getElementById('autocalAutoMatchCount')?.textContent.trim()==='3/3',null,{timeout:12000});
    await page.waitForFunction(() =>
      document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point').length>=20,
      null,{timeout:12000});
    const box = await page.evaluate(() => {
      const zone = document.getElementById('autocalZoneMeter');
      const chart = document.getElementById('autocalReferenceChart');
      const noise = document.getElementById('autocalAutoCleanLine');
      const b = node => {const r=node.getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,w:r.width,h:r.height}};
      const z=b(zone),c=b(chart),n=noise&&!noise.hidden?b(noise):null;
      return {z,c,n,state:getComputedStyle(zone).flexDirection,rows:[...zone.querySelectorAll('[data-autocal-zone-row]')].map(b),
        petrol:[...zone.querySelectorAll('[data-autocal-zone-petrol]')].map(x=>x.dataset.state),
        gas:[...zone.querySelectorAll('[data-autocal-zone-gas]')].map(x=>x.dataset.state)};
    });
    assert.equal(box.state,'column','zonas verticais, não faixa acima');
    assert.equal(box.rows.length,4,'nenhuma das 4 zonas pode sumir');
    assert.ok(box.z.x>box.c.x+box.c.w*.75,'zonas ficam no canto direito do grafico');
    assert.ok(box.z.y>=box.c.y,'zonas nao invadem legenda');
    assert.ok(box.z.bottom<=box.c.bottom+1,'zonas todas dentro do grafico');
    if(box.n) assert.ok(box.z.y>=box.n.bottom+2 || box.z.x>=box.n.right+2 || box.z.right<=box.n.x-2,
      'limpeza automatica nao encobre zonas');
    await page.screenshot({path:path.join(OUT,'autocal-3de3-zonas-direita.png')});
    await go(page,'refino');
    await page.waitForTimeout(1500);
    const state = await page.evaluate(() => {
      const host=document.getElementById('refinoChart');
      return {
        origins:[...host.querySelectorAll('[data-curve-origin]')].map(x=>x.dataset.curveOrigin),
        own:[...host.querySelectorAll('[data-own-curve]')].map(x=>x.dataset.ownCurve),
        svg:host.querySelectorAll('svg.autocal-reference-svg').length,
        text:host.textContent.trim().slice(0,200),
      };
    });
    assert.equal(state.svg,1,'Refino nao pode trocar grafico por quadro vazio em AutoCal 3/3');
    assert.ok(state.origins.includes('ecu')||state.own.length>0,
      'curva ECU ou propria disponivel deve aparecer, sem aguardar varrer todas as regioes');
    assert.equal(errors.filter(x=>x.startsWith('pageerror:')).length,0);
    await page.screenshot({path:path.join(OUT,'refino-3de3-curvas-ecu.png')});
    fs.writeFileSync(path.join(OUT,'state.json'),JSON.stringify({box,state,errors},null,2));
  } finally {await browser.close();}
});
