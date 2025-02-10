package org.wallentines.packserver;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface FileSupplier {

    @Nullable
    Path get(String name);

}
