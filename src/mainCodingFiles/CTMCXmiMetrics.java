package mainCodingFiles;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public final class CTMCXmiMetrics {
    private CTMCXmiMetrics() {}

    public static final class Config {
        public Set<String> stateTags      = set("State");
        public Set<String> transitionTags = set("Transition");
        public Set<String> agentTags      = set("Agent");   // optional
        public Set<String> traitTags      = set("Trait");   // optional
        // If your XMI uses different names, e.g., "CTMCState", "CTMCTransition", set them here.

        private static Set<String> set(String... s) {
            Set<String> z = new HashSet<>();
            for (String x : s) z.add(x);
            return z;
        }
    }

    public static final class Result {
        public final long states, transitions;
        public final int agents, traits;
        public Result(long states, long transitions, int agents, int traits) {
            this.states = states; this.transitions = transitions; this.agents = agents; this.traits = traits;
        }
    }

    public static Result analyze(File xmiFile) throws Exception {
        return analyze(xmiFile, new Config());
    }

    public static Result analyze(File xmiFile, Config cfg) throws Exception {
        if (xmiFile == null || !xmiFile.isFile())
            throw new IllegalArgumentException("Invalid CTMC XMI: " + xmiFile);

        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        SAXParser sax = spf.newSAXParser();

        CounterHandler h = new CounterHandler(cfg);
        sax.parse(xmiFile, h);

        return new Result(h.stateCount, h.transitionCount, h.agentCount, h.traitCount);
    }

    private static final class CounterHandler extends DefaultHandler {
        final Config cfg;
        long stateCount = 0, transitionCount = 0;
        int agentCount = 0, traitCount = 0;

        CounterHandler(Config cfg) { this.cfg = cfg; }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            final String name = localName != null && !localName.isEmpty() ? localName : qName;
            if (cfg.stateTags.contains(name))      stateCount++;
            else if (cfg.transitionTags.contains(name)) transitionCount++;
            else if (cfg.agentTags.contains(name)) agentCount++;
            else if (cfg.traitTags.contains(name)) traitCount++;
        }
    }
}