package mainCodingFiles;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/**
 * XMIValidator: Validates, auto-repairs, and reformats XMI files
 * for TCOEL → CTMC transformation.
 */
public class XMIValidator {

    // Correct TCOEL header block (preserved exactly)
    private static final String CORRECT_HEADER =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<tcoel:TCOEL xmi:version=\"2.0\"\n" +
        "    xmlns:xmi=\"http://www.omg.org/XMI\"\n" +
        "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "    xmlns:tcoel=\"http://example/tcoel\"\n" +
        "    xsi:schemaLocation=\"/convertTcoel2Ctmc/tcoel.ecore tcoel.ecore\">";

    // =========================================================
    //  MAIN PIPELINE
    // =========================================================
    public static String validateAndFix(File inputFile) throws IOException {
        String content = Files.readString(inputFile.toPath(), StandardCharsets.UTF_8);

        // Step 1: Fix header
        content = fixHeader(content);

        // Step 2: Validate XML
        boolean valid = isValidXML(content);
        if (!valid) {
            System.out.println("XML not well-formed, attempting auto-repair...");
            content = autoFixXML(content);
        }

        // Step 3: Fix hierarchy and transitions
        content = ensureHierarchyAndTransitions(content);

        // Step 4: Remove extra blank lines
        content = removeExtraNewlines(content);

        // Step 5: Re-fix header just in case
        content = fixHeader(content);

        // Step 6: Write back
        Files.writeString(inputFile.toPath(), content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("XMI validation and repair complete for: " + inputFile.getName()+"_validated");
        return content;
    }

    // =========================================================
    //  FIX HEADER
    // =========================================================
    private static String fixHeader(String content) {
        Pattern headerPattern = Pattern.compile("(?s)^.*?(?=<\\s*(ensemble|component|environment)\\b)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = headerPattern.matcher(content);

        String body = content;
        String userHeader = "";

        if (matcher.find()) {
            userHeader = matcher.group(0).trim();
            body = content.substring(matcher.end()).trim();
            if (!normalize(userHeader).equals(normalize(CORRECT_HEADER))) {
                content = CORRECT_HEADER + "\n" + body;
            }
        } else {
            content = CORRECT_HEADER + "\n" + content;
        }

        if (!content.trim().endsWith("</tcoel:TCOEL>")) {
            content += "\n</tcoel:TCOEL>";
        }

        return content;
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    // =========================================================
    //  AUTO FIX BASIC TAGS
    // =========================================================
    private static String autoFixXML(String content) {

        content = content.replaceAll("(?is)^.*?(?=<tcoel:TCOEL)", "");
        content = content.replaceAll("(?is)(</tcoel:TCOEL>).*", "$1");

        if (!content.trim().toLowerCase().startsWith("<?xml")) {
            content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + content;
        }

        // 🔥 Normalize tags according to your declared canonical names
        content = normalizeDeclaredTags(content);

        content = ensureClosedTag(content, "ensemble");
        content = ensureClosedTag(content, "component");
        content = ensureClosedTag(content, "role");
        content = ensureClosedTag(content, "transition");
        content = ensureClosedTag(content, "map");
        content = ensureClosedTag(content, "condition");
        content = ensureClosedTag(content, "xGrid");
        content = ensureClosedTag(content, "yGrid");

        if (!content.trim().endsWith("</tcoel:TCOEL>"))
            content += "</tcoel:TCOEL>";

        return content.trim();
    }
    
    // =========================================================
    //  FIX + Normalize Only Declared Tags
    // =========================================================
    
    private static String normalizeDeclaredTags(String content) {

        String[] declaredTags = {
        	"trait",
            "ensemble",
            "component",
            "role",
            "transition",
            "map",
            "condition",
            "xGrid",
            "yGrid"
        };

        for (String tag : declaredTags) {

            // Replace opening tags (case-insensitive)
            content = content.replaceAll(
                    "(?i)<\\s*" + tag + "(\\s|>)",
                    "<" + tag + "$1"
            );

            // Replace closing tags (case-insensitive)
            content = content.replaceAll(
                    "(?i)</\\s*" + tag + "\\s*>",
                    "</" + tag + ">"
            );
        }

        return content;
    }

    // =========================================================
    //  FIX HIERARCHY + TRANSITIONS + ORDER
    // =========================================================
    private static String ensureHierarchyAndTransitions(String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringComments(true);
            factory.setIgnoringElementContentWhitespace(true);
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            removeEmptyTextNodes(root);

            boolean changed = false; // track if we actually modify anything

            // Check if <environment> exists, if not → create
            NodeList envList = root.getElementsByTagName("environment");
            if (envList.getLength() == 0) {
                NodeList mapNodes = root.getElementsByTagName("map");
                if (mapNodes.getLength() > 0) {
                    Element environment = doc.createElement("environment");
                    for (int i = 0; i < mapNodes.getLength(); i++) {
                        Node mapNode = mapNodes.item(i);
                        Node imported = doc.importNode(mapNode, true);
                        environment.appendChild(imported);
                        mapNode.getParentNode().removeChild(mapNode);
                    }
                    root.appendChild(environment);
                    changed = true;
                }
            }

            // Fix <transition> structure only if still using attributes
            NodeList transitionNodes = root.getElementsByTagName("transition");
            for (int i = 0; i < transitionNodes.getLength(); i++) {
                Element transition = (Element) transitionNodes.item(i);
                boolean hasInitialChild = transition.getElementsByTagName("initial").getLength() > 0;
                boolean hasTargetChild = transition.getElementsByTagName("target").getLength() > 0;
                boolean hasAttrInitial = transition.hasAttribute("initial");
                boolean hasAttrTarget = transition.hasAttribute("target");

                if (hasAttrInitial || hasAttrTarget) {
                    String initialAttr = transition.getAttribute("initial");
                    String targetAttr = transition.getAttribute("target");
                    transition.removeAttribute("initial");
                    transition.removeAttribute("target");

                    if (!hasInitialChild && initialAttr != null && !initialAttr.isEmpty()) {
                        Element initElem = doc.createElement("initial");
                        initElem.setAttribute("name", initialAttr);
                        transition.appendChild(initElem);
                    }
                    if (!hasTargetChild && targetAttr != null && !targetAttr.isEmpty()) {
                        Element targElem = doc.createElement("target");
                        targElem.setAttribute("name", targetAttr);
                        transition.appendChild(targElem);
                    }
                    changed = true;
                }
            }

            // 🧹 Only pretty-print if modifications were made
            if (changed) {
                TransformerFactory tfFactory = TransformerFactory.newInstance();
                Transformer tf = tfFactory.newTransformer();
                tf.setOutputProperty(OutputKeys.INDENT, "yes");
                tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                tf.setOutputProperty(OutputKeys.METHOD, "xml");

                StringWriter writer = new StringWriter();
                tf.transform(new DOMSource(doc), new StreamResult(writer));
                return writer.toString()
                        .replaceAll("(?m)^[ \t]*\r?\n", "")
                        .replaceAll("(?m)\\s+$", "")
                        .trim();
            } else {
                // nothing changed — return original untouched
                return content;
            }

        } catch (Exception e) {
            System.err.println("Hierarchy + transition fix failed: " + e.getMessage());
            return content;
        }
    }

    /**
     * Recursively format XML nodes with hierarchical indentation.
     */
    private static void formatNode(Node node, StringBuilder sb, int level) {
        if (node.getNodeType() != Node.ELEMENT_NODE) return;

        String indent = "    ".repeat(level);
        String tag = node.getNodeName();

        // Opening tag
        sb.append(indent).append("<").append(tag);

        // Attributes
        NamedNodeMap attrs = node.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Node a = attrs.item(i);
                sb.append(" ").append(a.getNodeName()).append("=\"").append(a.getNodeValue()).append("\"");
            }
        }
        sb.append(">");

        NodeList children = node.getChildNodes();
        boolean hasElements = false;

        // Detect if has element children
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                hasElements = true;
                break;
            }
        }

        if (hasElements) {
            sb.append("\n");
            for (int i = 0; i < children.getLength(); i++) {
                formatNode(children.item(i), sb, level + 1);
            }
            sb.append(indent).append("</").append(tag).append(">\n");
        } else {
            // Inline text node (like <xGrid>10</xGrid>)
            String text = "";
            if (children.getLength() > 0)
                text = children.item(0).getTextContent().trim();

            sb.append(text).append("</").append(tag).append(">\n");
        }
    }

    private static void removeChildElements(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        for (int i = list.getLength() - 1; i >= 0; i--) {
            Node child = list.item(i);
            parent.removeChild(child);
        }
    }

    // =========================================================
    //  HELPERS
    // =========================================================
    private static void removeEmptyTextNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
                node.removeChild(child);
            } else if (child.hasChildNodes()) {
                removeEmptyTextNodes(child);
            }
        }
    }

    private static String removeExtraNewlines(String content) {
        content = content.replaceAll("(?m)^[ \t]*\r?\n", "");
        content = content.replaceAll("(?m)(\r?\n){2,}", "");
        content = content.replaceAll("(?m)\\s+$", "");
        return content.trim();
    }

    private static String ensureClosedTag(String text, String tag) {
        int openCount = countMatches(text, "<" + tag);
        int closeCount = countMatches(text, "</" + tag + ">");
        while (openCount > closeCount) {
            text += "</" + tag + ">";
            closeCount++;
        }
        return text;
    }

    private static int countMatches(String text, String sub) {
        return text.split(Pattern.quote(sub), -1).length - 1;
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