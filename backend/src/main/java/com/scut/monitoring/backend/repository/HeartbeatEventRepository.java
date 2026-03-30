package com.scut.monitoring.backend.repository;

import com.scut.monitoring.backend.model.HeartbeatEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeartbeatEventRepository extends JpaRepository<HeartbeatEvent, Long> {
}
