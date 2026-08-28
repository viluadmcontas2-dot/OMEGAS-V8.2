(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  class SplitLayout {
    constructor(app) {
      this.app = app;
      this.shell = document.querySelector('.app-shell') || document.body;
      this.onResize = this.apply.bind(this);
      this.injectStyle();
      root.addEventListener('resize', this.onResize, { passive: true });
      this.apply();
    }

    injectStyle() {
      if (document.querySelector('link[data-split-layout-style]')) return;
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = 'styles-split-layout.css';
      link.dataset.splitLayoutStyle = 'true';
      document.head.appendChild(link);
    }

    apply() {
      const width = Math.max(0, Number(root.innerWidth) || document.documentElement.clientWidth || 0);
      const height = Math.max(0, Number(root.innerHeight) || document.documentElement.clientHeight || 0);
      const compact = width > 0 && width < 960;
      this.shell.dataset.layout = compact ? 'split-compact' : 'full-width';
      this.shell.style.setProperty('--viewport-width', `${width}px`);
      this.shell.style.setProperty('--viewport-height', `${height}px`);
      // Deliberadamente não altera Store/Router/seleção/operação. Resize só reorganiza CSS.
    }

    destroy() {
      root.removeEventListener('resize', this.onResize);
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.router) {
      root.setTimeout(boot, 25);
      return;
    }
    if (app.splitLayout) return;
    app.splitLayout = new SplitLayout(app);
  }

  ns.SplitLayout = SplitLayout;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
