package com.example.gigacoffee.common.kafka.consumer;

import com.example.gigacoffee.common.kafka.model.event.PaymentConfirmedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.example.gigacoffee.common.kafka.model.consumerGroup.ConsumerGroup.DATA_PLATFORM_CONSUMER_GROUP;
import static com.example.gigacoffee.common.kafka.model.topic.KafkaTopic.TOPIC_PAYMENT_CONFIRMED;

@Slf4j
@Component
public class DataPlatformConsumer {

    @KafkaListener(
            topics = TOPIC_PAYMENT_CONFIRMED,
            groupId = DATA_PLATFORM_CONSUMER_GROUP,
            containerFactory = "paymentHistoryKafkaListenerContainerFactory"
    )
    public void consume(PaymentConfirmedEvent event) {
        log.info("[Data-Platform] 결제 데이터 전송 - userId: {}, menuIds: {} - paymentAmount: {}",
                event.getUserId(),
                event.getMenuQuantities(),
                event.getPaymentAmount());
    }
}
