package org.wallentines.packserver;

import org.jetbrains.annotations.Nullable;
import org.wallentines.mdcfg.serializer.InlineSerializer;
import org.wallentines.mdcfg.serializer.ObjectSerializer;
import org.wallentines.mdcfg.serializer.Serializer;
import org.wallentines.mdproxy.ResourcePack;
import org.wallentines.pseudonym.text.Component;
import org.wallentines.pseudonym.text.ConfigTextParser;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


public record PackEntry(UUID uuid, @Nullable String server, @Nullable String tag, @Nullable String hash, @Nullable Component prompt, boolean required, @Nullable Component kickMessage) {

    public PackEntry(String tag) {
        this(UUID.nameUUIDFromBytes(tag.getBytes()), null, tag, null, null, false, null);
    }

    private String getPackHash(HttpClient client, URI baseUrl) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(baseUrl.resolve("hash?tag=" + tag))
                .build();

        HttpResponse<byte[]> res = client.send(request, responseInfo -> HttpResponse.BodySubscribers.ofByteArray());
        return new String(res.body());
    }

    public CompletableFuture<ResourcePack> toPack(PackServerPlugin plugin, Executor executor) {

        URI baseUrl = plugin.getServer(server);
        if(baseUrl == null) {
            return CompletableFuture.completedFuture(null);
        }

        if(hash != null) {
            try {
                return CompletableFuture.completedFuture(new ResourcePack(uuid, baseUrl.resolve("pack?hash=" + hash).toURL().toString(), hash, required, prompt));
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            String packHash;
            try(HttpClient client = HttpClient.newBuilder().executor(executor).build()) {
                packHash = getPackHash(client, baseUrl);
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            try {
                return new ResourcePack(uuid, baseUrl.resolve("pack?hash=" + packHash).toURL().toString(), packHash, required, prompt);
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }

        }, executor);

    }

    private static final Serializer<Component> COMPONENT_SERIALIZER = InlineSerializer.of(ConfigTextParser.INSTANCE::serialize, ConfigTextParser.INSTANCE::parse);

    public static final Serializer<PackEntry> SERIALIZER = ObjectSerializer.create(
            Serializer.UUID.entry("uuid", PackEntry::uuid),
            Serializer.STRING.entry("server", PackEntry::server).optional(),
            Serializer.STRING.entry("tag", PackEntry::server).optional(),
            Serializer.STRING.entry("hash", PackEntry::server).optional(),
            COMPONENT_SERIALIZER.entry("prompt", PackEntry::prompt).optional(),
            Serializer.BOOLEAN.entry("required", PackEntry::required).orElse(false),
            COMPONENT_SERIALIZER.entry("kick_message", PackEntry::kickMessage).optional(),
            PackEntry::new
    ).or(Serializer.STRING.flatMap(PackEntry::tag, PackEntry::new));



}
