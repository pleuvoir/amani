package io.github.armani.client;

import io.github.armani.client.command.ConsoleCommandManager;
import io.github.armani.client.handler.ChatMessageResponsetHandler;
import io.github.armani.client.handler.CreateGroupResponseHandler;
import io.github.armani.client.handler.GroupMessageResponseHandler;
import io.github.armani.client.handler.LoginResponseHandler;
import io.github.armani.common.codec.PacketDecoder;
import io.github.armani.common.codec.PacketEncoder;
import io.github.armani.common.utils.AttributeKeyConst;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * 客户端
 */
public class ArmaniClientStartup {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArmaniClientStartup.class);

    /**
     * 最大重连次数
     */
    public static final int MAX_RETRY_CONNECT_NUM = 5;

    public static void main(String[] args) {

        final Bootstrap bootstrap = new Bootstrap();
        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();

        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)  //连接超时时间
                .option(ChannelOption.SO_KEEPALIVE, true) //关闭2小时的长链接keepalive
                .option(ChannelOption.TCP_NODELAY, true)  //关闭Nagle算法，不进行批量合并发送
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        LOGGER.info("客户端启动中");
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 6, 4));
                        ch.pipeline().addLast(new PacketDecoder());
                        ch.pipeline().addLast(new LoginResponseHandler());
                        ch.pipeline().addLast(new ChatMessageResponsetHandler());
                        ch.pipeline().addLast(new CreateGroupResponseHandler());
                        ch.pipeline().addLast(new GroupMessageResponseHandler());
                        ch.pipeline().addLast(new PacketEncoder());
                    }
                });

        bootstrap.attr(AttributeKeyConst.USER_ID, null);

        connectWithRetry(bootstrap, new InetSocketAddress("127.0.0.1", 8443), MAX_RETRY_CONNECT_NUM);
    }

    private static void connectWithRetry(Bootstrap bootstrap, SocketAddress remoteAddress, int retryNum) {
        bootstrap.connect(remoteAddress).addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                if (future.isSuccess()) {
                    LOGGER.info("客户端连接成功");
                    //此处需要强转
                    ChannelFuture channelFuture = (ChannelFuture) future;

                    ConsoleCommandManager.INSTANCE.startConsoleInput(channelFuture.channel());

                } else if (retryNum == 0) {
                    LOGGER.error("尝试重连到达上限，不再进行连接。");
                } else {
                    //第几次重连
                    int sequence = MAX_RETRY_CONNECT_NUM - retryNum + 1;
                    //重连延迟  2的sequence幂 2 4 8 16
                    int delay = 1 << sequence;
                    LOGGER.warn("客户端第{}次连接失败，{}，{}秒后尝试重新连接... ", sequence, future.cause().getMessage(), delay);

                    //netty提供的定时器
                    bootstrap.config().group().schedule(() -> {
                        connectWithRetry(bootstrap, remoteAddress, retryNum - 1);
                    }, delay, TimeUnit.SECONDS);
                }
            }
        });
    }


}
