package org.wallentines.packserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class PackManager implements FileSupplier {

    private final Path root;
    public PackManager(Path root) { this.root = root; }

    @Override
    public Path get(String hash) {
        if (!Util.isHexadecimal(hash)) {
            return null;
        }
        return root.resolve(hash);
    }

    public void prune(Set<String> toKeep) {
        try {
            Files.list(root)
                .filter(p -> !toKeep.contains(p.toString()))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
