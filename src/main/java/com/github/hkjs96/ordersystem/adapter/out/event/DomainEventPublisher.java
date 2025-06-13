package com.github.hkjs96.ordersystem.adapter.out.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 도메인 이벤트를 스프링 이벤트로 발행
 */
@Component
public class DomainEventPublisher {
    private final ApplicationEventPublisher springPublisher;
    public DomainEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }
    public void publish(Object event) {
        springPublisher.publishEvent(event);
    }
}