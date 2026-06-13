package mainCodingFiles;

import java.io.StringReader;
import java.util.*;
import java.util.regex.*;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

public class PrismGenerator {

    protected long startConvert, memStartConvert, endConvert, memEndConvert, elaspedConvert, memoryConvert,
            startXtractNgenerate, memXtractNgenerate, endXtractNgenerate, elapsedXtractNgenerate, memoryXtractNgenerate,
            memStartXtractNgenerate, memEndXtractNgenerate, startGeneratePrism, memStartGeneratePrism, endGeneratePrism,
            memEndGeneratePrism;
    protected long startVerifyPrism, memStartVerifyPrism, endVerifyPrism, memEndVerifyPrism, startConvertPrismModel,
            memConvertStartPrismModel, endConvertPrismModel, memConvertEndPrismModel, endConvertProps,
            memEndConvertProps, memStartConvertProps, startConvertProps;

    public void generatePrism(String xmiContent, JTextArea outputTextArea, JTextArea outTextSM, JTextArea outTextProp) {
        try {
            if ((xmiContent == null || xmiContent.isBlank()) && outputTextArea != null) {
                xmiContent = outputTextArea.getText().trim();
            }
            if (xmiContent == null || xmiContent.isBlank()) throw new Exception("XMI content is empty.");

            startConvertPrismModel = System.currentTimeMillis();
            memConvertStartPrismModel = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            String cleanedContent = xmiContent.replaceFirst("^([\\W]+)<", "<");
            Document document = builder.parse(new InputSource(new StringReader(cleanedContent)));

            NodeList ctmcNodes = document.getElementsByTagName("ctmc:CTMC");
            NodeList gridNodes = document.getElementsByTagName("ctmc:Grid");
            if (ctmcNodes.getLength() == 0) throw new Exception("No ctmc:CTMC nodes found.");

            // ---------- guardExpression → consts + normalized formula using declared names ----------
            String guardExprRaw = ((Element) ctmcNodes.item(0)).getAttribute("guardExpression");
            Map<String, String> consts = new LinkedHashMap<>();
            String condAllFormula = null;

            // Optional: store probability thresholds if you want later for .props
            Double probReachThresh = null;
            Double probSolveThresh = null;

            if (guardExprRaw != null && !guardExprRaw.trim().isEmpty()) {
                String expr = guardExprRaw.replaceAll("\\s+", " ").trim();
                Matcher m = Pattern.compile("\\(([^()=]+)=([^()]+)\\)").matcher(expr);

                List<String> condClauses = new ArrayList<>();

                while (m.find()) {
                    String name = m.group(1).trim(); // e.g., FIRE_INTENSITY
                    String val  = m.group(2).trim(); // e.g., 3, full, >0.95, true
                    String constName = name.replaceAll("\\s+", "_").toUpperCase();

                 // --- 1) Handle PROB_* thresholds: parse only numeric part, skip from COND_ALL ---
                    if (constName.startsWith("PROB_")) {
                        // Extract the first number that looks like 0.95, 1, 0.9, etc.
                        Matcher pm = Pattern.compile("([0-9]*\\.?[0-9]+)").matcher(val);
                        if (pm.find()) {
                            String numStr = pm.group(1);
                            if (numStr != null && !numStr.isBlank()) {
                                try {
                                    double thr = Double.parseDouble(numStr);
                                    if (constName.equals("PROB_REACH_OBJECTIVE")) probReachThresh = thr;
                                    if (constName.equals("PROB_SOLVE_OBJECTIVE")) probSolveThresh = thr;
                                } catch (NumberFormatException ignore) {
                                    // if parsing fails, just skip the threshold
                                }
                            }
                        }
                        // Do NOT include PROB_* in consts or COND_ALL
                        continue;
                    }

                    // --- 2) Normal boolean or numeric → declare const + include in COND_ALL ---
                    if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")) {
                        consts.put(constName, val.toLowerCase());
                        condClauses.add("(" + constName + "=" + val.toLowerCase() + ")");
                    } else if (val.matches("[-+]?[0-9]+") || val.matches("[-+]?[0-9]*\\.[0-9]+")) {
                        consts.put(constName, val);
                        condClauses.add("(" + constName + "=" + val + ")");
                    } else {
                        // --- 3) String-like values (full, near, etc.) → IGNORE for now in COND_ALL ---
                    	 if (constName.endsWith("_LEVEL")) {
                    	        int encoded;
                    	        if (val.equalsIgnoreCase("full"))        encoded = 3;
                    	        else if (val.equalsIgnoreCase("medium")) encoded = 2;
                    	        else if (val.equalsIgnoreCase("low"))    encoded = 1;
                    	        else                                     encoded = 0; // unknown

                    	        consts.put(constName, String.valueOf(encoded));
                    	        condClauses.add("(" + constName + "=" + encoded + ")");
                    	        continue;
                    	    }

                    	    // 3b) AGENT_DISTANCE textual values (near/medium/far) → encode to numeric
                    	    if (constName.equals("AGENT_DISTANCE")) {
                    	        int encoded;
                    	        if (val.equalsIgnoreCase("near"))        encoded = 1;
                    	        else if (val.equalsIgnoreCase("medium")) encoded = 2;
                    	        else if (val.equalsIgnoreCase("far"))    encoded = 3;
                    	        else                                     encoded = 0; // unknown

                    	        consts.put(constName, String.valueOf(encoded));
                    	        condClauses.add("(" + constName + "=" + encoded + ")");
                    	        continue;
                    	    }

                    	    // Other string-valued params (if any) are still ignored for now
                    	    continue;
                    	}
                }

                if (!condClauses.isEmpty()) {
                    condAllFormula = String.join(" & ", condClauses);
                }
            }

            StringBuilder sm = new StringBuilder();
            sm.append("ctmc\n\n");

            // ---------- Grid constants ----------
            int xMax = -1, yMax = -1;
            if (gridNodes.getLength() > 0) {
                Element grid = (Element) gridNodes.item(0);
                try { xMax = Integer.parseInt(grid.getAttribute("xMax")); } catch (Exception ignored) {}
                try { yMax = Integer.parseInt(grid.getAttribute("yMax")); } catch (Exception ignored) {}
            }
            if (xMax > 0 && yMax > 0) {
                sm.append("// Grid from XMI\n");
                sm.append("const int X_MAX = ").append(xMax).append(";\n");
                sm.append("const int Y_MAX = ").append(yMax).append(";\n\n");
            }

            // ---------- Declare constants (NOT variables) + formula ----------
            if (!consts.isEmpty()) {
                sm.append("// Constants extracted from guardExpression\n");
                for (Map.Entry<String,String> e : consts.entrySet()) {
                    String name = e.getKey(), val = e.getValue();
                    if (val.equals("true") || val.equals("false")) {
                        sm.append("const bool ").append(name).append(" = ").append(val).append(";\n");
                    } else {
                        if (val.contains(".")) sm.append("const double ");
                        else sm.append("const int ");
                        sm.append(name).append(" = ").append(val).append(";\n");
                    }
                }
                sm.append("\n");
            }
            if (condAllFormula != null && !condAllFormula.isEmpty()) {
                sm.append("// Formula reconstructed from guardExpression (uses constants declared above)\n");
                sm.append("formula COND_ALL = ").append(condAllFormula).append(";\n\n");
            }

            // ---------- Auto dynamic level variables from *_LEVEL constants + labels ----------
            List<String> levelGlobals = new ArrayList<>();
            for (String cName : consts.keySet()) {

                // Case 1: *_LEVEL → e.g., WATER_LEVEL = 3 → water_level : [0..WATER_LEVEL]
                if (cName.endsWith("_LEVEL")) {
                    String base = cName.substring(0, cName.length() - "_LEVEL".length()); // WATER
                    String var  = base.toLowerCase() + "_level";                           // water_level
                    levelGlobals.add(var);
                    sm.append("global ").append(var)
                      .append(" : [0..").append(cName).append("] init 0;\n");
                }

                // Case 2: AGENT_DISTANCE → create distance_level : [0..AGENT_DISTANCE]
                if (cName.equals("AGENT_DISTANCE")) {
                    String var = "distance_level";
                    levelGlobals.add(var);
                    sm.append("global ").append(var)
                      .append(" : [0..").append(cName).append("] init ").append(cName).append(";\n");
                }
            }

            if (!levelGlobals.isEmpty()) {
                sm.append("\n");
                for (String g : levelGlobals) {
                    sm.append("label \"").append(g).append("_empty\" = (").append(g).append("=0);\n");
                    sm.append("label \"").append(g).append("_low\"   = (").append(g).append(" <= 1);\n");
                }
                sm.append("\n");
            }

            // ---------- Identifier sanitizers ----------
            java.text.Normalizer.Form NFD = java.text.Normalizer.Form.NFD;
            java.util.function.Function<String,String> toAscii = (s) -> {
                if (s == null) return "";
                String n = java.text.Normalizer.normalize(s, NFD);
                return n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            };
            java.util.Set<String> usedIdents = new java.util.HashSet<>();
            java.util.Map<String,String> identMap = new java.util.HashMap<>();
            java.util.function.Supplier<String> alphaGen = new java.util.function.Supplier<>() {
                int counter = 0; @Override public String get() {
                    int idx = counter % 26; int k = counter / 26; counter++;
                    char base = (char) ('a' + idx);
                    return (k == 0) ? String.valueOf(base) : (base + String.valueOf(k));
                }
            };
            java.util.function.Function<String,String> getSafeIdent = (raw) -> identMap.computeIfAbsent(
                raw == null ? "" : raw,
                r -> {
                    String s = toAscii.apply(r).replaceAll("[^A-Za-z0-9_]", "_");
                    if (s.isEmpty()) s = alphaGen.get();
                    if (!Character.isLetter(s.charAt(0))) s = "v" + s;
                    String base2 = s; int i = 1;
                    while (usedIdents.contains(s)) s = base2 + i++;
                    usedIdents.add(s);
                    return s;
                }
            );
            java.util.function.Function<String,String> safeAction = getSafeIdent;

         // ---------- Build modules ----------
            class ModuleInfo {
                String safeModule;
                String safeVar;
                List<String> states = new ArrayList<>();

                // NEW: spatial + availability info
                int posX = -1;
                int posY = -1;
                boolean hasPosition = false;

                // Behavioural availability (for allAvailableModules_reachX)
                boolean isBehaviourallyAvailable = true;

                // Spatial availability (inside grid AND has position)
                boolean isSpatiallyAvailable = false;
            }

            List<ModuleInfo> modules = new ArrayList<>();

            // NEW: collect spatial warnings for user
            StringBuilder spatialWarnings = new StringBuilder();

            for (int i = 0; i < ctmcNodes.getLength(); i++) {
                Element ctmcElement = (Element) ctmcNodes.item(i);
                String moduleName = ctmcElement.getAttribute("name");
                if (moduleName == null || moduleName.trim().isEmpty()) moduleName = "Module" + (i+1);
                
                // NEW: read optional position attributes x,y from CTMC node
                int posX = -1, posY = -1;
                boolean hasPos = false;
                try {
                    String xAttr = ctmcElement.getAttribute("x");
                    String yAttr = ctmcElement.getAttribute("y");
                    if (xAttr != null && !xAttr.isBlank() && yAttr != null && !yAttr.isBlank()) {
                        posX = Integer.parseInt(xAttr.trim());
                        posY = Integer.parseInt(yAttr.trim());
                        hasPos = true;
                    }
                } catch (Exception ignore) {
                    // position is optional, so ignore parsing errors
                }

                // Collect states
                NodeList stateNodes = ctmcElement.getElementsByTagName("states");
                LinkedHashSet<String> stateSet = new LinkedHashSet<>();
                for (int s = 0; s < stateNodes.getLength(); s++) {
                    Element st = (Element) stateNodes.item(s);
                    String sName = st.getAttribute("name");
                    if (sName == null || sName.isEmpty()) sName = st.getAttribute("id");
                    if (sName == null || sName.isEmpty()) sName = "q" + s;
                    stateSet.add(sName);
                }

                // Collect transitions
                NodeList transNodes = ctmcElement.getElementsByTagName("transition");
                List<String[]> transitions = new ArrayList<>();
                boolean needsFail = false;

                for (int t = 0; t < transNodes.getLength(); t++) {
                    Element tr = (Element) transNodes.item(t);
                 // --- robust extraction for <transition> ---
                    String rate = tr.getAttribute("rate") != null ? tr.getAttribute("rate").trim() : "";
                    if (rate.isEmpty()) {
                        // Try to find rate from child text or attribute
                        if (tr.hasChildNodes()) {
                            NodeList rateNodes = tr.getElementsByTagName("rate");
                            if (rateNodes.getLength() > 0)
                                rate = rateNodes.item(0).getTextContent().trim();
                        }
                    }
                    if (rate.isEmpty()) rate = "1.0";

                    // Extract initial and target from either attributes or nested tags
                    String initial = tr.getAttribute("initial") != null ? tr.getAttribute("initial").trim() : "";
                    String target  = tr.getAttribute("target") != null ? tr.getAttribute("target").trim() : "";

                    if (initial.isEmpty()) {
                        NodeList iniNodes = tr.getElementsByTagName("initial");
                        if (iniNodes.getLength() > 0) {
                            Node ini = iniNodes.item(0);
                            if (ini instanceof Element) {
                                Element e = (Element) ini;
                                initial = e.hasAttribute("name") ? e.getAttribute("name").trim() : e.getTextContent().trim();
                            }
                        }
                    }
                    if (target.isEmpty()) {
                        NodeList tgtNodes = tr.getElementsByTagName("target");
                        if (tgtNodes.getLength() > 0) {
                            Node tgt = tgtNodes.item(0);
                            if (tgt instanceof Element) {
                                Element e = (Element) tgt;
                                target = e.hasAttribute("name") ? e.getAttribute("name").trim() : e.getTextContent().trim();
                            }
                        }
                    }

                    if (!initial.isEmpty() && !target.isEmpty()) {
                        stateSet.add(initial);
                        stateSet.add(target);
                        transitions.add(new String[]{initial, target, rate});
                        // check if this transition needs a fail branch
                        String rateStr = rate.isEmpty() ? "1.0" : rate;
                        double r = Double.parseDouble(rateStr);
                        if (r > 0.0 && r < 1.0) needsFail = true;
                    }
                }

                if (stateSet.isEmpty() && transitions.isEmpty()) continue;

                // ---------- FIX: Normalize fail-state case-insensitively ----------

		        // Step 1: Canonical map (lowercase → original)
		        LinkedHashMap<String, String> canonical = new LinkedHashMap<>();
		
		        for (String s : stateSet) {
		            String key = s.trim().toLowerCase(); // canonical key
		            canonical.putIfAbsent(key, s);       // keep first version we see
		        }
		
		        // Step 2: Add fail state only if no version exists
		        if (needsFail && !canonical.containsKey("fail")) {
		            canonical.put("fail", "fail");  // or "Fail"
		        }
		
		        // Step 3: Final state list
		        List<String> stateList = new ArrayList<>(canonical.values());

                Map<String,Integer> index = new HashMap<>();
                for (int s = 0; s < stateList.size(); s++) index.put(stateList.get(s), s);
             // ---- FIX: Case-insensitive lookup for fail state ----
                java.util.function.Supplier<Integer> getFailIndex = () -> {
                    for (Map.Entry<String,Integer> e : index.entrySet()) {
                        if (e.getKey().equalsIgnoreCase("fail")) {
                            return e.getValue();
                        }
                    }
                    return null;
                };
                String safeModule = getSafeIdent.apply(moduleName);
                String safeVar    = getSafeIdent.apply(moduleName + "_state");

                ModuleInfo mi = new ModuleInfo();
                mi.safeModule = safeModule;
                mi.safeVar = safeVar;
                mi.states.addAll(stateList);

                // NEW: attach spatial info & compute availability
                mi.hasPosition = hasPos;
                mi.posX = posX;
                mi.posY = posY;

                // default: behaviourally available
                mi.isBehaviourallyAvailable = true;
                mi.isSpatiallyAvailable = false;

                // Only if we know the grid and position
                if (hasPos && xMax > 0 && yMax > 0) {
                    if (posX >= 0 && posX < xMax && posY >= 0 && posY < yMax) {
                        mi.isSpatiallyAvailable = true;  // inside grid
                    } else {
                        // outside grid → mark as behaviourally unavailable
                        mi.isBehaviourallyAvailable = false;
                        spatialWarnings.append(" - ")
                                       .append(mi.safeModule)
                                       .append(" at (").append(posX).append(",").append(posY).append(")")
                                       .append(" is OUTSIDE grid [0..").append(xMax - 1)
                                       .append("] x [0..").append(yMax - 1).append("] -> marked UNAVAILABLE\n");
                    }
                } else if (!hasPos) {
                    // no position: still behaviourally available, just no spatial info
                    mi.isBehaviourallyAvailable = true;
                    mi.isSpatiallyAvailable = false;
                }

                modules.add(mi);

                sm.append("module ").append(safeModule).append("\n");
                sm.append("    // States: ");
                for (int s = 0; s < stateList.size(); s++) {
                    sm.append(s).append("=").append(stateList.get(s));
                    if (s < stateList.size()-1) sm.append(", ");
                }
                sm.append("\n");

                int upper = stateList.size() - 1; // 0..N-1
                sm.append("    ").append(safeVar).append(" : [0..").append(upper).append("] init 0;\n\n");

             // --- Transition and default rate handling ---
                if (!transitions.isEmpty()) {
                    sm.append("    // Transitions parsed from XMI\n");
                    for (String[] tr : transitions) {
                        String from = tr[0], to = tr[1];
                        String rateStr = tr[2].isEmpty() ? "1.0" : tr[2];
                        double rate = Double.parseDouble(rateStr);
                        int fromIdx = index.get(from), toIdx = index.get(to);

                        String actionLabel = safeAction.apply(safeModule + "_" + from + "_to_" + to);

                        sm.append("    [").append(actionLabel).append("] ")
                          .append(mi.safeVar).append("=").append(fromIdx);

                        if (condAllFormula != null && !condAllFormula.isEmpty()) {
                            sm.append(" & COND_ALL");
                        }

                        String pOk  = String.format("%.2f", rate);
                        String pRem = String.format("%.2f", (1.0 - rate));

                        if (rate >= 0.999999) {
                            sm.append(" -> ").append(pOk)
                              .append(" : (").append(mi.safeVar).append("'=").append(toIdx).append(");\n");
                        } else if (rate <= 0.000001) {
                            // zero probability: fail or self-loop
                            if (needsFail) {
                            	Integer failIdx = getFailIndex.get();
                            	if (failIdx == null) {
                            	    throw new RuntimeException("Fail state expected but not found in index map!");
                            	}
                                sm.append(" -> 1.00 : (").append(mi.safeVar).append("'=").append(failIdx).append(");\n");
                            } else {
                                sm.append(" -> 1.00 : (").append(mi.safeVar).append("'=").append(fromIdx).append(");\n");
                            }
                        } else {
                            // split into success and fail
                        	Integer failIdx = getFailIndex.get();
                        	if (failIdx == null) {
                        	    throw new RuntimeException("Fail state expected but not found in index map!");
                        	}
                            sm.append(" -> ").append(pOk)
                              .append(" : (").append(mi.safeVar).append("'=").append(toIdx).append(")")
                              .append(" + ").append(pRem)
                              .append(" : (").append(mi.safeVar).append("'=").append(failIdx).append(");\n");
                        }
                    }
                } else {
                    // --- NEW: auto transitions when states exist but no transitions found ---
                    double defaultRate = 1.0; // you can later make this configurable
                    if (stateList.size() > 1) {
                        sm.append("    // Auto-generated default transitions (no transitions found in XMI)\n");
                        for (int s = 0; s < stateList.size(); s++) {
                            int fromIdx = s;
                            int toIdx = (s + 1 < stateList.size()) ? s + 1 : s; // last state self-loop
                            String from = stateList.get(fromIdx);
                            String to = stateList.get(toIdx);
                            String actionLabel = safeAction.apply(safeModule + "_auto_" + from + "_to_" + to);

                            sm.append("    [").append(actionLabel).append("] ")
                              .append(mi.safeVar).append("=").append(fromIdx)
                              .append(" -> ").append(String.format("%.2f", defaultRate))
                              .append(" : (").append(mi.safeVar).append("'=").append(toIdx).append(");\n");
                        }
                    } else {
                        // only one state, self-loop it
                        sm.append("    // Only one state available, self-loop maintained\n");
                        sm.append("    [] true -> 1.00 : (").append(mi.safeVar).append("'=").append(mi.safeVar).append(");\n");
                    }
                }

                sm.append("\n");

                sm.append("endmodule\n\n");
            }
            
            // NEW: show any spatial warnings (non-blocking)
            if (spatialWarnings.length() > 0) {
                JOptionPane.showMessageDialog(
                    null,
                    "Spatial validation warnings (agents marked unavailable):\n" + spatialWarnings,
                    "Spatial Checks", JOptionPane.WARNING_MESSAGE
                );
            }
            
           // ======================================= AFTER all modules have been added =======================================

           // (A) Per-module state labels
           for (ModuleInfo miLabel : modules) {
               for (int s = 0; s < miLabel.states.size(); s++) {
                  String stateName = capitalize(miLabel.states.get(s));
                  sm.append("label \"")
                    .append(miLabel.safeVar).append("_").append(stateName)
                    .append("\" = (")
                    .append(miLabel.safeVar).append("=").append(s)
                    .append(");\n");
               }
               sm.append("\n");
            }
	            
	        // (B) Global reachX labels:
			//         - allAvailableModules_reachX  : ALL behaviourally-available modules that have state X are in it
			//         - allModules_reachX           : ALL modules in the model have state X and are in it
		
		    int totalModules = modules.size();
	
		    // key = lowercase state name (e.g. "idle", "extinguishing")
		    class StateGroup {
		        String canonicalName;                 // e.g. "Idle", "Extinguishing"
		        List<String> allTerms = new ArrayList<>();        // all modules that have this state
		        List<String> availableTerms = new ArrayList<>();  // only behaviourally-available modules
		    }
		    
		    Map<String, StateGroup> stateGroups = new LinkedHashMap<>();
		
		    for (ModuleInfo m : modules) {
		        for (int i = 0; i < m.states.size(); i++) {
		            String st = m.states.get(i);
		            String key = st.trim().toLowerCase();
		            StateGroup g = stateGroups.computeIfAbsent(key, k -> {
		                StateGroup ng = new StateGroup();
		                ng.canonicalName = capitalize(st.trim().toLowerCase());
		                return ng;
		            });
		
		            String term = m.safeVar + "=" + i;
		            g.allTerms.add(term);
		
		            if (m.isBehaviourallyAvailable) {
		                g.availableTerms.add(term);
		            }
		        }
		    }
		
		    // Emit labels
		    for (StateGroup g : stateGroups.values()) {
		        String stateName = g.canonicalName;
		
		        // 1) allAvailableModules_reachX: only available modules
		        if (!g.availableTerms.isEmpty()) {
		            String availLabel = "allAvailableModules_reach" + stateName;
		            sm.append("label \"").append(availLabel).append("\" = (")
		              .append(String.join(" & ", g.availableTerms))
		              .append(");\n");
		        }
		
		        // 2) allModules_reachX: only if EVERY module has that state
		        if (g.allTerms.size() == totalModules) {
		            String allLabel = "allModules_reach" + stateName;
		            sm.append("label \"").append(allLabel).append("\" = (")
		              .append(String.join(" & ", g.allTerms))
		              .append(");\n");
		        }
		    }
		
		    sm.append("\n");


            endConvertPrismModel = System.currentTimeMillis();
            memConvertEndPrismModel = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            outTextSM.setText(sm.toString());
            //JOptionPane.showMessageDialog(null, "Conversion to PRISM file completed successfully!");
            System.out.println("Conversion to PRISM file completed successfully!");

            final String prismModelText = sm.toString();

            // ---------- Properties ----------
            startConvertProps = System.currentTimeMillis();
            memStartConvertProps = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            String timeInput = JOptionPane.showInputDialog("Enter the time limit for the properties:");
            int timeLimit = (timeInput != null && !timeInput.trim().isEmpty()) ? Integer.parseInt(timeInput.trim()) : 10;

            StringBuilder props = new StringBuilder("// Generated PRISM Properties\n\n");

            // Per-module reachability and steady-state (readable)
            for (ModuleInfo mi : modules) {
                int maxProps = Math.min(10, Math.max(5, mi.states.size()));
                props.append("// --- Module ").append(mi.safeModule).append(" ---\n");
                for (int i = 0; i < Math.min(maxProps, mi.states.size()); i++) {
                    props.append("P>0.9 [ F (").append(mi.safeVar).append("=").append(i).append(") ]  // reach ")
                         .append(mi.states.get(i)).append("\n");
                    props.append("P<=0.1 [ F (").append(mi.safeVar).append("=").append(i).append(") ]  // reach ")
                    .append(mi.states.get(i)).append("\n");
                    props.append("P=?  [ F (").append(mi.safeVar).append("=").append(i).append(") ]  // reach ")
                         .append(mi.states.get(i)).append("\n");
                    props.append("P=?  [ F<=").append(timeLimit).append(" (")
                         .append(mi.safeVar).append("=").append(i).append(") ]  // reach ")
                         .append(mi.states.get(i)).append(" within timebound = ")
                         .append(timeLimit).append("\n\n");
                }
                // Long-run per-state (also readable)
                for (int i = 0; i < mi.states.size(); i++) {
                    props.append("S=? [ ").append(mi.safeVar).append("=").append(i).append(" ]  // long-run ")
                         .append(mi.safeModule).append(" in state ").append(mi.states.get(i)).append("\n");
                }
               props.append("\n");
            }
            
	         // ==================================================== Global properties: allModules_reachX & allAvailableModules_reachX ====================================================
	
	         Pattern globalLabelPattern = Pattern.compile(
	             "label\\s+\"(all(?:Available)?Modules_reach[^\"]+)\"\\s*="
	         );
	         Matcher mm = globalLabelPattern.matcher(prismModelText);
	         Set<String> globalLabels = new LinkedHashSet<>();
	         while (mm.find()) {
	             globalLabels.add(mm.group(1));   // e.g. allModules_reachIdle, allAvailableModules_reachExtinguishing
	         }
	
	         for (String label : globalLabels) {
	             props.append("// === Global coordination property: ").append(label).append(" ===\n");
	             props.append("P>0.9 [ F (\"").append(label).append("\") ]").append("  // with high probability, ").append(label).append(" eventually holds\n");
	             props.append("P<=0.1 [ F (\"").append(label).append("\") ]").append("\n");
	             props.append("P=?  [ F (\"").append(label).append("\") ]").append("  // probability that ").append(label).append(" eventually holds\n\n");
	         }


            // === Generic, case-study-agnostic resource checks ===
            //List<BoundedVar> boundedVars = extractBoundedVars(prismModelText);
            List<BoundedVar> boundedVars = extractBoundedVarsFromCTMC(prismModelText);
            Map<String,String> constants  = extractConstants(prismModelText); // NAME -> literal value
            boolean hasCondAll = prismModelText.contains("formula COND_ALL");

	        //==============================================
	        // 🔹 SECTION A: Verification Properties (Logic Consistency)
	        // Check correctness of model constants, thresholds, and ensemble conditions (COND_ALL).
	        //==============================================
	
	        // Match constants (like FIRE_LEVEL) with corresponding variables (like fire_level)
	        appendMatchedConstVarSteadyStateProps(boundedVars, constants, hasCondAll, props);
	
	        //=============================================
	        // 🔹 SECTION B: Behavioural Analysis (Steady-State Trends)
	        // Analyze how resources behave statistically in steady state, independent of constants. Useful for long-run performance trends.
	        //==============================================
	
	        // Append resource-based checks (fire_level, water_level, etc.)
	        appendGenericSteadyStateProps(boundedVars, props);
	
	        //==============================================
	        // 🔹 SECTION C: Time-Bounded Resource Checks
	        // Check how fast resources reach certain conditions (empty/low) within the given time limit.
	        //==============================================
	
	        // Append time-bounded reachability checks using level labels (e.g. _empty, _low)
	        appendLevelLabelTimeBounds(prismModelText, timeLimit, props);

            outTextProp.setText(props.toString());
            endConvertProps = System.currentTimeMillis();
            memEndConvertProps = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            //JOptionPane.showMessageDialog(null, "Creation of properties file completed successfully!");
            System.out.println("Creation of properties file completed successfully!");

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to convert to PRISM file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Simple bounded var record ----
    static class BoundedVar {
        final String name; final int min; final int max;
        BoundedVar(String name, int min, int max) { this.name = name; this.min = min; this.max = max; }
    }

    // ---- Extract ALL bounded integer variables (global or module-local) ----
    private static List<BoundedVar> extractBoundedVars(String prismModelText) {
        List<BoundedVar> vars = new ArrayList<>();
        if (prismModelText == null) return vars;

        String text = prismModelText.replaceAll("(?m)//.*?$", "").replaceAll("(?s)/\\*.*?\\*/", "");

        Pattern p = Pattern.compile("(?s)(?<![A-Za-z0-9_])(?:global\\s+)?([A-Za-z_]\\w*)\\s*:\\s*\\[(\\d+)\\.\\.(\\d+)\\]\\s*init\\s*[^;]+;");
        Matcher m = p.matcher(text);
        while (m.find()) {
            String n = m.group(1);
            int min = Integer.parseInt(m.group(2));
            int max = Integer.parseInt(m.group(3));
            if (min <= max) vars.add(new BoundedVar(n, min, max));
        }
        return vars;
    }

    // ---- Extract const NAME = value; pairs (int/double/bool) ----
    private static Map<String,String> extractConstants(String prismModelText) {
        Map<String,String> map = new LinkedHashMap<>();
        if (prismModelText == null) return map;

        String text = prismModelText.replaceAll("(?m)//.*?$", "").replaceAll("(?s)/\\*.*?\\*/", "");

        Pattern p = Pattern.compile("(?m)\\bconst\\s+(?:int|double|bool)\\s+([A-Za-z_]\\w*)\\s*=\\s*([^;]+);");
        Matcher m = p.matcher(text);
        while (m.find()) {
            String name = m.group(1).trim();
            String value = m.group(2).trim();
            map.put(name, value);
        }
        return map;
    }

    // ---- Key builders for robust matching ----
    private static Set<String> varKeys(String name) {
        Set<String> keys = new LinkedHashSet<>();
        if (name == null) return keys;
        String base = name.toLowerCase();
        String[] parts = base.split("[^a-z]+");
        List<String> toks = new ArrayList<>();
        for (String p : parts) if (!p.isEmpty()) toks.add(p);
        if (!toks.isEmpty()) {
            keys.add(String.join("", toks));
            keys.addAll(toks);
            if (toks.size() >= 2) {
                String last = toks.get(toks.size()-1);
                String prev = toks.get(toks.size()-2);
                keys.add(prev + last);
                if (last.equals("level")) keys.add(prev);
            }
        }
        return keys;
    }
    private static Set<String> constKeys(String name) {
        Set<String> keys = new LinkedHashSet<>();
        if (name == null) return keys;
        String base = name.toLowerCase();
        String[] parts = base.split("[^a-z]+");
        List<String> toks = new ArrayList<>();
        for (String p : parts) if (!p.isEmpty()) toks.add(p);
        if (!toks.isEmpty()) {
            keys.add(String.join("", toks));
            keys.addAll(toks);
            if (toks.size() >= 2) {
                String last = toks.get(toks.size()-1);
                String prev = toks.get(toks.size()-2);
                keys.add(prev + last);
                if (last.equals("level")) keys.add(prev);
            }
        }
        return keys;
    }

    // ---- Match constants to variables and emit steady-state S=? using const threshold ----
    private static void appendMatchedConstVarSteadyStateProps(
            List<BoundedVar> vars,
            Map<String,String> consts,
            boolean hasCondAll,
            StringBuilder props) {

        if (vars == null || vars.isEmpty() || consts == null || consts.isEmpty()) return;

        // index vars by multiple keys
        Map<String, List<BoundedVar>> varsByKey = new LinkedHashMap<>();
        for (BoundedVar v : vars) {
            for (String k : varKeys(v.name)) {
                varsByKey.computeIfAbsent(k, kk -> new ArrayList<>()).add(v);
            }
        }

        boolean headerAdded = false;

        for (Map.Entry<String,String> ce : consts.entrySet()) {
            String cName = ce.getKey();
            String cVal  = ce.getValue();

            // gather candidates via any overlapping key
            Set<BoundedVar> hit = new LinkedHashSet<>();
            for (String ck : constKeys(cName)) {
                List<BoundedVar> lst = varsByKey.get(ck);
                if (lst != null) hit.addAll(lst);
            }
            if (hit.isEmpty()) continue;

            if (!headerAdded) {
                props.append("// === Steady-state checks driven by constants (auto-matched) ===\n");
                headerAdded = true;
            }
            for (BoundedVar v : hit) {
                String vn = v.name.toLowerCase();
                if (vn.endsWith("_state")) continue; // skip module state indices

                props.append("// ").append(v.name).append(" vs ").append(cName)
                     .append("   (").append(v.name).append(" in [").append(v.min).append("..").append(v.max).append("], ")
                     .append(cName).append("=").append(cVal).append(")\n");

                props.append("S=? [ ").append(v.name).append(" <= ").append(cName).append(" ]")
                     .append("   // long-run ").append(v.name).append(" at/under ").append(cName).append("\n");
                props.append("S=? [ ").append(v.name).append(" = ").append(v.min).append(" ]")
                     .append("   // long-run ").append(v.name).append(" at minimum (empty)\n");

                if (hasCondAll) {
                    props.append("S=? [ COND_ALL & (").append(v.name).append(" <= ").append(cName).append(") ]\n");
                    props.append("S=? [ COND_ALL & (").append(v.name).append(" = ").append(v.min).append(") ]\n");
                }
                props.append("\n");
            }
        }
    }
    
    private static void appendLevelLabelTimeBounds(String prismModelText, int timeLimit, StringBuilder props) {
        if (prismModelText == null) return;     // ---- Time-bounded checks using *_empty / *_low labels (if present) ----

        Pattern lab = Pattern.compile("label\\s+\"([A-Za-z0-9_]+)_(empty|low)\"\\s*=");
        Matcher m = lab.matcher(prismModelText);
        Set<String> bases = new LinkedHashSet<>();
        while (m.find()) {
            String base = m.group(1);
            bases.add(base);
        }
        if (bases.isEmpty()) return;

        props.append("\n// === Time-bounded checks using level labels ===\n");
        for (String base : bases) {
            props.append("P=? [ F<=").append(timeLimit).append(" (\"").append(base).append("_empty\") ]")
                 .append("  // probability becomes empty within ").append(timeLimit).append("\n");
            props.append("P=? [ F<=").append(timeLimit).append(" (\"").append(base).append("_low\") ]")
                 .append("    // probability becomes low (<=1) within ").append(timeLimit).append("\n\n");
        }
    }
    
    private static List<BoundedVar> extractBoundedVarsFromCTMC(String modelText) {
        List<BoundedVar> vars = new ArrayList<>();
        Map<String, Integer> constants = new HashMap<>();

        // 1️. Extract constants (like FIRE_LEVEL = 2)
        Pattern constPat = Pattern.compile("(?i)const\\s+int\\s+(\\w+)\\s*=\\s*(\\d+)\\s*;");
        Matcher mc = constPat.matcher(modelText);
        while (mc.find()) {
            constants.put(mc.group(1), Integer.parseInt(mc.group(2)));
            //System.out.println("Detected constant: " + mc.group(1) + " = " + mc.group(2));
        }

        // 2️. Extract globals (like fire_level : [0..FIRE_LEVEL])
        Pattern varPat = Pattern.compile("(?i)(global|\\w+)\\s+(\\w+)\\s*:\\s*\\[(\\d+)\\.\\.([\\w_]+)\\]");
        Matcher mv = varPat.matcher(modelText);
        while (mv.find()) {
            String name = mv.group(2);
            String minStr = mv.group(3);
            String maxStr = mv.group(4);
            int min = Integer.parseInt(minStr);
            int max;

            if (maxStr.matches("\\d+")) {
                max = Integer.parseInt(maxStr);
            } else if (constants.containsKey(maxStr)) {
                max = constants.get(maxStr);
            } else {
                max = min; // fallback
            }

            vars.add(new BoundedVar(name, min, max));
           // System.out.println("Extracted variable: " + name + " [" + min + ".." + max + "]");
        }

        return vars;
    }
    
 // ---- Capitalize helper ----
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ---- Generic steady-state checks for resource-like variables ----
    private static void appendGenericSteadyStateProps(List<BoundedVar> vars, StringBuilder props) {
        //if (vars == null || vars.isEmpty()) return;
    	 if (vars == null) {
    	        System.out.println("appendGenericSteadyStateProps(): vars is NULL!");
    	        return;
    	    }
    	    if (vars.isEmpty()) {
    	        System.out.println("appendGenericSteadyStateProps(): vars is EMPTY!");
    	        return;
    	    }
    	    
    	    String summary = vars.stream()
    	    	    .map(v -> v.name + "[" + v.min + ".." + v.max + "]")
    	    	    .reduce((a, b) -> a + ", " + b)
    	    	    .orElse("(none)");
        
    	    props.append("// === Generic steady-state checks for resource-like variables ===\n");

        for (BoundedVar v : vars) {
            String vn = v.name.toLowerCase();
            if (vn.endsWith("_state")) continue; // skip module state indices
            if (!vn.contains("level")) continue; // focus on *level variables

            int low = (v.min < v.max) ? v.min + 1 : v.min;

            props.append("// Variable ").append(v.name)
                 .append(" has domain [").append(v.min).append("..").append(v.max).append("]\n");

         // --- Steady-state probability at minimum ---
            props.append("S=? [ ").append(v.name).append(" = ").append(v.min).append(" ]")
                 .append("   // Long-run probability that ").append(v.name)
                 .append(" is at its minimum (empty/depleted)\n");

            // --- Steady-state probability at low or minimum ---
            if (v.max > v.min) {
                props.append("S=? [ ").append(v.name).append(" <= ").append(low).append(" ]")
                     .append("   // Long-run probability that ").append(v.name)
                     .append(" is at or below threshold (near depletion)\n");
            }

            // --- Steady-state probability near maximum (optional, resource saturation) ---
            if (v.max > v.min) {
                int nearMax = (v.max > v.min + 1) ? v.max - 1 : v.max;
                props.append("S=? [ ").append(v.name).append(" >= ").append(nearMax).append(" ]")
                     .append("   // Long-run probability that ").append(v.name)
                     .append(" is high/saturated (≥ near maximum)\n");
            }
    	    //System.out.println("appendGenericSteadyStateProps(): received " + vars + " as variables.");

            props.append("\n");
        }
 
        class State {
            String name; // e.g., "depot", "fire", "extinguish", "Fail"
            int id;      // e.g., 0, 1, 2, 3

            public State(String name, int id) {
                this.name = name;
                this.id = id;
            }
        }
    }
}