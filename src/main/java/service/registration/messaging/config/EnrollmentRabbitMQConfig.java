package service.registration.messaging.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnrollmentRabbitMQConfig {

    public static final String EXCHANGE = "enrollment.events";
    public static final String ROUTING_KEY_CREATED = "enrollment.created";
    public static final String ROUTING_KEY_CANCELLED = "enrollment.cancelled";

    @Bean
    public Jackson2JsonMessageConverter enrollmentMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter enrollmentMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(enrollmentMessageConverter);
        return template;
    }
}
