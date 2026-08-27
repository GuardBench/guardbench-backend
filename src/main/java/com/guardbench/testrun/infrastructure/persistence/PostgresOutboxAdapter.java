package com.guardbench.testrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;

@Repository
class PostgresOutboxAdapter implements OutboxPort {

    private final JdbcTemplate jdbcTemplate;

    PostgresOutboxAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(OutboxEventRecord event) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_event (event_id, event_type, schema_version, payload, deduplication_key, status, created_at, published_at)
                VALUES (?::uuid, ?, ?, ?::jsonb, ?, ?, ?, ?)
                ON CONFLICT (deduplication_key) DO NOTHING
                """,
                event.eventId().toString(),
                event.eventType(),
                event.schemaVersion(),
                event.payload(),
                event.deduplicationKey(),
                event.status(),
                Timestamp.from(event.createdAt()),
                event.publishedAt() == null ? null : Timestamp.from(event.publishedAt())
        );
    }

    @Override
    public List<OutboxEventRecord> findPendingBatch(int batchSize) {
        return jdbcTemplate.query(
                """
                SELECT event_id, event_type, schema_version, payload, deduplication_key, status, created_at, published_at
                FROM outbox_event
                WHERE status = 'PENDING'
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """,
                (rs, rowNum) -> mapRow(rs),
                batchSize
        );
    }

    @Override
    public void markPublished(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return;
        }
        String placeholders = eventIds.stream().map(id -> "?::uuid").collect(java.util.stream.Collectors.joining(","));
        Object[] args = eventIds.stream().map(UUID::toString).toArray();
        jdbcTemplate.update(
                """
                UPDATE outbox_event
                SET status = 'PUBLISHED', published_at = clock_timestamp()
                WHERE event_id IN (%s) AND status = 'PENDING'
                """.formatted(placeholders),
                args
        );
    }

    private static OutboxEventRecord mapRow(ResultSet rs) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new OutboxEventRecord(
                UUID.fromString(rs.getString("event_id")),
                rs.getString("event_type"),
                rs.getInt("schema_version"),
                rs.getString("payload"),
                rs.getString("deduplication_key"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                publishedAt == null ? null : publishedAt.toInstant()
        );
    }
}
