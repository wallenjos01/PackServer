package org.wallentines.packserver;

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

    public ResourcePack toPack(PackServerPlugin plugin) {

        URI baseUrl = plugin.getServer(server);
        if (baseUrl == null) {
            return null;
        }
        if (tag == null && hash == null) {
            return null;
        }

        URI finalUri = baseUrl.resolve("pack?" + tag == null ? ("hash=" + hash)
                                                             : ("tag=" + tag));

        try {
            return new ResourcePack(uuid, finalUri.toURL().toString(), hash,
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
                Serializer.STRING.entry("tag", PackEntry::server).optional(),
                Serializer.STRING.entry("hash", PackEntry::server).optional(),
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
