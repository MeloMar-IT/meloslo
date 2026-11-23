package com.example.meloslo.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.net.URI;

/** Writes alert URLs as {@code scheme://host[:port]/****} so webhook secrets never leave the server. */
public class MaskedUrlSerializer extends StdSerializer<String> {

    private static final String MASK = "****";

    public MaskedUrlSerializer() {
        super(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(mask(value));
    }

    /** Null stays null; a URL without a host or one that cannot be parsed becomes {@code ****}. */
    public static String mask(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) return MASK;
            String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
            return uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + uri.getHost() + port + "/" + MASK;
        } catch (Exception e) {
            return MASK;
        }
    }

    public static boolean isMasked(String url) {
        return url != null && (url.equals(MASK) || url.endsWith("/" + MASK));
    }
}
