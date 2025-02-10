package org.wallentines.packserver.netty;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public enum ChannelType {

    NIO(NioServerSocketChannel::new, NioSocketChannel::new),
    EPOLL(EpollServerSocketChannel::new, EpollSocketChannel::new),
    KQUEUE(KQueueServerSocketChannel::new, KQueueSocketChannel::new);

    public final ChannelFactory<? extends ServerSocketChannel> serverSocketChannelFactory;
    public final ChannelFactory<? extends SocketChannel> socketChannelFactory;

    ChannelType(ChannelFactory<? extends ServerSocketChannel> serverSocketChannelFactory, ChannelFactory<? extends SocketChannel> socketChannelFactory) {
        this.serverSocketChannelFactory = serverSocketChannelFactory;
        this.socketChannelFactory = socketChannelFactory;
    }

    public static EventLoopGroup createEventLoopGroup(ChannelType type, String name) {

        ThreadFactory tf = createThreadFactory(name);
        return switch (type) {
            case NIO -> new NioEventLoopGroup(0, tf);
            case EPOLL -> new EpollEventLoopGroup(0,tf);
            case KQUEUE -> new KQueueEventLoopGroup(0, tf);
        };
    }

    public static ChannelType getBestChannelType() {
        if(Epoll.isAvailable()) return EPOLL;
        if(KQueue.isAvailable()) return KQUEUE;
        return NIO;
    }

    public static ThreadFactory createThreadFactory(String name) {
        return new Factory(name + " #%d");
    }

    private static class Factory implements ThreadFactory {

        private final AtomicInteger currentThread = new AtomicInteger();
        private final String nameFormat;

        public Factory(String nameFormat) {
            this.nameFormat = nameFormat;
        }

        @Override
        public Thread newThread(@NotNull Runnable r) {
            String name = String.format(nameFormat, currentThread.getAndIncrement());
            return new FastThreadLocalThread(name) {
                @Override
                public void run() {
                    r.run();
                }
            };
        }
    }



}
