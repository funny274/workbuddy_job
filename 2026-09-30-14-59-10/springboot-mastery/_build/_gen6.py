import io, os

TARGET = r"C:/Users/issuser/WorkBuddy/2026-09-30-14-59-10/springboot-mastery/content.js"

part = r'''
/* ============================================================
   DAY 6：大厂场景题攻坚
   ============================================================ */
CHAPTERS.push({
  id: "day6",
  badge: "D6",
  badgeLabel: "Day 6",
  badgeType: "warm",
  title: "大厂场景题攻坚 · 高并发 / 分布式 / 性能调优",
  intro: "今天不谈语法，只谈「生产事故」和「架构决策」。这些是大厂三面、四面的核心考察内容，也是区分「会用框架」和「能扛系统」的分界线。每题都给出完整的答题框架。",
  sections: [
    {
      id: "s6-1",
      title: "6.1 高并发接口设计：秒杀场景完整方案",
      html:
'<p>秒杀是大厂面试的经典场景题，几乎必问。它考察的是<strong>分层削峰</strong>的架构思维。</p>'+

'<h4>核心矛盾</h4>'+
'<p>瞬间流量（10 万 QPS）远大于数据库承载能力（几千 TPS），必须在链路上层层拦截。</p>'+

'<h4>五层削峰架构</h4>'+
'<ol class="steps">'+
'<li><div class="st-t">第一层：CDN + 静态化</div>商品详情页静态化推到 CDN，只有「下单」按钮走后端。这一层就能挡掉 90% 的流量。</li>'+
'<li><div class="st-t">第二层：网关限流 + 黑名单</div>Nginx / Spring Cloud Gateway 做 IP 限流、用户频次限流；拦截黄牛（风控识别）。</li>'+
'<li><div class="st-t">第三层：Redis 预扣库存（核心）</div>库存预热到 Redis，用 <strong>Lua 脚本</strong>保证「判断库存 + 扣减」的原子性。这一步挡住绝大部分无效请求。</li>'+
'<li><div class="st-t">第四层：MQ 异步下单</div>扣减成功后不直接写库，而是发消息到 RocketMQ，由消费端异步创建订单。实现流量削峰填谷。</li>'+
'<li><div class="st-t">第五层：数据库最终扣减 + 唯一索引兜底</div>消费端落库时用 <code>UPDATE stock = stock - 1 WHERE stock > 0</code> 兜底，并用唯一索引防重复下单。</li>'+
'</ol>'+

'<h4>核心代码：Redis Lua 原子扣减</h4>'+
PRE("java",
`@Service
public class SeckillService {

    private static final String STOCK_KEY = "seckill:stock:";
    private static final String USER_SET_KEY = "seckill:users:";

    private final StringRedisTemplate redisTemplate;
    private final RocketMQTemplate mqTemplate;

    /** Lua 脚本：原子执行「判断重复购买 + 判断库存 + 扣减 + 记录用户」 */
    private static final String SECKILL_SCRIPT =
        // 1. 判断用户是否已经买过
        "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then " +
        "   return -2 " +                       // -2 表示重复下单
        "end " +
        // 2. 判断库存
        "local stock = tonumber(redis.call('get', KEYS[1])) " +
        "if stock == nil or stock <= 0 then " +
        "   return -1 " +                       // -1 表示库存不足
        "end " +
        // 3. 扣减库存 + 记录用户
        "redis.call('decr', KEYS[1]) " +
        "redis.call('sadd', KEYS[2], ARGV[1]) " +
        "return 0";                             // 0 表示成功

    public SeckillResult seckill(Long seckillId, Long userId) {
        String stockKey = STOCK_KEY + seckillId;
        String userSetKey = USER_SET_KEY + seckillId;

        // 执行 Lua 脚本（原子操作）
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(SECKILL_SCRIPT, Long.class),
                Arrays.asList(stockKey, userSetKey),
                userId.toString());

        if (result == null) throw new BizException("系统异常");
        if (result == -2) throw new BizException("您已参与过本次秒杀");
        if (result == -1) throw new BizException("商品已售罄");

        // 扣减成功，发异步消息创建订单（削峰）
        SeckillMessage msg = new SeckillMessage(seckillId, userId);
        mqTemplate.sendOneWay("SECKILL_ORDER_TOPIC", JSON.toJSONString(msg));

        return SeckillResult.accepted();     // 返回「排队中」，前端轮询结果
    }
}`)+

'<h4>为什么必须用 Lua 脚本</h4>'+
'<div class="cols">'+
'<div class="col bad"><h5>❌ 分步操作（有并发问题）</h5>'+
PRE("java",
`// 两个线程同时执行：
Long stock = redis.get(stockKey);   // 都读到 1
if (stock > 0) {
    // 都判断通过 → 超卖！
    redis.decr(stockKey);           // 变成 -1
}`)+
'</div>'+
'<div class="col good"><h5>✅ Lua 脚本（原子）</h5>'+
PRE("java",
`// Redis 单线程执行 Lua，
// 判断和扣减是一个原子整体，
// 不存在中间状态被其他请求插入`)+
'</div></div>'+

BOX("interview","秒杀场景的追问清单",
'<p><strong>Q：Redis 扣减成功但消息发送失败怎么办？</strong><br>'+
'A：用本地消息表（事务消息）——在同一事务里写「扣减记录」和「待发消息」，后台定时任务补偿发送。或者先落库再扣 Redis。</p>'+
'<p><strong>Q：Redis 挂了怎么办？</strong><br>'+
'A：Redis 集群 + 哨兵保证高可用；极端情况降级为「直接查库 + 数据库行锁」，牺牲性能保正确性。同时用 <code>UPDATE ... WHERE stock > 0</code> 做最终兜底，绝不允许超卖。</p>'+
'<p><strong>Q：怎么防止同一用户重复抢？</strong><br>'+
'A：Lua 脚本里的 <code>SISMEMBER</code> 判断（如上）；数据库层再加 <code>UNIQUE KEY (seckill_id, user_id)</code> 唯一索引兜底。</p>'+
'<p><strong>Q：前端怎么知道结果？</strong><br>'+
'A：异步下单场景下，立即返回「排队中」，前端轮询订单结果接口（或长连接推送）。</p>','🎯')
    },
    {
      id: "s6-2",
      title: "6.2 分布式锁的三种实现与选型",
      html:
'<div class="tbl-wrap"><table><thead><tr><th>实现</th><th>原理</th><th>优点</th><th>缺点</th><th>适用</th></tr></thead><tbody>'+
'<tr><td><strong>Redis</strong></td><td>SETNX + 过期时间</td><td>性能最高（10万+ QPS）、实现简单</td><td>主从切换可能丢锁；非强一致</td><td>绝大多数场景</td></tr>'+
'<tr><td><strong>ZooKeeper</strong></td><td>临时顺序节点 + Watch</td><td><strong>强一致</strong>、锁释放及时（会话断开自动删节点）</td><td>性能较低、依赖 ZK 集群</td><td>对一致性要求极高</td></tr>'+
'<tr><td><strong>数据库</strong></td><td>唯一索引 / <code>SELECT ... FOR UPDATE</code></td><td>实现最简单、无需额外组件</td><td>性能差、易死锁、DB 压力大</td><td>并发极低或已有 DB 兜底</td></tr>'+
'</tbody></table></div>'+

'<h4>Redis 分布式锁的三个致命问题与解法</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>问题</th><th>场景</th><th>解法</th></tr></thead><tbody>'+
'<tr><td><strong>误删别人的锁</strong></td><td>A 超时释放，B 拿到锁，A 执行完删了 B 的锁</td><td>value 存 UUID+线程ID，删除前用 <strong>Lua 脚本校验</strong></td></tr>'+
'<tr><td><strong>锁过期业务未完</strong></td><td>业务跑了 15s，锁 10s 就过期了</td><td><strong>看门狗自动续期</strong>（Redisson 每 10s 续到 30s）</td></tr>'+
'<tr><td><strong>主从切换丢锁</strong></td><td>主节点写入锁后宕机，锁还没同步到从，从升主后另一线程拿到锁</td><td>RedLock（多节点投票）；<strong>或接受该风险 + 业务兜底</strong></td></tr>'+
'</tbody></table></div>'+

'<h4>手写一个正确的 Redis 锁（面试手写题）</h4>'+
PRE("java",
`/**
 * 正确的 Redis 分布式锁实现
 * 要点：唯一 value + Lua 原子删除 + 续期
 */
public class RedisLock {

    private static final String UNLOCK_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "   return redis.call('del', KEYS[1]) " +
        "else " +
        "   return 0 " +
        "end";

    private final StringRedisTemplate redis;
    private final String lockKey;
    /** 唯一标识：UUID + 线程ID，确保只删自己的锁 */
    private final String lockValue;
    /** 锁过期时间（ms） */
    private final long expireMs;
    /** 续期任务 */
    private ScheduledExecutorService renewer;

    public RedisLock(StringRedisTemplate redis, String lockKey, long expireMs) {
        this.redis = redis;
        this.lockKey = lockKey;
        this.expireMs = expireMs;
        this.lockValue = UUID.randomUUID() + ":" + Thread.currentThread().getId();
    }

    /** 加锁：SET key value NX PX expireMs （一条命令保证原子性） */
    public boolean tryLock(long waitMs) {
        long deadline = System.currentTimeMillis() + waitMs;
        do {
            Boolean ok = redis.opsForValue().setIfAbsent(
                    lockKey, lockValue, Duration.ofMillis(expireMs));
            if (Boolean.TRUE.equals(ok)) {
                startRenewal();          // 启动看门狗续期
                return true;
            }
            try {
                Thread.sleep(50);        // 自旋重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return false;
    }

    /** 解锁：Lua 脚本保证「校验 + 删除」原子性 */
    public void unlock() {
        stopRenewal();
        redis.execute(new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                Collections.singletonList(lockKey), lockValue);
    }

    /** 看门狗：每 expireMs/3 续期一次 */
    private void startRenewal() {
        renewer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lock-renewer");
            t.setDaemon(true);
            return t;
        });
        renewer.scheduleAtFixedRate(() -> {
            // 只有锁还在自己手上才续期
            String script =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "   return redis.call('pexpire', KEYS[1], ARGV[2]) " +
                "else return 0 end";
            redis.execute(new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(lockKey),
                    lockValue, String.valueOf(expireMs));
        }, expireMs / 3, expireMs / 3, TimeUnit.MILLISECONDS);
    }

    private void stopRenewal() {
        if (renewer != null) renewer.shutdownNow();
    }
}`)+

BOX("tip","生产建议：直接用 Redisson",
'<p>上面这段代码是「理解原理」用的。<strong>生产上直接用 Redisson</strong>，它已经正确实现了这些细节，并且提供了可重入锁、公平锁、读写锁、联锁（MultiLock）等高级能力。</p>'+
'<pre style="margin:8px 0"><code>RLock lock = redissonClient.getLock("order:" + orderId);<br>lock.lock(10, TimeUnit.SECONDS);   // 注意：指定了 leaseTime 就没有看门狗<br>try { /* 业务 */ } finally { lock.unlock(); }</code></pre>'+
'<p><strong>关键坑</strong>：<code>lock()</code> 不传 leaseTime 才有看门狗自动续期；传了 leaseTime 就固定过期，业务超时会导致锁提前释放。</p>','💡')+

QA(1, "Redis 分布式锁有什么问题？怎么解决？",
'<p>三个核心问题：</p>'+
'<p>① <strong>误删别人的锁</strong>：A 业务超时锁自动释放，B 拿到锁，A 执行完后删除 key 把 B 的锁删了。解法：value 存唯一标识（UUID+线程ID），删除前用 Lua 脚本校验 value 是否匹配。</p>'+
'<p>② <strong>锁提前过期</strong>：业务执行时间超过锁过期时间。解法：看门狗机制自动续期（Redisson 默认 30 秒，每 10 秒续一次）。</p>'+
'<p>③ <strong>主从切换丢锁</strong>：主节点加锁后未同步就宕机，从节点升主后其他线程能拿到同一把锁。解法：RedLock 对多个独立 Master 加锁，超过半数成功才算成功；但 RedLock 本身有争议（Martin Kleppmann 质疑其依赖时钟），很多团队选择接受该风险并用业务幂等兜底。</p>')+

QA(2, "为什么推荐 Redisson 而不是自己实现？",
'<p>因为自己实现容易漏掉细节：Lua 原子性、唯一 value、看门狗续期、可重入（同一线程多次加锁要计数）、锁释放通知其他等待线程。Redisson 用 <code>RMap + 发布订阅</code> 实现了阻塞等待（避免无脑自旋消耗 CPU），并正确处理了上述所有边界情况。</p>')
    },
    {
      id: "s6-3",
      title: "6.3 接口性能优化：从 3 秒到 50 毫秒",
      html:
'<p>面试场景题：「我们有个接口响应 3 秒，你怎么优化？」这是我建议你准备的标准答题框架——<strong>先定位，再分层优化</strong>。</p>'+

'<h4>第一步：定位瓶颈（不能上来就优化）</h4>'+
'<ol class="steps">'+
'<li><div class="st-t">看监控指标</div>QPS、RT、错误率、CPU、内存、GC 频率、连接池等待数。先确认是「整体慢」还是「某段时间慢」。</li>'+
'<li><div class="st-t">看调用链</div>用 SkyWalking / Zipkin 看这一次请求在哪个环节耗时最长——是 DB、Redis、还是下游 RPC。</li>'+
'<li><div class="st-t">看慢 SQL 日志</div>如果瓶颈在 DB，开启慢查询日志（<code>long_query_time=1</code>），或 MySQL 的 <code>performance_schema</code>。</li>'+
'<li><div class="st-t">看线程栈</div><code>jstack</code> 看线程在等什么（等锁？等 IO？等连接池？）。</li>'+
'</ol>'+

'<h4>第二步：分层优化清单</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>层级</th><th>优化手段</th><th>预期收益</th></tr></thead><tbody>'+
'<tr><td><strong>SQL 层</strong></td><td>加索引、避免 <code>SELECT *</code>、避免函数作用于索引列、用 <code>EXPLAIN</code> 检查执行计划、大表分页用「游标分页」而非 <code>LIMIT 100000,10</code></td><td>最高，常见 10-100 倍</td></tr>'+
'<tr><td><strong>数据库层</strong></td><td>读写分离、分库分表、冗余字段避免 join</td><td>高</td></tr>'+
'<tr><td><strong>缓存层</strong></td><td>热点数据加 Redis、本地缓存 Caffeine、多级缓存</td><td>高</td></tr>'+
'<tr><td><strong>应用层</strong></td><td>无状态化便于扩容、合理线程池、异步化（不阻塞主流程）</td><td>中</td></tr>'+
'<tr><td><strong>并发层</strong></td><td>串行改并行（<code>CompletableFuture</code> 聚合多个 RPC）</td><td>中-高</td></tr>'+
'<tr><td><strong>数据量层</strong></td><td>减少返回字段（DTO 裁剪）、分页、按需加载</td><td>中</td></tr>'+
'<tr><td><strong>JVM 层</strong></td><td>合理堆大小、更换 GC（G1/ZGC）、减少对象创建</td><td>中</td></tr>'+
'</tbody></table></div>'+

'<h4>实战：串行改并行（最见效的手段之一）</h4>'+
PRE("java",
`// 优化前：串行调用 3 个 RPC，总耗时 = 200 + 300 + 150 = 650ms
public OrderDetailVO getOrderDetail(Long orderId) {
    Order order = orderService.get(orderId);                 // 200ms
    User user = userService.get(order.getUserId());          // 300ms
    List<Item> items = itemService.list(orderId);            // 150ms
    return assemble(order, user, items);
}

// 优化后：并行调用，总耗时 = max(200, 300, 150) = 300ms
public OrderDetailVO getOrderDetail(Long orderId) {
    Order order = orderService.get(orderId);                 // 必须先拿到 order 才能并行

    // 用 CompletableFuture 并行执行两个独立调用
    CompletableFuture<User> userFuture = CompletableFuture
            .supplyAsync(() -> userService.get(order.getUserId()), bizExecutor);
    CompletableFuture<List<Item>> itemsFuture = CompletableFuture
            .supplyAsync(() -> itemService.list(orderId), bizExecutor);

    // 等待两个都完成（设置超时，避免无限等待）
    try {
        CompletableFuture.allOf(userFuture, itemsFuture)
                .get(500, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        // 超时降级：返回不含用户信息的订单（保证核心可用）
        userFuture.cancel(true);
        return assemble(order, null, itemsFuture.getNow(Collections.emptyList()));
    } catch (Exception e) {
        throw new BizException("查询失败");
    }

    return assemble(order, userFuture.join(), itemsFuture.join());
}

// 必须用自定义线程池，不能用默认的 ForkJoinPool.commonPool（会被其他任务挤占）
private final Executor bizExecutor = new ThreadPoolExecutor(
        16, 32, 60, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(200),
        new ThreadFactoryBuilder().setNameFormat("biz-async-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy());`)+

BOX("danger","CompletableFuture 的三个坑",
'<p><strong>① 必须用自定义线程池。</strong>默认 <code>ForkJoinPool.commonPool()</code> 的线程数是 CPU 核数-1，且被全 JVM 共享，IO 密集任务会互相影响。</p>'+
'<p><strong>② 必须设置超时。</strong><code>allOf().get()</code> 无超时会永久阻塞，下游服务挂了你的接口也挂了。</p>'+
'<p><strong>③ 异常处理。</strong>CompletableFuture 里的异常会被包装成 <code>CompletionException</code>，必须 <code>exceptionally()</code> 或 <code>handle()</code> 处理，否则静默丢失。</p>','⚠️')+

'<h4>实战：大表深度分页优化</h4>'+
PRE("sql",
`-- 慢：LIMIT 1000000, 10 会扫描并丢弃前 100 万行
SELECT * FROM t_order ORDER BY id LIMIT 1000000, 10;

-- 快：游标分页（记住上次的最大 id），只扫描 10 行
SELECT * FROM t_order WHERE id > 1000000 ORDER BY id LIMIT 10;

-- 或者用子查询先定位 id（覆盖索引避免回表）
SELECT o.* FROM t_order o
JOIN (SELECT id FROM t_order ORDER BY id LIMIT 1000000, 10) t ON o.id = t.id;`)+

BOX("ok","面试答题框架（背这个结构）",
'<p>「我一般按四步走：</p>'+
'<p><strong>第一步定位</strong>——先看监控（RT、QPS、GC）和调用链，确定瓶颈在哪一层，不能凭感觉优化。</p>'+
'<p><strong>第二步分层排查</strong>——如果是 DB，看慢 SQL 和 <code>EXPLAIN</code>；如果是应用，看线程栈在等什么；如果是下游，看 RPC 耗时分布。</p>'+
'<p><strong>第三步优化</strong>——优先做收益最大的：加索引、加缓存、串行改并行、异步化、减少数据量。</p>'+
'<p><strong>第四步验证</strong>——用压测（JMeter / wrk）对比优化前后，并观察监控指标确认没有引入新问题（比如缓存导致数据不一致）。</p>'+
'<p>实际做过的一个案例：一个订单详情接口 800ms，通过调用链发现 3 个 RPC 串行执行占了 650ms，用 CompletableFuture 并行化后降到 300ms，再加 Redis 缓存用户信息后稳定在 80ms。」</p>','✅')
    },
    {
      id: "s6-4",
      title: "6.4 JVM 与 Tomcat 生产调优",
      html:
'<h4>容器化环境的 JVM 参数（Boot 3.x + JDK 17）</h4>'+
PRE("bash",
`# 推荐参数（容器内，JDK 17）
java -jar app.jar \\
  # ===== 内存：容器环境下建议显式指定，不要依赖默认 1/4 =====
  -Xms2g -Xmx2g \\                       # 堆固定大小，避免动态扩缩容抖动
  -XX:MaxMetaspaceSize=512m \\           # 元空间上限，防类加载泄露
  -XX:MaxDirectMemorySize=512m \\        # 堆外内存上限（Netty/NIO 用）
  # ===== GC：G1 是 JDK 17 默认，一般不用改 =====
  -XX:+UseG1GC \\
  -XX:MaxGCPauseMillis=200 \\            # 目标停顿时间
  -XX:G1HeapRegionSize=8m \\
  -XX:InitiatingHeapOccupancyPercent=45 \\ # 触发并发 GC 的堆占用比
  # ===== OOM 现场保留（关键！）=====
  -XX:+HeapDumpOnOutOfMemoryError \\
  -XX:HeapDumpPath=/var/log/app/heap.hprof \\
  -XX:+ExitOnOutOfMemoryError \\         # OOM 后立即退出，让 K8s 重启（避免僵死）
  # ===== 其他 =====
  -XX:+UseContainerSupport \\            # 识别容器内存限制（JDK 10+ 默认开启）
  -Duser.timezone=Asia/Shanghai \\
  -Dfile.encoding=UTF-8`)+

BOX("interview","为什么容器里必须显式设 -Xmx",
'<p>JDK 10 之前，JVM 识别不到容器的 cgroup 内存限制，会按<strong>物理机内存</strong>的 1/4 来设堆，导致堆远超容器限制，被 K8s OOMKilled。</p>'+
'<p>JDK 10+ 通过 <code>UseContainerSupport</code>（默认开启）能正确识别，但仍然建议<strong>显式指定</strong>，因为：① 堆之外的元空间、直接内存、线程栈也占内存，总内存 = 堆 + 元空间 + 直接内存 + 栈 × 线程数 + JVM 自身开销，容器 limit 要留足余量（一般堆占 limit 的 60-70%）；② 显式设置避免不同 JDK 版本默认行为差异。</p>'+
'<p><strong>反例</strong>：容器 limit 4G，设 <code>-Xmx4g</code>，结果堆还没满就被 OOMKilled（因为还有其他内存占用）。</p>','🎯')+

'<h4>Tomcat 线程池调优</h4>'+
PRE("yaml",
`server:
  tomcat:
    # 最大工作线程数：默认 200
    threads:
      max: 400
      min-spare: 50                # 最小空闲线程，默认 10
    # 最大连接数：默认 8192，表示能同时接受多少连接
    max-connections: 10000
    # 等待队列长度：默认 100，满了会拒绝连接
    accept-count: 200
    # 最大请求头大小
    max-http-header-size: 16KB
    # 连接超时（ms）
    connection-timeout: 20000
  # 优雅停机：等待正在处理的请求完成
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s`)+

'<h4>Tomcat 三个关键参数的关系（面试常问）</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>参数</th><th>含义</th><th>满了会怎样</th></tr></thead><tbody>'+
'<tr><td><code>max-connections</code></td><td>能同时建立的最大连接数（含 keep-alive 空闲连接）</td><td>超过则新连接被放入 <code>accept-count</code> 队列</td></tr>'+
'<tr><td><code>accept-count</code></td><td>TCP 已连接但未被处理的等待队列</td><td>超过则客户端连接被拒绝（connection reset）</td></tr>'+
'<tr><td><code>threads.max</code></td><td>真正处理请求的工作线程数</td><td>请求在队列中等待，等不到则超时</td></tr>'+
'</tbody></table></div>'+
'<p class="mini">关系：<code>max-connections</code> >= <code>threads.max</code> + <code>min-spare</code>，否则线程数成为瓶颈。</p>'+

BOX("tip","线程数怎么定",
'<p>Tomcat 默认 200 线程对绝大多数场景够用。<strong>加大线程数不总是好事</strong>——线程上下文切换有成本，且每个线程占约 1MB 栈空间。</p>'+
'<p>实践方法：压测找到「QPS 不再增长但响应时间开始上升」的拐点，那个点的线程数就是最优值。一般 IO 密集的服务配 200-500，CPU 密集的 50-100 就够。</p>'+
'<p><strong>Boot 3.2 + JDK 21 的虚拟线程</strong>：如果开启了 <code>spring.threads.virtual.enabled=true</code>，Tomcat 会用虚拟线程处理请求，此时 <code>threads.max</code> 的约束就被放宽了（虚拟线程可以创建百万级），IO 密集场景吞吐提升明显。</p>','💡')+

'<h4>优雅停机（生产必须配置）</h4>'+
'<p>K8s 滚动更新时，如果不配置优雅停机，正在处理的请求会被直接断开，用户看到 502。</p>'+
PRE("yaml",
`server:
  shutdown: graceful            # 开启优雅停机
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # 最多等待 30 秒`)+
'<p>配合 K8s 的 <code>preStop</code> 钩子（sleep 几秒等负载均衡摘除），才能实现真正的无损发布。</p>'
    },
    {
      id: "s6-5",
      title: "6.5 云原生部署与 GraalVM 原生镜像",
      html:
'<h4>Dockerfile 最佳实践（分层构建）</h4>'+
PRE("bash",
`# ===== 阶段一：构建 =====
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
# 先只复制 pom.xml，利用 Docker 层缓存加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B
# 再复制源码编译
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== 阶段二：运行（镜像小得多）=====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安全：用非 root 用户运行（K8s 安全基线要求）
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /build/target/*.jar app.jar

# 时区
ENV TZ=Asia/Shanghai

# JVM 参数通过环境变量注入，便于灵活调整
ENV JAVA_OPTS="-Xms1g -Xmx1g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"

EXPOSE 8080
# 用 exec 形式（不是 shell 形式），确保 JVM 是 PID 1，能正确接收 SIGTERM
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]`)+

BOX("danger","Docker 部署的三个坑",
'<p><strong>① PID 1 问题。</strong>如果用 <code>ENTRYPOINT java -jar app.jar</code> 的 shell 形式，JVM 不是 PID 1，收不到 <code>SIGTERM</code>，优雅停机会失效。<strong>必须用 exec 形式</strong>（JSON 数组），或加 <code>--init</code>。</p>'+
'<p><strong>② 时区错误。</strong>容器默认 UTC，日志时间比北京时间少 8 小时，排查问题时会看错时间线。<strong>必须设 <code>TZ=Asia/Shanghai</code></strong> 或 JVM 参数 <code>-Duser.timezone</code>。</p>'+
'<p><strong>③ 用 root 运行。</strong>违反安全基线，容器逃逸风险高。必须创建非 root 用户。</p>','⚠️')+

'<h4>GraalVM 原生镜像（Boot 3.x 正式支持）</h4>'+
'<p>把 Java 应用编译成<strong>本地机器码</strong>，启动时间从 2 秒降到 50 毫秒，内存占用减半。适合 Serverless / FaaS / 快速扩容场景。</p>'+
PRE("bash",
`# 1. 引入 AOT 插件
mvn -Pnative native:compile

# 2. 或者用 Buildpacks 直接构建镜像（无需本地装 GraalVM）
mvn spring-boot:build-image -Pnative

# 3. 启动时间对比
# 传统 JVM:  Started in 2.135 seconds
# 原生镜像:  Started in 0.058 seconds`)+

'<div class="tbl-wrap"><table><thead><tr><th>维度</th><th>JVM 模式</th><th>原生镜像</th></tr></thead><tbody>'+
'<tr><td>启动时间</td><td>1-3 秒</td><td><strong>30-100 毫秒</strong></td></tr>'+
'<tr><td>内存占用</td><td>200MB+</td><td><strong>50-80MB</strong></td></tr>'+
'<tr><td>峰值吞吐</td><td><strong>更高</strong>（JIT 优化）</td><td>略低</td></tr>'+
'<tr><td>构建时间</td><td>几十秒</td><td><strong>几分钟</strong></td></tr>'+
'<tr><td>兼容性</td><td>无限制</td><td><strong>反射/动态代理需配置</strong>，部分库不支持</td></tr>'+
'</tbody></table></div>'+

BOX("warn","原生镜像的限制",
'<p>核心是<strong>AOT（提前编译）无法处理运行时的动态特性</strong>：</p>'+
'<p>① <strong>反射</strong>：需要 <code>reflect-config.json</code> 声明，或 Spring 的 AOT 处理器自动推断（但自定义反射仍需手工配置）。</p>'+
'<p>② <strong>动态代理</strong>：CGLIB 代理在原生镜像里有兼容问题。</p>'+
'<p>③ <strong>延迟加载 / 条件装配</strong>：构建时就固定了，运行时无法改变。</p>'+
'<p>④ <strong>第三方库兼容</strong>：一些依赖字节码操作的库（如老版本 MyBatis、部分监控 Agent）不支持。</p>'+
'<p>所以原生镜像更适合<strong>无状态、依赖简单</strong>的服务；复杂业务系统仍建议用 JVM 模式。</p>','⚠️')

    }
  ]
});
'''

with io.open(TARGET, "a", encoding="utf-8") as f:
    f.write(part)
print("Day6 appended, size =", os.path.getsize(TARGET))
