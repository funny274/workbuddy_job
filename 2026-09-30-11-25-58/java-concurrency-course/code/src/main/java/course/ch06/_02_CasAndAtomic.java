package course.ch06;

import java.util.concurrent.atomic.*;
import java.util.function.LongBinaryOperator;

/**
 * ch07_01 CAS 原理与原子类全家桶
 *
 * 运行：java src/main/java/course/ch06/_02_CasAndAtomic.java
 */
public class _02_CasAndAtomic {

    // ABA 问题演示用
    static AtomicReference<String> abaLock = new AtomicReference<>("A");
    static AtomicStampedReference<String> stampedRef = new AtomicStampedReference<>("A", 0);

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) AtomicInteger：自旋 CAS 解决计数 =====");
        AtomicInteger ai = new AtomicInteger(0);
        Thread[] ts = new Thread[10];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < 100_000; j++) ai.incrementAndGet(); });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        System.out.println("  AtomicInteger 结果 = " + ai.get() + "，期望 1000000");

        System.out.println("\n===== 2) 三个核心方法语义（易错点）=====");
        AtomicInteger x = new AtomicInteger(10);
        System.out.println("  getAndIncrement() = " + x.getAndIncrement() + "，之后 x = " + x.get() + "  (返回旧值)");
        System.out.println("  incrementAndGet() = " + x.incrementAndGet() + "，之后 x = " + x.get() + "  (返回新值)");
        System.out.println("  getAndAdd(5)      = " + x.getAndAdd(5) + "，之后 x = " + x.get() + "  (返回旧值)");
        AtomicInteger y = new AtomicInteger(5);
        System.out.println("  compareAndSet(15, 99) 在 y=5 时 = " + y.compareAndSet(15, 99)
                + "  (期望值不匹配 → 失败，y 仍为 " + y.get() + ")");

        System.out.println("\n===== 3) 函数式更新 =====");
        AtomicInteger fx = new AtomicInteger(7);
        int nv = fx.updateAndGet(v -> v * v);                  // x = x^2
        System.out.println("  updateAndGet(v -> v*v) => " + nv);
        LongBinaryOperator op = (a, b) -> a > b ? a : b;
        AtomicLong al = new AtomicLong(3);
        System.out.println("  accumulateAndGet(10, max) => " + al.accumulateAndGet(10, op));

        System.out.println("\n===== 4) ABA 问题与解决方案 =====");
        // 经典 ABA 场景：线程1 想 CAS(A->B)，中途线程2 把 A 改成 B 又改回 A
        demoAba();

        System.out.println("\n===== 5) 字段更新器 vs 原子类 =====");
        // AtomicIntegerFieldUpdater：给已有对象的 volatile int 字段加原子能力，省内存
        AtomicIntegerFieldUpdater<Counter> updater =
                AtomicIntegerFieldUpdater.newUpdater(Counter.class, "count");
        Counter c = new Counter();
        Thread[] ts2 = new Thread[10];
        for (int i = 0; i < ts2.length; i++) {
            ts2[i] = new Thread(() -> { for (int j = 0; j < 100_000; j++) updater.incrementAndGet(c); });
            ts2[i].start();
        }
        for (Thread t : ts2) t.join();
        System.out.println("  AtomicIntegerFieldUpdater 结果 = " + c.count + "，期望 1000000");
        System.out.println("  适用：对象有上万个实例、不想每个都持有 AtomicInteger（省 16 字节/对象）");

        System.out.println("\n===== 6) 高并发下的累加器：LongAdder =====");
        compareAdders();

        System.out.println("""
            
            ================== CAS 核心机制 ==================
            什么是 CAS？Compare-And-Swap，一条 CPU 原子指令（x86 的 cmpxchg）：
              boolean cas(V, expect, update) {
                  if (*V == expect) { *V = update; return true; }
                  return false;
              }
            Java 里由 sun.misc.Unsafe.compareAndSwapInt 暴露出来，
            JDK9+ 用 VarHandle 替代（但底层一样）。

            ---------- CAS 的三大问题 ----------
            ① ABA 问题
               线程1 读到 A，准备改；线程2 把 A→B→A；线程1 CAS 成功，但中间态丢失。
               解决：AtomicStampedReference（加版本号）/ AtomicMarkableReference（加布尔）
               注意：只有当中间态「有业务含义」时才需要解决，单纯计数无所谓。

            ② 自旋开销
               高竞争时大量线程 CAS 失败 → 空转烧 CPU。
               解决：LongAdder 分段累加 / 限制并发度 / 退化为锁

            ③ 只能保证单个变量的原子性
               多个变量要原子 → 封装成对象用 AtomicReference，或直接上锁

            ---------- LongAdder vs AtomicLong ----------
            AtomicLong：所有线程 CAS 同一个 value，热点集中，高并发下失败率飙升。
            LongAdder ：一个 base + 一个 Cell[] 数组，不同线程散列到不同 Cell 上累加，
                        最后 sum() 把 base 和所有 Cell 加起来。
                        → 写性能随并发度近线性提升，代价是读到的是「近似值」。
            =================================================
            """);
    }

    static void demoAba() throws Exception {
        // 制造 ABA
        Thread t2 = new Thread(() -> {
            sleep(100);
            abaLock.compareAndSet("A", "B");     // A -> B
            abaLock.compareAndSet("B", "A");     // B -> A   （看起来没变）
            System.out.println("  [干扰线程] 已完成 A→B→A 的往返");
        });
        t2.start();
        sleep(50);
        // 线程1 读到了 A，但此刻线程2 还没开始
        String snapshot = abaLock.get();
        sleep(200);                              // 期间发生 ABA
        boolean casOk = abaLock.compareAndSet(snapshot, "C");
        System.out.println("  [主线程] CAS(" + snapshot + " → C) 结果 = " + casOk
                + " → ABA 问题：CAS 成功了，但我们不知道中间发生过变化");

        // 用 AtomicStampedReference 解决
        Thread t4 = new Thread(() -> {
            sleep(50);
            int[] stamp = new int[1];
            String cur = stampedRef.get(stamp);
            stampedRef.compareAndSet(cur, "B", stamp[0], stamp[0] + 1);
            cur = stampedRef.get(stamp);
            stampedRef.compareAndSet(cur, "A", stamp[0], stamp[0] + 1);
            System.out.println("  [干扰线程] 带版本号的 A→B→A 完成，版本号已到 " + stampedRef.getStamp());
        });
        t4.start();
        sleep(30);
        int[] myStamp = new int[1];
        String mySnap = stampedRef.get(myStamp);
        sleep(150);
        boolean stampedOk = stampedRef.compareAndSet(mySnap, "C", myStamp[0], myStamp[0] + 1);
        System.out.println("  [主线程] AtomicStampedReference CAS 结果 = " + stampedOk
                + " → 版本号不匹配，成功拦截 ABA");
    }

    static void compareAdders() throws Exception {
        int threads = 16, times = 1_000_000;

        AtomicLong atomicLong = new AtomicLong();
        LongAdder longAdder = new LongAdder();
        LongAccumulator accumulator = new LongAccumulator(Long::max, Long.MIN_VALUE);

        long t1 = time(() -> run(() -> { for (int j = 0; j < times; j++) atomicLong.incrementAndGet(); }, threads));
        long t2 = time(() -> run(() -> { for (int j = 0; j < times; j++) longAdder.increment(); }, threads));
        long t3 = time(() -> run(() -> {
            for (int j = 0; j < times; j++) {
                long v = System.nanoTime();
                accumulator.accumulate(v);
            }
        }, threads));

        System.out.printf("  AtomicLong   : %4d ms  (结果 %d)%n", t1, atomicLong.get());
        System.out.printf("  LongAdder    : %4d ms  (结果 %d)%n", t2, longAdder.sum());
        System.out.printf("  LongAccumulator(max): %4d ms%n", t3);
        System.out.println("  → 高并发下 LongAdder 通常比 AtomicLong 快 2~5 倍（分段减少 CAS 冲突）");
    }

    static long time(Runnable r) {
        long s = System.nanoTime();
        r.run();
        return (System.nanoTime() - s) / 1_000_000;
    }

    static void run(Runnable body, int threads) {
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(body);
            ts[i].start();
        }
        for (Thread t : ts) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static class Counter {
        public volatile int count;      // 必须是 volatile
    }
}
