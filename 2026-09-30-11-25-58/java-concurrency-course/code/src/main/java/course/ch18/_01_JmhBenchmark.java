package course.ch18;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * ch18_01 JMH 基准测试 —— 并发性能优化的唯一可靠手段
 *
 * 为什么不能手写 System.nanoTime() 测性能？
 *   ① JIT 预热不充分：前几万次执行是解释模式，比稳态慢几十倍
 *   ② 死代码消除：JIT 发现结果没被使用，直接把整个循环删掉
 *   ③ 常量折叠：编译期就能算出的结果直接替换成常量
 *   ④ 循环展开、分支预测、CPU 频率调节都会干扰结果
 *   JMH 通过预热轮次、Blackhole、@Fork 等机制系统性地解决这些问题。
 *
 * 运行（Maven 工程）：
 *   mvn clean package
 *   java -jar target/jmh-benchmarks.jar
 *   或只跑某个类：
 *   java -jar target/jmh-benchmarks.jar ".*AdderBenchmark.*"
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class _01_JmhBenchmark {

    // ============================================================
    // 实验一：AtomicLong vs LongAdder 高并发累加
    // ============================================================
    @Benchmark
    @Threads(16)
    public long atomicLong(AtomicState s) {
        return s.atomicLong.incrementAndGet();
    }

    @Benchmark
    @Threads(16)
    public long longAdder(AdderState s) {
        s.longAdder.increment();
        return s.longAdder.sum();
    }

    // ============================================================
    // 实验二：不同锁策略的计数器
    // ============================================================
    @Benchmark
    @Threads(16)
    public long synchronizedCounter(SyncState s) {
        synchronized (s.lock) { s.value++; }
        return s.value;
    }

    @Benchmark
    @Threads(16)
    public long lockCounter(LockState s) {
        s.lock.lock();
        try { s.value++; } finally { s.lock.unlock(); }
        return s.value;
    }

    // ============================================================
    // 实验三：伪共享的影响
    // ============================================================
    @Benchmark
    @Threads(4)
    public void falseSharing(FalseSharingState s) {
        for (int i = 0; i < 100; i++) {
            s.unpadded[s.threadIndex()].value++;
        }
    }

    @Benchmark
    @Threads(4)
    public void padding(PaddedState s) {
        for (int i = 0; i < 100; i++) {
            s.padded[s.threadIndex()].value++;
        }
    }

    @State(Scope.Benchmark)
    public static class AtomicState {
        final AtomicLong atomicLong = new AtomicLong();
    }

    @State(Scope.Benchmark)
    public static class AdderState {
        final LongAdder longAdder = new LongAdder();
    }

    @State(Scope.Benchmark)
    public static class SyncState {
        final Object lock = new Object();
        long value;
    }

    @State(Scope.Benchmark)
    public static class LockState {
        final java.util.concurrent.locks.ReentrantLock lock =
                new java.util.concurrent.locks.ReentrantLock();
        long value;
    }

    @State(Scope.Benchmark)
    public static class FalseSharingState {
        final Shared[] unpadded = new Shared[8];
        {
            for (int i = 0; i < unpadded.length; i++) unpadded[i] = new Shared();
        }
        int threadIndex() {
            return (int) (Thread.currentThread().threadId() % unpadded.length);
        }
    }

    @State(Scope.Benchmark)
    public static class PaddedState {
        final PaddedCell[] padded = new PaddedCell[8];
        {
            for (int i = 0; i < padded.length; i++) padded[i] = new PaddedCell();
        }
        int threadIndex() {
            return (int) (Thread.currentThread().threadId() % padded.length);
        }
    }

    public static class Shared { volatile long value; }

    /** 缓存行填充：64 字节 = 8 个 long */
    public static class PaddedCell {
        volatile long p1, p2, p3, p4, p5, p6, p7;
        volatile long value;
    }

    // ============================================================
    public static void main(String[] args) throws Exception {
        printGuide();

        // 说明：默认不自动运行 JMH（耗时长），取消注释即可执行
        // runJmh();

        System.out.println("""
            要真正运行基准测试，请执行：
              mvn clean package
              java -jar target/jmh-benchmarks.jar
            （完整跑一轮约需 3~5 分钟，建议睡前跑）
            """);
    }

    static void runJmh() throws Exception {
        Options opt = new OptionsBuilder()
                .include(_01_JmhBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }

    static void printGuide() {
        System.out.println("""
            ╔══════════════════════════════════════════════════════════════════╗
            ║           JMH 基准测试 —— 并发性能优化的唯一可靠手段              ║
            ╚══════════════════════════════════════════════════════════════════╝

            ┌──────────────────────────────────────────────────────────────────┐
            │ 为什么不能手写 System.nanoTime() 测性能？                         │
            └──────────────────────────────────────────────────────────────────┘
            ① JIT 预热不充分
               前几万次执行走解释器，比 JIT 编译后的机器码慢几十倍。
               不加预热就测，测的是「解释执行速度」而不是「生产速度」。

            ② 死代码消除（Dead Code Elimination）
               如果计算结果没被使用，JIT 会把整个循环直接删掉。
               → 这就是为什么 JMH 要求方法有返回值并配合 Blackhole。

            ③ 常量折叠（Constant Folding）
               编译期能算出来的，直接替换成常量。

            ④ 循环展开优化
               空循环体会被 JIT 直接优化掉，导致测出「零耗时」。

            ⑤ CPU 频率调节 / 线程调度干扰
               单次测量噪声太大，必须多次迭代取统计值。

            ┌──────────────────────────────────────────────────────────────────┐
            │ JMH 的核心注解                                                    │
            └──────────────────────────────────────────────────────────────────┘
            | 注解                  | 作用                                        |
            |-----------------------|---------------------------------------------|
            | @Benchmark            | 标记被测方法                                |
            | @BenchmarkMode        | 测试模式：Throughput/AverageTime/SampleTime |
            | @Warmup               | 预热轮次（不计入结果）                      |
            | @Measurement          | 正式测量轮次                                |
            | @Fork                 | 每个测试开几个独立 JVM 进程（隔离干扰）     |
            | @State                | 定义状态对象及其作用域                      |
            | @Scope                | Benchmark/Thread/Group                      |
            | @Threads              | 并发线程数                                  |
            | @Setup / @TearDown    | 生命周期钩子（Level = Trial/Iteration/Invocation）|
            | @Param                | 参数化测试                                  |
            | @CompilerControl      | 控制 JIT 行为（如禁止内联、禁止消除）       |

            ┌──────────────────────────────────────────────────────────────────┐
            │ 怎么看结果？                                                      │
            └──────────────────────────────────────────────────────────────────┘
            Throughput 模式（ops/ns）→ 越大越好，适合比较吞吐量
            AverageTime 模式（ns/op）→ 越小越好，适合看单次延迟
            每个结果后面会有：
              ± 标准差：波动范围，太大说明测试不稳定
              (min, avg, max)：分布情况，看 max 判断有没有长尾
              GC 次数：如果 GC 很频繁，说明分配压力大

            ┌──────────────────────────────────────────────────────────────────┐
            │ 并发性能优化的方法论（面试可讲）                                  │
            └──────────────────────────────────────────────────────────────────┘
            第 0 步：先测量，不要猜！没有 profiling 的优化是玄学。
                   工具：JMH（微基准）、async-profiler（火焰图）、
                         arthas（线上诊断）、JFR（JVM 内置）
            第 1 步：找瓶颈 —— 是 CPU 打满？还是锁竞争？还是 GC？
            第 2 步：按性价比排序优化手段：
                    ├─ 消除共享（最重要）→ 线程本地化、无状态设计
                    ├─ 减少临界区 → 缩小锁范围、读写分离
                    ├─ 降低锁竞争 → 分段锁、CAS、无锁数据结构
                    ├─ 减少内存分配 → 对象池、预分配、避免装箱
                    └─ 最后才考虑换锁类型 / 调 JVM 参数
            第 3 步：每次只改一个变量，改完重新测量，确认真的有提升。
            第 4 步：警惕「过早优化」—— 先保证正确，再保证可读，最后才优化。

            ---------- 一个真实的优化案例（面试可以讲）----------
            现象：订单查询接口 P99 从 50ms 涨到 800ms
            定位：
              ① arthas 的 trace 命令发现 80% 时间在获取本地缓存锁
              ② 代码用 synchronized 修饰整个方法，包括序列化操作
            优化：
              ① 缩小锁范围，只锁真正的共享结构修改
              ② 改用 ConcurrentHashMap（原先用 HashMap + synchronized）
              ③ 热点数据加 Caffeine 本地缓存
            结果：P99 降到 60ms，QPS 从 2000 提升到 8000
            ====================================================
            """);
    }
}
