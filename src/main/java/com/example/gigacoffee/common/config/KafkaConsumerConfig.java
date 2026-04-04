package com.example.gigacoffee.common.config;

import com.example.gigacoffee.common.model.kafka.event.PaymentConfirmedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

import static com.example.gigacoffee.common.model.kafka.consumerGroup.ConsumerGroup.DATA_PLATFORM_CONSUMER_GROUP;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseConsumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return props;
    }

    private ConsumerFactory<String, PaymentConfirmedEvent> buildConsumerFactory(String groupId) {
        JacksonJsonDeserializer<PaymentConfirmedEvent> deserializer = new JacksonJsonDeserializer<>(PaymentConfirmedEvent.class);

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(groupId),
                new StringDeserializer(),
                deserializer
        );
    }

    private ConcurrentKafkaListenerContainerFactory<String, PaymentConfirmedEvent> baseKafkaListenerContainerFactory(ConsumerFactory<String, PaymentConfirmedEvent> consumerFactory, CommonErrorHandler commonErrorHandlerWithDLT) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentConfirmedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(commonErrorHandlerWithDLT);
        return factory;
    }

    @Bean
    public CommonErrorHandler commonErrorHandlerWithDLT(KafkaTemplate<String, PaymentConfirmedEvent> paymentCompletedEventKafkaTemplate) {
        // 재처리 로직도 실패한 경우 DLT 토픽으로 해당 메시지 전송
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(paymentCompletedEventKafkaTemplate);

        // 재시도 로직(재시도 2회, 총 3회, 기본값 10회)
        FixedBackOff backOff = new FixedBackOff(1000L, 2L);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConsumerFactory<String, PaymentConfirmedEvent> paymentHistoryConsumerFactory() {
        return buildConsumerFactory(DATA_PLATFORM_CONSUMER_GROUP);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentConfirmedEvent> paymentHistoryKafkaListenerContainerFactory(
            CommonErrorHandler commonErrorHandlerWithDLT
    ) {
        return baseKafkaListenerContainerFactory(paymentHistoryConsumerFactory(), commonErrorHandlerWithDLT);
    }
}
