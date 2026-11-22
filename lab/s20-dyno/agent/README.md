# Ômegas — Investigador de evidências (protótipo read-only)

**Estado real:** núcleo determinístico + validador de propostas de LLM. **Ainda não existe um modelo conectado, serviço autônomo permanente, transmissão online ou execução no S20.** Não modificar o APK em campo durante sessão ativa.

## O que muda

Em vez de gerar interpretações sem prova, o agente mantém um livro de **questões verificáveis**. Toda questão tem ID, critério de fechamento, evidência identificada, contradições, lacuna exata e próxima tarefa somente-leitura. Hipóteses não viram fatos por votação de modelos, frequência de registros do mesmo sensor nem “95% de certeza” autodeclarada.

Pipeline pretendido:

`ZIP privado + OBD + MP48 + documentação do ProgBase` → verificação de integridade/proveniência → parser e comparação matemática → livro de evidências → **LLM propõe investigação read-only** → validador verifica ID, referências e ação permitida → ferramenta read-only investiga → evidência volta ao livro → conclusão apenas pelo critério pré-definido → resumo claro para o dono.

### Fronteiras

- **Um único dono da serial USB da MP48:** serviço canônico do Ômegas; laboratório observa, sem segundo leitor disputando a conexão.
- OBD via Bluetooth é *consulta*, não captura completa da rede CAN.
- O agente nunca escreve em Mapa K, Curva K, AutoCal, parâmetros anti-apagão, central GNV, nem envia reset ou flashes. Modificações continuam exclusivamente manuais com ACK + readback.
- Capturas brutas permanecem privadas no S20; GitHub recebe apenas código e agregados não pessoais.
- Cada afirmação descritiva vale **para a captura verificada**; validade física e causalidade exigem documentos originais e medições independentes.
- A saída de LLM é um **plano a validar**, não uma execução nem uma prova.

## Arquivos

- `evidence_engine.py`: valida SHA-256, formato do ZIP, classificação de fatos/questões abertas, permissões de propostas e confronto físico.
- `test_evidence_engine.py`: **12 testes sintéticos** de regressão, integridade e bloqueios de afirmações/ações.
- `../tools/analyze_dual_capture.py`: analisador OBD × MP48 com pares temporais da sessão.
- `../tools/detect_gnv_pulse_gaps.py`: detecta intervalos de pulsos GNV ausentes, sem presumir causa.

## Reproduzir

No checkout do repositório (Python 3.10+):

```bash
python3 -m unittest discover -s lab/s20-dyno/agent -p 'test_*.py' -v
python3 lab/s20-dyno/agent/evidence_engine.py
```

Para recalcular evidência contra um ZIP privado local, importe a função `analyze_verified_capture(path, expected_sha256)` pelo caminho do arquivo ou ajuste o PYTHONPATH. Ela só aceita o ZIP íntegro e usa os analisadores locais já versionados.

## Primeira sessão (21/11/2026): 8 questões

**Observações estabelecidas apenas nesta captura:** concordância de RPM, concordância de MAP, janela de ausência de pulsos GNV perto da marcha lenta, sequência observada de `0x88 → 0x90`.

**Aguardando prova, não resolvidas por LLM:** sensor/escala física da temperatura MP48, causa da ausência de pulsos GNV, função real do byte 19 (candidato a tensão) e semântica geral de `0x88`. Uma captura só não prova causalidade.

## Próximas evidências de maior valor

1. **Sem nova coleta:** comparar evento de marcha lenta com o corpus histórico do Ômegas, procurar padrão repetido e contraexemplos.
2. **Documentação original:** recuperar configuração real do sensor MP48 para desvendar a temperatura. Não forçar a fórmula ao valor OBD.
3. **Medição independente:** confirmar byte 19 versus tensão observada do sistema, não apenas a escala numérica.
4. **Quando o aparelho voltar online:** automatizar verificação SHA-256 e ingestão do ZIP, sem enviar dados brutos ao GitHub.
5. **Só depois de provas:** conectar o LLM planejador de ferramentas read-only; validar saída com corpus de casos falsos e reais, custo, latência e rastreabilidade.

O código tem um `first_s20_case()` com os agregados previamente verificados; é uma demonstração do ledger, **não revalidação do ZIP do S20 nesta execução**. Não afirmar que o LLM autônomo está pronto.
