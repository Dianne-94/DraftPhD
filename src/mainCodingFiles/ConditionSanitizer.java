package mainCodingFiles;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionSanitizer {

    private static final Pattern RANGE_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[-–—]\\s*(\\d+(?:\\.\\d+)?)");

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)");

    public static String sanitize(String xmiContent) {
        if (xmiContent == null || xmiContent.isBlank()) return xmiContent;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmiContent)));

            sanitizeNodes(doc.getElementsByTagName("condition"));
            sanitizeNodes(doc.getElementsByTagName("preCondition"));

            // Convert DOM → string
            StringWriter sw = new StringWriter();
            javax.xml.transform.Transformer tx =
                    javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            tx.transform(new javax.xml.transform.dom.DOMSource(doc),
                    new javax.xml.transform.stream.StreamResult(sw));

            return sw.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return xmiContent; // fallback safe
        }
    }

    private static void sanitizeNodes(NodeList nodes) {
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element)) continue;

            Element cond = (Element) nodes.item(i);

            String desc = cond.getAttribute("description");
            String value = cond.getAttribute("value");

            if (desc == null) desc = "";
            desc = desc.replace("–", "-").replace("—", "-");

            cond.setAttribute("description", desc);

            // CASE A: Already has a numeric value
            if (value != null && !value.isBlank()) {
                cond.setAttribute("type", "numeric");
                continue;
            }

            // CASE B: Detect range (x-y)
            Matcher r = RANGE_PATTERN.matcher(desc);
            if (r.find()) {
                cond.setAttribute("min", r.group(1));
                cond.setAttribute("max", r.group(2));
                cond.setAttribute("type", "range");
                continue;
            }

            // CASE C: Detect single number
            Matcher n = NUMBER_PATTERN.matcher(desc);
            if (n.find()) {
                cond.setAttribute("value", n.group(1));
                cond.setAttribute("type", "numeric");
                continue;
            }

            // CASE D: Qualitative only
            cond.setAttribute("type", "qualitative");
        }
    }
}