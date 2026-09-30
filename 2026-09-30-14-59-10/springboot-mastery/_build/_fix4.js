// 用 Node 生成 Day4 段落：所有 HTML 与代码都通过 JSON 序列化，杜绝转义问题
const fs = require('fs');
const TARGET = 'content.js';

function PRE(lang, code) {
  return '<pre data-lang="' + lang + '" data-code="' + encodeURIComponent(code) + '"></pre>';
}
function QA(n, q, a) {
  return '<div class="qa"><div class="q"><span class="qn">Q' + n + '</span><span>' + q +
    '</span><span class="arrow">▶</span></div><div class="a">' + a + '</div></div>';
}
function BOX(type, title, body, icon) {
  return '<div class="box ' + type + '"><div class="box-t">' + (icon || '') + ' ' + title + '</div>' + body + '</div>';
}

const chapters = [];

// ============ Day 4 ============
const day4 = {
  id: 'day4',
  badge: 'D4',
  badgeLabel: 'Day 4',
  badgeType: '',
  title: '数据访问与事务 · 从 CRUD 到事务失效排查',
  intro: '数据层是面试重灾区：事务传播行为、事务失效的 8 种场景、连接池调优、多数据源、分页插件深坑。结合你 12 年 Oracle / OceanBase / GoldenDB 迁移经验，这块是你的主场，要答得比别人深。',
  sections: []
};

// ---------- 4.1 ----------
day4.sections.push({
  id: 's4-1',
  title: '4.1 Spring 事务：传播行为与隔离级别',
  html:
'<h4>事务传播行为（7 种，面试必问前 4 种）</h4>' +
'<div class="tbl-wrap"><table><thead><tr><th>传播行为</th><th>含义</th><th>典型场景</th></tr></thead><tbody>' +
'<tr><td><code>REQUIRED</code>（默认）</td><td>有事务就加入，没有就新建</td><td>99% 的业务方法</td></tr>' +
'<tr><td><code>REQUIRES_NEW</code></td><td><strong>挂起当前事务</strong>，新建独立事务</td><td>记流水日志：业务失败但日志必须留下</td></tr>' +
'<tr><td><code>NESTED</code></td><td>嵌套事务（Savepoint 实现），子事务回滚不影响父</td><td>批量处理，部分失败不回滚全部</td></tr>' +
'<tr><td><code>SUPPORTS</code></td><td>有就用，没有就以非事务方式执行</td><td>查询方法</td></tr>' +
'<tr><td><code>NOT_SUPPORTED</code></td><td>挂起当前事务，以非事务方式执行</td><td>某些不能有事务的操作</td></tr>' +
'<tr><td><code>MANDATORY</code></td><td>必须存在事务，否则抛异常</td><td>强制要求上层开启事务</td></tr>' +
'<tr><td><code>NEVER</code></td><td>必须不存在事务，否则抛异常</td><td>极少用</td></tr>' +
'</tbody></table></div>' +

BOX('interview', 'REQUIRES_NEW 与 NESTED 的关键区别',
'<p><strong>REQUIRES_NEW</strong>：会<strong>挂起</strong>外层事务（外层连接归还但事务未提交），用<strong>新的数据库连接</strong>开新事务。外层回滚<strong>不影响</strong>内层。</p>' +
'<p><strong>NESTED</strong>：不挂起，用<strong>同一个连接</strong>，通过 JDBC Savepoint 实现。内层回滚只回滚到 Savepoint，外层回滚则内层也回滚。</p>' +
'<p>结论：需要「无论如何都提交」用 REQUIRES_NEW；需要「部分失败不回滚全部」用 NESTED。注意 NESTED 只支持 <code>DataSourceTransactionManager</code>（JDBC），JTA 不支持。</p>', '🎯') +

'<h4>@Transactional 关键属性</h4>' +
PRE('java',
`@Transactional(
    // 1. 传播行为
    propagation = Propagation.REQUIRED,
    // 2. 隔离级别（默认用数据库的）
    isolation = Isolation.REPEATABLE_READ,
    // 3. 超时（秒），超时自动回滚
    timeout = 30,
    // 4. 只读优化（MySQL 会做优化提示）
    readOnly = false,
    // 5. 遇到哪些异常回滚（默认 RuntimeException + Error）
    rollbackFor = Exception.class,
    // 6. 遇到哪些异常不回滚
    noRollbackFor = BizNotRollbackException.class
)
public void doBusiness() { }`) +

BOX('danger', '最容易踩的坑：默认不回滚受检异常',
'<p><code>@Transactional</code> <strong>默认只在遇到 <code>RuntimeException</code> 和 <code>Error</code> 时回滚</strong>。如果你抛的是 <code>Exception</code>（受检异常，如 <code>IOException</code>），<strong>事务不会回滚</strong>，数据已经写进去了！</p>' +
'<p>生产规范：<strong>永远显式写 <code>rollbackFor = Exception.class</code></strong>。这一条是大厂代码规约的强制要求。</p>', '⚠️') +

'<h4>try-catch 吞掉异常也会导致不回滚</h4>' +
PRE('java',
`// 错误写法：异常被吞了，Spring 感知不到，事务照样提交
@Transactional
public void wrong() {
    try {
        orderMapper.insert(order);
        throw new RuntimeException("模拟异常");
    } catch (Exception e) {
        log.error("出错了", e);      // 吞掉异常 -> 事务不回滚！
    }
}

// 正确写法一：不吞，让它抛出去
@Transactional(rollbackFor = Exception.class)
public void correct1() {
    orderMapper.insert(order);
    throw new BizException("业务失败");
}

// 正确写法二：确实要处理，就手动标记回滚
@Transactional(rollbackFor = Exception.class)
public void correct2() {
    try {
        orderMapper.insert(order);
        // ...
    } catch (Exception e) {
        log.error("出错了", e);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();  // 手动标记
    }
}`)
});

// ---------- 4.2 ----------
day4.sections.push({
  id: 's4-2',
  title: '4.2 事务失效的 8 大场景（面试必答）',
  html:
'<p>这道题是「懂没懂」的分水岭。我按出现频率排序。</p>' +
'<div class="tbl-wrap"><table><thead><tr><th>#</th><th>失效场景</th><th>原因</th><th>解法</th></tr></thead><tbody>' +
'<tr><td>1</td><td><strong>自调用</strong>（同类内方法互调）</td><td>this 调用不走代理</td><td>拆 Bean / 注入 self / AopContext</td></tr>' +
'<tr><td>2</td><td>方法非 <strong>public</strong></td><td>代理只增强 public 方法</td><td>改为 public</td></tr>' +
'<tr><td>3</td><td>异常被 <strong>try-catch 吞掉</strong></td><td>Spring 感知不到异常</td><td>抛出或手动 setRollbackOnly</td></tr>' +
'<tr><td>4</td><td>抛的是<strong>受检异常</strong>未配 rollbackFor</td><td>默认只回滚 RuntimeException</td><td>加 <code>rollbackFor=Exception.class</code></td></tr>' +
'<tr><td>5</td><td>类未被 <strong>Spring 管理</strong></td><td>没有代理对象</td><td>加 @Service 等注解</td></tr>' +
'<tr><td>6</td><td>数据库引擎不支持事务</td><td>MyISAM 不支持</td><td>改用 InnoDB</td></tr>' +
'<tr><td>7</td><td>方法被 <strong>final / static</strong> 修饰</td><td>CGLIB 无法覆盖</td><td>去掉修饰符</td></tr>' +
'<tr><td>8</td><td>多线程调用</td><td>事务是 ThreadLocal 绑定，子线程不共享</td><td>子线程内自己开事务</td></tr>' +
'</tbody></table></div>' +

'<h4>场景 8 详解：多线程下事务失效</h4>' +
PRE('java',
`@Transactional(rollbackFor = Exception.class)
public void wrongAsync() {
    orderMapper.insert(order);
    // 新线程里的事务是独立的，主线程回滚不会影响它
    new Thread(() -> {
        logMapper.insert(logEntry);      // 不受主事务控制
    }).start();
    throw new BizException("失败");       // 主事务回滚，但日志已写入
}`) +

'<h4>场景 1 的三种解法（代码）</h4>' +
PRE('java',
`@Service
public class OrderService {

    private final OrderService self;      // 解法二：注入自己
    private final LogService logService;  // 解法一：拆 Bean

    public OrderService(OrderService self, LogService logService) {
        this.self = self;
        this.logService = logService;
    }

    public void outer() {
        // 解法一：调用另一个 Bean 的方法
        logService.saveLog();

        // 解法二：通过代理对象调用（Spring 4.3+ 支持自注入）
        self.inner();

        // 解法三：AopContext（需 @EnableAspectJAutoProxy(exposeProxy=true)）
        // ((OrderService) AopContext.currentProxy()).inner();
    }

    @Transactional(rollbackFor = Exception.class)
    public void inner() {
        // 事务生效
    }
}`) +

BOX('ok', '面试答题模板（背这段）',
'<p>「事务失效的根本原因是 <strong>Spring 事务基于 AOP 代理</strong>，凡是导致增强逻辑无法执行、或异常无法传播到事务拦截器的情况，都会失效。主要分三类：</p>' +
'<p><strong>一类是代理没生效</strong>：自调用（this 调用不走代理）、方法非 public、方法 final/static、类没被 Spring 管理、多线程调用。</p>' +
'<p><strong>二类是异常没被感知</strong>：try-catch 吞掉异常、抛出的是受检异常但没配 rollbackFor（默认只回滚 RuntimeException）。</p>' +
'<p><strong>三类是底层不支持</strong>：数据库引擎是 MyISAM 不支持事务。</p>' +
'<p>生产规范上，我们团队要求所有 <code>@Transactional</code> 都必须显式写 <code>rollbackFor = Exception.class</code>，且禁止在事务方法里写 try-catch 吞异常。」</p>', '✅')
});

// ---------- 4.3 ----------
day4.sections.push({
  id: 's4-3',
  title: '4.3 MyBatis / MyBatis-Plus 实战要点',
  html:
'<h4>井号占位符与美元占位符的区别（高频八股）</h4>' +
'<div class="tbl-wrap"><table><thead><tr><th>对比</th><th>#{}（预编译）</th><th>${}（拼接）</th></tr></thead><tbody>' +
'<tr><td>原理</td><td>预编译占位符，生成 <code>?</code>，用 PreparedStatement 参数绑定</td><td>字符串直接拼接进 SQL</td></tr>' +
'<tr><td>SQL 注入</td><td>安全</td><td><strong>有注入风险</strong></td></tr>' +
'<tr><td>适用场景</td><td>所有参数传值</td><td>仅用于动态表名/列名/排序字段</td></tr>' +
'<tr><td>示例</td><td><code>where id = #{id}</code> 变成 <code>where id = ?</code></td><td><code>order by ${column}</code> 变成 <code>order by name</code></td></tr>' +
'</tbody></table></div>' +

BOX('danger', '美元占位符的安全用法',
'<p>当必须用 <code>${}</code>（如动态排序字段）时，<strong>必须在 Java 层做白名单校验</strong>：</p>' +
'<pre style="margin:8px 0"><code>private static final Set&lt;String&gt; ALLOWED_SORT = Set.of("create_time", "amount", "id");<br><br>if (!ALLOWED_SORT.contains(sortField)) {<br>    throw new BizException("非法排序字段");<br>}</code></pre>', '⚠️') +

'<h4>MyBatis-Plus 常用配置与插件</h4>' +
PRE('java',
`@Configuration
@MapperScan("com.example.**.mapper")        // 指定 Mapper 扫描路径
public class MybatisPlusConfig {

    /** 分页插件（必须配，否则 Page 查询不生效，返回全量数据） */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 防止全表更新与删除（生产必须加！）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

    /** 自动填充创建时间、更新时间 */
    @Component
    public class MyMetaObjectHandler implements MetaObjectHandler {
        @Override
        public void insertFill(MetaObject metaObject) {
            this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
            this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
        @Override
        public void updateFill(MetaObject metaObject) {
            this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
    }
}`) +

BOX('danger', '分页插件不生效的经典坑',
'<p>如果忘记配置 <code>PaginationInnerInterceptor</code>，MyBatis-Plus 的 <code>Page</code> 查询<strong>不会报错，但会返回全量数据</strong>。这是一个极其危险的静默失败——生产上可能导致全表扫描 + OOM。</p>' +
'<p><strong>排查方法</strong>：打开 SQL 日志（<code>logging.level.com.example.mapper=DEBUG</code>），看实际执行的 SQL 有没有 <code>LIMIT</code>。</p>', '⚠️') +

'<h4>乐观锁与逻辑删除</h4>' +
PRE('java',
`@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.ASSIGN_ID)          // 雪花算法生成 ID
    private Long id;

    /** 乐观锁版本号：必须标 @Version */
    @Version
    private Integer version;

    /** 逻辑删除标记：0 未删除，1 已删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

// 使用乐观锁：先查出来，改完再更新，MP 会自动带 version 条件
// UPDATE t_order SET amount = ?, version = version + 1
//   WHERE id = ? AND version = ?
// 返回影响行数 0 表示被其他线程改过，需要重试
Order order = orderMapper.selectById(1L);
order.setAmount(new BigDecimal("100"));
int rows = orderMapper.updateById(order);
if (rows == 0) {
    throw new BizException("数据已被修改，请重试");
}`) +
PRE('yaml',
`mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted      # 全局逻辑删除字段
      logic-delete-value: 1            # 已删除
      logic-not-delete-value: 0        # 未删除`) +

BOX('warn', '逻辑删除的坑',
'<p>逻辑删除后，<code>selectById</code> 会自动带上 <code>WHERE deleted = 0</code>，看起来很方便。但：</p>' +
'<p>① <strong>唯一索引会冲突</strong>：删除后想再插入相同业务键的数据会被唯一索引拦住。解法：把 <code>deleted</code> 字段放进唯一索引，删除时写入时间戳而非固定 1。</p>' +
'<p>② <strong>数据量膨胀</strong>：表会越来越大，需要定期归档。</p>' +
'<p>③ <strong>关联查询容易漏</strong>：手写 SQL 的 join 不会自动带逻辑删除条件。</p>', '⚠️')
});

// ---------- 4.4 ----------
day4.sections.push({
  id: 's4-4',
  title: '4.4 连接池调优（HikariCP）',
  html:
'<p>Spring Boot 2.x 起默认使用 <strong>HikariCP</strong>（性能最好的连接池）。调优参数就那几个，但讲清楚原理能在面试中加分。</p>' +
'<h4>核心参数</h4>' +
PRE('yaml',
`spring:
  datasource:
    url: jdbc:mysql://db:3306/order?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: my_password_here
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      # ===== 连接数 =====
      maximum-pool-size: 20            # 最大连接数（核心参数）
      minimum-idle: 10                 # 最小空闲连接数
      # ===== 超时 =====
      connection-timeout: 30000        # 获取连接超时（ms），默认 30s
      idle-timeout: 600000             # 空闲连接超时（ms），默认 10min
      max-lifetime: 1800000            # 连接最大存活时间（ms），默认 30min
      # ===== 健康检查 =====
      connection-test-query: SELECT 1  # 校验连接是否可用
      pool-name: OrderHikariPool       # 连接池名称（便于排查）`) +

BOX('interview', 'maximum-pool-size 到底该设多少？',
'<p>这是最能体现深度的问题。很多人的答案是「越大越好」，这是错的。</p>' +
'<p><strong>正确思路</strong>：</p>' +
'<p>① <strong>连接数不是越多越好</strong>。数据库的连接是有限资源，每个连接占内存和线程。连接数过多反而因为锁竞争、上下文切换导致性能下降。PostgreSQL 官方甚至建议 <code>连接数 = 核心数 * 2 + 有效磁盘数</code>。</p>' +
'<p>② <strong>计算公式</strong>：假设 QPS 需要 5000，单个请求 DB 操作耗时 20ms，则并发连接数约等于 5000 x 0.02 = 100。但要考虑实例数——如果部署 5 个实例，每个实例配 20 个连接就够（总 100）。</p>' +
'<p>③ <strong>必须考虑数据库上限</strong>：MySQL 默认 <code>max_connections=151</code>。如果 10 个服务实例每个配 50，就是 500，直接打爆数据库。这是生产事故的常见原因。</p>' +
'<p>④ <strong>实践建议</strong>：单实例 10-20 个（业务简单）到 50 个（高并发服务），配合压测调整。<strong>关键是「所有实例的最大连接数之和小于数据库 max_connections 的 80%」</strong>。</p>', '🎯') +

BOX('danger', 'max-lifetime 必须小于数据库的 wait_timeout',
'<p>MySQL 默认 <code>wait_timeout=28800</code>（8 小时），Hikari 默认 <code>max-lifetime=30min</code>，看起来没问题。</p>' +
'<p>但如果数据库端配置被改小（有些云数据库默认 60 秒或 300 秒），而连接池不知道，就会拿到<strong>已被数据库关闭的死连接</strong>，报 <code>Communications link failure</code>。</p>' +
'<p><strong>规范：Hikari 的 max-lifetime 应比数据库的 wait_timeout 小 30 秒以上</strong>，保证连接在数据库关闭它之前被连接池主动回收。</p>', '⚠️') +

'<h4>连接池监控（生产必做）</h4>' +
PRE('yaml',
`management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    tags:
      application: order-service`) +
'<p>接入 Prometheus 后关注这几个指标：</p>' +
'<ul>' +
'<li><code>hikaricp_connections_active</code> 活跃连接数——持续接近 maximum 说明连接不够</li>' +
'<li><code>hikaricp_connections_pending</code> 等待获取连接的线程数——<strong>大于 0 就要告警</strong></li>' +
'<li><code>hikaricp_connections_timeout_total</code> 获取连接超时次数——<strong>大于 0 说明已经影响业务</strong></li>' +
'</ul>'
});

// ---------- 4.5 ----------
day4.sections.push({
  id: 's4-5',
  title: '4.5 多数据源与分库分表',
  html:
'<p>你做过 Oracle 到 OceanBase / GoldenDB 的迁移，这是很好的面试素材。这一节讲清楚多数据源的技术实现。</p>' +
'<h4>方案一：AbstractRoutingDataSource 动态数据源</h4>' +
PRE('java',
`/** 1. 定义动态数据源：根据 ThreadLocal 决定用哪个库 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }
}

/** 2. ThreadLocal 持有当前数据源 key */
public class DataSourceContextHolder {
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void set(String key) { CONTEXT.set(key); }
    public static String get() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }      // 必须清理！
}

/** 3. 配置多数据源 */
@Configuration
public class DataSourceConfig {

    @Bean("master")
    @ConfigurationProperties("spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean("slave")
    @ConfigurationProperties("spring.datasource.slave")
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @Primary                                  // 默认数据源
    public DataSource dynamicDataSource(
            @Qualifier("master") DataSource master,
            @Qualifier("slave") DataSource slave) {
        Map<Object, Object> targetMap = new HashMap<>();
        targetMap.put("master", master);
        targetMap.put("slave", slave);
        DynamicDataSource ds = new DynamicDataSource();
        ds.setTargetDataSources(targetMap);
        ds.setDefaultTargetDataSource(master);
        return ds;
    }
}

/** 4. 自定义注解 + AOP 切换数据源 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DS {
    String value() default "master";
}

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)      // 必须在事务切面之前执行
public class DataSourceAspect {

    @Around("@annotation(ds)")
    public Object around(ProceedingJoinPoint pjp, DS ds) throws Throwable {
        try {
            DataSourceContextHolder.set(ds.value());
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clear();      // 必须清理
        }
    }
}`) +
PRE('java',
`@Service
public class UserService {

    @DS("master")
    public void insert(User user) { userMapper.insert(user); }

    @DS("slave")                    // 读走从库
    public User getById(Long id) { return userMapper.selectById(id); }
}`) +

BOX('danger', '多数据源 + 事务的三个坑',
'<p><strong>① 数据源切换必须发生在事务开启之前。</strong>因为事务一旦开启，连接就已经绑定到 ThreadLocal 了。所以切面 <code>@Order</code> 必须比事务切面<strong>更靠前</strong>（值更小）。</p>' +
'<p><strong>② 跨库事务无法用本地事务解决。</strong><code>@Transactional</code> 只对当前数据源有效，跨库写必须用分布式事务（Seata、TCC、消息最终一致性）。</p>' +
'<p><strong>③ ThreadLocal 必须 clear。</strong>和方法调用栈绑定，用 try-finally 保证清理，否则线程复用会导致后续请求走错库——这个 bug 极难排查。</p>', '⚠️') +

BOX('tip', '结合你的迁移经验怎么答',
'<p>面试问「做过数据库迁移吗」，可以这样组织：</p>' +
'<p>「做过 Oracle 到 OceanBase、GoldenDB 的迁移。<strong>技术上主要挑战有三点</strong>：① <strong>语法兼容</strong>——Oracle 的 <code>ROWNUM</code>、<code>NVL</code>、<code>DECODE</code>、序列 <code>SEQ.NEXTVAL</code> 都需要改写；② <strong>数据类型差异</strong>——<code>NUMBER</code>、<code>VARCHAR2</code>、<code>DATE</code>（含时分秒）要映射到目标库类型；③ <strong>性能回归</strong>——执行计划不同，部分复杂 SQL 需要重建索引或改写（如把 <code>OR</code> 改成 <code>UNION ALL</code>）。</p>' +
'<p><strong>工程上</strong>：用双写 + 数据校验（抽样比对 + 全量 checksum）保证一致性，灰度切流逐步放量，保留回滚能力。」</p>', '💡') +

QA(1, 'Spring 事务传播行为有哪些？REQUIRED 和 REQUIRES_NEW 区别？',
'<p>7 种：REQUIRED（默认，有则加入无则新建）、REQUIRES_NEW（挂起当前，新建独立事务）、NESTED（Savepoint 嵌套）、SUPPORTS、NOT_SUPPORTED、MANDATORY、NEVER。</p>' +
'<p>REQUIRED 与 REQUIRES_NEW 的核心区别：<strong>REQUIRED 复用外层事务连接，内层失败会导致整个事务回滚；REQUIRES_NEW 挂起外层事务，用自己的新连接开独立事务，外层回滚不影响内层。</strong></p>' +
'<p>典型场景：记录操作日志。业务失败要回滚，但日志必须保留，就用 REQUIRES_NEW。</p>') +

QA(2, '@Transactional 什么时候会失效？',
'<p>八种场景：自调用、非 public 方法、final/static 方法、异常被 try-catch 吞掉、抛受检异常未配 rollbackFor、类未被 Spring 管理、数据库引擎不支持事务（MyISAM）、多线程调用。</p>' +
'<p>根因是事务基于 AOP 代理，凡是让代理增强失效或异常无法传播的情况都会失效。</p>' +
'<p>生产规范：显式写 <code>rollbackFor = Exception.class</code>，禁止吞异常，禁止事务方法内自调用。</p>')
});

const out = '\n/* ============================================================\n' +
'   DAY 4：数据访问与事务\n' +
'   ============================================================ */\n' +
'CHAPTERS.push(' + JSON.stringify(day4, null, 2) + ');\n';

fs.appendFileSync(TARGET, out, 'utf8');
console.log('Day4 已重新写入（JSON 安全模式），追加字节:', Buffer.byteLength(out));
