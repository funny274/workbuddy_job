package com.example.fileimport.framework.schedule;

import com.example.fileimport.framework.core.AbstractFileImportTask;
import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.core.TaskRegistry;
import com.example.fileimport.framework.core.TaskResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.elasticjob.api.ShardingContext;
import org.apache.shardingsphere.elasticjob.simple.job.SimpleJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ElasticJob 任务执行器 —— 生产环境的分布式调度形态。
 * <p>
 * <b>为什么选 ElasticJob 而不是 Quartz / XXL-JOB / Spring @Scheduled？</b>
 * <table border="1">
 *   <tr><th>方案</th><th>分片</th><th>弹性扩缩容</th><th>依赖</th><th>适用</th></tr>
 *   <tr><td>@Scheduled</td><td>无</td><td>无</td><td>无</td><td>单机、小规模</td></tr>
 *   <tr><td>Quartz 集群</td><td>无（靠 DB 锁选主）</td><td>弱</td><td>数据库锁表</td><td>传统单体</td></tr>
 *   <tr><td>XXL-JOB</td><td>有（路由策略）</td><td>好</td><td>独立调度中心</td><td>中大型，需运维平台</td></tr>
 *   <tr><td><b>ElasticJob</b></td><td><b>原生分片</b></td><td><b>自动重分片</b></td><td>ZooKeeper</td><td><b>需要任务级分片的场景</b></td></tr>
 * </table>
 *
 * <p><b>ElasticJob 的杀手特性：任务分片（Sharding）。</b>
 * 这正是本场景需要的——"某一时刻可能有很多份文件"，如果单机处理不完，
 * 可以起 3 个实例，ElasticJob 自动把文件分成 3 片，每台机器只处理自己那片。
 * 而且实例上下线时会<b>自动重新分片</b>（rebalance），无需人工干预。
 *
 * <h3>分片策略说明</h3>
 * 本实现采用"<b>按文件下标取模</b>"的简单分片：第 i 个文件由 {@code i % shardingTotalCount}
 * 等于 {@code shardingItem} 的实例处理。为什么不直接在扫描时按分片过滤？
 * 因为如果直接过滤，每台机器只扫描到自己那部分文件，看似省事，
 * 但一旦实例数变化，已被过滤掉的文件归属会变，容易漏处理。
 * 更稳妥的做法是"所有机器扫到全集，各自只处理自己分片的那部分"，
 * 由 Redis 文件锁兜底防止重复。这里采用后者，牺牲一点点扫描开销换取正确性。
 *
 * <h3>重要提醒：ElasticJob 通过 jobName 关联配置</h3>
 * ElasticJob 用 `jobName` 作为 ZooKeeper 上的唯一标识。同一个 jobName 在不同
 * 环境（dev/test/prod）如果共用同一个 ZK namespace，会互相覆盖配置。
 * 务必给每个环境独立的 {@code namespace}，见 application.yml。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.schedule.mode", havingValue = "elasticjob")
public class FileImportElasticJob implements SimpleJob {

    private final TaskRegistry taskRegistry;

    public FileImportElasticJob(TaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    /**
     * ElasticJob 的执行入口。
     * <p>
     * 每次触发，ElasticJob 会为每个分片调用本方法（可并行，取决于配置）。
     * 多个实例部署时，分片会自动均摊到各实例上。
     *
     * @param shardingContext 分片上下文，含当前分片序号、总分片数、任务自定义参数
     */
    @Override
    public void execute(ShardingContext shardingContext) {
        // ElasticJob 的 jobName 即 taskCode（在配置类中显式映射）
        String taskCode = resolveTaskCode(shardingContext);
        if (taskCode == null) {
            log.error("无法从分片上下文解析任务编码，jobName={}", shardingContext.getJobName());
            return;
        }

        AbstractFileImportTask task = taskRegistry.getTask(taskCode).orElse(null);
        if (task == null) {
            log.error("任务[{}]无对应实现，请检查配置", taskCode);
            return;
        }

        int shardingItem = shardingContext.getShardingItem();
        int shardingTotal = shardingContext.getShardingTotalCount();

        log.info("[ElasticJob] 任务[{}]在分片 {}/{} 上开始执行",
                taskCode, shardingItem, shardingTotal - 1);

        TaskContext ctx = taskRegistry.getContextTemplate(taskCode);
        // 把分片信息放进上下文，让框架在处理文件时按分片过滤
        ctx.getExtras().put("shardingItem", String.valueOf(shardingItem));
        ctx.getExtras().put("shardingTotal", String.valueOf(shardingTotal));

        try {
            TaskResult result = task.execute(ctx);
            log.info("[ElasticJob] 任务[{}]分片 {}/{} 执行完成: {}",
                    taskCode, shardingItem, shardingTotal - 1, result.summary());
        } catch (Exception e) {
            // 不能让异常逃逸到 ElasticJob，否则会被记为执行失败并触发重试语义，
            // 而我们已经做了更细粒度的重试与死信处理，这里只需记录。
            log.error("[ElasticJob] 任务[{}]分片 {} 执行异常", taskCode, shardingItem, e);
        }
    }

    /**
     * 从分片上下文解析 taskCode。
     * <p>ElasticJob 的 jobParameter 里带着我们写入的 taskCode，
     * 这样多个任务可以复用同一个 SimpleJob 实现类——
     * 又少写一份样板代码，新增任务时配置即可。
     */
    private String resolveTaskCode(ShardingContext shardingContext) {
        String param = shardingContext.getJobParameter();
        if (param != null && !param.isBlank()) {
            // 约定 jobParameter 格式: taskCode=ORDER_IMPORT;xxx=yyy
            for (String kv : param.split(";")) {
                String[] pair = kv.split("=", 2);
                if (pair.length == 2 && "taskCode".equals(pair[0].trim())) {
                    return pair[1].trim();
                }
            }
        }
        // 兜底：直接用 jobName（要求 jobName 与 taskCode 保持一致）
        String jobName = shardingContext.getJobName();
        if (taskRegistry.getAllTaskCodes().contains(jobName)) {
            return jobName;
        }
        List<String> all = taskRegistry.getAllTaskCodes();
        // 单任务场景下，若只有一个任务注册，直接返回它，容错更友好
        return all.size() == 1 ? all.get(0) : null;
    }
}
