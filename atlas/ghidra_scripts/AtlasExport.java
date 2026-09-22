import ghidra.app.decompiler.*;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class AtlasExport extends GhidraScript {
    private static final int MAX_FUNCS = 12000;
    private static final int MAX_DEPTH = 8;
    private static final int HUB_LIMIT = 48;
    private static final int MAX_DECOMPILE = 2500;

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("\t"," ").replace("\r"," ").replace("\n"," ");
    }
    private String joinFns(Set<Function> fs) {
        ArrayList<String> out = new ArrayList<>();
        for (Function f: fs) if (!f.isExternal()) out.add(f.getEntryPoint().toString());
        Collections.sort(out);
        return String.join(";", out);
    }
    private boolean interesting(String s) {
        String x=s.toLowerCase(Locale.ROOT);
        return x.contains("autocal") || x.contains("auto cal") || x.contains("automatch") ||
               x.contains("mul_act") || x.contains("acquired_zones") || x.contains("mnfld") ||
               x.contains("petr_inj") || x.contains("gas_mnfld") || x.contains("rifautocal") ||
               x.contains("curve") || x.contains("point") || x.contains("band") ||
               x.contains("acquisition") || x.contains("acqusition") || x.contains("cursorlimit") ||
               x.contains("chart") || x.contains("series");
    }

    @Override public void run() throws Exception {
        String[] argv=getScriptArgs();
        if (argv.length < 2) throw new IllegalArgumentException("AtlasExport <out-dir> <seed-file>");
        Path out=Paths.get(argv[0]); Path decompDir=out.resolve("decomp");
        Files.createDirectories(decompDir);
        Listing listing=currentProgram.getListing();
        FunctionManager fm=currentProgram.getFunctionManager();
        ReferenceManager rm=currentProgram.getReferenceManager();

        LinkedHashSet<Function> seeds=new LinkedHashSet<>();
        try (BufferedReader br=Files.newBufferedReader(Paths.get(argv[1]),StandardCharsets.UTF_8)) {
            String line;
            while ((line=br.readLine())!=null) {
                line=line.trim(); if (line.isEmpty()) continue;
                String token=line.split("\\s+")[0];
                try {
                    long value=Long.decode(token);
                    Address a=toAddr(value);
                    Function f=fm.getFunctionContaining(a);
                    if (f==null) f=fm.getFunctionAt(a);
                    if (f!=null && !f.isExternal()) seeds.add(f);
                } catch (Exception ignored) {}
            }
        }

        ArrayList<String> stringLines=new ArrayList<>();
        DataIterator di=listing.getDefinedData(true);
        while (di.hasNext()) {
            Data d=di.next();
            Object v=d.getValue();
            if (!(v instanceof String)) continue;
            String s=(String)v;
            if (!interesting(s)) continue;
            ArrayList<String> refs=new ArrayList<>();
            ReferenceIterator ri=rm.getReferencesTo(d.getAddress());
            while (ri.hasNext()) {
                Reference r=ri.next(); refs.add(r.getFromAddress().toString());
                Function f=fm.getFunctionContaining(r.getFromAddress());
                if (f!=null && !f.isExternal()) seeds.add(f);
            }
            Collections.sort(refs);
            String b64=Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
            stringLines.add(d.getAddress()+"\t"+b64+"\t"+String.join(";",refs));
        }
        Files.write(out.resolve("strings.tsv"),stringLines,StandardCharsets.UTF_8);

        if (seeds.isEmpty()) throw new IllegalStateException("No AutoCal seed functions resolved from canonical leads");

        Map<Function,Integer> dist=new HashMap<>();
        ArrayDeque<Function> q=new ArrayDeque<>();
        for (Function f:seeds) { dist.put(f,0); q.add(f); }
        while (!q.isEmpty() && dist.size()<MAX_FUNCS) {
            Function f=q.removeFirst(); int d=dist.get(f);
            Set<Function> called=f.getCalledFunctions(monitor);
            Set<Function> callers=f.getCallingFunctions(monitor);
            boolean boundary=d>0 && (called.size()+callers.size()>HUB_LIMIT);
            if (d>=MAX_DEPTH || boundary) continue;
            LinkedHashSet<Function> next=new LinkedHashSet<>(); next.addAll(called); next.addAll(callers);
            for (Function n:next) {
                if (n==null || n.isExternal() || dist.containsKey(n)) continue;
                dist.put(n,d+1); q.addLast(n);
                if (dist.size()>=MAX_FUNCS) break;
            }
        }

        boolean functionCapHit=!q.isEmpty();
        ArrayList<Function> fs=new ArrayList<>(dist.keySet());
        fs.sort((a,b)->{
            int x=Integer.compare(dist.get(a),dist.get(b));
            return x!=0?x:a.getEntryPoint().compareTo(b.getEntryPoint());
        });
        ArrayList<String> fnLines=new ArrayList<>();
        fnLines.add("entry\tdistance\tmin\tmax\tname\tboundary\tcallers\tcallees");
        for (Function f:fs) {
            Set<Function> called=f.getCalledFunctions(monitor), callers=f.getCallingFunctions(monitor);
            boolean boundary=dist.get(f)>0 && (called.size()+callers.size()>HUB_LIMIT);
            fnLines.add(f.getEntryPoint()+"\t"+dist.get(f)+"\t"+f.getBody().getMinAddress()+"\t"+f.getBody().getMaxAddress()+"\t"+safe(f.getName(true))+"\t"+boundary+"\t"+joinFns(callers)+"\t"+joinFns(called));
        }
        Files.write(out.resolve("functions.tsv"),fnLines,StandardCharsets.UTF_8);

        DecompInterface iface=new DecompInterface();
        iface.setOptions(new DecompileOptions()); iface.toggleCCode(true); iface.toggleSyntaxTree(true);
        iface.openProgram(currentProgram);
        int n=0;
        boolean decompileCapHit=fs.size()>MAX_DECOMPILE;
        for (Function f:fs) {
            if (n>=MAX_DECOMPILE) break;
            DecompileResults dr=iface.decompileFunction(f,15,monitor);
            if (dr!=null && dr.decompileCompleted() && dr.getDecompiledFunction()!=null) {
                String c=dr.getDecompiledFunction().getC();
                Files.writeString(decompDir.resolve(f.getEntryPoint().toString()+".c"),c,StandardCharsets.UTF_8);
            }
            n++;
        }
        iface.dispose();
        String meta="{\"schema\":\"omegas.atlas.ghidra-index.v1\",\"program\":\""+safe(currentProgram.getName())+"\",\"seed_functions\":"+seeds.size()+",\"reachable_functions\":"+fs.size()+",\"function_cap\":"+MAX_FUNCS+",\"function_cap_hit\":"+functionCapHit+",\"decompile_attempts\":"+Math.min(fs.size(),MAX_DECOMPILE)+",\"decompile_cap\":"+MAX_DECOMPILE+",\"decompile_cap_hit\":"+decompileCapHit+"}\n";
        Files.writeString(out.resolve("meta.json"),meta,StandardCharsets.UTF_8);
        println(meta);
    }
}
