'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.join(__dirname, '../..');
const html = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/index.html'), 'utf8');
const css = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/styles-witness-multimedia.css'), 'utf8');
const app = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/app.js'), 'utf8');
const activity = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/MainActivity.kt'), 'utf8');

function sectionBody(route) {
  const re = new RegExp(`<section[^>]*data-screen=["']${route}["'][^>]*>([\\s\\S]*?)<\\/section>`);
  const match = html.match(re);
  return match ? match[1] : '';
}

test('rotas essenciais possuem fallback local não vazio antes do bootstrap JS', () => {
  const dashboard = sectionBody('dashboard');
  const obd = sectionBody('obd');
  const tools = sectionBody('tools');

  assert.match(dashboard, /data-bootstrap-fallback=["']dashboard["']/,
    'Agora não pode depender de screen JS para deixar de ser uma área vazia');
  assert.match(obd, /data-bootstrap-fallback=["']obd["']/,
    'OBD não pode depender de screen JS para deixar de ser uma área vazia');
  assert.match(tools, /id=["']toolDiagnosticsWorkspace["']/,
    'Ferramentas precisa nascer com workspace local em vez de depender de append dinâmico');
});

test('layout essencial não depende de :has, ausente na WebView física observada', () => {
  assert.doesNotMatch(css, /body:has\(/,
    'CSS :has() não pode controlar layout essencial da multimídia');
  assert.match(app, /document\.body\.dataset\.omegasRoute/,
    'router deve publicar a rota no body para CSS compatível');
});

test('WebView invalida cache de assets empacotados e verifica bootstrap', () => {
  assert.match(activity, /cacheMode\s*=\s*WebSettings\.LOAD_NO_CACHE/,
    'assets locais não podem reutilizar JS/CSS de APK anterior');
  assert.match(activity, /verifyWebUiBootstrap/,
    'MainActivity precisa detectar bootstrap ausente em vez de aceitar tela silenciosamente vazia');
  assert.match(activity, /onPageFinished/,
    'verificação de bootstrap precisa ser ligada ao carregamento real da página');
});

console.log('BLUE_RUNTIME_ESSENTIAL_SCREENS_CONTRACT=PASS');
