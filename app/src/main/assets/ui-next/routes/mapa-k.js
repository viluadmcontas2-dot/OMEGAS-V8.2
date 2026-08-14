import { renderMapKEditor } from '../components/map-k-editor.js';

let lastMapState = null;
let lastCellContext = null;

export const mapaKRoute = {
  mount(ctx, state) {
    lastMapState = null;
    lastCellContext = null;
    ctx.workspace.innerHTML = `<section class="route-page" data-route="mapa-k">
      <div class="route-heading"><div><h1>Mapa K</h1><p>Ajuste local: ler, selecionar, preparar, revisar e só então confirmar.</p></div></div>
      <div id="map-k-root"></div></section>`;
    if (state.mapK.state !== 'READY') ctx.readMapK();
    this.update(ctx, state);
  },
  update(ctx, state) {
    if (lastMapState === state.mapK && lastCellContext === state.cellContext) return;
    lastMapState = state.mapK;
    lastCellContext = state.cellContext;
    const root = document.getElementById('map-k-root');
    if (root) renderMapKEditor(root, ctx.mapEditorState(state), ctx.mapEditorActions());
  },
};