(function (root, factory) {
  'use strict';
  const api = factory();
  const commonJs = typeof module === 'object' && module.exports;
  if (commonJs) module.exports = api;
  const ns = root.OmegasUi = root.OmegasUi || {};
  ns.LearningWaterNudge = api;

  if (!commonJs && typeof root.setTimeout === 'function') {
    const installWhenReady = () => {
      if (!ns.LearningScreen) {
        root.setTimeout(installWhenReady, 25);
        return;
      }
      api.install(ns.LearningScreen);
    };
    root.setTimeout(installWhenReady, 0);
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function finite(value, fallback) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function adjustTemperature(value, delta, minimum = 20, maximum = 100) {
    const current = finite(value, 60);
    const step = finite(delta, 0);
    return Math.round(Math.max(minimum, Math.min(maximum, current + step)));
  }

  function enhancePane(pane) {
    if (!pane) return false;
    const input = pane.querySelector('#learningMinimumWaterInput');
    if (!input || pane.querySelector('[data-learning-water-nudges]')) return false;

    const row = pane.ownerDocument.createElement('div');
    row.className = 'nudge-row learning-water-nudge-row';
    row.dataset.learningWaterNudges = 'true';
    row.setAttribute('aria-label', 'Ajuste rápido da temperatura mínima da água');
    row.innerHTML = [
      '<button type="button" data-learning-water-nudge="-10">−10 °C</button>',
      '<button type="button" data-learning-water-nudge="-5">−5 °C</button>',
      '<button type="button" data-learning-water-nudge="5">+5 °C</button>',
      '<button type="button" data-learning-water-nudge="10">+10 °C</button>',
    ].join('');

    const field = input.closest('label');
    if (field?.parentNode) field.parentNode.insertBefore(row, field.nextSibling);
    else input.parentNode?.appendChild(row);

    row.querySelectorAll('[data-learning-water-nudge]').forEach(button => {
      button.addEventListener('click', () => {
        input.value = String(adjustTemperature(input.value, button.dataset.learningWaterNudge));
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
      });
    });
    return true;
  }

  function install(LearningScreen) {
    const proto = LearningScreen?.prototype;
    if (!proto || proto.__omegasWaterNudgeInstalled || typeof proto.renderTolerances !== 'function') return false;
    const original = proto.renderTolerances;
    proto.renderTolerances = function (...args) {
      const result = original.apply(this, args);
      enhancePane(this.tolerancePane);
      return result;
    };
    proto.__omegasWaterNudgeInstalled = true;
    return true;
  }

  return { adjustTemperature, enhancePane, install };
});
