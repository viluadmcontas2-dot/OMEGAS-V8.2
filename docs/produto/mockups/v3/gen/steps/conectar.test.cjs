const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict');
const p='docs/produto/mockups/v3/project/';
const names=['01-Conectar','01b-Conectar-permissao','01c-Conectar-retomando','01d-Conectar-problema'];
for(const name of names){
 const html=fs.readFileSync(p+name+'.dc.html','utf8');
 assert.match(html,/id="conectar"/,'botão de conexão deve existir');
 const script=html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
 const c=vm.runInNewContext(script+';new Component()', {DCLogic:class{forceUpdate(){this.updates=(this.updates||0)+1;}}});
 const initial=c.renderVals();
 for(const action of ['conectar','concluir','deixar','detalhes'])assert.equal(typeof initial[action],'function');
 c.renderVals().detalhes();assert.equal(c.renderVals().detailsLabel,'Voltar');assert.equal(c.updates,1);c.renderVals().detalhes();assert.equal(c.renderVals().detailsLabel,'Ver detalhes');
 if(name.startsWith('01c')){assert.equal(initial.resumeDisabled,false);c.renderVals().deixar();assert.equal(c.renderVals().resumeDisabled,false);c.renderVals().concluir();assert.equal(c.renderVals().resumeDisabled,true);assert.match(c.renderVals().pending,/conferida/);}
 else {c.renderVals().concluir();assert.equal(c.renderVals().resumeDisabled,true);c.renderVals().conectar();assert.doesNotMatch(c.renderVals().status,/Central conectada/);}
}
console.log('PASS: quatro estados, ajuda reversível, retomada manual e conexão sem falso sucesso');
