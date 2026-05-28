package service.registration.messaging;

import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import service.registration.messaging.config.EnrollmentRabbitMQConfig;
import service.registration.messaging.dto.EnrollmentEventDTO;

@Component
public class EnrollmentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public EnrollmentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishCreated(UUID conferenceId, UUID userId, String tipo) {
        EnrollmentEventDTO event = new EnrollmentEventDTO();
        event.setConferenceId(conferenceId);
        event.setUserId(userId);
        event.setTipo(tipo);
        rabbitTemplate.convertAndSend(
                EnrollmentRabbitMQConfig.EXCHANGE,
                EnrollmentRabbitMQConfig.ROUTING_KEY_CREATED,
                event);
    }
}
