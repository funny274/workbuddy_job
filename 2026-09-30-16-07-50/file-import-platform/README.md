# 通用文件采集入库调度平台

一个**配置驱动**的定时任务框架：用同一套流程骨架支撑 N 个"读文件 → 入库 → 归档"任务。
新增任务只需 **加一段 yml 配置 + 写两个小类**，无需改动框架代码。

---

## 一、它解决什么问题

企业里有一类需求反复出现，每次都重写一遍，每次都踩同样的坑：

> 每隔 N 分钟，去某个目录把文件捞出来，解析后写入数据库，然后归档到备份目录。

听起来简单，但生产环境会依次遇到下面这些问题。**本框架把每一个都处理了**：

| 问题 | 不处理的后果 | 框架的处理方式 |
|---|---|---|
| 文件还在被上游写入 | 读到半个文件，数据残缺 | **静默期**检测（最后修改距今 > 阈值） |
| 多台机器同时扫到同一文件 | 数据翻倍，对账全错 | **Redis 分布式文件锁** + 处理完即移走 |
| 上一轮没跑完下一轮又触发 | 同一批文件重复处理 | **任务级防重叠**（CAS 标记） |
| 一次 insert 一行 | 数据库被打爆，TPS 个位数 | **攒批提交** + `rewriteBatchedStatements` |
| 一行脏数据让整文件失败 | 文件反复重试且永远不成功 | **行级容错**，脏数据计数不阻断 |
| 重试风暴把下游打死 | 雪崩 | **指数退避 + 随机抖动** |
| 归档移动到一半中断 | 文件损坏 | **原子移动**（`ATOMIC_MOVE`） |
| 出问题查不到现场 | 通宵排查 | **执行日志表** + **错误文件保留现场** |
| 漏刷最后一批数据 | <u>静默丢数据</u>，极难发现 | 文件末尾**强制 flush** |

---

## 二、快速开始

### 0. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| **JDK** | **21 (LTS)** | ⚠️ **不要用 JDK 24/25**，见下方说明 |
| Maven | 3.8+ | |
| MySQL | 8.0+ | |
| Redis | 5+ | 可选。单机部署不装也能跑（会降级为单机模式） |
| ZooKeeper | 3.6+ | 可选。仅 `mode=elasticjob` 时需要 |

> ⚠️ **JDK 版本务必用 21**
>
> 在 JDK 24/25 上编译本项目会报出 `方法不会覆盖或实现超类型的方法`，
> 指向一个签名完全正确的方法，非常难以定位。原因是 Lombok 的注解处理器
> 与 JDK 24+ 的 javac 内部结构存在兼容问题，会破坏 `@Override` 的校验。
> JDK 21 是 Spring Boot 3.3 官方支持的 LTS 版本，本项目在 21 上编译通过、零告警。

```bash
# 确认 JDK 版本
java -version   # 应输出 21.x

# 若机器上有多个 JDK，编译时显式指定
export JAVA_HOME=/path/to/jdk-21
mvn clean package
```

### 1. 建库建表

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

### 2. 改配置

编辑 `src/main/resources/application.yml`，至少改这两处：

```yaml
spring:
  datasource:
    url: jdbc:mysql://你的IP:3306/file_import?...
    username: 你的账号
    password: 你的密码

app:
  task:
    tasks:
      ORDER_DETAIL_IMPORT:
        source-dirs:
          - D:/data/in/order     # ← 改成你的实际目录
        backup-dir: D:/data/backup/order
```

### 3. 启动

```bash
mvn spring-boot:run
```

默认 `app.schedule.mode=local`，**不需要 ZooKeeper** 就能跑通。

### 4. 投放文件验证

项目已内置 3 个测试文件，直接可用：

```bash
# 观察日志，应当看到扫描到 3 个文件并处理成功
# 注意：首次启动不会立即执行，需等待 cron 触发点（默认每 5 分钟）
```

想立刻看到效果，用手动触发接口：

```bash
curl -X POST http://localhost:8080/ops/tasks/ORDER_DETAIL_IMPORT/trigger
```

然后查库验证：

```sql
SELECT COUNT(*) FROM t_order_detail;              -- 应约 6000 条
SELECT * FROM sys_task_exec_log ORDER BY id DESC LIMIT 5;  -- 查看执行记录
```

---

## 三、如何新增一个任务（重点）

以"新增一个**商品快照**导入任务"为例，**总共 4 步**。

### 第 1 步：建目标表

```sql
CREATE TABLE t_product_snapshot (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(256),
    price        DECIMAL(18,4),
    batch_no     VARCHAR(64),
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> **建议**：加上唯一索引做幂等兜底。即使文件被重复投放，数据库层也挡住重复数据。

### 第 2 步：写 Handler（**唯一需要写业务逻辑的地方**）

复制 `OrderDetailCsvHandler.java` 改名，改 `taskCode()` 和 `handleLine()` 里的解析逻辑：

```java
@Component
public class ProductSnapshotHandler implements TaskHandler {

    public static final String TASK_CODE = "PRODUCT_SNAPSHOT_IMPORT";  // ① 新任务编码

    @Override
    public String taskCode() {
        return TASK_CODE;
    }

    @Override
    public void handleLine(long lineNo, String rawLine, BatchBuffer buffer, TaskContext ctx) {
        // ② 这里写你的解析逻辑
        String[] cols = rawLine.split("\\|", -1);   // 竖线分隔
        if (cols.length < 3) {
            throw new IllegalArgumentException("列数不足: " + cols.length);  // 框架会记脏数据并继续
        }

        ProductSnapshot po = new ProductSnapshot();
        po.setProductCode(cols[0].trim());
        po.setProductName(cols[1].trim());
        po.setPrice(new BigDecimal(cols[2].trim()));
        po.setBatchNo(ctx.getBatchNo());

        buffer.add(new ProductSnapshotRowBinder(po));   // ③ 攒批，攒够自动提交
    }
}
```

> **`handleLine` 被多线程并发调用，不要写实例可变字段。** 所有随行状态放 `buffer` 里，天然线程隔离。

### 第 3 步：写 Task 类（**只有 3 个方法**）

复制 `OrderDetailImportTask.java` 改名，改 `taskCode()` / `createHandler()` / `createBatchWriter()`：

```java
@Component
public class ProductSnapshotImportTask extends AbstractFileImportTask {

    private static final String INSERT_SQL = """
            INSERT INTO t_product_snapshot (product_code, product_name, price, batch_no)
            VALUES (?,?,?,?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ProductSnapshotHandler handler;

    public ProductSnapshotImportTask(JdbcTemplate jdbcTemplate,
                                     TransactionTemplate transactionTemplate,
                                     ProductSnapshotHandler handler) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.handler = handler;
    }

    @Override
    public String taskCode() {
        return ProductSnapshotHandler.TASK_CODE;
    }

    @Override
    protected TaskHandler createHandler() {
        return handler;
    }

    @Override
    protected JdbcBatchWriter createBatchWriter(TaskContext ctx) {
        return new JdbcTemplateBatchWriter(jdbcTemplate, transactionTemplate,
                INSERT_SQL, ctx.getBatchSize());
    }
}
```

### 第 4 步：加配置

在 `application.yml` 的 `app.task.tasks` 下**照着已有任务复制一段**：

```yaml
app:
  task:
    tasks:
      # ↓↓↓ 复制粘贴，改这几个值就行 ↓↓↓
      PRODUCT_SNAPSHOT_IMPORT:              # 必须与 TASK_CODE 一致
        name: 商品快照文件导入
        enabled: true
        cron: 0 0 3 * * ?
        source-dirs:
          - D:/data/in/product
        file-patterns:
          - "PRODUCT_*.dat"
        backup-dir: D:/data/backup/product
        charset: GBK                        # 上游是 GBK
        delimiter: "|"                      # 竖线分隔
        skip-header: false
        batch-size: 2000
        concurrency: 2
```

**完成。** 重启应用即可，框架会自动扫描、解析、入库、归档、记录日志。

---

## 四、新增任务 checklist

- [ ] 目标表已建（建议加唯一索引做幂等兜底）
- [ ] Handler 的 `taskCode()` 与配置 key **完全一致**
- [ ] `INSERT_SQL` 的 `?` 顺序与 `RowBinder#bind` 的 `ps.setXxx(下标)` **逐一对齐**
- [ ] 金额字段用 `BigDecimal`，`DECIMAL` 列（**绝不用 double/float**）
- [ ] 可空字段显式 `ps.setNull(idx, Types.XXX)`
- [ ] `handleLine` 内**无实例可变状态**（并发安全）
- [ ] 配置里 `enabled: true`
- [ ] 生产环境把 `overwrite` 设为 `false`

> **最容易出错的是第三项**：字段顺序错位时程序不报错，只是数据静默错位。
> 所以框架刻意把 `INSERT_SQL` 和 `RowBinder` 放在相邻位置，方便对照。

---

## 五、核心设计说明

### 5.1 分层结构

```
com.example.fileimport
├── framework/                  ← 框架层（新增任务不用改这里）
│   ├── core/
│   │   ├── AbstractFileImportTask    ★ 模板方法：固化"扫描→限流→重试→入库→归档"全流程
│   │   ├── TaskHandler               ★ 业务 SPI：新增任务实现这个
│   │   ├── BatchBuffer               攒批缓冲区，数据库压力的调节阀
│   │   ├── TaskContext               一次执行的全部配置（不可变 + Builder）
│   │   ├── TaskResult                执行统计（LongAdder，高并发友好）
│   │   ├── TaskRegistry              启动时校验"配置与实现一一对应"，Fail Fast
│   │   └── TaskExecutionRecorder     执行日志落库，运维可观测性
│   ├── schedule/
│   │   ├── LocalTaskScheduler        @Scheduled 模式（无需 ZK）
│   │   ├── FileImportElasticJob      ElasticJob 模式（支持分片）
│   │   ├── ElasticJobConfig          配置驱动的调度装配
│   │   └── CronMatcher               轻量 cron 匹配（复用 Spring 解析器）
│   ├── support/
│   │   ├── FileDispatcher            扫描/静默期/分布式锁/原子移动
│   │   ├── JdbcBatchWriter           批量写入抽象
│   │   ├── JdbcTemplateBatchWriter   裸 JDBC Batch 实现（性能最优）
│   │   ├── RetryTemplate             指数退避 + 抖动 + 不重试白名单
│   │   └── BindableRow               行绑定契约（零反射）
│   └── exception/                    统一异常体系（携带"可否重试"语义）
├── config/                     ← 配置绑定、线程池、事务、全局异常
├── task/order/                 ← 业务任务（新增任务放这里）
└── controller/                 ← 运维接口
```

### 5.2 关键技术选型及理由

| 技术 | 版本 | 选择理由 |
|---|---|---|
| Spring Boot | 3.3.5 | 当前主流稳定版，JDK 17+ |
| JDK | 21 (LTS) | **虚拟线程**：文件采集是 IO 密集型，一文件一线程不再有资源代价 |
| ElasticJob | 3.0.4 | **原生任务分片**，实例上下线自动重平衡，应对"多份文件并行处理" |
| MySQL + HikariCP | 8.x | 驱动 `rewriteBatchedStatements=true` 是批量写入提速的关键 |
| 裸 JdbcTemplate Batch | - | 比 ORM 的 saveBatch 更可控，无一级缓存膨胀风险 |
| Redis | - | 分布式文件锁（`setIfAbsent` + TTL 防死锁） |
| 虚拟线程 + Semaphore | - | **双重限流**：虚拟线程承载并发，信号量限制并发 |

### 5.3 两个最容易配错的地方

**① 连接池不是越大越好**

```yaml
maximum-pool-size: 20
```

经验公式 `CPU核数 × 2 + 有效磁盘数`。连接数超过数据库处理能力后，
加大连接数只会让等待队列更长、整体 TPS **下降**。
压力大时应该调小 `concurrency`，而不是调大连接池。

**② 虚拟线程不代表可以无限并发**

真正的瓶颈在下游。数据库只有 20 个连接时，开 1000 个虚拟线程只会让请求排更长队。
所以框架用 `Semaphore` 按任务限制并发文件数（`concurrency`），这是保护数据库的主阀门。

### 5.4 为什么基类要实现 TaskHandler 接口

`AbstractFileImportTask implements TaskHandler`，这一点值得说明，因为它是**一个容易踩的坑**：

子类常需要覆写 `onFileSuccess` / `onFileFailure` 这类回调。如果基类不实现 `TaskHandler`，
子类里写 `@Override` 就会「无物可覆写」，直接编译失败：

```
错误: 方法不会覆盖或实现超类型的方法
```

基类实现该接口后，职责划分非常清晰：

| 角色 | 职责 |
|---|---|
| `AbstractFileImportTask`（基类） | 管**流程** + 提供**回调钩子**的默认转发 |
| `TaskHandler`（接口） | 管**行级转换**（一行文本 → 一条记录） |
| 业务 `*ImportTask`（子类） | 只做**声明**：我是谁、处理什么、写哪张表 |

所以新增任务时，回调钩子「可以不写」，需要时再覆写，且有明确的覆写目标。

### 5.5 调度模式切换
改一行配置即可，业务代码零改动：

```yaml
app:
  schedule:
    mode: local        # 单机/开发：Spring @Scheduled，无需外部依赖
    # mode: elasticjob # 生产集群：ElasticJob + ZooKeeper，支持分片
```

ElasticJob 模式下需配置 ZooKeeper：

```yaml
    elasticjob:
      server-lists: zk1:2181,zk2:2181,zk3:2181   # 生产必须 3 节点以上
      namespace: file-import-prod                 # 各环境必须独立，否则互相覆盖
      overwrite: false                            # 生产建议 false
```

### 5.6 生产环境调优参数对照

| 现象 | 调整项 | 方向 |
|---|---|---|
| 数据库 CPU 高 / 慢查询多 | `batch-size`、`concurrency` | 调大 batch、调小并发 |
| 连接池等待超时 | `concurrency` | 调小（**不是**调大连接池） |
| 吞吐上不去、CPU 空转 | `concurrency`、`fetch-threads` | 调大 |
| 读到半截文件 | `file-quiet-period-millis` | 调大 |
| 重试风暴 | `retry-times`、`retry-interval-millis` | 减小次数、加大间隔 |
| 错过执行时刻丢数据 | ElasticJob `misfire` | 保持 `true` |

---

## 六、运维接口

| 接口 | 说明 |
|---|---|
| `GET /ops/tasks` | 查看所有任务配置与运行状态 |
| `POST /ops/tasks/{taskCode}/trigger` | 手动触发一次（排障用） |
| `GET /actuator/health` | 健康检查（K8s 探针） |
| `GET /actuator/prometheus` | Prometheus 指标 |

> **安全提醒**：生产环境务必给 `/ops/**` 加鉴权并限制内网访问。手动触发是有副作用的操作。

### 常用排障 SQL

```sql
-- 最近 20 次执行情况
SELECT task_code, batch_no, start_time, cost_millis,
       scanned_files, success_files, failed_files,
       total_rows, inserted_rows, dirty_rows, status
FROM sys_task_exec_log ORDER BY id DESC LIMIT 20;

-- 脏数据率异常的任务（质量预警）
SELECT task_code, batch_no,
       ROUND(dirty_rows / NULLIF(total_rows,0) * 100, 2) AS dirty_rate_pct
FROM sys_task_exec_log
WHERE total_rows > 0 AND dirty_rows / NULLIF(total_rows,0) > 0.05
ORDER BY id DESC;

-- 连续多轮扫描不到文件（上游疑似断供）
SELECT task_code, start_time, scanned_files
FROM sys_task_exec_log
WHERE scanned_files = 0 ORDER BY id DESC LIMIT 50;
```

---

## 七、注意事项与后续演进

### 已知取舍

1. **`handleLine` 用 `split` 而非 CSV 解析器**
   适用于字段内不含引号包裹逗号的简单格式（多数国内清单文件如此），性能更好。
   若字段可能含引号包裹的逗号或换行，需换成 `commons-csv` 的 `CSVParser`。

2. **Redis 不可用时降级为单机模式继续处理**
   取舍是"宁可可能重复，也不因 Redis 抖动导致业务停摆"。
   若业务不允许重复，改 `FileDispatcher#tryLock` 的 catch 分支返回 `false`。

3. **yml 配置需重启生效**
   如需动态改 cron / 启停任务，`sys_task_config` 表已建好，
   加一个定时 reload 任务即可（ElasticJob 管控台也可在线调整）。

### 建议的下一步演进

- [ ] 告警接入：`afterExecute` 里对接钉钉/企微/邮件
- [ ] 失败文件重投运维页（`sys_failed_file` 表已备好）
- [ ] 大文件分片读取（超过 1GB 的文件按行偏移切分并行处理）
- [ ] 动态配置刷新（读 `sys_task_config`）
- [ ] 多数据源支持（不同任务写不同库，用 `@DS` 或自定义 `JdbcBatchWriter`）
- [ ] 链路追踪接入（SkyWalking / Micrometer Tracing）
