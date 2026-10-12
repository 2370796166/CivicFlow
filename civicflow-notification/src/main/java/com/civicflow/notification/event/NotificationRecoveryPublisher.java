package com.civicflow.notification.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.notification.entity.NotificationRecordEntity;
import com.civicflow.notification.mapper.NotificationRecordMapper;
import com.civicflow.notification.service.NotificationStatus;
import com.civicflow.notification.service.impl.NotificationDeliveryService;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "civicflow.notification.recovery-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationRecoveryPublisher {
    private final NotificationDeliveryService delivery;
    private final NotificationRecordMapper records;
    private final RabbitTemplate rabbit;

    public NotificationRecoveryPublisher(
            NotificationDeliveryService delivery,
            NotificationRecordMapper records,
            RabbitTemplate rabbit) {
        this.delivery = delivery;
        this.records = records;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${civicflow.notification.recovery-delay-ms:60000}")
    public void recover() {
        delivery.recoverStale();
        for (NotificationRecordEntity record :
                records.selectList(
                        Wrappers.<NotificationRecordEntity>lambdaQuery()
                                .in(
                                        NotificationRecordEntity::getStatus,
                                        NotificationStatus.PENDING.name(),
                                        NotificationStatus.FAILED.name())
                                .lt(NotificationRecordEntity::getAttempts, 3)
                                .orderByAsc(NotificationRecordEntity::getUpdatedAt)
                                .last("LIMIT 50"))) {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            rabbit.send(
                    "",
                    NotificationMessaging.IN_APP_QUEUE,
                    new Message(
                            record.getEventJson().getBytes(StandardCharsets.UTF_8), properties));
        }
    }
}
