package com.function.graphql;

import java.util.Iterator;
import java.util.Map;

public final class JsonUtils {
    private JsonUtils() {
    }

    public static String toJson(Object value) {
        StringBuilder builder = new StringBuilder();
        appendJson(builder, value);
        return builder.toString();
    }

    private static void appendJson(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
            return;
        }

        if (value instanceof String text) {
            builder.append('"').append(escape(text)).append('"');
            return;
        }

        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }

        if (value instanceof Map<?, ?> map) {
            builder.append('{');
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                builder.append('"')
                    .append(escape(String.valueOf(entry.getKey())))
                    .append("\":");
                appendJson(builder, entry.getValue());
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append('}');
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            Iterator<?> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                appendJson(builder, iterator.next());
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append(']');
            return;
        }

        builder.append('"').append(escape(String.valueOf(value))).append('"');
    }

    private static String escape(String text) {
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            switch (current) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                    break;
            }
        }

        return builder.toString();
    }
}
