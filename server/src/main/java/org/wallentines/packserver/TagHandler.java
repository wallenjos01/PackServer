package org.wallentines.packserver;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.wallentines.mdcfg.ConfigObject;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.codec.DecodeException;
import org.wallentines.mdcfg.codec.JSONCodec;
import org.wallentines.mdproxy.jwt.JWT;
import org.wallentines.mdproxy.jwt.JWTReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class TagHandler {

    private final WebServer server;

    public TagHandler(WebServer server) {
        this.server = server;
    }

    // POST /tag
    // token: <JWT>
    // hash: <hash>
    // tag: <tag>
    public FullHttpResponse handle(FullHttpRequest req, ChannelHandlerContext ctx) {

        if(req.method() != HttpMethod.POST) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        String token;
        String hash;
        String tag;
        try(InputStream is = new ByteBufInputStream(req.content())) {
            ConfigObject obj = JSONCodec.loadConfig(is);
            if(!obj.isSection()) {
                return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
            }

            ConfigSection sec = obj.asSection();
            if(!sec.has("token") || !sec.has("hash")) {
                return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
            }

            token = sec.getOrDefault("token", (String) null);
            hash = sec.getOrDefault("hash", (String) null);
            tag = sec.getOrDefault("tag", (String) null);

        } catch (IOException ex) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
        } catch (DecodeException ex) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        if(hash == null || token == null || tag == null) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        Path packFile = server.packManager().get(hash);
        if(packFile == null) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        JWT jwt = JWTReader.readAny(token, server.keySupplier()).getOrNull();
        if(jwt == null || !server.jwtVerifier().verify(jwt)) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.FORBIDDEN);
        }

        if(!Files.exists(packFile)) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND);
        }

        server.tagManager().pushTag(tag, hash);
        return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK);
    }


}
