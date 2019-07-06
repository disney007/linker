package com.linker.processor.messageprocessors;

import com.google.common.collect.ImmutableSet;
import com.linker.common.Message;
import com.linker.common.MessageFeature;
import com.linker.common.MessageProcessor;
import com.linker.common.MessageState;
import com.linker.common.MessageType;
import com.linker.common.MessageUtils;
import com.linker.processor.PostOffice;
import com.linker.processor.repositories.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class MessageProcessorService {
    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    PostOffice postOffice;

    @Autowired
    MessageRepository messageRepository;

    Map<MessageType, MessageProcessor<?>> processors = new HashMap<>();

    Set<MessageType> persistentMessageTypes = ImmutableSet.of(MessageType.MESSAGE, MessageType.USER_CONNECTED, MessageType.USER_DISCONNECTED);

    @PostConstruct
    public void setup() {
        applicationContext.getBeansOfType(MessageProcessor.class)
                .forEach((key, value) -> processors.put(value.getMessageType(), value));
    }

    public void process(Message message) {
        log.info("start processing message [{}]", message);

        MessageUtils.touchMessage(message);
        if (!MessageUtils.isMessageAlive(message)) {
            log.info("message [{}] is not alive", message);
            return;
        }

        MessageType messageType = message.getContent().getType();
        MessageProcessor<?> processor = processors.getOrDefault(messageType, null);
        if (processor != null) {
            processor.preprocess(message, null);
            saveMessage(message);
            processor.process(message, null);
        } else {
            log.warn("no processor found for message type {}", messageType);
        }
    }

    boolean shouldPersistMessage(MessageFeature feature, MessageType type) {
        return feature == MessageFeature.RELIABLE && persistentMessageTypes.contains(type);
    }

    void saveMessage(Message message) {
        if (shouldPersistMessage(message.getContent().getFeature(), message.getContent().getType())) {
            messageRepository.save(message);
        }
    }

    void updateMessageState(Message message, MessageState newState) {
        updateMessageState(message.getContent().getFeature(), message.getContent().getType(), message.getId(), newState);
    }

    void updateMessageState(MessageFeature feature, MessageType type, String messageId, MessageState newState) {
        if (shouldPersistMessage(feature, type)) {
            messageRepository.updateState(messageId, newState);
        }
    }
}
