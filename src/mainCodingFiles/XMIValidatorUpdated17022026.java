package mainCodingFiles;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public class XMIValidatorUpdated17022026 {

    private static final String CORRECT_HEADER =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<tcoel:TCOEL xmi:version=\"2.0\"\n" +
            "    xmlns:xmi=\"http://www.omg.org/XMI\"\n" +
            "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "    xmlns:tcoel=\"http://example/tcoel\"\n" +
            "    xsi:schemaLocation=\"/convertTcoel2Ctmc/tcoel.ecore tcoel.ecore\">";

    private static final java.util.Set<String> ALLOWED_TAGS = java.util.Set.of(
        "state", "transition", "ensemble", "component", "environment", "role", "xGrid", "yGrid"
    );

    // ====================== MAIN PIPELINE ======================
    public static String validateAndFix(File inputFile) throws IOException {
        String content = Files.readString(inputFile.toPath(), StandardCharsets.UTF_8);

        content = normalizeAllTagsToWhitelist(content);
        content = fixHeader(content);

        if (!isValidXML(content)) {
            content = autoFixXML(content);
        }

        content = ensureHierarchyAndTransitions(content);
        content = removeExtraNewlines(content);
        content = fixHeader(content);
        content = balanceTags(content);

        Files.writeString(inputFile.toPath(), content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        return content;
    }

    // ====================== HEADER ======================
    private static String fixHeader(String content) {
        content = content.replaceAll("(?is)^.*?(?=<\\s*(ensemble|component|environment|role|state|transition|xGrid|yGrid)\\b)", "");
        content = CORRECT_HEADER + "\n" + content.trim();
        if (!content.trim().endsWith("</tcoel:TCOEL>")) {
            content += "\n</tcoel:TCOEL>";
        }
        return content;
    }

    private static String normalizeAllTagsToWhitelist(String content) {
        Pattern pattern = Pattern.compile("<(/?)(\\w+)([^>]*)>");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        // Define exact casing for allowed tags
        java.util.Map<String, String> tagMap = java.util.Map.of(
            "state", "state",
            "transition", "transition",
            "ensemble", "ensemble",
            "component", "component",
            "environment", "environment",
            "role", "role",
            "xgrid", "xGrid",
            "ygrid", "yGrid"
        );

        while (matcher.find()) {
            String slash = matcher.group(1);
            String tag = matcher.group(2);
            String attr = matcher.group(3);

            // Preserve exact casing for allowed tags
            String normalized = tag;
            for (String key : tagMap.keySet()) {
                if (tag.equalsIgnoreCase(key)) {
                    normalized = tagMap.get(key);
                    break;
                }
            }

            matcher.appendReplacement(sb, "<" + slash + normalized + attr + ">");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ====================== AUTO FIX ======================
    private static String autoFixXML(String content) {
        String[] tags = {"ensemble", "component", "role", "state", "transition", "map", "condition", "xGrid", "yGrid"};
        for (String tag : tags) {
            content = ensureClosedTag(content, tag);
        }
        return content.trim();
    }

    private static String ensureClosedTag(String text, String tag) {
        int openCount = text.split(Pattern.quote("<" + tag), -1).length - 1;
        int closeCount = text.split(Pattern.quote("</" + tag + ">"), -1).length - 1;
        while (openCount > closeCount) {
            text += "</" + tag + ">";
            closeCount++;
        }
        return text;
    }

    // ====================== HIERARCHY & TRANSITIONS ======================
    private static String ensureHierarchyAndTransitions(String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringComments(true);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            removeEmptyTextNodes(root);

            // Smart auto-wrap only floating root-level nodes
           // autoWrapFloatingNodes(root);

            // Convert transitions with attributes to <initial>/<target> children
            NodeList transitionNodes = root.getElementsByTagName("transition");
            for (int i = 0; i < transitionNodes.getLength(); i++) {
                Element t = (Element) transitionNodes.item(i);
                boolean hasInitial = t.getElementsByTagName("initial").getLength() > 0;
                boolean hasTarget = t.getElementsByTagName("target").getLength() > 0;

                if ((t.hasAttribute("from") || t.hasAttribute("to")) && (!hasInitial || !hasTarget)) {
                    String from = t.getAttribute("from");
                    String to = t.getAttribute("to");
                    t.removeAttribute("from");
                    t.removeAttribute("to");

                    if (!hasInitial && !from.isEmpty()) {
                        Element init = doc.createElement("initial");
                        init.setAttribute("name", from);
                        t.appendChild(init);
                    }
                    if (!hasTarget && !to.isEmpty()) {
                        Element targ = doc.createElement("target");
                        targ.setAttribute("name", to);
                        t.appendChild(targ);
                    }
                }
            }

            // Smart formatting
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < root.getChildNodes().getLength(); i++) {
                formatNode(root.getChildNodes().item(i), sb, 1);
            }

            return CORRECT_HEADER + "\n" + sb.toString() + "\n</tcoel:TCOEL>";
        } catch (Exception e) {
            System.err.println("Hierarchy + transition fix failed: " + e.getMessage());
            return content;
        }
    }

    // ====================== SMART AUTO-WRAP ======================
	/*
	 * private static void autoWrapFloatingNodes(Element root) { for (String tag :
	 * ALLOWED_TAGS) { NodeList nodes = root.getElementsByTagName(tag); if
	 * (nodes.getLength() == 0) continue;
	 * 
	 * boolean anyFloating = false; for (int i = 0; i < nodes.getLength(); i++) {
	 * Node node = nodes.item(i); if (node.getParentNode() == root) { anyFloating =
	 * true; break; } } if (!anyFloating) continue;
	 * 
	 * String containerName = tag.substring(0, 1).toUpperCase() + tag.substring(1) +
	 * "s"; NodeList existingContainers = root.getElementsByTagName(containerName);
	 * if (existingContainers.getLength() > 0) continue;
	 * 
	 * Element container = root.getOwnerDocument().createElement(containerName); for
	 * (int i = 0; i < nodes.getLength(); i++) { Node node = nodes.item(i); if
	 * (node.getParentNode() == root) {
	 * container.appendChild(root.removeChild(node)); } }
	 * root.appendChild(container); } }
	 */

    // ====================== HELPER METHODS ======================
    private static void removeEmptyTextNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.TEXT_NODE && c.getTextContent().trim().isEmpty()) {
                node.removeChild(c);
            } else if (c.hasChildNodes()) {
                removeEmptyTextNodes(c);
            }
        }
    }

    private static void formatNode(Node node, StringBuilder sb, int level) {
        if (node.getNodeType() != Node.ELEMENT_NODE) return;
        String indent = "    ".repeat(level);
        String tag = node.getNodeName();
        sb.append(indent).append("<").append(tag);

        NamedNodeMap attrs = node.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Node a = attrs.item(i);
                sb.append(" ").append(a.getNodeName()).append("=\"").append(a.getNodeValue()).append("\"");
            }
        }
        sb.append(">");

        NodeList children = node.getChildNodes();
        boolean hasElementChildren = false;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                hasElementChildren = true;
                break;
            }
        }

        if (!hasElementChildren) {
            sb.append(node.getTextContent().trim()).append("</").append(tag).append(">\n");
        } else {
            sb.append("\n");
            for (int i = 0; i < children.getLength(); i++) {
                formatNode(children.item(i), sb, level + 1);
            }
            sb.append(indent).append("</").append(tag).append(">\n");
        }
    }

    private static String removeExtraNewlines(String content) {
        content = content.replaceAll("(?m)^[ \t]*\r?\n", "");
        content = content.replaceAll("(?m)(\r?\n){2,}", "\n");
        content = content.replaceAll("(?m)\\s+$", "");
        return content.trim();
    }

    private static String balanceTags(String content) {
        Pattern tagPattern = Pattern.compile("<(/?)(\\w+)([^>]*)>");
        Matcher matcher = tagPattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        java.util.Stack<String> stack = new java.util.Stack<>();
        int lastEnd = 0;

        while (matcher.find()) {
            String slash = matcher.group(1);
            String tagName = matcher.group(2);
            String attr = matcher.group(3);
            sb.append(content, lastEnd, matcher.start());
            lastEnd = matcher.end();

            if (slash.equals("/")) {
                if (!stack.isEmpty() && stack.peek().equals(tagName)) {
                    sb.append(matcher.group());
                    stack.pop();
                }
            } else {
                if (attr.trim().endsWith("/")) {
                    sb.append("<").append(tagName).append(attr.replaceAll("/$", "")).append("></").append(tagName).append(">");
                } else {
                    sb.append(matcher.group());
                    stack.push(tagName);
                }
            }
        }

        sb.append(content.substring(lastEnd));
        while (!stack.isEmpty()) {
            sb.append("</").append(stack.pop()).append(">");
        }

        return sb.toString();
    }

    private static boolean isValidXML(String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            return true;
        } catch (Exception e) {
            System.err.println("Invalid XML: " + e.getMessage());
            return false;
        }
    }
}
