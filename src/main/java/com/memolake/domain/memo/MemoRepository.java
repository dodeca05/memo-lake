package com.memolake.domain.memo;

import com.memolake.domain.topic.Topic;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoRepository extends JpaRepository<Memo, Long> {

    Optional<Memo> findByTopicAndName(Topic topic, String name);

    boolean existsByTopicAndName(Topic topic, String name);

    /**
     * Loads the memo with a row lock ({@code SELECT ... FOR UPDATE}) held until the transaction ends.
     * Every content change goes through this method, so writes to the same memo are serialized.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Memo m where m.topic = :topic and m.name = :name")
    Optional<Memo> findForUpdate(@Param("topic") Topic topic, @Param("name") String name);

    /**
     * Memo names and timestamps of a topic, newest first. The content column is not selected.
     */
    @Query(value = """
            select new com.memolake.domain.memo.MemoSummary(m.name, m.updatedAt)
            from Memo m
            where m.topic = :topic
            order by m.updatedAt desc, m.name asc
            """,
            countQuery = "select count(m) from Memo m where m.topic = :topic")
    Page<MemoSummary> findSummaries(@Param("topic") Topic topic, Pageable pageable);
}
