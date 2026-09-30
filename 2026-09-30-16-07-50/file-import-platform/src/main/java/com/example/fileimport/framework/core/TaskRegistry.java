package com.example.fileimport.framework.core;

import com.example.fileimport.config.TaskProperties;
import com.example.fileimport.framework.exception.FileImportException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务注册中心 —— 把"配置"和"任务实现"装配到一起。
 * <p>
 * <b>设计的核心思想：配置驱动的任务装配</b>
 * <p>
 * 新增一个任务时，你要做两件事：
 * <ol>
 *   <li>在 yml（或 DB）里加一段配置，声明目录、cron、批大小等；</li>
 *   <li>写一个 {@link AbstractFileImportTask} 子类，声明 taskCode 和业务转换逻辑。</li>
 * </ol>
 * 本类负责在启动时把两者校验并关联起来，任何一边缺失都在<b>启动阶段</b>就报错，
 * 而不是等到凌晨 3 点任务该跑的时候才发现配置没配——这就是"快速失败"（Fail Fast）。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class TaskRegistry {

    private final TaskProperties taskProperties;

    /** taskCode -> 任务实现 */
    private final Map<String, AbstractFileImportTask> taskMap = new ConcurrentHashMap<>();

    /** 所有已注册任务的构造好的上下文模板（batchNo 等运行时字段每次执行时补齐） */
    private final Map<String, TaskContext> contextTemplates = new ConcurrentHashMap<>();

    /** Spring 会自动把容器里所有 AbstractFileImportTask 子类注入进来 */
    public TaskRegistry(TaskProperties taskProperties,
                        List<AbstractFileImportTask> tasks) {
        this.taskProperties = taskProperties;
        for (AbstractFileImportTask task : tasks) {
            String code = task.taskCode();
            if (taskMap.containsKey(code)) {
                // 重复 taskCode 会导致配置错乱，必须启动就炸掉
                throw new FileImportException("任务编码重复: " + code
                        + "，冲突类: " + taskMap.get(code).getClass().getName()
                        + " vs " + task.getClass().getName());
            }
            taskMap.put(code, task);
        }
    }

    /**
     * 启动时校验：配置与实现必须一一对应。
     * <p>分三种情况处理，每种都有明确的运维语义：
     * <ul>
     *   <li>有实现无配置 → 报错。很可能是漏配了，属于人为失误，不能静默忽略。</li>
     *   <li>有配置无实现 → 仅告警。允许"先把配置准备好、代码后补"的工作方式，
     *       也允许通过配置 {@code enabled: false} 来暂时停用某个任务。</li>
     *   <li>两者都有 → 构建上下文模板。</li>
     * </ul>
     */
    @PostConstruct
    public void validateAndBuild() {
        Map<String, TaskProperties.TaskConfig> configs = taskProperties.getTasks();

        // 情况一：有实现但没配置 —— 直接启动失败
        for (String code : taskMap.keySet()) {
            if (configs == null || !configs.containsKey(code)) {
                throw new FileImportException(
                        "任务[" + code + "]有代码实现但缺少配置，请在 application.yml 的 app.task.tasks 下补全配置");
            }
        }

        // 情况二/三
        if (configs != null) {
            for (Map.Entry<String, TaskProperties.TaskConfig> entry : configs.entrySet()) {
                String code = entry.getKey();
                TaskProperties.TaskConfig config = entry.getValue();

                if (!taskMap.containsKey(code)) {
                    log.warn("任务[{}]已配置但无对应代码实现，该配置将被忽略（如需启用请添加对应的 Task 子类）", code);
                    continue;
                }
                if (!config.isEnabled()) {
                    log.info("任务[{}]配置为停用状态(enabled=false)，本次不注册", code);
                    continue;
                }
                // 校验必填项，缺了就在启动时报错
                validateConfig(code, config);
                contextTemplates.put(code, buildContext(code, config));
                log.info("任务[{}]注册成功: 目录={}, 通配符={}, 并发={}, 批大小={}",
                        code, config.getSourceDirs(), config.getFilePatterns(),
                        config.getConcurrency(), config.getBatchSize());
            }
        }

        log.info("任务注册完成，共 {} 个可用任务: {}", contextTemplates.size(), contextTemplates.keySet());
    }

    /** 必填项校验。启动时炸掉，远好过运行到一半炸掉 */
    private void validateConfig(String code, TaskProperties.TaskConfig config) {
        if (config.getSourceDirs() == null || config.getSourceDirs().isEmpty()) {
            throw new FileImportException("任务[" + code + "]未配置 source-dirs");
        }
        if (config.getBackupDir() == null || config.getBackupDir().isBlank()) {
            throw new FileImportException("任务[" + code + "]未配置 backup-dir");
        }
        if (config.getCron() == null || config.getCron().isBlank()) {
            throw new FileImportException("任务[" + code + "]未配置 cron 表达式");
        }
    }

    /** 把扁平化的配置对象转换成任务执行上下文 */
    private TaskContext buildContext(String code, TaskProperties.TaskConfig c) {
        return TaskContext.builder()
                .taskCode(code)
                .taskName(c.getName() == null ? code : c.getName())
                .sourceDirs(c.getSourceDirs())
                .filePatterns(c.getFilePatterns())
                .backupDir(c.getBackupDir())
                .errorDir(c.getErrorDir() == null ? c.getBackupDir() + "_error" : c.getErrorDir())
                .charset(Charset.forName(c.getCharset() == null ? "UTF-8" : c.getCharset()))
                .delimiter(c.getDelimiter() == null ? ',' : c.getDelimiter())
                .skipHeader(c.isSkipHeader())
                .maxFileSizeBytes(c.getMaxFileSizeBytes())
                .fileQuietPeriodMillis(c.getFileQuietPeriodMillis())
                .batchSize(c.getBatchSize())
                .fetchThreads(c.getFetchThreads())
                .concurrency(c.getConcurrency())
                .retryTimes(c.getRetryTimes())
                .retryIntervalMillis(c.getRetryIntervalMillis())
                .extras(c.getExtras() == null ? new HashMap<>() : c.getExtras())
                .build();
    }

    // ==================== 对外查询接口 ====================

    public Optional<AbstractFileImportTask> getTask(String taskCode) {
        return Optional.ofNullable(taskMap.get(taskCode));
    }

    /**
     * 取任务上下文模板的副本。
     * <p>
     * <b>必须返回副本</b>：TaskContext 在每次执行时会被写入 batchNo/startTime 等运行时字段，
     * 若直接返回同一个实例，多线程并发执行时会产生脏写（A 任务的 batchNo 被 B 任务覆盖）。
     * 这是共享可变状态的经典陷阱，用 {@code toBuilder().build()} 做浅拷贝规避。
     */
    public TaskContext getContextTemplate(String taskCode) {
        TaskContext template = contextTemplates.get(taskCode);
        if (template == null) {
            throw new FileImportException("任务[" + taskCode + "]未注册或无可用配置");
        }
        return template.toBuilder().build();
    }

    public List<String> getAllTaskCodes() {
        return List.copyOf(contextTemplates.keySet());
    }

    /** 配置里声明的 cron，供调度层使用 */
    public String getCron(String taskCode) {
        TaskProperties.TaskConfig c = taskProperties.getTasks().get(taskCode);
        return c == null ? null : c.getCron();
    }
}
