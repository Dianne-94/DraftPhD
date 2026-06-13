// File: mainCodingFiles/XMIUtils.java
package mainCodingFiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XMIUtils {

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
            int gridY, int gridX,
            Set<String> stateNames,
            Set<String> conditions,
            Set<String> memberships,
            Set<String> cardinalities,
            Map<String, int[]> roleCardinalities,
            Set<String> componentPosition
    ) {
        StringBuilder xmiContent = new StringBuilder();
        xmiContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xmiContent.append(
                "<tcoel:TCOEL xmi:version=\"2.0\"\n" +
                        "    xmlns:xmi=\"http://www.omg.org/XMI\"\n" +
                        "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        xmiContent.append("    xmlns:tcoel=\"http://example/tcoel\"\n");
        xmiContent.append("    xsi:schemaLocation=\"/convertTcoel2Ctmc/tcoel.ecore tcoel.ecore\">\n");

        List<String> coorBaseList = toList(coorEnsembleNames);
        if (coorBaseList.isEmpty()) {
            System.out.println("[Warning] No coordinator ensemble detected — using default prefix 'Root'.");
            coorBaseList.add("Root");
        }

        List<String> ensembleList = toList(ensembleNames);
        List<String> componentBaseList = toList(componentNames);
        List<String> roleList = filterNonCoordinatorRoles(roleNames);
        List<String> stateList = toList(stateNames);

        List<Position> positions = parsePositions(componentPosition);
        int[] posIdx = new int[]{0};

        List<Condition> parsedConditions = parseConditions(conditions);
        List<Trait> parsedTraits = parseTraits(traitInformation);

        Map<String, String> rateByName = parseRateMap(transitionRate);
        List<Transition> parsedTransitions = parseTransitions(transitionNames, rateByName, stateList);

        int globalCoordinatorIndex = 1;
        int ensembleIndex = 0;

        for (String ensemble : ensembleList) {
            String utility = resolveUtility(utilityValue, ensemble);
            xmiContent.append("   <ensemble name=\"")
                    .append(escapeXml(ensemble))
                    .append("\" utility=\"")
                    .append(escapeXml(utility))
                    .append("\">\n");

            // Conditions under ensemble
            for (Condition c : parsedConditions) {
                xmiContent.append("      <condition name=\"").append(escapeXml(c.name)).append("\"")
                        .append(" description=\"").append(escapeXml(c.description)).append("\"")
                        .append(" type=\"").append(escapeXml(c.type)).append("\"")
                        .append(" value=\"").append(escapeXml(c.value)).append("\"/>\n");
            }

            // Coordinator component (keeps your naming approach)
            String coorPrefix = coorBaseList.get(ensembleIndex % coorBaseList.size());
            String coordinatorNameRaw = coorPrefix + ensemble + globalCoordinatorIndex;
            globalCoordinatorIndex++;

            String ensemblePrefix = derivePrefixFromEnsemble(ensemble);
            String refinedName = coordinatorNameRaw.replace(ensemble, "").trim().replace("=", "").trim();

            xmiContent.append("      <component name=\"")
                    .append(escapeXml(ensemblePrefix))
                    .append("_")
                    .append(escapeXml(refinedName))
                    .append("\">\n");
            xmiContent.append("         <role name=\"coordinator\">\n");

            // Coordinator: keep state list if provided (fallback behavior)
            for (String state : stateList) {
                xmiContent.append("            <state name=\"").append(escapeXml(state)).append("\" />\n");
            }

            xmiContent.append("         </role>\n");
            appendSinglePosition(xmiContent, nextPosition(positions, posIdx));
            xmiContent.append("      </component>\n");

            // Agent generation (cardinalities of 'agent')
            int[] agentCard = (roleCardinalities == null)
                    ? null
                    : roleCardinalities.get("agent");
            int min = 1;
            int max = 1;
            if (agentCard != null && agentCard.length >= 2) {
                min = Math.max(1, agentCard[0]);
                max = Math.max(min, agentCard[1]);
            } else {
                max = 2; // your prior default behavior
            }

            Map<String, Integer> nameCounts = new HashMap<>();

            // Prefer "agent" if present (matches your expected output)
            String forcedRole = roleList.stream().anyMatch(r -> "agent".equalsIgnoreCase(r)) ? "agent" : null;

            for (int i = 0; i < max; i++) {
                String baseName = (!componentBaseList.isEmpty())
                        ? componentBaseList.get(i % componentBaseList.size())
                        : "agent";

                int count = nameCounts.getOrDefault(baseName, 0) + 1;
                nameCounts.put(baseName, count);

                String componentName = baseName + count;

                String finalRole;
                if (forcedRole != null) {
                    finalRole = forcedRole;
                } else if (!roleList.isEmpty()) {
                    finalRole = roleList.get(i % roleList.size());
                } else {
                    finalRole = "agent";
                }

                // id derived from baseName (e.g., fireTruck -> FT1)
                String idPrefix = deriveIdPrefixFromBaseName(baseName);
                String componentId = idPrefix + count;

                // Component name: closer to expected output (no ensemblePrefix_ prepended)
                xmiContent.append("      <component name=\"")
                        .append(escapeXml(componentName))
                        .append("\" id=\"").append(escapeXml(componentId))
                        .append("\">\n");

                // Traits under component
                for (Trait t : parsedTraits) {
                    xmiContent.append("         <traits traitName=\"")
                            .append(escapeXml(t.traitName))
                            .append("\" description=\"").append(escapeXml(t.description))
                            .append("\"/>\n");
                }

                xmiContent.append("         <role name=\"").append(escapeXml(finalRole)).append("\">\n");

                // If transitions exist, emit transitions; else fallback to states
                if (!parsedTransitions.isEmpty()) {
                    for (Transition tr : parsedTransitions) {
                        xmiContent.append("            <transition name=\"")
                                .append(escapeXml(tr.name))
                                .append("\" rate=\"").append(escapeXml(tr.rate))
                                .append("\" condition=\"").append(escapeXml(tr.condition))
                                .append("\">\n");
                        xmiContent.append("               <initial name=\"").append(escapeXml(tr.initial)).append("\"/>\n");
                        xmiContent.append("               <target  name=\"").append(escapeXml(tr.target)).append("\"/>\n");
                        xmiContent.append("            </transition>\n");
                    }
                } else {
                    for (String state : stateList) {
                        xmiContent.append("            <state name=\"").append(escapeXml(state)).append("\" />\n");
                    }
                }

                xmiContent.append("         </role>\n");
                appendSinglePosition(xmiContent, nextPosition(positions, posIdx));
                xmiContent.append("      </component>\n");
            }

            xmiContent.append("   </ensemble>\n");
            ensembleIndex++;
        }

        xmiContent.append("  <environment>\n");
        appendMapSection(xmiContent, gridX, gridY);
        xmiContent.append("  </environment>\n");
        xmiContent.append("</tcoel:TCOEL>");

        return xmiContent.toString();
    }

    // ---------------- helpers ----------------

    private static List<String> toList(Set<String> set) {
        return (set == null) ? new ArrayList<>() : new ArrayList<>(set);
    }

    private static List<String> filterNonCoordinatorRoles(Set<String> roleNames) {
        List<String> out = new ArrayList<>();
        if (roleNames == null) return out;
        for (String rn : roleNames) {
            if (rn == null) continue;
            if (!"coordinator".equalsIgnoreCase(rn.trim())) {
                out.add(rn.trim());
            }
        }
        return out;
    }

    private static String resolveUtility(Map<String, String> utilityValue, String ensemble) {
        if (utilityValue == null || utilityValue.isEmpty()) return "1.0";
        String v = utilityValue.get(ensemble);
        if (v != null && !v.isBlank()) return v.trim();

        // common fallbacks if caller stores a default
        v = utilityValue.get("default");
        if (v != null && !v.isBlank()) return v.trim();
        v = utilityValue.get("*");
        if (v != null && !v.isBlank()) return v.trim();

        return "1.0";
    }

    private static String derivePrefixFromEnsemble(String ensemble) {
        if (ensemble == null || ensemble.isBlank()) return "EN";
        String caps = ensemble.replaceAll("[^A-Z]", "");
        if (!caps.isBlank()) return caps;
        String trimmed = ensemble.trim();
        return trimmed.substring(0, Math.min(2, trimmed.length())).toUpperCase();
    }

    private static String deriveIdPrefixFromBaseName(String baseName) {
        if (baseName == null || baseName.isBlank()) return "ID";
        String s = baseName.trim();

        // CamelCase: first letter + capitals (fireTruck -> f + T => FT)
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(s.charAt(0)));
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) sb.append(c);
        }
        String out = sb.toString();
        if (out.length() >= 2) return out;

        // fallback: first 2 letters
        String lettersOnly = s.replaceAll("[^A-Za-z]", "");
        if (lettersOnly.length() >= 2) return lettersOnly.substring(0, 2).toUpperCase();
        if (!lettersOnly.isEmpty()) return lettersOnly.substring(0, 1).toUpperCase();
        return "ID";
    }

    private static void appendSinglePosition(StringBuilder xmiContent, Position pos) {
        xmiContent.append("         <position>\n");
        xmiContent.append("            <x>").append(escapeXml(pos.x)).append("</x>\n");
        xmiContent.append("            <y>").append(escapeXml(pos.y)).append("</y>\n");
        xmiContent.append("         </position>\n");
    }

    private static Position nextPosition(List<Position> positions, int[] idx) {
        if (positions == null || positions.isEmpty()) return new Position("0", "0");
        int i = Math.max(0, idx[0]);
        Position p = positions.get(Math.min(i, positions.size() - 1));
        idx[0] = i + 1;
        return p;
    }

    private static List<Position> parsePositions(Set<String> componentPosition) {
        List<Position> out = new ArrayList<>();
        if (componentPosition == null) return out;

        for (String pos : componentPosition) {
            if (pos == null || pos.isBlank()) continue;
            String cleaned = pos.trim().replaceAll("[()\\[\\]]", "");
            String[] coords = cleaned.split(",");
            if (coords.length != 2) continue;
            out.add(new Position(coords[0].trim(), coords[1].trim()));
        }
        return out;
    }

    private static List<Condition> parseConditions(Set<String> conditions) {
        List<Condition> out = new ArrayList<>();
        if (conditions == null) return out;

        Pattern kv = Pattern.compile("(name|description|type|value)\\s*=\\s*\"?([^\"\\s,;|]+)\"?", Pattern.CASE_INSENSITIVE);

        for (String raw : conditions) {
            if (raw == null || raw.isBlank()) continue;
            String s = raw.trim();

            // key=value style
            Matcher m = kv.matcher(s);
            Map<String, String> map = new HashMap<>();
            while (m.find()) {
                map.put(m.group(1).toLowerCase(), m.group(2));
            }
            if (!map.isEmpty() && map.containsKey("name")) {
                String name = map.getOrDefault("name", "");
                String desc = map.getOrDefault("description", "");
                String type = map.getOrDefault("type", "numeric");
                String val = map.getOrDefault("value", desc);
                out.add(new Condition(name, desc, type, val));
                continue;
            }

            // delimited fallback: name,description,type,value (also supports | ; :)
            String[] parts = s.split("[,;|]");
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
            if (parts.length >= 1 && !parts[0].isBlank()) {
                String name = parts[0];
                String desc = (parts.length >= 2) ? parts[1] : "";
                String type = (parts.length >= 3) ? parts[2] : (looksNumeric(desc) ? "numeric" : "text");
                String val = (parts.length >= 4) ? parts[3] : desc;
                out.add(new Condition(name, desc, type, val));
            }
        }
        return out;
    }

    private static List<Trait> parseTraits(Set<String> traitInformation) {
        List<Trait> out = new ArrayList<>();
        if (traitInformation == null) return out;

        Pattern kv = Pattern.compile("(traitName|description)\\s*=\\s*\"?([^\"\\n\\r]+?)\"?(?=\\s*(traitName|description|$))",
                Pattern.CASE_INSENSITIVE);

        for (String raw : traitInformation) {
            if (raw == null || raw.isBlank()) continue;
            String s = raw.trim();

            // key=value style
            Matcher m = kv.matcher(s);
            Map<String, String> map = new HashMap<>();
            while (m.find()) {
                map.put(m.group(1).toLowerCase(), m.group(2).trim());
            }
            if (!map.isEmpty() && map.containsKey("traitname")) {
                String tn = map.getOrDefault("traitname", "");
                String desc = map.getOrDefault("description", "");
                if (!tn.isBlank()) out.add(new Trait(tn, desc));
                continue;
            }

            // traitName:description
            String[] colon = s.split(":", 2);
            if (colon.length == 2 && !colon[0].isBlank()) {
                out.add(new Trait(colon[0].trim(), colon[1].trim()));
                continue;
            }

            // delimited fallback: traitName,description
            String[] parts = s.split("[,;|]", 2);
            if (parts.length >= 1 && !parts[0].isBlank()) {
                String tn = parts[0].trim();
                String desc = (parts.length == 2) ? parts[1].trim() : "";
                out.add(new Trait(tn, desc));
            }
        }
        return out;
    }

    private static Map<String, String> parseRateMap(Set<String> transitionRate) {
        Map<String, String> out = new HashMap<>();
        if (transitionRate == null) return out;

        for (String raw : transitionRate) {
            if (raw == null || raw.isBlank()) continue;
            String s = raw.trim();

            // name=1.0
            int eq = s.indexOf('=');
            if (eq > 0 && eq < s.length() - 1) {
                String name = s.substring(0, eq).trim();
                String rate = s.substring(eq + 1).trim();
                if (!name.isBlank() && !rate.isBlank()) out.put(name, rate);
                continue;
            }

            // name,1.0
            String[] parts = s.split("[,;|]");
            if (parts.length >= 2) {
                String name = parts[0].trim();
                String rate = parts[1].trim();
                if (!name.isBlank() && !rate.isBlank()) out.put(name, rate);
            }
        }
        return out;
    }

    private static List<Transition> parseTransitions(Set<String> transitionNames,
                                                    Map<String, String> rateByName,
                                                    List<String> stateFallback) {
        List<Transition> out = new ArrayList<>();
        if (transitionNames == null) return out;

        Pattern kv = Pattern.compile("(name|rate|condition|initial|target)\\s*=\\s*\"?([^\"\\s,;|]+)\"?",
                Pattern.CASE_INSENSITIVE);

        for (String raw : transitionNames) {
            if (raw == null || raw.isBlank()) continue;
            String s = raw.trim();

            // key=value style
            Matcher m = kv.matcher(s);
            Map<String, String> map = new HashMap<>();
            while (m.find()) {
                map.put(m.group(1).toLowerCase(), m.group(2));
            }
            if (!map.isEmpty() && map.containsKey("name")) {
                String name = map.get("name");
                String rate = map.getOrDefault("rate", rateByName.getOrDefault(name, "1.0"));
                String cond = map.getOrDefault("condition", "");
                String initial = map.getOrDefault("initial", fallbackState(stateFallback, 0));
                String target = map.getOrDefault("target", fallbackState(stateFallback, 1));
                out.add(new Transition(name, rate, cond, initial, target));
                continue;
            }

            // delimited fallback:
            // - name,initial,target
            // - name,rate,initial,target
            // - name,rate,condition,initial,target
            String[] parts = s.split("[,;|]");
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
            if (parts.length == 0 || parts[0].isBlank()) continue;

            String name = parts[0];
            String rate = rateByName.getOrDefault(name, "1.0");
            String cond = "";
            String initial = fallbackState(stateFallback, 0);
            String target = fallbackState(stateFallback, 1);

            if (parts.length == 3) {
                initial = parts[1];
                target = parts[2];
            } else if (parts.length == 4) {
                // detect if parts[1] is rate
                if (looksNumeric(parts[1])) {
                    rate = parts[1];
                    initial = parts[2];
                    target = parts[3];
                } else if (looksNumeric(parts[3])) {
                    initial = parts[1];
                    target = parts[2];
                    rate = parts[3];
                } else {
                    rate = parts[1];
                    initial = parts[2];
                    target = parts[3];
                }
            } else if (parts.length >= 5) {
                rate = looksNumeric(parts[1]) ? parts[1] : rate;
                cond = parts[2];
                initial = parts[3];
                target = parts[4];
            }

            out.add(new Transition(name, rate, cond, initial, target));
        }

        return out;
    }

    private static String fallbackState(List<String> states, int idx) {
        if (states == null || states.isEmpty()) return (idx == 0) ? "Idle" : "Idle";
        if (states.size() == 1) return states.get(0);
        return states.get(Math.min(idx, states.size() - 1));
    }

    private static boolean looksNumeric(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty()) return false;
        return t.matches("[-+]?\\d+(\\.\\d+)?");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // Generate <map> section with fallback grid size
    private static void appendMapSection(StringBuilder xmiContent, int gridX, int gridY) {
        final int DEFAULT_X = 10;
        final int DEFAULT_Y = 10;

        int finalX = (gridX > 0) ? gridX : DEFAULT_X;
        int finalY = (gridY > 0) ? gridY : DEFAULT_Y;

        xmiContent.append("    <map>\n");
        xmiContent.append("        <xGrid>").append(finalX).append("</xGrid>\n");
        xmiContent.append("        <yGrid>").append(finalY).append("</yGrid>\n");
        xmiContent.append("    </map>\n");
    }

    private static String generateRandomUtilityFunction() {
        Random rand = new Random();
        double randomValue = rand.nextDouble();
        return String.format("%.2f", randomValue);
    }

    private static final class Position {
        final String x;
        final String y;

        Position(String x, String y) {
            this.x = (x == null || x.isBlank()) ? "0" : x;
            this.y = (y == null || y.isBlank()) ? "0" : y;
        }
    }

    private static final class Condition {
        final String name;
        final String description;
        final String type;
        final String value;

        Condition(String name, String description, String type, String value) {
            this.name = (name == null) ? "" : name;
            this.description = (description == null) ? "" : description;
            this.type = (type == null || type.isBlank()) ? "numeric" : type;
            this.value = (value == null) ? "" : value;
        }
    }

    private static final class Trait {
        final String traitName;
        final String description;

        Trait(String traitName, String description) {
            this.traitName = (traitName == null) ? "" : traitName;
            this.description = (description == null) ? "" : description;
        }
    }

    private static final class Transition {
        final String name;
        final String rate;
        final String condition;
        final String initial;
        final String target;

        Transition(String name, String rate, String condition, String initial, String target) {
            this.name = (name == null) ? "" : name;
            this.rate = (rate == null || rate.isBlank()) ? "1.0" : rate;
            this.condition = (condition == null) ? "" : condition;
            this.initial = (initial == null) ? "Idle" : initial;
            this.target = (target == null) ? "Idle" : target;
        }
    }
}
