# OMEGAS V7

Aplicativo Android para leitura, aprendizado e ajuste manual assistido de centrais MP48.

Este repositório é a fonte funcional única do produto. A interface ativa e o núcleo Kotlin usados por telemetria, aprendizado, leitura e escrita segura da ECU pertencem ao próprio OMEGAS V7 e devem ser verificados na branch autorizada.

## Contratos do produto

- nenhuma sugestão grava automaticamente;
- edição manual e sugestões do aprendizado são fluxos separados;
- o Mapa K usa 12 × 12 células editáveis e preserva a 13ª linha técnica;
- lotes possuem no máximo 16 células;
- toda escrita exige ação humana, ACK e readback;
- o botão **Revisar selecionadas** abre a revisão no próprio Mapa K e nunca navega para Sugestões;
- o editor permanece fixo na parte inferior para evitar rolagens de ida e volta.

## Verificação

```bash
python -B tests/test_clean_ui_contract.py
python -B tests/test_mp48_k_map_axes_contract.py
node --test tests/ui/*.test.cjs
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

O APK de teste é publicado pelo workflow `OMEGAS V7 Android`.
