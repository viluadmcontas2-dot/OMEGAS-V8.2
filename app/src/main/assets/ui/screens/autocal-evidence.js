(function(root){
 'use strict';const ns=root.OmegasUi=root.OmegasUi||{};
 const n=v=>v!=null&&Number.isFinite(Number(v))?Number(v):null;
 const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const fmt=(v,k=1)=>n(v)===null?'não informado':Number(v).toLocaleString('pt-BR',{maximumFractionDigits:k});
 function steepest(points){let best=null;for(let i=1;i<(points||[]).length;i++){const a=points[i-1],b=points[i];if(!(a.referenceTimeMs>0&&b.referenceTimeMs>a.referenceTimeMs&&a.currentFactor>0&&b.currentFactor>0))continue;const e=Math.abs(Math.log(b.currentFactor/a.currentFactor)/Math.log(b.referenceTimeMs/a.referenceTimeMs));if(!best||e>best.e)best={from:a.referenceTimeMs,to:b.referenceTimeMs,e};}return best;}
 function html(analysis,eq,snapshot,state){
  const a=analysis||{},p=eq?.autopilot||{},span=steepest(a.points),rejected=a.rejectedBands||[];
  const diag=a.joltRiskBefore==='LOW'?'Curva sem degrau importante na análise':a.joltRiskBefore&&span?`Degrau com risco de tranco em ${fmt(span.from)}–${fmt(span.to)} ms`:'Curva ainda sem análise — solicite o snapshot';
  const warning=rejected.length>0||['ATTENTION','HIGH'].includes(a.joltRiskBefore)||p.autoCalEnabled===false||state?.ok===false;
  const missing=(p.bandsMissing||[]).map(b=>{const t=(eq?.typicalBands||[]).find(t=>t.fromMs===b.fromMs&&t.toMs===b.toMs);return `<li>Falta ${fmt(b.fromMs)}–${fmt(b.toMs)} ms: ${t&&t.samples>0&&n(t.rpmMedian)!==null&&n(t.mapMedian)!==null?`costuma ser ~${fmt(t.rpmMedian,0)} rpm, ${fmt(t.mapMedian,2)} bar`:'ainda sem referência — rode na gasolina'}</li>`;}).join('');
  return `<section class="autocal-evidence" data-tone="${warning?'warn':'ok'}"><b>${esc(diag)}</b><button type="button" data-autocal-refino>Ver correção no Refino</button><p>Base: MAP × Tpet da ECU. Nossos pares RPM × MAP complementam o refino.</p><p>Automático ${fmt(p.autoMatchCount,0)} / ${fmt(p.maxAutomatch,0)} · Gasolina ${fmt(p.petrolValid,0)}/18 · GNV ${fmt(p.gasValid,0)}/18</p>${state?.ok===false?'<p class="problem">Leitura falhou: a aquisição não pode ser avaliada. Confira o cabo e solicite outro snapshot.</p>':''}${p.autoCalEnabled===false?'<p class="problem">AutoCal desligado: a ECU não coleta novas bandas. Confira se a coleta terminou antes de habilitar novamente.</p>':''}${rejected.length?`<div class="problem"><b>Bandas incoerentes</b>${rejected.map(b=>`<p>${esc(b.fuel)} · MAP ${fmt(b.mapBar,3)} bar · ${fmt(b.timeMs,2)} ms: fora da curva física — ignorada no refino</p>`).join('')}<p>Rode novamente nessas cargas e confira a aquisição antes de revisar.</p></div>`:''}${missing?`<details><summary>O que falta coletar</summary><ul>${missing}</ul></details>`:''}</section>`;
 }
 ns.AutoCalEvidence={html,steepest};
})(typeof window!=='undefined'?window:globalThis);
