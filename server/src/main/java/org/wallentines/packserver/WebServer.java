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
    private final Path packDir;

    private final ConnectionManager connectionManager;

    private final PackHandler packHandler;
    private final HasHandler hasHandler;
    private final PushHandler pushHandler;
    private final DeleteHandler deleteHandler;

    public WebServer(int port, String baseUrl, KeySupplier jwtKey, Path packDir) {
        this.port = port;
        this.baseUrl = baseUrl;
        this.jwtKeySupplier = jwtKey;
        this.packDir = packDir;

        this.connectionManager = new ConnectionManager(this);
        this.packHandler = new PackHandler(this);
        this.hasHandler = new HasHandler(this);
        this.pushHandler = new PushHandler(this);
        this.deleteHandler = new DeleteHandler(this);
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

    public Path packDir() {
        return packDir;
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
}
