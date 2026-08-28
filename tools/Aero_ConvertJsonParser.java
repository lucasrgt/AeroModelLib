import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dependency-free JSON parser for Blockbench converter input. */
final class Aero_ConvertJsonParser {
    private final String source;
    private int position;

    Aero_ConvertJsonParser(String source) { this.source = source; }

    Object parseValue() {
        whitespace();
        if (position >= source.length()) fail("Unexpected end of JSON");
        char value = source.charAt(position);
        if (value == '{') return object();
        if (value == '[') return array();
        if (value == '"') return string();
        if (value == 't') { position += 4; return Boolean.TRUE; }
        if (value == 'f') { position += 5; return Boolean.FALSE; }
        if (value == 'n') { position += 4; return null; }
        return number();
    }

    private Map object() {
        Map result = new LinkedHashMap();
        position++;
        whitespace();
        if (peek('}')) { position++; return result; }
        while (true) {
            whitespace();
            String key = string();
            whitespace();
            expect(':');
            result.put(key, parseValue());
            whitespace();
            if (peek('}')) { position++; return result; }
            if (peek(',')) { position++; continue; }
            fail("Expected ',' or '}'");
        }
    }

    private List array() {
        List result = new ArrayList();
        position++;
        whitespace();
        if (peek(']')) { position++; return result; }
        while (true) {
            result.add(parseValue());
            whitespace();
            if (peek(']')) { position++; return result; }
            if (peek(',')) { position++; continue; }
            fail("Expected ',' or ']'");
        }
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (position < source.length()) {
            char value = source.charAt(position++);
            if (value == '"') return result.toString();
            if (value != '\\' || position >= source.length()) { result.append(value); continue; }
            char escaped = source.charAt(position++);
            if (escaped == '"') result.append('"');
            else if (escaped == '\\') result.append('\\');
            else if (escaped == '/') result.append('/');
            else if (escaped == 'n') result.append('\n');
            else if (escaped == 'r') result.append('\r');
            else if (escaped == 't') result.append('\t');
            else if (escaped == 'u') {
                result.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                position += 4;
            } else result.append(escaped);
        }
        fail("Unterminated string");
        return null;
    }

    private Object number() {
        int start = position;
        if (peek('-')) position++;
        boolean decimal = false;
        while (position < source.length()) {
            char value = source.charAt(position);
            if (Character.isDigit(value)) { position++; continue; }
            if (value == '.') { decimal = true; position++; continue; }
            if (value == 'e' || value == 'E') {
                decimal = true;
                position++;
                if (peek('+') || peek('-')) position++;
                continue;
            }
            break;
        }
        String text = source.substring(start, position);
        if (decimal) return Double.valueOf(Double.parseDouble(text));
        long value = Long.parseLong(text);
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
            ? Integer.valueOf((int) value) : Long.valueOf(value);
    }

    private boolean peek(char expected) {
        return position < source.length() && source.charAt(position) == expected;
    }

    private void whitespace() {
        while (position < source.length() && source.charAt(position) <= ' ') position++;
    }

    private void expect(char expected) {
        if (!peek(expected)) fail("Expected '" + expected + "'");
        position++;
    }

    private void fail(String message) { throw new RuntimeException(message + " at pos " + position); }
}
