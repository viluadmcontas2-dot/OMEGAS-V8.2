"""P1/P2: replica de quadros originais e Delta RPM. Sem exe, USB ou ECU.
TNumericEdit 0x96B924: rounding de fracoes e HIPOTESE de RE, nao CPU executada.
0x4C97D8 -> 0x979E48: outputs inteiros documentados de execucao Unicorn anterior.
0x4A33FC: s16 little-endian no offset 0x1E do buffer TStreamDati.
"""
from decimal import Decimal

MAX_DFM=(6000,1000,10000,1000,1000)
FABRICA=(1600,100,1500,2,20)
PASSOS_JS=(50,5,100,5,5)
QUADROS=(
 (0,1800,"14 33 01 00 08 07 57"),
 (0,1600,"14 33 01 00 40 06 8E"),
 (0,1601,"14 33 01 00 41 06 8F"),
 (1,150,"14 33 01 01 96 00 DF"),
 (2,1000,"14 33 01 02 E8 03 35"),
 (3,5,"14 33 01 03 05 00 50"),
 (4,30,"14 33 01 04 1E 00 6A"),
)
REAL_48=(
 ("48 01 49 53 22 c6 05 3c 26 00 00 00 00 c9 09 00 88 38 14 e3 0d 38 d8 01 00 00 00 00 00 00 00 00 00 8d 09 00 00 00 00 df",1478),
 ("48 01 49 53 22 f9 03 3b 1f 00 00 00 00 76 06 00 88 38 14 21 0e 38 93 01 00 00 00 00 00 00 00 00 00 69 06 00 00 00 00 85",1017),
)

def checksum(b):
 return sum(b)&255

def arredondar_candidato(x):
 x=Decimal(str(x))
 if not x.is_finite(): raise ValueError("nao finito")
 return int(x+(Decimal("0.5") if x>=0 else Decimal("-0.5")))

def quadro_indexado(indice,valor):
 if type(indice) is not int or indice not in range(5): raise ValueError("indice")
 if type(valor) is not int or not 0<=valor<=MAX_DFM[indice]: raise ValueError("limite DFM")
 body=bytes([0x14,0x33,0x01,indice])+valor.to_bytes(2,"little")
 return body+bytes([checksum(body)])

def tecla_original(indice,texto,key="Enter"):
 if key!="Enter": return None
 return quadro_indexado(indice,arredondar_candidato(Decimal(texto)))

def s16_delta(two):
 if len(two)!=2: raise ValueError("delta incompleto")
 return int.from_bytes(two,"little",signed=True)

def parse_48(frame):
 if len(frame)!=40 or frame[:3]!=bytes.fromhex("48 01 49"): raise ValueError("eco ou tamanho")
 resp=frame[3:]
 if resp[:2]!=bytes([0x53,34]) or checksum(resp[:-1])!=resp[-1]: raise ValueError("ACK/CRC")
 payload=resp[2:-1]
 return {"rpm":int.from_bytes(payload[:2],"little"),"delta":s16_delta(payload[30:32]),
         "delta_bytes":payload[30:32].hex(" ").upper()}

def quadro_48_sintetico(rpm,delta):
 if not 0<=rpm<=65535 or not -32768<=delta<=32767: raise ValueError("limites")
 payload=bytearray(34)
 payload[:2]=rpm.to_bytes(2,"little")
 payload[30:32]=delta.to_bytes(2,"little",signed=True)
 body=bytes([0x53,34])+payload
 return bytes.fromhex("48 01 49")+body+bytes([checksum(body)])

if __name__=="__main__":
 import json
 print(json.dumps({"tipo":"REPLICA_PYTHON_NAO_CPU_ORIGINAL",
 "quadros":[{"indice":i,"valor":v,"bate":quadro_indexado(i,v).hex(" ").upper()==h} for i,v,h in QUADROS],
 "amostras_48":[parse_48(bytes.fromhex(h)) for h,_ in REAL_48],
 "corpus_48":"1189 quadros 48 01 reais com offset 30-31 igual a zero (5+15+1169); subtipo do campo da tela ainda nao provado",
 "arredondamento_fracoes":"hipotese, nao reproduzido em binario"},ensure_ascii=False,indent=2))
