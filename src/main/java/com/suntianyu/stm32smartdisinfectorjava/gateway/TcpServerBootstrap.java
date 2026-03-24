package com.suntianyu.stm32smartdisinfectorjava.gateway;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TcpServerBootstrap {

    @Value("${app.device.tcp-port:9000}")
    private int port;

    @Value("${app.device.offline-timeout-ms:30000}")
    private long offlineTimeoutMs;

    private final DeviceMessageHandler deviceMessageHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;

    @PostConstruct
    public void start() {
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new LineBasedFrameDecoder(1024));
                                ch.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));
                                ch.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8));
                                ch.pipeline().addLast(new IdleStateHandler(offlineTimeoutMs, 0, 0, TimeUnit.MILLISECONDS));
                                ch.pipeline().addLast(deviceMessageHandler);
                            }
                        });

                channelFuture = b.bind(port).sync();
                log.info("TCP Server started on port {}", port);
                channelFuture.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("TCP Server interrupted", e);
                Thread.currentThread().interrupt();
            } finally {
                shutdown();
            }
        }).start();
    }

    @PreDestroy
    public void shutdown() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("TCP Server stopped");
    }
}

