package com.example.library.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Повільний побічний ефект №1: лист про нову книгу. Fire-and-forget: void, ніхто не чекає. */
@Service
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final SimulatedLatency latency;

    public EmailSender(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Async(AsyncConfig.EXECUTOR_NAME)
    public void sendBookCreatedEmail(String title, String isbn) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Cannot send email: title is blank");
        }
        latency.pause("send-email");
        log.info("EMAIL sent: new book '{}' (isbn={})", title, isbn);
    }
}
