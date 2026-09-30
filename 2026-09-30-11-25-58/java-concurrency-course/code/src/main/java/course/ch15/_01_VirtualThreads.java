package course.ch15;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ch15_01 虚拟线程（Virtual Threads）—— JDK 21 正式特性，JDK 25 已是主流
 *
 * 这是 Java 并发近十年最大的变革：Go 的 goroutine 终于有了 Java 版。
 *
 * 运行：java src/main/java/course/ch15/_01_VirtualThreads.java
 */
public class _01_VirtualThreads {

    public static void main(String[] args) throws Exception {
        System.out.println("JDK 版本 = " + Runtime.version());
        System.out.println("CPU 核心数 = " + Runtime.getRuntime().availableProcessors());

        System.out.println("\n===== 1) 三种创建方式 =====");
        // ① 直接启动单线程
        Thread vt1 = Thread.ofVirtual().name("vt-1").start(() ->
                System.out.println("  ① Thread.ofVirtual().start() → " + Thread.currentThread()));
        vt1.join();

        // ② startVirtualThread（最简）
        Thread vt2 = Thread.startVirtualThread(() ->
                System.out.println("  ② Thread.startVirtualThread() → isVirtual="
                        + Thread.currentThread().isVirtual()));
        vt2.join();

        // ③ ExecutorService（生产推荐）
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> f = exec.submit(() -> "  ③ newVirtualThreadPerTaskExecutor 提交的任务");
            System.out.println(f.get());
        }

        System.out.println("\n===== 2) 关键：虚拟线程 vs 平台线程 =====");
        System.out.println("""
            | 维度         | 平台线程(Platform)        | 虚拟线程(Virtual)                  |
            |--------------|---------------------------|------------------------------------|
            | 实现         | 1:1 映射 OS 线程          | M:N 映射（大量虚拟线程→少量载体线程）|
            | 创建成本     | 约 1MB 栈，创建慢         | 约几百字节~几KB，创建极快          |
            | 数量上限     | 几千个（受内存限制）      | 百万级（取决于堆内存）             |
            | 调度         | 操作系统内核调度          | JVM 调度（ForkJoinPool 作为载体）  |
            | 阻塞代价     | 线程挂起，切换成本高      | 自动 unmount，几乎零成本           |
            | 适用         | CPU 密集                  | IO 密集（高并发网络请求）          |
            """);

        System.out.println("===== 3) 数量对比实测 =====");
        int count = 100_000;

        // 平台线程（用池模拟，直接创建 10 万会 OOM）
        long s1 = System.currentTimeMillis();
        AtomicInteger platformDone = new AtomicInteger();
        ExecutorService platformPool = Executors.newFixedThreadPool(500);
        CountDownLatch l1 = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            platformPool.submit(() -> {
                try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                platformDone.incrementAndGet();
                l1.countDown();
            });
        }
        l1.await();
        long platformMs = System.currentTimeMillis() - s1;
        platformPool.shutdown();

        // 虚拟线程
        long s2 = System.currentTimeMillis();
        AtomicInteger virtualDone = new AtomicInteger();
        CountDownLatch l2 = new CountDownLatch(count);
        try (ExecutorService vtExec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                vtExec.submit(() -> {
                    try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    virtualDone.incrementAndGet();
                    l2.countDown();
                });
            }
            l2.await();
        }
        long virtualMs = System.currentTimeMillis() - s2;

        System.out.printf("  %d 个任务（每个 sleep 10ms）%n", count);
        System.out.printf("  平台线程池(500线程)  : %5d ms%n", platformMs);
        System.out.printf("  虚拟线程(每任务一线程): %5d ms%n", virtualMs);
        System.out.printf("  → 提速 %.1f 倍%n", (double) platformMs / virtualMs);
        System.out.println("  注意：换成线程池=10万 处理同样的 IO 阻塞，才是真正的「多少线程都不够」场景");

        System.out.println("""
            
            ============== 虚拟线程必须知道的坑 ==============
            【坑1】synchronized 会导致载体线程被 pin 住（JDK21）
               synchronized 块里发生阻塞 → 虚拟线程无法 unmount，
               载体线程被占用 → 严重时整个调度器饥饿。
               JDK24 起已修复（JEP 491），但 JDK21 项目必须注意：
               → 用 ReentrantLock 替代 synchronized
            """);
        testPinning();

        System.out.println("""
            【坑2】ThreadLocal 变得危险
               百万虚拟线程 × 每个 ThreadLocal 一份 → 内存爆炸。
               → 改用 ScopedValue（JDK21 预览 / JDK25 稳定）做不可变上下文传递
               → 或确保及时 remove

            【坑3】不要池化虚拟线程！
               虚拟线程极轻量，「池化」反而损失了它的价值。
               正确用法：Executors.newVirtualThreadPerTaskExecutor()，
                        一个任务一个虚拟线程，用完即弃。
               → 注意：这个 Executor 内部并不是真的「池」，只是复用调度器。

            【坑4】CPU 密集型任务用虚拟线程没有收益
               载体线程数默认 = CPU 核数，真正的计算任务还是排队。
               → CPU 密集继续用平台线程池

            【坑5】结构化并发（Structured Concurrency，JDK25 正式）
               StructuredTaskScope 让「子任务的生命周期严格嵌套在父任务内」，
               避免线程泄漏，异常传播更清晰。这是配套虚拟线程的最佳实践。
            ==================================================
            """);

        System.out.println("===== 4) StructuredTaskScope 示例 =====");
        structuredDemo();
    }

    static void testPinning() throws Exception {
        // 对比：synchronized vs ReentrantLock 在虚拟线程下的表现
        Object monitor = new Object();
        ReentrantLock lock = new ReentrantLock();
        int tasks = 2000;

        long t1 = runVt(tasks, () -> {
            synchronized (monitor) {
                sleep(1);        // 模拟阻塞，会 pin 载体线程
            }
        });
        long t2 = runVt(tasks, () -> {
            lock.lock();
            try { sleep(1); } finally { lock.unlock(); }
        });
        System.out.printf("  synchronized 块内阻塞   : %5d ms%n", t1);
        System.out.printf("  ReentrantLock 块内阻塞  : %5d ms%n", t2);
        System.out.println("""
              说明：JDK 24 起 JEP 491 已让 synchronized 块内的阻塞也能正常 unmount，
                    所以在 JDK24+ 上两者耗时接近。
                    如果你的项目还在 JDK21（大量公司的现状），这个差异会非常明显，
                    排查命令：-Djdk.tracePinnedThreads=full  （会打印被 pin 的栈）
            """);
    }

    static long runVt(int tasks, Runnable body) throws Exception {
        CountDownLatch latch = new CountDownLatch(tasks);
        long s = System.nanoTime();
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                exec.submit(() -> {
                    try { body.run(); } finally { latch.countDown(); }
                });
            }
            latch.await();
        }
        return (System.nanoTime() - s) / 1_000_000;
    }

    static void structuredDemo() throws Exception {
        // JDK21+ 结构化并发：所有子任务在 scope 内，退出时自动等待/取消
        System.out.println("""
              try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                  Supplier<String> user  = scope.fork(() -> fetchUser());
                  Supplier<String> order = scope.fork(() -> fetchOrder());
                  scope.join();                    // 等所有子任务
                  scope.throwIfFailed();           // 任一失败就抛异常
                  return new Result(user.get(), order.get());
              }
              // 出 try 块时：所有子任务保证已结束（成功或取消）→ 无线程泄漏
            """);
        System.out.println("  核心价值：把「并发任务的作用域」变成代码块级的结构化约束，");
        System.out.println("           像 try-with-resources 一样管理线程生命周期。");
    }

    static String fetchUser() { sleep(50); return "user"; }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
