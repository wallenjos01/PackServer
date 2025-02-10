package org.wallentines.packserver.netty;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.wallentines.packserver.WebServer;

public class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final WebServer server;

    public HttpHandler(WebServer server) {
        this.server = server;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {

        if(!req.decoderResult().isSuccess() || req.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST));
            return;
        }

        String path = req.uri();
        if(!path.startsWith(server.baseUrl())) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND));
            return;
        }

        path = path.substring(server.baseUrl().length());
        if(path.startsWith("/")) {
            path = path.substring(1);
        }

        int paramsStart = path.indexOf('?');
        if(paramsStart != -1) {
            path = path.substring(0, paramsStart);
        }

        try {
            FullHttpResponse res = switch (path) {
                case "pack" -> server.packHandler().handle(req, ctx);
                case "has" -> server.hasHandler().handle(req, ctx);
                case "push" -> server.pushHandler().handle(req, ctx);
                case "delete" -> server.deleteHandler().handle(req, ctx);
                case "hash" -> server.hashHandler().handle(req, ctx);
                case "tag" -> server.tagHandler().handle(req, ctx);
                default -> new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND);
            };
            sendHttpResponse(ctx, req, res);
        } catch (Throwable t) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        HttpResponseStatus responseStatus = res.status();
        if (responseStatus.code() != 200) {
            ByteBufUtil.writeUtf8(res.content(), responseStatus.toString());
        }

        HttpUtil.setContentLength(res, res.content().readableBytes());
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
}
