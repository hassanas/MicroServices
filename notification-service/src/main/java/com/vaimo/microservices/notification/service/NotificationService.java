package com.vaimo.microservices.notification.service;

import com.vaimo.microservices.notification.order.OrderPlacedEvent;
import jakarta.mail.MessagingException; // Fixes Java 21 / Spring Boot compatibility
import jakarta.mail.internet.MimeMessage; // Fixes the constructor compilation error
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender javaMailSender;

    @KafkaListener(topics = "order-placed", groupId = "notification-consumer-v4")
    public void listen(OrderPlacedEvent orderPlacedEvent) {
        log.info("Received OrderPlacedEvent {}", orderPlacedEvent);

        try {
            // 1. Create the base MimeMessage instance
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();

            // 2. Initialize the MimeMessageHelper (true allows HTML and attachments)
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // 3. Set the delivery addresses and subject line
            messageHelper.setFrom("noreply@yourcompany.com");
            messageHelper.setTo(orderPlacedEvent.getEmail());
            messageHelper.setSubject("Order Confirmation - " + orderPlacedEvent.getOrderNumber());

            // 4. Construct the HTML email body message template
            String htmlContent = String.format(
                    "<h1>Thank you for your order!</h1>" +
                            "<p>Hi,</p>" +
                            "<p>Your order <strong>#%s</strong> has been successfully placed.</p>",
                    orderPlacedEvent.getOrderNumber()
            );

            // Apply text layout (true marks text content as HTML format)
            messageHelper.setText(htmlContent, true);

            // 5. Trigger delivery via SMTP mail center
            javaMailSender.send(mimeMessage);
            log.info("Email successfully sent to {}", orderPlacedEvent.getEmail());

        } catch (MessagingException e) {
            log.error("Failed to compile or deliver email for order {}", orderPlacedEvent.getOrderNumber(), e);
        }
    }
}
