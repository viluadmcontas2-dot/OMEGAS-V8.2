'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const model=require(path.join(__dirname,'../app/src/main/assets/ui/core/learning-model.js'));
const kind=process.argv[2];
const cases={
  'grid-regular':{comparisons:120,rounds:1600},
  'grid-heavy':{comparisons:1500,rounds:300},
  'autocal-reference':{rounds:30000},
  'autocal-band-strip':{rounds:30000},
};
const cfg=cases[kind];
if(!cfg)throw Error('Unknown scenario: '+kind);
const n=Array.from({length:18},(_,i)=>i);
let checksum=0;
if(kind.startsWith('grid-')){
  const cells=[];
  for(let row=0;row<12;row++)for(let column=0;column<12;column++){
    cells.push({row,column,fuel:'PETROL',samples:32,confidence:.83});
    cells.push({row,column,fuel:'CNG',epoch:3,samples:24,confidence:.79});
  }
  const comparisons=Array.from({length:cfg.comparisons},(_,i)=>{
    const row=i%12,column=Math.floor(i/12)%12;
    return {row,column,continuous_cell_weights:[
      {row,column,weight:.49},
      {row,column:(column+1)%12,weight:.21},
      {row:(row+1)%12,column,weight:.21},
      {row:(row+1)%12,column:(column+1)%12,weight:.09},
    ]};
  });
  const input={epoch:3,grid:{rows:12,columns:12,
    rpmBins:Array.from({length:12},(_,i)=>750+i*480),
    petrolBins:Array.from({length:12},(_,i)=>1.25+i*.5)},
    cells,comparisons};
  for(let i=0;i<cfg.rounds;i++){
    const result=model.buildModel(input);
    if(result.cells.length!==144)throw Error('Invalid grid shape');
    checksum+=result.counts.comparable+result.counts.ready;
  }
}else{
  // Isolate DOM-free AutoCalUxModel; prevent screen boot only in this test VM.
  const context={OmegasUi:{},setTimeout:()=>undefined};
  const source=fs.readFileSync(path.join(__dirname,
    '../app/src/main/assets/ui/screens/autocal-cockpit.js'),'utf8');
  vm.runInNewContext(source,context,{filename:'autocal-cockpit.js'});
  const ux=context.OmegasUi.AutoCalUxModel;
  if(!ux||typeof ux.referenceFingerprint!=='function'||typeof ux.bandStrip!=='function')
    throw Error('AutoCalUxModel unavailable');
  const phys=(key,values)=>({key,status:'VALID',physicalValues:values,rawValues:values});
  const raw=(key,values)=>({key,status:'VALID',rawValues:values});
  const snapshot={fields:[
    phys('MNFLD_PRESS_THD',n.map(i=>.15+i*.04)),
    phys('PETR_INJ_TBP',n.map(i=>1.5+i*.22)),
    phys('PETR_MNFLD_PRESS_RV',n.map(i=>.21+i*.035)),
    phys('GAS_MNFLD_PRESS_RV',n.map(i=>.22+i*.036)),
    phys('PETR_INJ_TBUF_GAS',n.map(i=>1.7+i*.21)),
    phys('MNFLD_PRESS_BUF_GAS',n.map(i=>.23+i*.032)),
    raw('NUM_BUF_UPD_GAS',n.map(i=>20+i)),
    raw('ACQUIRED_ZONES_GAS',[1,0,1,0]),
  ]};
  if(kind==='autocal-reference'){
    const analysis={points:n.map(index=>({index,gasEquivalentTimeMs:1.7+index*.2}))};
    for(let i=0;i<cfg.rounds;i++){
      const value=ux.referenceFingerprint(snapshot,analysis);
      if(!value)throw Error('Missing reference fingerprint');
      checksum+=value.length;
    }
  }else{
    const projection={
      acquisitionZones:{gas:[true,false,true,false]},
      correlation:[{bandIndex:2,correlationState:'CORRELATED'}],
      correlationState:{correlatedBands:[2,5],retryableBands:[8]},
    };
    for(let i=0;i<cfg.rounds;i++){
      const bands=ux.bandStrip(snapshot,projection);
      if(bands.length!==18)throw Error('Invalid AutoCal band count');
      checksum+=bands.length+bands[2].counter;
    }
  }
}
if(!Number.isFinite(checksum)||checksum<=0)throw Error('Invalid benchmark result');
process.stdout.write('BENCHMARK_OK '+kind+' checksum='+checksum+'\n');
