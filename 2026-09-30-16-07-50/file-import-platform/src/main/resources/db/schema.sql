-- ============================================================================
--  文件采集入库平台 —— 数据库建表脚本 (MySQL 8.0+)
--  执行方式: mysql -u root -p < schema.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `file_import`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `file_import`;

-- ----------------------------------------------------------------------------
-- 1. 任务配置表（可选）
--    说明：本项目示例采用 yml 配置 + 启动时加载的方式，简单直接。
--    如果你需要"不重启应用就能改 cron / 启用停用任务"，可以启用这张表，
--    配合定时刷新（如每 60 秒 reload 一次）即可实现动态配置。
--    这也是 ElasticJob 管控台之外的另一种动态配置思路。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_task_config`;
CREATE TABLE `sys_task_config`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_code`       VARCHAR(64)  NOT NULL COMMENT '任务编码，全局唯一',
    `task_name`       VARCHAR(128) NOT NULL COMMENT '任务名称',
    `cron`            VARCHAR(64)  NOT NULL COMMENT 'cron 表达式',
    `source_dirs`     VARCHAR(1024) NOT NULL COMMENT '源目录，多个用英文逗号分隔',
    `file_patterns`   VARCHAR(512) DEFAULT NULL COMMENT '文件名通配符，多个用逗号分隔',
    `backup_dir`      VARCHAR(512) NOT NULL COMMENT '备份目录',
    `error_dir`       VARCHAR(512) DEFAULT NULL COMMENT '错误文件目录',
    `charset`         VARCHAR(32)  DEFAULT 'UTF-8' COMMENT '文件编码',
    `delimiter`       VARCHAR(8)   DEFAULT ',' COMMENT '分隔符',
    `skip_header`     TINYINT(1)   DEFAULT 1 COMMENT '是否跳过表头: 1是 0否',
    `batch_size`      INT          DEFAULT 1000 COMMENT '批量提交条数',
    `concurrency`     INT          DEFAULT 4 COMMENT '并发处理文件数',
    `retry_times`     INT          DEFAULT 2 COMMENT '失败重试次数',
    `sharding_total`  INT          DEFAULT 1 COMMENT 'ElasticJob 分片总数',
    `extra_params`    JSON         DEFAULT NULL COMMENT '业务扩展参数',
    `enabled`         TINYINT(1)   DEFAULT 1 COMMENT '是否启用: 1启用 0停用',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_code` (`task_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='任务配置表';

-- 初始化一条与 application.yml 对应的配置，便于对照理解
INSERT INTO `sys_task_config` (`task_code`, `task_name`, `cron`, `source_dirs`, `file_patterns`,
                               `backup_dir`, `error_dir`, `batch_size`, `concurrency`)
VALUES ('ORDER_DETAIL_IMPORT', '订单明细文件导入', '0 0/5 * * * ?',
        'data/in/order', 'ORDER_*.csv',
        'data/backup/order', 'data/error/order', 1000, 4);


-- ----------------------------------------------------------------------------
-- 2. 任务执行日志表
--    每次调度执行落一条记录，是排查线上问题的第一手资料。
--    建议按月分表或定期归档（超过 3 个月的数据转历史表），避免表无限膨胀。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_task_exec_log`;
CREATE TABLE `sys_task_exec_log`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_code`      VARCHAR(64)  NOT NULL COMMENT '任务编码',
    `batch_no`       VARCHAR(64)  NOT NULL COMMENT '批次号（一次调度一个批次）',
    `instance_id`    VARCHAR(64)  DEFAULT NULL COMMENT '执行实例（IP:PID），多机部署定位用',
    `start_time`     DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`       DATETIME     DEFAULT NULL COMMENT '结束时间',
    `cost_millis`    BIGINT       DEFAULT 0 COMMENT '耗时（毫秒）',
    `scanned_files`  INT          DEFAULT 0 COMMENT '扫描到文件数',
    `success_files`  INT          DEFAULT 0 COMMENT '成功文件数',
    `failed_files`   INT          DEFAULT 0 COMMENT '失败文件数',
    `skipped_files`  INT          DEFAULT 0 COMMENT '跳过文件数',
    `total_rows`     BIGINT       DEFAULT 0 COMMENT '解析总行数',
    `inserted_rows`  BIGINT       DEFAULT 0 COMMENT '成功入库行数',
    `dirty_rows`     BIGINT       DEFAULT 0 COMMENT '脏数据行数',
    `status`         VARCHAR(16)  NOT NULL COMMENT '状态: SUCCESS/FAILED',
    `error_msg`      VARCHAR(2000) DEFAULT NULL COMMENT '错误摘要',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    -- 按任务+开始时间查询是最常见的运维查询模式，据此建索引
    KEY `idx_task_time` (`task_code`, `start_time`),
    KEY `idx_batch_no` (`batch_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='任务执行日志表';


-- ----------------------------------------------------------------------------
-- 3. 业务目标表 —— 订单明细
--    这是"示例任务"要写入的表。新增任务时，你需要建自己的目标表。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_order_detail`;
CREATE TABLE `t_order_detail`
(
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`      VARCHAR(64)    NOT NULL COMMENT '订单号',
    `customer_no`   VARCHAR(64)    DEFAULT NULL COMMENT '客户号',
    `product_code`  VARCHAR(64)    DEFAULT NULL COMMENT '商品编码',
    `quantity`      INT            DEFAULT NULL COMMENT '数量',
    -- 金额一律 DECIMAL，绝不用 FLOAT/DOUBLE，否则会产生对账精度差异
    `unit_price`    DECIMAL(18, 4) DEFAULT NULL COMMENT '单价',
    `amount`        DECIMAL(18, 4) DEFAULT NULL COMMENT '订单金额',
    `order_time`    DATETIME       DEFAULT NULL COMMENT '订单时间',
    `channel_code`  VARCHAR(32)    DEFAULT NULL COMMENT '渠道号（来自任务配置）',
    `batch_no`      VARCHAR(64)    DEFAULT NULL COMMENT '来源批次号（数据溯源用）',
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`),
    -- 唯一索引做幂等兜底：同一订单号+商品编码只保留一条。
    -- 即使文件被重复投放，数据库也不会产生重复数据（写入时会报 1062，被识别为不可重试错误）。
    UNIQUE KEY `uk_order_product` (`order_no`, `product_code`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_batch_no` (`batch_no`),
    KEY `idx_order_time` (`order_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细表';

-- ----------------------------------------------------------------------------
-- 4.（可选）失败文件登记表
--    比"文件移入 error 目录"更进一步：把失败信息结构化落库，
--    方便做一个"失败文件重投"的运维页面。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_failed_file`;
CREATE TABLE `sys_failed_file`
(
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_code`    VARCHAR(64)   NOT NULL COMMENT '任务编码',
    `batch_no`     VARCHAR(64)   DEFAULT NULL COMMENT '批次号',
    `file_name`    VARCHAR(512)  NOT NULL COMMENT '文件名',
    `file_path`    VARCHAR(1024) DEFAULT NULL COMMENT '失败文件归档路径',
    `file_size`    BIGINT        DEFAULT 0 COMMENT '文件大小（字节）',
    `fail_stage`   VARCHAR(32)   DEFAULT NULL COMMENT '失败阶段: DATA/IO/DATABASE/RETRY_EXHAUSTED',
    `fail_reason`  VARCHAR(2000) DEFAULT NULL COMMENT '失败原因',
    `retry_count`  INT           DEFAULT 0 COMMENT '已重试次数',
    `handle_status` VARCHAR(16)  DEFAULT 'PENDING' COMMENT '处理状态: PENDING/HANDLED/IGNORED',
    `handle_user`  VARCHAR(64)   DEFAULT NULL COMMENT '处理人',
    `handle_time`  DATETIME      DEFAULT NULL COMMENT '处理时间',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_status` (`task_code`, `handle_status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='失败文件登记表';
