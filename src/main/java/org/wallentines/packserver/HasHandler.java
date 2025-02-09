package org.wallentines.packserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HasHandler {

    private final WebServer server;

    public HasHandler(WebServer server) {
        this.server = server;
    }

    // GET /has?hash=<HASH>
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
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK);
        } else {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND);
        }

    }

}
