"""DERIVADO: contratos do leitor; oráculos OBS-COD/OBS-FIO opcionais e explícitos."""
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/progbase'))
import protocolo_log as log
import protocolo_x87 as x87


class ProtocoloTest(unittest.TestCase):
    def test_respostas_e_falhas_nao_sao_sucesso(self):
        req = bytes.fromhex('01 00 3A 3B')
        for tail, expected in [('53 00 53', 'ACK_53'), ('CA 01 08 D3', 'NAK_CA'),
                               ('96', 'RETRY_96'), ('A7', 'FLASH_A7'),
                               ('53 01', 'CURTA'), ('53 00 52', 'CK_INVALIDO'), ('', 'ECO_SO')]:
            with self.subTest(tail=tail):
                self.assertEqual(log.classificar(dict(req=req, resp=req + bytes.fromhex(tail)))[0], expected)
        self.assertEqual(log.classificar(dict(req=req, resp=b''))[0], 'SEM_RESPOSTA')
        self.assertEqual(log.classificar(dict(req=req, resp=b'\x00'))[0], 'ECO_DIVERGENTE')
        self.assertEqual(log.classificar(dict(req=b'\x80', resp=b'\x80'))[0], 'WAKE_80')

    def test_leituras_fragmentadas_e_fecho(self):
        lines = []
        def event(seq, op, detail, response=''):
            lines.extend([f'{seq} 0.0 ProgBase.exe {op} Silabser0 {detail}',
                          f'{seq} 0.0 SUCCESS {response}'])
        event(1, 'IRP_MJ_CREATE', '')
        event(2, 'IOCTL_SERIAL_SET_BAUD_RATE', 'Rate: 38400')
        event(3, 'IRP_MJ_WRITE', 'Length 3: 00 25 25')
        event(4, 'IRP_MJ_READ', 'Length 3', 'Length 3: 00 25 25')
        event(5, 'IRP_MJ_READ', 'Length 2', 'Length 2: 53 01')
        event(6, 'IRP_MJ_READ', 'Length 2', 'Length 2: 02 56')
        event(7, 'IRP_MJ_CLOSE', '')
        event(8, 'IRP_MJ_READ', 'Length 1', 'Length 1: FF')
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'fixture.log'
            p.write_text('\n'.join(lines))
            transactions = log.transacoes(p)
        self.assertEqual(len(transactions), 1)
        self.assertEqual(transactions[0]['baud'], 38400)
        self.assertEqual(transactions[0]['resp'], bytes.fromhex('00 25 25 53 01 02 56'))
        self.assertEqual(log.classificar(transactions[0])[0], 'ACK_53')

    def test_conversao_mascara_sinal_formula_e_limites(self):
        self.assertEqual(x87.raw_para_fisico(255, sinal=True, transformacao='ttNone'), -1)
        self.assertEqual(x87.raw_para_fisico(0xF123, tamanho=2, mascara=0xFF), 35)
        self.assertEqual(x87.raw_para_fisico(1536, tamanho=2, coef=(1, 0, 0, 512)), 3)
        self.assertEqual(x87.raw_para_fisico(0, coef=(1, 0, 0, 0)), x87.ERRO_FORMULA)
        self.assertEqual(x87.fisico_para_raw(3, tamanho=2, coef=(1, 0, 0, 512)), 1536)
        self.assertEqual(x87.fisico_para_raw(12, vmin=1, vmax=10), 10)
        self.assertEqual(x87.fisico_para_raw(-2.5, sinal=True), -3)

    def test_conversao_original_trunca_none_e_limita_par_com_zero(self):
        # OBS-COD: 0x009752C8 executada no Unicorn, sem Single/mesclagem.
        cases = [(-2.5, dict(transformacao='ttNone'), -2),
                 (2.5, dict(transformacao='ttNone'), 2),
                 (-3., dict(sinal=False), -2),
                 (-3., dict(sinal=True), -3),
                 (-20., dict(vmin=0., vmax=10.), 0),
                 (20., dict(vmin=-10., vmax=0.), 0),
                 (0., dict(vmin=10., vmax=1.), 10)]
        for value, params, expected in cases:
            with self.subTest(value=value, params=params):
                self.assertEqual(x87.fisico_para_raw(value, **params), expected)

    def test_orquestrador_valida_antes_de_escrever(self):
        import protocolo_gerar as gerar
        with tempfile.TemporaryDirectory() as tmp:
            destination = Path(tmp) / 'output'
            with self.assertRaises(FileNotFoundError):
                gerar.gerar(Path(tmp) / 'ausente.exe', [('LN', Path(tmp) / 'ausente.log')], destination, ROOT)
            self.assertFalse(destination.exists())

    def test_orquestrador_ordem_e_falha_interrompe(self):
        import protocolo_gerar as gerar
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            exe, capture = p / 'input.exe', p / 'input.log'
            exe.write_bytes(b'fixture')
            capture.write_text('fixture')
            calls = []
            def runner(argv, **kwargs):
                calls.append(argv)
                self.assertTrue(kwargs['check'])
            with patch.object(gerar.subprocess, 'run', side_effect=runner):
                gerar.gerar(exe, [('LN', capture)], p / 'out', ROOT)
            self.assertEqual([Path(c[1]).stem for c in calls],
                             ['protocolo_comandos', 'protocolo_vetores', 'protocolo_matriz', 'protocolo_backlog'])
            with patch.object(gerar.subprocess, 'run', side_effect=RuntimeError('falha')) as run:
                with self.assertRaises(RuntimeError):
                    gerar.gerar(exe, [('LN', capture)], p / 'out', ROOT)
                self.assertEqual(run.call_count, 1)

    @unittest.skipUnless(os.environ.get('PROGBASE_EXE') and Path(os.environ.get('PROGBASE_EXE', '')).is_file(),
                         'PROGBASE_EXE ausente; não há prova contra binário')
    def test_original_x87_e_instrucoes(self):
        if not all(importlib.util.find_spec(m) for m in ('unicorn', 'pefile', 'capstone')):
            self.skipTest('oráculo requer unicorn, pefile e capstone')
        import protocolo_comandos as commands
        self.assertEqual(log.sha256_arquivo(os.environ['PROGBASE_EXE']), commands.EXE_SHA256)
        self.assertEqual(commands.verificar_codigo(os.environ['PROGBASE_EXE']), [])
        original = x87.ConversorOriginal(os.environ['PROGBASE_EXE'])
        for raw in (0, 1, 127, 128, 255, 256, 1536, 65535):
            with self.subTest(raw=raw):
                params = dict(tamanho=2, coef=(1., 0., 0., 512.))
                self.assertEqual(original.raw_para_fisico(raw, **params), x87.raw_para_fisico(raw, **params))

    @unittest.skipUnless(os.environ.get('PROGBASE_LOG') and Path(os.environ.get('PROGBASE_LOG', '')).is_file(),
                         'PROGBASE_LOG ausente; não há replay real')
    def test_log_real_reproduz_captura(self):
        import protocolo_matriz as matriz
        report = matriz.montar(os.environ['PROGBASE_LOG'])
        self.assertGreater(report['transacoes'], 0)
        self.assertGreater(report['emulador']['pares_regenerados'], 0)
        self.assertEqual(report['emulador']['pares_divergentes'], 0)


if __name__ == '__main__':
    unittest.main()
