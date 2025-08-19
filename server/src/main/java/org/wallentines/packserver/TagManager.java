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
import org.wallentines.mdcfg.Tuples;

public class TagManager implements FileSupplier {

    private final Path root;
    private final Map<String, String> cache = new HashMap<>();

    public TagManager(Path root) { this.root = root; }

    @Override
    public Path get(String tag) {

        Tuples.T2<String, String> parsed = Util.parseTag(tag);
        if (parsed == null) {
            return null;
        }

        return root.resolve(parsed.p1).resolve(parsed.p2);
    }

    public void pushTag(String tag, String hash) {
        Tuples.T2<String, String> parsed = Util.parseTag(tag);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid tag " + tag);
        }

        Path dir = root.resolve(parsed.p1);
        Path file = dir.resolve(parsed.p2);

        try {
            Files.createDirectories(dir);
            try (OutputStream os = Files.newOutputStream(file)) {
                os.write(hash.getBytes());
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        cache.put(tag, hash);
    }

    public void removeTag(String tag) {
        cache.remove(tag);
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

    public void clearCache() { this.cache.clear(); }

    public TagManager copy() {
        TagManager out = new TagManager(root);
        out.cache.putAll(cache);
        return out;
    }

    @Nullable
    public String getHash(String tag) {

        return cache.computeIfAbsent(tag, k -> {
            try (InputStream is = Files.newInputStream(get(tag))) {
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
