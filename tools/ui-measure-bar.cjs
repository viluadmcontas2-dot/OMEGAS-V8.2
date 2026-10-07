// Mede uma barra de botões no Chromium real: node tools/ui-measure-bar.cjs <rota> <seletor> <saida.png> (skill omegas-ui-fluxo)
const {open,go,playwright}=require('../tests/ui/render/lib.js');
const [,,route,sel,out]=process.argv;
(async()=>{const {browser,page,errors}=await open(playwright().chromium,'connected',{viewport:{width:1280,height:720},scn:{noStalls:true}});
await go(page,route);await page.waitForTimeout(2200);
const m=await page.evaluate(sel=>{const bar=document.querySelector(sel);if(!bar)return 'sem barra';const items=[...bar.querySelectorAll('button,summary,input')].filter(e=>e.getBoundingClientRect().width>0&&!e.closest('[hidden]')).map(e=>e.getBoundingClientRect());
 const w=document.querySelector('.screen.active');return {n:items.length,left:Math.round(Math.min(...items.map(i=>i.left))),right:Math.round(Math.max(...items.map(i=>i.right))),tops:[...new Set(items.map(i=>Math.round(i.top)))],hs:[...new Set(items.map(i=>Math.round(i.height)))],overflowX:w.scrollWidth>w.clientWidth+1};},sel);
console.log(JSON.stringify(m),errors.join('|'));await page.screenshot({path:out});await browser.close();})();
