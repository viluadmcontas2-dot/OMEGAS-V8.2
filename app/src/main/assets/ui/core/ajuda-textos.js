(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Conteúdo da ajuda por tela (preenchido pelo trabalho de ajuda). Chave = rota. Cada item: { eoque, fazer, acontece }.
  ns.AjudaTextos = { telas: {}, primeiroUso: [] };
})(typeof window !== 'undefined' ? window : globalThis);
