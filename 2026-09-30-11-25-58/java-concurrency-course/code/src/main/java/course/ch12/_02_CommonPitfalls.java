package course.ch12;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ch12_02 并发编程十大经典陷阱（每一个都能在线上造成事故）
 *
 * 运行：java src/main/java/course/ch12/_02_CommonPitfalls.java
 */
public class _02_CommonPitfalls {

    public static void main(String[] args) throws Exception {
        System.out.println("===== 陷阱1：SimpleDateFormat 非线程安全 =====");
        pitfall1();

        System.out.println("\n===== 陷阱2：Integer 缓存导致的「锁失效」=====");
        pitfall2();

        System.out.println("\n===== 陷阱3：字符串常量池锁（不同模块互相阻塞）=====");
        pitfall3();

        System.out.println("\n===== 陷阱4：HashMap 并发 put 数据丢失 =====");
        pitfall4();

        System.out.println("\n===== 陷阱5：双重检查锁缺少 volatile =====");
        System.out.println("  → 已在 ch02_03 详细演示，此处略");

        System.out.println("\n===== 陷阱6：线程池用无界队列导致 OOM =====");
        System.out.println("""
              Executors.newFixedThreadPool(n) 内部是：
                  new LinkedBlockingQueue<Runnable>()   ← 默认容量 Integer.MAX_VALUE
              任务堆积速度 > 消费速度时，队列无限增长 → OutOfMemoryError: Java heap space
              同理 newCachedThreadPool() 的 maximumPoolSize = Integer.MAX_VALUE
              → 会创建几十万线程，直接 OOM: unable to create native thread
              【正确做法】new ThreadPoolExecutor(...) 全部参数显式指定
              """);

        System.out.println("===== 陷阱7：ThreadLocal 未清理导致内存泄漏 =====");
        System.out.println("  → 已在 ch10_01 详细演示，此处略");

        System.out.println("\n===== 陷阱8：父子线程池共用导致级联阻塞 =====");
        System.out.println("""
              场景：线程池 A（核心2）的任务里，又向线程池 A 提交子任务并等待结果。
                   2 个核心线程都在等子任务，但子任务排在队列里没人执行 → 死锁。
              【正确做法】父子任务用不同线程池；或改用 CompletableFuture 异步编排。
              """);

        System.out.println("===== 陷阱9：volatile 数组元素不保证可见性 =====");
        pitfall9();

        System.out.println("\n===== 陷阱10：interrupt 被吞掉 =====");
        pitfall10();

        System.out.println("""
            
            ================ 陷阱速查表 ================
            | # | 陷阱                        | 一句话解决                          |
            |---|-----------------------------|-------------------------------------|
            | 1 | SimpleDateFormat 非线程安全 | 用 DateTimeFormatter 或 ThreadLocal |
            | 2 | 小整数 Integer 缓存         | 别用 Integer 做锁对象，用 Object    |
            | 3 | 字符串常量池锁              | 别用 String 做锁，用 new Object()   |
            | 4 | HashMap 并发丢数据          | 用 ConcurrentHashMap                |
            | 5 | DCL 缺 volatile             | 加 volatile                         |
            | 6 | 无界队列 OOM                | 手动 new ThreadPoolExecutor + 有界队列|
            | 7 | ThreadLocal 泄漏            | finally 里 remove()                 |
            | 8 | 父子线程池死锁              | 分离线程池 / 异步编排               |
            | 9 | volatile 数组元素           | volatile 不修饰元素，用 Atomic*Array|
            |10 | interrupt 被吞              | 捕获后必须 Thread.currentThread().interrupt()|
            ============================================
            """);
    }

    static void pitfall1() throws Exception {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        int threads = 50;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int id = i;
            ts[i] = new Thread(() -> {
                try {
                    // 多个线程共用同一个 SimpleDateFormat → format 方法内部用 Calendar，非线程安全
                    String s = sdf.format(new java.util.Date(id * 1000L));
                    sdf.parse(s);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            ts[i].start();
        }
        latch.await();
        System.out.println("  50 个线程共用 SimpleDateFormat → 异常次数 = " + errors.get()
                + "（常见 NumberFormatException / 结果错乱）");
    }

    static void pitfall2() throws Exception {
        // Integer 缓存范围是 -128 ~ 127，超出范围每次 new 新对象
        Integer a = 100, b = 100;
        Integer c = 1000, d = 1000;
        System.out.println("  Integer.valueOf(100) == Integer.valueOf(100)  → " + (a == b)
                + "   （在缓存范围内，同一对象）");
        System.out.println("  Integer.valueOf(1000) == Integer.valueOf(1000) → " + (c == d)
                + "  （超出缓存范围，不同对象）");
        System.out.println("  → 如果用 Integer 当锁对象，值相同但对象不同 → 锁根本锁不住！");

        // 实测：用 Integer 做锁
        AtomicInteger counter = new AtomicInteger();
        Thread[] ts = new Thread[5];
        // 反例：每次循环都新建一个 Integer 对象当锁 → 锁形同虚设
        // （下面用显式 new 来避免编译器的「基于值的类」警告，同时更直观地暴露问题）
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 10000; j++) {
                    Integer lockObj = Integer.valueOf(1000);   // 每次都是新对象！
                    synchronized (lockObj) {
                        counter.incrementAndGet();
                    }
                }
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        System.out.println("  反例演示完成（5 线程 × 1万次），此时锁实际无效，仅靠 AtomicInteger 自保");
        System.out.println("  【结论】锁对象必须用 private final Object，绝不用 Integer/String");
    }

    static void pitfall3() throws Exception {
        // 两个不相关的模块都锁同一个字符串字面量 → 意外互斥甚至死锁
        Thread t1 = new Thread(() -> {
            synchronized ("common".intern()) {
                System.out.println("  模块A 拿到 \"common\" 的锁");
                sleep(300);
            }
        });
        Thread t2 = new Thread(() -> {
            sleep(50);
            System.out.println("  模块B 尝试拿同一把（字符串常量池）锁，被无辜阻塞...");
            long s = System.currentTimeMillis();
            synchronized ("common".intern()) {
                System.out.println("  模块B 等了 " + (System.currentTimeMillis() - s) + "ms 才拿到");
            }
        });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("  → 两个毫无关系的模块因为字符串常量池共用锁而互相干扰");
    }

    static void pitfall4() throws Exception {
        java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
        int threads = 8, ops = 100_000;
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int id = i;
            ts[i] = new Thread(() -> {
                for (int j = 0; j < ops; j++) {
                    map.put(id * ops + j, j);
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        int expected = threads * ops;
        System.out.println("  HashMap 期望 size = " + expected + "，实际 = " + map.size()
                + "，丢失 = " + (expected - map.size()));
        System.out.println("  → 并发 put 会互相覆盖（JDK7 还会形成环形链表死循环）");
    }

    static void pitfall9() throws Exception {
        System.out.println("""
              volatile int[] arr = new int[10];
              arr[0] = 1;      // ← 这行没有 volatile 语义！
              volatile 只保证「arr 这个引用」的可见性，
              不保证「arr 指向的数组元素」的可见性。
              【正确做法】
                AtomicIntegerArray / AtomicLongArray
                或 volatile int[] + 配合 Unsafe/VarHandle
              """);
    }

    static void pitfall10() throws Exception {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // 错误示范：吞掉中断，什么都不做 → 上层完全感知不到
                System.out.println("  子线程被中断，但异常被吞掉了（错误示范）");
            }
        });
        t.start();
        Thread.sleep(100);
        t.interrupt();
        t.join();

        System.out.println("  【正确做法】");
        System.out.println("""
              try { Thread.sleep(1000); }
              catch (InterruptedException e) {
                  Thread.currentThread().interrupt();   // 恢复中断标志
                  // 可选：做清理工作后 return / throw
                  return;
              }
              为什么必须恢复？
                ① 中断是「协作式」的，上层可能也在 await 中断标志
                ② 吞掉中断会让「优雅停机」「超时取消」功能全部失效
              """);
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
