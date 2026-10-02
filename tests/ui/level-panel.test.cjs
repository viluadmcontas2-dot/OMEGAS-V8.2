const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
function moduleUnderTest(){const c={};vm.runInNewContext(fs.readFileSync('app/src/main/assets/ui/components/level-panel.js','utf8'),c);return c.OmegasUi.LevelPanel;}
test('missing level has no zero or fabricated volume',()=>{const h=moduleUnderTest().html({levelSensor:{percent:null}});assert.match(h,/aguardando leitura/);assert.doesNotMatch(h,/<progress/);});
test('actual zero stays visible and raw unknown metadata remains explicit',()=>{const h=moduleUnderTest().html({levelSensor:{percent:0,label:'Proxy',fields:[{label:'não decodificado',rawHex:'AB'}]}});assert.match(h,/value="0"/);assert.match(h,/proxy, sem volume/);assert.match(h,/não decodificado/);assert.match(h,/AB/);});
test('read-only integration never requests SC313 index one or writes sensor',()=>{const s=fs.readFileSync('app/src/main/java/com/omegas/prohub/telemetry/LevelSensorSnapshot.kt','utf8');assert.match(s,/313.*index=0/);assert.doesNotMatch(s,/AutoCalProtocol.write/);assert.match(s,/"NOT_READ"/);});

test('scoreboard gates comparison and missing level stays unknown',()=>{const h=moduleUnderTest().scoreHtml({current:{target:'CURVE_K',km:2,levelDrop:null},gasPerAirChangePercent:null});assert.match(h,/dados insuficientes/);assert.match(h,/Queda filtrada: indisponível/);assert.match(h,/5 km/);});
