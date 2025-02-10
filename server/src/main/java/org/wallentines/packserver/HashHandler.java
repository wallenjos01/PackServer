package org.wallentines.packserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.util.List;

public class HashHandler {

    private final WebServer server;

    public HashHandler(WebServer server) {
        this.server = server;
    }

    // GET /hash?tag=<TAG>
    public FullHttpResponse handle(FullHttpRequest req, ChannelHandlerContext ctx) {

        if(req.method() != HttpMethod.GET) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        List<String> tagParam = decoder.parameters().get("tag");
        if(tagParam == null || tagParam.isEmpty()) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        String tag = tagParam.getFirst();
        String hash = server.tagManager().getHash(tag);

        if(hash == null) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND);
        } else {
            ByteBuf out = ctx.alloc().buffer();
            out.writeBytes(hash.getBytes());
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK, out);
        }

    }

}
