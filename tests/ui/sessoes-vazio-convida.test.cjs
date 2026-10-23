'use strict';
// Auditoria ponta a ponta (cabo → export): tela vazia convida a agir conforme o que de fato acontece.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const src = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/components/drawers.js'), 'utf8');

test('Sessões vazia: com gravação automática ligada diz que começa sozinha; desligada manda tocar em Começar a gravar', () => {
  const a = src.indexOf('Nenhuma gravação ainda');
  const trecho = src.slice(a - 200, a + 320);
  assert.match(trecho, /autoStartOnUsb !== false/, 'a frase depende da configuração real');
  assert.match(src, /Nenhuma gravação ainda\. Toque em Começar a gravar\./);
  assert.match(src, /Nenhuma gravação ainda\. Ela começa sozinha ao conectar a central\./);
});
