package com.example.fileimport.framework.schedule;

import com.example.fileimport.config.TaskProperties;
import com.example.fileimport.framework.core.TaskRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.elasticjob.api.JobConfiguration;
import org.apache.shardingsphere.elasticjob.lite.api.bootstrap.impl.ScheduleJobBootstrap;
import org.apache.shardingsphere.elasticjob.reg.base.CoordinatorRegistryCenter;
import org.apache.shardingsphere.elasticjob.reg.zookeeper.ZookeeperConfiguration;
import org.apache.shardingsphere.elasticjob.reg.zookeeper.ZookeeperRegistryCenter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * ElasticJob 装配配置。
 * <p>
 * <b>这里做的事：把 application.yml 里的任务配置，逐个翻译成 ElasticJob 的 JobConfiguration，
 * 并注册到 ZooKeeper。</b> 新增任务时代码零改动——只是在 yml 里多一段配置。
 *
 * <h3>几个关键配置项的解释（生产环境必须理解）</h3>
 * <ul>
 *   <li><b>overwrite=true</b>：以本地配置为准覆盖 ZK 上的配置。
 *       开发阶段方便；<b>生产环境建议设为 false</b>，否则每次发版都会把
 *       运维在管控台上调的参数冲掉（这是很常见的事故）。</li>
 *   <li><b>misfire=true</b>：错过执行时刻后是否补跑。文件采集场景应该补跑，
 *       因为"该采的数据"不能因为一次 GC 停顿就永久丢失。</li>
 *   <li><b>failover=true</b>：任务在某实例执行失败时，由其他实例接管重跑。
 *       需要实现类幂等——我们已经用"Redis 文件锁 + 处理完移走文件"保证了幂等。</li>
 *   <li><b>monitorExecution=true</b>：把执行轨迹上报到 ZK。
 *       <b>生产环境建议开启</b>，是排查"到底哪台机器在处理"的关键信息。</li>
 *   <li><b>shardingTotalCount</b>：总分片数。不是越大越好，等于"期望的最大并行实例数"，
 *       一般设 2~4 倍于实例数，让扩容时能平滑分摊。</li>
 * </ul>
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.schedule.mode", havingValue = "elasticjob")
public class ElasticJobConfig {

    @Value("${app.schedule.elasticjob.server-lists:127.0.0.1:2181}")
    private String serverLists;

    @Value("${app.schedule.elasticjob.namespace:file-import}")
    private String namespace;

    @Value("${app.schedule.elasticjob.base-sleep-time-ms:1000}")
    private int baseSleepTimeMs;

    @Value("${app.schedule.elasticjob.max-retries:3}")
    private int maxRetries;

    @Value("${app.schedule.elasticjob.overwrite:false}")
    private boolean overwrite;

    private final TaskProperties taskProperties;
    private final TaskRegistry taskRegistry;
    private final FileImportElasticJob job;

    /** 记录所有被 bootstrap 的任务，便于优雅停机时逐个关闭 */
    private final List<ScheduleJobBootstrap> bootstraps = new ArrayList<>();

    public ElasticJobConfig(TaskProperties taskProperties,
                            TaskRegistry taskRegistry,
                            FileImportElasticJob job) {
        this.taskProperties = taskProperties;
        this.taskRegistry = taskRegistry;
        this.job = job;
    }

    /**
     * ZooKeeper 注册中心。
     * <p>生产环境必须配置 3 个以上 ZK 节点做高可用：
     * {@code server-lists: zk1:2181,zk2:2181,zk3:2181}
     */
    @Bean(initMethod = "init")
    public CoordinatorRegistryCenter registryCenter() {
        ZookeeperConfiguration zkConfig = new ZookeeperConfiguration(serverLists, namespace);
        zkConfig.setBaseSleepTimeMilliseconds(baseSleepTimeMs);
        zkConfig.setMaxRetries(maxRetries);
        // 会话超时：过短会因网络抖动频繁触发重分片；过长则故障发现慢。60s 是较稳妥的默认值
        zkConfig.setSessionTimeoutMilliseconds(60_000);
        zkConfig.setConnectionTimeoutMilliseconds(15_000);
        log.info("[ElasticJob] 初始化 ZooKeeper 注册中心: servers={}, namespace={}", serverLists, namespace);
        return new ZookeeperRegistryCenter(zkConfig);
    }

    /**
     * 为配置中每个启用的任务创建并启动一个 ElasticJob ScheduleJobBootstrap。
     * <p>这个方法代替了"每个任务写一个 @Bean"的样板代码，
     * 是"配置驱动"设计在调度层的体现。
     */
    @Bean
    public List<ScheduleJobBootstrap> jobBootstraps(CoordinatorRegistryCenter registryCenter) {
        for (String taskCode : taskRegistry.getAllTaskCodes()) {
            TaskProperties.TaskConfig config = taskProperties.getTasks().get(taskCode);
            if (config == null || !config.isEnabled()) {
                continue;
            }

            JobConfiguration jobConfig = JobConfiguration.newBuilder(taskCode, config.getShardingTotalCount())
                    .cron(config.getCron())
                    // 把 taskCode 通过 jobParameter 传给执行器，实现多任务复用同一执行类
                    .jobParameter("taskCode=" + taskCode)
                    .shardingItemParameters("")             // 使用下标取模分片，无需固定参数
                    .overwrite(overwrite)                   // 生产建议 false，避免覆盖运维调参
                    .misfire(true)                          // 错过则补跑
                    .failover(true)                         // 实例故障时其他实例接管
                    .monitorExecution(true)                 // 上报执行轨迹，便于定位
                    .build();

            ScheduleJobBootstrap bootstrap = new ScheduleJobBootstrap(registryCenter, job, jobConfig);
            bootstrap.schedule();
            bootstraps.add(bootstrap);

            log.info("[ElasticJob] 任务[{}]已启动: cron={}, 分片数={}",
                    taskCode, config.getCron(), config.getShardingTotalCount());
        }
        return bootstraps;
    }
}
