package com.example.fileimport.framework.core;

import com.example.fileimport.framework.exception.FileImportDataException;
import com.example.fileimport.framework.exception.FileImportRetryableException;
import com.example.fileimport.framework.support.FileDispatcher;
import com.example.fileimport.framework.support.JdbcBatchWriter;
import com.example.fileimport.framework.support.RetryTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <h2>文件采集任务的抽象基类 —— 整个框架的"模板"</h2>
 *
 * <p>这个类把"文件采集入库"的通用流程固化下来，新增任务时你<b>不需要改这个类</b>，
 * 只需要继承它并实现三个方法：{@link #taskCode()}、{@link #createHandler()}
 * 和 {@link #createBatchWriter(TaskContext)}。
 *
 * <p><b>为什么本类要 implements TaskHandler？</b>
 * 因为子类常需要覆写 {@code onFileSuccess} / {@code onFileFailure} 这类回调钩子。
 * 如果基类不实现 {@code TaskHandler}，子类里的 {@code @Override} 就会"无物可覆写"而编译失败
 * （报"方法不会覆盖或实现超类型的方法"）——这是个很容易踩的坑。
 * 基类实现该接口后，回调钩子有了明确的覆写目标，框架也统一通过 {@link #createHandler()}
 * 取业务处理器，职责清晰：<b>基类管流程与回调，Handler 管行级转换。</b>
 *
 * <h3>框架固化的执行流程（模板方法）</h3>
 * <pre>
 *  1. 扫描目录，按通配符匹配文件
 *  2. 过滤：文件锁检测 → 静默期检测 → 大小上限检测 → 幂等检测
 *  3. 用 Semaphore 限流，控制同时在处理的文件数（保护数据库）
 *  4. 每个文件交给一个虚拟线程处理
 *  5. 处理：加分布式文件锁 → 流式读取 → 逐行交给 Handler → 攒批入库 → 刷尾批
 *  6. 成功：原子移动到备份目录（按日期分目录）
 *     失败：移动到错误目录，保留现场
 *  7. 汇总统计，写执行日志表，超阈值告警
 * </pre>
 *
 * <h3>为什么这样设计？</h3>
 * <ul>
 *   <li><b>模板方法模式</b>：流程标准化，避免每个任务各写一套、各有各的坑。
 *       新人照着已有任务复制，天然就继承了全部最佳实践（限流、重试、幂等、原子移动）。</li>
 *   <li><b>虚拟线程</b>：文件处理是典型的 IO 密集型（读磁盘 + 写网络），
 *       JDK 21 虚拟线程让"一个文件一个线程"这种朴素模型不再有资源代价。</li>
 *   <li><b>信号量限流</b>：并发不是越高越好。文件数多但数据库连接有限，
 *       Semaphore 保证同时打向 DB 的压力是可控的。</li>
 * </ul>
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractFileImportTask implements TaskHandler {

    private static final DateTimeFormatter DATE_DIR_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BATCH_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 用 Spring 的字段注入而非构造器注入，是为了让子类不必在自己的构造器里
     * 反复转发这些框架依赖 —— 新增任务时少写 5 行样板代码。
     * （如果偏好构造器注入，可改为 protected 构造器 + super(...)，见 README 说明）
     */
    @Autowired
    protected FileDispatcher fileDispatcher;

    @Autowired
    protected RetryTemplate retryTemplate;

    @Autowired
    protected TaskExecutionRecorder executionRecorder;

    /** 处理文件的虚拟线程执行器，由框架统一提供并管理生命周期 */
    @Autowired
    protected ExecutorService taskExecutor;

    // ==================== 子类必须实现的方法 ====================

    /**
     * 任务编码，需与配置文件中的 {@code taskCode} 一致。
     * <p>框架据此把配置路由到本任务实例。
     * <p>本方法覆写自 {@link TaskHandler#taskCode()}，因此访问修饰符为 {@code public}。
     */
    @Override
    public abstract String taskCode();

    /**
     * 创建业务处理器 —— <b>新增任务时唯一需要写业务逻辑的地方</b>。
     * <p>通常直接返回一个 Spring 注入的 Handler Bean。
     */
    protected abstract TaskHandler createHandler();

    /**
     * 创建批量写入器。子类实现，指定写入哪张表、如何绑定字段。
     */
    protected abstract JdbcBatchWriter createBatchWriter(TaskContext ctx);

    // ==================== 回调钩子的默认实现 ====================
    // 这些方法覆写 TaskHandler 的接口默认方法，默认直接转发给业务 Handler，
    // 因此子类<b>可以不覆写</b>；需要时再覆写即可，且一定有明确的覆写目标。

    /** 默认转发给业务 Handler，子类可覆写以实现额外逻辑 */
    @Override
    public void onFileSuccess(String fileName, FileStat stat, TaskContext ctx) {
        TaskHandler handler = createHandler();
        if (handler != null) {
            handler.onFileSuccess(fileName, stat, ctx);
        }
    }

    /** 默认转发给业务 Handler，子类可覆写 */
    @Override
    public void onFileFailure(String fileName, Throwable cause, TaskContext ctx) {
        TaskHandler handler = createHandler();
        if (handler != null) {
            handler.onFileFailure(fileName, cause, ctx);
        }
    }

    // ==================== 子类可选覆写的钩子 ====================

    /**
     * 任务执行前的业务校验（可选）。
     * <p>例如：检查下游系统是否可用、检查今天是否工作日（节假日不跑）。
     *
     * @return false 表示跳过本次执行
     */
    protected boolean beforeExecute(TaskContext ctx) {
        return true;
    }

    /** 任务执行后的收尾（可选）。无论成功失败都会调用 */
    protected void afterExecute(TaskContext ctx, TaskResult result) {
        // 默认空实现
    }

    // ==================== 框架主流程 ====================

    /**
     * 任务执行入口。<b>由调度层（ElasticJob 或本地 @Scheduled）调用，子类不要覆写。</b>
     * <p>
     * 加了 final 是有意为之：执行流程的稳定性由框架保证，
     * 子类如果需要一个"不同的流程"，说明应该抽象出新的基类，而不是在这里打补丁。
     *
     * @param ctx 任务上下文（由配置构建）
     * @return 执行统计
     */
    public final TaskResult execute(TaskContext ctx) {
        // 补齐运行时信息
        LocalDateTime start = LocalDateTime.now();
        ctx.setStartTime(start);
        ctx.setBatchNo(start.format(BATCH_NO_FMT) + "-" + Integer.toHexString(System.identityHashCode(this)));
        ctx.setInstanceId(executionRecorder.currentInstanceId());

        TaskResult result = new TaskResult();
        long startMillis = System.currentTimeMillis();

        log.info("========== 任务[{}]开始执行, batchNo={}, instance={} ==========",
                ctx.getTaskCode(), ctx.getBatchNo(), ctx.getInstanceId());

        try {
            // ---- Step 0: 前置校验，快速失败 ----
            if (!beforeExecute(ctx)) {
                log.info("任务[{}]前置校验未通过，跳过本次执行", ctx.getTaskCode());
                return result;
            }

            // ---- Step 1: 扫描待处理文件 ----
            List<Path> files = scanFiles(ctx);
            result.getScannedFiles().add(files.size());

            if (files.isEmpty()) {
                log.info("任务[{}]无待处理文件，本次执行结束", ctx.getTaskCode());
                return result;
            }
            log.info("任务[{}]扫描到 {} 个待处理文件", ctx.getTaskCode(), files.size());

            // ---- Step 2: 解析业务处理器 ----
            TaskHandler handler = createHandler();

            // ---- Step 3: 并发处理（信号量限流 + 虚拟线程）----
            processFilesConcurrently(files, handler, ctx, result);

        } catch (Exception e) {
            // 框架级异常（扫描失败、目录不可访问等）：整体标记失败，但不再向上抛，
            // 避免 ElasticJob 持续重试造成雪崩。异常详情落库 + 告警。
            log.error("任务[{}]执行发生框架级异常, batchNo={}", ctx.getTaskCode(), ctx.getBatchNo(), e);
            result.getFailedFiles().add(filesSafeSize(result));
            result.addError(TaskResult.FileError.builder()
                    .fileName("-")
                    .stage("FRAMEWORK")
                    .message(truncate(e.getMessage()))
                    .build());
        } finally {
            result.setCostMillis(System.currentTimeMillis() - startMillis);
            // 无论成败都要收尾并记录，否则运维看不到失败趋势
            try {
                afterExecute(ctx, result);
            } catch (Exception e) {
                log.error("任务[{}] afterExecute 执行异常", ctx.getTaskCode(), e);
            }
            executionRecorder.record(ctx, result);
            log.info("========== 任务[{}]执行结束, {} ==========", ctx.getTaskCode(), result.summary());
        }
        return result;
    }

    /**
     * 扫描目录并过滤出真正可以处理的文件。
     * <p>过滤链顺序很重要，从便宜的判断到昂贵的判断，尽早短路。
     */
    private List<Path> scanFiles(TaskContext ctx) {
        List<Path> candidates = fileDispatcher.scan(ctx);
        List<Path> ready = new ArrayList<>(candidates.size());
        for (Path path : candidates) {
            try {
                if (fileDispatcher.isReadyToProcess(path, ctx)) {
                    ready.add(path);
                } else {
                    log.debug("文件[{}]暂不满足处理条件（未过静默期/正在被写/被其他节点占用），本轮跳过", path);
                }
            } catch (Exception e) {
                // 单个文件的检测异常不应影响其他文件
                log.warn("文件[{}]就绪检测异常，本轮跳过: {}", path, e.getMessage());
            }
        }
        return ready;
    }

    /**
     * 并发处理文件列表。
     * <p>
     * 关键设计：
     * <ul>
     *   <li><b>Semaphore 限流</b>：并发数由配置决定，而不是"有多少文件就开多少线程"。</li>
     *   <li><b>虚拟线程</b>：提交到虚拟线程池，阻塞在 IO 上不占平台线程。</li>
     *   <li><b>异常隔离</b>：一个文件失败不影响其他文件（catch 在单个任务内部）。</li>
     *   <li><b>失败快速收敛</b>：不可重试异常立即放弃该文件，不拖累整批。</li>
     * </ul>
     */
    private void processFilesConcurrently(List<Path> files, TaskHandler handler,
                                          TaskContext ctx, TaskResult result) {
        int concurrency = Math.max(1, ctx.getConcurrency());
        // 公平模式：避免大文件长期饥饿（文件大小差异大时很重要）
        Semaphore semaphore = new Semaphore(concurrency, true);

        List<CompletableFuture<Void>> futures = new ArrayList<>(files.size());
        for (Path file : files) {
            // 在提交线程里先 acquire，形成背压：
            // 上游扫描出的文件不会一次性全部堆进队列，内存占用可控。
            semaphore.acquireUninterruptibly();

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    processOneFile(file, handler, ctx, result);
                } finally {
                    semaphore.release();
                }
            }, taskExecutor);
            futures.add(future);
        }

        // 等待全部完成。这里用 allOf + join 而非 for + join，
        // 语义更清晰；异常已在 processOneFile 内部消化，故 join 不会抛业务异常。
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 处理单个文件 —— 全流程的核心，包含幂等、重试、原子移动。
     */
    private void processOneFile(Path file, TaskHandler handler, TaskContext ctx, TaskResult result) {
        String fileName = file.getFileName().toString();
        long fileStart = System.currentTimeMillis();

        // ---- 幂等防护：抢分布式文件锁，防止多节点重复处理同一文件 ----
        // 这是多机部署下的必备动作。假如没有它，同一份文件会被两台机器各导入一遍，
        // 下游对账数据直接翻倍——这是生产事故的高发点。
        if (!fileDispatcher.tryLock(file, ctx)) {
            log.info("文件[{}]已被其他节点锁定，本节点跳过", fileName);
            result.getSkippedFiles().increment();
            return;
        }

        try {
            log.info("开始处理文件[{}], 大小={}字节", fileName, Files.size(file));

            // ---- 带重试的文件处理 ----
            FileStat fileStat = retryTemplate.execute(
                    ctx,
                    // 主逻辑
                    () -> doProcessFile(file, handler, ctx, result),
                    // 重试判定：不可重试异常直接放弃，不再浪费时间
                    e -> e instanceof FileImportRetryableException
                            || (e.getCause() instanceof FileImportRetryableException)
            );

            // ---- 成功：原子移动到备份目录 ----
            Path target = fileDispatcher.moveToBackup(file, ctx);
            result.getSuccessFiles().increment();
            result.getTotalRows().add(fileStat.totalRows());
            result.getInsertedRows().add(fileStat.insertedRows());
            result.getDirtyRows().add(fileStat.dirtyRows());

            log.info("文件[{}]处理成功: 解析={}行, 入库={}行, 脏数据={}行, 耗时={}ms, 已归档至[{}]",
                    fileName, fileStat.totalRows(), fileStat.insertedRows(), fileStat.dirtyRows(),
                    System.currentTimeMillis() - fileStart, target);

            // 业务回调
            safeCallback(() -> handler.onFileSuccess(fileName, fileStat, ctx));

        } catch (Exception e) {
            // ---- 失败：移动到错误目录，保留现场 ----
            result.getFailedFiles().increment();
            result.addError(TaskResult.FileError.builder()
                    .fileName(fileName)
                    .stage(classifyStage(e))
                    .message(truncate(e.getMessage()))
                    .build());

            Path errorTarget = null;
            try {
                errorTarget = fileDispatcher.moveToError(file, ctx, e);
            } catch (Exception moveEx) {
                // 连移动都失败（磁盘满/权限问题），只能告警，文件留在原地下一轮重试
                log.error("文件[{}]移动至错误目录失败，文件将保留原目录待下轮重试", fileName, moveEx);
            }

            log.error("文件[{}]处理失败，已移至[{}]: {}", fileName, errorTarget, e.getMessage(), e);

            safeCallback(() -> handler.onFileFailure(fileName, e, ctx));
        } finally {
            // 释放文件锁。放在 finally 保证异常时也能释放，否则文件会永久卡死
            fileDispatcher.unlock(file, ctx);
        }
    }

    /**
     * 真正读取并处理文件内容。
     * <p>
     * <b>内存控制要点</b>：全程流式，不把文件读进内存。
     * 无论文件是 1MB 还是 10GB，堆内存占用都只与 batchSize 相关（O(batchSize)）。
     * 这是能处理"很大数据量"的根本原因。
     */
    private FileStat doProcessFile(Path file, TaskHandler handler,
                                               TaskContext ctx, TaskResult result) throws Exception {
        Charset charset = ctx.getCharset();
        JdbcBatchWriter writer = writerFor(ctx);

        long totalRows = 0;
        long insertedRows = 0;
        long dirtyRows = 0;

        // try-with-resources 保证任何异常路径下文件句柄都被关闭，避免 fd 泄漏
        try (InputStream in = Files.newInputStream(file);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, charset),
                     // 1MB 缓冲：默认 8KB 在百万行场景下会导致大量系统调用
                     1024 * 1024)) {

            // ---- 处理表头 ----
            if (ctx.isSkipHeader()) {
                String header = reader.readLine();
                log.debug("文件[{}]表头: {}", file.getFileName(), header);
            }

            // BatchBuffer 每个文件独享，天然线程封闭
            BatchBuffer buffer = new BatchBuffer(ctx.getBatchSize(), writer, ctx);

            String line;
            long lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                totalRows++;

                // 跳过空行：上游导出常带空行，不跳过会污染脏数据统计
                if (line.isBlank()) {
                    continue;
                }

                try {
                    if (handler.useWholeFileMode()) {
                        // 整文件模式不走逐行，这里不会进来（已在下方分支处理）
                        throw new IllegalStateException("wholeFileMode 不应走到逐行分支");
                    }
                    handler.handleLine(lineNo, line, buffer, ctx);
                } catch (Exception rowEx) {
                    // ---- 单行失败不中断整个文件 ----
                    // 这是"数据量可能很多"场景下的关键取舍：
                    // 一行脏数据让整个文件回滚，会导致反复重试且永远成功不了。
                    // 正确做法是记录脏数据行号，继续处理，最后由脏数据率决定是否告警。
                    dirtyRows++;
                    buffer.markDirty();
                    // 只在最初几条打印完整堆栈，避免日志被刷爆（日志本身也是 IO 瓶颈）
                    if (dirtyRows <= 5) {
                        log.warn("文件[{}]第 {} 行解析失败: {}", file.getFileName(), lineNo, rowEx.getMessage());
                    } else if (dirtyRows == 6) {
                        log.warn("文件[{}]脏数据超过 5 条，后续相同问题不再逐条打印", file.getFileName());
                    }
                    // 如果缓冲区已经有数据，先提交掉，避免脏数据影响已解析的好数据
                    if (buffer.pendingSize() > 0) {
                        buffer.flush();
                    }
                }
            }

            // 整文件模式：一次性交给 Handler 自行处理（不推荐用于大文件）
            if (handler.useWholeFileMode()) {
                handler.handleFile(reader, result, ctx);
                return new FileStat(totalRows, result.getInsertedRows().sum(), 0);
            }

            // ---- 刷尾批：不足 batchSize 的最后一批必须显式提交 ----
            // 这是最容易漏的一步，漏了就会丢数据，而且因为数据量少很难被发现。
            buffer.flush();
            insertedRows = buffer.getCommittedCount();
            dirtyRows = buffer.getDirtyCount();

        }

        return new FileStat(totalRows, insertedRows, dirtyRows);
    }

    /** 取该任务对应的批量写入器（当前直接委托给 createBatchWriter，保留此方法作为扩展点） */
    protected JdbcBatchWriter writerFor(TaskContext ctx) {
        return createBatchWriter(ctx);
    }

    // ==================== 工具方法 ====================

    /** 回调异常吞掉，不能因为"发通知失败"影响主流程 */
    private void safeCallback(Runnable callback) {
        try {
            callback.run();
        } catch (Exception e) {
            log.warn("业务回调执行异常（已忽略，不影响主流程）: {}", e.getMessage());
        }
    }

    /** 按异常类型归类，便于告警时一眼看出问题性质 */
    private String classifyStage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof FileImportDataException) {
            return "DATA";
        }
        if (root instanceof FileImportRetryableException) {
            return "RETRY_EXHAUSTED";
        }
        if (root instanceof java.sql.SQLException) {
            return "DATABASE";
        }
        if (root instanceof IOException) {
            return "IO";
        }
        return "UNKNOWN";
    }

    private long filesSafeSize(TaskResult result) {
        return result.getScannedFiles().sum();
    }

    /** 异常消息截断：错误目录的文件名/DB 字段都有长度限制，超长会二次报错 */
    private String truncate(String msg) {
        if (msg == null) {
            return "unknown";
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
