package com.memolake.domain.topic;

import com.memolake.common.exception.MemoLakeException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findByName(String name);

    boolean existsByName(String name);

    default Topic getByName(String name) {
        return findByName(name).orElseThrow(() -> MemoLakeException.topicNotFound(name));
    }

    /**
     * Topics ordered by their most recent memo modification (topics without memos last, then by name).
     * The latest time is computed per topic with an index lookup on (topic_id, updated_at);
     * memo rows (and their content) are never loaded.
     */
    @Query(value = """
            select new com.memolake.domain.topic.TopicSummary(
                t.name,
                t.description,
                (select max(m.updatedAt) from Memo m where m.topic = t))
            from Topic t
            order by (select max(m2.updatedAt) from Memo m2 where m2.topic = t) desc nulls last, t.name asc
            """,
            countQuery = "select count(t) from Topic t")
    Page<TopicSummary> findSummaries(Pageable pageable);
}
