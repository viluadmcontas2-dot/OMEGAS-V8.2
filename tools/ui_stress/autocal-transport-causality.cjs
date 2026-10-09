'use strict';
// Classe 4 parcial: WebView/DOM real no Chrome; simula o cache da bridge atrasado por N leituras.
// A latência física USB nao e medida. Apenas ponte/browser; Kotlin coberto separadamente por JVM.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { open, go, playwright, chromiumPath } = require('../../tests/ui/render/lib.js');
const { SIM_SCRIPT } = require('../../tests/ui/helpers/teia-sim.cjs');

const pw=playwright();
if(!pw || !chromiumPath(pw.chromium)) {
 console.error('CHROMIUM_REQUIRED: não foi possível executar o teste visual');process.exit(2);
}
const out=process.env.OMEGAS_REVISION_STRESS_OUT||path.join(__dirname,'out-revision');
fs.mkdirSync(out,{recursive:true});
const result={schema:'omegascinza.autoCal.bridge-causality.v1',realEcu:false,originalMonitorInBrowser:false,steps:[],failures:[]};
const pointsFn=()=>[...document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point')].map(x=>x.dataset.autocalPointKey);
(async()=>{
 const o=await open(pw.chromium,'connected',{viewport:{width:1280,height:720}});
 try {
  const page=o.page;await go(page,'autocal');await page.waitForTimeout(750);
  await page.evaluate(SIM_SCRIPT);
  if (process.env.OMEGAS_REVISION_MUTANT === '1') {
    // Mutação experimental: simula o defeito antigo (projeção em cache considerada pronta).
    await page.evaluate(() => { window.OmegasUi.AutoCalUxModel.projectionBehindTables = () => false; });
  }
  await page.evaluate(()=>{
   const indexes=Array.from({length:18},(_,i)=>i);
   window.__sim.acquire('gas',indexes,12);window.__sim.acquire('petrol',indexes,12);
  });
  await page.waitForFunction(()=>document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point').length===36,{timeout:15000,polling:70});
  await page.evaluate(()=>{
   const A=window.OmegasAutoCal;
   const original=A.getUiProjection.bind(A);
   const initial=JSON.parse(original());
   initial.transportTablesRevision=window.__ss.rev;
   let last=initial;
   window.__bridgeFake={pending:0,reads:0,staleReads:0,from:initial.transportTablesRevision,latest:0,ready:false};
   A.getUiProjection=()=>{
    const st=window.__bridgeFake;st.reads++;
    if(st.pending>0){st.pending--;st.staleReads++;return JSON.stringify(last);}
    const fresh=JSON.parse(original());fresh.transportTablesRevision=window.__ss.rev;
    st.latest=fresh.transportTablesRevision;last=fresh;
    return JSON.stringify(fresh);
   };
   window.__notify=()=>{
    window.__bridgeFake.pending=3;
    window.__bridgeFake.from=window.__bridgeFake.latest||initial.transportTablesRevision;
    window.__bridgeFake.staleReads=0;
    window.__bridgeFake.ready=false;
    window.OmegasOnRevision('tables',window.__ss.rev);
   };
  });
  const phase=async(name,modify,expected)=>{
    const since=Date.now();
    await page.evaluate(modify);
    await page.waitForFunction(expected,null,{timeout:2400,polling:35});
    const metrics=await page.evaluate(()=>({
      reads:window.__bridgeFake.reads,staleReads:window.__bridgeFake.staleReads,
      simulatedRevision:window.__ss.rev,projectionRevision:window.OmegasApp?.autoCalCockpit?.projection?.transportTablesRevision,
      points:[...document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point')].map(x=>x.dataset.autocalPointKey),
      zones:[...document.querySelectorAll('#autocalZoneMeter [data-autocal-zone-row]')].map(row=>({
        petrol:row.querySelector('[data-autocal-zone-petrol]')?.dataset.state,
        gas:row.querySelector('[data-autocal-zone-gas]')?.dataset.state
      })),
    }));
    const elapsed=Date.now()-since;
    result.steps.push({name,elapsedMs:elapsed,...metrics});
    await page.screenshot({path:path.join(out,name+'.png')});
    assert.equal(metrics.projectionRevision,metrics.simulatedRevision);
    assert.ok(metrics.staleReads>=3, 'simulador não introduziu 3 respostas anteriores');
    assert.ok(elapsed<1800,name+' demorou '+elapsed+'ms');
  };
  await phase('01-automatch-limpa-gnv',()=>{
    window.__sim.automatch();window.__notify();
  },()=>{
    const x=window.OmegasApp?.autoCalCockpit?.projection;
    return Number(x?.transportTablesRevision)===window.__ss.rev &&
      document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point.gas').length===0 &&
      document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point.petrol').length===18;
  });
  await phase('02-gnv-reaprendido',()=>{
    window.__sim.acquire('gas',Array.from({length:18},(_,i)=>i),14);window.__notify();
  },()=>{
    const x=window.OmegasApp?.autoCalCockpit?.projection;
    return Number(x?.transportTablesRevision)===window.__ss.rev &&
      document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point.gas').length===18;
  });
  await phase('03-reset-completo',()=>{
    window.__sim.clear();window.__notify();
  },()=>{
    const x=window.OmegasApp?.autoCalCockpit?.projection;
    return Number(x?.transportTablesRevision)===window.__ss.rev &&
      document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point').length===0;
  });
  const errors=o.errors.filter(x=>x.startsWith('pageerror:'));
  assert.equal(errors.length,0,'erros JS '+errors.join(';'));
  result.ok=true;
  console.log('OMEGASCINZA_REVISION_BRIDGE_PASS '+JSON.stringify(result.steps.map(x=>({name:x.name,ms:x.elapsedMs,stale:x.staleReads,points:x.points.length}))));
 }catch(err){result.ok=false;result.failures.push(String(err?.stack||err));console.error('OMEGASCINZA_REVISION_BRIDGE_FAIL '+result.failures.join('')) ;process.exitCode=1}
 finally {
  fs.writeFileSync(path.join(out,'metrics.json'),JSON.stringify(result,null,2));
  await o.browser.close();
 }
})().catch(e=>{console.error(e);process.exitCode=1;});
