package com.scut.monitoring.backend.repository;

import com.scut.monitoring.backend.model.MetricsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MetricsSnapshotRepository extends JpaRepository<MetricsSnapshot, Long> {

    /**
     * 查询指定时间范围内的指标快照，按时间升序排列
     */
    @Query("SELECT m FROM MetricsSnapshot m WHERE m.timestamp >= :startTime AND m.timestamp <= :endTime ORDER BY m.timestamp ASC")
    List<MetricsSnapshot> findByTimestampRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * 查询最近的N条记录
     */
    @Query(value = "SELECT * FROM metrics_snapshots ORDER BY timestamp DESC LIMIT :limit", nativeQuery = true)
    List<MetricsSnapshot> findLatestSnapshots(@Param("limit") int limit);

    /**
     * 删除指定时间前的快照数据（用于数据清理）
     */
    @Query("DELETE FROM MetricsSnapshot m WHERE m.timestamp < :cutoffTime")
    void deleteOlderThan(@Param("cutoffTime") Instant cutoffTime);
}
