package org.wallentines.packserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PackHandler {

    private final WebServer server;

    public PackHandler(WebServer server) {
        this.server = server;
    }

    // GET /pack?hash=<HASH>
    public FullHttpResponse handle(FullHttpRequest req, ChannelHandlerContext ctx) {

        if(req.method() != HttpMethod.GET) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        Path packFile = getPackPath(req);

        if(packFile == null || !Files.exists(packFile)) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND);
        }

        ByteBuf out = ctx.alloc().buffer();
        int length = 0;
        try(InputStream is = Files.newInputStream(packFile)) {
            while(is.available() > 0) {
                length += out.writeBytes(is, is.available());
            }
        } catch (IOException ex) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }

        DefaultFullHttpResponse res = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK, out);
        res.headers()
                .add(HttpHeaderNames.CONTENT_TYPE, "application/zip")
                .add(HttpHeaderNames.CONTENT_LENGTH, length)
                .add(HttpHeaderNames.CONTENT_DISPOSITION, "inline; filename=\"pack.zip\"");

        return res;
    }

    private Path getPackPath(FullHttpRequest req) {

        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        List<String> hashParam = decoder.parameters().get("hash");
        if(hashParam != null && !hashParam.isEmpty()) {
            return server.packManager().get(hashParam.getFirst());
        }

        List<String> tagParam = decoder.parameters().get("tag");
        if(tagParam != null && !tagParam.isEmpty()) {
            String hash = server.tagManager().getHash(tagParam.getFirst());
            if(hash == null) return null;

            return server.packManager().get(hash);
        }

        return null;
    }

}
