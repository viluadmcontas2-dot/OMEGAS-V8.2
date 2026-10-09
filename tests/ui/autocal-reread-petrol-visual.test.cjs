'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { open, go, playwright, chromiumPath } = require('./render/lib.js');
const { SIM_SCRIPT } = require('./helpers/teia-sim.cjs');
const pw = playwright();
const skip = pw && chromiumPath(pw.chromium) ? false : 'Chromium indisponível';
const OUT = process.env.OMEGAS_PETROL_REREAD_OUT || path.join(__dirname, '__teia__', 'out-petrol-reread');

test('Reler gasolina não desenha curva GNV falsa nem placeholders calculados com referência gasolina antiga', { skip }, async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const { browser, page, errors } = await open(pw.chromium,'connected',{viewport:{width:1280,height:720}});
  try {
    await go(page,'autocal'); await page.waitForTimeout(750);
    await page.evaluate(SIM_SCRIPT);
    await page.evaluate(() => {
      const bands = Array.from({length:18},(_,i)=>i);
      window.__sim.acquire('petrol',bands,12);
      window.__sim.acquire('gas',bands,12);
      // Perturbação APENAS da bancada visual; NÃO é uma medição LOGNOVO.
      // Serve para provar que não se conectam pontos de contextos diferentes sem referência válida.
      const A=window.OmegasAutoCal;
      const prev=A.getUiProjection.bind(A);
      A.getUiProjection=()=>{
        const v=JSON.parse(prev());
        const g=v.snapshot.fields.find(f=>f.key==='MNFLD_PRESS_BUF_GAS');
        if (g && g.physicalValues.length===18) {
          g.physicalValues[7]=0.72;
          g.physicalValues[8]=0.53;
          g.physicalValues[9]=0.81;
          g.physicalValues[10]=0.62;
        }
        return JSON.stringify(v);
      };
    });
    await page.waitForFunction(() => document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point.gas').length===18, null, {timeout:10000});
    await page.evaluate(() => {
      const A=window.OmegasAutoCal;
      const prep=JSON.parse(A.prepareNativeAction('RESET_PETROL'));
      if(!prep.ok)throw Error('prepare RESET_PETROL falhou');
      const result=JSON.parse(A.executeNativeAction(prep.preparationId));
      if(!result.ok)throw Error('execute RESET_PETROL falhou');
    });
    await page.waitForFunction(() => {
      const s=window.__ss;
      const epoch=window.OmegasApp?.autoCalCockpit?.projection?.liveAcquisitionEpoch;
      return s.p.every(c=>c===0)&&s.g.every(c=>c>0)&&epoch?.petrolPending===true&&s.act?.busy!==true;
    },null,{timeout:10000});
    await page.waitForTimeout(600);
    const state=await page.evaluate(() => {
      const host=document.getElementById('autocalReferenceChart');
      const count=sel=>host.querySelectorAll(sel).length;
      return {
        petrolPoints:count('circle.autocal-acquired-point.petrol'),
        gasPoints:count('circle.autocal-acquired-point.gas'),
        gasConnectedLines:count('path.autocal-epoch-acquisition-line.gas'),
        petrolConnectedLines:count('path.autocal-epoch-acquisition-line.petrol'),
        petrolGhostPlaceholders:count('circle.autocal-missing-point.petrol'),
        allGhostPlaceholders:count('circle.autocal-missing-point'),
        svg:count('svg.autocal-reference-svg'),
        stalePetrolCurve:count('path.autocal-reference-line.petrol'),
        stalePetrolAnchor:count('circle.autocal-reference-point.petrol'),
        zoneText:[...host.querySelectorAll('[data-autocal-zone-label]')].map(n=>n.textContent.trim()),
      };
    });
    await page.screenshot({path:path.join(OUT,'reread-gasolina.png')});
    fs.writeFileSync(path.join(OUT,'reread-gasolina.json'),JSON.stringify(state,null,2));
    assert.equal(state.svg,1,'grade e eixos preservados');
    assert.equal(state.gasPoints,18,'pontos válidos do GNV continuam visíveis');
    assert.equal(state.petrolPoints,0,'nenhum ponto antigo da gasolina reaparece');
    assert.equal(state.stalePetrolCurve,0,'referência gasolina anterior não pode aparecer após RESET_PETROL');
    assert.equal(state.stalePetrolAnchor,0,'nenhum ponto RV de gasolina antigo pode reaparecer');
    assert.equal(state.gasConnectedLines,0,'não ligar pontos GNV numa curva fictícia durante releitura da gasolina');
    assert.equal(state.petrolConnectedLines,0,'sem curva fictícia gasolina');
    assert.equal(state.petrolGhostPlaceholders,0,'referência gasolina antiga não gera pontos fantasmas');
    assert.equal(state.allGhostPlaceholders,0,'pontos previstos dependem da referência gasolina atual');
    assert.equal(errors.filter(s=>s.startsWith('pageerror:')).length,0);
    console.log('PETROL_REREAD_VISUAL=PASS '+JSON.stringify(state));
  } finally {await browser.close();}
});
