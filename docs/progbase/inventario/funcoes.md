# Funções do ProgBase.exe nas unidades do programa

Gerado por `tools/progbase/inventario_backlog.py` (SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`).

- **Método (OBS-COD):** descida recursiva a partir do ponto de entrada, de todos os nomes conhecidos (RTTI, exportações PE, vtables) e das chamadas diretas; varredura de lacunas que começam com prólogo de função. **Não segue** chamadas virtuais/indiretas nem tabelas de despacho não reconhecidas.
- **Unidade (DERIVADO):** fronteiras dadas pelos marcadores `@@Unidade@Initialize/Finalize` exportados; faixas do programa: 0x00405DC0–0x00526CA4, 0x008F02A4–0x0098A10C. Fora delas há RTL/VCL/TeeChart/Indy (não inventariadas: só 7779 funções de biblioteca foram alcançadas).
- **Funções nas unidades do programa:** 4404; por status: nomeada=1981, sem_nome_resumida_por_handler=665, sem_nome_citada_em_doc=12, sem_nome_sem_analise=1746.
- Fonte dos nomes: exportacao=1146, manual-DERIVADO=1, rtti-dinamico=80, rtti-metodo=558, vtable=157, vtable-ctor=39.

| Unidade | Funções | Nomeadas | Sem nome | Bytes (últ. instrução) | Bytes sem nome |
|---|---:|---:|---:|---:|---:|
| Aeb3dmap | 64 | 50 | 14 | 29627 | 1047 |
| Aebbee | 82 | 15 | 67 | 14256 | 10862 |
| Aebbeelight | 58 | 31 | 27 | 33064 | 10305 |
| Aebbeeprog | 22 | 5 | 17 | 5302 | 5222 |
| Aebcheckbox | 33 | 29 | 4 | 6596 | 1378 |
| Aebcom | 24 | 22 | 2 | 3550 | 321 |
| Aebcombobox | 34 | 33 | 1 | 9104 | 88 |
| Aebconnection | 10 | 9 | 1 | 1028 | 87 |
| Aebcontrolsregister | 592 | 4 | 588 | 94720 | 94189 |
| Aebcrypt | 6 | 2 | 4 | 2440 | 2408 |
| Aebdevice | 4 | 2 | 2 | 398 | 366 |
| Aebftdi | 24 | 22 | 2 | 4658 | 873 |
| Aebgrid | 110 | 97 | 13 | 41190 | 6199 |
| Aebgroupbox | 12 | 7 | 5 | 1060 | 395 |
| Aeblabel | 31 | 30 | 1 | 5118 | 75 |
| Aeblabelededit | 38 | 33 | 5 | 13245 | 368 |
| Aebprotocol | 149 | 111 | 38 | 43441 | 3265 |
| Aebradiogroup | 13 | 12 | 1 | 3797 | 116 |
| Aebregistrydata | 18 | 17 | 1 | 5233 | 105 |
| Aebrichedit | 14 | 13 | 1 | 2981 | 45 |
| Aebscrollbox | 13 | 12 | 1 | 1595 | 88 |
| Aebshellctrls | 317 | 169 | 148 | 62987 | 17159 |
| Aebstrings | 9 | 8 | 1 | 2168 | 859 |
| Aebtcp | 27 | 25 | 2 | 2185 | 412 |
| Aebvectoritems | 54 | 54 | 0 | 13256 | 0 |
| Aggkey | 148 | 7 | 141 | 33302 | 30750 |
| Autocal_colorsettings | 19 | 8 | 11 | 3001 | 2038 |
| Autocal_dm | 53 | 11 | 42 | 9522 | 8783 |
| Autocal_dm_ee | 4 | 3 | 1 | 190 | 71 |
| Autocal_settings | 11 | 8 | 3 | 3198 | 2132 |
| Autocal_ui | 124 | 60 | 64 | 27774 | 14382 |
| Buttonimage | 29 | 11 | 18 | 2789 | 2113 |
| Calibra | 50 | 18 | 32 | 26078 | 15356 |
| Calibraswitch | 15 | 11 | 4 | 3284 | 2154 |
| Centricelle | 7 | 6 | 1 | 1589 | 111 |
| Centricelleauto | 8 | 3 | 5 | 2570 | 1471 |
| Centricellek | 8 | 7 | 1 | 1641 | 111 |
| Checkimage | 9 | 3 | 6 | 1250 | 1068 |
| Childlabel | 21 | 21 | 0 | 1860 | 0 |
| Commonmodules | 54 | 2 | 52 | 49012 | 48980 |
| Componentresizer | 22 | 20 | 2 | 4502 | 118 |
| Config | 443 | 261 | 182 | 276880 | 164755 |
| Corrcalibra | 12 | 9 | 3 | 5232 | 878 |
| Cryptengine | 11 | 2 | 9 | 6971 | 6939 |
| Datarefresher | 18 | 17 | 1 | 1453 | 75 |
| Diagnosi | 32 | 28 | 4 | 44857 | 14218 |
| Ecufunctionalities | 9 | 2 | 7 | 3686 | 3649 |
| Editex | 30 | 27 | 3 | 3391 | 206 |
| Ffdatamodulesinglearray | 23 | 3 | 20 | 18600 | 15906 |
| File | 41 | 16 | 25 | 25642 | 14928 |
| Formmainbase | 7 | 4 | 3 | 676 | 534 |
| Gauge | 19 | 3 | 16 | 2138 | 1843 |
| Graficodrt | 120 | 7 | 113 | 40768 | 39799 |
| Graficoscope | 82 | 37 | 45 | 39467 | 29980 |
| Helpscope | 2 | 2 | 0 | 32 | 0 |
| Hsv | 7 | 2 | 5 | 938 | 906 |
| Httpthread | 8 | 3 | 5 | 2870 | 2536 |
| Inneruilist | 8 | 2 | 6 | 454 | 412 |
| Iodata | 297 | 4 | 293 | 351458 | 348361 |
| Keycombobox | 26 | 13 | 13 | 6324 | 4087 |
| Landiconnect | 20 | 14 | 6 | 9080 | 4126 |
| Licensemanager | 32 | 2 | 30 | 7094 | 7062 |
| Main | 83 | 43 | 40 | 93293 | 50563 |
| Market | 22 | 3 | 19 | 3382 | 3113 |
| Messaggio | 2 | 2 | 0 | 32 | 0 |
| Modificamappa | 12 | 12 | 0 | 2199 | 0 |
| Modmappa | 7 | 5 | 2 | 1390 | 322 |
| Modulesversioning | 2 | 2 | 0 | 32 | 0 |
| Numericedit | 35 | 11 | 24 | 9391 | 5370 |
| Numericeditex | 50 | 50 | 0 | 11514 | 0 |
| Panelimage | 17 | 4 | 13 | 3562 | 1128 |
| Pdflib_ocx | 99 | 31 | 68 | 8345 | 5535 |
| Progthread | 4 | 3 | 1 | 203 | 145 |
| Refreshthread | 21 | 3 | 18 | 2308 | 2104 |
| Riferimentimappa | 5 | 5 | 0 | 1226 | 0 |
| Scaricafile | 8 | 4 | 4 | 4311 | 4106 |
| Scopefileutility | 11 | 2 | 9 | 3197 | 3165 |
| Serialdata | 191 | 161 | 30 | 51567 | 7664 |
| Service | 37 | 24 | 13 | 12697 | 5459 |
| Splash | 3 | 3 | 0 | 119 | 0 |
| Stampa | 2 | 2 | 0 | 32 | 0 |
| Strategiapanda_dm | 16 | 9 | 7 | 598 | 199 |
| Strategiapanda_dm_ee | 4 | 3 | 1 | 190 | 71 |
| Strategiapanda_ui | 20 | 16 | 4 | 4831 | 2585 |
| Strategiatempimorti_dm | 21 | 9 | 12 | 747 | 348 |
| Strategiatempimorti_dm_ee | 4 | 3 | 1 | 190 | 71 |
| Strategiatempimorti_ui | 24 | 15 | 9 | 11683 | 9538 |
| Tracce | 3 | 3 | 0 | 178 | 0 |
| Transformations | 16 | 14 | 2 | 1737 | 150 |
| Translatorext | 28 | 18 | 10 | 10046 | 1868 |
| Unitumtransform | 9 | 2 | 7 | 643 | 611 |
| Utilitiesenvironmentcheck | 3 | 2 | 1 | 350 | 318 |
| Valueeditex | 44 | 39 | 5 | 11989 | 386 |
| Visualizza | 14 | 9 | 5 | 18091 | 1537 |
