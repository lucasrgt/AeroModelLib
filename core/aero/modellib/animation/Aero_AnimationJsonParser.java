package aero.modellib.animation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal recursive-descent parser used by the dependency-free animation loader. */
final class Aero_AnimationJsonParser {
    private final String source;
    private int position;

    Aero_AnimationJsonParser(String source) { this.source = source; }

    Object parseValue() {
        skipWhitespace();
        if (position >= source.length()) fail("Unexpected end of JSON");
        char value = source.charAt(position);
        if (value == '{') return parseObject();
        if (value == '[') return parseArray();
        if (value == '"') return parseString();
        if (value == 't') { position += 4; return Boolean.TRUE; }
        if (value == 'f') { position += 5; return Boolean.FALSE; }
        if (value == 'n') { position += 4; return null; }
        return parseNumber();
    }

    private Map parseObject() {
        Map result = new HashMap();
        position++;
        skipWhitespace();
        if (peek('}')) { position++; return result; }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            result.put(key, parseValue());
            skipWhitespace();
            if (peek('}')) { position++; return result; }
            if (peek(',')) { position++; continue; }
            fail("Expected ',' or '}'");
        }
    }

    private List parseArray() {
        List result = new ArrayList();
        position++;
        skipWhitespace();
        if (peek(']')) { position++; return result; }
        while (true) {
            result.add(parseValue());
            skipWhitespace();
            if (peek(']')) { position++; return result; }
            if (peek(',')) { position++; continue; }
            fail("Expected ',' or ']'");
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (position < source.length()) {
            char value = source.charAt(position++);
            if (value == '"') return result.toString();
            if (value != '\\' || position >= source.length()) { result.append(value); continue; }
            appendEscape(source.charAt(position++), result);
        }
        fail("Unterminated string");
        return null;
    }

    private static void appendEscape(char escape, StringBuilder output) {
        switch (escape) {
            case '"': output.append('"'); break;
            case '\\': output.append('\\'); break;
            case '/': output.append('/'); break;
            case 'n': output.append('\n'); break;
            case 'r': output.append('\r'); break;
            case 't': output.append('\t'); break;
            default: output.append(escape);
        }
    }

    private Float parseNumber() {
        int start = position;
        if (peek('-')) position++;
        while (position < source.length()) {
            char value = source.charAt(position);
            if (Character.isDigit(value) || value == '.') { position++; continue; }
            if (value == 'e' || value == 'E') {
                position++;
                if (peek('+') || peek('-')) position++;
                continue;
            }
            break;
        }
        return Float.valueOf(Float.parseFloat(source.substring(start, position)));
    }

    private boolean peek(char expected) {
        return position < source.length() && source.charAt(position) == expected;
    }

    private void skipWhitespace() {
        while (position < source.length() && source.charAt(position) <= ' ') position++;
    }

    private void expect(char expected) {
        if (!peek(expected)) fail("Expected '" + expected + "'");
        position++;
    }

    private void fail(String message) { throw new RuntimeException(message + " at pos " + position); }
}
