function makeMap() {
  return Array.from({ length: 12 }, (_, row) =>
    Array.from({ length: 12 }, (_, column) => 112 + row * 4 + column * 2),
  );
}

export class SimulatedMapKAdapter {
  #map = makeMap();
  #revision = 'SIM-MAP-001';

  async readMap() {
    return {
      state: 'READY',
      map: this.#map.map((row) => [...row]),
      selection: [],
      proposal: null,
      sourceRevision: this.#revision,
      technicalRowProtected: true,
      writableCells: 144,
    };
  }

  async preview(selection, delta) {
    const changes = selection.map(({ row, column }) => ({
      row,
      column,
      before: this.#map[row][column],
      after: Math.max(50, Math.min(255, this.#map[row][column] + delta)),
    }));
    return {
      summary: `${changes.length} célula${changes.length === 1 ? '' : 's'} • delta ${delta > 0 ? '+' : ''}${delta} • nenhuma escrita`,
      delta,
      changes,
      automaticWrite: false,
      humanConfirmationRequired: true,
      simulatedOnly: true,
    };
  }
}

export const simulatedMapKAdapter = new SimulatedMapKAdapter();
