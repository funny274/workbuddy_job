package com.example.fileimport.framework.core;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务运行上下文。
 * <p>
 * 一次任务执行（一个调度周期）对应一个 Context 实例，承载该次执行的全部可配置项。
 * 设计要点：
 * <ul>
 *   <li><b>不可变</b>：通过 Builder 构建，执行期间不修改，天然线程安全。</li>
 *   <li><b>可序列化下发</b>：所有字段都可从 DB / 配置中心 / ElasticJob 的 jobParameter 反序列化得到，
 *       因此运维改 cron、改目录、改批大小都不需要重启应用。</li>
 *   <li><b>与具体业务解耦</b>：框架层只认这里的通用字段，业务字段（如"哪个渠道的订单"）
 *       通过 {@link #extras} 透传给业务 Handler。</li>
 * </ul>
 *
 * @author platform-team
 * @since 1.0.0
 */
@Data
@Builder(toBuilder = true)
// 只挑几个关键字段进 toString，避免把 extras、目录列表全打出来导致日志冗长。
// 【易错点】@ToString 的 of 里写的字段名必须真实存在（sourceDir 是错的，正确是 sourceDirs）。
// 写错时 Lombok 只给 WARNING 不报错，字段会静默不出现在 toString 里，容易漏掉。
@ToString(of = {"taskCode", "batchNo", "sourceDirs", "filePatterns", "batchSize", "fetchThreads"})
public class TaskContext {

    // ==================== 标识 ====================

    /** 任务编码，全局唯一。对应 DB 表 sys_task_config.task_code，也是 ElasticJob 的 jobName */
    private String taskCode;

    /** 任务中文名，用于告警文案与运维界面展示 */
    private String taskName;

    /** 本次执行批次号（一次调度 = 一个批次），形如 20260930160000-2f3a1b，用于全链路日志追踪 */
    private String batchNo;

    /** 本次执行所属的应用实例标识（IP:PORT），多机部署时用于定位是哪台机器在处理 */
    private String instanceId;

    // ==================== 数据源（文件）====================

    /** 待采集文件所在目录（绝对路径）。支持多个目录，用于多挂载点/多磁盘并行 */
    private java.util.List<String> sourceDirs;

    /** 文件名通配符，如 {@code ORDER_*.csv}。支持多个模式 */
    private java.util.List<String> filePatterns;

    /** 归档目录（处理成功后文件移动到此，按 yyyyMMdd 分子目录） */
    private String backupDir;

    /** 失败文件目录（解析失败/入库失败的文件移动到此，保留现场供人工介入） */
    private String errorDir;

    /** 文件编码。国内常见 GBK 的存量系统，遇到乱码优先怀疑这里 */
    private Charset charset;

    /** CSV 分隔符，默认英文逗号。制表符传 \t */
    private Character delimiter;

    /** 是否跳过首行表头 */
    /** 是否跳过首行表头 */
    private boolean skipHeader;

    /** 单文件大小上限（字节），超过则拒绝处理并告警，防止误投放超大文件打爆内存 */
    private long maxFileSizeBytes;

    /** 文件"静默期"（毫秒）：文件最后修改时间距今需超过该值才处理，
     *  避免上游还在写、我方已经读走半截文件（经典的半包问题）。*/
    private long fileQuietPeriodMillis;

    // ==================== 性能与数据库 ====================

    /** JDBC 批量提交条数。经验值 500~2000；过大会撑爆 undo log 且单次失败回滚代价高 */
    private int batchSize;

    /** 单文件处理线程数（虚拟线程）。IO 密集可放大，但需与 batchSize、DB 连接池联动评估 */
    private int fetchThreads;

    /** 单任务并发度：同一时刻允许几个文件在被处理。数据库压力大就调小这个值 */
    private int concurrency;

    /** 失败重试次数（针对可重试异常，如数据库连接瞬断、死锁） */
    private int retryTimes;

    /** 重试间隔（毫秒），采用指数退避：interval * 2^(n-1) */
    private long retryIntervalMillis;

    // ==================== 业务扩展 ====================

    /**
     * 业务扩展参数，透传给 {@link TaskHandler}。
     * <p>例如：{@code {"tenantId":"GZ001","dataType":"ORDER_DETAIL"}}。
     * 这样新增任务时，若业务逻辑只是"换个表、换个字段映射"，
     * 完全可以复用同一个 Handler，只改配置即可。
     */
    private Map<String, String> extras;

    // ==================== 运行时信息 ====================

    /** 本批次开始时间，由框架填充 */
    private LocalDateTime startTime;

    // ==================== 便捷方法 ====================

    /** 取扩展参数，带默认值 */
    public String getExtra(String key, String defaultValue) {
        if (extras == null) {
            return defaultValue;
        }
        return extras.getOrDefault(key, defaultValue);
    }

    /** 取扩展参数，必填，缺失则抛异常（用于必须配置的场景，早失败早发现） */
    public String requireExtra(String key) {
        String v = extras == null ? null : extras.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                    "任务[" + taskCode + "]缺少必填扩展参数: " + key);
        }
        return v;
    }
}
