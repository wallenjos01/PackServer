package org.wallentines.packserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.mdcfg.serializer.SerializeResult;
import org.wallentines.mdproxy.jwt.JWT;
import org.wallentines.mdproxy.jwt.JWTReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public class PushHandler {

    private static final Logger log = LoggerFactory.getLogger(PushHandler.class);
    private final WebServer server;

    public PushHandler(WebServer server) {
        this.server = server;
    }

    // POST /pack
    // token: <JWT>
    // data: <Zip Data>
    public FullHttpResponse handle(FullHttpRequest req, ChannelHandlerContext ctx) {

        if(req.method() != HttpMethod.POST) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(req);
        String token = null;
        ByteBuf fileData = null;
        String tag = null;

        while(decoder.hasNext() && (token == null || fileData == null || tag == null)) {
            InterfaceHttpData data = decoder.next();
            if(data.getName().equals("data")) {
                if (!(data instanceof FileUpload up)) {
                    return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
                }
                fileData = up.content();
            } else if(data.getName().equals("token")) {
                ByteBuf tokenData = ((HttpData) data).content();
                token = tokenData.toString(StandardCharsets.US_ASCII);
            } else if(data.getName().equals("tag")) {
                ByteBuf tagData = ((HttpData) data).content();
                tag = tagData.toString(StandardCharsets.US_ASCII);
            }
        }

        if(token == null || fileData == null) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }


        SerializeResult<JWT> jwt = JWTReader.readAny(token, server.keySupplier());
        if(!jwt.isComplete() || !server.jwtVerifier().verify(jwt.getOrNull())) {
            log.info("Attempt to push pack with invalid JWT {}", token);
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.FORBIDDEN);
        }


        ByteBuf sha1Buf = fileData.asReadOnly();
        byte[] copyBuffer = new byte[4096];
        byte[] sha1;
        try(InputStream is = new ByteBufInputStream(sha1Buf)) {

            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            int bytesRead;
            while((bytesRead = is.read(copyBuffer)) > 0) {
                digest.update(copyBuffer, 0, bytesRead);
            }
            sha1 = digest.digest();

        } catch (IOException | GeneralSecurityException ex) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        String hashHex = HexFormat.of().formatHex(sha1);
        Path packPath = server.packManager().get(hashHex);
        if(packPath == null) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }

        try(OutputStream os = Files.newOutputStream(packPath);
            InputStream is = new ByteBufInputStream(fileData)) {

            int bytesRead;
            while ((bytesRead = is.read(copyBuffer)) > 0) {
                os.write(copyBuffer, 0, bytesRead);
            }
        } catch (IOException ex) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }

        ByteBuf out = ctx.alloc().buffer();
        out.writeBytes(hashHex.getBytes(StandardCharsets.US_ASCII));

        if(tag != null) {
            server.tagManager().pushTag(tag, hashHex);
        }

        return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK, out);
    }

}
