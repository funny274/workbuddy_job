package com.example.fileimport.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池与事务模板配置。
 *
 * <h3>为什么用虚拟线程（JDK 21）？</h3>
 * 文件采集是典型的 <b>IO 密集型</b>任务：读磁盘、写数据库，CPU 大部分时间在等。
 * 传统平台线程模型下，200 个文件就要 200 个平台线程（约 200MB 栈内存 + 大量上下文切换），
 * 而虚拟线程的内存开销是 KB 级、切换由 JVM 管理，可以放心"一个文件一个线程"。
 * <p>
 * 但要注意：<b>虚拟线程不代表可以无限并发</b>。真正的瓶颈在下游——
 * 数据库连接池只有 20 个连接时，开 1000 个虚拟线程只会让请求排更长队。
 * 所以框架用了双重限流：
 * <ol>
 *   <li>虚拟线程池负责"承载"并发</li>
 *   <li>任务级 {@link Semaphore} 负责"限制"并发（见 AbstractFileImportTask）</li>
 * </ol>
 * 这个区分是很多团队会踩的坑：以为换成虚拟线程就能解决吞吐问题，
 * 结果下游数据库先扛不住了。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableScheduling
public class ThreadPoolConfig {

    @Value("${app.thread-pool.core-size:0}")
    private int coreSize;

    /**
     * 文件处理执行器。
     * <p>
     * 用 {@code newVirtualThreadPerTaskExecutor()} 创建"每任务一个虚拟线程"的执行器：
     * 提交多少任务就创建多少虚拟线程，无需关心线程池大小。
     */
    @Bean(destroyMethod = "close")
    public ExecutorService taskExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                // 命名线程便于 jstack / 火焰图定位问题，这是生产环境的基本要求
                return Thread.ofVirtual()
                        .name("file-import-worker-" + seq.getAndIncrement())
                        .unstarted(r);
            }
        };
        log.info("初始化文件处理执行器: 虚拟线程模式（每任务一虚拟线程）");
        return Executors.newThreadPerTaskExecutor(factory);
    }

    /**
     * 事务模板。
     * <p>
     * 直接注入 TransactionTemplate 而不是到处写 {@code @Transactional}，原因：
     * <ul>
     *   <li>批量写入发生在<b>虚拟线程</b>里，而 {@code @Transactional} 依赖 ThreadLocal
     *       传播上下文，跨线程不生效。用编程式事务更可靠、更直观。</li>
     *   <li>事务边界需要精确控制到"一批"，而不是"一个方法调用"。</li>
     * </ul>
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        // 超时保护：单批写入超过 60 秒说明有严重问题，主动回滚释放连接，
        // 避免慢 SQL 把连接池占满引发雪崩
        template.setTimeout(60);
        return template;
    }
}
