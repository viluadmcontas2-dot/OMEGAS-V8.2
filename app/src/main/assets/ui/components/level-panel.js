(function(root){
 'use strict';const ns=root.OmegasUi=root.OmegasUi||{};
 const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function html(data){const level=data.levelSensor||{},p=level.percent;const valid=p!==null&&p!==undefined&&Number.isFinite(Number(p));
 return `<section class="level-observation"><header><b>Sensor de nível</b><button type="button" data-level-read class="secondary" ${level.state==='READING'?'disabled':''}>${level.state==='READING'?'Lendo…':'Ler parâmetros da ECU'}</button></header>
 <p>${esc(level.label||'Sem leitura atual')} · ${valid?`${esc(p)}/100 · proxy, sem volume`:'aguardando leitura'}</p>
 ${valid?`<progress max="100" value="${Number(p)}" aria-label="Proxy de nível"></progress>`:''}
 <p>Referências, cheio e regras do filtro/LEDs não comprovados. Sem litros ou LEDs estimados.</p>
 ${level.error?`<p>${esc(level.error)}</p>`:''}
 <details><summary>Bytes e estado da leitura</summary><pre>${esc(JSON.stringify(level,null,2))}</pre></details></section>`;}
 function scoreHtml(score={},compact=false){const c=score.current,p=score.previous;const fmt=v=>v===null||v===undefined?'indisponível':Number(v).toLocaleString('pt-BR',{maximumFractionDigits:2});const delta=score.gasPerAirChangePercent;return `<section class="calibration-score"><b>Esta calibração × anterior</b><p>${c?`${esc(c.target)} · ${fmt(c.km)} km GNV`:'Aguardando gravação manual confirmada'} · ${delta===null||delta===undefined?'dados insuficientes':`gás por ar ${fmt(delta)}%`}</p>${compact?'':`<p>Esta: ${fmt(c?.gasPerAir)} gás/ar · anterior: ${fmt(p?.gasPerAir)} gás/ar. Queda filtrada: ${fmt(c?.levelDrop)} · km/degrau: ${fmt(c?.kmPerStep)}.</p><small>Comparação exige 5 km em cada calibração. Janela operacional mínima para reduzir efeito de trechos curtos; não comprova economia. Sem perfil comprovado, queda e km/degrau ficam indisponíveis.</small>`}</section>`;}
 function render(s){if(s.route!=='tools'&&s.route!=='dashboard')return;const screen=root.document?.querySelector(`[data-screen="${s.route}"]`);if(!screen)return;
 let host=screen.querySelector('[data-level-panel]');if(!host){host=root.document.createElement('div');host.dataset.levelPanel='';screen.appendChild(host);host.addEventListener('click',e=>{const b=e.target.closest('[data-level-read]');if(!b)return;b.disabled=true;b.textContent='Leitura solicitada…';const r=root.OmegasApp?.api?.startLevelSensorRead?.();if(!r?.started){b.disabled=false;b.textContent=r?.error||'Leitura indisponível';}});}
 const signature=JSON.stringify([s.telemetry?.levelSensor,s.telemetry?.calibrationScoreboard,s.status?.usbConnected]);if(host.dataset.signature===signature)return;host.dataset.signature=signature;host.innerHTML=(s.route==='tools'?html(s.telemetry||{}):'')+scoreHtml(s.telemetry?.calibrationScoreboard,s.route==='dashboard');
 const b=host.querySelector('[data-level-read]');if(b&&s.status?.usbConnected!==true)b.disabled=true;}
 ns.LevelPanel={html,scoreHtml,render};
})(typeof window!=='undefined'?window:globalThis);
