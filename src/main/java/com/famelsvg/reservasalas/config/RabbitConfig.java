package com.famelsvg.reservasalas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "reservas";
    public static final String KEY_CREATED = "reserva.criada";
    public static final String KEY_CANCELLED = "reserva.cancelada";

    /** Durável, declarada pelo RabbitAdmin do Boot ao conectar. */
    @Bean
    TopicExchange reservationsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /** O Boot injeta este conversor no RabbitTemplate; usa o ObjectMapper da app (Instant em ISO-8601). */
    @Bean
    MessageConverter rabbitMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }
}
