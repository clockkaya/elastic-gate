# High-Performance Elastic Gate & Shading

本项目是一个高性能的告警数据处理与分片存储系统，主要应用于安全中心（Safety Center）的实时数据消费、统计与持久化。

## 🚀 技术亮点

### 1. 高性能实时数据容器 (ElasticBoundedPrioritySet)
针对安全中心实时播报接口，设计并实现了一个**线程安全的有界优先级集合**：
- **双重清理策略**：
    - **弹性收缩 (Elastic Shrink)**：当容器接近上限时，通过轻量级锁控制的多线程任务异步移除低优先级元素，保证系统响应。
    - **强制截断 (Truncate Clean)**：当瞬间流量激增超过耐受极限时，采用直接截断策略，防止 OOM。
- **性能优化**：底层基于 `ConcurrentSkipListSet`，在不牺牲线程安全的前提下，实现了 $O(\log n)$ 的插入与删除效率。

### 2. ShardingSphere 与 Nacos 的深度集成
针对安全日志数据量大的特点，实现了基于时间的动态分表架构：
- **动态 URL Provider**：自定义 `NacosCloudDriverURLProvider`，通过 SPI 机制扩展 ShardingSphere，使其具备从 Nacos 动态加载分片规则并手动替换环境变量（如数据库凭据）的能力。
- **自动建表机制**：扩展 `StandardShardingAlgorithm`，在 CRUD 路由过程中实现“按需自动创建物理表”，彻底解决了运维手动建表的痛点。
- **预加载优化**：实现 `ApplicationRunner`，在容器启动时预刷新分片元数据缓存，避免首次访问时的延迟抖动。

### 3. 多维统计与 Redis 计数器
- 利用 Redis `HINCRBY` 原子性操作实现高并发下的“五分钟步长”点统计。
- 采用“异步批量持久化”策略，将 Redis 中的热点数据定时落库至 MySQL，平衡了实时查询性能与数据持久化需求。

## 🛠 技术栈
- **Core**: Spring Boot, MyBatis Plus, Dubbo
- **Middleware**: Nacos (Config), Kafka (MQ), Redis (Cache), MySQL
- **Sharding**: ShardingSphere-JDBC (Class-based Algorithm)
- **Utilities**: Hutool, Fastjson2, Guava