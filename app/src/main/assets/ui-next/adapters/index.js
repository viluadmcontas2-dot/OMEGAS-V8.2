import { assertNextAdapter } from './next-contract.js';
import { nativeNextAdapter } from './native-next.js';
import { simulatedNextAdapter } from './simulated-next.js';

function hasAndroidTransport() {
  return typeof globalThis?.OmegasNative === 'object' && typeof globalThis?.OmegasV7 === 'object';
}

/**
 * Única seleção de ambiente da UI NEXT.
 * Rota nenhuma pode decidir entre bridge/simulador por conta própria.
 */
export function createNextAdapter() {
  return assertNextAdapter(hasAndroidTransport() ? nativeNextAdapter : simulatedNextAdapter);
}

export const nextAdapter = createNextAdapter();
