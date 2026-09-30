package course.ch03;

import org.openjdk.jol.info.ClassLayout;

/**
 * ch03_03 锁升级完整过程实测：无锁 → 偏向锁 → 轻量级锁 → 重量级锁
 *
 * 关键参数（JDK 15+ 默认已关闭偏向锁，需手动开启才能看到偏向锁）：
 *   -XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0
 *
 * 运行：
 *   java -XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0 \
 *        -cp "target/classes;target/lib/*" course.ch03._03_LockEscalation
 */
public class _03_LockEscalation {

    static Object[] objs = new Object[4];

    public static void main(String[] args) throws Exception {
        System.out.println("JVM 参数 UseBiasedLocking = "
                + java.lang.management.ManagementFactory.getRuntimeMXBean()
                      .getInputArguments());

        // ---------- 阶段 0：无锁 ----------
        Object lock = new Object();
        System.out.println("\n===== 阶段 0：刚创建，无锁 =====");
        print(lock);

        // ---------- 阶段 1：偏向锁 ----------
        // 只有一个线程反复进入同步块 → 偏向该线程，后续再进无需 CAS
        System.out.println("===== 阶段 1：单线程反复加锁 → 偏向锁 =====");
        for (int i = 0; i < 5; i++) {
            synchronized (lock) { /* 同一线程反复进入 */ }
        }
        print(lock);
        System.out.println("说明：若 Mark Word 最后 3 位是 101，说明已偏向当前线程；");
        System.out.println("      若是 001，说明本 JVM 没开偏向锁（JDK15+ 默认关闭，属正常）。");

        // ---------- 阶段 2：轻量级锁 ----------
        // 出现第二个线程竞争 → 撤销偏向，升级为轻量级锁（CAS 自旋）
        System.out.println("\n===== 阶段 2：两个线程交替竞争 → 轻量级锁 =====");
        final Object l2 = new Object();
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) synchronized (l2) { }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) synchronized (l2) { }
        });
        t1.start(); t2.start();
        t1.join(); t2.join();
        print(l2);
        System.out.println("说明：偏向被撤销后，竞争采用 CAS + 自旋，Mark Word 指向线程栈上的 Lock Record。");

        // ---------- 阶段 3：重量级锁 ----------
        // 自旋失败 / 竞争激烈 → 升级为重量级锁，挂起线程进入 monitor 等待队列
        System.out.println("\n===== 阶段 3：激烈竞争（线程数 > CPU 核数）→ 重量级锁 =====");
        final Object l3 = new Object();
        Thread[] hot = new Thread[Runtime.getRuntime().availableProcessors() * 4];
        for (int i = 0; i < hot.length; i++) {
            final int id = i;
            hot[i] = new Thread(() -> {
                for (int j = 0; j < 500_000; j++) {
                    synchronized (l3) {
                        Blackhole.consume(id + j);
                    }
                }
            });
        }
        long start = System.nanoTime();
        for (Thread t : hot) t.start();
        for (Thread t : hot) t.join();
        System.out.printf("全部完成，耗时 %d ms%n", (System.nanoTime() - start) / 1_000_000);
        print(l3);

        System.out.println("""
            ==================== 锁升级全景 ====================
            | 阶段     | 触发条件                    | 实现方式              | 开销        |
            |----------|-----------------------------|-----------------------|-------------|
            | 无锁     | 无竞争                      | -                     | 无          |
            | 偏向锁   | 只有一个线程反复进入        | Mark Word 记录线程ID  | 近乎零      |
            | 轻量级锁 | 少量线程交替、竞争时间极短  | CAS + 自旋            | 自旋耗 CPU  |
            | 重量级锁 | 竞争激烈 / 自旋超阈值       | 操作系统 Mutex（内核态）| 线程挂起/唤醒 |
            ----------------------------------------------------
            注意两点：
            ① 锁升级是单向的，不能降级（只能随 GC 重置为无锁）。
            ② 偏向锁在 JDK15 已被默认禁用（JEP 374），JDK18 相关代码被移除。
               原因是：现代应用大量使用线程池，偏向撤销的开销超过了收益。
            ====================================================
            """);
    }

    static void print(Object o) {
        String s = ClassLayout.parseInstance(o).toPrintable();
        for (String line : s.split("\n")) {
            if (line.contains("mark") || line.contains("class") || line.contains("Instance size")) {
                System.out.println("   " + line.trim());
            }
        }
    }

    static class Blackhole {
        static volatile long sink;
        static void consume(long v) { sink = v; }
    }
}
