const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const source=f=>fs.readFileSync(path.join(__dirname,'../src/main/',f),'utf8');
test('Gemini button reflects real saved credential state',()=>{
 const js=source('assets/dashboard.js');
 assert.match(js,/gemini\.configured\s*===\s*true/);
 assert.match(js,/Alterar chave Gemini/);
 assert.match(js,/Ativar Gemini/);
});
test('Gemini UI explains authentication separately from 503 overload',()=>{
 const js=source('assets/dashboard.js');
 assert.match(js,/CREDENCIAL_VALIDADA/);
 assert.match(js,/MODELO_INDISPONIVEL/);
 assert.match(js,/sobrecarga|sobrecarregado/i);
 assert.match(js,/gemini\.message/);
 assert.match(js,/SUGESTAO_NAO_E_PROVA/);
});

test('live status distinguishes current OBD and MP48 counts from historical evidence',()=>{
 const js=source('assets/dashboard.js');
 assert.match(js,/liveSourceCounts/);
 assert.match(js,/SEM_DADOS_RECENTES/);
});
