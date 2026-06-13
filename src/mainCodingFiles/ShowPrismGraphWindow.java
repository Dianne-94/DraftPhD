package mainCodingFiles;

import org.knowm.xchart.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShowPrismGraphWindow {
    private ShowPrismGraphWindow() {} // utility class
    static Matcher mp, mpUnb, mpt, ms, mi, mv, mr, mCheck, mb, mc, mdirect, mt, md, mtp, mns, tr;
    static Pattern propP, propPUnb, propPThresh, propS, iterPat, initPat, resPat, resBoolPat, directCompPat, timeCheckPat, typePat, statesPat, transPat, deadlockPat, constructTimePat;

    /** Parse + show dashboards along with verifyTimeSec */
    public static Parsed plotComparisonsFromPrismLog( String prismLog, Path runDir, double prismOnlyTimeSec, double pipelineTimeSec) 
    {
        Parsed parsed = parse(prismLog);
        parsed.verificationTimeSec = prismOnlyTimeSec;   // PRISM-only
        parsed.pipelineTimeSec = pipelineTimeSec;        // Total pipeline

        if (parsed.families.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No matching P=? or S=? properties.", "PRISM Charts", JOptionPane.WARNING_MESSAGE);
            return parsed;
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("PRISM Comparison");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(1280, 860);
            frame.setLocationRelativeTo(null);

            JTabbedPane tabs = new JTabbedPane();

            // order families: bounded -> unbounded -> threshold -> steady
            List<String> famOrder = new ArrayList<>(parsed.families);
            famOrder.sort((a, b) -> {
                boolean aB = a.startsWith("P=?  F<="), bB = b.startsWith("P=?  F<=");
                boolean aU = a.equals("P=?  F"),     bU = b.equals("P=?  F");
                boolean aT = a.startsWith("P>"),      bT = b.startsWith("P>");
                boolean aS = a.equals("S=?"),         bS = b.equals("S=?");
                int ga = aB?0: aU?1: aT?2: aS?3:4;
                int gb = bB?0: bU?1: bT?2: bS?3:4;
                if (ga!=gb) return Integer.compare(ga, gb);
                if (aB && bB) return Double.compare(extractT(a), extractT(b));
                return a.compareTo(b);
            });
            
         // ---------- Construction Info Tab ----------
            if (parsed.modelType != null || parsed.constructionTimeSec != null) {
                JPanel infoPanel = new JPanel(new GridBagLayout());
                infoPanel.setBackground(new Color(250, 250, 250));
                infoPanel.setBorder(new EmptyBorder(30, 50, 30, 50));

                Font labelFont = new Font("Segoe UI", Font.BOLD, 14);
                Font valueFont = new Font("Consolas", Font.PLAIN, 14);

                GridBagConstraints gbc = new GridBagConstraints();
                gbc.insets = new Insets(10, 10, 10, 10);
                gbc.anchor = GridBagConstraints.WEST;
                gbc.fill = GridBagConstraints.HORIZONTAL;

                // Helper to add rows
                int row = 0;
                String prismOnlyStr = parsed.verificationTimeSec != null ? String.format("%.3f", parsed.verificationTimeSec): "N/A";
                String pipelineStr = parsed.pipelineTimeSec != null ? String.format("%.3f", parsed.pipelineTimeSec): "N/A";
                row = addInfoRow(infoPanel, gbc, row, "Trait Type:", parsed.traitType, labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "Model Type:", parsed.modelType, labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "States:", parsed.states >= 0 ? String.valueOf(parsed.states) : "N/A", labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "No of Initial State:", parsed.states >= 0 ? String.valueOf(parsed.initialStates) : "N/A", labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "Transitions:", parsed.transitions >= 0 ? String.valueOf(parsed.transitions) : "N/A", labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "Deadlocks fixed:", parsed.deadlocks >= 0 ? String.valueOf(parsed.deadlocks) : "N/A", labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "Time for model construction (sec):", parsed.constructionTimeSec != null ? String.valueOf(parsed.constructionTimeSec) : "N/A", labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "Time for PRISM only model verification (sec):", prismOnlyStr, labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "Time for total pipeline verification (sec):", pipelineStr, labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "Rate Matrix Info:", parsed.rateMatrix, labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "Diagonals Vector Info:", parsed.diagVector, labelFont, valueFont);
                row = addInfoRow(infoPanel, gbc, row, "Embedded Markov Chain Info:", parsed.embeddedChain, labelFont, valueFont);

                JScrollPane scroll = new JScrollPane(infoPanel);
                scroll.getVerticalScrollBar().setUnitIncrement(16);
                scroll.setBorder(null);
                tabs.add("Construction Info", scroll);
            }

            for (String fam : famOrder) {
                FamilyData fd = parsed.byFamily.get(fam);
                if (fd == null || fd.byVar.isEmpty()) continue;

                String tabTitle = niceTabTitle(fam);

                XYChart chA = lineChart(niceResTitle(fam),
                        "State",
                        fam.startsWith("P>") ? "Satisfaction (Yes/No)" :
                        (fam.equals("S=?") ? "Steady-State Probability" : "Probability"),
                        0.0, 1.0);

                XYChart chB = lineChart("Verification Time by State", "State", "Time (seconds)", 0.0, null);
                XYChart chC = lineChart("Iterations by State", "State", "Solver Iterations", 0.0, null);
                XYChart chD;
	                if (fam.startsWith("P>")) {
	                    chD = lineChart("Threshold θ by State", "State", "θ", 0.0, 1.0);
	                } else if (fam.equals("S=?")
	                        || fam.startsWith("P=?  F<=")
	                        || fam.equals("P=?  F")) {
	                    chD = lineChart("Modules Reaching Target (p ≥ 0.9)", "State", "Number of modules", 0.0, null);
	                } else {
	                    chD = lineChart("Initial State Value (Vinit)", "State", "Vinit", 0.0, 1.0);
	                }


	             // First loop: per-module series
	                for (String var : sorted(fd.byVar.keySet())) {
                    VarSeries vs = fd.byVar.get(var);
                    List<Integer> x = new ArrayList<>(vs.idx);

                    if (fam.startsWith("P>")) {
                        // Threshold: satisfaction vs theta
                        if (hasNumbers(vs.satisfied))
                            chA.addSeries(var, x, new ArrayList<>(vs.satisfied));
                        if (hasNumbers(vs.thresholds))
                            chD.addSeries(var, x, new ArrayList<>(vs.thresholds));

                    } else if (fam.equals("S=?")) {
                        // Steady-state: show per-module probability only in A
                        if (hasNumbers(vs.initVals))
                            chA.addSeries(var, x, new ArrayList<>(vs.initVals));

                    } else if (fam.startsWith("P=?  F<=") || fam.equals("P=?  F")) {
                        // Reachability (bounded & unbounded): only show probability in A
                        if (hasNumbers(vs.results))
                            chA.addSeries(var, x, new ArrayList<>(vs.results));
                        // Do NOT plot Vinit here – D is reserved for aggregate view

                    } else {
                        // Fallback
                        if (hasNumbers(vs.results))
                            chA.addSeries(var, x, new ArrayList<>(vs.results));
                        if (hasNumbers(vs.initVals))
                            chD.addSeries(var, x, new ArrayList<>(vs.initVals));
                    }

                    // Time & Iterations (cleanup small redundancy)
                    if (hasNumbers(vs.timeSec)) chB.addSeries(var, x, new ArrayList<>(vs.timeSec));
                    if (hasNumbers(vs.iterations)) {
                        chC.addSeries(var, x, new ArrayList<>(vs.iterations));
                    } else {
                       // System.out.println("No iteration data available for " + var);
                    }
                }
                
	                if (fam.equals("S=?") || fam.startsWith("P=?  F<=") || fam.equals("P=?  F")) {
	                    double TARGET = 0.9; // later can be configurable

	                    // Map: state index -> count of modules reaching the target
	                    Map<Integer, Integer> countByIdx = new TreeMap<>();

	                    for (Map.Entry<String, VarSeries> e : fd.byVar.entrySet()) {
	                        VarSeries vs = e.getValue();
	                        for (int i = 0; i < vs.idx.size(); i++) {
	                            int idx = vs.idx.get(i);

	                            // For S=?, use steady-state values; for reachability, use probability results
	                            Double val = fam.equals("S=?")
	                                    ? get(vs.initVals, i)
	                                    : get(vs.results, i);

	                            if (val != null && !val.isNaN() && val >= TARGET) {
	                                countByIdx.merge(idx, 1, Integer::sum);
	                            }
	                        }
	                    }

	                    if (!countByIdx.isEmpty()) {
	                        List<Integer> xAgg = new ArrayList<>(countByIdx.keySet());
	                        List<Double> yAgg = new ArrayList<>();
	                        for (Integer idx : xAgg) {
	                            yAgg.add(countByIdx.get(idx).doubleValue());
	                        }
	                        chD.addSeries("Modules reaching target", xAgg, yAgg);
	                    }
                }

                
                tabs.add(tabTitle, fourUp(chA, chB, chC, chD));
            }
         
            frame.setContentPane(tabs);
            frame.setVisible(true);
        });
        return parsed;
    }

    // ---------- CSV export (writes to runDir/parsed_outcomes.csv) ----------
    public static void exportParsedToCSV(Parsed parsed, String scenarioId, Path runDir) {
        Objects.requireNonNull(runDir, "runDir must not be null");
        Path out = runDir.resolve("parsed_outcomes.csv");
        boolean writeHeader = !Files.exists(out);

        try {
            Files.createDirectories(runDir);
            try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

                if (writeHeader) {
                    bw.write(String.join(",",
                            "scenario_id",
                            "family",
                            "formula",
                            "var",
                            "state_index",
                            "T_bound",
                            "prob_result",
                            "time_sec",
                            "iterations",
                            "init_value",
                            "theta",
                            "satisfaction01"));
                    bw.newLine();
                }

                for (String fam : parsed.families) {
                    FamilyData fd = parsed.byFamily.get(fam);
                    if (fd == null) continue;
                    String boundT = fam.startsWith("P=?  F<=") ? fam.replaceAll(".*<=\\s*", "").trim() : "";

                    for (Map.Entry<String, VarSeries> e : fd.byVar.entrySet()) {
                        String var = e.getKey();
                        VarSeries vs = e.getValue();

                        for (int i = 0; i < vs.idx.size(); i++) {
                            int idx = vs.idx.get(i);
                            Double res   = get(vs.results, i);
                            Double time  = get(vs.timeSec, i);
                            Integer iter = getI(vs.iterations, i);
                            Double init  = get(vs.initVals, i);
                            Double theta = get(vs.thresholds, i);
                            Double sat01 = get(vs.satisfied, i);

                            String formula;
                            if (fam.startsWith("P=?  F<=")) {
                                formula = "P=? [ F<=" + (isBlank(boundT)?"?":boundT) + " (" + var + "=" + idx + ") ]";
                            } else if (fam.equals("P=?  F")) {
                                formula = "P=? [ F (" + var + "=" + idx + ") ]";
                            } else if (fam.startsWith("P>") || fam.startsWith("P<") || fam.startsWith("P=")) {
                                String thetaStr = (theta==null || theta.isNaN()) ? "?" : trimDouble(theta);
                                formula = "P>=" + thetaStr + " [ F (" + var + "=" + idx + ") ]";
                            } else if (fam.equals("S=?")) {
                                formula = "S=? [ " + var + "=" + idx + " ]";
                            } else {
                                formula = fam + " (" + var + "=" + idx + ")";
                            }

                            bw.write(csv(scenarioId)); bw.write(",");
                            bw.write(csv(fam)); bw.write(",");
                            bw.write(csv(formula)); bw.write(",");
                            bw.write(csv(var)); bw.write(",");
                            bw.write(Integer.toString(idx)); bw.write(",");
                            bw.write(csv(boundT)); bw.write(",");
                            bw.write(csv(trimDouble(res))); bw.write(",");
                            bw.write(csv(trimDouble(time))); bw.write(",");
                            bw.write(csv(iter==null? "": Integer.toString(iter))); bw.write(",");
                            bw.write(csv(trimDouble(init))); bw.write(",");
                            bw.write(csv(trimDouble(theta))); bw.write(",");
                            bw.write(csv(trimDouble(sat01)));
                            bw.newLine();
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to write CSV to " + out + ": " + ex.getMessage(), ex);
        }
    }

    // ================== parsing ==================
    static Parsed parse(String text) {
        Parsed P = new Parsed();

        // Support long variable names like fireTruck_state
        propP = Pattern.compile("(?i)Model\\s*checking\\s*:\\s*P=\\?\\s*\\[\\s*F<=\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\(\\s*([a-zA-Z0-9_]+)\\s*=\\s*(\\d+)\\s*\\)\\s*\\]");
        propPUnb = Pattern.compile("(?i)Model\\s*checking\\s*:\\s*P=\\?\\s*\\[\\s*F\\s*\\(\\s*([a-zA-Z0-9_]+)\\s*=\\s*(\\d+)\\s*\\)\\s*\\]");
        propPThresh = Pattern.compile("(?i)Model\\s*checking\\s*:\\s*P\\s*([<>]=?)\\s*([0-9]*\\.?[0-9]+)\\s*\\[\\s*F\\s*\\(\\s*([a-zA-Z0-9_]+)\\s*=\\s*(\\d+)\\s*\\)\\s*\\]");
        propS = Pattern.compile("(?i)Model\\s*checking\\s*:\\s*S=\\?\\s*\\[\\s*([a-zA-Z0-9_]+)\\s*=\\s*(\\d+)\\s*\\]");

        // Other metrics
        iterPat = Pattern.compile("(?i)Iterative\\s+method\\s*:\\s*(\\d+)\\s*iterations");
        initPat = Pattern.compile("(?i)Value\\s+in\\s+the\\s+initial\\s+state\\s*:\\s*([+\\-]?\\d*\\.?\\d+(?:[eE][+\\-]?\\d+)?)");
        resPat  = Pattern.compile("(?i)Result\\s*:\\s*([+\\-]?\\d*\\.?\\d+(?:[eE][+\\-]?\\d+)?)");
        resBoolPat = Pattern.compile("(?i)Result\\s*:\\s*(true|false)");
        directCompPat = Pattern.compile("(?i)(computing steady-state probabilities|computing probabilities directly)");
        timeCheckPat = Pattern.compile("(?i)Time\\s+for\\s+model\\s+checking\\s*:\\s*([0-9]*\\.?\\d+)\\s*seconds");
        typePat = Pattern.compile("(?i)^Type\\s*:\\s*(\\S+)");
        statesPat = Pattern.compile("(?i)^States\\s*:\\s*(\\d+)(?:\\s*\\((\\d+)\\s*initial\\))?");
        transPat = Pattern.compile("(?i)^Transitions\\s*:\\s*(\\d+)");
        deadlockPat = Pattern.compile("(?i)^Warning:\\s*Deadlocks\\s*detected\\s*and\\s*fixed\\s*in\\s*(\\d+)\\s*states?");
        constructTimePat = Pattern.compile("(?i)^Time\\s*for\\s*model\\s*construction\\s*:\\s*([0-9]*\\.?[0-9]+)\\s*seconds?\\.?$");

        String[] lines = text.split("\\R");
        String currentFamily = null, currentVar = null;
        Integer currentIdx = null;
        BlockMetrics m = new BlockMetrics();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            mp = propP.matcher(line);
            if (mp.find()) {
                commit(P, currentFamily, currentVar, currentIdx, m);
                currentFamily = "P=?  F<=" + mp.group(1);
                currentVar = mp.group(2);
                currentIdx = Integer.parseInt(mp.group(3));
                m.reset(); continue;
            }

            mpUnb = propPUnb.matcher(line);
            if (mpUnb.find()) {
                commit(P, currentFamily, currentVar, currentIdx, m);
                currentFamily = "P=?  F";
                currentVar = mpUnb.group(1);
                currentIdx = Integer.parseInt(mpUnb.group(2));
                m.reset(); continue;
            }

            mpt = propPThresh.matcher(line);
            if (mpt.find()) {
                commit(P, currentFamily, currentVar, currentIdx, m);
                currentFamily = "P" + mpt.group(1) + " " + mpt.group(2) + "  F";
                currentVar = mpt.group(3);
                currentIdx = Integer.parseInt(mpt.group(4));
                try { m.threshold = Double.parseDouble(mpt.group(2)); } catch(Exception ignored){}
                m.reset(); continue;
            }

            ms = propS.matcher(line);
            if (ms.find()) {
                commit(P, currentFamily, currentVar, currentIdx, m);
                currentFamily = "S=?";
                currentVar = ms.group(1);
                currentIdx = Integer.parseInt(ms.group(2));
                m.reset(); continue;
            }

            mi = iterPat.matcher(line); 
            if (mi.find()) { 
            	try { m.iterations=Integer.parseInt(mi.group(1)); } 
            	catch (Exception ignored) {} continue; 
            	}
            
            mv = initPat.matcher(line); 
            if (mv.find()) { 
            	try { m.initVal=Double.parseDouble(mv.group(1)); } 
            	catch (Exception ignored) {} continue; 
            	}
            
            mr = resPat.matcher(line);  
            if (mr.find()) { 
            	try { m.result=Double.parseDouble(mr.group(1)); } 
            	catch (Exception ignored) {} continue; }
            
            mCheck = timeCheckPat.matcher(line);
            if (mCheck.find()) {
                try {
                    double t = Double.parseDouble(mCheck.group(1));
                    if (m != null) m.verificationTimeSec = t;  // ✅ specific to property
                } catch (Exception ignored) {}
                continue;
            }
            
            mb = resBoolPat.matcher(line); 
            if (mb.find()) {m.satisfied = "true".equalsIgnoreCase(mb.group(1)); continue;}
            
            mc = constructTimePat.matcher(line);
            if (mc.find()) {
                try {
                    double t = Double.parseDouble(mc.group(1));
                    P.constructionTimeSec = t;   // ✅ global record
                    if (m != null) m.constructTimeSec = t; // optional
                } catch (Exception ignored) {}
                continue;
            }
            
            mdirect = directCompPat.matcher(line);
            if (mdirect.find()) {
                // PRISM sometimes skips "Iterative method" and computes directly
                if (m.iterations == null) m.iterations = 1; // assume single computation step
                continue;
            }

            mt = typePat.matcher(line);
            if (mt.find()) {P.modelType = mt.group(1); continue; }

            md = deadlockPat.matcher(line); 
            if (md.find()) { P.deadlocks = Integer.parseInt(md.group(1)); continue; }
            
            mtp = typePat.matcher(line); 
            if (mtp.find()) {P.modelType = mtp.group(1).trim(); continue;}
            
            mns = statesPat.matcher(line);
            if (mns.find()) {
                P.states = Integer.parseInt(mns.group(1));
                if (mns.group(2) != null) {
                    P.initialStates = Integer.parseInt(mns.group(2));
                }
                continue;
            }
            
            tr = transPat.matcher(line);
            if (tr.find()) {P.transitions = Integer.parseInt(tr.group(1)); continue; }
            
            if (line.startsWith("Rate matrix:"))
                P.rateMatrix = line.replaceFirst("(?i)^Rate\\s*matrix:\\s*", "").trim();
            if (line.startsWith("Diagonals vector:"))
                P.diagVector = line.replaceFirst("(?i)^Diagonals\\s*vector:\\s*", "").trim();
            if (line.startsWith("Embedded Markov chain:"))
                P.embeddedChain = line.replaceFirst("(?i)^Embedded\\s*Markov\\s*chain:\\s*", "").trim();
        }
        commit(P, currentFamily, currentVar, currentIdx, m);
        return P;
    }

    private static void commit(Parsed P, String fam, String var, Integer idx, BlockMetrics m) {
        if (fam != null && var != null && idx != null) {
            FamilyData fd = P.byFamily.computeIfAbsent(fam, k -> new FamilyData());
            P.families.add(fam);
            VarSeries vs = fd.byVar.computeIfAbsent(var, k -> new VarSeries());
            vs.idx.add(idx);
            vs.results.add(nz(m.result));
            vs.timeSec.add(nz(m.verificationTimeSec)); //model-checking time
            vs.iterations.add(nz(m.iterations == null ? null : m.iterations.doubleValue()));
            vs.initVals.add(nz(m.initVal));
            vs.thresholds.add(nz(m.threshold));
            vs.satisfied.add(m.satisfied == null ? Double.NaN : (m.satisfied ? 1.0 : 0.0));
            vs.constructTimeSec.add(nz(m.constructTimeSec)); //optional for correlation later
            
            //System.out.println("Detected threshold = " + m.threshold + " for family " + fam);
        }
        m.reset();
    }

    // ================== containers ==================
    public static final class Parsed {
        final Map<String, FamilyData> byFamily = new LinkedHashMap<>();
        final Set<String> families = new LinkedHashSet<>();
        Double constructionTimeSec, verificationTimeSec, pipelineTimeSec;
        String modelType, rateMatrix, diagVector, embeddedChain, traitType;
        int states = -1, transitions = -1, deadlocks = -1, initialStates = -1;
    }
    
    public static final class FamilyData {
        final Map<String, VarSeries> byVar = new LinkedHashMap<>();
    }
    
    public static final class VarSeries {
        final List<Integer> idx = new ArrayList<>();
        final List<Double> results = new ArrayList<>();
        final List<Double> timeSec = new ArrayList<>();
        final List<Double> iterations = new ArrayList<>();
        final List<Double> initVals = new ArrayList<>();
        final List<Double> thresholds = new ArrayList<>();
        final List<Double> satisfied = new ArrayList<>();
        final List<Double> constructTimeSec = new ArrayList<>();
    }
    
    private static final class BlockMetrics {
        Double result, verificationTimeSec, constructTimeSec, initVal, threshold;
        Integer iterations;
        Boolean satisfied;
        void reset(){
            result = verificationTimeSec = constructTimeSec = initVal = threshold = null;
            iterations = null;
            satisfied = null;
        }
    }

    // ================== UI helpers ==================
    private static XYChart lineChart(String title, String x, String y, Double ymin, Double ymax) {
        XYChart c = new XYChartBuilder().width(980).height(620).title(title).xAxisTitle(x).yAxisTitle(y).build();
        c.getStyler().setLegendVisible(true);
        c.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        c.getStyler().setMarkerSize(5);
        c.getStyler().setToolTipsEnabled(true);
        c.getStyler().setChartTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        if (ymin!=null) c.getStyler().setYAxisMin(ymin);
        if (ymax!=null) c.getStyler().setYAxisMax(ymax);
        return c;
    }
    
    private static JPanel fourUp(XYChart a, XYChart b, XYChart c, XYChart d) {
        JPanel grid = new JPanel(new GridLayout(2,2,10,10));
        grid.setBorder(new EmptyBorder(10,10,10,10));
        grid.add(new XChartPanel<>(a)); grid.add(new XChartPanel<>(b));
        grid.add(new XChartPanel<>(c)); grid.add(new XChartPanel<>(d));
        return grid;
    }
    
    private static JLabel label(String text, Font f) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        return l;
    }

    private static JTextField field(String val, Font f) {
        JTextField t = new JTextField(val != null ? val : "N/A");
        t.setEditable(false);
        t.setFont(f);
        return t;
    }
    
    private static int addInfoRow(JPanel panel, GridBagConstraints gbc, int row,
            String labelText, String valueText, Font labelFont, Font valueFont) {
				gbc.gridx = 0;
				gbc.gridy = row;
				gbc.weightx = 0.3;
				JLabel lbl = new JLabel(labelText);
				lbl.setFont(labelFont);
				panel.add(lbl, gbc);
				
				gbc.gridx = 1;
				gbc.weightx = 0.7;
				JLabel val = new JLabel(valueText != null && !valueText.isEmpty() ? valueText : "N/A");
				val.setFont(valueFont);
				val.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(220, 220, 220)),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)
				));
				val.setOpaque(true);
				val.setBackground(Color.WHITE);
				panel.add(val, gbc);
				
				return row + 1;
				}

    // ================== small utils ==================
    private static List<String> sorted(Set<String> s){ List<String> l=new ArrayList<>(s); l.sort(Comparator.naturalOrder()); return l; }
    private static boolean hasNumbers(List<Double> vals){ return vals!=null && vals.stream().anyMatch(d -> d!=null && !d.isNaN()); }
    private static Double nz(Double d){ return d==null? Double.NaN : d; }
    private static Double get(List<Double> l,int i){ return (l==null||i>=l.size())? null : l.get(i); }
    private static Integer getI(List<Double> l,int i){ if(l==null||i>=l.size()) return null; Double v=l.get(i); return (v==null||v.isNaN())? null : v.intValue(); }
    private static boolean isBlank(String s){ return s==null || s.trim().isEmpty(); }
    private static String trimDouble(Double d){
        if (d==null) return "";
        if (d.isNaN()) return "";
        String s = String.format(Locale.ROOT, "%.12f", d);
        // strip trailing zeros and dot
        s = s.replaceFirst("\\.?0+$", "");
        return s;
        }
    private static String csv(String s){
        if (s==null) return "";
        String q = s.replace("\"","\"\"");
        if (q.indexOf(',')>=0 || q.indexOf('"')>=0 || q.indexOf('\n')>=0) return "\""+q+"\"";
        return q;
    }

    // naming helpers
    private static String niceTabTitle(String fam) {
        if (fam.equals("S=?")) return "Steady-State";
        if (fam.startsWith("P=?  F<=")) return "Reachability ≤T";
        if (fam.equals("P=?  F")) return "Reachability (Unbounded)";
        if (fam.startsWith("P>") || fam.startsWith("P<") || fam.startsWith("P=")) return "Threshold Check";
        return fam;
    }
    private static String niceResTitle(String fam) {
        if (fam.startsWith("P=?  F<=")) return "Probability (P=? [F<=T]) by State";
        if (fam.equals("P=?  F"))       return "Probability (P=? [F]) by State";
        if (fam.startsWith("P>") || fam.startsWith("P<") || fam.startsWith("P=")) return "Satisfaction (Yes/No) of P>θ by State";
        if (fam.equals("S=?"))          return "Steady-State Probability (S=?) by State";
        return "Probability by State";
    }
    private static double extractT(String fam){
        try{
            int i = fam.indexOf("F<="); 
            if (i<0) return Double.NaN;
            String s = fam.substring(i+3).trim();
            return Double.parseDouble(s);
        }catch(Exception e){ return Double.NaN; }
    }
}
