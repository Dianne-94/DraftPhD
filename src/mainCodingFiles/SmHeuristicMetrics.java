package mainCodingFiles;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Heuristic metrics from a PRISM .sm file.
 * Counts simple variable ranges and "->" transitions as a proxy.
 * Prefer CTMCXmiMetrics when possible.
 */
public final class SmHeuristicMetrics {
    private SmHeuristicMetrics() {}

    private static final Pattern VAR_RANGE =
            Pattern.compile("\\b([a-z])\\s*:\\s*\\[\\s*0\\s*\\.\\.\\s*(\\d+)\\s*]\\s*init\\s*\\d+\\s*;", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSITION_LINE =
            Pattern.compile("\\b\\[.*?]\\s+[^;]*->[^;]*;|\\b([a-zA-Z0-9_]+)\\s*->\\s*[^;]*;", Pattern.CASE_INSENSITIVE);

    public static Result analyze(Path smPath) throws IOException {
        if (smPath == null || !Files.isRegularFile(smPath))
            throw new IllegalArgumentException("Invalid .sm: " + smPath);

        String text = Files.readString(smPath);

        Matcher m = VAR_RANGE.matcher(text);
        long lowerBoundStates = 0;
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            if (seen.add(m.group(1))) {
                int max = Integer.parseInt(m.group(2));
                lowerBoundStates += (max + 1);
            }
        }

        long transitions = 0;
        Matcher mt = TRANSITION_LINE.matcher(text);
        while (mt.find()) transitions++;

        return new Result(lowerBoundStates, transitions);
    }

    public static final class Result {
        public final long statesLowerBound, transitions;
        public Result(long statesLowerBound, long transitions) {
            this.statesLowerBound = statesLowerBound;
            this.transitions = transitions;
        }
    }
}