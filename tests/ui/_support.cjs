'use strict';
// Apoio dos testes de UI: os módulos da UI compartilham utilitários (core/display-rules.js, core/live-store.js,
// components/curve-chart.js). Quem carrega uma tela sozinha num contexto vm precisa carregá-los antes, como o index.html faz.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
const PRELOAD = ['core/display-rules.js', 'core/live-store.js', 'components/curve-chart.js'];

function preload(context, files) {
  for (const file of files || PRELOAD) {
    vm.runInContext(fs.readFileSync(path.join(UI, file), 'utf8'), context, { filename: file });
  }
  return context;
}

/** Contexto novo com window/globalThis e os utilitários já carregados. */
function freshContext(extra, files) {
  const window = { setTimeout: () => 0, ...(extra || {}) };
  window.window = window;
  window.globalThis = window;
  vm.createContext(window);
  return preload(window, files);
}

module.exports = { UI, PRELOAD, preload, freshContext };
