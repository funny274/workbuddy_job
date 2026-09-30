package com.example.fileimport.task.order;

import com.example.fileimport.framework.core.AbstractFileImportTask;
import com.example.fileimport.framework.core.FileStat;
import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.core.TaskHandler;
import com.example.fileimport.framework.core.TaskResult;
import com.example.fileimport.framework.support.JdbcBatchWriter;
import com.example.fileimport.framework.support.JdbcTemplateBatchWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ==========================================================================
 * <b>【模板】订单明细导入任务 —— 新增任务时照抄这个类的结构</b>
 * ==========================================================================
 *
 * <p>这个类是全项目最短的类之一，也正说明了框架的抽象是成功的：
 * <b>业务任务只负责"声明"，不负责"实现流程"。</b>
 *
 * <p>整个类只有 3 个关注点：
 * <ol>
 *   <li>{@link #taskCode()} —— 我是谁（路由键）</li>
 *   <li>{@link #createHandler()} —— 我处理什么数据（业务转换）</li>
 *   <li>{@link #createBatchWriter(TaskContext)} —— 我写进哪张表（目标表与字段映射）</li>
 * </ol>
 *
 * <p><b>新增任务的完整步骤见 README.md。</b> 简言之：
 * 复制本类 → 改 taskCode → 换成自己的 Handler 和 Writer → 加 yml 配置 → 完成。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class OrderDetailImportTask extends AbstractFileImportTask {

    /**
     * INSERT 语句。
     * <p>
     * <b>它与 OrderDetailRowBinder#bind 的字段顺序必须严格一一对应</b>，
     * 所以刻意把两者放在相邻的位置、并在注释里互相引用。
     * 这是工程上防止"顺序错位"这类致命低级错误最有效的办法。
     * <p>
     * 表名带反引号是 MySQL 规范写法，防止表名与保留字冲突。
     */
    private static final String INSERT_SQL = """
            INSERT INTO t_order_detail
              (order_no, customer_no, product_code, quantity,
               unit_price, amount, order_time, channel_code, batch_no)
            VALUES (?,?,?,?,?,?,?,?,?)
            """;

    /** 主数据源的 JdbcTemplate。多数据源场景下用 @Qualifier 指定 */
    private final JdbcTemplate jdbcTemplate;

    /** 编程式事务模板。批量写入的"一批一事务"边界由它控制 */
    private final TransactionTemplate transactionTemplate;

    /** 业务处理器，由 Spring 注入（也可 new 出来，视是否需要依赖其他 Bean 而定） */
    private final OrderDetailCsvHandler handler;

    public OrderDetailImportTask(JdbcTemplate jdbcTemplate,
                                 TransactionTemplate transactionTemplate,
                                 OrderDetailCsvHandler handler) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.handler = handler;
    }

    /** 任务编码，必须与 application.yml 中 app.task.tasks 下的 key 完全一致 */
    @Override
    public String taskCode() {
        return OrderDetailCsvHandler.TASK_CODE;
    }

    /** 提供业务处理器：把一行文本变一条记录 */
    @Override
    protected TaskHandler createHandler() {
        return handler;
    }

    /**
     * 提供批量写入器：声明"写到哪张表、字段怎么绑"。
     * <p>
     * 这是新增任务时唯一需要"认真想"的地方——表的字段顺序、类型、可空性。
     * 其余全靠框架。
     */
    @Override
    protected JdbcBatchWriter createBatchWriter(TaskContext ctx) {
        return new JdbcTemplateBatchWriter(
                jdbcTemplate,
                transactionTemplate,
                INSERT_SQL,
                ctx.getBatchSize());
    }

    /**
     * 文件处理成功后的业务回调示例。
     * <p>
     * 典型用途：写一条导入汇总表记录、发消息通知下游开始对账、
     * 更新上游文件状态标记。
     * <p>
     * 注意：本方法抛出的异常框架会吞掉（不影响主流程），
     * 所以"必须成功"的操作不适合放这里，应该放在主流程里。
     * <p>
     * 【易错点】这里是覆写 {@link TaskHandler} 的**接口方法**（public），
     * 因此访问修饰符必须是 {@code public}——写成 protected 编译不过。
     * 而 {@link #beforeExecute}、{@link #afterExecute} 覆写的是父类的
     * **protected 方法**，所以用 protected。这个区别很容易搞混。
     */
    @Override
    public void onFileSuccess(String fileName, FileStat stat, TaskContext ctx) {
        log.info("[订单明细]文件 {} 导入完成: 入库 {} 行（批次 {}）",
                fileName, stat.insertedRows(), ctx.getBatchNo());
        // 示例：脏数据率超阈值时告警。5% 是经验值，需按业务调整。
        if (stat.totalRows() > 0) {
            double dirtyRate = (double) stat.dirtyRows() / stat.totalRows();
            if (dirtyRate > 0.05) {
                log.error("[订单明细]文件 {} 脏数据率过高: {}/{} = {}%，请检查上游文件格式规范",
                        fileName, stat.dirtyRows(), stat.totalRows(),
                        String.format("%.2f", dirtyRate * 100));
            }
        }
    }

    /**
     * 任务执行前校验示例：检查目标目录是否可用。
     * <p>
     * 在启动阶段就发现"目录不存在/没权限"，比跑到一半才发现要好得多。
     */
    @Override
    protected boolean beforeExecute(TaskContext ctx) {
        boolean ok = true;
        for (String dir : ctx.getSourceDirs()) {
            java.nio.file.Path path = java.nio.file.Paths.get(dir);
            if (!java.nio.file.Files.isDirectory(path)) {
                log.error("任务[{}]源目录不存在，本次执行跳过: {}", ctx.getTaskCode(), dir);
                ok = false;
            } else if (!java.nio.file.Files.isReadable(path)) {
                log.error("任务[{}]源目录无读权限，本次执行跳过: {}", ctx.getTaskCode(), dir);
                ok = false;
            }
        }
        return ok;
    }

    /**
     * 任务执行后收尾示例。
     * <p>这里可以输出统计摘要到监控系统、或把结果推送给告警平台。
     */
    @Override
    protected void afterExecute(TaskContext ctx, TaskResult result) {
        if (!result.isSuccess()) {
            log.warn("任务[{}]本批次存在失败文件，失败明细: {}",
                    ctx.getTaskCode(), result.getErrors());
        }
    }
}
