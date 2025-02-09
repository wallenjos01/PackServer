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
import org.wallentines.mdproxy.jwt.JWTVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeleteHandler {

    private final WebServer server;
    private final JWTVerifier verifier;

    public DeleteHandler(WebServer server) {
        this.server = server;
        this.verifier = new JWTVerifier();
    }

    // POST /delete
    // token: <JWT>
    // hash: <hash>
    public FullHttpResponse handle(FullHttpRequest req, ChannelHandlerContext ctx) {

        if(req.method() != HttpMethod.POST) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        String token = null;
        String hash = null;

        try(InputStream is = new ByteBufInputStream(req.content())) {
            ConfigObject obj = JSONCodec.loadConfig(is);
            if(!obj.isSection()) {
                return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
            }

            ConfigSection sec = obj.asSection();
            if(!sec.has("token") || !sec.has("hash")) {
                return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
            }

            token = sec.getString("token");
            hash = sec.getString("hash");

        } catch (IOException ex) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
        } catch (DecodeException ex) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        if(hash == null || token == null || !Util.isHexadecimal(hash)) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST);
        }

        JWT jwt = JWTReader.readAny(token, server.keySupplier()).getOrNull();
        if(jwt == null || !verifier.verify(jwt)) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.FORBIDDEN);
        }

        Path packFile = server.packDir().resolve(hash);
        if(!Files.exists(packFile)) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND);
        }

        try {
            Files.delete(packFile);
        } catch (IOException ex) {
            return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }

        return new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK);
    }


}
