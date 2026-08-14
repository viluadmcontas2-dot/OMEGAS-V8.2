export const ACTION_COPY = Object.freeze({
  READ: 'Ler',
  REREAD: 'Reler ECU',
  PREPARE: 'Preparar',
  REVIEW: 'Revisar',
  CONFIRM_INTENT: 'Confirmar intenção',
  WRITE_ECU: 'Gravar na ECU',
  VALIDATE_READBACK: 'Validar readback',
});

export const OPERATION_COPY = Object.freeze({
  RECEIVED: 'Comando recebido',
  WAITING_RESOURCE: 'Aguardando recurso',
  EXECUTING: 'Executando',
  SUCCESS: 'Concluído e validado',
  FAILURE: 'Não foi possível concluir',
  STALE: 'Dados antigos — releitura necessária',
});

export function criticalWriteLabel(target) {
  const normalized = String(target || '').trim();
  return normalized ? `${ACTION_COPY.WRITE_ECU} • ${normalized}` : ACTION_COPY.WRITE_ECU;
}

export function recoverableFailureCopy(message) {
  return Object.freeze({
    human: message || OPERATION_COPY.FAILURE,
    action: ACTION_COPY.REREAD,
    keepsDraft: true,
    automaticRetry: false,
  });
}
