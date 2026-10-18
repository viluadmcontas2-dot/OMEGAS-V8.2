// Pré-script: cria funções nos endereços RTTI (TSV: 0xADDR<TAB>nome) e as renomeia. Marca: OBS-COD (nomes vêm do RTTI Delphi).
// Uso: -preScript SeedNomes.java <arquivo.tsv>
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.SourceType;
import java.io.*;

public class SeedNomes extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] a = getScriptArgs();
        int novos = 0, ren = 0, falhas = 0;
        try (BufferedReader r = new BufferedReader(new FileReader(a[0]))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] kv = l.split("\t");
                if (kv.length < 2) continue;
                Address ad = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(Long.parseLong(kv[0].replace("0x", ""), 16));
                Function f = getFunctionAt(ad);
                if (f == null) {
                    disassemble(ad);
                    f = createFunction(ad, null);
                    if (f != null) novos++;
                }
                if (f == null) { falhas++; continue; }
                try { f.setName(kv[1].replaceAll("[^A-Za-z0-9_]", "_"), SourceType.USER_DEFINED); ren++; } catch (Exception e) { falhas++; }
            }
        }
        println("SeedNomes: novos=" + novos + " renomeados=" + ren + " falhas=" + falhas);
    }
}
