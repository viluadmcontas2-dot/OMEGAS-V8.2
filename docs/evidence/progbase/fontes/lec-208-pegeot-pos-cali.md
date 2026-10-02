# Configuração real do carro salva pelo ProgBase (`208 pegeot pos cali.lec`)

Arquivo: Drive `1soXrqmU-3ASHNSlxBNrlslVhN2DAS6k-` (pasta `ConfigCNG`), 28.988 B. Só os **primeiros 3.546 bytes** foram decifrados nesta sessão (limite de transferência do executor); o SHA-256 do arquivo inteiro não foi calculado. O arquivo-irmão `208 pegeot.lec` (`12xtc5S0PpVSHLKp5Xo_rMQqDuIMPhIH_`) tem o mesmo cabeçalho e não foi aberto.

## Formato e decifração (PROVADO nesta sessão)

- Cabeçalho ASCII `ENCRYPTED_MLTP_FILE` + `CR LF` (21 bytes); o payload cifrado começa no offset 21.
- Transformação que funcionou: `plain[i] = cipher[i] XOR ((key[i mod 16] << (i mod 5)) & 0xFF)`, com `key = "AEBXLANDIRENZO03"` **sem** a derivação por soma acumulada descrita em `tests/fixtures/progbase-autocal-resource-defaults-v1.json#mltpDecrypt.keyDerivation` (a variante derivada produz lixo; a chave crua produz INI limpo, 217 linhas `chave=valor`, 100 % imprimível).
- O resultado é um INI CRLF com seções `[General]`, `[Generale]`, `[Temperatura]`, `[MappaCoefficientiK]`, … (o restante do arquivo não foi lido).

Receita reproduzível (Python):

```python
data = open('208 pegeot pos cali.lec','rb').read()
assert data.startswith(b'ENCRYPTED_MLTP_FILE\r\n')
key = b'AEBXLANDIRENZO03'
plain = bytes(b ^ ((key[i % 16] << (i % 5)) & 0xFF) for i, b in enumerate(data[21:]))
```

## Valores observados no prefixo (PROVADO como conteúdo do arquivo)

```
[General]  InjCompCode=10
[Generale] Flag1=9350  Flag2=4177  Flag3=1  Flag4=0  Flag5=0  Flag6=0
           Cilindrata=1000  CentCubici=1600  VersioneFile=1
           OffsetMapInterno=100  PendenzaMapInterno=500
           OffsetMapEsterno=100  PendenzaMapEsterno=500
           OffsetDiff=35  PendenzaDiff=826
[Temperatura]
           TipoSensoreTemperaturaGas=0
           ParametriTempMotore0..4 = 18, 92, 5, -20, 30
           EcuTempMotore0..29 = 231,224,216,206,195,183,170,156,142,128,114,101,89,78,68,59,51,44,38,33,29,25,22,19,16,14,12,11,10,8
           Rif0..9     = 0,38,50,58,67,77,83,88,100,255
           Coeff0..8   = 100,99,97,94,91,89,86,82,80
           RifMot0..9  = 0,24,38,50,67,88,113,155,195,255
           CoeffMot0..8= 107,105,103,101,100,98,97,94,92
[MappaCoefficientiK]
           NumeroRighe=12  NumeroColonne=12
           MappaK0_0..11 = 124 126 128 128 131 132 131 128 126 126 126 126
           MappaK1       = 124 125 126 127 130 131 131 130 129 128 128 128
           MappaK2       = 125 126 126 132 133 136 134 131 132 133 133 133
           MappaK3       = 145 145 142 135 138 139 137 135 136 138 138 138
           MappaK4       = 145 145 145 139 141 141 139 138 140 142 141 141
           MappaK5       = 145 145 145 138 137 138 136 137 140 141 140 140
           MappaK6       = 145 145 138 132 130 131 133 135 136 138 139 139
           MappaK7       = 128 129 128 129 128 131 134 134 135 138 139 139
           MappaK8       = 125 126 124 125 126 128 132 132 125 126 127 127
           MappaK9       = 124 125 123 125 128 … (arquivo truncado aqui)
```

## Cruzamentos com a fiação (PROVADO)

- `Flag1/Flag2 = 0x2486/0x1051` = exatamente `FLAG_CONF1` lido na ECU (`29 03 00 2C → 86 24 51 10`, `protocolo.md` 1.6).
- `RifMot/CoeffMot` = `RIF_TEMP_GAS` SC 92 (`29 5C 00 → 00 18 26 32 43 58 71 9B C3 FF`) e `COEFF_TEMP_GAS` SC 93 (`29 5D 00 → 6B 69 67 65 64 62 61 5E 5C`): os coeficientes U8 de temperatura são **percentuais com 100 = neutro** (`parametros.md`).
- `Rif/Coeff` = `RIF_TEMP_RID` SC 42 / `COEFF_TEMP_RID` SC 43 (mesma convenção).
- `Cilindrata=1000` = `CILINDRATA` SC 75 (`09 4B 00 → E8 03` = 1000).
- `MappaK` 12×12 em torno de 128 (124–145) após a calibração, enquanto o Lognovo (sessão anterior) mostra o Mapa K da ECU em `0xA2…0xB5` (162–181) e um bloco em que o ProgBase escreve **100** em todas as 144 células. Discussão em `curvas-mapas.md`.

## O que falta (DESCONHECIDO)

Seções restantes do `.lec` (nível `TIPO_SENSORE`/`RIF_SENSORE`/LEDs, Curva K, eixos, AutoCal), diff entre `208 pegeot.lec` e `pos cali`. Como fechar: rodar a receita acima localmente nos dois arquivos completos.
