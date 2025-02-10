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

        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        List<String> hashParam = decoder.parameters().get("hash");
        if(hashParam == null || hashParam.isEmpty()) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        String hash = hashParam.getFirst();
        if(!Util.isHexadecimal(hash)) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        Path packFile = server.packDir().resolve(hash);

        if(Files.exists(packFile)) {

            ByteBuf out = ctx.alloc().buffer();
            try(InputStream is = Files.newInputStream(packFile)) {
                while(is.available() > 0) {
                    out.writeBytes(is, is.available());
                }
            } catch (IOException ex) {
                return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK, out);

        } else {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND);
        }
    }

}
