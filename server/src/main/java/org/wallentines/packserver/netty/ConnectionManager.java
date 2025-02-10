package org.wallentines.packserver.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.packserver.WebServer;

import java.net.InetSocketAddress;

public class ConnectionManager {

    private static final WriteBufferWaterMark WATER_MARK = new WriteBufferWaterMark(1 << 20, 1 << 21);
    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);


    private final ChannelType channelType;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final WebServer server;
    private ChannelFuture listenChannel;

    public ConnectionManager(WebServer server) {
        this.server = server;
        this.channelType = ChannelType.getBestChannelType();
        this.bossGroup = ChannelType.createEventLoopGroup(channelType, "Netty Boss");
        this.workerGroup = ChannelType.createEventLoopGroup(channelType, "Netty Worker");
    }

    public void startListener() {

        InetSocketAddress addr = new InetSocketAddress(server.port());

        ServerBootstrap bootstrap = new ServerBootstrap()
                .channelFactory(channelType.serverSocketChannelFactory)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, WATER_MARK)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.IP_TOS, 0x18)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1024 * 1024 * 512))
                                .addLast(new HttpHandler(server));
                    }
                })
                .group(bossGroup, workerGroup)
                .localAddress(addr);

        this.listenChannel = bootstrap.bind().syncUninterruptibly();
        log.info("Web server listening on {}:{}", addr.getHostString(), addr.getPort());
    }

    public void stop() {
        this.listenChannel.channel().close().syncUninterruptibly();
        this.bossGroup.shutdownGracefully();
    }

}
