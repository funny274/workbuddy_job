package course.ch03;

/**
 * ch03_01 synchronized 的三种用法 + 锁对象到底是谁
 *
 * 运行：java src/main/java/course/ch03/_01_SynchronizedBasics.java
 */
public class _01_SynchronizedBasics {

    private final Object lock = new Object();
    private int count = 0;

    // 1) 修饰实例方法 → 锁是 this（当前实例）
    public synchronized void syncMethod() { count++; }

    // 2) 修饰静态方法 → 锁是 Class 对象（全局唯一）
    public static synchronized void staticSyncMethod() { /* ... */ }

    // 3) 修饰代码块 → 锁是你指定的任意对象
    public void blockSync() {
        synchronized (lock) {
            count++;
        }
    }

    public static void main(String[] args) throws Exception {
        _01_SynchronizedBasics demo = new _01_SynchronizedBasics();
        int threads = 20, times = 50_000;

        // 用不同锁对象验证：「锁的是对象，不是代码」
        System.out.println("=========== 实验：锁对象决定了互斥范围 ===========");
        CountC1 c1 = new CountC1();
        runParallel(() -> c1.incMethod(), threads, times);
        System.out.println("场景1 锁 this（同一实例）      : " + c1.n + " 期望 " + (threads * times));

        CountC2 c2a = new CountC2(), c2b = new CountC2();
        runParallel(() -> c2a.incMethod(), threads / 2, times);
        runParallel(() -> c2b.incMethod(), threads / 2, times);
        System.out.println("场景2 锁 this（两个不同实例）  : " + (c2a.n + c2b.n)
                + " 期望 " + (threads * times) + "  → 实例锁不能互斥");

        runParallel(() -> CountC3.incStatic(), threads, times);
        System.out.println("场景3 锁 Class（静态方法）     : " + CountC3.n + " 期望 " + (threads * times));

        System.out.println("\n=========== 两种加锁的等价性 ===========");
        System.out.println("""
            public synchronized void m() { ... }
            等价于
            public void m() { synchronized (this) { ... } }

            public static synchronized void m() { ... }
            等价于
            public void m() { synchronized (Xxx.class) { ... } }
            """);

        // 验证：synchronized 是可重入的
        System.out.println("=========== 可重入性验证 ===========");
        Reentrant demo2 = new Reentrant();
        demo2.outer();
        System.out.println("→ synchronized 可重入：同一线程可重复获取同一把锁（靠 monitor 的 owner + 计数器）");
    }

    static void runParallel(Runnable r, int threads, int times) throws InterruptedException {
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < times; j++) r.run(); });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
    }

    static class CountC1 { int n = 0; synchronized void incMethod() { n++; } }
    static class CountC2 { int n = 0; synchronized void incMethod() { n++; } }
    static class CountC3 { static int n = 0; static synchronized void incStatic() { n++; } }

    static class Reentrant {
        synchronized void outer() {
            System.out.println("outer 拿到锁");
            inner();                              // 同一把锁再拿一次，不会死锁
        }
        synchronized void inner() {
            System.out.println("inner 再次拿到同一把锁（重入成功）");
        }
    }
}
