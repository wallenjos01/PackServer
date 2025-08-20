package org.wallentines.packserver.uploader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.cli.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.utils.Hex;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.codec.JSONCodec;
import org.wallentines.mdcfg.serializer.ConfigContext;

public class Main {

    public static void main(String[] args) {

        Options options = new Options();

        Option server = new Option("s", "server", true, "Server URL");
        server.setRequired(true);
        options.addOption(server);

        Option token = new Option("t", "token", true, "Server Access Token");
        token.setRequired(true);
        options.addOption(token);

        Option input = new Option("i", "input", true, "Input file");
        input.setRequired(true);
        options.addOption(input);

        options.addOption("T", "tag", true, "pack tag");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("uploader", options);
            System.exit(1);
            return;
        }

        execute(cmd);
    }

    private static String getSha1(InputStream is)
        throws IOException, GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        return Hex.encodeHexString(digest.digest());
    }

    private static void execute(CommandLine cmd) {

        String address = cmd.getOptionValue("s");
        String token = cmd.getOptionValue("t");
        String inputFileName = cmd.getOptionValue("i");
        String tag = cmd.getOptionValue("T");

        if (!address.endsWith("/"))
            address += "/";

        Path inputFile =
            Paths.get(System.getProperty("user.dir"), inputFileName);
        if (!Files.exists(inputFile)) {
            System.err.println("Input file " + inputFileName +
                               " does not exist");
            System.exit(1);
        }

        AbstractContentBody dataBody;
        String sha1;

        // Make a zip file
        if (Files.isDirectory(inputFile)) {

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(baos)) {

                Files.walkFileTree(inputFile, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file,
                                                     BasicFileAttributes attrs)
                        throws IOException {
                        ZipEntry ent =
                            new ZipEntry(inputFile.relativize(file).toString());
                        ent.setCreationTime(FileTime.from(Instant.EPOCH));
                        ent.setLastModifiedTime(FileTime.from(Instant.EPOCH));
                        ent.setLastAccessTime(FileTime.from(Instant.EPOCH));
                        zos.putNextEntry(ent);
                        try (InputStream is = Files.newInputStream(file)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                zos.write(buffer, 0, bytesRead);
                            }
                        }
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
                zos.flush();
                zos.close();

                baos.flush();

                byte[] bytes = baos.toByteArray();
                dataBody = new ByteArrayBody(
                    bytes, ContentType.APPLICATION_OCTET_STREAM,
                    inputFile.getFileName().toString());
                sha1 = getSha1(new ByteArrayInputStream(bytes));

            } catch (IOException | GeneralSecurityException ex) {
                throw new RuntimeException(ex);
            }

        } else {
            dataBody = new FileBody(inputFile.toFile(),
                                    ContentType.APPLICATION_OCTET_STREAM);
            try (InputStream is = Files.newInputStream(inputFile)) {
                sha1 = getSha1(is);
            } catch (GeneralSecurityException | IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpResponse res =
                client.execute(new HttpGet(address + "has?hash=" + sha1));
            if (res.getCode() == 200) { // Already uploaded

                if (tag == null) {
                    System.out.println("Success! The server already has a " +
                                       "pack with that hash!");
                    return;
                }

                HttpPost post = new HttpPost(address + "tag");
                String data = JSONCodec.minified().encodeToString(
                    ConfigContext.INSTANCE, new ConfigSection()
                                                .with("token", token)
                                                .with("hash", sha1)
                                                .with("tag", tag));
                byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
                post.setEntity(
                    new ByteArrayEntity(bytes, ContentType.APPLICATION_JSON));
                client.execute(post);
                System.out.println(
                    "Success! Tagged an existing pack with that hash!");

            } else {

                HttpPost post = new HttpPost(address + "push");
                MultipartEntityBuilder builder =
                    MultipartEntityBuilder.create()
                        .addPart("token", new StringBody(
                                              token, ContentType.DEFAULT_TEXT))
                        .addPart("data", dataBody)
                        .addPart("tag",
                                 new StringBody(tag, ContentType.DEFAULT_TEXT));

                post.setEntity(builder.build());
                res = client.execute(post);
                if (res.getCode() != 200) {
                    throw new RuntimeException(
                        "Could not upload resource pack to: " + address +
                        "! Got code " + res.getCode());
                }
                System.out.println("Success! Uploaded a new pack!");
            }

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
