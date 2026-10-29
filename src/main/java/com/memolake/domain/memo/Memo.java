package com.memolake.domain.memo;

import com.memolake.domain.topic.Topic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A plain-text memo that belongs to a topic. The content is stored exactly as received.
 */
@Entity
@Table(name = "memo")
public class Memo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Incremented by Hibernate on every content change; starts at 1. */
    @Version
    @Column(name = "version", nullable = false)
    private long version = 1;

    protected Memo() {
    }

    public Memo(Topic topic, String name, String content, Instant now) {
        this.topic = topic;
        this.name = name;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void replaceContent(String newContent, Instant now) {
        this.content = newContent;
        this.updatedAt = now;
    }

    public void appendContent(String addition, Instant now) {
        this.content = this.content + addition;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Topic getTopic() {
        return topic;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
