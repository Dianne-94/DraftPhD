package mainCodingFiles;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XMIUtilsUpdate {
	  public static String generateXMI(
		      Set<String> coorEnsembleNames,
		      Set<String> rootEnsembleNames,
		      Set<String> ensembleNames,
		      Set<String> componentNames,
		      Set<String> transitionNames,
		      Set<String> transitionRate,
		      Set<String> traitInformation,
		      Set<String> roleNames,
		      Map<String, String> utilityValue,
		      int gridY,
		      int gridX,
		      Set<String> stateNames,
		      Set<String> conditions,
		      Set<String> memberships,
		      Set<String> cardinalities,
		      Map<String, int[]> roleCardinalities,
		      Set<String> componentPosition
		  ) {
		    StringBuilder xmi = new StringBuilder();

		    // ----- Header -----
		    xmi.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		    xmi.append("<tcoel:TCOEL xmi:version=\"2.0\"\n")
		       .append("  xmlns:xmi=\"http://www.omg.org/XMI\"\n")
		       .append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
		       .append("  xmlns:tcoel=\"http://example/tcoel\"\n")
		       .append("  xsi:schemaLocation=\"/convertTcoel2Ctmc/tcoel.ecore tcoel.ecore\">\n\n");

		    // Prepare lists with stable order
		    List<String> ensembles = safeList(ensembleNames);
		    List<String> components = safeList(componentNames);
		    List<String> rolesRaw = safeList(roleNames);
		    List<String> states = safeList(stateNames);

		    // Remove "coordinator" from agent roles
		    List<String> roles = new ArrayList<>();
		    for (String r : rolesRaw) {
		      if (r == null) continue;
		      if (!"coordinator".equalsIgnoreCase(r.trim())) roles.add(r.trim());
		    }

		    // Positions: parse to ordered list of (x,y)
		    List<int[]> positions = parsePositions(componentPosition);

		    // Coordinator prefixes (fallback)
		    List<String> coorBaseList = safeList(coorEnsembleNames);
		    if (coorBaseList.isEmpty()) coorBaseList.add("Coordinator");

		    int globalCoordinatorIndex = 1;
		    int ensembleIndex = 0;

		    // ----- Ensembles -----
		    for (String ens : ensembles) {
		      if (ens == null || ens.isBlank()) continue;

		      String ensName = ens.trim();
		      String ensUtility = (utilityValue != null && utilityValue.containsKey(ensName))
		          ? utilityValue.get(ensName)
		          : "0.0";

		      xmi.append("  <ensemble name=\"").append(escape(ensName))
		         .append("\" utility=\"").append(escape(ensUtility)).append("\">\n");

		      // ---- (A) Coordinator component (1 per ensemble) ----
		      String coorPrefix = coorBaseList.get(ensembleIndex % coorBaseList.size());
		      String coordinatorName = coorPrefix + "_" + ensName + "_" + globalCoordinatorIndex++;
		      String prefix = extractPrefix(ensName);

		      int[] coorPos = pickPosition(positions, 0); // deterministic: first position for coordinator
		      xmi.append("    <component name=\"").append(escape(prefix)).append("_").append(escape(coordinatorName)).append("\">\n");
		      xmi.append("      <role name=\"coordinator\">\n");
		      for (String st : states) {
		        xmi.append("        <state name=\"").append(escape(st)).append("\"/>\n");
		      }
		      xmi.append("      </role>\n");
		      xmi.append("      <position x=\"").append(coorPos[0]).append("\" y=\"").append(coorPos[1]).append("\"/>\n");
		      xmi.append("    </component>\n");

		      // ---- (B) Emit Ensemble-level Conditions (membership/cardinality/conditions) ----
		      emitConditionsBlock(xmi, conditions, memberships, cardinalities, roleCardinalities, ensName);

		      // ---- (C) Generate participant components based on role cardinalities ----
		      // Pick a "primary role" for sizing. If none, fallback to max=2.
		      int maxAgents = resolveMaxAgents(roleCardinalities, roles);

		      // Ensure we have at least 1 component template name
		      if (components.isEmpty()) components = List.of("agent");

		      // Track uniqueness
		      Map<String, Integer> nameCounts = new HashMap<>();

		      // Start positions from index 1 onwards (0 used for coordinator)
		      int posCursor = 1;

		      for (int i = 0; i < maxAgents; i++) {
		        // Choose base component name
		        String base = components.get(i % components.size());
		        int count = nameCounts.getOrDefault(base, 0) + 1;
		        nameCounts.put(base, count);
		        String compName = base + count;

		        // Choose a role (normalize)
		        String rawRole = roles.isEmpty() ? "agent" : roles.get(i % roles.size());
		        String finalRole = normalizeRole(rawRole);

		        // Assign ONE position per component
		        int[] p = pickPosition(positions, posCursor++);
		        xmi.append("    <component name=\"").append(escape(prefix)).append("_").append(escape(compName)).append("\">\n");
		        xmi.append("      <role name=\"").append(escape(finalRole)).append("\">\n");
		        for (String st : states) {
		          xmi.append("        <state name=\"").append(escape(st)).append("\"/>\n");
		        }
		        xmi.append("      </role>\n");
		        xmi.append("      <position x=\"").append(p[0]).append("\" y=\"").append(p[1]).append("\"/>\n");

		        // ---- Component-level transitions (optional but useful) ----
		        emitTransitions(xmi, transitionNames, transitionRate);

		        xmi.append("    </component>\n");
		      }

		      xmi.append("  </ensemble>\n\n");
		      ensembleIndex++;
		    }

		    // ----- Environment -----
		    xmi.append("  <environment>\n");
		    appendMapSection(xmi, gridX, gridY);
		    // Optional: preconditions (if your extractor stores them in conditions, you can also push them here)
		    xmi.append("  </environment>\n");

		    xmi.append("</tcoel:TCOEL>\n");
		    return xmi.toString();
		  }

		  // ========================= Helpers =========================

		  private static List<String> safeList(Set<String> s) {
		    if (s == null || s.isEmpty()) return new ArrayList<>();
		    // If you used HashSet, order is random; LinkedHashSet is recommended.
		    return new ArrayList<>(s);
		  }

		  private static String extractPrefix(String ensemble) {
		    String prefix = ensemble.replaceAll("[a-z]", "");
		    if (prefix.isEmpty()) prefix = ensemble.substring(0, Math.min(2, ensemble.length())).toUpperCase();
		    return prefix;
		  }

		  // Accept formats: "(x,y)" or "x,y" or "Position(x,y)" etc.
		  private static List<int[]> parsePositions(Set<String> componentPosition) {
		    List<int[]> out = new ArrayList<>();
		    if (componentPosition == null) return out;

		    Pattern p = Pattern.compile("(-?\\d+)\\s*,\\s*(-?\\d+)");
		    for (String raw : componentPosition) {
		      if (raw == null) continue;
		      Matcher m = p.matcher(raw);
		      if (m.find()) {
		        int x = Integer.parseInt(m.group(1));
		        int y = Integer.parseInt(m.group(2));
		        out.add(new int[]{x, y});
		      }
		    }
		    // fallback if empty
		    if (out.isEmpty()) out.add(new int[]{0, 0});
		    return out;
		  }

		  private static int[] pickPosition(List<int[]> positions, int index) {
		    if (positions == null || positions.isEmpty()) return new int[]{0, 0};
		    return positions.get(index % positions.size());
		  }

		  // normalize role names across scenarios
		  private static String normalizeRole(String raw) {
		    if (raw == null) return "agent";
		    String r = raw.trim().toLowerCase();

		    if (r.equals("brigades") || r.equals("firebrigades") || r.contains("brigade"))
		      return "FireBrigadeRole";

		    if (r.equals("ambulances") || r.contains("ambulance"))
		      return "AmbulanceRole";

		    if (r.equals("coordinator") || r.contains("station"))
		      return "CoordinatorRole";

		    return raw.trim();
		  }

		  private static int resolveMaxAgents(Map<String, int[]> roleCardinalities, List<String> roles) {
		    // Preferred: use cardinality of the first extracted role (after normalization)
		    if (roleCardinalities != null && !roles.isEmpty()) {
		      String r0 = roles.get(0);
		      int[] mm = roleCardinalities.get(r0);
		      if (mm == null) mm = roleCardinalities.get(normalizeRole(r0));
		      if (mm != null && mm.length >= 2) return Math.max(1, mm[1]);
		    }

		    // Otherwise take the maximum "max" found among all role cardinalities
		    if (roleCardinalities != null && !roleCardinalities.isEmpty()) {
		      int best = 1;
		      for (int[] mm : roleCardinalities.values()) {
		        if (mm != null && mm.length >= 2) best = Math.max(best, mm[1]);
		      }
		      return Math.max(1, best);
		    }

		    // ultimate fallback
		    return 2;
		  }

		  private static void emitConditionsBlock(
		      StringBuilder xmi,
		      Set<String> conditions,
		      Set<String> memberships,
		      Set<String> cardinalities,
		      Map<String, int[]> roleCardinalities,
		      String ensembleName
		  ) {
		    // generic conditions
		    if (conditions != null) {
		      for (String c : conditions) {
		        if (c == null || c.isBlank()) continue;
		        xmi.append("    <condition name=\"condition\" type=\"expr\" value=\"")
		           .append(escape(c.trim())).append("\" description=\"extracted\"/>\n");
		      }
		    }

		    // membership expressions
		    if (memberships != null) {
		      for (String m : memberships) {
		        if (m == null || m.isBlank()) continue;
		        xmi.append("    <condition name=\"membership\" type=\"expr\" value=\"")
		           .append(escape(m.trim())).append("\" description=\"membership\"/>\n");
		      }
		    }

		    // cardinalities as raw text (if extracted)
		    if (cardinalities != null) {
		      for (String cd : cardinalities) {
		        if (cd == null || cd.isBlank()) continue;
		        xmi.append("    <condition name=\"cardinality\" type=\"range\" value=\"")
		           .append(escape(cd.trim())).append("\" description=\"cardinality\"/>\n");
		      }
		    }

		    // role cardinalities (structured)
		    if (roleCardinalities != null) {
		      for (Map.Entry<String, int[]> e : roleCardinalities.entrySet()) {
		        String role = e.getKey();
		        int[] mm = e.getValue();
		        if (role == null || mm == null || mm.length < 2) continue;
		        xmi.append("    <condition name=\"CARD_").append(escape(role))
		           .append("\" type=\"range\" value=\"").append(mm[0]).append("..").append(mm[1])
		           .append("\" description=\"role cardinality\"/>\n");
		      }
		    }
		  }

		  private static void emitTransitions(StringBuilder xmi, Set<String> transitionNames, Set<String> transitionRate) {
		    // If your extractor doesn't provide these, it's ok; but at least output placeholders cleanly.
		    List<String> tn = transitionNames == null ? List.of() : new ArrayList<>(transitionNames);
		    List<String> tr = transitionRate == null ? List.of() : new ArrayList<>(transitionRate);

		    int n = Math.min(tn.size(), tr.size());
		    for (int i = 0; i < n; i++) {
		      String name = tn.get(i);
		      String rate = tr.get(i);
		      if (name == null || name.isBlank()) continue;
		      if (rate == null || rate.isBlank()) rate = "1.0";

		      xmi.append("      <transition name=\"").append(escape(name))
		         .append("\" condition=\"\" rate=\"").append(escape(rate)).append("\">\n")
		         .append("        <initial name=\"\"/>\n")
		         .append("        <target name=\"\"/>\n")
		         .append("      </transition>\n");
		    }
		  }

		  private static void appendMapSection(StringBuilder xmiContent, int gridX, int gridY) {
		    final int DEFAULT_X = 10;
		    final int DEFAULT_Y = 10;

		    int finalX = (gridX > 0) ? gridX : DEFAULT_X;
		    int finalY = (gridY > 0) ? gridY : DEFAULT_Y;

		    xmiContent.append("    <map>\n");
		    xmiContent.append("      <xGrid>").append(finalX).append("</xGrid>\n");
		    xmiContent.append("      <yGrid>").append(finalY).append("</yGrid>\n");
		    xmiContent.append("    </map>\n");
		  }

		  private static String escape(String s) {
		    if (s == null) return "";
		    return s.replace("&", "&amp;")
		            .replace("\"", "&quot;")
		            .replace("<", "&lt;")
		            .replace(">", "&gt;");
		  }
}
