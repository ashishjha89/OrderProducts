package com.orderproduct.orderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "eventid", nullable = false)
    private String eventId;

    @Column(name = "eventtype", nullable = false)
    private String eventType;

    @Column(name = "aggregatetype", nullable = false)
    private String aggregateType;

    @Column(name = "aggregateid", nullable = false)
    private String aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "createdat", nullable = false)
    private Long createdAt;

    @Column(name = "processedat")
    private Long processedAt;
}