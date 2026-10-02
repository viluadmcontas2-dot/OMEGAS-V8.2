(function(root){
 'use strict';
 const ns=root.OmegasUi=root.OmegasUi||{};
 class RefinementScreen {
   constructor(app){
     this.app=app;
     this.panel=new ns.AutoCalRefinePanel(root.document.getElementById('refinementScreenHost'),app,ns.AutoCalApi);
     this.unhook=app.scheduler.addHook('context',()=>this.refresh());
     this.unsubscribe=app.store.subscribeSelected(s=>`${s.route}|${s.visible}|${JSON.stringify(s.routeContext)}`,()=>this.refresh(true), true);
   }
   refresh(force){
     const s=this.app.store.get();if(s.route!=='refino'||s.visible===false)return;
     this.panel.refresh();
     if(s.routeContext?.review && !this.panel.reviewOpen && this.panel.operation.phase==='idle'){
       this.app.store.patch({routeContext:null});this.panel.openReview();
     }
   }
 }
 ns.RefinementScreen=RefinementScreen;
 function boot(){const app=root.OmegasApp;if(app?.store&&app?.scheduler&&!app.refinementScreen)app.refinementScreen=new RefinementScreen(app);}
 root.addEventListener?.('omegas-ready',boot,{once:true});boot();
})(typeof window!=='undefined'?window:globalThis);
