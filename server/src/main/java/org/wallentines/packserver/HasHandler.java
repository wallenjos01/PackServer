package org.wallentines.packserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HasHandler {

    private final WebServer server;

    public HasHandler(WebServer server) { this.server = server; }

    // GET /has?tag=<TAG>
    // GET /has?hash=<HASH>
    public FullHttpResponse handle(FullHttpRequest req,
                                   ChannelHandlerContext ctx) {

        if (req.method() != HttpMethod.GET) {
            return new DefaultFullHttpResponse(req.protocolVersion(),
                                               HttpResponseStatus.BAD_REQUEST);
        }

        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        FullHttpResponse response =
            getResponse(req, decoder, "tag", server.tagManager());
        if (response == null) {
            response = getResponse(req, decoder, "hash", server.packManager());
        }
        if (response == null) {
            response = new DefaultFullHttpResponse(
                req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        return response;
    }

    private FullHttpResponse getResponse(FullHttpRequest req,
                                         QueryStringDecoder decoder,
                                         String param, FileSupplier supplier) {

        List<String> paramData = decoder.parameters().get(param);
        if (paramData == null || paramData.isEmpty()) {
            return null;
        }

        String name = paramData.getFirst();
        Path file = supplier.get(name);

        if (file == null) {
            return new DefaultFullHttpResponse(req.protocolVersion(),
                                               HttpResponseStatus.BAD_REQUEST);
        } else if (!Files.exists(file)) {
            return new DefaultFullHttpResponse(req.protocolVersion(),
                                               HttpResponseStatus.NOT_FOUND);
        }

        return new DefaultFullHttpResponse(req.protocolVersion(),
                                           HttpResponseStatus.OK);
    }
}
