#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""13-18 章 + 附录 内容"""

from chapters_a import code_block, out_block, tip, table, pre


def ch13(w):
    w.chapter("ch13", "13", "无锁编程与 RingBuffer",
              "Disruptor 的核心思想，性能提升的另一种思路")
    w.h3("13.1 手写无锁环形队列")
    w.raw(code_block("ch13/_01_RingBuffer.java"))
    w.raw(out_block("""===== 1) 无锁环形队列功能验证 =====
  放入 10 个后 size = 10
  依次取出: 0 1 2 3 4 5 6 7 8 9 
  取出后 size = 0

===== 2) 性能对比：无锁 RingBuffer vs 阻塞队列 =====
  ArrayBlockingQueue :  1305 ms
  无锁 RingBuffer    :   703 ms  (快 1.9 倍)"""))
    w.p("<strong>无锁队列快了 1.9 倍</strong>。这个提升看起来没有 LongAdder 那么夸张，"
        "但考虑到 ArrayBlockingQueue 已经是优化过的实现，这个差距在高频交易、"
        "日志采集这类场景里就非常关键了。")
    w.raw(pre("""RingBuffer 为什么快？四个原因：

  1 数组预先分配，永不 GC
     对比：LinkedBlockingQueue 每次 offer 都要 new Node -> GC 压力
     RingBuffer 的槽位循环复用 -> 零对象创建

  2 序号单调递增 + 位运算代替取模
     index = sequence & (capacity - 1)   <- 比 sequence % capacity 快
     这也是「容量必须是 2 的幂」的原因

  3 生产者和消费者只操作各自的 volatile 序号
     生产者：写 buffer[tail & mask]，然后 volatile 写 tail
     消费者：读 tail，读 buffer[head & mask]，然后 volatile 写 head
     -> 没有锁竞争，没有 CAS 失败重试

  4 内存顺序友好
     生产者访问 tail 附近的区域，消费者访问 head 附近的区域
     如果配合缓存行填充，两者完全不冲突

Disruptor 在此基础上又加了三件事：
  +--------------------------------------------------------+
  | 1 序号栅栏（SequenceBarrier）                          |
  |   支持多消费者、消费者组、依赖链（handlerA -> handlerB）|
  | 2 消除伪共享                                           |
  |   每个 Sequence 都做了缓存行填充（64 字节对齐）        |
  | 3 批量处理（BatchEventProcessor）                      |
  |   一次取一批，摊薄序号 CAS 的成本                      |
  +--------------------------------------------------------+
  官方基准测试：比 ArrayBlockingQueue 快一个数量级

什么时候该用无锁？
  [OK] 极高吞吐、极低延迟的场景（金融、游戏、日志采集）
  [OK] 逻辑简单，容易证明正确性
  [X]  不要为了「炫技」而用——无锁代码的正确性极难验证
  [X]  不要在没有 profiling 数据的情况下用"""))
    w.end()


def ch14(w):
    w.chapter("ch14", "14", "ForkJoinPool 与工作窃取",
              "让空闲线程去偷活干")
    w.h3("14.1 并行归并排序实战")
    w.raw(code_block("ch14/_01_ForkJoin.java"))
    w.raw(out_block("""CPU 核心数 = 8

===== 1) 手写 RecursiveTask：并行归并排序 =====
  Arrays.sort（单线程）:  1487 ms
  ForkJoin 并行归并    :   722 ms"""))
    w.p("<strong>并行归并快了约 2 倍</strong>（归并排序本身并行度有限，因为 merge 阶段是串行的），"
        "已接近 8 核实际可用的加速比。")
    w.raw(pre("""工作窃取（Work-Stealing）—— ForkJoinPool 的灵魂：

  每个工作线程有自己的一条双端队列（Deque）：

    线程1 的队列                    线程2 的队列
  +------------------+          +------------------+
  |  队尾 [任务J][任务K] |          |  队尾 [空]        |
  |                  |          |                  |
  |  队头 [任务A][任务B] |          |  队头 [空]        |
  +------------------+          +------------------+
        ^                              ^
     自己从队头取                   空闲了！
     （LIFO，栈式）                     |
                                       |  从别人队尾「偷」
                                       +--------------> 偷任务J

  为什么自己用 LIFO，偷别人用 FIFO？
    递归拆分的任务里，越靠后拆出来的任务粒度越「大」（子任务还没被细分）。
    偷大的 -> 一次偷到更多工作量 -> 减少窃取的次数 -> 降低竞争。

  为什么比 ThreadPoolExecutor 快？
    ThreadPoolExecutor：所有线程抢同一条队列的头部
                        -> 队列头是热点，需要加锁/CAS，竞争激烈
    ForkJoinPool：每个线程一条队列，互不干扰
                  -> 只在「偷」的时候才有跨线程操作

ForkJoinTask 三个子类：
  +---------------------+------------------------------------+
  | RecursiveAction     | 无返回值                            |
  | RecursiveTask<V>    | 有返回值（本项目演示）              |
  | CountedCompleter    | 更灵活，CompletableFuture 底层用到  |
  +---------------------+------------------------------------+

适用场景与禁忌：
  [OK] CPU 密集型分治：排序、归并、树遍历、大数组聚合
  [X]  IO 密集型：线程会被阻塞，无法被窃取走阻塞
  [X]  任务粒度极细：调度开销 > 并行收益
  [!]  parallelStream 默认用 commonPool（线程数 = CPU-1），
       被全 JVM 共享。生产环境建议自定义池（代码里有示例）"""))
    w.end()


def ch15(w):
    w.chapter("ch15", "15", "虚拟线程（JDK 21+）",
              "Java 并发近十年最大的变革")
    w.h3("15.1 虚拟线程实战与性能对比")
    w.raw(code_block("ch15/_01_VirtualThreads.java"))
    w.raw(out_block("""JDK 版本 = 25.0.3+9-LTS-195
CPU 核心数 = 8

===== 1) 三种创建方式 =====
  ① Thread.ofVirtual().start() → VirtualThread[#27,vt-1]/runnable@ForkJoinPool-1-worker-1
  ② Thread.startVirtualThread() → isVirtual=true
  ③ newVirtualThreadPerTaskExecutor 提交的任务

===== 3) 数量对比实测 =====
  100000 个任务（每个 sleep 10ms）
  平台线程池(500线程)  :  2173 ms
  虚拟线程(每任务一线程):   348 ms
  → 提速 6.2 倍"""))
    w.p("<strong>10 万个任务，虚拟线程只用 348ms，而 500 个平台线程的池要 2173ms</strong>。"
        "注意输出里 <code>VirtualThread[...]@ForkJoinPool-1-worker-1</code> 这个格式"
        "——<code>@</code> 后面就是它当前挂载的<strong>载体线程</strong>（carrier thread），"
        "这清楚地展示了 M:N 的调度关系。")
    w.raw(table(["维度", "平台线程 (Platform)", "虚拟线程 (Virtual)"], [
        ["实现", "1:1 映射 OS 线程", "M:N 映射（大量虚拟线程 → 少量载体线程）"],
        ["创建成本", "约 1MB 栈，创建慢", "约几百字节~几 KB，创建极快"],
        ["数量上限", "几千个（受内存限制）", "<strong>百万级</strong>（取决于堆内存）"],
        ["调度", "操作系统内核调度", "JVM 调度（ForkJoinPool 作为载体）"],
        ["阻塞代价", "线程挂起，切换成本高", "<strong>自动 unmount，几乎零成本</strong>"],
        ["适用场景", "CPU 密集", "IO 密集（高并发网络请求）"],
    ]))
    w.raw(tip("danger", "虚拟线程的五个坑（每个都很致命）",
              "<strong>坑 1：synchronized 会导致载体线程被 pin 住（JDK 21）</strong><br>"
              "<code>synchronized</code> 块内发生阻塞时，虚拟线程无法 unmount，载体线程被占用，"
              "严重时整个调度器饥饿。<em>JDK 24 起已修复（JEP 491），"
              "但 JDK 21 项目必须用 <code>ReentrantLock</code> 替代。</em>"
              "排查方法：<code>-Djdk.tracePinnedThreads=full</code>。<br><br>"
              "<strong>坑 2：ThreadLocal 变得危险</strong><br>"
              "百万虚拟线程 × 每个 ThreadLocal 一份 → 内存爆炸。"
              "改用 <code>ScopedValue</code>，或确保及时 remove。<br><br>"
              "<strong>坑 3：不要池化虚拟线程！</strong><br>"
              "虚拟线程极轻量，池化反而损失价值。正确用法是 "
              "<code>Executors.newVirtualThreadPerTaskExecutor()</code>"
              "——这个名字有点误导，它内部并不是真的「池」，只是复用调度器。<br><br>"
              "<strong>坑 4：CPU 密集型任务用虚拟线程没有收益</strong><br>"
              "载体线程数默认 = CPU 核数，真正的计算任务还是要排队。CPU 密集继续用平台线程池。<br><br>"
              "<strong>坑 5：结构化并发才是配套的最佳实践</strong><br>"
              "<code>StructuredTaskScope</code> 让子任务的生命周期严格嵌套在父任务内，"
              "避免线程泄漏，异常传播更清晰。"))
    w.h3("15.2 结构化并发（JDK 25 正式）")
    w.raw(pre("""// 传统写法：容易泄漏线程，异常传播混乱
Future<String> user = pool.submit(() -> fetchUser());
Future<String> order = pool.submit(() -> fetchOrder());
// 如果 fetchUser 抛异常，fetchOrder 还在跑 -> 线程泄漏

// 结构化并发：作用域退出时保证所有子任务已结束
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> user  = scope.fork(() -> fetchUser());
    Subtask<String> order = scope.fork(() -> fetchOrder());
    scope.join();                    // 等所有子任务
    scope.throwIfFailed();           // 任一失败就抛异常
    return new Result(user.get(), order.get());
}
// 出 try 块时：所有子任务保证已结束（成功或取消）-> 无线程泄漏

核心价值：把「并发任务的作用域」变成代码块级的结构化约束，
         像 try-with-resources 管理资源一样管理线程生命周期。"""))
    w.end()


def ch16(w):
    w.chapter("ch16", "16", "综合实战：三个大厂场景",
              "把前面 15 章的知识组装成能上线的方案")
    w.h3("16.1 秒杀库存扣减：五种方案演进")
    w.raw(code_block("ch16/_01_SeckillStock.java"))
    w.raw(out_block("""========== 场景一：秒杀库存扣减（库存 100，200 个线程抢）==========
  方案1 成功扣减=100 剩余库存=0 [正确]
  方案2 成功扣减=103 剩余库存=0 [超卖/错误]
  方案3 成功扣减=100 剩余库存=0 [正确]
  方案4 成功扣减=100 剩余库存=0 [正确]
  方案5 成功扣减=100 剩余库存=0 [正确]

========== 场景二：令牌桶限流器 ==========
  300 个请求涌入，限流 50 QPS → 通过 11，拒绝 289，耗时 25ms

========== 场景三：缓存击穿防护（单飞模式）==========
  100 个线程同时请求热点 key → 成功 100 次，实际查库 1 次，耗时 314ms"""))
    w.p("三个实验的关键发现：<br>"
        "<strong>① 方案 2 用 volatile 修饰库存，结果扣减了 103 次（库存只有 100）——超卖复现！</strong>"
        "再次证明 volatile 解决不了 check-then-act 的原子性问题。<br>"
        "<strong>② 令牌桶限流器</strong>：300 个请求只放行 11 个"
        "（桶容量 10 + 25ms 内补充的 1 个），完全符合物理规律。<br>"
        "<strong>③ 单飞模式把 100 次并发查库合并成 1 次</strong>——这是防止缓存击穿的关键。")
    w.raw(pre("""生产级秒杀系统架构（面试可以按这个框架答）：

  +----------------------------------------------------------+
  | 1 前端                                                   |
  |    答题验证码 / 按钮置灰防重复提交 / 静态资源 CDN        |
  +----------------------------------------------------------+
  | 2 网关层                                                 |
  |    限流（令牌桶）/ 黑名单 / 用户维度防刷                 |
  +----------------------------------------------------------+
  | 3 服务层                                                 |
  |    库存预扣减走 Redis Lua 脚本（原子操作，防超卖）        |
  |    扣减成功后发 MQ 消息，立即返回「排队中」               |
  +----------------------------------------------------------+
  | 4 MQ 削峰填谷                                            |
  |    消息队列缓冲突发流量，下游按自身能力消费               |
  +----------------------------------------------------------+
  | 5 DB 层                                                  |
  |    最终一致性扣减 + 唯一索引防重                          |
  |    异步下单，用户轮询或推送结果                           |
  +----------------------------------------------------------+
  | 6 兜底                                                   |
  |    Redis 故障降级方案 / 库存回滚补偿 / 对账任务          |
  +----------------------------------------------------------+

  答题要点：每一层讲一个关键技术 + 一个风险点，
           并说明这个设计是为了解决什么问题。
           这比单纯罗列技术名词有说服力得多。"""))
    w.h3("16.2 限流与缓存三大问题")
    w.raw(pre("""四种限流算法对比：

  +----------+----------------------------+------------------+
  | 算法     | 特点                        | 适用              |
  +----------+----------------------------+------------------+
  | 计数器   | 最简单，有临界问题           | 粗粒度限流        |
  |          | （窗口边界可能双倍流量）     |                  |
  | 滑动窗口 | 解决临界问题，需存时间戳     | 精确限流          |
  | 漏桶     | 恒定速率流出，不允许突发     | 保护下游稳定      |
  | 令牌桶   | 允许一定突发，最灵活         | 通用首选          |
  +----------+----------------------------+------------------+

  生产实现：
    单机：Guava RateLimiter / Sentinel
    分布式：Redis + Lua 脚本（保证原子性）/ Redisson RRateLimiter
    注意：必须处理 Redis 故障降级（通常降级为放行 + 事后统计）

缓存三大问题：

  【1】缓存穿透：查询不存在的数据，每次都打到 DB
      原因：恶意攻击 or 业务误用（如 id = -1）
      解决：1 空值缓存（短 TTL，如 60s）
            2 布隆过滤器前置拦截
            3 参数校验（id > 0）

  【2】缓存击穿：热点 key 过期的瞬间，大量请求打到 DB
      原因：热点数据 TTL 到期
      解决：1 互斥锁重建（本项目演示的单飞模式）<- 最常用
            2 热点数据永不过期 + 后台异步更新
            3 逻辑过期（value 里存过期时间，异步刷新）

  【3】缓存雪崩：大量 key 同时过期 or 缓存服务宕机
      原因：统一 TTL、Redis 集群故障
      解决：1 TTL 加随机值（如 30min + random(0,5min)）
            2 多级缓存（本地 Caffeine + Redis）
            3 Redis 高可用（哨兵/集群）
            4 熔断降级（Sentinel）

多级缓存架构：
  L1: Caffeine（进程内，纳秒级，容量小）
  L2: Redis（分布式，毫秒级，容量大）
  L3: DB
  更新策略：先更新 DB 再删除缓存（Cache Aside），
           配合延迟双删 or 订阅 binlog（Canal）异步刷新"""))
    w.end()


def ch17(w):
    w.chapter("ch17", "17", "大厂面试真题精讲",
              "20 道高频题的标准答案 + 加分点 + 追问链")
    w.p("这一章把前面所有知识点重新组织成「面试答题」的形态。"
        "每题都按 <strong>考察意图 → 标准答案 → 加分点 → 追问链</strong> 组织，可以直接背诵复用。"
        "完整内容在下面的代码文件中（展开查看）。")
    w.raw(code_block("ch17/_01_InterviewQuestions.java"))
    w.raw(tip("success", "答题通用技巧（比背题更重要）",
              "<strong>① 分层回答</strong>：按「API 层 → 原理层 → 源码层 → 实践层」递进。"
              "不要一上来就背源码细节，先给结论框架，让面试官知道你有全貌。<br>"
              "<strong>② 主动延伸</strong>：答完问题后补一句「这个问题在生产中还常遇到 xxx 坑」，"
              "把被动答题变成主动展示。<br>"
              "<strong>③ 结合项目</strong>：每个知识点都准备一个「我在项目里怎么用的」例子。"
              "10 年经验的候选人必须体现工程落地能力，纯背八股会露馅。<br>"
              "<strong>④ 坦诚边界</strong>：不会的就说「这块我了解有限，我的理解是……，可能不准确」，"
              "比胡编更好。<br>"
              "<strong>⑤ 反问环节</strong>：问「团队的技术栈和并发场景」"
              "「有没有遇到过性能瓶颈，怎么解决的」，展示技术热情。"))
    w.h3("按考察频率排序的重点")
    w.raw(table(["优先级", "知识点", "出现频率", "对应章节"], [
        ["P0", "线程池参数与执行流程", "几乎必问", "第 7 章"],
        ["P0", "AQS 原理", "几乎必问", "第 4 章"],
        ["P0", "synchronized 锁升级", "几乎必问", "第 3 章"],
        ["P0", "volatile 原理与 DCL", "几乎必问", "第 2 章"],
        ["P1", "ConcurrentHashMap 演进", "高频", "第 9 章"],
        ["P1", "ThreadLocal 内存泄漏", "高频", "第 10 章"],
        ["P1", "CAS 与 ABA", "高频", "第 6 章"],
        ["P2", "死锁排查", "中频", "第 12 章"],
        ["P2", "CompletableFuture", "中频", "第 8 / 10 章"],
        ["P2", "ForkJoinPool", "中频", "第 14 章"],
        ["P3", "虚拟线程", "新兴热点", "第 15 章"],
        ["P3", "无锁编程 / Disruptor", "加分项", "第 13 章"],
    ]))
    w.end()


def ch18(w):
    w.chapter("ch18", "18", "性能测试与调优方法论",
              "为什么手写 benchmark 不靠谱")
    w.h3("18.1 JMH 基准测试")
    w.raw(code_block("ch18/_01_JmhBenchmark.java"))
    w.raw(pre("""为什么不能手写 System.nanoTime() 测性能？

  1 JIT 预热不充分
     前几万次执行走解释器，比 JIT 编译后的机器码慢几十倍。
     不预热就测，你测的是「解释执行速度」而不是「生产速度」。

  2 死代码消除（Dead Code Elimination）
     如果计算结果没被使用，JIT 会把整个循环直接删掉。
     你测出的「零耗时」其实是代码被优化没了。

  3 常量折叠（Constant Folding）
     编译期能算出来的结果，直接替换成常量。

  4 循环展开优化
     空循环体会被 JIT 直接优化掉。

  5 CPU 频率调节 / 线程调度干扰
     单次测量噪声太大，必须多次迭代取统计值。

JMH 通过预热轮次、Blackhole、@Fork 独立进程等机制，系统性地解决了这些问题。

运行基准测试：
  cd java-concurrency-course/code
  mvn clean package
  java -jar target/jmh-benchmarks.jar
  # 或只跑某个类
  java -jar target/jmh-benchmarks.jar ".*JmhBenchmark.*" """))
    w.raw(table(["注解", "作用"], [
        ["<code>@Benchmark</code>", "标记被测方法"],
        ["<code>@BenchmarkMode</code>", "测试模式：Throughput / AverageTime / SampleTime"],
        ["<code>@Warmup</code>", "预热轮次（不计入结果）"],
        ["<code>@Measurement</code>", "正式测量轮次"],
        ["<code>@Fork</code>", "每个测试开几个独立 JVM 进程（隔离干扰）"],
        ["<code>@State</code> / <code>@Scope</code>", "定义状态对象及其作用域"],
        ["<code>@Threads</code>", "并发线程数"],
        ["<code>@Setup</code> / <code>@TearDown</code>", "生命周期钩子"],
        ["<code>@Param</code>", "参数化测试"],
    ]))
    w.h3("18.2 并发性能优化的完整方法论")
    w.raw(pre("""第 0 步：先测量，不要猜！
        没有 profiling 的优化都是玄学。
        工具：JMH（微基准）/ async-profiler（火焰图）/
             arthas（线上诊断）/ JFR（JVM 内置，零开销）

第 1 步：找瓶颈 —— 是 CPU 打满？锁竞争？还是 GC？
        +- CPU 打满 -> top -H -p <pid> 找热点线程 -> jstack 看栈
        +- 锁竞争   -> arthas 的 monitor 命令看锁等待
        +- GC 频繁  -> jstat -gcutil <pid> 1s

第 2 步：按「性价比」排序优化手段（这个顺序很重要）：
        +----------------------------------------------------+
        | 1 消除共享（最重要）                                |
        |   -> 线程本地化、无状态设计、不可变对象             |
        |   -> 这是唯一能「根治」并发问题的方法               |
        +----------------------------------------------------+
        | 2 减少临界区                                        |
        |   -> 缩小锁范围（只锁必要的代码）                   |
        |   -> 读写分离（读多写少用读写锁/COW）               |
        +----------------------------------------------------+
        | 3 降低锁竞争                                        |
        |   -> 分段锁、CAS、无锁数据结构                      |
        |   -> 缓存行填充（消除伪共享）                       |
        +----------------------------------------------------+
        | 4 减少内存分配                                      |
        |   -> 对象池、预分配、避免装箱、避免日志字符串拼接   |
        +----------------------------------------------------+
        | 5 最后才考虑：换锁类型 / 调 JVM 参数                |
        +----------------------------------------------------+

第 3 步：每次只改一个变量，改完重新测量，确认真的有提升。

第 4 步：警惕「过早优化」——
        先保证正确，再保证可读，最后才优化。
        Knuth 的原话是「过早优化是万恶之源」，
        但完整的表述是「在 97% 的情况下不成熟的优化是万恶之源，
        但我们不应放过那关键的 3%」——前提是你先通过测量找到了那 3%。

一个真实的优化案例（面试可以讲）：
  现象：订单查询接口 P99 从 50ms 涨到 800ms
  定位：
    1 arthas 的 trace 命令发现 80% 时间在获取本地缓存锁
    2 代码用 synchronized 修饰了整个方法，包括了序列化操作
  优化：
    1 缩小锁范围，只锁真正的共享结构修改
    2 改用 ConcurrentHashMap（原先用 HashMap + synchronized）
    3 热点数据加 Caffeine 本地缓存
  结果：P99 降到 60ms，QPS 从 2000 提升到 8000
  -----------------------------
  答题要点：一定要有「量化数据」和「方法论沉淀」，
           体现你不仅能解决问题，还能防止问题再发生。"""))
    w.raw(tip("success", "学习完成后的自检清单",
              "如果你能<strong>不看任何资料</strong>回答出以下问题，"
              "说明你已经达到 10 年 Java 开发的并发水平：<br><br>"
              "1. DCL 单例为什么必须加 volatile？不加会怎样？<br>"
              "2. 锁升级的四个阶段是什么？偏向锁为什么在 JDK15 被禁用？<br>"
              "3. AQS 的 state 在 ReentrantLock / Semaphore / CountDownLatch 里分别是什么语义？<br>"
              "4. 线程池为什么先入队而不是先扩容？无界队列会导致什么问题？<br>"
              "5. ConcurrentHashMap 在 JDK8 为什么放弃分段锁？<br>"
              "6. ThreadLocal 为什么会导致内存泄漏？为什么 key 要用弱引用？<br>"
              "7. 任务用 submit() 提交抛异常了会怎样？怎么排查？<br>"
              "8. 死锁怎么在线排查？四个必要条件是什么？<br>"
              "9. 什么是伪共享？怎么解决？JDK 里哪些类用了这个技巧？<br>"
              "10. 虚拟线程的 pinning 问题是什么？你会在什么场景用它？<br><br>"
              "如果都能答上来，<strong>恭喜你，这份教程的目标达成了。</strong>"))
    w.end()


def appendix(w):
    w.chapter("appendix", "附录", "工程结构与参考资料",
              "如何运行全部代码，以及延伸学习方向")
    w.h3("A.1 工程结构")
    w.raw(pre("""java-concurrency-course/
+- index.html                          <- 你正在看的这份教程
+- code/
    +- pom.xml                         <- Maven 配置（JOL / JCTools / JMH 依赖）
    +- src/main/java/course/
        +- ch01/  线程基础与竞态条件
        |   +- _01_HelloThread.java
        |   +- _02_ThreadState.java
        |   +- _03_RaceCondition.java
        +- ch02/  JMM 与 volatile
        |   +- _01_VisibilityProblem.java
        |   +- _02_VolatileWrongVsRight.java
        |   +- _03_DclSingleton.java
        +- ch03/  synchronized 与锁升级
        |   +- _01_SynchronizedBasics.java
        |   +- _02_MarkWordLayout.java          （需 JOL）
        |   +- _03_LockEscalation.java          （需 JOL）
        |   +- _04_WaitNotify.java
        +- ch04/  AQS 手写同步器
        +- ch05/  ReentrantLock 与 Condition
        +- ch06/  读写锁与 CAS 原子类
        +- ch07/  线程池核心原理
        +- ch08/  线程池生产实践与 CompletableFuture
        +- ch09/  并发容器与阻塞队列
        +- ch10/  ThreadLocal 与异步编排实战
        +- ch11/  happens-before 与伪共享
        +- ch12/  死锁与十大陷阱
        +- ch13/  无锁 RingBuffer
        +- ch14/  ForkJoinPool
        +- ch15/  虚拟线程（需 JDK 21+）
        +- ch16/  综合实战三场景
        +- ch17/  面试真题精讲
        +- ch18/  JMH 基准测试"""))
    w.h3("A.2 三种运行方式")
    w.raw(pre("""【方式一】单文件直接运行（JDK 11+，最方便，适合学习）
cd java-concurrency-course/code
java src/main/java/course/ch01/_03_RaceCondition.java
java src/main/java/course/ch07/_01_ThreadPoolParams.java

【方式二】Maven 编译运行（适合需要第三方依赖的章节）
cd java-concurrency-course/code
mvn compile
mvn exec:java -Dexec.mainClass=course.ch07._01_ThreadPoolParams

【方式三】需要 JOL 依赖的章节（ch03_02 / ch03_03）
mvn dependency:copy-dependencies -DoutputDirectory=target/lib
java -cp "target/classes;target/lib/*" course.ch03._02_MarkWordLayout
# Linux/macOS 用冒号分隔:  "target/classes:target/lib/*"

【运行 JMH 基准测试】
mvn clean package
java -jar target/jmh-benchmarks.jar
# 只跑某个类
java -jar target/jmh-benchmarks.jar ".*JmhBenchmark.*"

【看到偏向锁需要额外 JVM 参数】
java -XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0 \\
     -cp "target/classes;target/lib/*" course.ch03._03_LockEscalation"""))
    w.raw(table(["依赖", "版本", "用途", "使用章节"], [
        ["org.openjdk.jol:jol-core", "0.17", "对象内存布局 / Mark Word 分析", "ch03_02, ch03_03"],
        ["org.jctools:jctools-core", "4.0.5", "高性能无锁队列参考实现", "ch13"],
        ["org.openjdk.jmh:jmh-core", "1.37", "基准测试框架", "ch18"],
        ["org.openjdk.jmh:jmh-generator-annprocess", "1.37", "JMH 注解处理器", "ch18"],
    ]))
    w.h3("A.3 延伸学习方向")
    w.raw(pre("""【源码阅读路线】（按这个顺序读，从易到难）
  1 java.util.concurrent.atomic.AtomicInteger
     -> 看 CAS 自旋的写法（约 50 行核心代码）
  2 java.util.concurrent.locks.AbstractQueuedSynchronizer
     -> 重点看 acquire / release / addWaiter / acquireQueued
  3 java.util.concurrent.locks.ReentrantLock
     -> 看 NonfairSync 和 FairSync 的差异
  4 java.util.concurrent.ThreadPoolExecutor
     -> 重点看 execute / addWorker / runWorker / getTask
  5 java.util.concurrent.ConcurrentHashMap
     -> 重点看 putVal / initTable / transfer / helpTransfer
  6 java.util.concurrent.CompletableFuture
     -> 看 Completion 链式结构（较难，可选）

【推荐书籍】
  * 《Java 并发编程实战》(Java Concurrency in Practice)
    -> 并发领域最经典的书，「JCiP」，必读
  * 《Java 并发编程的艺术》
    -> 中文原创，适合入门，对 JMM 和 AQS 讲解清晰
  * 《深入理解 Java 虚拟机》周志明
    -> 第 13 章专门讲线程安全与锁优化，JVM 视角的必读

【推荐工具】
  * arthas         https://arthas.aliyun.com/
    -> 阿里开源的 Java 诊断工具，生产环境必备
       重点掌握：thread / trace / watch / monitor / dashboard
  * async-profiler https://github.com/async-profiler/async-profiler
    -> 低开销的采样 profiler，生成火焰图
  * JMH            https://github.com/openjdk/jmh
    -> 官方基准测试框架
  * JOL            https://github.com/openjdk/jol
    -> 对象内存布局分析工具

【官方文档】
  * JEP 444（虚拟线程）    https://openjdk.org/jeps/444
  * JEP 491（虚拟线程与 synchronized）https://openjdk.org/jeps/491
  * JEP 453（结构化并发预览）https://openjdk.org/jeps/453
  * JEP 374（弃用偏向锁）  https://openjdk.org/jeps/374
  * Java SE 并发教程       https://docs.oracle.com/javase/tutorial/essential/concurrency/

【面试准备建议】
  如果时间有限，优先顺序：
    第 7 章（线程池）-> 第 4 章（AQS）-> 第 3 章（锁升级）
    -> 第 2 章（JMM）-> 第 9 章（并发容器）-> 第 17 章（面试题）
  这六章覆盖 80% 的面试问题。"""))
    w.raw(tip("success", "最后一点建议",
              "并发编程最大的特点是：<strong>看起来懂和真正懂，差距极大</strong>。"
              "很多人能背出 AQS 的原理，但写不出一个正确的同步器；"
              "能说出 volatile 保证可见性，但说不清为什么 DCL 需要它。<br><br>"
              "检验自己是否真懂的唯一标准是：<strong>能不能从零写出来</strong>。"
              "所以请不要只读这份教程——<strong>把第 4 章的同步器、第 7 章的线程池、"
              "第 13 章的 RingBuffer 试着自己写一遍</strong>，遇到卡住的地方再回来查。"
              "这个过程比读十遍教程都有用。<br><br>"
              "祝你在面试中顺利，也祝你写出真正可靠的并发代码。"))
    w.end()
