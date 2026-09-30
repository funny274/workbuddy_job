package course.ch05;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ch05_01 ReentrantLock 全 API 实战 + 与 synchronized 对比
 *
 * 运行：java src/main/java/course/ch05/_01_ReentrantLockApi.java
 */
public class _01_ReentrantLockApi {

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) 公平锁 vs 非公平锁 =====");
        fairVsUnfair();

        System.out.println("\n===== 2) tryLock 超时：避免死锁的利器 =====");
        tryLockTimeout();

        System.out.println("\n===== 3) lockInterruptibly：可中断等待 =====");
        lockInterruptibly();

        System.out.println("""
            
            ========== ReentrantLock vs synchronized 选型 ==========
            | 维度         | synchronized         | ReentrantLock            |
            |--------------|----------------------|--------------------------|
            | 实现层       | JVM 内置（monitor）  | JDK 层（AQS）            |
            | 释放         | 自动（异常也释放）   | 手动 finally unlock      |
            | 公平性       | 非公平               | 可公平可非公平           |
            | 超时         | 不支持               | tryLock(timeout)         |
            | 可中断       | 不支持               | lockInterruptibly        |
            | 条件队列     | 1 个（wait/notify）  | 多个 Condition           |
            | 性能         | 优化后差距很小       | 高竞争下略优             |
            选型原则：能用 synchronized 就用 synchronized（简洁不易错）；
                     需要超时/中断/多条件/公平性时，才换 ReentrantLock。
            ========================================================
            """);
    }

    /** 公平 vs 非公平：公平锁按 FIFO 排队，吞吐略低但更可控 */
    static void fairVsUnfair() throws Exception {
        for (boolean fair : new boolean[]{false, true}) {
            Lock lock = new ReentrantLock(fair);
            StringBuilder order = new StringBuilder();
            Thread[] ts = new Thread[5];
            for (int i = 0; i < ts.length; i++) {
                final int id = i;
                ts[i] = new Thread(() -> {
                    lock.lock();
                    try {
                        order.append(id);
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        lock.unlock();
                    }
                });
                ts[i].start();
                Thread.sleep(2);        // 让线程按 0,1,2,3,4 顺序启动
            }
            for (Thread t : ts) t.join();
            System.out.printf("  %s 锁获取顺序 = %s%n",
                    fair ? "公平  " : "非公平", order);
        }
        System.out.println("  → 公平锁严格 FIFO；非公平锁允许「插队」，吞吐更高");
    }

    /** tryLock 超时：抢不到就放弃，天然规避死锁 */
    static void tryLockTimeout() throws Exception {
        Lock lockA = new ReentrantLock();
        Lock lockB = new ReentrantLock();
        AtomicInteger success = new AtomicInteger();

        // 线程1：先 A 后 B
        Thread t1 = new Thread(() -> transfer(lockA, lockB, "线程1", success));
        // 线程2：先 B 后 A（经典死锁顺序）
        Thread t2 = new Thread(() -> transfer(lockB, lockA, "线程2", success));
        t1.start(); t2.start();
        t1.join(5000); t2.join(5000);
        System.out.println("  两个线程都成功结束了（靠 tryLock 超时打破了死锁）");
    }

    static void transfer(Lock first, Lock second, String name, AtomicInteger success) {
        try {
            if (first.tryLock(100, TimeUnit.MILLISECONDS)) {
                try {
                    Thread.sleep(50);
                    if (second.tryLock(100, TimeUnit.MILLISECONDS)) {
                        try {
                            success.incrementAndGet();
                            System.out.println("  " + name + " 同时拿到两把锁，成功");
                        } finally {
                            second.unlock();
                        }
                    } else {
                        System.out.println("  " + name + " 第二把锁超时，放弃并释放第一把（避免死锁）");
                    }
                } finally {
                    first.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** lockInterruptibly：等待锁的过程中可以被 interrupt 打断 */
    static void lockInterruptibly() throws Exception {
        Lock lock = new ReentrantLock();
        lock.lock();
        Thread waiter = new Thread(() -> {
            try {
                System.out.println("  子线程尝试可中断地获取锁...");
                lock.lockInterruptibly();       // 会一直等，但能被中断
                System.out.println("  拿到了");
            } catch (InterruptedException e) {
                System.out.println("  等待中被 interrupt 成功打断（这是 synchronized 做不到的）");
            }
        });
        waiter.start();
        Thread.sleep(300);
        waiter.interrupt();
        waiter.join();
        lock.unlock();
    }
}
