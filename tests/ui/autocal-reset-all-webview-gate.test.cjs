'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const bridge = fs.readFileSync(path.join(root, 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt'), 'utf8');
const nativeManager = fs.readFileSync(path.join(root, 'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt'), 'utf8');

function methodBody(start, end) {
  const from = bridge.indexOf(start);
  assert.notEqual(from, -1, 'método bridge ausente: ' + start);
  const to = bridge.indexOf(end, from + start.length);
  assert.notEqual(to, -1, 'limite do método ausente: ' + end);
  return bridge.slice(from, to);
}

test('P0: nenhuma UI do AutoCal expõe RESET_ALL ou um botão de reset geral', () => {
  assert.doesNotMatch(cockpit, /data-autocal-action\s*=\s*["']RESET_ALL["']/,
    'RESET_ALL continua alcançável pela UI do AutoCal');
});

test('P0: bridge recusa explicitamente preparar RESET_ALL vindo da WebView', () => {
  const body = methodBody('fun prepareNativeAction(action: String)', 'fun preparePointDelete(');
  assert.match(body, /if\s*\(\s*requested\s*==\s*"RESET_ALL"\s*\)/,
    'a WebView precisa recusar RESET_ALL antes de chamar o manager');
  assert.doesNotMatch(body, /AutoCalNativeActionManager\.Action\.RESET_ALL\s*,/,
    'a lista permitida à WebView não pode incluir RESET_ALL');
});

test('P0: bridge recusa executar RESET_ALL mesmo com preparação externa', () => {
  const body = methodBody('fun executeNativeAction(preparationId: String)', 'fun clearNativeActionPreparation(');
  assert.match(body, /if\s*\(\s*action\s*==\s*AutoCalNativeActionManager\.Action\.RESET_ALL\s*\)/,
    'a execução por WebView precisa negar RESET_ALL após validar preparação');
  assert.doesNotMatch(body, /AutoCalNativeActionManager\.Action\.RESET_ALL\s*,/,
    'a lista de execução da WebView não pode incluir RESET_ALL');
});

test('P0: preservar rastreabilidade do modo do protocolo original no manager interno', () => {
  assert.match(nativeManager, /RESET_ALL\s*\(/,
    'não apagar o comando do oráculo: apenas torná-lo indisponível pela WebView');
});
