package org.wallentines.packserver;

import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdproxy.jwt.*;
import org.wallentines.packserver.netty.ConnectionManager;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

public class WebServer {

    private final int port;
    private final String baseUrl;
    private final KeySupplier jwtKeySupplier;
    private final JWTVerifier jwtVerifier;

    private final ConnectionManager connectionManager;
    private final PackManager packManager;
    private final TagManager tagManager;

    private final PackHandler packHandler;
    private final HasHandler hasHandler;
    private final PushHandler pushHandler;
    private final DeleteHandler deleteHandler;
    private final HashHandler hashHandler;
    private final TagHandler tagHandler;

    public WebServer(int port, String baseUrl, KeySupplier jwtKey, Path packDir, Path tagDir) {
        this.port = port;
        this.baseUrl = baseUrl;
        this.jwtKeySupplier = jwtKey;
        this.jwtVerifier = new JWTVerifier();

        this.connectionManager = new ConnectionManager(this);
        this.packManager = new PackManager(packDir);
        this.tagManager = new TagManager(tagDir);

        this.packHandler = new PackHandler(this);
        this.hasHandler = new HasHandler(this);
        this.pushHandler = new PushHandler(this);
        this.deleteHandler = new DeleteHandler(this);
        this.hashHandler = new HashHandler(this);
        this.tagHandler = new TagHandler(this);
    }

    public String generateToken() {
        return new JWTBuilder()
                .expiresAt(Instant.MAX)
                .encrypted(KeyCodec.A256KW(jwtKeySupplier.getKey(new ConfigSection(), KeyType.AES)), CryptCodec.A256CBC_HS512())
                .asString()
                .getOrThrow();
    }

    public int port() {
        return port;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public void start() throws IOException {
        connectionManager.startListener();
    }

    public void shutdown() {
        connectionManager.stop();
    }

    public KeySupplier keySupplier() {
        return jwtKeySupplier;
    }

    public JWTVerifier jwtVerifier() {
        return jwtVerifier;
    }

    public PackManager packManager() {
        return packManager;
    }

    public TagManager tagManager() {
        return tagManager;
    }

    public PackHandler packHandler() {
        return packHandler;
    }

    public HasHandler hasHandler() {
        return hasHandler;
    }

    public PushHandler pushHandler() {
        return pushHandler;
    }

    public DeleteHandler deleteHandler() {
        return deleteHandler;
    }

    public HashHandler hashHandler() {
        return hashHandler;
    }

    public TagHandler tagHandler() {
        return tagHandler;
    }
}
