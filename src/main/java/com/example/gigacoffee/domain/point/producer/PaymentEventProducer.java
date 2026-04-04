package com.example.gigacoffee.domain.point.producer;


import com.example.gigacoffee.common.model.kafka.event.PaymentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.example.gigacoffee.common.model.kafka.topic.KafkaTopic.TOPIC_PAYMENT_CONFIRMED;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentConfirmedEvent> paymentConfirmedEventKafkaTemplate;

    public void sendPaymentConfirmed(PaymentConfirmedEvent event) {
        paymentConfirmedEventKafkaTemplate.send(TOPIC_PAYMENT_CONFIRMED, event)
                .whenComplete((result, ex) -> {
                    if(ex != null) {
                        // 실패 처리
                        log.error("[Kafka] 결제 이벤트 발행 실패 - userId = {}, menuIds =- {}",
                                event.getUserId(), event.getMenuIds(), ex);
                    } else {
                        // 성공 처리
                        log.info("[Kafka] 결제 이벤트 발행 성공 - userId = {}, menuIds = {}",
                                event.getUserId(), event.getMenuIds());
                    }
                });
    }
}
