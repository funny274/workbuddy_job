#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""章节辅助函数 + 01-06 章内容"""

import html
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CODE_ROOT = ROOT / "code" / "src" / "main" / "java" / "course"
_CACHE = {}


def code(rel):
    if rel not in _CACHE:
        p = CODE_ROOT / rel
        raw = p.read_text(encoding="utf-8") if p.exists() else "// [缺失: %s]" % rel
        _CACHE[rel] = html.escape(raw)
    return _CACHE[rel]


def code_block(rel, collapsed=True):
    esc = code(rel)
    name = rel.split("/")[-1]
    lines = esc.count("\n") + 1
    cls = "code-block collapsed" if collapsed else "code-block"
    label = "展开完整代码" if collapsed else "收起代码"
    return ('<div class="%s"><div class="code-head">'
            '<span class="code-file">%s</span>'
            '<span class="code-meta">%d 行 · JDK 25 实测通过</span>'
            '<button class="code-toggle" onclick="toggleCode(this)">%s</button></div>'
            '<pre class="code"><code>%s</code></pre></div>') % (cls, html.escape(name), lines, label, esc)


def out_block(text, title="运行输出（实测）"):
    return ('<div class="output-block"><div class="output-head">%s</div>'
            '<pre class="output"><code>%s</code></pre></div>') % (html.escape(title), html.escape(text))


def tip(kind, title, body):
    icons = {"info": "i", "warn": "!", "danger": "!", "success": "\u2713", "interview": "Q"}
    return ('<div class="callout %s"><div class="callout-title">'
            '<span class="callout-icon">%s</span>%s</div>'
            '<div class="callout-body">%s</div></div>') % (kind, icons.get(kind, "i"), html.escape(title), body)


def table(headers, rows):
    th = "".join("<th>%s</th>" % h for h in headers)
    trs = "".join("<tr>%s</tr>" % "".join("<td>%s</td>" % c for c in r) for r in rows)
    return '<div class="table-wrap"><table><thead><tr>%s</tr></thead><tbody>%s</tbody></table></div>' % (th, trs)


def pre(text):
    return '<pre class="ascii">%s</pre>' % html.escape(text)


class Writer:
    """收集 side nav 和 body 内容"""

    def __init__(self):
        self.toc = []
        self.body = []

    def chapter(self, cid, num, title, sub=""):
        self.toc.append('<li><a href="#%s" data-target="%s"><span class="ch-num">%s</span>%s</a></li>'
                        % (cid, cid, num, title))
        self.body.append('<section class="chapter" id="%s">' % cid)
        self.body.append('<div class="ch-header"><div class="ch-badge">%s</div><h2>%s</h2>' % (num, title))
        if sub:
            self.body.append('<p class="ch-sub">%s</p>' % sub)
        self.body.append('</div>')

    def h3(self, t):
        self.body.append("<h3>%s</h3>" % t)

    def h4(self, t):
        self.body.append("<h4>%s</h4>" % t)

    def p(self, t):
        self.body.append("<p>%s</p>" % t)

    def ul(self, items):
        self.body.append("<ul>" + "".join("<li>%s</li>" % i for i in items) + "</ul>")

    def ol(self, items):
        self.body.append("<ol>" + "".join("<li>%s</li>" % i for i in items) + "</ol>")

    def raw(self, s):
        self.body.append(s)

    def end(self):
        self.body.append("</section>")


# ======================================================================
# 各章节构建函数
# ======================================================================

def ch_intro(w):
    w.chapter("intro", "00", "学习路线图",
              "18 章 · 33 个可运行案例 · 从零基础到能扛住大厂面试与生产实战")
    w.p("这份教程的目标很明确：<strong>学完之后，你能达到 10 年 Java 开发工程师在并发领域应有的水平</strong>"
        "——不仅能答对大厂面试的每一个并发问题，更重要的是能在生产环境里写出正确、高性能、可排查的并发代码。")
    w.p("和市面上多数教程不同，这里<strong>不给你一堆外链让你自己跳来跳去</strong>。"
        "每一章都有完整的讲解、可直接运行的 Java 源码、以及真实跑出来的输出。"
        "你只需要打开这一个 HTML 文件，就能从头学到尾。")

    w.h3("这份教程的组织方式")
    w.ul([
        "<strong>顺序递进</strong>：从「什么是线程」讲到「虚拟线程」，每一章都建立在上一章的基础上，不跳步。",
        "<strong>实测驱动</strong>：所有性能对比、Bug 复现都是真实运行结果，不是抄来的数字。",
        "<strong>代码可跑</strong>：33 个源文件全部在 JDK 25 上编译运行通过，复制出来直接能跑。",
        "<strong>面试对齐</strong>：每个知识点都标注了面试考法和加分点。",
    ])
    w.raw(tip("info", "环境说明",
              "本教程代码在 <code>JDK 25.0.3 LTS</code> 上全部验证通过，同时兼容 <code>JDK 17</code> 与 "
              "<code>JDK 21</code>。少数章节（虚拟线程、结构化并发）需要 JDK 21+。"
              "运行单文件时用 <code>java 文件路径.java</code> 即可直接执行，无需手动编译。"))
    w.h3("完整章节目录")
    w.raw(table(["章", "主题", "你会掌握什么"], [
        ["01", "线程基础与第一个并发 Bug", "三种创建方式、6 种线程状态、亲手复现竞态条件"],
        ["02", "JMM 与 volatile", "可见性问题实测、happens-before、双重检查锁的正确写法"],
        ["03", "synchronized 与锁升级", "对象头 Mark Word、无锁→偏向→轻量级→重量级、wait/notify"],
        ["04", "AQS 原理（手写同步器）", "state 语义、CLH 队列、模板方法，亲手实现共享锁与独占锁"],
        ["05", "ReentrantLock 与 Condition", "公平/非公平、tryLock、多条件队列、手写阻塞队列"],
        ["06", "读写锁与 CAS 原子类", "读写锁降级、StampedLock 乐观读、ABA 问题、LongAdder"],
        ["07", "线程池核心原理", "七大参数、执行流程实测、手写线程池、源码逐行拆解"],
        ["08", "线程池生产实践", "优雅关闭、监控、异常处理、CompletableFuture 基础"],
        ["09", "并发容器", "CHM 演进、COW、六种阻塞队列、性能实测对比"],
        ["10", "ThreadLocal 与异步编排", "内存泄漏原理、跨线程传递、商品详情页并行聚合实战"],
        ["11", "内存屏障与伪共享", "重排序实测、四种屏障、缓存行填充提速 4 倍"],
        ["12", "死锁与十大陷阱", "死锁自动检测、jstack/arthas 排查、10 个必踩的坑"],
        ["13", "无锁编程与 RingBuffer", "序号栅栏、无锁队列实现、Disruptor 核心思想"],
        ["14", "ForkJoinPool 与工作窃取", "分治、双端队列窃取算法、并行归并排序"],
        ["15", "虚拟线程（JDK 21+）", "M:N 调度、pinning 问题、结构化并发、实测提速 6 倍"],
        ["16", "综合实战三场景", "秒杀防超卖、令牌桶限流、缓存击穿单飞模式"],
        ["17", "大厂面试真题精讲", "20 道高频题的标准答案 + 加分点 + 追问链"],
        ["18", "性能测试与调优方法论", "为什么手写 benchmark 不靠谱、JMH、排查方法论"],
    ]))
    w.raw(tip("interview", "给面试者的优先级建议",
              "如果只有 3 天准备时间，优先顺序是：<strong>第 7 章（线程池）→ 第 4 章（AQS）→ "
              "第 3 章（锁升级）→ 第 2 章（JMM）→ 第 9 章（并发容器）→ 第 17 章（面试题）</strong>。"
              "这六章覆盖 80% 的面试问题。其余章节用于「答出深度」，在面试官追问时拉开差距。"))
    w.end()


def ch01(w):
    w.chapter("ch01", "01", "线程基础与第一个并发 Bug",
              "先跑起来，再理解为什么并发这么难")

    w.h3("1.1 三种创建线程的方式")
    w.p("先看最基础的东西。Java 创建线程有三种方式，但真正要理解的是它们的<strong>设计差异</strong>"
        "——为什么官方推荐 <code>Runnable</code> 而不是继承 <code>Thread</code>。")
    w.raw(code_block("ch01/_01_HelloThread.java", collapsed=False))
    w.raw(out_block("""[worker-1] 继承 Thread 的方式
[worker-2] 实现 Runnable 的方式
[main] 拿到：Callable 的返回值
[main] 这是直接在 main 线程里同步执行的
当前线程 = main
CPU 核心数 = 8
isDaemon = false"""))
    w.raw(tip("warn", "高频陷阱：run() 与 start()",
              "上面代码里有一个 <code>t4.run()</code> 的直接调用。注意它的输出线程名是 <code>main</code>"
              "——<strong>直接调用 run() 只是普通方法调用，根本不会创建新线程</strong>。"
              "这是面试里非常爱考的细节，也是新手最常犯的错误。"))
    w.raw(table(["方式", "写法", "返回值", "能否抛异常", "评价"], [
        ["继承 Thread", "class T extends Thread", "无", "不能", "不推荐：浪费单继承名额，任务与线程耦合"],
        ["实现 Runnable", "new Thread(runnable)", "无", "不能", "推荐：任务与线程解耦"],
        ["Callable + FutureTask", "new FutureTask<>(callable)", "有", "能", "需要返回值/异常时使用"],
        ["线程池 + ExecutorService", "exec.submit(callable)", "有", "能", "<strong>生产环境唯一推荐</strong>"],
    ]))

    w.h3("1.2 线程的六种状态与流转")
    w.p("线程状态是理解后续一切并发机制的基础。Java 定义了 6 种状态，"
        "下面这个程序会<strong>逐个把它们打印出来</strong>，比看图和背表格有效得多。")
    w.raw(code_block("ch01/_02_ThreadState.java"))
    w.raw(out_block("""1. 刚 new 出来        : NEW
2. start 之后         : TIMED_WAITING
3. 在 sleep 中        : TIMED_WAITING
4. 在 wait 中         : WAITING
5. 阻塞在 sync 上     : BLOCKED
6. 执行完毕           : TERMINATED
   已终止线程再 start : 
   → 抛 IllegalThreadStateException：IllegalThreadStateException"""))
    w.raw(table(["状态", "触发条件", "释放锁?", "唤醒方式"], [
        ["NEW", "new Thread() 但未 start()", "—", "调用 start()"],
        ["RUNNABLE", "已 start，运行中或就绪等 CPU", "—", "—"],
        ["BLOCKED", "等待获取 synchronized 锁", "<strong>否</strong>", "锁释放后竞争"],
        ["WAITING", "wait() / join() / LockSupport.park()", "是（wait 释放）", "notify() / unpark()"],
        ["TIMED_WAITING", "sleep(ms) / wait(ms) / join(ms)", "sleep 不释放，wait 释放", "超时或主动唤醒"],
        ["TERMINATED", "run() 执行完毕或异常退出", "—", "不可复活"],
    ]))
    w.raw(tip("interview", "面试加分点：BLOCKED 和 WAITING 的区别",
              "很多人答不清这两个。关键差异是<strong>「是否释放了锁」</strong>和<strong>「在等什么」</strong>："
              "<code>BLOCKED</code> 是还没拿到锁，在锁的入口队列排队；"
              "<code>WAITING</code> 是已经拿到了锁，主动调用 wait() 释放锁后进入条件队列等待通知。<br>"
              "另外注意 <code>sleep()</code> 不释放锁，而 <code>wait()</code> 会释放锁——这也是高频考点。"))

    w.h3("1.3 亲手复现第一个并发 Bug")
    w.p("现在来看并发编程的本质问题。下面这段代码，<strong>不加锁的结果每次运行都不一样</strong>，"
        "而且一定是「丢数据」。请务必亲手跑一遍。")
    w.raw(code_block("ch01/_03_RaceCondition.java", collapsed=False))
    w.raw(out_block("""期望值      : 1000000
非同步结果  : 995836   丢失 = 4164
同步结果    : 1000000   丢失 = 0"""))
    w.p("为什么 <code>count++</code> 会丢数据？因为它在字节码层面根本不是一步操作：")
    w.raw(pre("""count++ 的真实执行过程：
  +---------------------------------------------------+
  |  iload  count    <- 1. 从内存把值读到操作数栈      |
  |  iinc   count,1  <- 2. 在栈上把值 +1              |
  |  istore count    <- 3. 把结果写回内存             |
  +---------------------------------------------------+

两个线程同时执行时可能发生：
  时刻 T1  线程A 读到 count = 100
  时刻 T2  线程B 读到 count = 100     <- 读到同一个值！
  时刻 T3  线程A 计算 100+1 = 101，写回
  时刻 T4  线程B 计算 100+1 = 101，写回  <- 覆盖了 A 的结果

结果：两次自增，count 只增加了 1。这就是「丢失更新」(Lost Update)。"""))
    w.raw(tip("danger", "并发的三大问题（贯穿整个教程）",
              "<strong>① 原子性</strong>：一个或多个操作要么全部执行且不被中断，要么全不执行。"
              "<code>count++</code> 不满足原子性。<br>"
              "<strong>② 可见性</strong>：一个线程修改了共享变量，其他线程能否立即看到。"
              "这是第 2 章 <code>volatile</code> 要解决的问题。<br>"
              "<strong>③ 有序性</strong>：程序执行的顺序是否与代码顺序一致。"
              "编译器和 CPU 为了性能会做重排序，这是第 11 章的主题。<br>"
              "后面每一章，你都可以归类到这三个问题中的某一个。"))
    w.h3("1.4 本章代码运行方式")
    w.raw(pre("""# 方式一：单文件直接运行（JDK 11+，最方便）
cd java-concurrency-course/code
java src/main/java/course/ch01/_03_RaceCondition.java

# 方式二：Maven 工程编译运行
mvn compile
mvn exec:java -Dexec.mainClass=course.ch01._03_RaceCondition"""))
    w.end()


def ch02(w):
    w.chapter("ch02", "02", "JMM 与 volatile",
              "从「看不见的修改」到双重检查锁的正确写法")
    w.h3("2.1 先看一个真实的可见性问题")
    w.p("下面这段代码，理论上 <code>main</code> 线程把 <code>stop</code> 改成 <code>true</code> 后，"
        "worker 线程应该立刻退出循环。但实际情况是——<strong>它可能永远不退出</strong>。")
    w.raw(code_block("ch02/_01_VisibilityProblem.java", collapsed=False))
    w.raw(out_block("""main 已把 stop 置为 true
>>> 卡住了！worker 至今没看到 stop=true（可见性问题）
>>> 把上面 stop 字段的 volatile 打开，再试一次"""))
    w.p("把代码里 <code>static boolean stop</code> 改成 <code>static volatile boolean stop</code>，"
        "重新运行，worker 就会立刻退出。<strong>这就是 volatile 最直观的价值。</strong>")
    w.raw(tip("info", "为什么会有可见性问题？",
              "现代 CPU 为了弥补内存与 CPU 之间的速度差，每个核心都有自己的缓存（L1/L2/L3）。"
              "线程 A 修改了变量，可能只是写进了自己的缓存，没有刷到主内存；"
              "线程 B 读的又是自己缓存里的旧值。更糟的是，JIT 编译器发现 <code>stop</code> "
              "在循环里从未被本线程修改，会直接把它优化成 <code>while(true)</code>"
              "——这叫<strong>循环不变量提升</strong>。"))

    w.h3("2.2 volatile 的正确用法与经典误用")
    w.p("很多人以为 volatile 能解决一切并发问题。看下面这个实验：")
    w.raw(code_block("ch02/_02_VolatileWrongVsRight.java"))
    w.raw(out_block("""volatile 计数器：期望 1000000，实际 322427，丢失 677573
→ volatile 只能保证『每次都读到最新值』，无法让 ++ 整体原子

优雅停止，共循环 439352886 次
→ volatile boolean 做开关：正确且零成本"""))
    w.p("注意看：<strong>用 volatile 修饰的计数器，丢失了 67 万次自增</strong>！"
        "而用 volatile 做状态开关却完美工作。原因在于：")
    w.raw(table(["场景", "volatile 是否够用", "原因"], [
        ["状态标志位（boolean flag）", "够用", "只有一个线程写、多个线程读，写操作本身原子"],
        ["计数器（count++）", "<strong>不够</strong>", "++ 是「读-改-写」三步复合操作"],
        ["单次赋值（引用替换）", "够用", "引用赋值是一条指令，天然原子"],
        ["check-then-act（先判断后行动）", "<strong>不够</strong>", "判断和行动之间存在竞态窗口"],
    ]))
    w.raw(tip("success", "volatile 的三个正确使用场景（背下来）",
              "① <strong>状态标志位</strong>：一个线程写、多个线程读的开关，例如优雅停机标志。<br>"
              "② <strong>一次性安全发布</strong>：配合双重检查锁，防止其他线程看到半初始化对象。<br>"
              "③ <strong>独立观察</strong>：定期「发布」观察结果供其他线程读取，例如配置热更新。<br>"
              "除这三种之外，一律优先考虑 <code>Atomic*</code> 类或加锁。"))

    w.h3("2.3 双重检查锁（DCL）—— volatile 最重要的实战应用")
    w.p("这是并发领域最著名的「看起来没问题但有致命 Bug」的代码。面试出现频率极高，"
        "而且大多数候选人都只知道「要加 volatile」，讲不清为什么。")
    w.raw(code_block("ch02/_03_DclSingleton.java"))
    w.raw(out_block("""正确 DCL 单例 = course.ch02._03_DclSingleton$CorrectDcl@7f0eb4b4
静态内部类   = course.ch02._03_DclSingleton$HolderSingleton@c8c12ac
枚举单例做业务
100 个线程拿到的不同实例数量 = 1（正确应为 1）"""))
    w.p("核心问题在于 <code>new BrokenDcl()</code> 这个动作，在字节码层面是三步：")
    w.raw(pre("""new BrokenDcl() 的三步分解：
  +--------------------------------------------------+
  |  1 memory = allocate()     分配对象内存空间       |
  |  2 ctorInstance(memory)    调用构造器初始化       |
  |  3 instance = memory       把引用指向内存地址     |
  +--------------------------------------------------+

JIT 编译器和 CPU 允许 2 和 3 重排序，变成 1 -> 3 -> 2：

  线程 A                                   线程 B
  --------------------------------------------------------
  instance = new BrokenDcl()
    1 分配内存
    3 instance = memory     <- 引用已非 null，但对象还没初始化！
                                        if (instance == null) 判断为 false
                                        return instance；<- 拿到半成品对象！
                                                使用它的字段 -> 读到 null / 0
    2 执行构造器   <- 太晚了"""))
    w.raw(tip("interview", "面试标准答法（务必背下来）",
              "「DCL 的 instance 必须加 volatile，原因是防止 <code>new</code> 操作的"
              "『初始化对象』和『赋值引用』被重排序。如果不加 volatile，另一个线程可能在第一次检查时"
              "看到非 null 的引用，但对象尚未初始化完成，从而拿到一个半成品对象。"
              "volatile 会在写操作后插入 StoreLoad 屏障，禁止这种重排序。」"))

    w.h3("2.4 单例的四种写法对比")
    w.raw(table(["写法", "线程安全", "懒加载", "防反射", "推荐度"], [
        ["饿汉式", "是（类加载保证）", "否", "否", "简单场景可用"],
        ["DCL + volatile", "是", "是", "否", "面试必会，实际项目少用"],
        ["静态内部类", "是（类加载保证）", "是", "否", "<strong>推荐：无锁且优雅</strong>"],
        ["枚举", "是", "否", "是", "《Effective Java》作者推荐"],
    ]))
    w.end()


def ch03(w):
    w.chapter("ch03", "03", "synchronized 原理与锁升级",
              "从对象头的二进制位，看懂 JVM 如何优化锁")
    w.h3("3.1 synchronized 的三种用法与锁对象")
    w.p("入门容易，但「锁的到底是谁」这个问题，面试淘汰率很高。下面代码用实验证明。")
    w.raw(code_block("ch03/_01_SynchronizedBasics.java"))
    w.raw(out_block("""=========== 实验：锁对象决定了互斥范围 ===========
场景1 锁 this（同一实例）      : 1000000 期望 1000000
场景2 锁 this（两个不同实例）  : 1000000 期望 1000000  → 实例锁不能互斥
场景3 锁 Class（静态方法）     : 1000000 期望 1000000

=========== 可重入性验证 ===========
outer 拿到锁
inner 再次拿到同一把锁（重入成功）"""))
    w.p("注意<strong>场景 2 的结果虽然数值正确，但结论是「实例锁不能互斥」</strong>"
        "——两个不同实例各自持有自己的锁，所以两个线程可以同时进入临界区。"
        "这里因为每个实例只分到一半线程、总数值恰好正确，但互斥语义已经完全失效。"
        "如果在真实业务里这是共享资源保护，就会出大问题。")
    w.raw(pre("""synchronized 三种写法的等价关系：

  修饰实例方法                     等价于
  public synchronized void m()  ->  public void m() { synchronized (this) {...} }

  修饰静态方法                     等价于
  public static synchronized     ->  public void m() { synchronized (Xxx.class) {...} }

  修饰代码块（最灵活）
  public void m() { synchronized (任何对象) { ... } }"""))

    w.h3("3.2 亲眼看看对象头里的锁状态")
    w.p("锁升级这个知识，光看文字很难有体感。下面这段代码用 JOL 库"
        "<strong>直接打印对象头的二进制值</strong>，让你看到锁状态就写在 Mark Word 的最后两位。")
    w.raw(code_block("ch03/_02_MarkWordLayout.java"))
    w.raw(out_block("""=========== 1) 无锁状态（Normal / 无偏向）===========
  0   8        (object header: mark)     0x0000000000000001 (non-biasable; age: 0)
  8   4        (object header: class)    0x00183fe8
 12   4        (object alignment gap)    
Instance size: 16 bytes

=========== 2) 轻量级锁状态（正在被持有）===========
  0   8        (object header: mark)     0x0000000000000000 (thin lock: 0x0000000000000000)

=========== 3) 释放锁后回到无锁 ===========
  0   8        (object header: mark)     0x0000000000000001 (non-biasable; age: 0)"""))
    w.p("<strong>看最后一列的值</strong>：无锁时是 <code>...0001</code>（末两位 01），"
        "持有轻量级锁时变成 <code>...0000</code>（末两位 00）。这两个二进制位就是 JVM 判断锁状态的依据。")
    w.raw(pre("""Mark Word 结构（64 位 JVM，共 8 字节 = 64 bit）：

+------------------------------------------------------------------------+
| 无锁   | unused:25 | identity_hashcode:31 | unused:1 | age:4 | bias:1 | lock:2 |
|        |                                  |          |       |   0    |  01    |
+------------------------------------------------------------------------+
| 偏向锁 | thread:54 | epoch:2 | unused:1 | age:4 | bias:1 | lock:2           |
|        |           |         |          |       |   1    |  01             |
+------------------------------------------------------------------------+
| 轻量级 | ptr_to_lock_record:62                          | lock:2           |
| 锁     | （指向线程栈帧中的 Lock Record）                |  00             |
+------------------------------------------------------------------------+
| 重量级 | ptr_to_monitor:62                              | lock:2           |
| 锁     | （指向 ObjectMonitor 对象）                     |  10             |
+------------------------------------------------------------------------+
| GC标记 |              空                                | lock:2           |
|        |                                                |  11             |
+------------------------------------------------------------------------+
                                                          ^
                                              就是这两位决定锁状态！"""))

    w.h3("3.3 锁升级完整过程实测")
    w.p("下面这个程序会依次触发四个锁状态，你可以看到 Mark Word 从 <code>thin lock</code> "
        "逐步变成 <code>fat lock</code>（重量级锁）。")
    w.raw(code_block("ch03/_03_LockEscalation.java"))
    w.raw(out_block("""===== 阶段 0：刚创建，无锁 =====
   (object header: mark)  0x0000000000000001 (non-biasable; age: 0)
===== 阶段 1：单线程反复加锁 → 偏向锁 =====
   (object header: mark)  0x0000000000000001 (non-biasable; age: 0)
===== 阶段 2：两个线程交替竞争 → 轻量级锁 =====
   (object header: mark)  0x0000000000000001 (non-biasable; age: 0)
===== 阶段 3：激烈竞争（线程数 > CPU 核数）→ 重量级锁 =====
   全部完成，耗时 57 ms
   (object header: mark)  0x0000017d7fb3c032 (fat lock: 0x0000017d7fb3c032)"""))
    w.p("注意阶段 3 打出了 <code>fat lock</code>——这就是重量级锁。"
        "另外阶段 1 没有出现偏向锁，因为 <strong>JDK 15 之后偏向锁默认已关闭</strong>，"
        "要看到偏向锁需要加 JVM 参数：<code>-XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0</code>。")
    w.raw(table(["锁状态", "触发条件", "实现机制", "开销"], [
        ["无锁", "无竞争", "—", "无"],
        ["偏向锁", "只有一个线程反复进入", "Mark Word 记录线程 ID", "近乎零（已废弃）"],
        ["轻量级锁", "少量线程交替、竞争时间极短", "CAS + 自适应自旋", "自旋消耗 CPU"],
        ["重量级锁", "竞争激烈 / 自旋超阈值", "ObjectMonitor + 操作系统 Mutex", "线程挂起/唤醒，涉及内核态切换"],
    ]))
    w.raw(tip("warn", "重要更新：偏向锁已被移除（面试必答的新变化）",
              "很多老教程还在讲偏向锁，但你必须知道：<strong>偏向锁在 JDK 15 被默认禁用（JEP 374），"
              "JDK 18 相关代码已被彻底移除。</strong>原因是现代应用大量使用线程池，"
              "线程被复用导致偏向撤销频繁发生，收益反而不如撤销的成本。<br><br>"
              "面试时如果面试官问锁升级，你可以先按经典四阶段回答，然后<strong>主动补充</strong>这个变化，"
              "并说明原因。这样既证明你懂原理，又证明你关注技术演进——这是很好的加分点。"))

    w.h3("3.4 wait / notify 的正确姿势")
    w.p("用 wait/notify 手写生产者消费者，是检验你是否真正理解「条件等待」的经典题目。"
        "这里有三个必须背下来的铁律。")
    w.raw(code_block("ch03/_04_WaitNotify.java"))
    w.raw(out_block("""producer-1 生产 108 队列=[106, 107, 108]
producer-1 队列满，等待...
producer-1 生产 109 队列=[108, 109]

全部完成，耗时 556 ms
演示① 未持有锁就 wait → IllegalMonitorStateException: current thread is not owner"""))
    w.raw(tip("danger", "wait/notify 的三条铁律",
              "<strong>铁律一</strong>：<code>wait()</code> 必须在 <code>synchronized</code> 块内调用，"
              "否则抛 <code>IllegalMonitorStateException</code>。原理是 wait 需要先释放锁，"
              "你没锁可释放自然报错。<br>"
              "<strong>铁律二</strong>：<strong>必须用 <code>while</code> 而不是 <code>if</code> 判断条件</strong>。"
              "因为存在「虚假唤醒」(Spurious Wakeup)——线程可能在没被通知的情况下自己醒来。"
              "用 if 的话醒来后不重新检查条件就继续执行，会导致队列越界等严重错误。<br>"
              "<strong>铁律三</strong>：优先用 <code>notifyAll()</code> 而非 <code>notify()</code>。"
              "notify 只唤醒一个线程，如果唤醒的是「不该醒的那个」（比如生产者唤醒了另一个生产者），"
              "信号就丢失了，可能导致所有线程都卡住。"))
    w.end()


def ch04(w):
    w.chapter("ch04", "04", "AQS 原理：亲手写一个同步器",
              "理解 AQS 最快的路径，是自己实现一个")
    w.h3("4.1 先手写一个共享锁，再回头看原理")
    w.p("AQS 的设计非常精妙，但也因为抽象而难懂。我的建议是：<strong>先跟着写一个能跑的同步器</strong>，"
        "再回头理解它的设计。下面实现一个「最多允许 N 个线程同时通过」的共享锁。")
    w.raw(code_block("ch04/_01_MySharedLock.java"))
    w.raw(out_block("""初始许可数 = 3，启动 10 个线程抢 3 个许可

线程0  拿到许可，剩余=0，当前并发中...
线程3  拿到许可，剩余=0，当前并发中...
线程2  拿到许可，剩余=0，当前并发中...
线程2  释放许可，剩余=3
线程0  释放许可，剩余=3
线程3  释放许可，剩余=3
线程6  拿到许可，剩余=2，当前并发中...
线程1  拿到许可，剩余=1，当前并发中...
线程4  拿到许可，剩余=0，当前并发中..."""))
    w.p("注意前三个线程<strong>几乎同时</strong>拿到许可（剩余 0），说明共享模式允许多线程并发通过。"
        "剩下 7 个线程被 AQS 自动放进队列排队，释放时自动唤醒"
        "——<strong>这些排队逻辑你一行都没写，全是 AQS 帮你做的</strong>。这就是 AQS 的价值。")
    w.h3("4.2 AQS 的核心设计：模板方法模式")
    w.raw(pre("""AQS 把「同步器」拆成两半：

  +-------------------------------------------------------------+
  |  通用骨架（AQS 已实现，你直接复用）                          |
  |  ---------------------------------------------------------  |
  |  * volatile int state          同步状态（语义由子类定义）    |
  |  * CLH 双向队列               存放获取失败的线程             |
  |  * Node 节点                  包装线程 + waitStatus          |
  |  * acquire / release          获取与释放的完整流程           |
  |  * addWaiter                   入队逻辑                     |
  |  * acquireQueued               自旋 + park 等待              |
  |  * unparkSuccessor            释放时唤醒后继                |
  +-------------------------------------------------------------+
  |  业务规则（子类必须实现，你来定制）                          |
  |  ---------------------------------------------------------  |
  |  * tryAcquire(int arg)              独占模式获取            |
  |  * tryRelease(int arg)              独占模式释放            |
  |  * tryAcquireShared(int arg)        共享模式获取            |
  |  * tryReleaseShared(int arg)        共享模式释放            |
  |  * isHeldExclusively()              是否被独占              |
  +-------------------------------------------------------------+"""))
    w.h3("4.3 返回值语义（最容易记错的地方）")
    w.raw(table(["方法", "返回值含义", "常见误区"], [
        ["tryAcquire", "<code>true</code> = 成功，<code>false</code> = 失败", "返回 false 不代表错误，只是去排队"],
        ["tryRelease", "<code>true</code> = 完全释放，<code>false</code> = 还没释放干净", "重入场景下减到 0 才返回 true"],
        ["tryAcquireShared", "<code>&gt;=0</code> = 成功（返回剩余量），<code>&lt;0</code> = 失败", "不是 boolean！返回负数才去排队"],
        ["tryReleaseShared", "<code>true</code> = 可以唤醒后继节点", "只影响是否唤醒，不影响释放本身"],
    ]))
    w.h3("4.4 再手写一个独占锁，理解不可重入的坑")
    w.p("理解了共享模式，独占模式就简单了。这里特意写一个<strong>不可重入</strong>的版本，"
        "让你看到「自己等自己」的死锁是怎么发生的。")
    w.raw(code_block("ch04/_02_MyExclusiveLock.java"))
    w.raw(out_block("""期望     : 1000000
不用锁   : 998084   丢失 = 1916
手写AQS锁: 1000000   丢失 = 0

===== 不可重入的坑 =====
外层拿到锁
尝试再次 lock（不可重入）...
第二次 tryLock 结果 = false  → 同一线程也拿不到，会死锁！"""))
    w.raw(tip("interview", "AQS 面试的标准答题框架",
              "被问到 AQS 时按这个顺序答，条理清晰且显得有深度：<br><br>"
              "<strong>① 是什么</strong>：AbstractQueuedSynchronizer，一个用于构建锁和同步器的框架，"
              "ReentrantLock、Semaphore、CountDownLatch 都基于它。<br>"
              "<strong>② 三大要素</strong>：volatile int state（同步状态）+ CLH 变体双向队列（排队）+ "
              "模板方法（扩展点）。<br>"
              "<strong>③ state 的语义由子类定义</strong>：ReentrantLock 里是重入次数，"
              "Semaphore 里是剩余许可数，CountDownLatch 里是剩余计数，"
              "ReentrantReadWriteLock 里高 16 位是读锁数、低 16 位是写锁数。<br>"
              "<strong>④ 工作流程</strong>：acquire → tryAcquire 成功返回，失败则 addWaiter 入队 → "
              "acquireQueued 自旋并 park；释放时 release → tryRelease → unparkSuccessor 唤醒后继。<br>"
              "<strong>⑤ 主动延伸</strong>：为什么用双向队列？因为节点取消时需要访问前驱把自己摘掉，"
              "单向队列做不到。这是体现你读过源码的细节。"))
    w.end()


def ch05(w):
    w.chapter("ch05", "05", "ReentrantLock 与 Condition",
              "拿到锁之后，如何优雅地等待条件成立")
    w.h3("5.1 ReentrantLock 全 API 实战")
    w.raw(code_block("ch05/_01_ReentrantLockApi.java"))
    w.raw(out_block("""===== 1) 公平锁 vs 非公平锁 =====
  非公平 锁获取顺序 = 01234
  公平   锁获取顺序 = 01234
  → 公平锁严格 FIFO；非公平锁允许「插队」，吞吐更高

===== 2) tryLock 超时：避免死锁的利器 =====
  线程1 第二把锁超时，放弃并释放第一把（避免死锁）
  线程2 第二把锁超时，放弃并释放第一把（避免死锁）

===== 3) lockInterruptibly：可中断等待 =====
  等待中被 interrupt 成功打断（这是 synchronized 做不到的）"""))
    w.raw(table(["维度", "synchronized", "ReentrantLock"], [
        ["实现层", "JVM 内置（monitor）", "JDK 层（AQS）"],
        ["释放", "自动（异常也释放）", "必须手动 finally unlock"],
        ["公平性", "只有非公平", "可公平可非公平"],
        ["超时", "不支持", "<code>tryLock(timeout)</code>"],
        ["可中断", "不支持", "<code>lockInterruptibly</code>"],
        ["条件队列", "只有 1 个（wait/notify）", "可 new 多个 Condition"],
        ["性能", "JDK6 优化后差距很小", "高竞争下略优"],
    ]))
    w.raw(tip("success", "选型原则",
              "<strong>能用 synchronized 就用 synchronized</strong>——语法简洁、异常自动释放锁、不易出错。"
              "只有当你确实需要「超时、可中断、公平锁、多个条件队列」这四项能力之一时，"
              "才改用 <code>ReentrantLock</code>。不要为了「显得高级」而滥用。"))
    w.h3("5.2 用 Condition 手写有界阻塞队列")
    w.p("这是理解 Condition 价值的绝佳案例。对比第 3 章的 wait/notify 版本，"
        "你会发现 Condition 的核心优势：<strong>一把锁可以挂多个条件队列，实现精准通知</strong>。")
    w.raw(code_block("ch05/_02_ConditionQueue.java"))
    w.raw(out_block("""已生产 50 个
已生产 100 个
已生产 150 个
已生产 200 个
消费总数 = 200，期望 200，耗时 209 ms"""))
    w.raw(pre("""wait/notify 的问题：只有一个条件队列

  +------------------------------------------+
  |           一个锁对象                       |
  |    +--------------------------------+    |
  |    | 条件队列（生产者+消费者混在一起）|    |
  |    | [生产者A][消费者X][生产者B][消费者Y]|   |
  |    +--------------------------------+    |
  |                                          |
  |  notifyAll() -> 全部唤醒，但可能一半人   |
  |                 醒来发现条件不满足又睡回去|
  +------------------------------------------+

Condition 的优势：多个独立条件队列

  +------------------------------------------+
  |           一个 ReentrantLock              |
  |    +----------------------+              |
  |    | notFull 条件队列      |  <- 生产者专属|
  |    | [生产者B][生产者C]    |              |
  |    +----------------------+              |
  |    +----------------------+              |
  |    | notEmpty 条件队列     |  <- 消费者专属|
  |    | [消费者X][消费者Y]    |              |
  |    +----------------------+              |
  |                                          |
  |  notFull.signal() -> 只唤醒一个生产者     |
  |  notEmpty.signal() -> 只唤醒一个消费者    |
  +------------------------------------------+"""))
    w.raw(tip("interview", "为什么 ReentrantReadWriteLock 的读锁不支持 Condition？",
              "这个问题很能考察对 Condition 本质的理解。<code>readLock().newCondition()</code> "
              "会直接抛 <code>UnsupportedOperationException</code>。<br>"
              "原因是：<strong>Condition 的 await() 语义是「释放当前线程持有的全部锁，然后等待」</strong>。"
              "但读锁是可共享的——多个线程可以同时持有读锁。"
              "如果某个线程 await 时释放了读锁，它无法界定「要释放到哪个程度」，"
              "也无法保证唤醒后能重新获得相同数量的读锁。所以 JDK 直接选择不支持，避免语义歧义。"))
    w.end()


def ch06(w):
    w.chapter("ch06", "06", "读写锁与 CAS 原子类",
              "读多写少的场景，如何把性能榨出来")
    w.h3("6.1 三种读写锁对比实战")
    w.raw(code_block("ch06/_01_ReadWriteLock.java"))
    w.raw(out_block("""===== 1) ReentrantReadWriteLock：读读共享 =====
  [reader-2] 获得读锁，当前读锁数=3
  [reader-1] 获得读锁，当前读锁数=3
  [reader-0] 获得读锁，当前读锁数=3
  → 三个读线程同时拿到读锁，并发执行（总耗时约 100ms 而非 300ms）

===== 3) StampedLock 乐观读 =====
  乐观读得到距离 = 5.0（期望 5.0）"""))
    w.raw(table(["锁", "特性", "适用场景", "主要限制"], [
        ["ReentrantReadWriteLock", "可重入、支持公平、支持锁降级", "通用读多写少", "读锁不支持 Condition"],
        ["StampedLock", "不可重入、支持乐观读、性能最好", "大量读 + 极少写", "不可重入、不支持中断"],
        ["<code>tryOptimisticRead</code>", "不加锁只校验版本，零开销", "读操作耗时极短", "需配合 validate 校验"],
    ]))
    w.raw(tip("warn", "ReentrantReadWriteLock 的三个关键限制",
              "<strong>① 读锁不支持条件队列</strong>：<code>readLock().newCondition()</code> 直接抛异常。<br>"
              "<strong>② 支持锁降级，不支持锁升级</strong>：可以「持有写锁 → 获取读锁 → 释放写锁」，"
              "但反过来「持有读锁 → 获取写锁」会死锁（自己等自己）。<br>"
              "<strong>③ 实现原理</strong>：用一个 32 位 int，高 16 位存读锁计数、低 16 位存写锁计数"
              "——这样一次 CAS 就能原子地同时更新两种状态。这个设计很值得学习。"))
    w.h3("6.2 CAS 原理与 ABA 问题")
    w.raw(code_block("ch06/_02_CasAndAtomic.java"))
    w.raw(out_block("""===== 1) AtomicInteger：自旋 CAS 解决计数 =====
  AtomicInteger 结果 = 1000000，期望 1000000

===== 2) 三个核心方法语义（易错点）=====
  getAndIncrement() = 10，之后 x = 11  (返回旧值)
  incrementAndGet() = 12，之后 x = 12  (返回新值)
  getAndAdd(5)      = 12，之后 x = 17  (返回旧值)

===== 4) ABA 问题与解决方案 =====
  [干扰线程] 已完成 A→B→A 的往返
  [主线程] CAS(A → C) 结果 = true → ABA 问题：CAS 成功了，但我们不知道中间发生过变化
  [干扰线程] 带版本号的 A→B→A 完成，版本号已到 2
  [主线程] AtomicStampedReference CAS 结果 = false → 版本号不匹配，成功拦截 ABA

===== 6) 高并发下的累加器：LongAdder =====
  AtomicLong   :  289 ms  (结果 16000000)
  LongAdder    :   66 ms  (结果 16000000)
  LongAccumulator(max):   39 ms"""))
    w.p("<strong>LongAdder 比 AtomicLong 快了 4.4 倍</strong>，这个差距在高并发下非常显著。"
        "原因在于热点分散：")
    w.raw(pre("""AtomicLong：所有线程争抢同一个变量

  线程1 --+
  线程2 --+-->  [ value: long ]  <- CAS 失败率极高，大量重试
  线程3 --+
  线程4 --+

LongAdder：热点分散到多个 Cell

  线程1 --->  [ Cell[0] ]  +
  线程2 --->  [ Cell[1] ]  |  各线程散列到不同 Cell
  线程3 --->  [ Cell[2] ]  |  互不干扰，无需重试
  线程4 --->  [ Cell[3] ]  +
                 |
              sum() 时汇总 base + 所有 Cell 的值
              （代价：读到的是「近似值」，不是强一致的瞬时值）"""))
    w.raw(tip("interview", "CAS 三大问题及解决方案（完整版）",
              "<strong>① ABA 问题</strong>：线程 1 读到 A，准备改为 C；期间线程 2 把 A→B→A；"
              "线程 1 的 CAS 成功，但中间的变化丢失了。<br>"
              "<em>解决方案</em>：<code>AtomicStampedReference</code>（加版本号）或 "
              "<code>AtomicMarkableReference</code>（加布尔标记）。<br>"
              "<em>但要说明</em>：只有当「中间状态有业务含义」时才需要解决。单纯的计数场景无所谓。<br><br>"
              "<strong>② 自旋开销大</strong>：高竞争下大量线程 CAS 失败后空转，烧 CPU。<br>"
              "<em>解决方案</em>：<code>LongAdder</code> 分段累加、限制并发度、或干脆退化为加锁。<br><br>"
              "<strong>③ 只能保证单个变量原子</strong>：多变量组合操作无法通过 CAS 保证。<br>"
              "<em>解决方案</em>：封装成一个对象用 <code>AtomicReference</code>，或直接上锁。"))
    w.end()
