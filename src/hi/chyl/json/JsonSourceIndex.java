package hi.chyl.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;

/** Connects tree nodes to structural paths and current editor character offsets. */
final class JsonSourceIndex {
    record Range(int start, int end) {}

    private final Map<DefaultMutableTreeNode, List<Object>> paths = new IdentityHashMap<>();
    private String indexedText;
    private Map<List<Object>, Range> ranges = Map.of();

    JsonSourceIndex(JsonElement json, DefaultMutableTreeNode root) {
        paths.put(root, List.of());
        if (json.isJsonArray() || json.isJsonPrimitive() || json.isJsonNull()) {
            bind(json, (DefaultMutableTreeNode) root.getChildAt(0), List.of());
        } else {
            bind(json, root, List.of());
        }
    }

    private static List<Object> childPath(List<Object> parent, Object key) {
        var path = new ArrayList<Object>(parent);
        path.add(key);
        return List.copyOf(path);
    }

    private void bind(JsonElement json, DefaultMutableTreeNode node, List<Object> path) {
        paths.put(node, path);
        int index = 0;
        if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                bind(entry.getValue(), (DefaultMutableTreeNode) node.getChildAt(index++), childPath(path, entry.getKey()));
            }
        } else if (json.isJsonArray()) {
            for (JsonElement value : json.getAsJsonArray()) {
                bind(value, (DefaultMutableTreeNode) node.getChildAt(index), childPath(path, index++));
            }
        }
    }

    Range locate(DefaultMutableTreeNode node, String text) {
        if (!text.equals(indexedText)) {
            indexedText = text;
            try {
                Scanner scanner = new Scanner(text);
                scanner.value(JsonParser.parseString(text), List.of());
                scanner.whitespace();
                if (scanner.offset != text.length()) throw new IllegalArgumentException("Trailing JSON text");
                ranges = scanner.ranges;
            } catch (RuntimeException invalidOrIncompleteJson) {
                // The editor may be mid-edit. Never jump using stale offsets.
                ranges = Map.of();
            }
        }
        List<Object> path = paths.get(node);
        return path == null ? null : ranges.get(path);
    }

    private static final class Scanner {
        private final String text;
        private final Map<List<Object>, Range> ranges = new HashMap<>();
        private int offset;

        Scanner(String text) { this.text = text; }

        void whitespace() {
            while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) offset++;
        }

        void expect(char expected) {
            whitespace();
            if (offset >= text.length() || text.charAt(offset++) != expected) throw new IllegalArgumentException("JSON syntax");
        }

        Range string() {
            whitespace();
            int start = offset;
            expect('"');
            while (offset < text.length()) {
                char current = text.charAt(offset++);
                if (current == '\\') {
                    offset++; // An escaped quote does not terminate the string.
                } else if (current == '"') {
                    return new Range(start, offset);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        void value(JsonElement json, List<Object> path) {
            whitespace();
            int start = offset;
            if (json.isJsonObject()) {
                expect('{');
                ranges.put(path, new Range(start, start + 1));
                int index = 0;
                for (var entry : json.getAsJsonObject().entrySet()) {
                    if (index++ > 0) expect(',');
                    Range key = string();
                    String actualKey = JsonParser.parseString(text.substring(key.start(), key.end())).getAsString();
                    if (!actualKey.equals(entry.getKey())) throw new IllegalArgumentException("JSON member mismatch");
                    expect(':');
                    List<Object> child = childPath(path, entry.getKey());
                    value(entry.getValue(), child);
                    ranges.put(child, key);
                }
                expect('}');
            } else if (json.isJsonArray()) {
                expect('[');
                ranges.put(path, new Range(start, start + 1));
                int index = 0;
                for (JsonElement element : json.getAsJsonArray()) {
                    if (index > 0) expect(',');
                    value(element, childPath(path, index++));
                }
                expect(']');
            } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                ranges.put(path, string());
            } else {
                while (offset < text.length() && !Character.isWhitespace(text.charAt(offset))
                        && ",]}".indexOf(text.charAt(offset)) < 0) offset++;
                if (offset == start) throw new IllegalArgumentException("Missing JSON value");
                ranges.put(path, new Range(start, offset));
            }
        }
    }
}
