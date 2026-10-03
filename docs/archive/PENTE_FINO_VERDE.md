# Auditoria Pente Fino - OMEGAS VERDE

## 1. Thread Locks e Possíveis Gargalos (Deadlocks)
- **SessionRecorder.kt**: A função clearStoppedSessions() é @Synchronized, o que bloqueia a instância inteira enquanto realiza I/O pesado de disco (usando dir.deleteRecursively() em múltiplos diretórios). Como a thread de gravação (worker) exige o lock synchronized(this) para processar a fila, a exclusão de sessões antigas bloqueará o worker, causando rápido esgotamento da fila e descarte massivo de telemetria. 
*Solução adotada:* Remover o @Synchronized, pois as variáveis críticas já são @Volatile.

## 2. Exceções Não Tratadas em Workers
- **SessionRecorder.kt**: O ThreadPoolExecutor não possui um UncaughtExceptionHandler definido para Errors (OutOfMemory, etc).

## 3. Lógica Legada e Bloqueios
- **AutoMatchV5Engine**: O limite de calibração em MAX_STEP_RATIO = 0.05 atua como um portão lento para a convergência.

## 4. Limites Estáticos (Hardcoded)
- **KMapPhysicalAxes.kt**: Eixos estritos (RPM 6500, MS 18.0) podem clipar leituras em V8 ou motores que giram alto.
- **KWriteManager.kt**: MIN_SAFE_K = 100 pode ser muito alto para algumas ECUs antigas.
