package aero.modellib.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON parser used by model importers. */
final class Aero_JsonValueParser {
    private final String source;
    private int position;

    private Aero_JsonValueParser(String source) {
        this.source = source;
    }

    static Object parse(String source) {
        if (source == null) throw new IllegalArgumentException("JSON must not be null");
        Aero_JsonValueParser parser = new Aero_JsonValueParser(source);
        Object value = parser.value();
        parser.whitespace();
        if (parser.position != source.length()) parser.fail("Unexpected trailing content");
        return value;
    }

    private Object value() {
        whitespace();
        if (position >= source.length()) fail("Unexpected end of JSON");
        char c = source.charAt(position);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't') return literal("true", Boolean.TRUE);
        if (c == 'f') return literal("false", Boolean.FALSE);
        if (c == 'n') return literal("null", null);
        return number();
    }

    private Map object() {
        Map result = new LinkedHashMap();
        position++;
        whitespace();
        if (take('}')) return result;
        while (true) {
            whitespace();
            if (position >= source.length() || source.charAt(position) != '"') {
                fail("Expected object key");
            }
            String key = string();
            whitespace();
            expect(':');
            result.put(key, value());
            whitespace();
            if (take('}')) return result;
            expect(',');
        }
    }

    private List array() {
        List result = new ArrayList();
        position++;
        whitespace();
        if (take(']')) return result;
        while (true) {
            result.add(value());
            whitespace();
            if (take(']')) return result;
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (position < source.length()) {
            char c = source.charAt(position++);
            if (c == '"') return result.toString();
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (position >= source.length()) fail("Unterminated escape");
            char escaped = source.charAt(position++);
            if (escaped == '"' || escaped == '\\' || escaped == '/') result.append(escaped);
            else if (escaped == 'b') result.append('\b');
            else if (escaped == 'f') result.append('\f');
            else if (escaped == 'n') result.append('\n');
            else if (escaped == 'r') result.append('\r');
            else if (escaped == 't') result.append('\t');
            else if (escaped == 'u') result.append(unicode());
            else fail("Unsupported escape");
        }
        fail("Unterminated string");
        return null;
    }

    private char unicode() {
        if (position + 4 > source.length()) fail("Incomplete unicode escape");
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(source.charAt(position++), 16);
            if (digit < 0) fail("Invalid unicode escape");
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private Object literal(String text, Object result) {
        if (!source.regionMatches(position, text, 0, text.length())) fail("Invalid literal");
        position += text.length();
        return result;
    }

    private Number number() {
        int start = position;
        if (take('-')) { /* sign */ }
        digits();
        if (take('.')) digits();
        if (take('e') || take('E')) {
            if (take('+') || take('-')) { /* exponent sign */ }
            digits();
        }
        try {
            return Double.valueOf(source.substring(start, position));
        } catch (NumberFormatException e) {
            fail("Invalid number");
            return null;
        }
    }

    private void digits() {
        int start = position;
        while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
        if (position == start) fail("Expected digit");
    }

    private void whitespace() {
        while (position < source.length() && source.charAt(position) <= ' ') position++;
    }

    private boolean take(char expected) {
        if (position < source.length() && source.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!take(expected)) fail("Expected '" + expected + "'");
    }

    private void fail(String message) {
        throw new RuntimeException(message + " at position " + position);
    }
}
