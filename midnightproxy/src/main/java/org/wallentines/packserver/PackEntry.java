package org.wallentines.packserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.wallentines.mdcfg.serializer.InlineSerializer;
import org.wallentines.mdcfg.serializer.ObjectSerializer;
import org.wallentines.mdcfg.serializer.Serializer;
import org.wallentines.mdproxy.ResourcePack;
import org.wallentines.pseudonym.text.Component;
import org.wallentines.pseudonym.text.ConfigTextParser;

public record PackEntry(UUID uuid, @Nullable String server,
                        @Nullable String tag, @Nullable String hash,
                        @Nullable Component prompt, boolean required,
                        @Nullable Component kickMessage) {

    public PackEntry(String tag) {
        this(UUID.nameUUIDFromBytes(tag.getBytes()), null, tag, null, null,
             false, null);
    }

    private String getHash(URI baseUrl, String tag) {
        URI hashUri = baseUrl.resolve("hash?tag=" + tag);
        try {
            HttpURLConnection conn =
                (HttpURLConnection)hashUri.toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            byte[] bytes = conn.getInputStream().readNBytes(40);
            return new String(bytes);
        } catch (IOException ex) {
            return null;
        }
    }

    public ResourcePack toPack(PackServerPlugin plugin) {

        URI baseUrl = plugin.getServer(server);
        if (baseUrl == null) {
            return null;
        }
        if (tag == null && hash == null) {
            return null;
        }

        String realHash = hash;
        if (hash == null) {
            realHash = getHash(baseUrl, tag);
        }

        URI finalUri = baseUrl.resolve(
            "pack?" + hash == null ? ("tag=" + tag) : ("hash=" + hash));

        try {
            return new ResourcePack(uuid, finalUri.toURL().toString(), realHash,
                                    required, prompt);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final Serializer<Component> COMPONENT_SERIALIZER =
        InlineSerializer.of(ConfigTextParser.INSTANCE::serialize,
                            ConfigTextParser.INSTANCE::parse);

    public static final Serializer<PackEntry> SERIALIZER =
        ObjectSerializer
            .create(
                Serializer.UUID.entry("uuid", PackEntry::uuid),
                Serializer.STRING.entry("server", PackEntry::server).optional(),
                Serializer.STRING.entry("tag", PackEntry::tag).optional(),
                Serializer.STRING.entry("hash", PackEntry::hash).optional(),
                COMPONENT_SERIALIZER.entry("prompt", PackEntry::prompt)
                    .optional(),
                Serializer.BOOLEAN.entry("required", PackEntry::required)
                    .orElse(false),
                COMPONENT_SERIALIZER
                    .entry("kick_message", PackEntry::kickMessage)
                    .optional(),
                PackEntry::new)
            .or(Serializer.STRING.flatMap(PackEntry::tag, PackEntry::new));
}
