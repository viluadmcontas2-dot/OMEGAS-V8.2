'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');

const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const router = read('app/src/main/assets/ui/core/router.js');
const index = read('app/src/main/assets/ui/index.html');
const app = read('app/src/main/assets/ui/app.js');
const api = read('app/src/main/assets/ui/core/autocal-api.js');
const cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js');
const monitor = read('app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt');
const protocol = read('app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt');

assert.match(router, /ROUTES\s*=\s*\[[^\]]*['"]autocal['"]/s,
  'AutoCal precisa ser uma rota top-level');
assert.ok(index.indexOf('data-route="autocal"') >= 0, 'nav AutoCal ausente');
assert.ok(index.indexOf('data-route="autocal"') < index.indexOf('data-route="obd"'),
  'AutoCal precisa ficar imediatamente antes de OBD');
assert.match(index, /data-screen="autocal"/, 'AutoCal precisa ter screen própria');
assert.match(app, /autocal:\s*\[['"]AUTO-CAL['"],\s*['"]AutoCal['"]\]/,
  'shell precisa ter metadata própria do AutoCal');
assert.match(api, /readerStatus:\s*\(\)\s*=>\s*invoke\(['"]getStatus['"]/,
  'status da leitura manual deve observar o reader que startRead inicia');
assert.match(api, /readerSnapshot:\s*\(\)\s*=>\s*invoke\(['"]getSnapshot['"]/,
  'snapshot manual deve observar o reader correto');
assert.match(api, /cancelRead:\s*\(\)\s*=>\s*invoke\(['"]cancelRead['"]/,
  'leitura precisa poder ser cancelada pela UI');
assert.match(api, /acquisitionStatus:\s*\(\)\s*=>\s*invoke\(['"]getNativeMonitorStatus['"]/,
  'estado de aquisição nativa precisa ser explícito');
assert.match(api, /acquisitionSnapshot:\s*\(\)\s*=>\s*invoke\(['"]getNativeMonitorSnapshot['"]/,
  'snapshot de aquisição nativa precisa ser explícito');

assert.equal(cockpit.includes('data-curve-view="autocal"'), false,
  'AutoCal final não pode voltar a ser subview da Curva K');
assert.equal(cockpit.includes('data-curve-panel="autocal"'), false,
  'painel AutoCal não pode ser injetado dentro da Curva K');
assert.match(cockpit, /route === ['"]autocal['"]/,
  'cockpit deve acompanhar a rota AutoCal');
assert.match(cockpit, /data-autocal-cancel-read/,
  'leitura em andamento precisa oferecer cancelamento');
assert.match(cockpit, /readerStatus\(\)/,
  'cockpit precisa consultar o estado do reader manual');
assert.match(cockpit, /readerSnapshot\(\)/,
  'cockpit precisa consultar o snapshot do reader manual');
assert.match(cockpit, /livePoint\(/,
  'modelo deve separar cursor vivo da evidência adquirida');
assert.match(cockpit, /autocal-live-point/,
  'gráfico deve ter camada visual própria para o cursor AGORA');
assert.match(cockpit, />AGORA</,
  'cursor vivo precisa ser rotulado para o operador');
assert.equal(cockpit.includes('>Ajustar</button>'), false,
  'fit visual não pode parecer ajuste da ECU');
assert.match(cockpit, />Ver tudo</,
  'fit deve ter linguagem visual inequívoca');

assert.equal(cockpit.includes("'<span>B' +"), false,
  'Bxx não pode ser rótulo primário das regiões');
assert.match(cockpit, /Região\s+['"]?\s*\+?\s*\(?.*index/s,
  'faixas devem ser apresentadas como regiões humanas');

assert.match(monitor, /snapshotRequested\s*=\s*newSessionId\s*>\s*0L/,
  'sessão conectada deve agendar bootstrap snapshot após settle');
assert.match(monitor, /snapshotReason\s*=\s*if\s*\(newSessionId\s*>\s*0L\)\s*["']SESSION_BOOTSTRAP["']/,
  'bootstrap precisa ter motivo auditável');

assert.match(protocol, /NUM_AUTOMATCH_EXECUTED[^\n]+U8_OR_U16_LE/,
  'contador AutoMatch deve manter contrato U8/U16');
assert.match(protocol, /payload\.size\s*==\s*1\s*\|\|\s*payload\.size\s*==\s*2/,
  'decoder deve aceitar payload histórico de 1 byte e firmware de 2 bytes');

console.log('AUTOCAL_FINAL_PRODUCT_CONTRACT=PASS');

assert.match(
  protocol,
  /val NUM_AUTOMATCH_EXECUTED = Field\("NUM_AUTOMATCH_EXECUTED", 0x0174, Encoding\.U8_OR_U16_LE, Shape\.SCALAR, 1\)/,
  'contrato exato do contador AutoMatch precisa continuar U8/U16'
);
assert.match(cockpit, /const liveMarkup = live/, 'camada AGORA precisa depender da telemetria viva');
assert.match(cockpit, /if \(!live\) \{\s*if \(layer\) layer\.setAttribute\('display', 'none'\);/s,
  'telemetria inválida precisa esconder cursor AGORA antigo');
assert.match(cockpit, /state === 'CANCEL_REQUESTED'/, 'cancelamento intermediário precisa de estado humano explícito');
assert.match(cockpit, /const nativeSnapshot = state\.latestSnapshot\?\.fields \? state\.latestSnapshot : \{\};/,
  'estado de aquisição deve vir do monitor nativo');
assert.match(cockpit, /const enabled = finite\(state\.autoCalEnabled \?\? nativeSnapshot\.autoCalEnabled \?\? scalarValue\(nativeSnapshot, 'AUTO_CAL_ENABLE'\)\);/,
  'snapshot manual não pode ser autoridade de enable/pause');
const inspectBandBlock = cockpit.slice(cockpit.indexOf('inspectBand(index)'), cockpit.indexOf('renderEvents(events)'));
assert.equal(/contador|limiar/.test(inspectBandBlock), false,
  'contador/limiar são RAW técnico e não podem vazar no inspetor humano');
