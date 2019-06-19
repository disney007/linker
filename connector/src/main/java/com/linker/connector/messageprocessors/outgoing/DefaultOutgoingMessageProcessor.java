package com.linker.connector.messageprocessors.outgoing;

import com.linker.common.Message;
import com.linker.common.MessageContext;
import com.linker.common.MessageType;
import com.linker.connector.NetworkUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DefaultOutgoingMessageProcessor extends OutgoingMessageProcessor<Object> {
    @Autowired
    NetworkUserService networkUserService;

    @Override
    public MessageType getMessageType() {
        return MessageType.ANY;
    }

    @Override
    public void doProcess(Message message, Object data, MessageContext context) throws IOException {
        networkUserService.getUser(message.getTo()).sendMessage(message);
    }
}