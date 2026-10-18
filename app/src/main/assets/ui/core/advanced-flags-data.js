// GERADO por tools/ci/gerar_flags_avancado.py a partir de docs/evidence/flags-do-original.json. Não editar à mão.
// Só entram bits PROVADOS (caixa ou lista da tela do ProgBase original, condição "sempre", sem ambiguidade).
(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  ns.AdvancedFlagsData = {
    FLAG_CONF1: { code: 3, bits: [
      { word: 0, mask: 0x0002, kase: 1, control: "ComboCarburante", kind: "lista", label: "Lista «Combustível»" },
      { word: 0, mask: 0x0010, kase: 14, control: "ComboMisuratorePressione", kind: "lista", label: "Lista «Medidor de pressão»" },
      { word: 0, mask: 0x0020, kase: 27, control: "CheckSequenzaInj", kind: "caixa", label: "Sequência de injeção manual" },
      { word: 0, mask: 0x0080, kase: 19, control: "CheckElettrovalvole", kind: "caixa", label: "Válvulas de gás abrem com antecipação" },
      { word: 0, mask: 0x2000, kase: 13, control: "ComboPressioneMisurata", kind: "lista", label: "Lista «Pressão medida»" },
      { word: 1, mask: 0x0001, kase: 8, control: "CheckTempAcqua", kind: "caixa", label: "Trocar de combustível pela temperatura da água" },
      { word: 1, mask: 0x0002, kase: 9, control: "CheckTempGas", kind: "caixa", label: "Trocar de combustível pela temperatura do gás" },
      { word: 1, mask: 0x0004, kase: 10, control: "CheckTempMotore", kind: "caixa", label: "Usar o sensor de temperatura do motor do carro" },
      { word: 1, mask: 0x0010, kase: 15, control: "MenuControlloIniettori", kind: "caixa", label: "Controle dos injetores habilitado" },
      { word: 1, mask: 0x0020, kase: 16, control: "ComboTurbo", kind: "lista", label: "Lista «Turbo»" },
      { word: 1, mask: 0x1000, kase: 22, control: "CheckIstantaneo", kind: "caixa", label: "Passar da gasolina ao gás em modo sequencial" },
      { word: 1, mask: 0x2000, kase: 23, control: "CheckParzializza", kind: "caixa", label: "Gasolina entra sozinha em rotação alta" },
      { word: 1, mask: 0x8000, kase: 25, control: "CheckAdattativita", kind: "caixa", label: "Adaptatividade OBD" },
    ] },
    FLAG_CONF2: { code: 192, bits: [
      { word: 0, mask: 0x0001, kase: 0, control: "CheckTagliaExtraIniettate", kind: "caixa", label: "Corte das injeções extras" },
      { word: 0, mask: 0x0002, kase: 1, control: "CheckPartenzaGasCaldo", kind: "caixa", label: "Partida a gás com o motor quente" },
      { word: 0, mask: 0x0004, kase: 2, control: "CheckValvetronik", kind: "caixa", label: "Valvetronic" },
      { word: 0, mask: 0x0010, kase: 4, control: "CheckAzzeraErroriObd", kind: "caixa", label: "Apagar erros OBD" },
      { word: 0, mask: 0x0020, kase: 5, control: "CheckDisabilitaCommutatore", kind: "caixa", label: "Desabilitar o comutador" },
      { word: 0, mask: 0x0040, kase: 6, control: "CheckRiscaldamento", kind: "caixa", label: "Aquecimento dos injetores" },
      { word: 0, mask: 0x0080, kase: 7, control: "CheckAzzeraBlackListObd", kind: "caixa", label: "Apagar erros OBD de forma seletiva" },
      { word: 0, mask: 0x0100, kase: 8, control: "CheckSpegniBuzzer", kind: "caixa", label: "Buzzer por tempo" },
      { word: 0, mask: 0x0200, kase: 9, control: "CheckSplitFuel", kind: "caixa", label: "Dividir combustível (split fuel)" },
      { word: 0, mask: 0x1000, kase: 12, control: "CheckStartAndStop", kind: "caixa", label: "Start & Stop" },
      { word: 0, mask: 0x2000, kase: 13, control: "CheckSwitchOnDiagnosis", kind: "caixa", label: "Ligar o diagnóstico (SwitchOnDiagnosis)" },
      { word: 0, mask: 0x4000, kase: 14, control: "CheckContributi", kind: "caixa", label: "Mapa de adição de gasolina" },
      { word: 1, mask: 0x0100, kase: 16, control: "CheckFlexFuel", kind: "caixa", label: "Flex fuel (álcool)" },
      { word: 1, mask: 0x0200, kase: 17, control: "CheckboxPreheatOnlyAfterSleepMode", kind: "caixa", label: "Aquecer injetores só depois do modo repouso" },
      { word: 1, mask: 0x0400, kase: 18, control: "CheckIncrementoMaxCorrente", kind: "caixa", label: "Aumentar a corrente máxima na troca para gás" },
      { word: 1, mask: 0x0800, kase: 19, control: "CheckboxPreheatAsyncMode", kind: "caixa", label: "Aquecimento dos injetores assíncrono" },
      { word: 1, mask: 0x2000, kase: 29, control: "CheckInjAcquisition", kind: "caixa", label: "Sequência de injeção manual (aquisição)" },
      { word: 1, mask: 0x4000, kase: 20, control: "CheckFlashLube", kind: "caixa", label: "Diagnóstico do nível de lubrificante" },
    ] },
    FLAG_CONF3: { code: 250, bits: [
      { word: 0, mask: 0x0004, kase: 1, control: "CheckTaglioPompaBenzina", kind: "caixa", label: "Corte da bomba de gasolina" },
      { word: 0, mask: 0x0008, kase: 2, control: "CheckPetrolObdConnection", kind: "caixa", label: "Conexão OBD quando roda a gasolina" },
      { word: 0, mask: 0x0010, kase: 3, control: "CheckCambioConSovrapp", kind: "caixa", label: "Troca com sobreposição (= progressões)" },
      { word: 0, mask: 0x0020, kase: 4, control: "CheckAntiStallo", kind: "caixa", label: "Anti-apagão (anti-stallo)" },
      { word: 0, mask: 0x0040, kase: 12, control: "CheckDeleteErrorMotoreSpento", kind: "caixa", label: "Apagar erros OBD com o motor desligado" },
      { word: 0, mask: 0x0080, kase: 5, control: "CheckFlashLubeSuTempAcqua", kind: "caixa", label: "Diagnóstico da temperatura da água (sistema VP)" },
      { word: 0, mask: 0x0100, kase: 6, control: "CheckOutDiagInfoSuKLine", kind: "caixa", label: "Nível alto na linha K em erro de diagnóstico" },
      { word: 0, mask: 0x0400, kase: 7, control: "CheckUsaCanPerObd", kind: "caixa", label: "Usar CAN para o OBD" },
      { word: 0, mask: 0x0800, kase: 8, control: "CheckAttivaPompaInRiserva", kind: "caixa", label: "Reativar a bomba na reserva" },
      { word: 0, mask: 0x1000, kase: 9, control: "CheckRiconoscimentoPieno", kind: "caixa", label: "Reconhecimento de tanque cheio" },
      { word: 0, mask: 0x2000, kase: 10, control: "CheckGiriRuotaFonica", kind: "caixa", label: "Roda fônica" },
      { word: 0, mask: 0x4000, kase: 11, control: "CheckEnableLandiConnect", kind: "caixa", label: "Landi Connect habilitado" },
      { word: 0, mask: 0x8000, kase: 13, control: "CheckCambioAutomatico", kind: "caixa", label: "Troca automática" },
      { word: 1, mask: 0x0004, kase: 15, control: "chkRitardoPassaggioInMinuti", kind: "caixa", label: "Atraso da passagem em minutos" },
      { word: 1, mask: 0x0008, kase: 16, control: "CheckBoxEnableExtLedKLine", kind: "caixa", label: "LED externo na linha K" },
      { word: 1, mask: 0x0020, kase: 18, control: "CheckBoxEnDiagDtcPowertrain", kind: "caixa", label: "Diagnóstico DTC do Powertrain" },
    ] }
  };
})(typeof window !== 'undefined' ? window : globalThis);
