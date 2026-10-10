'use strict';
const assert = require('node:assert/strict');
const { test } = require('node:test');
const { freshContext } = require('./_support.cjs');

test('AutoCal mostra Z1-Z4 por combustível sem depender do SVG ou dos limiares MAP', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const source = fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
  const rows = [...source.matchAll(/data-autocal-zone-row="([1-4])"/g)];
  assert.equal(rows.length, 4, 'as quatro zonas devem existir na árvore HTML até com gráfico vazio');
  assert.equal([...source.matchAll(/data-autocal-zone-petrol="([0-3])"/g)].length, 4);
  assert.equal([...source.matchAll(/data-autocal-zone-gas="([0-3])"/g)].length, 4);
  assert.ok(source.indexOf('id="autocalZoneMeter"') < source.indexOf('id="autocalReferenceChart"'),
    'estado independente do gráfico');
  assert.match(source,/zoneSurface\(snapshot = \{\}, human = \{\}\)/);
  assert.match(source,/lower: valid \? thresholds\[edges\[index\]\] : null/);
});

test('Refino aprende 100 regiões mas desenha curvas próprias e poucos marcadores', () => {
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
  const own = Array.from({length:60},(_,i)=>({petrolMs:1+i*.4,mapBar:.25+i*.008}));
  const r=chart.buildSvg({domain:{xMin:0,xMax:26,yMin:0.2,yMax:0.9},reference:[],ecu:[],betweenPoints:normalized,
    ownCurves:{regime:'DRIVING',petrol:own,gas:own.map(p=>({petrolMs:p.petrolMs*1.04,mapBar:p.mapBar}))}},
    {width:1000,height:380,mode:'between'});
  assert.match(r.svg,/data-own-curve="petrol"/);
  assert.match(r.svg,/data-own-curve="gas"/);
  assert.equal((r.svg.match(/data-chart-our=/g)||[]).length,0,'amostras ficam no motor, não no SVG');
  assert.equal((r.svg.match(/data-own-curve=/g)||[]).length,2,'somente curvas gasolina e GNV');
  assert.match(chart.describeBetween(normalized[99]),/região própria do OMEGAS/);
});

test('Refino não desenha a curva nativa quando faltam curvas próprias, e eixo segue região observada', () => {
  const chart = freshContext().OmegasUi.CurveChart;
  const native = [
    {index:0, petrolMs:2.5, petrolMapBar:0.35, gasMapBar:0.38, gasEquivalentMs:2.7},
    {index:1, petrolMs:22, petrolMapBar:1.05, gasMapBar:1.08, gasEquivalentMs:23},
  ];
  const model = {
    reference:native, ecu:[], betweenPoints:[], proposal:[], stalls:[],
    ownCurves:{regime:'DRIVING',petrol:[],gas:[]},
    domain:{xMin:0,xMax:24,yMin:0.2,yMax:1.15},
  };
  const svg = chart.buildSvg(model,{width:1280,height:400,mode:'between'}).svg;
  assert.doesNotMatch(svg, /class="autocal-reference-line (?:gas|petrol)(?: "|')/);
  assert.doesNotMatch(svg, /class="autocal-equivalence-line"/);
  assert.doesNotMatch(svg, /data-own-curve=/);
  assert.match(svg, /data-chart-live/, 'cursor continua na árvore com curva própria vazia');
  const measured = chart.focusDomain([], [], [
    {tpetMs:2.5,mapBar:0.3},{tpetMs:13.4,mapBar:0.9},
  ], {fullRange:false});
  assert.equal(measured.xMin, 0);
  assert.ok(measured.xMax > 13.4 && measured.xMax < 15,
    'eixo acompanha 13,4 ms medidos, não estica sozinho até 22 ms');
  const upper = chart.focusDomain([], [], [
    {tpetMs:2.5,mapBar:0.3},{tpetMs:22.7,mapBar:0.9},
  ], {fullRange:false});
  assert.ok(upper.xMax > 22.7, 'medições reais acima de 22 ms continuam visíveis');
});
