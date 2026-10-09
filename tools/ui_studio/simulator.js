/* Driving and ECU responses for the visual workshop only. Never loaded by the APK. */
(() => {
'use strict';
const bridge=window.OmegasAutoCal,native=window.OmegasNative;
if(!bridge||!native)return;
const read=JSON.parse(bridge.getUiProjection());
const snap=JSON.parse(JSON.stringify(read.snapshot));
const original=JSON.parse(JSON.stringify(snap));
const epoch={petrolGeneration:1,gasGeneration:1,petrolPending:false,gasPending:false};
const sim={revision:1,enabled:true,action:{state:'IDLE',busy:false},fuel:'GNV',speed:1,progress:{GAS:0,PETROL:18},elapsed:0};
const field=(s,k)=>s.fields.find(f=>f.key===k);
function set(key,values,scale=1){let f=field(snap,key);if(!f){f={key,status:'VALID'};snap.fields.push(f)}f.rawValues=values.slice();f.physicalValues=values.map(v=>v/scale);f.capturedAtMs=Date.now();}
function keys(fuel){return fuel==='GAS'?['PETR_INJ_TBUF_GAS','MNFLD_PRESS_BUF_GAS','NUM_BUF_UPD_GAS','ACQUIRED_ZONES_GAS']:['PETR_INJ_TBUF','MNFLD_PRESS_BUF','NUM_BUF_UPD_PETR','ACQUIRED_ZONES_PETROL'];}
function populate(fuel,count){const [x,y,n,z]=keys(fuel);for(const [key,scale] of [[x,512],[y,1024],[n,1]]){const values=field(original,key).rawValues;set(key,values.map((v,i)=>i<count?(v||Math.round((i+1)*(key===x?240:key===y?55:1))):0),scale)}set(z,[0,1,2,3].map(i=>count>=(i+1)*4?1:0));}
if(window.__SCN?.workshopDynamic){populate('GAS',0);set('NUM_AUTOMATCH_EXECUTED',[0]);}
set('AUTO_CAL_ENABLE',[1]);
function projection(){const p=JSON.parse(bridgeOriginal.projection());return Object.assign(p,{revision:sim.revision,sessionId:'studio-simulated-session',snapshot:snap,nativeSnapshot:snap,nativeStatus:{ok:true,state:sim.enabled?'RUNNING':'PAUSED',enabled:sim.enabled,latestSnapshot:snap},liveAcquisitionEpoch:{...epoch},acquisitionZones:{gas:field(snap,'ACQUIRED_ZONES_GAS').rawValues.map(Boolean),petrol:field(snap,'ACQUIRED_ZONES_PETROL').rawValues.map(Boolean)}});}
const bridgeOriginal={projection:bridge.getUiProjection.bind(bridge)};
bridge.getUiProjection=()=>JSON.stringify(projection());
bridge.getNativeMonitorSnapshot=()=>JSON.stringify(snap);
bridge.getNativeMonitorStatus=()=>JSON.stringify(projection().nativeStatus);
bridge.getSnapshot=()=>JSON.stringify(snap);
bridge.getNativeActionStatus=()=>JSON.stringify(sim.action);
let prepared=null,sequence=0;
function prepare(action,targets=[]){if(sim.action.busy)return JSON.stringify({ok:false,message:'Uma operação simulada já está em andamento.'});prepared={id:'studio-'+(++sequence),action,targets};return JSON.stringify({ok:true,prepared:true,preparationId:prepared.id,action,label:action,description:'ECU simulada',commandHex:'SIMULATED',sessionId:'studio-simulated-session'});}
bridge.prepareNativeAction=action=>prepare(action);
bridge.preparePointDelete=(fuel,index)=>prepare('DELETE_POINT',[{fuel,index:Number(index)}]);
bridge.preparePointDeleteBatch=targets=>prepare('DELETE_POINT',JSON.parse(targets));
bridge.clearNativeActionPreparation=()=>{prepared=null;return JSON.stringify({ok:true})};
bridge.executeNativeAction=id=>{
 if(!prepared||prepared.id!==id||sim.action.busy)return JSON.stringify({ok:false,message:'Preparação inválida.'});
 const operation=prepared;prepared=null;
 sim.action={action:operation.action,state:'WRITING',busy:true,message:'Pedido recebido pela ECU simulada'};
 setTimeout(()=>{sim.action={...sim.action,state:'READING_AFTER',message:'Conferindo a leitura da ECU simulada'}},450);
 setTimeout(()=>{
  if(operation.action.startsWith('RESET_')){
   const fuels=operation.action==='RESET_ALL'?['GAS','PETROL']:[operation.action==='RESET_PETROL'?'PETROL':'GAS'];
   for(const fuel of fuels){sim.progress[fuel]=0;populate(fuel,0);const prefix=fuel==='GAS'?'gas':'petrol';epoch[prefix+'Generation']++;epoch[prefix+'Pending']=true;}
   sim.elapsed=0;sim.enabled=true;set('AUTO_CAL_ENABLE',[1]);set('NUM_AUTOMATCH_EXECUTED',[0]);
  }else if(operation.action==='DELETE_POINT'){
   for(const target of operation.targets){const fuel=/PETROL|PETR|GASOLINA/i.test(target.fuel)?'PETROL':'GAS';const [x,y,n]=keys(fuel);for(const key of [x,y,n]){const f=field(snap,key);const values=f.rawValues.slice();if(target.index>=0&&target.index<values.length)values[target.index]=0;set(key,values,key===x?512:key===y?1024:1)}sim.progress[fuel]=Math.min(sim.progress[fuel],Math.max(0,target.index));}
  }
  sim.revision++;sim.action={action:operation.action,state:'CONFIRMED',busy:false,ok:true,readbackValid:true,message:'Leitura confirmada pela ECU simulada',details:{targets:operation.targets,finishedAtMs:Date.now()}};
 },1200);
 return JSON.stringify({ok:true,started:true,action:operation.action});
};
bridge.setAcquisitionEnabled=enabled=>{sim.enabled=enabled===true||enabled==='true';set('AUTO_CAL_ENABLE',[sim.enabled?1:0]);sim.revision++;return JSON.stringify({ok:true,enabled:sim.enabled})};
bridge.startRead=()=>JSON.stringify({ok:true,started:true});bridge.cancelRead=()=>JSON.stringify({ok:true});
const rawPresent=native.getPresentSnapshot.bind(native);let last=performance.now(),smoothed=null;
function present(){const p=JSON.parse(rawPresent());if(!p.data?.live)return p;const now=performance.now(),alpha=1-Math.exp(-(now-last)/900);last=now;const live=p.data.live;if(sim.manualFuel)live.fuel=sim.manualFuel;if(!smoothed)smoothed={...live};for(const k of ['rpm','petrol_ms','gas_ms_diagnostic','load_bar']){if(Number.isFinite(live[k]))smoothed[k]+=alpha*(live[k]-smoothed[k]);live[k]=k==='rpm'?Math.round(smoothed[k]):smoothed[k]}
 sim.fuel=live.fuel;return p;}
native.getPresentSnapshot=()=>JSON.stringify(present());
native.getPresentSnapshotIfChanged=()=>JSON.stringify({...present(),changed:true});
native.getLiveTelemetry=()=>JSON.stringify(present().data);
native.getFullEngineSnapshot=()=>JSON.stringify(present().data);
const rawStatus=native.getStatus.bind(native);
native.getStatus=()=>{const s=JSON.parse(rawStatus()),p=present();if(p.data?.live&&s.usbConnected){const f=p.data.live;Object.assign(s,{fuelState:f.fuel,rpm:f.rpm,petrolMs:f.petrol_ms,gasMs:f.gas_ms_diagnostic,mapBar:f.load_bar})}return JSON.stringify(s)};
let tick=performance.now();
setInterval(()=>{const now=performance.now(),dt=now-tick;tick=now;if(!window.__SCN?.workshopDynamic||!sim.enabled||sim.action.busy||window.__MODE==='disconnected')return;sim.elapsed+=dt*sim.speed;
 if(sim.elapsed<4000)return;sim.elapsed=0;const fuel=sim.fuel==='GNV'?'GAS':'PETROL';if(sim.progress[fuel]>=18)return;
 sim.progress[fuel]++;populate(fuel,sim.progress[fuel]);const prefix=fuel==='GAS'?'gas':'petrol';epoch[prefix+'Pending']=false;sim.revision++;
},200);
window.__workshopSimulator={state:sim,snapshot:()=>JSON.parse(JSON.stringify(snap)),setFuel:value=>{sim.manualFuel=value;sim.fuel=value;},setSpeed:value=>{sim.speed=Math.max(.25,Math.min(3,Number(value)||1));}};
})();
