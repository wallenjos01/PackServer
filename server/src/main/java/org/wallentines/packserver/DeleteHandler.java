package org.wallentines.packserver;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.wallentines.jwt.JWT;
import org.wallentines.jwt.JWTReader;
import org.wallentines.mdcfg.ConfigObject;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.Tuples;
import org.wallentines.mdcfg.codec.DecodeException;
import org.wallentines.mdcfg.codec.JSONCodec;

public class DeleteHandler {

    private final WebServer server;

    public DeleteHandler(WebServer server) { this.server = server; }

    // POST /delete
    // token: <JWT>
    // name: <name>
    // tag: <tag>
    public FullHttpResponse handle(FullHttpRequest req,
                                   ChannelHandlerContext ctx) {

        if (req.method() != HttpMethod.POST) {
            return new DefaultFullHttpResponse(req.protocolVersion(),
                                               HttpResponseStatus.BAD_REQUEST);
        }

        String token;
        String tag;
        String name;
        try (InputStream is = new ByteBufInputStream(req.content())) {
            ConfigObject obj = JSONCodec.loadConfig(is);
            if (!obj.isSection()) {
                return new DefaultFullHttpResponse(
                    req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
            }

            ConfigSection sec = obj.asSection();
            if (!sec.has("token") || !sec.has("hash")) {
                return new DefaultFullHttpResponse(
                    req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
            }

            token = sec.getOrDefault("token", (String)null);
            tag = sec.getOrDefault("tag", (String)null);
            name = sec.getOrDefault("name", (String)null);

        } catch (IOException ex) {
            return new DefaultFullHttpResponse(
                req.protocolVersion(),
                HttpResponseStatus.INTERNAL_SERVER_ERROR);
        } catch (DecodeException ex) {
            return new DefaultFullHttpResponse(req.protocolVersion(),
                                               HttpResponseStatus.BAD_REQUEST);
        }

        if (tag == null && name == null) {
            return new DefaultFullHttpResponse(req.protocolVersion(),
                                               HttpResponseStatus.BAD_REQUEST);
        } else if (name != null) {
            Tuples.T2<String, String> parsed = Util.parseTag(name);
            if (parsed == null) {
                return new DefaultFullHttpResponse(
                    req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
            }
            name = parsed.p1;
        }

        if (token == null) {
            return new DefaultFullHttpResponse(req.protocolVersion(),
                                               HttpResponseStatus.BAD_REQUEST);
        }

        JWT jwt = JWTReader.readAny(token, server.keySupplier()).getOrNull();
        if (jwt == null || !server.jwtVerifier().verify(jwt)) {
            return new DefaultFullHttpResponse(req.protocolVersion(),
                                               HttpResponseStatus.FORBIDDEN);
        }

        if (tag != null) {
            server.tagManager().removeTag(tag);
        } else if (name != null) {
            server.tagManager().removeAll(name);
        }

        return new DefaultFullHttpResponse(req.protocolVersion(),
                                           HttpResponseStatus.OK);
    }
}
