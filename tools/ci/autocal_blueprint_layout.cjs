
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),assert=require('node:assert/strict');
const {execFileSync}=require('child_process');
const BASELINE='7a8fb479a342eca6aabf6296e33ffc86ce071658';
fs.mkdirSync('build/ui-evidence',{recursive:true});
(async()=>{
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
try {
async function render(cssRef, height=672){
const page=await browser.newPage({viewport:{width:1280,height}});
const ui=path.resolve('app/src/main/assets/ui');
let html=fs.readFileSync(path.join(ui,'index.html'),'utf8').replace(/<script[\s\S]*?<\/script>/g,'').replace(/<link[^>]*>/g,'');
await page.setContent(html);
for(const f of ['styles.css','styles-calibration-obd.css','styles-autocal-cockpit.css']) await page.addStyleTag({content:cssRef && f==='styles-autocal-cockpit.css' ? execFileSync('git',['show',cssRef+':app/src/main/assets/ui/'+f],{encoding:'utf8'}) : fs.readFileSync(path.join(ui,f),'utf8')});
await page.evaluate(()=>{
document.querySelector('.app-shell').classList.add('autocal-focus');
document.querySelectorAll('[data-screen]').forEach(n=>n.classList.toggle('active',n.dataset.screen==='autocal'));
window.setTimeout=()=>0;
});
await page.addScriptTag({content:fs.readFileSync(path.join(ui,'screens/autocal-cockpit.js'),'utf8')});
await page.evaluate(()=>{
const fields=[['PETR_INJ_TBP',[2,5,10]],['PETR_MNFLD_PRESS_RV',[.3,.6,1]],['GAS_MNFLD_PRESS_RV',[.32,.65,1.05]],['MNFLD_PRESS_THD',Array.from({length:18},(_,i)=>.15+i/17)],['ACQUIRED_ZONES_GAS',[1,1,1,1]],['ACQUIRED_ZONES_PETROL',[1,1,1,1]],['AUTO_CAL_ENABLE',[1]]].map(([key,v])=>({key,status:'VALID',physicalValues:v,rawValues:v}));
window.projection={ok:true,sessionId:1,referenceUsable:true,snapshot:{available:true,snapshotHash:'first',fields},analysis:{},nativeStatus:{autoCalEnabled:1},nativeSnapshot:{available:true,fields},acquisitionZones:{gas:[true,true,true,true],petrol:[true,true,true,true]}};
window.alerts=[];
window.OmegasUi.AutoCalApi={available:()=>true,projection:()=>window.projection,actionStatus:()=>({}),sessionStatus:()=>({}),prepare:()=>({ok:true,prepared:true,preparationId:'p',label:'Readquirir gasolina'}),execute:()=>({}),setAcquisitionEnabled:()=>({})};
window.cockpit=new window.OmegasUi.AutoCalCockpit({store:{get:()=>({route:'autocal',telemetry:{valid:true,ageMs:0,live:{petrol_ms:5,map_bar:.6,rpm:900,fuel:'GASOLINA'}}}),patch:a=>window.alerts.push(a)},scheduler:{addHook:()=>()=>{}}});
window.cockpit.refresh();
});
const result=await page.evaluate(()=>{
const box=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height}};
return {viewport:innerWidth,height:innerHeight,
toolbar:box(document.querySelector('.autocal-focus-toolbar')),
title:box(document.querySelector('.autocal-focus-title')),
metrics:box(document.querySelector('.autocal-focus-metrics')),
chart:box(document.querySelector('.autocal-chart-host')),
inspector:box(document.querySelector('.autocal-chart-inspector')),
history:box(document.querySelector('[data-autocal-history'])),
actions:[...document.querySelectorAll('.autocal-focus-actions > button,.autocal-focus-actions > details > summary')].map(e=>({text:e.textContent,...box(e)}))};
});
await page.screenshot({path:'build/ui-evidence/'+(cssRef ? (cssRef===BASELINE?'before-overlap':'before-clipping') : 'after-'+height)+'.png'});
await page.close();return result;
}
const before=await render(BASELINE);
const clipped=await render('67ceed128c2bf65f3554b09366e8cddd73a94ac2');
const after=await render(null), fullHeight=await render(null,720);
const inside=r=>r.actions.every(a=>a.x>=r.toolbar.x-1&&a.right<=r.toolbar.right+1&&a.right<=r.viewport+1&&a.height>=48);
const overlaps=(a,b)=>a.x<b.right-1&&a.right>b.x+1&&a.y<b.bottom-1&&a.bottom>b.y+1;
const clear=r=>r.actions.every(a=>!overlaps(a,r.metrics)&&!overlaps(a,r.title));
const visibleInspector=r=>r.inspector.y>=r.chart.bottom&&r.inspector.bottom<=r.height;
console.log(JSON.stringify({before,clipped,after,fullHeight},null,2));
fs.writeFileSync('build/ui-evidence/geometry.json',JSON.stringify({before,clipped,after,fullHeight},null,2));
assert.equal(clear(before),false,'baseline must reproduce controls overlapping telemetry');
assert.equal(visibleInspector(clipped),false,'prior toolbar fix must reproduce clipped point information');
assert.equal(inside(after),true,'all primary controls must fit the viewport and have touch targets >=48px');
for(const result of [after,fullHeight]) {
assert.equal(inside(result),true,'primary touch targets must fit the toolbar');
assert.equal(clear(result),true,'primary controls must not cover telemetry or title');
assert.equal(visibleInspector(result),true,'point information must remain below graph and inside viewport');
assert.ok(result.chart.height>=420,'graph must remain dominant');
assert.ok(result.history.y>=result.chart.bottom,'comparison control must not overlap the plot');
assert.ok(result.history.height>=48,'comparison control must have a full touch target');
}
} finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1;});