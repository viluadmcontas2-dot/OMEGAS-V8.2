(function(root){'use strict';const ns=root.OmegasUi=root.OmegasUi||{};
 function consumeLaunchRoute(surface,router){const route=surface.omegasPendingRoute;if(route!=='refino'){surface.omegasPendingRoute=null;return false;}if(!router)return false;surface.omegasPendingRoute=null;return router.navigate('refino');}
 function suggestion(eq){return eq?.autopilot?.phase==='PROPOSTA_PRONTA'?{title:'Curva refinada pronta',route:'refino',context:{review:true}}:null;}
 ns.consumeLaunchRoute=consumeLaunchRoute;ns.RefinementLaunch={suggestion};
})(typeof window!=='undefined'?window:globalThis);
