import unittest, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gemini_gateway import status_label, preferred_models, normalized_gateway_status

class GeminiUXTests(unittest.TestCase):
    def test_saved_key_is_not_same_as_generation_success(self):
        d=normalized_gateway_status(configured=True, authenticated=True, status="MODELO_INDISPONIVEL", http_status=503)
        self.assertTrue(d['configured'])
        self.assertTrue(d['authenticated'])
        self.assertEqual(d['status'],"MODELO_INDISPONIVEL")
        self.assertIn('sobrecarga',status_label(d).lower())
    def test_no_key_versus_invalid_key_clear(self):
        self.assertIn('não configurad',status_label(normalized_gateway_status(False,False,"SEM_CHAVE")).lower())
        self.assertIn('inválid',status_label(normalized_gateway_status(True,False,"CREDENCIAL_INVALIDA")).lower())
    def test_ready_state_after_model_catalog_auth(self):
        d=normalized_gateway_status(True,True,"CREDENCIAL_VALIDADA")
        self.assertIn('validada',status_label(d).lower())
    def test_retry_cap_when_models_overloaded(self):
        found=['models/gemini-3.1-flash-lite','models/gemini-3.8-flash','models/gemini-3.6-flash']
        self.assertEqual(preferred_models(found,limit=3),['gemini-3.8-flash','gemini-3.6-flash','gemini-3.1-flash-lite'])
        self.assertEqual(len(preferred_models(found,limit=2)),2)
    def test_success_is_suggestion_not_proof(self):
        d=normalized_gateway_status(True,True,'SUGESTAO_NAO_E_PROVA')
        self.assertIn('não é prova',status_label(d).lower())

if __name__=='__main__':unittest.main()

class GeminiCompletionTests(unittest.TestCase):
    def test_complete_requires_finish_reason_stop(self):
        from gemini_gateway import complete_gemini_response
        self.assertFalse(complete_gemini_response({'candidates':[{'finishReason':'MAX_TOKENS','content':{'parts':[{'text':'### 1. MEDIDO:'}]}}]}))
        self.assertTrue(complete_gemini_response({'candidates':[{'finishReason':'STOP','content':{'parts':[{'text':'A validação é parcial.'}]}}]}))
    def test_generation_uses_small_thinking_budget(self):
        from gemini_gateway import generation_settings
        p=generation_settings('gemini-3.6-flash')
        self.assertEqual(p['thinkingConfig']['thinkingLevel'],'MINIMAL')
        self.assertGreaterEqual(p['maxOutputTokens'],1500)
