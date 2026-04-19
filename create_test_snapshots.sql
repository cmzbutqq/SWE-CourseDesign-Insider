-- 创建过去1小时的指标快照数据
-- 每10分钟一条数据
SET @now = NOW();

INSERT INTO metrics_snapshots (timestamp, total_nodes, online_nodes, offline_nodes, warning_nodes, total_services, healthy_services, abnormal_services, unresolved_alerts)
VALUES
(@now - INTERVAL 60 MINUTE, 2, 2, 0, 0, 4, 4, 0, 0),
(@now - INTERVAL 50 MINUTE, 2, 2, 0, 0, 4, 4, 0, 0),
(@now - INTERVAL 40 MINUTE, 2, 2, 0, 0, 4, 4, 0, 0),
(@now - INTERVAL 30 MINUTE, 2, 2, 0, 0, 4, 4, 0, 0),
(@now - INTERVAL 20 MINUTE, 2, 2, 0, 0, 4, 4, 0, 0),
(@now - INTERVAL 10 MINUTE, 2, 2, 0, 0, 4, 4, 0, 0),
(@now, 2, 2, 0, 0, 4, 4, 0, 0);

SELECT COUNT(*) as snapshot_count FROM metrics_snapshots;
