package org.wallentines.gradle.packserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.codec.JSONCodec;
import org.wallentines.mdcfg.serializer.ConfigContext;

public class PackUploaderExtension {

    private final List<UploadInfo> uploadUrls = new ArrayList<>();
    private String description;
    private Project project;

    private record UploadInfo(String url, String token, @Nullable String tag) {}

    public PackUploaderExtension(Project project) {

        this.project = project;

        project.getTasks().register("buildPack", BuildPackTask.class, this);
        project.getTasks().register("uploadPack", UploadTask.class, this);
        this.description = project.getRootProject().getName();
    }

    public String getDescription() { return description; }

    public void setDescription(String description) {
        this.description = description;
    }

    public void uploadTo(String packServerUrl, String token) {

        if (!packServerUrl.endsWith("/"))
            packServerUrl = packServerUrl + "/";
        this.uploadUrls.add(
            new UploadInfo(packServerUrl, token, project.getName()));
    }

    public void uploadTo(String packServerUrl, String token, String tag) {

        if (!packServerUrl.endsWith("/"))
            packServerUrl = packServerUrl + "/";
        this.uploadUrls.add(new UploadInfo(packServerUrl, token, tag));
    }

    public static class BuildPackTask
        extends DefaultTask implements Action<Task> {

        private final PackUploaderExtension extension;
        private final RegularFileProperty outputZip;
        private final RegularFileProperty outputHash;

        @Inject
        public BuildPackTask(PackUploaderExtension extension) {
            this.extension = extension;
            Path packDir = getProject()
                               .getLayout()
                               .getBuildDirectory()
                               .getAsFile()
                               .get()
                               .toPath()
                               .resolve("packs");

            this.outputZip = getProject().getObjects().fileProperty();
            this.outputHash = getProject().getObjects().fileProperty();

            outputZip.fileValue(packDir.resolve("resources.zip").toFile());
            outputHash.fileValue(packDir.resolve("resources.sha1").toFile());

            this.dependsOn("remapJar");
            this.dependsOn("processResources");
            this.setActions(List.of(this));
        }

        @OutputFile
        public RegularFileProperty getOutputZip() {
            return outputZip;
        }

        @OutputFile
        public RegularFileProperty getOutputHash() {
            return outputHash;
        }

        private ZipEntry zipEntry(String fileName) {

            ZipEntry ze = new ZipEntry(fileName);
            ze.setCreationTime(FileTime.from(Instant.EPOCH));
            ze.setLastAccessTime(FileTime.from(Instant.EPOCH));
            ze.setLastModifiedTime(FileTime.from(Instant.EPOCH));

            return ze;
        }

        @Override
        public void execute(Task task) {

            Project project = getProject();
            SourceSetContainer sourceSets =
                project.getExtensions().getByType(SourceSetContainer.class);
            byte[] buffer = new byte[4096];

            // Find Resource Pack version
            SourceSet set =
                sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            LoomGradleExtensionAPI ext =
                project.getExtensions().getByType(LoomGradleExtensionAPI.class);

            File f = ext.getNamedMinecraftJars()
                         .getFiles()
                         .stream()
                         .findAny()
                         .orElseThrow();
            if (!f.exists()) {
                throw new RuntimeException(
                    "Could not find minecraft server jar");
            }

            ConfigSection packMeta;
            try (ZipFile zf = new ZipFile(f)) {
                ZipEntry ent = zf.getEntry("version.json");

                ConfigSection config =
                    JSONCodec.loadConfig(zf.getInputStream(ent)).asSection();
                int packFormat =
                    config.getSection("pack_version").getInt("resource");

                packMeta = new ConfigSection().with(
                    "pack", new ConfigSection()
                                .with("pack_format", packFormat)
                                .with("description", extension.description));

            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            // Export resource pack
            Path packFile = this.outputZip.getAsFile().get().toPath();
            try {
                Files.createDirectories(packFile.getParent());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            try (ZipOutputStream zos =
                     new ZipOutputStream(Files.newOutputStream(packFile))) {

                zos.putNextEntry(zipEntry("pack.mcmeta"));
                String meta = JSONCodec.minified().encodeToString(
                    ConfigContext.INSTANCE, packMeta);
                zos.write(meta.getBytes());
                zos.closeEntry();

                for (File resourceDir :
                     set.getResources().getSourceDirectories().getFiles()) {
                    Path assetsDir = resourceDir.toPath().resolve("assets");
                    Files.walkFileTree(assetsDir, new SimpleFileVisitor<>() {
                        @Override
                        public @NotNull FileVisitResult visitFile(
                            Path file, @NotNull BasicFileAttributes attrs)
                            throws IOException {
                            zos.putNextEntry(zipEntry(
                                "assets/" + assetsDir.relativize(file)));
                            try (InputStream is = Files.newInputStream(file)) {
                                int bytesRead;
                                while ((bytesRead = is.read(buffer)) != -1) {
                                    zos.write(buffer, 0, bytesRead);
                                }
                            }
                            zos.closeEntry();
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            // Compute sha1
            String sha1;
            try (InputStream is = Files.newInputStream(packFile)) {

                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }

                byte[] sha1Bytes = digest.digest();
                sha1 = HexFormat.of().formatHex(sha1Bytes);

            } catch (IOException | GeneralSecurityException ex) {
                throw new RuntimeException(ex);
            }

            Path sha1File = this.outputHash.getAsFile().get().toPath();
            try {
                Files.createDirectories(sha1File.getParent());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            try (OutputStream os = Files.newOutputStream(sha1File)) {
                os.write(sha1.getBytes());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static class UploadTask extends DefaultTask implements Action<Task> {

        private final PackUploaderExtension extension;

        @Inject
        public UploadTask(PackUploaderExtension extension) {
            this.extension = extension;
            this.dependsOn("buildPack");
            this.setActions(List.of(this));
        }

        @Override
        public void execute(@NotNull Task task) {

            Project project = getProject();
            BuildPackTask bpt =
                (BuildPackTask)project.getTasks().getByName("buildPack");

            // Upload pack
            if (this.extension.uploadUrls.isEmpty()) {
                return;
            }

            String sha1;
            try (InputStream is = Files.newInputStream(
                     bpt.outputHash.getAsFile().get().toPath())) {
                sha1 = new String(is.readAllBytes());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            try (ExecutorService executor = Executors.newFixedThreadPool(
                     Math.min(4, this.extension.uploadUrls.size()))) {
                for (UploadInfo ent : extension.uploadUrls) {
                    executor.execute(() -> {
                        try (CloseableHttpClient client =
                                 HttpClientBuilder.create().build()) {
                            HttpGet get =
                                new HttpGet(ent.url() + "has?hash=" + sha1);

                            HttpResponse res = client.execute(get);
                            if (res.getCode() == 200) { // Already uploaded

                                if (ent.tag == null)
                                    return;

                                String tagEndpoint = ent.url() + "tag";
                                HttpPost post = new HttpPost(tagEndpoint);
                                String data =
                                    JSONCodec.minified().encodeToString(
                                        ConfigContext.INSTANCE,
                                        new ConfigSection()
                                            .with("token", ent.token)
                                            .with("hash", sha1)
                                            .with("tag", ent.tag));
                                byte[] bytes =
                                    data.getBytes(StandardCharsets.UTF_8);
                                post.setEntity(new ByteArrayEntity(
                                    bytes, ContentType.APPLICATION_JSON));
                                client.execute(post);

                            } else {

                                HttpPost post =
                                    new HttpPost(ent.url() + "push");
                                MultipartEntityBuilder builder =
                                    MultipartEntityBuilder.create()
                                        .addPart("token",
                                                 new StringBody(
                                                     ent.token(),
                                                     ContentType.DEFAULT_TEXT))
                                        .addPart(
                                            "data",
                                            new FileBody(
                                                bpt.outputZip.getAsFile().get(),
                                                ContentType
                                                    .APPLICATION_OCTET_STREAM));

                                if (ent.tag != null) {
                                    builder.addPart(
                                        "tag",
                                        new StringBody(
                                            ent.tag, ContentType.DEFAULT_TEXT));
                                }

                                post.setEntity(builder.build());
                                res = client.execute(post);
                                if (res.getCode() != 200) {
                                    throw new RuntimeException(
                                        "Could not upload resource pack to: " +
                                        ent.url + " (" + res.getCode() + ")");
                                }
                            }

                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                }
            }
        }
    }
}
