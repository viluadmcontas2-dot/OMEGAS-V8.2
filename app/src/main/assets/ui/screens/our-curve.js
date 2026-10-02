(function(root){
 'use strict'; const ns=root.OmegasUi=root.OmegasUi||{};
 const number=v=>v!==null&&v!==undefined&&Number.isFinite(Number(v));
 const esc=v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function points(snapshot,dense){
   const ecu=(snapshot?.humanProjection?.acquisitionPoints||[]).filter(p=>['PETROL','GAS'].includes(p.fuel)&&p.positioned&&number(p.tPetrolMs)&&number(p.mapBar))
    .map(p=>({fuel:p.fuel,mapBar:Number(p.mapBar),tpetMs:Number(p.tPetrolMs),samples:null,rpmMedian:null,source:'ECU',valid:p.maturity==='ACQUIRED',index:p.bandIndex}));
   const own=['petrol','gas'].flatMap(lane=>(dense?.[lane]||[]).filter(p=>number(p.mapBar)&&number(p.tpetMs)&&number(p.samples)&&p.samples>0)
    .map(p=>({...p,fuel:lane==='petrol'?'PETROL':'GAS',source:'nosso',valid:true})));
   return ecu.concat(own);
 }
 function detail(p){return `${p.fuel==='PETROL'?'Gasolina':'GNV'} · MAP ${p.mapBar.toFixed(3)} bar · ${p.tpetMs.toFixed(2)} ms · ${p.samples===null?'amostras não informadas':p.samples+' amostras'} · ${number(p.rpmMedian)?Math.round(p.rpmMedian)+' rpm':'RPM não informado'} · ${p.source}`;}
 function html(snapshot,dense){
   const all=points(snapshot,dense); const own=all.some(p=>p.source==='nosso');
   const empty=own?'':'<p class="our-curve-empty">Ainda sem pontos próprios — rode na gasolina e no GNV</p>';
   if(!all.length)return empty+'<p>ECU ainda sem coordenadas de aquisição. Leia o snapshot conectado.</p>';
   const W=820,H=280,L=58,R=20,T=16,B=38;
   const xmax=Math.max(12,...all.map(p=>p.tpetMs))*1.05,ymax=Math.max(1,...all.map(p=>p.mapBar))*1.05;
   const x=v=>L+v/xmax*(W-L-R),y=v=>H-B-v/ymax*(H-T-B);
   let svg=`<svg class="our-curve-svg" viewBox="0 0 ${W} ${H}" role="img" aria-label="Tempo gasolina Tpet em ms × MAP em bar">`;
   for(let i=0;i<=4;i++){const vx=xmax*i/4,vy=ymax*i/4;svg+=`<path class="grid" d="M${x(vx)},${T}V${H-B} M${L},${y(vy)}H${W-R}"/><text x="${x(vx)}" y="${H-B+18}" text-anchor="middle">${vx.toFixed(1)}</text><text x="${L-8}" y="${y(vy)+4}" text-anchor="end">${vy.toFixed(2)}</text>`;}
   svg+=`<text x="${W/2}" y="${H-2}" text-anchor="middle">Tpet (ms)</text><text x="3" y="12">MAP (bar)</text>`;
   for(const fuel of ['PETROL','GAS']){
     const ecu=all.filter(p=>p.fuel===fuel).sort((a,b)=>a.mapBar-b.mapBar);
     if(ecu.length>1)svg+=`<polyline class="lane-${fuel}" fill="none" points="${ecu.map(p=>`${x(p.tpetMs)},${y(p.mapBar)}`).join(' ')}"/>`;
   }
   for(const p of all){const d=esc(detail(p));svg+=`<g role="button" tabindex="0" data-curve-detail="${d}" aria-label="${d}"><circle class="hit" cx="${x(p.tpetMs)}" cy="${y(p.mapBar)}" r="32"/><circle class="lane-${p.fuel} ${p.valid?'valid':'collecting'} ${p.source==='nosso'?'own':'ecu'}" cx="${x(p.tpetMs)}" cy="${y(p.mapBar)}" r="${p.source==='nosso'?3:6}"/><title>${d}</title></g>`;}
   return empty+svg+'</svg><div class="our-curve-legend"><span class="petrol">Gasolina</span><span class="gas">GNV</span><span>● ECU · pontos menores: nossos · afastamento GNV × gasolina</span></div><small title="nossas bandas: mediana das leituras estáveis a cada 0,025 bar">Nossas bandas: mediana das leituras estáveis a cada 0,025 bar · visualização</small>';
 }
 ns.OurCurvePlot={points,html,detail};
})(typeof window!=='undefined'?window:globalThis);
