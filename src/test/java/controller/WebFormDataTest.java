package controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebFormDataTest {
    @Test
    void decoderPreservesValuesAndRejectsMalformedOrDuplicateParameters() {
        assertEquals(Map.of(), WebFormData.parameters(null));
        assertEquals(Map.of(), WebFormData.parameters(""));
        assertEquals(Map.of("name", "North & South", "notes", ""),
                WebFormData.parameters("name=North+%26+South&notes="));
        for (String encoded : new String[]{"name", "name=x&name=y", "name=%", "name=%C3%28", "name=x&"}) {
            assertThrows(IllegalArgumentException.class, () -> WebFormData.parameters(encoded));
        }
    }

    @Test
    void bodiesRequireFormEncodingAndBoundedValidUtf8() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        when(exchange.getRequestHeaders()).thenReturn(headers);
        WebFormData reader = new WebFormData();
        assertThrows(IllegalArgumentException.class, () -> reader.read(exchange));
        headers.set("Content-Type", "text/plain");
        assertThrows(IllegalArgumentException.class, () -> reader.read(exchange));
        headers.set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream("name=%E2%98%83".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Map.of("name", "☃"), reader.read(exchange));
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(new byte[8193]));
        assertNull(reader.read(exchange));
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(new byte[]{(byte) 0xC3, 0x28}));
        assertThrows(IllegalArgumentException.class, () -> reader.read(exchange));
    }
}
