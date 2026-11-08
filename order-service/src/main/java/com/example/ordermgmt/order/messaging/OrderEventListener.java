package com.example.ordermgmt.order.messaging;

import com.example.ordermgmt.contract.Topics;
import com.example.ordermgmt.messaging.EventDispatcher;
import com.example.ordermgmt.order.OrderSagaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer of the Order Service. Listens to the inventory and payment topics.
 *
 * <h2>Manual acknowledgement</h2>
 * Kafka tracks, per consumer group and partition, an <em>offset</em>: "everything before this has
 * been handled". The container is configured with {@code ack-mode: manual}, so the offset is only
 * committed when we call {@link Acknowledgment#acknowledge()}, <strong>after</strong> the database
 * transaction committed. If the service crashes before that, Kafka re-delivers the message after
 * restart and the idempotent processor skips it if it was in fact already handled.
 *
 * <p>If the handler throws, we do not acknowledge; the error handler retries and eventually moves
 * the record to the dead-letter topic.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final EventDispatcher dispatcher;
    private final OrderSagaHandler sagaHandler;

    public OrderEventListener(EventDispatcher dispatcher, OrderSagaHandler sagaHandler) {
        this.dispatcher = dispatcher;
        this.sagaHandler = sagaHandler;
    }

    /**
     * @param message the raw JSON event
     * @param ack     handle used to commit the offset
     */
    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS}, groupId = "order-service")
    public void onEvent(String message, Acknowledgment ack) {
        dispatcher.dispatch(message, sagaHandler::handle);
        ack.acknowledge();
        log.debug("Offset acknowledged");
    }
}
