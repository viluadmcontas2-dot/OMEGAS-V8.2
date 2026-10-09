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
