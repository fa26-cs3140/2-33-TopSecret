package controller;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class WebFormData {
    Map<String, String> read(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("Expected a form");
        }
        byte[] body = exchange.getRequestBody().readNBytes(8193);
        if (body.length > 8192) {
            return null;
        }
        try {
            String encoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body)).toString();
            return parameters(encoded);
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8", exception);
        }
    }

    static Map<String, String> parameters(String encoded) {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }
        for (String pair : encoded.split("&", -1)) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid parameter");
            }
            String key = decode(parts[0]);
            String value = decode(parts[1]);
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate parameter");
            }
        }
        return values;
    }

    private static String decode(String value) {
        String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
        if (decoded.contains("\uFFFD")) {
            throw new IllegalArgumentException("Invalid encoded UTF-8");
        }
        return decoded;
    }
}
