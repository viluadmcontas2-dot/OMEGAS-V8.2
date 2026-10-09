'use strict';
const assert = require('node:assert/strict');
const { test } = require('node:test');
const { freshContext } = require('./_support.cjs');

test('AutoCal desenha sempre Z1-Z4 mesmo com Z3 e Z4 fora do recorte MAP', () => {
  const ctx = freshContext();
  const chart = ctx.OmegasUi.CurveChart;
  const zones = [
    {zone:1,lower:0.20,upper:0.35,petrolState:'acquired',gasState:'acquired'},
    {zone:2,lower:0.35,upper:0.52,petrolState:'acquired',gasState:'missing'},
    {zone:3,lower:0.52,upper:0.85,petrolState:'missing',gasState:'acquired'},
    {zone:4,lower:0.85,upper:1.13,petrolState:'missing',gasState:'missing'}
  ];
  const r = chart.buildSvg({domain:{xMin:0,xMax:14,yMin:0.2,yMax:0.5},zones,reference:[],ecu:[]},
    {width:1000,height:360,mode:'ecu18'});
  assert.equal((r.svg.match(/data-autocal-zone-rail=/g) || []).length, 4);
  for(let i=1;i<=4;i++) assert.match(r.svg, new RegExp('data-autocal-zone-rail="'+i+'"'));
  assert.match(r.svg, /data-state="missing"/);
  assert.match(r.svg, /data-state="acquired"/);
  assert.match(r.svg, /P:F/);
  assert.match(r.svg, /G:F/);
});

test('Refino não corta a representação depois de 17/36 regiões, nem acima de 13 ms', () => {
  const chart = freshContext().OmegasUi.CurveChart;
  const list = Array.from({length:100}, (_,i) => ({
    kind:'local',index:i,fromMs:i*0.25,toMs:(i+1)*0.25,centerMs:i*0.25+0.125,
    centerMapBar:0.48, state:i%2 ? 'aprendendo':'coletado',
    gas:{ms:i*0.25+0.15,mapBar:0.48,n:10},
    petrol:{ms:i*0.25+0.125,mapBar:0.48,n:10}
  }));
  const normalized=chart.normalizeBetween(list);
  assert.equal(normalized.length,100);
  assert.equal(normalized[99].kind,'local');
  assert.equal(normalized[99].state,'missing');
  const r=chart.buildSvg({domain:{xMin:0,xMax:26,yMin:0.2,yMax:0.9},reference:[],ecu:[],betweenPoints:normalized},
    {width:1000,height:380,mode:'between'});
  assert.match(r.svg,/data-chart-our="b:99"/);
  assert.match(r.svg,/learning/);
  assert.match(chart.describeBetween(normalized[99]),/região própria do OMEGAS/);
});
