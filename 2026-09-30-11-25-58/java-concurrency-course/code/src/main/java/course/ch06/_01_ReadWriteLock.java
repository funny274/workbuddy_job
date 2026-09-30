package course.ch06;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * ch06 读写锁三兄弟：ReentrantReadWriteLock / StampedLock / 乐观读
 *
 * 运行：java src/main/java/course/ch06/_01_ReadWriteLock.java
 */
public class _01_ReadWriteLock {

    /** 场景：读多写少的缓存 */
    static class RwCache<K, V> {
        private final Map<K, V> map = new HashMap<>();
        private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

        public V get(K k) {
            rw.readLock().lock();                // 读锁：多个读线程可并发
            try {
                System.out.println("  [" + Thread.currentThread().getName() + "] 获得读锁，当前读锁数="
                        + rw.getReadLockCount());
                sleep(100);
                return map.get(k);
            } finally {
                rw.readLock().unlock();
            }
        }

        public void put(K k, V v) {
            rw.writeLock().lock();               // 写锁：独占
            try {
                System.out.println("  [" + Thread.currentThread().getName() + "] 获得写锁（独占）");
                sleep(100);
                map.put(k, v);
            } finally {
                rw.writeLock().unlock();
            }
        }
    }

    /** StampedLock：乐观读，读性能最强 */
    static class StampedCache {
        private double x, y;
        private final StampedLock sl = new StampedLock();

        void move(double dx, double dy) {
            long stamp = sl.writeLock();         // 悲观写锁，返回版本号
            try { x += dx; y += dy; } finally { sl.unlockWrite(stamp); }
        }

        double distanceFromOrigin() {
            long stamp = sl.tryOptimisticRead();  // 乐观读：不加锁，只取版本号
            double cx = x, cy = y;                // 把字段拷到局部变量（可能读到不一致快照）
            if (!sl.validate(stamp)) {            // 校验：期间有没有被写过？
                stamp = sl.readLock();            // 有被写 → 退化成悲观读锁重来
                try { cx = x; cy = y; } finally { sl.unlockRead(stamp); }
            }
            return Math.sqrt(cx * cx + cy * cy);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) ReentrantReadWriteLock：读读共享 =====");
        RwCache<String, String> cache = new RwCache<>();
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            new Thread(() -> { cache.get("k"); latch.countDown(); }, "reader-" + i).start();
        }
        latch.await();
        System.out.println("  → 三个读线程同时拿到读锁，并发执行（总耗时约 100ms 而非 300ms）");

        System.out.println("\n===== 2) 读写互斥 =====");
        Thread writer = new Thread(() -> cache.put("k", "v"), "writer");
        Thread reader = new Thread(() -> cache.get("k"), "reader-x");
        writer.start(); reader.start();
        writer.join(); reader.join();
        System.out.println("  → 写锁与读锁、写锁与写锁均互斥");

        System.out.println("\n===== 3) StampedLock 乐观读 =====");
        StampedCache sc = new StampedCache();
        sc.move(3, 4);
        System.out.println("  乐观读得到距离 = " + sc.distanceFromOrigin() + "（期望 5.0）");
        Thread t = new Thread(() -> { for (int i = 0; i < 100; i++) sc.move(0.001, 0.001); });
        t.start();
        double d = sc.distanceFromOrigin();
        t.join();
        System.out.println("  并发写入下乐观读 = " + d + "（validate 失败会自动退化为悲观读）");

        System.out.println("""
            
            ============ 三种锁对比 ============
            | 锁                       | 特性                                     | 适用场景          |
            |--------------------------|------------------------------------------|-------------------|
            | ReentrantReadWriteLock   | 可重入、支持公平/非公平、锁降级支持      | 通用读多写少      |
            | StampedLock              | 不可重入、支持乐观读、性能最好           | 大量读+极少写     |
            | 乐观读(tryOptimisticRead)| 不加锁只校验版本，零开销                 | 读操作耗时极短时  |

            ---------- ReentrantReadWriteLock 的关键限制 ----------
            ① 读锁不支持条件队列：readLock().newCondition() 直接抛 UnsupportedOperationException
               （原因：Condition 的等待会释放全部锁，而读锁是可共享的，语义上无法界定）
            ② 支持锁降级：持有写锁 → 获取读锁 → 释放写锁（保证可见性）
               不支持锁升级：持有读锁 → 获取写锁（会死锁）
            ③ 实现原理：用一个 32 位 int 的前 16 位存读锁计数，后 16 位存写锁计数，
               这样 CAS 一次就能原子地同时更新两种状态。

            ---------- StampedLock 三大坑 ----------
            ① 不可重入：同一线程重复加锁会死锁
            ② 不支持中断：没有 lockInterruptibly
            ③ 必须成对 release，且 stamp 不能混用（FIFO 队列语义）
            ====================================
            """);
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
