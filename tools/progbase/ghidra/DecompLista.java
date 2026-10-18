// Pós-script: decompila a lista de endereços (um 0xADDR por linha) e grava JSON linhas {a,n,c,ok}. Estável e retomável.
// Uso: -postScript DecompLista.java <lista.txt> <saida.jsonl> [segundos_por_funcao]
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DecompLista extends GhidraScript {
    static String esc(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break; case '\r': break; case '\t': b.append("\\t"); break;
                default: if (c < 32) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.append('"').toString();
    }
    @Override
    public void run() throws Exception {
        String[] a = getScriptArgs();
        int tmo = a.length > 2 ? Integer.parseInt(a[2]) : 30;
        Set<String> feitos = new HashSet<>();
        File out = new File(a[1]);
        if (out.exists()) for (String l : Files.readAllLines(out.toPath())) { int i = l.indexOf(','); if (l.startsWith("{\"a\":") && i > 0) feitos.add(l.substring(5, i)); }
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(out, true)));
        int n = 0, ok = 0;
        for (String l : Files.readAllLines(Paths.get(a[0]))) {
            l = l.trim(); if (l.isEmpty()) continue;
            long va = Long.parseLong(l.replace("0x", ""), 16);
            if (feitos.contains(Long.toString(va))) continue;
            if (monitor.isCancelled()) break;
            Address ad = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(va);
            Function f = getFunctionAt(ad);
            if (f == null) continue;
            String c; boolean good = false;
            try {
                DecompileResults r = di.decompileFunction(f, tmo, monitor);
                if (r.decompileCompleted()) { c = r.getDecompiledFunction().getC(); good = true; ok++; }
                else c = "// falha: " + r.getErrorMessage();
            } catch (Exception e) { c = "// excecao: " + e; }
            w.println("{\"a\":" + va + ",\"n\":" + esc(f.getName()) + ",\"ok\":" + good + ",\"c\":" + esc(c) + "}");
            if (++n % 200 == 0) w.flush();
        }
        w.close();
        println("DecompLista: " + n + " funcoes, " + ok + " ok");
    }
}
