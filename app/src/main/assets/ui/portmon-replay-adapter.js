"use strict";
(() => {
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __commonJS = (cb, mod) => function __require() {
    try {
      return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
    } catch (e) {
      throw mod = 0, e;
    }
  };
  var require_portmon_replay_adapter = __commonJS({
    "app/src/main/assets/ui/portmon-replay-adapter.js"(exports, module) {
      (function(root, factory) {
        const api = factory();
        if (typeof module !== "undefined" && module.exports) module.exports = api;
        if (root) root.OmegasPortmonReplay = api;
      })(typeof globalThis !== "undefined" ? globalThis : exports, function() {
        class PortmonReplayError extends Error {
          constructor(code, message) {
            super(message);
            this.name = "PortmonReplayError";
            this.code = code;
          }
        }
        function normalizeHex(value) {
          if (typeof value !== "string") throw new TypeError("frame deve ser texto hexadecimal");
          const normalized = value.trim().replace(/\s+/g, " ").toUpperCase();
          if (!normalized || !/^(?:[0-9A-F]{2})(?: [0-9A-F]{2})*$/.test(normalized)) {
            throw new TypeError("frame hexadecimal inv\xE1lido");
          }
          return normalized;
        }
        function parseCorpus(corpus) {
          if (!corpus || !Array.isArray(corpus.transactions) || corpus.transactions.length === 0) {
            throw new TypeError("corpus sem transa\xE7\xF5es");
          }
          return corpus.transactions.map((item, index) => ({
            sequence: Number.isInteger(item.sequence) ? item.sequence : index + 1,
            request: normalizeHex(item.request),
            response: normalizeHex(item.response)
          }));
        }
        class PortmonReplayAdapter {
          constructor(corpus) {
            this.source = Object.freeze({
              originalSha256: corpus.originalSha256 || "",
              compressedSha256: corpus.compressedSha256 || "",
              description: corpus.description || ""
            });
            this.transactions = parseCorpus(corpus);
            this.byCommand = /* @__PURE__ */ new Map();
            this.positions = /* @__PURE__ */ new Map();
            this.mode = "NORMAL";
            this.transactions.forEach((tx) => {
              const queue = this.byCommand.get(tx.request) || [];
              queue.push(tx);
              this.byCommand.set(tx.request, queue);
            });
          }
          setMode(mode) {
            const allowed = ["NORMAL", "TIMEOUT", "TRUNCATE", "CORRUPT"];
            if (!allowed.includes(mode)) throw new TypeError("modo inv\xE1lido: ".concat(mode));
            this.mode = mode;
          }
          reset() {
            this.positions.clear();
            this.mode = "NORMAL";
          }
          exchange(requestHex) {
            const request = normalizeHex(requestHex);
            const queue = this.byCommand.get(request);
            if (!queue) throw new PortmonReplayError("UNKNOWN_COMMAND", "comando ausente no corpus: ".concat(request));
            if (this.mode === "TIMEOUT") throw new PortmonReplayError("TIMEOUT", "timeout simulado: ".concat(request));
            const position = this.positions.get(request) || 0;
            const transaction = queue[position % queue.length];
            this.positions.set(request, position + 1);
            let response = transaction.response;
            if (this.mode === "TRUNCATE") {
              const bytes = response.split(" ");
              response = bytes.slice(0, Math.max(1, bytes.length - 1)).join(" ");
            } else if (this.mode === "CORRUPT") {
              const bytes = response.split(" ");
              const last = Number.parseInt(bytes[bytes.length - 1], 16);
              bytes[bytes.length - 1] = ((last ^ 1) & 255).toString(16).padStart(2, "0").toUpperCase();
              response = bytes.join(" ");
            }
            return Object.freeze({
              sequence: transaction.sequence,
              request,
              response,
              mode: this.mode
            });
          }
        }
        return { PortmonReplayAdapter, PortmonReplayError, normalizeHex };
      });
    }
  });
  require_portmon_replay_adapter();
  if (typeof module === "object" && module.exports && typeof globalThis !== "undefined") module.exports = globalThis.OmegasPortmonReplay;

})();
