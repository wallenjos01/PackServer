package org.wallentines.packserver;

import java.nio.file.Path;

public class PackManager implements FileSupplier{

    private final Path root;
    public PackManager(Path root) {
        this.root = root;
    }

    @Override
    public Path get(String hash) {
        if(!Util.isHexadecimal(hash)) {
            return null;
        }
        return root.resolve(hash);
    }

}
