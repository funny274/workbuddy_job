package course.ch11;

/**
 * ch11_01 happens-before 八大规则 + 指令重排序实测
 *
 * 运行：java src/main/java/course/ch11/_01_HappensBefore.java
 */
public class _01_HappensBefore {

    static int a = 0, b = 0, x = 0, y = 0;
    static volatile boolean flagA = false, flagB = false;

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) 重排序实测：不留神就出现的 (0,0) 结果 =====");
        System.out.println("  （x86 是强内存模型 + JIT 需要预热，这个实验需要跑很多轮才有机会复现）");
        int zeroCount = 0, total = 2_000_000;
        // 用 CountDownLatch 让两个线程尽可能同时起跑，增加重排复现概率
        for (int i = 0; i < total; i++) {
            a = b = x = y = 0;
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            Thread t1 = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                a = 1;              // ①
                x = b;              // ②  这两行可能被重排
            });
            Thread t2 = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                b = 1;              // ③
                y = a;              // ④
            });
            t1.start(); t2.start();
            start.countDown();
            t1.join(); t2.join();
            if (x == 0 && y == 0) zeroCount++;
        }
        System.out.println("  出现 (x=0, y=0) 的次数 = " + zeroCount + " / " + total);
        System.out.println("""
              → 按顺序一致性推导，(x,y) 只可能是 (1,0)、(0,1)、(1,1)，绝不该出现 (0,0)。
                如果上面数字 > 0，说明 ①② 或 ③④ 之间发生了重排序（StoreLoad 重排）。
                在 x86 上复现率很低（TSO 强内存模型），在 ARM 上会明显得多 ——
                这正是为什么「我本机跑没问题」的并发 bug 到了 ARM 服务器就炸。
                可以加 -XX:-UseCompressedOops 之类参数、或改用 volatile 验证差异。
              """);
        System.out.println("  对比：给 a/b 都加上 volatile 后（禁止重排序），(0,0) 将彻底消失。");

        System.out.println("===== 2) happens-before 八大规则（背下来）=====");
        System.out.println("""
            | # | 规则名称                | 说明                                                    |
            |---|-------------------------|---------------------------------------------------------|
            | 1 | 程序顺序规则            | 同一线程内，前面的操作 hb 后面的操作                     |
            | 2 | 监视器锁规则            | 对同一把锁的 unlock hb 后续的 lock                       |
            | 3 | volatile 变量规则       | 对 volatile 变量的写 hb 后续的读                         |
            | 4 | 线程启动规则            | Thread.start() hb 该线程内的所有操作                     |
            | 5 | 线程终止规则            | 线程内所有操作 hb 其他线程检测到该线程终止（join 返回） |
            | 6 | 线程中断规则            | interrupt() hb 被中断线程检测到中断                      |
            | 7 | 对象终结规则            | 构造器结束 hb finalize() 开始                            |
            | 8 | 传递性                  | A hb B，B hb C  ⟹  A hb C                               |

            ---------- 怎么用这条规则解题？ ----------
            题目：下面代码 i 的值是多少？
                int i = 0;
                new Thread(() -> { i = 5; }).start();   // 线程A
                System.out.println(i);                  // 主线程

            分析：主线程打印时，线程A 可能还没执行（无 hb 关系），i 可能是 0 也可能是 5。
            修正：加 join() → 规则5生效，保证 i = 5。
                 或用 volatile / AtomicInteger。

            ---------- 关键理解 ----------
            happens-before 不是「代码执行顺序」，而是「内存可见性保证」：
            如果 A hb B，则 A 的所有内存写入对 B 都可见，且 A 不会被重排到 B 之后。

            ---------- as-if-serial 语义 ----------
            单线程内，不管怎么重排序，都不能改变程序的执行结果。
            但多线程之间没有这个保证 —— 这就是并发 bug 的根源。
            ============================================================
            """);

        System.out.println("===== 3) volatile 的 happens-before 效果验证 =====");
        // volatile 写之后的东西，对 volatile 读之后的代码可见
        VolatileDemo demo = new VolatileDemo();
        Thread writer = new Thread(demo::write);
        Thread reader = new Thread(demo::read);
        writer.start();
        Thread.sleep(100);
        reader.start();
        writer.join(); reader.join();

        System.out.println("\n===== 4) 内存屏障（Memory Barrier）=====");
        System.out.println("""
            volatile 的可见性和有序性靠「内存屏障」实现：

            | 屏障类型          | 作用                                    |
            |-------------------|-----------------------------------------|
            | LoadLoad          | 禁止后面的读被重排到前面（读读有序）    |
            | StoreStore        | 禁止后面的写被重排到前面（写写有序）    |
            | LoadStore         | 禁止后面的写被重排到前面（读写有序）    |
            | StoreLoad（最贵） | 禁止后面的读被重排到前面（写读有序）    |

            volatile 写：在前面插 StoreStore，在后面插 StoreLoad
            volatile 读：在前面插 LoadLoad，在后面插 LoadStore

            ---------- 硬件层面的对应 ----------
            x86 是强内存模型，只有 StoreLoad 需要真的插入指令：
                lock addl $0x0,(%rsp)     ← 这条空操作就是 MFENCE 的效果
            ARM/POWER 是弱内存模型，需要更多屏障指令。

            ---------- 顺带说说伪共享（False Sharing）----------
            CPU 缓存行是 64 字节。两个变量如果在同一个缓存行：
                class Padded { volatile long a; volatile long b; }
            线程1 改 a、线程2 改 b，虽然逻辑上无关，
            但会因为缓存行失效（Cache Line Invalid）互相干扰 → 性能暴跌。
            解决：用 @sun.misc.Contended 注解，或者在字段前后填充 7 个 long。
            （JDK 的 LongAdder.Cell、RingBuffer 都用了这个技巧）
            ===================================================
            """);

        System.out.println("\n===== 5) 缓存行填充实测 =====");
        cacheLineDemo();
    }

    static class VolatileDemo {
        int plainData = 0;                  // 普通变量
        volatile boolean ready = false;     // volatile 标志

        void write() {
            plainData = 42;                 // ① 普通写
            ready = true;                   // ② volatile 写 —— 屏障：①一定先于②对读方可见
        }

        void read() {
            if (ready) {                    // ③ volatile 读
                System.out.println("  读到 ready=true，plainData = " + plainData
                        + " → 一定是 42，不可能是 0");
            }
        }
    }

    // ---------- 伪共享对比 ----------
    static class Shared {
        volatile long value = 0;
    }
    // 每个变量占满一个缓存行（64 字节 = 7 个 long 填充 + 数据）
    static class Padded {
        volatile long p1, p2, p3, p4, p5, p6, p7;
        volatile long value = 0;
    }

    static void cacheLineDemo() throws Exception {
        int threads = 4;
        long iterations = 50_000_000L;

        Shared[] shared = new Shared[threads];
        for (int i = 0; i < threads; i++) shared[i] = new Shared();
        long t1 = timeShared(shared, threads, iterations);

        Padded[] padded = new Padded[threads];
        for (int i = 0; i < threads; i++) padded[i] = new Padded();
        long t2 = timePadded(padded, threads, iterations);

        System.out.printf("  相邻变量（伪共享）: %5d ms%n", t1);
        System.out.printf("  缓存行填充隔离    : %5d ms%n", t2);
        System.out.printf("  → 提速 %.1f 倍%n", (double) t1 / t2);

        // 查看对象实际大小，验证填充是否生效
        System.out.println("  （用 JOL 可验证：Shared 约 24 字节，Padded 约 80+ 字节）");
    }

    static long timeShared(Shared[] arr, int threads, long iters) throws Exception {
        Thread[] ts = new Thread[threads];
        long s = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            final Shared target = arr[i];
            ts[i] = new Thread(() -> {
                for (long j = 0; j < iters; j++) target.value++;
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        return (System.nanoTime() - s) / 1_000_000;
    }

    static long timePadded(Padded[] arr, int threads, long iters) throws Exception {
        Thread[] ts = new Thread[threads];
        long s = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            final Padded target = arr[i];
            ts[i] = new Thread(() -> {
                for (long j = 0; j < iters; j++) target.value++;
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        return (System.nanoTime() - s) / 1_000_000;
    }
}
