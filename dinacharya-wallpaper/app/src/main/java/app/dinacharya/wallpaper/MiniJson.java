package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A small JSON reader and quoter, enough for Google's replies. Uses no Android classes. */
public final class MiniJson {
    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    /** Returns a Map, List, String, Double, Boolean or null. Throws IllegalArgumentException when the text is not JSON. */
    public static Object parse(String text) {
        MiniJson p = new MiniJson(text == null ? "" : text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw new IllegalArgumentException("extra text at " + p.i);
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        try {
            Object o = parse(text);
            if (o instanceof Map) return (Map<String, Object>) o;
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return new LinkedHashMap<>();
    }

    public static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof String ? (String) v : null;
    }

    public static long num(Map<String, Object> m, String k, long dflt) {
        Object v = m.get(k);
        return v instanceof Double ? (long) (double) (Double) v : dflt;
    }

    public static String quote(String t) {
        StringBuilder sb = new StringBuilder("\"");
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
        char c = s.charAt(i);
        if (c == '{') return obj();
        if (c == '[') return arr();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw new IllegalArgumentException("unexpected character at " + i);
        return Double.valueOf(s.substring(st, i));
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws();
            expect(':');
            ws();
            m.put(k, value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            expect('}');
            return m;
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            expect(']');
            return l;
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            if (i >= s.length()) break;
            char e = s.charAt(i++);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length()) throw new IllegalArgumentException("bad escape");
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: sb.append(e);
            }
        }
        throw new IllegalArgumentException("unterminated string");
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("expected " + c + " at " + i);
        i++;
    }
}
