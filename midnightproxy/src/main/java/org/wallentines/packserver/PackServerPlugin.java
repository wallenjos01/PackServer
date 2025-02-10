package org.wallentines.packserver;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.mcore.MidnightCoreAPI;
import org.wallentines.mcore.text.Component;
import org.wallentines.mdcfg.ConfigList;
import org.wallentines.mdcfg.ConfigObject;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.codec.FileWrapper;
import org.wallentines.mdcfg.serializer.ConfigContext;
import org.wallentines.mdcfg.serializer.InlineSerializer;
import org.wallentines.mdcfg.serializer.Serializer;
import org.wallentines.mdproxy.ClientConnection;
import org.wallentines.mdproxy.Proxy;
import org.wallentines.mdproxy.ResourcePack;
import org.wallentines.mdproxy.packet.ServerboundHandshakePacket;
import org.wallentines.mdproxy.packet.common.ServerboundResourcePackStatusPacket;
import org.wallentines.mdproxy.plugin.Plugin;
import org.wallentines.midnightlib.registry.Registry;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class PackServerPlugin implements Plugin {


    private static final ConfigSection DEFAULT_CONFIG = new ConfigSection()
            .with("servers", new ConfigSection())
            .with("packs", new ConfigSection())
            .with("routes", new ConfigSection())
            .with("global", new ConfigList());
    private static final Logger log = LoggerFactory.getLogger(PackServerPlugin.class);
    private static final Component DEFAULT_KICK_MESSAGE = Component.translate("multiplayer.requiredTexturePrompt.disconnect");
    private static final Serializer<URI> URI_SERIALIZER = Serializer.STRING.flatMap(URI::toString, URI::create);

    private final FileWrapper<ConfigObject> config;

    private Map<String, URI> servers;
    private Map<UUID, Component> kickMessages;
    private Map<String, List<ResourcePack>> routePacks;
    private List<ResourcePack> globalPacks;

    public PackServerPlugin() {

        Path configFolder = MidnightCoreAPI.GLOBAL_CONFIG_DIRECTORY.get().resolve("pack_server");
        try { Files.createDirectories(configFolder); } catch (IOException e) {
            throw new RuntimeException("Could not create messenger directory", e);
        }

        this.config = MidnightCoreAPI.FILE_CODEC_REGISTRY.findOrCreate(ConfigContext.INSTANCE, "config", configFolder, DEFAULT_CONFIG);
        config.save();

        reload();
    }

    @Nullable
    public URI getServer(String server) {
        if(server == null) server = "default";
        return servers.get(server);
    }

    private void reload() {

        config.load();
        ConfigSection root = config.getRoot().asSection();

        Registry<String, PackEntry> packs = Registry.createStringRegistry();
        PackEntry.SERIALIZER.mapOf().deserialize(ConfigContext.INSTANCE, root.getSection("packs")).getOrThrow().forEach(packs::register);

        servers = URI_SERIALIZER.mapOf().deserialize(ConfigContext.INSTANCE, root.getSection("servers")).getOrThrow();

        kickMessages = new HashMap<>();
        for(PackEntry ent : packs.values()) {
            if(ent.required()) {
                Component kickMessage = ent.kickMessage();
                if(kickMessage == null) {
                    kickMessage = DEFAULT_KICK_MESSAGE;
                }
                kickMessages.put(ent.uuid(), kickMessage);
            }
        }

        try(ExecutorService svc = Executors.newCachedThreadPool()) {

            Map<String, ResourcePack> finalPacks = new ConcurrentHashMap<>();
            CompletableFuture<?>[] futures = new CompletableFuture[packs.getSize()];
            for(int i = 0; i < futures.length; i++) {
                String id = packs.idAtIndex(i);
                PackEntry pack = packs.valueAtIndex(i);

                futures[i] = pack.toPack(this, svc).thenAccept(rp -> finalPacks.put(id, rp));
            }

            CompletableFuture.allOf(futures).join();
            InlineSerializer<ResourcePack> keySerializer = InlineSerializer.of(rp -> "", finalPacks::get);

            routePacks = keySerializer.listOf().mapToList().mapOf().deserialize(ConfigContext.INSTANCE, root.getSection("routes")).getOrThrow();
            globalPacks = keySerializer.listOf().mapToList().deserialize(ConfigContext.INSTANCE, root.getList("global")).getOrThrow();
        }
    }

    @Override
    public void initialize(Proxy proxy) {

        proxy.getCommands().register("ps", (sender, args) -> {
            if(args.length == 2 && args[1].equals("reload")) {
                reload();
                sender.sendMessage("Reloaded resource packs");
            }
        });

        proxy.clientConnectEvent().register(this, client -> {

            if(client.getIntent() == ServerboundHandshakePacket.Intent.STATUS) return;
            client.preConnectBackendEvent().register(this, ev -> {

                if(ev.p2.wasReconnected()) {
                    return;
                }

                if(!ev.p2.authenticated()) {
                    log.info("Player {} is not authenticated!", ev.p2.username());
                    return;
                }

                List<CompletableFuture<?>> futures = new ArrayList<>();
                for(ResourcePack pack : globalPacks) {
                    futures.add(ev.p2.sendResourcePack(pack).thenAccept(pck -> onComplete(client, pck)));
                }

                String id = proxy.getBackends().getId(ev.p1);
                if(id != null && routePacks.containsKey(id)) {
                    for(ResourcePack pack : routePacks.get(id)) {
                        futures.add(ev.p2.sendResourcePack(pack).thenAccept(pck -> onComplete(client, pck)));
                    }
                }

                CompletableFuture.allOf(futures.toArray(CompletableFuture<?>[]::new)).join();
            });
        });
    }

    private void onComplete(ClientConnection conn, ServerboundResourcePackStatusPacket packet) {
        if(packet.action() == ServerboundResourcePackStatusPacket.Action.DECLINED && kickMessages.containsKey(packet.packId())) {
            conn.disconnect(kickMessages.get(packet.packId()));
        }
    }

}
