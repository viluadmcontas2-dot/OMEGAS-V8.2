'use strict';
// Auditoria ponta a ponta (cabo → export): tela vazia convida a agir conforme o que de fato acontece.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const src = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/components/drawers.js'), 'utf8');

test('Sessões vazia: com gravação automática ligada diz que começa sozinha; desligada manda tocar em Começar a gravar', () => {
  const a = src.indexOf('Nenhuma gravação salva');
  assert.ok(a > 0, 'a tela vazia existe');
  assert.match(src, /const autoStart = settings\.autoStartOnUsb !== false;/, 'a frase depende da configuração real');
  assert.match(src, /autoStart \? 'Nenhuma gravação salva\. Conecte a central para iniciar automaticamente\.'/);
  assert.match(src, /Toque em “Começar a gravar” para iniciar\./);
});
