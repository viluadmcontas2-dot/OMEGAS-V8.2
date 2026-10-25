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


test('Sessões diferencia o resumo do pacote completo e dá nome claro aos ajustes', () => {
  assert.ok(src.includes('<b>Resumo da sessão</b><small>Mapa + curva · leitura rápida</small>'));
  assert.ok(src.includes('<b>Pacote completo</b><small>Eventos + dados brutos disponíveis</small>'));
  assert.ok(src.includes('<span class="sum-title">Ajustes da gravação</span>'));
});
