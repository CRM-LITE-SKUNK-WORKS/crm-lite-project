package com.crm.messaging.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

    boolean existsByMessageIdAndConsumerGroup(String messageId, String consumerGroup);

    long countByConsumerGroup(String consumerGroup);
}
