import { store } from './store.js';

export const MAIN_ROUTES = Object.freeze([
  Object.freeze({ id: 'agora', label: 'Agora' }),
  Object.freeze({ id: 'aprender', label: 'Aprender' }),
  Object.freeze({ id: 'predictor', label: 'Predictor' }),
  Object.freeze({ id: 'mapa-k', label: 'Mapa K' }),
  Object.freeze({ id: 'curva-k', label: 'Curva K' }),
  Object.freeze({ id: 'obd', label: 'OBD' }),
]);

const routeIds = new Set(MAIN_ROUTES.map((route) => route.id));

export class NextRouter {
  #current = 'agora';
  #listeners = new Set();
  #modal = null;

  current() {
    return Object.freeze({ route: this.#current, modal: this.#modal });
  }

  navigate(route, options = {}) {
    if (!routeIds.has(route)) throw new Error(`Rota NEXT desconhecida: ${route}`);
    if (route === this.#current && !this.#modal) return this.current();
    this.#current = route;
    if (!options.keepModal) this.#modal = null;
    store.dispatch({ type: 'ROUTE_CHANGED', route });
    this.#emit({ type: 'NAVIGATED', route });
    return this.current();
  }

  openContext(kind, payload = null) {
    if (!kind) throw new Error('Contexto modal precisa de tipo');
    this.#modal = Object.freeze({ kind, payload });
    this.#emit({ type: 'CONTEXT_OPENED', modal: this.#modal });
    return this.current();
  }

  closeContext() {
    if (!this.#modal) return this.current();
    const closed = this.#modal;
    this.#modal = null;
    this.#emit({ type: 'CONTEXT_CLOSED', modal: closed });
    return this.current();
  }

  subscribe(listener) {
    if (typeof listener !== 'function') throw new TypeError('listener obrigatório');
    this.#listeners.add(listener);
    listener(this.current(), { type: 'INITIAL_ROUTE' });
    return () => this.#listeners.delete(listener);
  }

  #emit(event) {
    const snapshot = this.current();
    for (const listener of [...this.#listeners]) listener(snapshot, event);
  }
}

export const router = new NextRouter();
