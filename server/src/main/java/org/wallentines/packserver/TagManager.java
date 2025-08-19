package org.wallentines.packserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

public class TagManager implements FileSupplier {

    private final Path root;
    private final Map<String, String> caches = new HashMap<>();

    public TagManager(Path root) { this.root = root; }

    @Override
    public Path get(String tag) {
        if (!Util.isValidTag(tag)) {
            return null;
        }
        return root.resolve(tag);
    }

    public void pushTag(String tag, String hash) {
        caches.put(tag, hash);
        try (OutputStream os = Files.newOutputStream(root.resolve(tag))) {
            os.write(hash.getBytes());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void removeTag(String tag) {
        caches.remove(tag);
        try {
            Files.deleteIfExists(root.resolve(tag));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Stream<String> getAllTags() {
        try {
            return Files.list(root)
                .map(Path::toString)
                .filter(Util::isValidTag);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Stream<String> getAllTaggedHashes() {
        return getAllTags().map(this::getHash);
    }

    public TagManager copy() {
        TagManager out = new TagManager(root);
        out.caches.putAll(caches);
        return out;
    }

    @Nullable
    public String getHash(String tag) {
        return caches.computeIfAbsent(tag, k -> {
            try (InputStream is = Files.newInputStream(root.resolve(tag))) {
                String bytes = new String(is.readAllBytes());
                if (!Util.isHexadecimal(bytes)) {
                    return null;
                }
                return bytes;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
