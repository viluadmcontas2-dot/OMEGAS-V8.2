"""RED/GREEN P1/P2: retrocompatibilidade observacional, sem comunicacao serial."""
import unittest
from decimal import Decimal
from tools.emulacao.antiapagao_bordas_p1p2 import (
 QUADROS,REAL_48,MAX_DFM,arredondar_candidato,quadro_indexado,tecla_original,
 parse_48,quadro_48_sintetico,s16_delta
)
class BordasP1P2Test(unittest.TestCase):
 def test_01_reproduz_sete_quadros_original(self):
  for i,v,h in QUADROS:
   with self.subTest(i=i,v=v): self.assertEqual(quadro_indexado(i,v).hex(" ").upper(),h)
 def test_02_enter_manda_mesmo_valor(self):
  self.assertEqual(tecla_original(0,"1600"),quadro_indexado(0,1600))
 def test_03_tab_nao_confirma(self):
  self.assertIsNone(tecla_original(0,"1601","Tab"))
 def test_04_limites_nao_implicam_passo(self):
  for i,m in enumerate(MAX_DFM):
   self.assertEqual(len(quadro_indexado(i,m)),7)
   self.assertRaises(ValueError,quadro_indexado,i,-1)
   self.assertRaises(ValueError,quadro_indexado,i,m+1)
  self.assertEqual(quadro_indexado(0,1601).hex(" ").upper(),"14 33 01 00 41 06 8F")
  self.assertEqual(quadro_indexado(3,3).hex(" ").upper(),"14 33 01 03 03 00 4E")
 def test_05_framing_checksum_little_endian(self):
  for i,v,_ in QUADROS:
   q=quadro_indexado(i,v)
   self.assertEqual(q[3],i)
   self.assertEqual(int.from_bytes(q[4:6],"little"),v)
   self.assertEqual(sum(q[:-1])&255,q[-1])
 def test_06_arredondamento_apenas_hipotese(self):
  for n,v in [("0.49",0),("0.5",1),("1.5",2),("2.5",3),("-0.49",0),("-0.5",-1),("-2.5",-3)]:
   self.assertEqual(arredondar_candidato(Decimal(n)),v)
  self.assertNotEqual(round(2.5),arredondar_candidato(Decimal("2.5")))
 def test_07_quadros_48_fisicos_zerados_na_posicao(self):
  for hx,rpm in REAL_48:
   d=parse_48(bytes.fromhex(hx))
   self.assertEqual((d["rpm"],d["delta"],d["delta_bytes"]),(rpm,0,"00 00"))
 def test_08_sinteticos_extremos_de_s16(self):
  for n in (-32768,-1000,-2,-1,0,1,2,1000,32767):
   self.assertEqual(parse_48(quadro_48_sintetico(980,n))["delta"],n)
 def test_09_rejeita_respostas_corrompidas(self):
  f=quadro_48_sintetico(980,-2)
  for b in (f[:-1],f[:-1]+b"\x00",f[1:],bytes.fromhex("48 02 4a")+f[3:]):
   self.assertRaises(ValueError,parse_48,b)
 def test_10_nao_inventa_escala_delta(self):
  self.assertEqual(s16_delta(bytes.fromhex("FE FF")),-2)
  self.assertEqual(s16_delta(bytes.fromhex("00 80")),-32768)
 def test_11_tecla_que_nao_e_enter_nao_grava(self):
  for key in ("Escape","Tab","Space"):
   self.assertIsNone(tecla_original(3,"5",key))
if __name__=="__main__":unittest.main()
