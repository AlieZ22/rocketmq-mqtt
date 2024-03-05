/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.mqtt.cs.test.protocol.mqtt5;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.common.hook.UpstreamHookManager;
import org.apache.rocketmq.mqtt.cs.protocol.mqtt5.Mqtt5PacketDispatcher;
import org.apache.rocketmq.mqtt.cs.protocol.mqtt5.handler.Mqtt5PingHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestMqtt5PacketDispatcher {
    private CompletableFuture<HookResult> upstreamHookResult = new CompletableFuture<>();

    private Mqtt5PacketDispatcher packetDispatcher;
    private MqttFixedHeader mqttFixedHeader;
    private MqttMessage mqttMessage;

    @Spy
    private NioSocketChannel channel;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private UpstreamHookManager upstreamHookManager;

    @Mock
    private Mqtt5PingHandler mqtt5PingHandler;

    @Before
    public void setUp() throws Exception {
        packetDispatcher = new Mqtt5PacketDispatcher();
        mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0);
        FieldUtils.writeDeclaredField(packetDispatcher, "upstreamHookManager", upstreamHookManager, true);
        FieldUtils.writeDeclaredField(packetDispatcher, "mqtt5PingHandler", mqtt5PingHandler, true);

        when(mqtt5PingHandler.preHandler(any(), any())).thenReturn(true);
        when(ctx.channel()).thenReturn(channel);
        doReturn(true).when(channel).isActive();
    }

    @Test
    public void testRead0ChannelInActive() throws Exception {
        mqttMessage = new MqttMessage(mqttFixedHeader, null, null, DecoderResult.SUCCESS);
        doReturn(false).when(channel).isActive();

        MethodUtils.invokeMethod(packetDispatcher, true, "channelRead0", ctx, mqttMessage);

        verify(ctx).channel();
        verify(channel).isActive();
        verifyNoMoreInteractions(ctx, channel, upstreamHookManager, mqtt5PingHandler);
    }

    @Test
    public void testRead0Success() throws Exception {
        mqttMessage = new MqttMessage(mqttFixedHeader, null, null, DecoderResult.SUCCESS);
        doReturn(upstreamHookResult).when(upstreamHookManager).doUpstreamHook(any(), any());
        upstreamHookResult.complete(new HookResult(HookResult.SUCCESS, -1, null, null));

        MethodUtils.invokeMethod(packetDispatcher, true, "channelRead0", ctx, mqttMessage);

        // include ctx.channel within buildMqttMessageUpContext
        verify(ctx, times(3)).channel();
        verify(channel).isActive();
        verify(upstreamHookManager).doUpstreamHook(any(), any());
        verify(mqtt5PingHandler).doHandler(eq(ctx), any(), any());
        verify(mqtt5PingHandler).preHandler(eq(ctx), any());
        verifyNoMoreInteractions(ctx, upstreamHookManager, mqtt5PingHandler);
    }

    @Test
    public void testBuildMqttMessageUpContext() {
        packetDispatcher.buildMqttMessageUpContext(ctx);
        verify(ctx).channel();
    }
}
