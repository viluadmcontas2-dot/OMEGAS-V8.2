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
  assert.ok(src.includes('<b>Resumo da sessão</b><small>Análise + guias · mapa e sessão · sem dados brutos'));
  assert.ok(src.includes("item.active ? 'Parcial · reúna todas as partes' : 'Arquivos + eventos e brutos capturados'"));
  assert.ok(src.includes('Gravação ativa: exportação parcial; guarde todas as partes'));
  assert.ok(src.includes('<span class="sum-title">Ajustes da gravação</span>'));
});


test('Sessões: falta de tamanho/data mantém desconhecido, sem inventar 0 B ou 1970', () => {
  const vm = require('node:vm');
  const host = { innerHTML: '', childElementCount: 0, querySelector: () => null };
  const context = {
    globalThis: { innerHeight: 720, OmegasUi: {} },
    document: { getElementById: id => id === 'sessionsHost' ? host : null },
  };
  vm.runInNewContext(src.replace('  ns.Drawers = Drawers;', '  ns.__bytesLabel = bytesLabel; ns.Drawers = Drawers;'), context, { filename: 'drawers.js' });
  const label = context.globalThis.OmegasUi.__bytesLabel;
  assert.equal(label(null), '—', 'tamanho não informado');
  assert.equal(label(undefined), '—', 'tamanho ausente');
  assert.equal(label(-1), '—', 'tamanho inválido');
  assert.equal(label(0), '0 B', 'arquivo realmente vazio mantém zero');
  assert.equal(label(2048), '2 KB', 'arquivos positivos mantêm unidade');
  const drawers = Object.create(context.globalThis.OmegasUi.Drawers.prototype);
  drawers.sessionPage = 0;
  drawers.sessionSettingsFeedback = '';
  drawers.renderSessions({
    sessionStatus: { recording: false, settings: { autoStartOnUsb: false, maxSessionMb: 256, keepSessions: 20 } },
    sessions: [{ id: 'sem_id_temporal', createdAt: null, bytes: null, durationMs: null, active: false, summaryBytes: null }],
    status: { usbConnected: false },
  });
  assert.match(host.innerHTML, /Data não informada/, 'sem data não apresentar Epoch');
  assert.match(host.innerHTML, /—/, 'sem tamanho não apresentar um 0 fictício');
  assert.doesNotMatch(host.innerHTML, /01\/01\/1970|0 B/, 'metadados ausentes não podem inventar zero');
});
