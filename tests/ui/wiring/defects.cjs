'use strict';
// Registro de defeitos REAIS do app encontrados pela suíte de uso (Lote W).
// Cada defeito tem uma `probe` que o REPRODUZ de forma independente. Enquanto a probe reproduz, os testes
// ligados a ele rodam como `todo` (node:test não falha o CI e continua mostrando o resultado); quando o app é
// corrigido, a probe deixa de reproduzir e os mesmos testes passam a valer sozinhos, sem editar nada.
// Lista humana: tests/wiring/DEFECTS.md.
const DEFECTS = new Map();
function defect(id, summary, probe) { DEFECTS.set(id, { summary, probe, state: undefined }); }
function active(id) {
  const d = DEFECTS.get(id);
  if (!d) throw new Error(`defeito desconhecido ${id}`);
  if (d.state === undefined) d.state = !!d.probe();
  return d.state;
}
/** Opções do node:test: `todo` enquanto o defeito existir. */
function todo(id) { return active(id) ? { todo: `${id}: ${DEFECTS.get(id).summary}` } : {}; }

module.exports = { defect, active, todo, DEFECTS };
