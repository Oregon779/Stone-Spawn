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
import java.util.Map;
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

    /**
     * Replaces the key {@code oldKey} below {@code parentPath} (dot separated, "" = top level) with {@code newKey},
     * leaving every other character of the file as it is. Returns null if the key isn't there.
     */
    static String renameKey(String text, String parentPath, String oldKey, String newKey) {
        try {
            NodeTuple tuple = find(section(rootMapping(text), parentPath).mapping(), oldKey);
            if (tuple == null) {
                return null;
            }
            List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
            replace(lines, tuple.getKeyNode().getStartMark(), tuple.getKeyNode().getEndMark(), newKey);
            return String.join("\n", lines);
        } catch (Unpatchable | IndexOutOfBoundsException ex) {
            return null;
        }
    }

    /**
     * Sets plain values (numbers, booleans, texts) in the section {@code sectionPath}, e.g. "points.welcome". A missing
     * section is added at the end of its parent, an empty parent like "points: {}" is opened up for it. Existing
     * values are replaced in place. Returns null if the layout doesn't allow a line-by-line change.
     */
    static String setValues(String text, String sectionPath, Map<String, Object> values) {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
        int dot = sectionPath.lastIndexOf('.');
        String parentPath = dot < 0 ? "" : sectionPath.substring(0, dot);
        String name = sectionPath.substring(dot + 1);
        try {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Section parent = section(rootMapping(String.join("\n", lines)), parentPath);
                NodeTuple target = find(parent.mapping(), name);
                if (target == null) {
                    insertSection(lines, parent, name, values);
                    break;
                }
                if (!(target.getValueNode() instanceof MappingNode mapping)
                        || mapping.getFlowStyle() == DumperOptions.FlowStyle.FLOW || mapping.getValue().isEmpty()) {
                    throw new Unpatchable();
                }
                NodeTuple option = find(mapping, entry.getKey());
                String rendered = scalar(entry.getValue());
                if (option == null) {
                    int indent = mapping.getValue().getFirst().getKeyNode().getStartMark().getColumn();
                    lines.add(contentEnd(lines, mapping.getValue().getLast()) + 1,
                            " ".repeat(indent) + entry.getKey() + ": " + rendered);
                } else if (option.getValueNode() instanceof ScalarNode value && !value.getValue().isEmpty()) {
                    replace(lines, value.getStartMark(), value.getEndMark(), rendered);
                } else {
                    throw new Unpatchable();
                }
            }
            return String.join(newline, lines);
        } catch (Unpatchable | IndexOutOfBoundsException ex) {
            return null;
        }
    }

    /**
     * Removes the option or section at {@code path} (its own comments above it stay). A section left without entries
     * is written as "{}", so it stays an empty section. Returns null if that isn't possible line by line.
     */
    static String removeKey(String text, String path) {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
        int dot = path.lastIndexOf('.');
        try {
            Section parent = section(rootMapping(text), dot < 0 ? "" : path.substring(0, dot));
            NodeTuple tuple = find(parent.mapping(), path.substring(dot + 1));
            if (tuple == null || parent.mapping().getFlowStyle() == DumperOptions.FlowStyle.FLOW) {
                return null;
            }
            int from = keyLine(tuple);
            int to = contentEnd(lines, tuple);
            if (parent.mapping().getValue().size() == 1) {
                if (parent.tuple() == null) {
                    return null;
                }
                Mark keyEnd = parent.tuple().getKeyNode().getEndMark();
                String keyLine = lines.get(keyEnd.getLine());
                int colon = keyLine.indexOf(':', keyEnd.getColumn());
                if (colon < 0) {
                    return null;
                }
                lines.set(keyEnd.getLine(), keyLine.substring(0, colon + 1) + " {}" + keyLine.substring(colon + 1));
            }
            lines.subList(from, to + 1).clear();
            return String.join(newline, lines);
        } catch (Unpatchable | IndexOutOfBoundsException ex) {
            return null;
        }
    }

    /** A mapping in the file together with the key that holds it ({@code tuple} is null for the top level). */
    private record Section(NodeTuple tuple, MappingNode mapping) {
    }

    private static Section section(MappingNode root, String path) throws Unpatchable {
        Section current = new Section(null, root);
        if (path.isEmpty()) {
            return current;
        }
        for (String part : path.split("\\.")) {
            NodeTuple tuple = find(current.mapping(), part);
            if (tuple == null || !(tuple.getValueNode() instanceof MappingNode child)) {
                throw new Unpatchable();
            }
            current = new Section(tuple, child);
        }
        return current;
    }

    private static void insertSection(List<String> lines, Section parent, String name, Map<String, Object> values)
            throws Unpatchable {
        MappingNode mapping = parent.mapping();
        int indent;
        int at;
        int step = 2;
        if (mapping.getFlowStyle() == DumperOptions.FlowStyle.FLOW) {
            if (!mapping.getValue().isEmpty() || parent.tuple() == null
                    || mapping.getStartMark().getLine() != mapping.getEndMark().getLine()) {
                throw new Unpatchable();
            }
            // "points: {}" becomes "points:" with the new entry below it.
            int line = mapping.getStartMark().getLine();
            String text = lines.get(line);
            lines.set(line, text.substring(0, mapping.getStartMark().getColumn()).stripTrailing()
                    + text.substring(mapping.getEndMark().getColumn()));
            indent = parent.tuple().getKeyNode().getStartMark().getColumn() + 2;
            at = line + 1;
        } else {
            if (mapping.getValue().isEmpty()) {
                throw new Unpatchable();
            }
            NodeTuple sibling = mapping.getValue().getFirst();
            indent = sibling.getKeyNode().getStartMark().getColumn();
            at = contentEnd(lines, mapping.getValue().getLast()) + 1;
            // Indent the values like the entries next to it do.
            if (sibling.getValueNode() instanceof MappingNode siblingValues && !siblingValues.getValue().isEmpty()
                    && siblingValues.getFlowStyle() != DumperOptions.FlowStyle.FLOW) {
                step = siblingValues.getValue().getFirst().getKeyNode().getStartMark().getColumn() - indent;
            }
        }
        int valueIndent = indent + step;
        List<String> block = new ArrayList<>();
        block.add(" ".repeat(indent) + name + ":");
        values.forEach((key, value) -> block.add(" ".repeat(valueIndent) + key + ": " + scalar(value)));
        lines.addAll(at, block);
    }

    private static void replace(List<String> lines, Mark start, Mark end, String replacement) throws Unpatchable {
        if (start.getLine() != end.getLine()) {
            throw new Unpatchable();
        }
        String line = lines.get(start.getLine());
        lines.set(start.getLine(), line.substring(0, start.getColumn()) + replacement + line.substring(end.getColumn()));
    }

    private static String scalar(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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
