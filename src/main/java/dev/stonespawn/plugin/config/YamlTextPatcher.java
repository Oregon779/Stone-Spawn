package dev.stonespawn.plugin.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Adds missing options to a YAML file by copying their text from the bundled file - comments, quotes and
 * blank lines included - right after the option that comes before them in the bundled file. Every other
 * line of the file stays exactly as the server owner wrote it, which a load-and-save through Bukkit would
 * not do (it re-quotes strings, rewraps long lines and rewrites lists).
 *
 * <p>This only decides where the lines go. {@link ConfigUpdater} checks the result against a regular merge
 * and uses that instead if anything differs, so unusual layouts can never change a value.
 */
final class YamlTextPatcher {

    private YamlTextPatcher() {
    }

    /** Returns the file with every missing option inserted, or null if its layout can't be patched line by line. */
    static String insertMissing(String currentText, String defaultsText, Set<String> userSections) {
        if (currentText.isBlank()) {
            return defaultsText;
        }
        String newline = currentText.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(currentText.split("\r?\n", -1)));
        List<String> defaultLines = Arrays.asList(defaultsText.split("\r?\n", -1));

        try {
            MappingNode defaults = rootMapping(defaultsText);
            // Every pass inserts one option; more passes than options means an insert didn't take effect.
            for (int pass = 0, limit = countOptions(defaults) + 1; pass < limit; pass++) {
                Insertion next = firstMissing(defaults, rootMapping(String.join("\n", lines)), "", userSections);
                if (next == null) {
                    return String.join(newline, lines);
                }
                lines.addAll(next.atLine(lines), next.lines(defaultLines));
            }
        } catch (Unpatchable ex) {
            return null;
        }
        return null;
    }

    /** Finds the first option of the bundled file (parents before children) that the current file lacks. */
    private static Insertion firstMissing(MappingNode defaults, MappingNode current, String path,
                                          Set<String> userSections) throws Unpatchable {
        NodeTuple previousPresent = null;
        for (NodeTuple defaultTuple : defaults.getValue()) {
            String key = keyOf(defaultTuple);
            String childPath = path.isEmpty() ? key : path + "." + key;
            NodeTuple currentTuple = find(current, key);

            if (currentTuple == null) {
                if (current.getFlowStyle() == DumperOptions.FlowStyle.FLOW || current.getValue().isEmpty()) {
                    throw new Unpatchable();
                }
                return new Insertion(defaultTuple, current, previousPresent);
            }
            if (defaultTuple.getValueNode() instanceof MappingNode defaultChild
                    && currentTuple.getValueNode() instanceof MappingNode currentChild
                    && !userSections.contains(childPath)) {
                Insertion nested = firstMissing(defaultChild, currentChild, childPath, userSections);
                if (nested != null) {
                    return nested;
                }
            }
            previousPresent = currentTuple;
        }
        return null;
    }

    /**
     * A bundled option to insert into {@code parent}: after {@code previous} (its predecessor in the bundled
     * file), or in front of the parent's first option if none of its predecessors exist in the current file.
     */
    private record Insertion(NodeTuple option, MappingNode parent, NodeTuple previous) {

        int atLine(List<String> lines) {
            if (previous != null) {
                return contentEnd(lines, previous) + 1;
            }
            return blockStart(lines, keyLine(parent.getValue().getFirst()));
        }

        /** The option's bundled lines, re-indented to the current file and preceded by a blank line if it had one. */
        List<String> lines(List<String> defaultLines) {
            int keyLine = keyLine(option);
            int start = blockStart(defaultLines, keyLine);
            int end = contentEnd(defaultLines, option);
            NodeTuple sibling = previous != null ? previous : parent.getValue().getFirst();
            int shift = sibling.getKeyNode().getStartMark().getColumn() - option.getKeyNode().getStartMark().getColumn();

            List<String> result = new ArrayList<>();
            if (start > 0 && defaultLines.get(start - 1).isBlank()) {
                result.add("");
            }
            for (String line : defaultLines.subList(start, end + 1)) {
                result.add(indent(line, shift));
            }
            return result;
        }
    }

    private static MappingNode rootMapping(String text) throws Unpatchable {
        try {
            Node root = new Yaml(new SafeConstructor(new LoaderOptions())).compose(new StringReader(text));
            if (root instanceof MappingNode mapping) {
                return mapping;
            }
        } catch (YAMLException ex) {
            // Handled like any other layout that can't be patched.
        }
        throw new Unpatchable();
    }

    private static String keyOf(NodeTuple tuple) throws Unpatchable {
        if (tuple.getKeyNode() instanceof ScalarNode scalar) {
            return scalar.getValue();
        }
        throw new Unpatchable();
    }

    private static NodeTuple find(MappingNode mapping, String key) throws Unpatchable {
        for (NodeTuple tuple : mapping.getValue()) {
            if (keyOf(tuple).equals(key)) {
                return tuple;
            }
        }
        return null;
    }

    private static int countOptions(MappingNode mapping) {
        int count = 0;
        for (NodeTuple tuple : mapping.getValue()) {
            count++;
            if (tuple.getValueNode() instanceof MappingNode child) {
                count += countOptions(child);
            }
        }
        return count;
    }

    private static int keyLine(NodeTuple tuple) {
        return tuple.getKeyNode().getStartMark().getLine();
    }

    /** First line of the comment block sitting directly on top of the key in {@code keyLine}. */
    private static int blockStart(List<String> lines, int keyLine) {
        int start = keyLine;
        while (start > 0 && isComment(lines.get(start - 1))) {
            start--;
        }
        return start;
    }

    /** Last line that holds the option's value. Trailing blank lines and comments belong to what follows. */
    private static int contentEnd(List<String> lines, NodeTuple tuple) {
        int keyLine = keyLine(tuple);
        Mark end = tuple.getValueNode().getEndMark();
        int line = Math.min(end.getLine(), lines.size() - 1);
        if (line == end.getLine()) {
            String text = lines.get(line);
            // A collection ends where the next token starts; if nothing of it is on that line, it ended before.
            if (text.substring(0, Math.min(end.getColumn(), text.length())).isBlank()) {
                line--;
            }
        }
        while (line > keyLine && (lines.get(line).isBlank() || isComment(lines.get(line)))) {
            line--;
        }
        return Math.max(line, keyLine);
    }

    private static boolean isComment(String line) {
        return line.stripLeading().startsWith("#");
    }

    private static String indent(String line, int shift) {
        if (line.isBlank()) {
            return "";
        }
        if (shift >= 0) {
            return " ".repeat(shift) + line;
        }
        int leading = line.length() - line.stripLeading().length();
        return line.substring(Math.min(-shift, leading));
    }

    private static final class Unpatchable extends Exception {
        Unpatchable() {
            super(null, null, false, false);
        }
    }
}
