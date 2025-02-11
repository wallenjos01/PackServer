import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TestDZip {

    @Test
    public void test() {

        byte[] compressedData;
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(bos)) {

            ZipEntry ze = new ZipEntry("hello.txt");
            ze.setCreationTime(FileTime.from(Instant.EPOCH));
            ze.setLastAccessTime(FileTime.from(Instant.EPOCH));
            ze.setLastModifiedTime(FileTime.from(Instant.EPOCH));
            zos.putNextEntry(ze);

            zos.write("hello".getBytes());

            zos.closeEntry();
            zos.close();

            compressedData = bos.toByteArray();

        } catch (IOException ex) {
            Assertions.fail(ex);
            return;
        }


        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] sum = digest.digest(compressedData);

            Assertions.assertEquals("3c4cadbfb3b877a329838f26701cad462223f2b6", HexFormat.of().formatHex(sum));

        } catch (GeneralSecurityException ex) {
            Assertions.fail(ex);
        }

    }


}
