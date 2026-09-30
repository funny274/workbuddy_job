package com.example.fileimport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务配置绑定类。
 * <p>
 * <b>这是"新增任务只改配置"的落点。</b> {@code application.yml} 里
 * {@code app.task.tasks.<TASK_CODE>:} 下的内容会被绑定到 {@link TaskConfig}。
 * <p>
 * 用 {@code Map<String, TaskConfig>} 而非定长字段，是为了让任务数量完全由配置决定——
 * 加任务不需要改 Java 代码，这是可扩展性的关键设计。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.task")
public class TaskProperties {

    /** 任务编码 -> 任务配置。使用 LinkedHashMap 保持 yml 中的书写顺序，便于人工核对 */
    private Map<String, TaskConfig> tasks = new LinkedHashMap<>();

    /**
     * 单个任务的配置项。
     * <p>所有字段都给了默认值，因此新增任务时<b>最少只需配 sourceDirs / backupDir / cron</b>，
     * 其余走默认即可。这是"让人能快速照着旧任务复制"的具体实现。
     */
    @Data
    public static class TaskConfig {

        /** 任务中文名，用于日志与告警 */
        private String name;

        /** 是否启用。设为 false 可临时停用任务（不用删配置、不用改代码） */
        private boolean enabled = true;

        /** cron 表达式，6 段（秒 分 时 日 月 周）。例：0 0/5 * * * ? 表示每 5 分钟 */
        private String cron;

        /** 待采集文件目录，支持多个（多挂载点并行） */
        private List<String> sourceDirs;

        /** 文件名通配符，如 [ "ORDER_*.csv", "*.txt" ]。留空表示全部文件 */
        private List<String> filePatterns;

        /** 处理成功后的归档目录 */
        private String backupDir;

        /** 处理失败后的错误目录。不配则默认在 backupDir 后加 _error 后缀 */
        private String errorDir;

        /** 文件编码。国内存量系统常见 GBK/GB18030 */
        private String charset = "UTF-8";

        /** CSV 分隔符。制表符用 \t，竖线用 | */
        private Character delimiter = ',';

        /** 是否跳过首行表头 */
        private boolean skipHeader = true;

        /** 单文件大小上限（字节），默认 2GB。防止误投放超大文件打爆系统 */
        private long maxFileSizeBytes = 2L * 1024 * 1024 * 1024;

        /** 静默期（毫秒），默认 30 秒。文件最后修改距今需超过该值才处理，防半包 */
        private long fileQuietPeriodMillis = 30_000L;

        /** JDBC 批量提交条数。默认 1000。调优区间 500~2000 */
        private int batchSize = 1000;

        /** 单文件处理线程数（虚拟线程）。默认 4 */
        private int fetchThreads = 4;

        /** 单任务同时处理的文件数。默认 4。DB 压力大就调小 */
        private int concurrency = 4;

        /** 失败重试次数，默认 2 */
        private int retryTimes = 2;

        /** 重试基础间隔（毫秒），默认 1000，指数退避 */
        private long retryIntervalMillis = 1000L;

        /** ElasticJob 分片总数，默认 1（单机）。多机部署时设为实例数的 2~4 倍 */
        private int shardingTotalCount = 1;

        /** 业务扩展参数，透传给 Handler。例如渠道号、数据来源标识 */
        private Map<String, String> extras = new LinkedHashMap<>();
    }
}
