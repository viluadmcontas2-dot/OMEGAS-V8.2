# Temperaturas — tabelas, seletores e legenda do original

Fontes: somente DUMP e LN completo. Continuação UTC 2026-10-03, base remota `60461a1a17e174ff0e6f664fd5ee78ba55295302`. **PROVADO** abaixo significa caminho e bytes do original; previsões do caminho com configuração do LN são **INFERIDAS**, sem snapshot de memória/UI ou validação física.

## 1. Três decisões distintas

| Decisão | Origem / vínculo | Efeito |
|---|---|---|
| Família interna | `00 02 02`, byte1 da identificação → TStreamDati+0x271D | Tipos14/17/48/47 hex ligam +0x80D; outros desligam; troca os canais de temperatura e o ramo de pressão |
| Sensor interno | SC134 `TIPO_SENSORE_TEMPERATURA` → +0x272C | bits0…4 escolhem tabela redutor; bits5…7 escolhem tabela motor/gás |
| Tabela custom | SC3 `FLAG_CONF1[1] & 0x0004` | getter bool passado ao inicializador motor/gás; quando verdadeiro usa SC138/139 |

Não confluir esses seletores com SC21 MODELLO_HARDWARE nem com enum de injetores. “motor/gás” aqui distingue o nome da legenda LabTempMotore do caminho de inicialização; não prova a posição física do sensor.

**Identificação:** export GetIdentification RVA0x523DA4 → VA0x923DA4; comando00/02 emitido em0x923E1C; payload byte1 armazenado no buffer+1 em0x923E8B. Caller0x42B8D4 passa +0x271C; testes de +0x271D em0x42B8EF e flag0 em0x42B957. Há inicialização offline0x42B964 que copia tipo fornecido, e default de construção flag1: não tratar flag como constante universal.

LN seq2/5/14 (idx73/108/227): `00 02 02 → eco | 53 04 FE 4F 45 0B F4`. Byte1=4F. Após o carregador conectado, a previsão é flag0 e ramo normal da tensão (4F não é5D/5E). Nome privado da flag e significado amigável dos tipos não recuperados; demais bytes permanecem L-01.

**SC134:** RTTI no PE offset0x6712ED, campo+0x244; DFM SerialCode propriedade0x847C/valor0x8487, DataMask255. Loader0x43B73C–0x43B757 usa cache+0x272C; chama inicializador redutor0x430A0C via0x43BAD1. Switch baixo aceita0…5; **6…31 retorna sem trocar a tabela anterior**. Inicializador motor0x430DFC, quando não custom, extrai `(SC134>>5)&7` em0x430E9C. Seq29/7558/19899, idx429/217530/551461: SC134=0.

**SC3:** RTTI offset0x6708F1, campo+0x90; loader0x43ADB8 lê vetor para+0x2984. TAebVector.GetEcuData0x978DCC copia elementos para **int32**, stride4 em0x978E02–0x978E26. Assim o teste+0x2988 &04 (getter0x42CFB8, índice10, alvo0x42D16A) é segundo elemento, não quinto byte da fiação. Caller0x43C44C passa bool a0x430DFC. Leituras e 19 escritas SC3 observadas mantêm segundo U16=0x1051, bit04 zerado; o verificador não encontra escrita SC134/138/139 no LN completo. Isso delimita o observado, sem provar imutabilidade na ECU. Há ainda chamada com false antes da carga de ECU_TEMP; receber tabela não significa selecioná-la.

## 2. Tabelas internas completas

Cada valor-fonte é float32 da .data. O loop x87 constrói inteiro:
`T[i] = trunc(255 × escala × fonte[i] / (denominador + escala × fonte[i]))`.
Helper0xA45A58 configura round toward zero, FISTP qword e restaura control word: **Trunc**, não arredondamento ao inteiro mais próximo.

| Grupo / seletor | Inicializador VA | Fonte VA | N | Origem | Passo | Denominador | Escala |
|---|---|---|---:|---:|---:|---:|---:|
| redutor0 | 430A46 | A5C830 | 48 | -55 | 5 | 4700 | 4700 |
| redutor1 | 430AE1 | A5C9B0 | 27 | -10 | 5 | 1000 | 1000 |
| redutor2 | 430B7C | A5CA1C | 35 | -40 | 5 | 2200 | 1 |
| redutor3 | 430C17 | A5CC30 | 35 | -40 | 5 | 4700 | 10000 |
| redutor4 | 430CB2 | A5CBA4 | 35 | -40 | 5 | 4700 | 10000 |
| redutor5 | 430D4D | A5CC30 | 35 | -40 | 5 | 4700 | 10000 |
| motor/gás1 | 430EB7 | A5CD44 | 37 | -40 | 5 | 4700 | 2200 |
| motor/gás2 | 430F52 | A5CC30 | 35 | -40 | 5 | 4700 | 10000 |
| motor/gás0,3…7 | 430FED | A5C830 | 48 | -55 | 5 | 4700 | 4700 |

A5CBA4 e A5CC30 têm endereços distintos e arrays float32 idênticos. Redutor3/4/5 coincidem numericamente; isso não identifica nomes comerciais. Os **335 nós internos**, bytes/hashes dos arrays, todas as 256 conversões por caso e sua projeção de legenda estão em `fontes/temperaturas-reconstrucao.json`. Arrays compactos com separador ";" têm índice=raw0…255; caption JSON tem null=ausência.

Tabela0 (também default motor):
`252 251 249 247 244 241 236 231 224 215 206 195 182 169 155 141 127 113 100 88 77 67 58 50 44 38 32 28 24 21 18 16 14 12 10 9 8 7 6 5 5 4 4 3 3 3 2 2`.

## 3. Tabela custom SC138/139

RTTI PARAMETRI_TEMP offsetPE0x67136E, campo+0x254 → cache+0x2848, getter0x4303A8 (5 elementos). ECU_TEMP offset0x671383, campo+0x258 → cache+0x285C, getter0x43040C (30). Loaders0x43BC9E/0x43C158 usam expansão int32 do vetor.

LN seq30/31, repetidas7559/7560 e19900/19901:
- SC138 payload `12 5C 05 EC 1E`: inteiros expandidos [18,92,5,-20,30]. Signed explicitamente true no DFM; DataLength omitido não prova default, os cinco bytes vêm da fiação.
- SC139 payload30: `E7 E0 D8 CE C3 B7 AA 9C 8E 80 72 65 59 4E 44 3B 33 2C 26 21 1D 19 16 13 10 0E 0C 0B 0A 08`.

Caminho0x430E12: count=params[4]=30; origem=params[3]=-20; passo=params[2]=5; combina U8(params[0])<<8 + U8(params[1]) =4700 em global+AECFD8 (unidade desse parâmetro não fechada); cópia de nós0x430E77. .data A5CDD8/A5CDEC contém os mesmos parâmetros/nós do LN. Caso custom foi reconstruído **como contrafactual verificável**: bit do LN está desligado; igualdade dos valores não prova uso em execução.

## 4. Busca, interpolação e extremos

Redutor helper0x42A52C usa arrayA5CE64 e globaisN/origem/passo AECFB8/BC/C0. Motor helper0x42A788 usa arrayA5CF2C e AECFCC/D0/D4. Busca i=0; avança enquanto raw<T[i] e i<N−1, em tabela decrescente.

Para i=0: f=(raw−T[0])/(T[0]−T[1]); para i>0: f=(raw−T[i])/(T[i−1]−T[i]). Se os dois nós do par são iguais, f=0,5. Resultado `(i−f)×passo+origem`. O código contém ramo i>N−1, mas a busca normal com N válido não o produz; não inventar clamp geral com esse ramo.

Há extrapolação acima do primeiro nó e pares repetidos abaixo. Tabela0: helper(255)=-70; helper(0/1)=177,5; helper(2)=175; helper(3)=160. **Esses números são resultado do helper isolado**, não validade física ou política de UI.

## 5. Despacho e legenda

Flag0: LabTempRiduttore(+0x454)=redutor(payload16), LabTempMotore(+0x45C)=motor(payload12). Flag1 troca os bytes. Raw0 em Timer1 recebe float0 de VA0x50D214 sem chamar helper.

Captions0x50BCE1–0x50BE0B comparam os doubles+0x520/+0x528 a zero. Para valores finitos, zero usa string de ausência+0x734; qualquer valor não zero chama Trunc0xA45A58 e formatador inteiro0x66D2BC antes de atribuir as duas legendas. Portanto **zero convertido também é ausência**: tabela0 raw195→0°C. Um valor não zero entre−1 e1 pode truncar para legenda numérica0; o teste ocorre antes da truncagem. Literal da string/prefixo e locale não foram fechados. Não generalizar este comportamento para gráfico/média nem para NaN/exception x87.

LN seq6109: payload16=68 e12=41. Com configuração observada, previsão de cálculo é49,5 e67,5°C; projeção racional inteira da legenda49 e67. Seq6073: raw12=40 →68⅓ →68. Previsões derivadas, sem afirmar tela efetivamente observada ou equivalência bit a bit do último Double x87.

## 6. Reprodução e alcance

`fontes/reconstruir-temperaturas.py TEXT DATA PE --log LOG --parser portmon_parser.py --excerpts temperaturas-trechos.json` recebe fontes originais, confere SHA-256 integrais e blob Git do parser17252b0a06e6091ae77d0d44843a8902c32d2172 (remoto base9c6d33b). Sem --log, valida somente DUMP. No modo completo: 39.517 transações reparseadas, 37 trechos mínimos com seq/idx, eco/len/checksum conferidos.

Verificação: 10 casos, 335 nós internos+30 custom; **2.560 conversões racionais e 2.560 projeções finitas de legenda**, monotonia, identidades dos primeiros nós distintos e estabilidade dos nós inteiros entre racional exato e float64 dos mesmos float32. Código/RTTI e spans têm hashes. `fontes/temperaturas-trechos.json` guarda âncoras mínimas; .text VA base401000, .data A51000; ambos conferidos com manifest.

Status **PASS_STATIC_RECONSTRUCTION_ONLY**. A sonda ELF32 foi rejeitada pelo runtime com Exec format error; replay nativo não executado. Aritmética racional não emula precisão final x87/Double nem prova calibração física. Há cobertura exaustiva do domínio de byte destes dez casos, não cobertura de cada byte do executável, todos os sensores/estados ou UI inteira.

Próxima continuidade: L-13, produtor/consumidor48 0B e independência do SC330; depois VAs históricos restantes L-12. Tabelas/seletores/legendas deste recorte estão reabertos, nomes físicos/consumidores desconhecidos permanecem explícitos.
