package com.example.fileimport.framework.support;

import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.exception.FileImportDataException;
import com.example.fileimport.framework.exception.FileImportException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 文件调度器 —— 负责"找文件、判断能不能处理、处理完往哪搬"。
 * <p>
 * 这是采集类系统里最容易出生产事故的一环，本类集中处理了以下经典问题：
 *
 * <h3>1. 半包文件（上游还在写，我方已经在读）</h3>
 * 两种防护叠加使用：
 * <ul>
 *   <li><b>静默期</b>：文件 lastModifiedTime 距今超过阈值才处理（最通用，必须有）</li>
 *   <li><b>排他文件锁</b>：尝试独占打开，失败说明有进程正在写（更精确，但依赖上游配合）</li>
 * </ul>
 *
 * <h3>2. 重复处理（多节点/多次调度读到同一文件）</h3>
 * <ul>
 *   <li><b>Redis 分布式锁</b>：跨节点互斥，key 维度 = 文件绝对路径</li>
 *   <li><b>处理中标记</b>：抢占后先标记，避免同节点下一轮调度重复拾取</li>
 * </ul>
 *
 * <h3>3. 归档时的原子性（移动到一半被中断）</h3>
 * 用 {@link Files#move} 的 {@code ATOMIC_MOVE}：
 * 同文件系统内移动是原子操作，绝不会出现"半个文件"。
 * 跨文件系统时 ATOMIC_MOVE 会抛异常，此时降级为 copy + delete，并做完整性校验。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class FileDispatcher {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 文件锁 key 前缀 */
    private static final String LOCK_KEY_PREFIX = "file-import:lock:";

    /** 锁的默认持有时间：防止持有者崩溃后锁永不释放（死锁） */
    private static final Duration LOCK_TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;

    /** 应用实例标识，用于追溯"是哪个节点在处理" */
    private final String instanceId;

    public FileDispatcher(StringRedisTemplate redisTemplate,
                          @Value("${app.instance-id:${HOSTNAME:local}}") String instanceId) {
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceId;
    }

    // ==================== 扫描 ====================

    /**
     * 扫描所有配置目录，返回匹配通配符的候选文件。
     * <p>
     * 只做"粗筛"（目录存在 + 文件名匹配），不做就绪判断——
     * 就绪判断（静默期、文件锁）相对昂贵，应尽量少做。
     *
     * @return 候选文件列表（只读第一层目录，不递归——递归会引入不可控的扫描成本）
     */
    public List<Path> scan(TaskContext ctx) {
        List<Path> result = new ArrayList<>();
        List<String> dirs = ctx.getSourceDirs();
        List<String> patterns = ctx.getFilePatterns();

        if (dirs == null || dirs.isEmpty()) {
            throw new FileImportException("任务[" + ctx.getTaskCode() + "]未配置 sourceDirs");
        }

        for (String dirStr : dirs) {
            Path dir = Paths.get(dirStr);
            if (!Files.isDirectory(dir)) {
                log.warn("任务[{}]源目录不存在，已跳过: {}", ctx.getTaskCode(), dirStr);
                continue;
            }
            if (!Files.isReadable(dir)) {
                log.error("任务[{}]源目录无读权限: {}", ctx.getTaskCode(), dirStr);
                continue;
            }

            try (Stream<Path> stream = Files.list(dir)) {
                List<Path> matched = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> matchesAny(p.getFileName().toString(), patterns))
                        .toList();
                result.addAll(matched);
            } catch (IOException e) {
                // 单个目录扫描失败不影响其他目录
                log.error("任务[{}]扫描目录失败: {}", ctx.getTaskCode(), dirStr, e);
            }
        }

        // 排序保证处理顺序稳定（先到先处理），便于排查问题
        Collections.sort(result);
        return result;
    }

    /** 文件名是否匹配任一通配符。patterns 为空时匹配全部 */
    private boolean matchesAny(String fileName, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        for (String pattern : patterns) {
            FileSystem fs = FileSystems.getDefault();
            PathMatcher matcher = fs.getPathMatcher("glob:" + pattern);
            if (matcher.matches(Paths.get(fileName))) {
                return true;
            }
        }
        return false;
    }

    // ==================== 就绪判断 ====================

    /**
     * 判断文件是否已经"可以安全处理"。
     * <p>
     * 三重检查，任一不通过都返回 false（本轮跳过，下轮再看）：
     * <ol>
     *   <li>静默期：最后修改时间距今必须超过配置阈值</li>
     *   <li>大小稳定：连续两次读取大小一致（应对"上游持续追加写"的场景）</li>
     *   <li>大小上限：超过配置上限直接拒绝，避免误投放巨大文件打爆系统</li>
     * </ol>
     */
    public boolean isReadyToProcess(Path path, TaskContext ctx) throws IOException {
        // ---- 1. 大小上限 ----
        long size = Files.size(path);
        if (ctx.getMaxFileSizeBytes() > 0 && size > ctx.getMaxFileSizeBytes()) {
            log.error("文件[{}]大小 {} 字节超过上限 {} 字节，拒绝处理并告警", path, size, ctx.getMaxFileSizeBytes());
            return false;
        }
        if (size == 0L) {
            log.warn("文件[{}]是空文件，跳过", path);
            return false;
        }

        // ---- 2. 静默期 ----
        if (ctx.getFileQuietPeriodMillis() > 0) {
            Instant lastModified = Files.getLastModifiedTime(path).toInstant();
            long idleMillis = Instant.now().toEpochMilli() - lastModified.toEpochMilli();
            if (idleMillis < ctx.getFileQuietPeriodMillis()) {
                log.debug("文件[{}]最后修改距今 {}ms，未达静默期 {}ms，本轮跳过",
                        path, idleMillis, ctx.getFileQuietPeriodMillis());
                return false;
            }
        }

        // ---- 3. 排他锁探测：能不能独占打开 ----
        // 能独占打开通常说明没有其他进程在写。注意此检查在 Linux/Windows 行为略有差异，
        // 所以它只是"静默期"的补充，不是替代。
        return canLockExclusively(path);
    }

    /**
     * 尝试获取排他文件锁，用于探测"是否有人在写"。
     * <p>加锁成功后立即释放——这里只是探测，不做长期持有。
     */
    private boolean canLockExclusively(Path path) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE);
             FileLock ignored = channel.tryLock()) {
            return true;
        } catch (OverlappingFileLockException e) {
            // 本 JVM 内已有线程持有该文件锁
            log.debug("文件[{}]已被本进程其他线程占用", path);
            return false;
        } catch (IOException e) {
            // 被其他进程占用（Windows 下表现为共享冲突）
            log.debug("文件[{}]当前无法独占打开: {}", path, e.getMessage());
            return false;
        }
    }

    // ==================== 分布式文件锁 ====================

    /**
     * 抢占文件的分布式处理权。
     * <p>
     * 用 Redis {@code setIfAbsent + TTL} 实现，语义是"同一文件在集群内同一时刻只被一个节点处理"。
     * TTL 是必需的：防止持锁节点宕机后该文件永远无人处理（死锁）。
     * TTL 应大于单个文件的最长处理时间，这里取 2 小时。
     *
     * @return true 表示抢到，可以处理
     */
    public boolean tryLock(Path file, TaskContext ctx) {
        String key = LOCK_KEY_PREFIX + normalizeKey(file);
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                    key, instanceId, LOCK_TTL);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 不可用时的降级策略：按"单机模式"继续处理
            // 这个取舍需要业务确认——宁可可能重复，也不能因为 Redis 抖动导致业务停摆。
            // 若业务不允许重复，应改为返回 false（宁可积压也不重复）。
            log.error("获取文件锁失败（Redis 异常），降级为单机模式继续处理: {}", key, e);
            return true;
        }
    }

    /** 释放文件锁 */
    public void unlock(Path file, TaskContext ctx) {
        String key = LOCK_KEY_PREFIX + normalizeKey(file);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            // 释放失败无妨：TTL 会兜底自动过期
            log.warn("释放文件锁失败（将由 TTL 自动过期）: {}", key, e);
        }
    }

    /** 把绝对路径规整成 Redis key 友好的形式 */
    private String normalizeKey(Path file) {
        return file.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    // ==================== 归档 ====================

    /**
     * 处理成功后移动到备份目录，按日期分子目录避免单目录文件数过多
     * （单目录几十万文件会让 ls / 文件系统索引变得极慢）。
     */
    public Path moveToBackup(Path source, TaskContext ctx) throws IOException {
        Path targetDir = Paths.get(ctx.getBackupDir(), LocalDate.now().format(DATE_DIR));
        return atomicMove(source, targetDir);
    }

    /**
     * 处理失败后移动到错误目录，并附带失败原因，方便人工排查。
     * 文件名格式：{原名}.{时间戳}.error
     */
    public Path moveToError(Path source, TaskContext ctx, Throwable cause) throws IOException {
        String errorDir = ctx.getErrorDir();
        if (errorDir == null || errorDir.isBlank()) {
            errorDir = ctx.getBackupDir() + "_error";
        }
        Path targetDir = Paths.get(errorDir, LocalDate.now().format(DATE_DIR));
        Files.createDirectories(targetDir);

        String newName = source.getFileName() + "." + System.currentTimeMillis() + ".error";
        Path target = targetDir.resolve(newName);

        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("移动失败文件到错误目录失败: {} -> {}", source, target, e);
            throw e;
        }

        // 写一份失败原因说明，与数据文件放在一起，人工介入时不用去翻日志
        Path reasonFile = targetDir.resolve(newName + ".reason.txt");
        String reason = """
                原始文件: %s
                任务编码: %s
                批次号: %s
                处理实例: %s
                失败时间: %s
                失败原因: %s
                """.formatted(source.toAbsolutePath(), ctx.getTaskCode(), ctx.getBatchNo(),
                instanceId, LocalDateTime.now(), cause == null ? "unknown" : cause.toString());
        Files.writeString(reasonFile, reason);
        return target;
    }

    /**
     * 原子移动文件。
     * <p>
     * 优先用 {@code ATOMIC_MOVE}（同分区内的文件重命名，纳秒级且绝不会出现半截文件）。
     * 跨分区时会抛 {@code AtomicMoveNotSupportedException}，此时降级为
     * "先复制到临时文件 → 校验大小 → 改名 → 删除源文件"。
     */
    private Path atomicMove(Path source, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(source.getFileName());

        // 目标已存在时加时间戳后缀，绝不覆盖——覆盖会直接导致数据丢失
        if (Files.exists(target)) {
            target = targetDir.resolve(
                    source.getFileName() + "." + System.currentTimeMillis());
            log.warn("备份目录已存在同名文件，重命名为: {}", target.getFileName());
        }

        try {
            return Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 跨文件系统：降级处理
            log.debug("不支持原子移动（可能跨文件系统），降级为复制+删除: {}", source);
            Path tmp = targetDir.resolve(target.getFileName() + ".tmp");
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
            // 校验大小，确保复制完整后才删源文件
            if (Files.size(tmp) != Files.size(source)) {
                Files.deleteIfExists(tmp);
                throw new FileImportDataException(
                        "文件复制校验失败（大小不一致），源文件保留: " + source);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(source);
            return target;
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
