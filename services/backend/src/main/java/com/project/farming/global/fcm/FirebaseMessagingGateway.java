package com.project.farming.global.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FirebaseMessagingGateway {

    public String send(Message message) throws FirebaseMessagingException {
        return FirebaseMessaging.getInstance().send(message);
    }

    public BatchResponse sendEach(List<Message> messages) throws FirebaseMessagingException {
        return FirebaseMessaging.getInstance().sendEach(messages);
    }

    public BatchResponse sendEachForMulticast(MulticastMessage message) throws FirebaseMessagingException {
        return FirebaseMessaging.getInstance().sendEachForMulticast(message);
    }
}
