#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""07-12 章内容"""

from chapters_a import code_block, out_block, tip, table, pre


def ch07(w):
    w.chapter("ch07", "07", "线程池核心原理",
              "大厂面试占比最高的一个主题")
    w.h3("7.1 七大参数与执行流程实测")
    w.p("线程池的执行流程，光看文字很容易记混。下面这段代码<strong>通过打印池状态</strong>，"
        "让你亲眼看到「先入队、再扩容」这个过程。")
    w.raw(code_block("ch07/_01_ThreadPoolParams.java"))
    w.raw(out_block("""CPU 核心数 = 8

===== 实验1：任务数在 corePoolSize 以内 → 只创建核心线程 =====
  [2 个任务] 池大小=2  核心=2  活跃=2  队列=0  已完成=0

===== 实验2：超过核心数 → 先入队列，队列满了才扩容 =====
  [核心2 + 队列2 后] 池大小=2  核心=2  活跃=2  队列=2  已完成=0
  [队列满后再提交1个] 池大小=3  核心=2  活跃=3  队列=2  已完成=0
  [再提交1个] 池大小=4  核心=2  活跃=4  队列=2  已完成=0"""))
    w.p("<strong>重点看实验 2 的三行输出</strong>：队列满了之后，"
        "池大小从 2 变成 3、再变成 4（扩容到 maxPoolSize），而队列长度始终是 2。"
        "这就直观证明了执行顺序：<strong>核心线程 → 队列 → 扩容 → 拒绝</strong>。")
    w.raw(pre("""线程池执行流程（务必背下这个顺序）：

                    提交任务
                       |
                       v
        +------------------------------+
        | 1 当前线程数 < corePoolSize?  |--是--> new 一个核心线程执行
        +------------------------------+        （即使其他线程空闲也创建）
                       | 否
                       v
        +------------------------------+
        | 2 workQueue.offer() 成功?     |--是--> 放入队列排队
        +------------------------------+
                       | 否（队列满）
                       v
        +------------------------------+
        | 3 线程数 < maximumPoolSize?   |--是--> new 一个非核心线程
        +------------------------------+
                       | 否
                       v
        +------------------------------+
        | 4 执行拒绝策略                |
        +------------------------------+"""))
    w.raw(tip("interview", "面试官最爱追问：为什么先入队而不是先扩容？",
              "标准答案是：<strong>「线程是稀缺资源，创建和销毁成本高。设计哲学是：能靠队列缓冲就先缓冲，"
              "只有当缓冲区也满了，才说明确实需要更多线程。」</strong><br><br>"
              "但更强的回答是继续补充这个设计带来的陷阱：<strong>「不过这个设计有个著名的坑——"
              "如果使用无界队列（如 <code>LinkedBlockingQueue</code> 默认容量 "
              "<code>Integer.MAX_VALUE</code>），那么第三步『扩容』永远不会触发，"
              "<code>maximumPoolSize</code> 完全失效，任务会无限堆积最终 OOM。"
              "这是线上事故最常见的来源之一，也是阿里开发手册强制要求禁用 "
              "<code>Executors</code> 快捷方法的原因。」</strong>"))
    w.raw(table(["参数", "含义", "实践建议"], [
        ["corePoolSize", "核心线程数（常驻）", "CPU 密集 = N+1，IO 密集 = 2N+"],
        ["maximumPoolSize", "最大线程数", "<strong>必须显式设置</strong>，配合有界队列"],
        ["keepAliveTime", "非核心线程空闲存活时间", "默认 60s；<code>allowCoreThreadTimeOut(true)</code> 可让核心线程也回收"],
        ["unit", "时间单位", "—"],
        ["workQueue", "任务队列", "<strong>必须有界！</strong>推荐 ArrayBlockingQueue"],
        ["threadFactory", "线程工厂", "<strong>必须自定义</strong>：起名、设守护、异常处理器"],
        ["handler", "拒绝策略", "按业务选，见下表"],
    ]))
    w.raw(table(["拒绝策略", "行为", "适用场景"], [
        ["AbortPolicy（默认）", "抛 RejectedExecutionException", "需要感知失败并做补偿的业务"],
        ["CallerRunsPolicy", "由提交任务的线程自己执行", "<strong>推荐</strong>：天然的反压机制，逼上游降速"],
        ["DiscardPolicy", "静默丢弃", "危险，除非数据可容忍丢失（如埋点日志）"],
        ["DiscardOldestPolicy", "丢弃队列中最老的任务再重试", "只关心最新数据的场景"],
    ]))
    w.h3("7.2 手写线程池 + 源码逐行拆解")
    w.p("想真正理解线程池，最好的办法是自己实现一个。下面是一个极简但完整可用的线程池。")
    w.raw(code_block("ch07/_02_MyThreadPool.java"))
    w.raw(out_block("""===== 测试：3 线程 + 队列容量 3 + CallerRuns 策略 =====
  [拒绝] 队列满，main 自己执行
  执行任务 4 by main
  执行任务 2 by simple-pool-1
  执行任务 3 by simple-pool-2
  执行任务 1 by simple-pool-0
  执行任务 5 by simple-pool-0
  执行任务 6 by simple-pool-1
  [拒绝] 队列满，main 自己执行
  执行任务 8 by main"""))
    w.p("注意 <code>main</code> 线程自己执行了任务 4、8、12——这就是 "
        "<code>CallerRunsPolicy</code> 的效果。它把压力传导回调用方，是一种优雅的降级。")
    w.p("现在再看 JDK 的真实源码，你会发现结构完全一致：")
    w.raw(pre("""public void execute(Runnable command) {
    if (command == null) throw new NullPointerException();
    int c = ctl.get();                              // ctl：高3位=状态, 低29位=线程数
    if (workerCountOf(c) < corePoolSize) {          // 1 核心线程没满
        if (addWorker(command, true)) return;
        c = ctl.get();
    }
    if (isRunning(c) && workQueue.offer(command)) { // 2 尝试入队
        int recheck = ctl.get();
        if (! isRunning(recheck) && remove(command))     // 双重检查：池已停？
            reject(command);
        else if (workerCountOf(recheck) == 0)
            addWorker(null, false);                      // 兜底：至少留一个线程
    }
    else if (!addWorker(command, false))            // 3 队列满 -> 扩容到 max
        reject(command);                             // 4 扩容失败 -> 拒绝
}"""))
    w.raw(tip("info", "三个精妙的源码设计（面试高光时刻）",
              "<strong>① 为什么用 ctl 一个字段同时表达「状态」和「线程数」？</strong><br>"
              "因为要保证「判断状态」和「读取线程数」的原子性。如果分成两个字段，"
              "在两次读取之间状态可能已经变了，需要额外加锁。合并成一个 AtomicInteger 后，"
              "一次 CAS 就能同时更新两者。高 3 位是 RUNNING/SHUTDOWN/STOP/TIDYING/TERMINATED，"
              "低 29 位是线程数（上限约 5 亿）。<br><br>"
              "<strong>② 为什么新建的 worker 先执行 firstTask 而不是先去 take()？</strong><br>"
              "这是「快速路径」优化。如果新线程创建后先去队列取任务，而此时队列是空的，"
              "就会白白多一次上下文切换的往返。<br><br>"
              "<strong>③ 为什么任务抛异常后工作线程不会死？</strong><br>"
              "<code>runWorker()</code> 内部有 try/finally 包裹，异常通过 "
              "<code>afterExecute</code> 钩子传递，然后 while 循环继续 <code>getTask()</code> 取下一个任务。"))
    w.end()


def ch08(w):
    w.chapter("ch08", "08", "线程池生产实践",
              "从「能用」到「敢上线」的距离")
    w.h3("8.1 优雅关闭、异常处理、监控")
    w.raw(code_block("ch08/_01_PoolBestPractice.java"))
    w.raw(out_block("""===== 1) 三种关闭方式对比 =====
  提交 5 个任务后，队列长度 = 4
  调用 shutdown() 后 isShutdown=true，队列还在跑，剩余 = 4
  等待完成 = true，isTerminated=true
  shutdownNow() 返回未执行任务数 = 4（立即中断线程 + 清空队列）

===== 4) 线程池监控指标 =====
  容量 = 8 线程 + 10 队列 = 18，实际提交 25 个 → 被拒绝 7 个
  [运行中] 池大小=8 活跃=8 队列=10(剩余容量=0) 最大曾达=8 完成=0 总任务=18
  [执行后] 池大小=8 活跃=0 队列=0(剩余容量=10) 最大曾达=8 完成=18 总任务=18"""))
    w.raw(tip("danger", "任务异常的隐蔽陷阱（血泪教训）",
              "<strong>用 <code>submit()</code> 提交的任务，如果抛了异常，会被 Future 静默吞掉！</strong>"
              "既不会打印日志，也不会触发 <code>UncaughtExceptionHandler</code>。"
              "如果你忘记调用 <code>future.get()</code>，这个异常就永远消失了——"
              "任务静默失败，你以为成功了。<br><br>"
              "这就是为什么很多老手坚持用 <code>execute()</code> 而不是 <code>submit()</code>，"
              "或者在重写的 <code>afterExecute()</code> 里统一做兜底处理。"))
    w.raw(pre("""优雅关闭的标准模板（生产可用）：

  pool.shutdown();                          // 1 拒绝新任务，队列中的继续执行
  try {
      if (!pool.awaitTermination(10, SECONDS)) {
          pool.shutdownNow();               // 2 超时则强制中断
          if (!pool.awaitTermination(5, SECONDS)) {
              log.error("线程池未能正常关闭");
          }
      }
  } catch (InterruptedException e) {
      pool.shutdownNow();                   // 3 自己也中断时补充一次
      Thread.currentThread().interrupt();   // 4 恢复中断标志
  }

shutdown() 与 shutdownNow() 的区别：
  +---------------+----------------------+--------------------------+
  |               | shutdown()           | shutdownNow()            |
  +---------------+----------------------+--------------------------+
  | 新任务        | 拒绝                 | 拒绝                     |
  | 队列中的任务  | 继续执行完           | 全部丢弃并返回列表       |
  | 正在执行的    | 等它跑完             | 发 interrupt 中断        |
  | 状态          | SHUTDOWN             | STOP                     |
  +---------------+----------------------+--------------------------+"""))
    w.h3("8.2 线程池参数到底设多少？")
    w.raw(pre("""理论公式（面试可以背）：

  CPU 密集型（计算为主，几乎不阻塞）
     线程数 = N + 1        （N = CPU 核心数）
     为什么 +1？为了在偶发的缺页中断或其他暂停时，仍能有一线程顶上。

  IO 密集型（大量等待网络/磁盘）
     线程数 = N x (1 + 等待时间 / 计算时间)
     例：CT=50ms 计算, WT=450ms 等待, N=8
         -> 8 x (1 + 9) = 80 个线程

但生产实践更推荐「压测 + 动态调整」：
  +---------------------------------------------------------+
  | 1 按公式给一个初值                                       |
  | 2 用 JMeter/wrk 做压力测试，观察 QPS 和响应时间曲线      |
  | 3 找到 QPS 不再上升、但响应时间开始飙升的拐点            |
  | 4 取拐点值的 80% 作为配置                                |
  | 5 接入配置中心（Nacos/Apollo），支持运行时热更新         |
  |   setCorePoolSize() / setMaximumPoolSize() 都支持动态改  |
  +---------------------------------------------------------+
  为什么不能只靠公式？
  因为公式里的「等待时间/计算时间」在真实业务中很难准确测量，
  而且流量模式会变化（大促 vs 平时）。"""))
    w.h3("8.3 CompletableFuture 完全指南")
    w.raw(code_block("ch08/_02_CompletableFutureBasic.java"))
    w.raw(out_block("""===== 2) 串行链式：thenApply / thenAccept / thenRun =====
    thenApply 在 main
    最终结果=25

===== 3) 带 Async 后缀：切换到别的线程 =====
    thenApplyAsync 在 cf-pool
    thenAcceptAsync 拿到 AB

===== 5) 组合：thenCompose（扁平化）vs thenCombine（合并）=====
    thenCompose → 根据[用户ID=100]查到订单
    thenCombine → A+B，耗时 301ms（并行，非 600ms）

===== 6) 多任务编排：allOf / anyOf =====
    allOf 全部完成 → [服务1结果, 服务2结果, 服务3结果]，耗时 206ms
    anyOf 第一个完成 → 快"""))
    w.p("注意<strong>第 2 节的输出「thenApply 在 main」</strong>——这是一个非常重要且容易踩坑的细节。"
        "<code>thenApply</code> 没有 Async 后缀时，如果上一个任务已经完成，"
        "<strong>它会用调用者线程同步执行</strong>。这可能导致意料之外的阻塞或在错误的线程上执行。")
    w.raw(table(["方法", "入参 → 出参", "用途"], [
        ["thenApply", "T → U", "转换结果（串行）"],
        ["thenAccept", "T → void", "消费结果"],
        ["thenRun", "void → void", "不关心结果，跑下一步"],
        ["thenCompose", "T → CF&lt;U&gt;", "依赖前一结果返回新 CF（扁平化）"],
        ["thenCombine", "(T,U) → V", "两个独立 CF 结果合并"],
        ["allOf", "CF[] → CF&lt;Void&gt;", "等全部完成"],
        ["anyOf", "CF[] → CF&lt;Object&gt;", "任一完成"],
        ["handle", "(T,E) → U", "成功/失败都处理，可改变结果"],
        ["exceptionally", "E → T", "只处理异常，兜底返回"],
        ["whenComplete", "(T,E) → void", "只观察不改变结果"],
    ]))
    w.raw(tip("danger", "CompletableFuture 的三个致命坑",
              "<strong>① 默认线程池是 ForkJoinPool.commonPool()</strong>，线程数只有 CPU-1，"
              "而且被 <code>parallelStream</code> 等共用。一个慢任务就能拖垮全站。"
              "<strong>所有异步方法必须显式传入自己的线程池。</strong><br><br>"
              "<strong>② 在 thenApply 里做阻塞操作（如 RPC 调用）</strong>，会占住线程池的线程。"
              "应该用 <code>thenApplyAsync</code> 并传入独立的线程池。<br><br>"
              "<strong>③ 忘记加超时</strong>。没有超时的异步调用就是一颗定时炸弹，"
              "下游卡住会一直占着线程。<strong>每个下游调用都要加 <code>orTimeout()</code>。</strong>"))
    w.end()


def ch09(w):
    w.chapter("ch09", "09", "并发容器",
              "从 HashMap 死循环到 ConcurrentHashMap 的演进")
    w.h3("9.1 容器全景与性能实测")
    w.raw(code_block("ch09/_01_ConcurrentCollections.java"))
    w.raw(out_block("""===== 3) Map 并发写性能实测（让线程写同一批 key 以制造真实冲突）=====
  synchronizedMap      :   103 ms
  ConcurrentHashMap    :    42 ms  (快 2.5 倍)
  → synchronizedMap 是「一把大锁锁全表」，CHM 是「CAS + 桶级锁」

===== 4) CopyOnWriteArrayList 读性能优势 =====
  synchronizedList 读:   257 ms
  CopyOnWriteArrayList 读:    21 ms  (快 12.2 倍)
  → COW 读完全无锁（读的是快照），但写会复制整个数组，只适合读多写极少"""))
    w.p("<strong>CopyOnWriteArrayList 的读性能快了 12 倍</strong>——因为它的读操作完全无锁，"
        "读的就是一个快照数组。但注意它写操作的代价：每次写都要<strong>复制整个数组</strong>，"
        "所以只适合「读极多、写极少」的场景（如配置项、监听器列表）。")
    w.raw(table(["接口", "并发实现", "特点 / 替换关系"], [
        ["Map", "ConcurrentHashMap", "替代 Hashtable / synchronizedMap"],
        ["List", "CopyOnWriteArrayList", "读多写极少（配置、监听器）"],
        ["Set", "ConcurrentHashMap.newKeySet()", "底层就是 CHM"],
        ["Queue", "ConcurrentLinkedQueue", "无界、CAS 无锁、高吞吐"],
        ["BlockingQueue", "ArrayBlockingQueue", "有界数组、单锁双条件"],
        ["BlockingQueue", "LinkedBlockingQueue", "双锁分离，吞吐更高"],
        ["BlockingQueue", "SynchronousQueue", "容量 0，直接移交"],
        ["BlockingQueue", "DelayQueue", "延时队列（订单超时取消）"],
        ["Deque", "ConcurrentLinkedDeque", "无锁双端队列"],
    ]))
    w.h3("9.2 ConcurrentHashMap 的两次重构")
    w.raw(pre("""JDK 7：Segment 分段锁

  +---------------------------------------------------------+
  |  ConcurrentHashMap                                       |
  |  +----------+ +----------+ +----------+ +----------+   |
  |  |Segment[0]| |Segment[1]| |Segment[2]| | ... [15] |   |
  |  | (继承    | |  继承    | |  继承    | |          |   |
  |  |Reentrant | |Reentrant | |Reentrant | |          |   |
  |  |  Lock)   | |  Lock)   | |  Lock)   | |          |   |
  |  | HashEntry| | HashEntry| | HashEntry| | HashEntry|   |
  |  |  数组    | |  数组    | |  数组    | |  数组    |   |
  |  +----------+ +----------+ +----------+ +----------+   |
  +---------------------------------------------------------+
  并发度 = 段数（默认 16），初始化后不可变 -> 并发度上限固定
  段间负载可能不均 -> 有的段竞争激烈，有的段很闲

JDK 8：CAS + synchronized 锁桶

  +---------------------------------------------------------+
  |  Node[] table                                            |
  |  +----++----++--------------++----++----+              |
  |  |null||Node||  链表/红黑树  ||Node||null|              |
  |  +----++-+--++------+-------++-+--++----+              |
  |          |          |           |                       |
  |      只锁这个桶头节点（synchronized）                     |
  |      空桶插入用 CAS，无需加锁                             |
  +---------------------------------------------------------+
  * 锁粒度从「段」细化到「桶」，并发度不设上限
  * 链表长度 >= 8 且数组长度 >= 64 时转为红黑树（O(n)->O(log n)）
  * size() 用 baseCount + CounterCell[] 分段计数
  * 扩容支持多线程协同迁移（helpTransfer）"""))
    w.raw(tip("interview", "为什么 JDK8 放弃分段锁？",
              "两个核心原因：<br><br>"
              "<strong>① 分段锁的并发度上限被固定死了</strong>。默认 16 段意味着最多 16 个线程并发写，"
              "而且段数在初始化后无法修改。桶级锁没有这个限制。<br>"
              "<strong>② JDK8 的 synchronized 已经做了大量优化</strong>（锁消除、锁粗化、轻量级锁自旋），"
              "在低竞争场景下性能已接近 CAS。所以用 synchronized 锁桶头节点，"
              "既简化了实现（不用维护 Segment 继承体系），又获得了更细的锁粒度。<br><br>"
              "追问：为什么 key 和 value 都不能为 null？<br>"
              "因为并发场景下需要「一次查找」就区分「key 不存在」和「key 存在但值为 null」。"
              "HashMap 可以用 <code>containsKey</code> 做二次确认，但 CHM 为了性能不做二次查找，"
              "所以干脆禁止 null。"))
    w.h3("9.3 六种阻塞队列全实战")
    w.raw(code_block("ch09/_02_BlockingQueues.java"))
    w.raw(out_block("""===== 3) SynchronousQueue：容量 0，一手交钱一手交货 =====
  put 前时间: 17627
  put 返回时间: 18129（阻塞了约 500ms）

===== 4) DelayQueue：延时队列（订单超时未支付自动取消）=====
  [ 101ms] 处理超时订单 ORDER-003
  [ 301ms] 处理超时订单 ORDER-001
  [ 802ms] 处理超时订单 ORDER-002
  → DelayQueue 按「剩余延时」排序，未到期取不到

===== 5) PriorityBlockingQueue：带优先级的无界队列 =====
  出队: 高优先级任务
  出队: 中优先级任务
  出队: 低优先级任务"""))
    w.raw(table(["操作", "抛异常", "返回特殊值", "阻塞", "超时退出"], [
        ["插入", "<code>add(e)</code>", "<code>offer(e)</code>", "<code>put(e)</code>", "<code>offer(e, time, unit)</code>"],
        ["移除", "<code>remove()</code>", "<code>poll()</code>", "<code>take()</code>", "<code>poll(time, unit)</code>"],
        ["检查", "<code>element()</code>", "<code>peek()</code>", "—", "—"],
    ]))
    w.raw(tip("success", "记忆法",
              "<code>add/remove</code> 会<strong>抛异常</strong>（因为是 Collection 接口定义的方法）；"
              "<code>offer/poll</code> 返回 <strong>false/null</strong>（不抛异常）；"
              "<code>put/take</code> 会<strong>阻塞</strong>（BlockingQueue 特有）。"
              "记住这三组，面试问起来不会混。"))
    w.end()


def ch10(w):
    w.chapter("ch10", "10", "ThreadLocal 与异步编排实战",
              "上下文传递的陷阱，以及商品详情页的并行聚合")
    w.h3("10.1 ThreadLocal 的真实结构")
    w.p("很多人以为 ThreadLocal 是一个「key 是线程、value 是值」的 Map。"
        "<strong>实际上完全相反</strong>——数据存在线程身上，ThreadLocal 只是访问它的钥匙。")
    w.raw(code_block("ch10/_01_ThreadLocalDeep.java"))
    w.raw(out_block("""===== 1) 基础：每个线程有自己独立的值 =====
  线程2 读到: UserContext{name=李四, tenant=TENANT-B}
  线程1 读到: UserContext{name=张三, tenant=TENANT-A}
  main 线程读到: null（未设置，返回 null）

===== 3) 不 remove 的后果演示（线程池场景）=====
  任务1 在 pool-1-thread-1 上放了 10MB，未清理
  任务2 在 pool-1-thread-1 上读到残留 = 有！脏数据！"""))
    w.raw(pre("""ThreadLocal 的真实数据结构（反直觉！）：

  +------------------------------------------------------------+
  |  Thread 对象                                                |
  |  +------------------------------------------------------+  |
  |  |  ThreadLocal.ThreadLocalMap threadLocals;  <- 存这里！|  |
  |  |  +------------------------------------------------+  |  |
  |  |  |  Entry[] table                                 |  |  |
  |  |  |  +------------------------------------------+  |  |  |
  |  |  |  | key (弱引用) --weak--> ThreadLocal 对象   |  |  |  |
  |  |  |  | value (强引用) ------> 你的数据          |  |  |  |
  |  |  |  +------------------------------------------+  |  |  |
  |  |  +------------------------------------------------+  |  |
  |  +------------------------------------------------------+  |
  +------------------------------------------------------------+

内存泄漏的完整链条：
  1 ThreadLocal 外部引用置 null  ->  key 被 GC 回收，变成 null
  2 但 value 是强引用，且被 Thread 引用着  ->  value 无法回收
  3 线程池里线程长期存活  ->  value 一直堆积  ->  内存泄漏

为什么 key 用弱引用？（面试重点）
  如果用强引用：ThreadLocal 对象即使外部不用了，也会因为被 Map 引用
  而永远无法回收 -> 泄漏更严重（连 ThreadLocal 本身都漏了）。
  用弱引用：至少 ThreadLocal 对象能回收，泄漏范围缩小到 value。
  这是「两害相权取其轻」的设计。"""))
    w.raw(tip("danger", "ThreadLocal 的正确使用姿势",
              "<pre style='margin:0'>private static final ThreadLocal&lt;Ctx&gt; CTX = new ThreadLocal&lt;&gt;();\n\n"
              "public void doBusiness() {\n"
              "    CTX.set(new Ctx(...));\n"
              "    try {\n"
              "        // ... 业务逻辑\n"
              "    } finally {\n"
              "        CTX.remove();      // &lt;- 必须！finally 保证异常也会清理\n"
              "    }\n"
              "}</pre>"
              "三个要点：<strong>① 声明为 <code>private static final</code></strong>"
              "（保证外部一直有强引用，避免 key 被提前回收）；"
              "<strong>② 用完必须 <code>remove()</code></strong>；"
              "<strong>③ 必须放在 <code>finally</code> 里</strong>。"))
    w.h3("10.2 跨线程传递上下文的正确方案")
    w.raw(pre("""ThreadLocal 无法传递给子线程：

  主线程 CTX.set("user-123")
         |
         +---> new Thread(...) ---> 子线程 CTX.get() = null  [X]

三种解决方案对比：
  +------------------------+----------------------------------------+
  | InheritableThreadLocal | 子线程创建时复制父线程的值              |
  |                        | [X] 线程池场景失效（线程复用，创建时机错）|
  +------------------------+----------------------------------------+
  | 手动传参                | [OK] 最可靠，但代码侵入性大             |
  +------------------------+----------------------------------------+
  | TransmittableThreadLocal| 阿里开源 TTL                           |
  |                        | [OK] 提交任务时快照传递，线程池唯一正解  |
  |                        | 需配合 TtlExecutors 包装线程池          |
  +------------------------+----------------------------------------+

TTL 使用示例：
  TransmittableThreadLocal<String> ctx = new TransmittableThreadLocal<>();
  ExecutorService pool = TtlExecutors.getTtlExecutorService(rawPool);
  // 之后 pool.submit(...) 时会自动传递当前线程的 TTL 值"""))
    w.h3("10.3 实战：商品详情页并行聚合")
    w.p("这是互联网公司最典型的 CompletableFuture 应用场景。打开一个商品详情页，"
        "需要调用 6 个下游服务，串行调用要 900ms，并行只需要最慢那个的时间。")
    w.raw(code_block("ch10/_02_CfRealWorld.java"))
    w.raw(out_block("""===== 方式一：串行调用 =====
  总耗时 = 907ms

===== 方式二：CompletableFuture 并行编排 =====
  商品 = 商品信息[SKU-10086]
  库存 = 88
  价格 = 19900 分
  优惠 = [满199减20, 满99减10]
  评价 = 好评率98%
  推荐 = 推荐：同款A/同款B
  总耗时 = 265ms（串行 907ms，提速 3.4 倍）

===== 方式三：多数据源竞速（谁快用谁）=====
  取到最快返回的 = 缓存数据

===== 方式四：生产级「超时降级」模板 =====
    [核心服务] 超时/异常，触发降级：TimeoutException
  结果 = 兜底默认值
  结果 = 真实数据"""))
    w.p("<strong>提速 3.4 倍</strong>，而且代码里演示了三个生产必备技巧："
        "① 无依赖的服务全部并行发起；② 有依赖的（优惠券依赖价格）用 <code>thenCompose</code> 串联；"
        "③ 非核心服务（推荐）加超时兜底，超时就降级返回默认值，不阻塞主流程。")
    w.end()


def ch11(w):
    w.chapter("ch11", "11", "内存屏障与伪共享",
              "深入到 CPU 缓存行，理解性能暴跌的真相")
    w.h3("11.1 重排序实测与 happens-before")
    w.raw(code_block("ch11/_01_HappensBefore.java"))
    w.raw(out_block("""===== 1) 重排序实测：不留神就出现的 (0,0) 结果 =====
  （x86 是强内存模型 + JIT 需要预热，这个实验需要跑很多轮才有机会复现）
  出现 (x=0, y=0) 的次数 = 1 / 2000000
→ 按顺序一致性推导，(x,y) 只可能是 (1,0)、(0,1)、(1,1)，绝不该出现 (0,0)。
  如果上面数字 > 0，说明 1/2 或 3/4 之间发生了重排序（StoreLoad 重排）。

===== 5) 缓存行填充实测 =====
  相邻变量（伪共享）:  2302 ms
  缓存行填充隔离    :   536 ms
  → 提速 4.3 倍"""))
    w.p("注意第一个实验：<strong>200 万次循环里只捕获到 1 次重排序</strong>。"
        "这个数字本身就说明了一个重要事实——<strong>并发 bug 极其难以复现</strong>。"
        "在 x86（强内存模型）上这么难出现，但同样的代码部署到 ARM 服务器"
        "（如阿里倚天、华为鲲鹏）上，复现概率会明显提高。"
        "这就是「我本机跑没问题，上线就炸」的技术原因。")
    w.raw(pre("""为什么 (x=0, y=0) 是不可能的（如果没有重排序）？

  线程1                      线程2
  a = 1;         1          b = 1;         3
  x = b;         2          y = a;         4

  按顺序执行的所有可能：
    T1先跑完 -> a=1, x=b(此时b可能0或1), b=1, y=a=1
      -> 可能是 (x=0, y=1) 或 (x=1, y=1)
    T2先跑完 -> b=1, y=a(此时a可能0或1), a=1, x=b=1
      -> 可能是 (x=1, y=0) 或 (x=1, y=1)

  三种结果：(1,0)、(0,1)、(1,1)  <- (0,0) 绝不可能！

  但如果 1/2 之间发生重排序（2 提到 1 前面）：
    T1: x = b(0); a = 1;
    T2: y = a(0); b = 1;
    -> x=0, y=0  <- 出现了！"""))
    w.raw(table(["#", "规则", "说明"], [
        ["1", "程序顺序规则", "同一线程内，前面的操作 hb 后面的操作"],
        ["2", "监视器锁规则", "对同一把锁的 unlock hb 后续的 lock"],
        ["3", "volatile 变量规则", "对 volatile 的写 hb 后续的读"],
        ["4", "线程启动规则", "<code>Thread.start()</code> hb 该线程内的所有操作"],
        ["5", "线程终止规则", "线程内所有操作 hb 其他线程检测到它终止（join 返回）"],
        ["6", "线程中断规则", "<code>interrupt()</code> hb 被中断线程检测到中断"],
        ["7", "对象终结规则", "构造器结束 hb <code>finalize()</code> 开始"],
        ["8", "传递性", "A hb B，B hb C 则 A hb C"],
    ]))
    w.raw(tip("interview", "happens-before 的本质（一句话说透）",
              "<strong>happens-before 不是「代码执行顺序」，而是「内存可见性保证」。</strong><br>"
              "如果 A hb B，那么：① A 的所有内存写入对 B 都可见；② A 不会被重排序到 B 之后。<br>"
              "它是对程序员的一种承诺：只要你满足 hb 关系，就不用关心底层到底有没有重排序、"
              "缓存有没有同步——JVM 保证你能看到正确的结果。<br><br>"
              "而单线程内的重排序自由，是由 <strong>as-if-serial 语义</strong>保证的："
              "不管怎么重排序，单线程的执行结果不能变。但多线程之间<strong>没有</strong>这个保证"
              "——这就是并发 bug 的根源。"))
    w.h3("11.2 伪共享：一个不起眼的性能杀手")
    w.p("上面第 5 个实验的结果很震撼：<strong>只是给变量加了些「填充字段」，性能就提升了 4.3 倍</strong>。"
        "这就是伪共享（False Sharing）的威力。")
    w.raw(pre("""CPU 缓存行是 64 字节。假设两个变量落在同一个缓存行里：

  内存（缓存行 = 64 字节）
  +----------------------------------------------------------+
  |  volatile long value1  |  volatile long value2  | 其他数据 |
  +----------------------------------------------------------+
        ^ 线程1 只改这个           ^ 线程2 只改这个

  逻辑上：两个线程操作完全无关的变量，不需要任何同步
  实际上：CPU 的缓存一致性协议以「缓存行」为最小单位

  线程1 修改 value1
      -> 该缓存行被标记为失效（Invalid）
      -> 线程2 的缓存中这行也失效了
      -> 线程2 必须重新从主内存加载整行
      -> 线程2 改 value2  -> 线程1 的缓存行又失效
      -> 来回拉锯，性能暴跌

解决方案：填充（Padding）让变量独占一个缓存行

  +----------------------------------------------------------+
  | p1,p2,p3,p4,p5,p6,p7 (7x8=56字节填充) | value | 填充     |
  +----------------------------------------------------------+
        ^ 填充字段，让 value 独占缓存行
  或者用 JDK8+ 的注解：@sun.misc.Contended

JDK 中的应用：
  * LongAdder 的 Cell 数组
  * Disruptor 的 Sequence
  * ConcurrentHashMap 的 CounterCell
  -> 这就是为什么这些类要写得那么「啰嗦」，每个字段周围都有一堆填充"""))
    w.end()


def ch12(w):
    w.chapter("ch12", "12", "死锁排查与十大陷阱",
              "每一个坑都对应一次线上事故")
    w.h3("12.1 死锁的复现与自动检测")
    w.raw(code_block("ch12/_01_DeadLock.java"))
    w.raw(out_block("""===== 1) 复现死锁 =====
  线程1 持有 A，等待 B...
  线程2 持有 B，等待 A...
  >>> 死锁形成！两个线程互相等待，永远无法推进

===== 2) 用 ThreadMXBean 自动检测死锁 =====
  检测到 2 个死锁线程：
    ── 线程: thread-1
       状态: BLOCKED
       阻塞在: java.lang.Object@7b02881e
       被谁持有: thread-2
       栈顶: course.ch12._01_DeadLock.lambda$main$0(_01_DeadLock.java:26)
    ── 线程: thread-2
       状态: BLOCKED
       阻塞在: java.lang.Object@117e949d
       被谁持有: thread-1"""))
    w.p("<strong>注意输出里「被谁持有」这一行</strong>——它直接告诉了你死锁环的两端是谁。"
        "这个能力可以做成一个定时任务，在线上每分钟检测一次，发现死锁立即告警。")
    w.raw(pre("""死锁的四个必要条件（缺一不可，打破任意一个即可解除）：

  +----------------------------------------------------------+
  | 1 互斥       资源同一时刻只能被一个线程占用               |
  | 2 持有并等待  持有资源的同时等待其他资源                   |
  | 3 不可抢占    不能强行夺走别人的资源                       |
  | 4 循环等待    形成 A->B->A 的环形等待链                    |
  +----------------------------------------------------------+

  实践中最容易打破的是 3 和 4：
    打破 4 -> 全局定义锁顺序，所有地方按同一顺序获取
    打破 3 -> 使用 tryLock 超时，拿不到就放弃并释放已持有的锁

实战中五种常见死锁场景：
  1) 简单互锁        -> 统一锁顺序
  2) 嵌套 synchronized 调用 -> 明确锁边界，避免持锁时调外部代码
  3) 数据库锁等待    -> 统一 SQL 执行顺序，缩短事务（最隐蔽！）
  4) 线程池任务互相依赖 -> 禁止在池内任务中同步等待同一池的任务
  5) 不同类加载器的类锁 -> 分布式/热部署环境，改用分布式锁"""))
    w.raw(pre("""在线排查四板斧：

  1 jps -l
     -> 找到 Java 进程 PID

  2 jstack <pid>
     -> 打印所有线程栈，JVM 会直接告诉你有死锁：
       "Found one Java-level deadlock:"
       "waiting to lock"   <- 想拿但没拿到的锁
       "locked ownable synchronizers" <- 已持有的锁
       java.lang.Thread.State: BLOCKED

  3 jconsole / jvisualvm
     -> 图形化界面，点「检测死锁」按钮

  4 arthas（阿里开源，最推荐，生产环境无需重启）
     thread -b                -> 直接找出阻塞其他线程的元凶
     thread -n 3              -> CPU 占用前 3 的线程栈
     thread --state BLOCKED   -> 所有 BLOCKED 线程
     thread -i 1000           -> 采样 1 秒的 CPU 占用"""))
    w.h3("12.2 十大经典陷阱实测")
    w.raw(code_block("ch12/_02_CommonPitfalls.java"))
    w.raw(out_block("""===== 陷阱1：SimpleDateFormat 非线程安全 =====
  50 个线程共用 SimpleDateFormat → 异常次数 = 14（常见 NumberFormatException / 结果错乱）

===== 陷阱2：Integer 缓存导致的「锁失效」=====
  Integer.valueOf(100) == Integer.valueOf(100)  → true   （在缓存范围内，同一对象）
  Integer.valueOf(1000) == Integer.valueOf(1000) → false  （超出缓存范围，不同对象）
  → 如果用 Integer 当锁对象，值相同但对象不同 → 锁根本锁不住！

===== 陷阱3：字符串常量池锁（不同模块互相阻塞）=====
  模块A 拿到 "common" 的锁
  模块B 尝试拿同一把（字符串常量池）锁，被无辜阻塞...
  模块B 等了 250ms 才拿到

===== 陷阱4：HashMap 并发 put 数据丢失 =====
  HashMap 期望 size = 800000，实际 = 561238，丢失 = 238762"""))
    w.p("三个实验都很触目惊心：<strong>SimpleDateFormat 出现 14 次异常</strong>、"
        "<strong>HashMap 丢了 23 万条数据</strong>、"
        "<strong>两个无关模块因为字符串常量池互相阻塞</strong>。"
        "这些都是在真实项目里会造成生产事故的代码。")
    w.raw(table(["#", "陷阱", "一句话解决"], [
        ["1", "SimpleDateFormat 非线程安全", "用 <code>DateTimeFormatter</code>（本身线程安全）"],
        ["2", "小整数 Integer 缓存", "别用 Integer 做锁对象，用 <code>new Object()</code>"],
        ["3", "字符串常量池锁", "别用 String 做锁对象"],
        ["4", "HashMap 并发丢数据", "用 <code>ConcurrentHashMap</code>"],
        ["5", "DCL 缺 volatile", "加 volatile"],
        ["6", "无界队列 OOM", "手动 new ThreadPoolExecutor + 有界队列"],
        ["7", "ThreadLocal 泄漏", "<code>finally</code> 里 remove()"],
        ["8", "父子线程池死锁", "分离线程池 / 异步编排"],
        ["9", "volatile 数组元素", "用 <code>AtomicIntegerArray</code>"],
        ["10", "interrupt 被吞", "捕获后必须 <code>Thread.currentThread().interrupt()</code>"],
    ]))
    w.raw(tip("danger", "第 10 个陷阱特别重要：为什么必须恢复中断标志？",
              "<pre style='margin:0'>try { Thread.sleep(1000); }\n"
              "catch (InterruptedException e) {\n"
              "    Thread.currentThread().interrupt();  // &lt;- 必须！\n"
              "    return;\n"
              "}</pre>"
              "<strong>原因一</strong>：中断是「协作式」的机制。上层代码可能也在依赖中断标志做决策，"
              "你吞掉中断会让上层的 <code>while(!Thread.interrupted())</code> 永远循环下去。<br>"
              "<strong>原因二</strong>：吞掉中断会让「优雅停机」「超时取消」这两个核心功能全部失效"
              "——Spring 的 <code>@PreDestroy</code>、线程池的 <code>shutdownNow</code> 都依赖中断。"
              "这是线上「服务关不掉，只能 kill -9」的常见原因。"))
    w.end()
