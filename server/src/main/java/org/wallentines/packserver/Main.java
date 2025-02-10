package org.wallentines.packserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.mdproxy.jwt.FileKeyStore;
import org.wallentines.mdproxy.jwt.KeyType;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Map;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path packDir = cwd.resolve("packs");

        String portStr = System.getenv("PACK_SERVER_PORT");
        String baseUrlStr = System.getenv("PACK_SERVER_BASE_URL");

        int port = 8080;
        String baseUrl = "/";
        if(portStr != null) {
            port = Integer.parseInt(portStr);
        }
        if(baseUrlStr != null) {
            baseUrl = baseUrlStr;
            if(!baseUrl.startsWith("/")) {
                baseUrl = "/" + baseUrl;
            }
        }

        try { Files.createDirectories(packDir); } catch (IOException ex) {
            log.error("Could not generate pack storage directory", ex);
            return;
        }

        FileKeyStore ks = new FileKeyStore(cwd, Map.of(KeyType.AES, "key"));
        if(ks.getKey("jwt", KeyType.AES) == null) {

            SecretKey key;
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(256);
                key = keyGenerator.generateKey();
            } catch (GeneralSecurityException ex) {
                log.error("Could not generate key", ex);
                return;
            }

            ks.setKey("jwt", KeyType.AES, key);
        }

        WebServer ws = new WebServer(port, baseUrl, ks.supplier("jwt", KeyType.AES), packDir);
        try {
            ws.start();
        } catch (Throwable th) {
            log.error("Unable to start the web server!", th);
        }

        ConsoleHandler ch = new ConsoleHandler(ws);
        ch.start();

        Runtime.getRuntime().addShutdownHook(new Thread("Proxy Shutdown Thread") {
            @Override
            public void run() {
                ch.stop();
                ws.shutdown();
            }
        });
    }

}
