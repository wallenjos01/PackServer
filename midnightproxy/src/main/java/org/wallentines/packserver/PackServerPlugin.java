package org.wallentines.packserver;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.mdcfg.ConfigList;
import org.wallentines.mdcfg.ConfigObject;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.codec.FileWrapper;
import org.wallentines.mdcfg.registry.Registry;
import org.wallentines.mdcfg.serializer.ConfigContext;
import org.wallentines.mdcfg.serializer.InlineSerializer;
import org.wallentines.mdcfg.serializer.Serializer;
import org.wallentines.mdproxy.ClientConnection;
import org.wallentines.mdproxy.Proxy;
import org.wallentines.mdproxy.ResourcePack;
import org.wallentines.mdproxy.packet.ServerboundHandshakePacket;
import org.wallentines.mdproxy.packet.common.ServerboundResourcePackStatusPacket;
import org.wallentines.mdproxy.packet.common.ServerboundResourcePackStatusPacket.Action;
import org.wallentines.mdproxy.plugin.Plugin;
import org.wallentines.pseudonym.text.Component;
import org.wallentines.pseudonym.text.Content;
import org.wallentines.pseudonym.text.ImmutableComponent;
import org.wallentines.pseudonym.text.Style;

public class PackServerPlugin implements Plugin {

    private static final ConfigSection DEFAULT_CONFIG =
        new ConfigSection()
            .with("servers", new ConfigSection())
            .with("packs", new ConfigSection())
            .with("routes", new ConfigSection())
            .with("global", new ConfigList());
    private static final Logger log =
        LoggerFactory.getLogger(PackServerPlugin.class);
    private static final Component DEFAULT_KICK_MESSAGE =
        new ImmutableComponent(
            new Content.Translate(
                "multiplayer.requiredTexturePrompt.disconnect"),
            Style.EMPTY, Collections.emptyList());
    private static final Serializer<URI> URI_SERIALIZER =
        Serializer.STRING.flatMap(URI::toString, URI::create);

    private FileWrapper<ConfigObject> config;

    private Map<String, URI> servers;
    private Map<UUID, Component> kickMessages;
    private Map<UUID, CachedPack> packsById;
    private Map<String, List<CachedPack>> routePacks;
    private List<CachedPack> globalPacks;

    public PackServerPlugin() {}

    @Nullable
    public URI getServer(String server) {
        if (server == null)
            server = "default";
        return servers.get(server);
    }

    private void reload() {

        config.load();
        ConfigSection root = config.getRoot().asSection();

        Registry<String, PackEntry> packs = Registry.createStringRegistry();
        PackEntry.SERIALIZER.mapOf()
            .deserialize(ConfigContext.INSTANCE, root.getSection("packs"))
            .getOrThrow()
            .forEach(packs::register);

        servers =
            URI_SERIALIZER.mapOf()
                .deserialize(ConfigContext.INSTANCE, root.getSection("servers"))
                .getOrThrow();

        kickMessages = new HashMap<>();
        for (PackEntry ent : packs.values()) {
            if (ent.required()) {
                Component kickMessage = ent.kickMessage();
                if (kickMessage == null) {
                    kickMessage = DEFAULT_KICK_MESSAGE;
                }
                kickMessages.put(ent.uuid(), kickMessage);
            }
        }

        packsById = new HashMap<>();

        Map<String, CachedPack> finalPacks = new ConcurrentHashMap<>();
        for (int i = 0; i < packs.getSize(); i++) {
            String id = packs.idAtIndex(i);
            PackEntry pack = packs.valueAtIndex(i);

            CachedPack cached = new CachedPack(pack);

            finalPacks.put(id, cached);
            packsById.put(pack.uuid(), cached);
        }

        InlineSerializer<CachedPack> keySerializer =
            InlineSerializer.of(rp -> "", finalPacks::get);

        routePacks =
            keySerializer.listOf()
                .mapToList()
                .mapOf()
                .deserialize(ConfigContext.INSTANCE, root.getSection("routes"))
                .getOrThrow();
        globalPacks =
            keySerializer.listOf()
                .mapToList()
                .deserialize(ConfigContext.INSTANCE, root.getList("global"))
                .getOrThrow();
    }

    @Override
    public void initialize(Proxy proxy) {

        Path configFolder =
            proxy.getPluginManager().configFolder().resolve("pack_server");
        try {
            Files.createDirectories(configFolder);
        } catch (IOException e) {
            throw new RuntimeException("Could not create messenger directory",
                                       e);
        }

        this.config = proxy.fileCodecRegistry().findOrCreate(
            ConfigContext.INSTANCE, "config", configFolder, DEFAULT_CONFIG);
        config.save();

        reload();
        proxy.getCommands().register("ps", (sender, args) -> {
            if (args.length == 2 && args[1].equals("reload")) {
                reload();
                sender.sendMessage("Reloaded resource packs");
            }
        });

        proxy.clientConnectEvent().register(this, client -> {
            if (client.getIntent() == ServerboundHandshakePacket.Intent.STATUS)
                return;
            client.preConnectBackendEvent().register(this, ev -> {
                if (ev.p2.wasReconnected()) {
                    return;
                }

                if (!ev.p2.authenticated()) {
                    log.info("Player {} is not authenticated!",
                             ev.p2.username());
                    return;
                }

                List<CompletableFuture<?>> futures = new ArrayList<>();
                for (CachedPack pack : globalPacks) {
                    futures.add(
                        ev.p2.sendResourcePack(pack.get())
                            .thenAccept(pck -> onComplete(client, pck)));
                }

                String id = proxy.getBackends().getId(ev.p1);
                if (id != null && routePacks.containsKey(id)) {
                    for (CachedPack pack : routePacks.get(id)) {
                        futures.add(
                            ev.p2.sendResourcePack(pack.get())
                                .thenAccept(pck -> onComplete(client, pck)));
                    }
                }

                CompletableFuture
                    .allOf(futures.toArray(CompletableFuture<?>[] ::new))
                    .join();
            });
        });
    }

    private void onComplete(ClientConnection conn,
                            ServerboundResourcePackStatusPacket packet) {
        if (packet.action() ==
            ServerboundResourcePackStatusPacket.Action.DECLINED) {
            if (kickMessages.containsKey(packet.packId())) {
                conn.disconnect(kickMessages.get(packet.packId()));
            }
        } else if (packet.action() == Action.DOWNLOAD_FAILED) {
            CachedPack pck = packsById.get(packet.packId());
            if (pck != null && !pck.failedRecently) {
                conn.sendResourcePack(pck.forceGet());
            }
        }
    }

    private class CachedPack {
        final PackEntry entry;
        long cacheTime;
        ResourcePack cachedPack;
        boolean failedRecently;

        CachedPack(PackEntry entry) { this.entry = entry; }

        ResourcePack forceGet() {
            synchronized (cachedPack) {

                ResourcePack oldPack = cachedPack;
                cachedPack = entry.toPack(PackServerPlugin.this);

                if (!Objects.equals(oldPack, cachedPack)) {
                    failedRecently = false;
                }
                cacheTime = System.currentTimeMillis();
                return cachedPack;
            }
        }

        ResourcePack get() {
            synchronized (cachedPack) {
                if (System.currentTimeMillis() - cacheTime > 86400L * 1000L) {
                    forceGet();
                }
                return cachedPack;
            }
        }
    }
}
