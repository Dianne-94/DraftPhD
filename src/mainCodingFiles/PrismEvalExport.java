package mainCodingFiles;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PrismEvalExport {

	private PrismEvalExport() {}

    public static String newRunId() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
    }
    public static Path runDir(String baseOutDir, String runId) {
        Path p = Paths.get(baseOutDir, runId);
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    // ----- CSV helpers -----
    private static void ensureHeader(Path p, String header) throws IOException {
        if (Files.notExists(p) || Files.size(p) == 0) {
            Files.createDirectories(p.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(p)) { w.write(header); w.newLine(); }
        }
    }
    private static void appendRow(Path p, List<String> cols) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardOpenOption.APPEND)) {
            w.write(String.join(",", escape(cols))); w.newLine();
        }
    }
    private static List<String> escape(List<String> cols) {
        List<String> out = new ArrayList<>(cols.size());
        for (String s : cols) {
            if (s == null) s = "";
            boolean q = s.contains(",") || s.contains("\"") || s.contains("\n");
            out.add(q ? "\"" + s.replace("\"", "\"\"") + "\"" : s);
        }
        return out;
    }
    private static String val(Number n){ return n==null ? "" : n.toString(); }

    // ----- T1: Parse status -----
    public static void logParseStatus(Path runDir, String scenario, String model, String prismVersion, boolean ok, String errorMsg) {
        try {
            Path p = runDir.resolve("parse_status.csv");
            ensureHeader(p, "timestamp,scenario,model,prism_version,status,error");
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            appendRow(p, Arrays.asList(ts, scenario, model, prismVersion, ok ? "OK" : "FAIL", errorMsg == null ? "" : errorMsg));
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ----- T3: Scenario metrics -----
    public static void logScenarioMetrics(Path runDir, String scenario, String modelName, int numAgents, int numTraits, long states, long transitions,
                                          double genTimeSec, double verifyTimeSec, long memMB) {
        try {
            Path p = runDir.resolve("scenario_metrics.csv");
            ensureHeader(p, "scenario,model,agents,traits,states,transitions,gen_time_s,verify_time_s,mem_mb");
            appendRow(p, Arrays.asList(scenario, modelName,
                    String.valueOf(numAgents), String.valueOf(numTraits),
                    String.valueOf(states), String.valueOf(transitions),
                    val(genTimeSec), val(verifyTimeSec), String.valueOf(memMB)));
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ----- T4: Property outcomes -----
    public static void appendPropertyOutcome(Path runDir, String scenario, String family, String formula,
                                             String var, int idx, String boundT,
                                             Double result, Double timeSec, Integer iterations, Double initVal) {
        try {
            Path p = runDir.resolve("property_outcomes.csv");
            ensureHeader(p, "scenario,family,formula,var,idx,boundT,result,time_s,iterations,init_value");
            appendRow(p, Arrays.asList(
                    scenario, family, formula, var, String.valueOf(idx),
                    boundT == null ? "" : boundT, val(result), val(timeSec),
                    iterations==null?"":iterations.toString(), val(initVal)
            ));
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ----- R1: Environment -----
    public static void exportEnvironment(Path runDir, String prismVersion) {
        try {
            Path p = runDir.resolve("environment.csv");
            ensureHeader(p, "timestamp,prism_version,java_version,os,arch,cores,ram_mb,user");
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String jv = System.getProperty("java.version");
            String os = System.getProperty("os.name") + " " + System.getProperty("os.version");
            String arch = System.getProperty("os.arch");
            int cores = Runtime.getRuntime().availableProcessors();
            long ramMB = Runtime.getRuntime().maxMemory() / (1024*1024);
            String user = System.getProperty("user.name");
            appendRow(p, Arrays.asList(ts, prismVersion, jv, os, arch, String.valueOf(cores), String.valueOf(ramMB), user));
        } catch (IOException e) { e.printStackTrace(); }
    }
}