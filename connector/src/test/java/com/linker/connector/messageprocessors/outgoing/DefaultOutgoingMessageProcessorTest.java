package com.linker.connector.messageprocessors.outgoing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.linker.common.Address;
import com.linker.common.Keywords;
import com.linker.common.Message;
import com.linker.common.MessageContent;
import com.linker.common.MessageFeature;
import com.linker.common.MessageMeta;
import com.linker.common.MessageState;
import com.linker.common.MessageType;
import com.linker.common.MessageUtils;
import com.linker.common.Utils;
import com.linker.common.messages.MessageForward;
import com.linker.common.messages.MessageStateChanged;
import com.linker.connector.IntegrationTest;
import com.linker.connector.SocketHandler;
import com.linker.connector.TestUser;
import com.linker.connector.TestUtils;
import io.netty.channel.DefaultChannelPromise;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;


@Slf4j
public class DefaultOutgoingMessageProcessorTest extends IntegrationTest {

    TestUser testUser;
    String userId1 = "ANZ-123223";
    String userId2 = "ANZ-123224";

    @After
    public void clean() throws TimeoutException {
        TestUtils.logout(testUser);
    }

    @Test
    public void test_successfullySentOut() throws TimeoutException, JsonProcessingException {
        testUser = TestUtils.loginClientUser(userId1);
        Message receivedMessage = messageArrived();

        // check user received message
        MessageContent userReceivedMessage = testUser.getReceivedMessage(MessageType.MESSAGE);
        userReceivedMessage.setData(userReceivedMessage.getData(MessageForward.class));
        assertEquals(receivedMessage.getContent(), userReceivedMessage);

        // check confirmation message
        checkConfirmedMessage(receivedMessage, MessageState.PROCESSED);
    }

    @Test
    public void test_targetNotFound() throws JsonProcessingException, TimeoutException {
        Message receivedMessage = messageArrived();
        // check confirmation message
        checkConfirmedMessage(receivedMessage, MessageState.TARGET_NOT_FOUND);
    }

    @Test
    public void test_errorInSendingMessage() throws TimeoutException, JsonProcessingException {
        testUser = TestUtils.loginClientUser(userId1);
        List<SocketHandler> user = networkUserService.getUser(userId1);
        SocketHandler spySocketHandler = spy(user.get(0));
        DefaultChannelPromise channelFuture = new DefaultChannelPromise(spySocketHandler.getChannel());
        doReturn(channelFuture).when(spySocketHandler).sendMessage(any(Message.class));
        user.set(0, spySocketHandler);
        Message receivedMessage = messageArrived();
        channelFuture.setFailure(new RuntimeException());
        // check confirmation message
        checkConfirmedMessage(receivedMessage, MessageState.NETWORK_ERROR);
    }

    Message messageArrived() throws JsonProcessingException {
        MessageContent content = new MessageContent(MessageType.MESSAGE,
                new MessageForward(userId2, "hi, this is the message form some one"),
                null, MessageFeature.FAST
        );
        Message receivedMessage = Message.builder()
                .content(content)
                .from(userId2)
                .to(userId1)
                .meta(new MessageMeta(
                        new Address(applicationConfig.getDomainName(), applicationConfig.getConnectorName(), 2L),
                        new Address(applicationConfig.getDomainName(), applicationConfig.getConnectorName(), 1L)
                ))
                .build();

        natsExpressDelivery.onMessageArrived(Utils.toJson(receivedMessage));

        return receivedMessage;
    }

    void checkConfirmedMessage(Message receivedMessage, MessageState state) throws TimeoutException {
        Message stateChangedMessage = kafkaExpressDelivery.getDeliveredMessage(MessageType.MESSAGE_STATE_CHANGED);
        Message expectedStateChangedMessage = Message.builder()
                .from(Keywords.SYSTEM)
                .meta(new MessageMeta(new Address(applicationConfig.getDomainName(), applicationConfig.getConnectorName(), 1L)))
                .content(
                        MessageUtils.createMessageContent(MessageType.MESSAGE_STATE_CHANGED,
                                new MessageStateChanged(receivedMessage.toSnapshot(), state), MessageFeature.RELIABLE)
                )
                .build();
        TestUtils.messageEquals(expectedStateChangedMessage, stateChangedMessage);
    }
}
