# OMEGAS V8.2

Aplicativo Android para telemetria, AutoCal com equivalência refinada GNV = gasolina, aprendizado e ajuste manual assistido de centrais OMEGAS/MP48.

O repositório é a fonte canônica de engenharia. Comece por [PROJECT.md](PROJECT.md), [STATUS.md](STATUS.md) e pela WorkUnit ativa em [docs/workunits](docs/workunits).

Produto: Agora · AutoCal (equivalência refinada GNV = gasolina) · Aprender · Ajuste global (Curva K) · Ajuste local (Mapa K) · Sugestões · Ferramentas.

## Invariantes do produto

- equivalência primária: `RPM × MAP(bar) → Petrol Inj. (ms)`;
- aprendizado e sugestões são passivos;
- nenhuma conexão, sugestão ou aprendizado grava automaticamente na ECU;
- toda escrita segue preparar → revisar → confirmar → ACK → readback;
- falha de ACK ou readback divergente não é sucesso;
- Mapa K (`RPM × Petrol Inj.`) e Curva K permanecem separados;
- matemática e protocolo críticos permanecem no Kotlin.

## Verificação

```bash
python3 -B tools/run_checks.py
./gradlew testDebugUnitTest lintDebug assembleDebug -PomegasAbis=armeabi-v7a
```

O pipeline de release registra o SHA do source, o hash SHA-256 do APK e os limites da evidência.
