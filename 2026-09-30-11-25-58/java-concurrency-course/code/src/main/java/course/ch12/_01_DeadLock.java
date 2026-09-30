package course.ch12;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ch12_01 死锁：四种成因 + 自动检测 + 在线排查
 *
 * 运行：java src/main/java/course/ch12/_01_DeadLock.java
 */
public class _01_DeadLock {

    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) 复现死锁 =====");
        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                System.out.println("  线程1 持有 A，等待 B...");
                sleep(200);
                synchronized (LOCK_B) {
                    System.out.println("  线程1 拿到 B");
                }
            }
        }, "thread-1");
        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                System.out.println("  线程2 持有 B，等待 A...");
                sleep(200);
                synchronized (LOCK_A) {
                    System.out.println("  线程2 拿到 A");
                }
            }
        }, "thread-2");
        t1.start(); t2.start();
        t1.join(1500); t2.join(1500);

        if (t1.isAlive() && t2.isAlive()) {
            System.out.println("  >>> 死锁形成！两个线程互相等待，永远无法推进");

            System.out.println("\n===== 2) 用 ThreadMXBean 自动检测死锁 =====");
            ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
            long[] deadlocked = mxBean.findDeadlockedThreads();
            if (deadlocked != null) {
                System.out.println("  检测到 " + deadlocked.length + " 个死锁线程：");
                for (ThreadInfo info : mxBean.getThreadInfo(deadlocked, true, true)) {
                    System.out.println("    ── 线程: " + info.getThreadName());
                    System.out.println("       状态: " + info.getThreadState());
                    System.out.println("       阻塞在: " + info.getLockName());
                    System.out.println("       被谁持有: " + info.getLockOwnerName());
                    StackTraceElement[] st = info.getStackTrace();
                    if (st.length > 0) System.out.println("       栈顶: " + st[0]);
                }
            } else {
                System.out.println("  未检测到死锁");
            }
        }

        System.out.println("""
            
            ============ 死锁的四个必要条件（缺一不可）============
            ① 互斥：资源同一时刻只能被一个线程占用
            ② 持有并等待：持有资源的同时等待其他资源
            ③ 不可抢占：不能强行夺走别人的资源
            ④ 循环等待：形成 A→B→A 的环形等待链
            打破任意一个即可解除死锁 —— 实践中最容易打破的是③和④。

            ============ 实战中常见的五种死锁场景 ============
            1) 简单互锁：两个线程以不同顺序获取两把锁（本项目演示）
               解决：全局定义锁顺序，所有地方都按同一顺序获取

            2) 嵌套 synchronized 方法调用
               解决：明确锁边界，避免在持锁时调用外部代码（可能回调回来）

            3) 数据库锁等待（最隐蔽）
               事务1：update A; update B;
               事务2：update B; update A;
               → 数据库层死锁，MySQL 会自动检测并回滚一个（Deadlock found）
               解决：统一 SQL 执行顺序；缩短事务；加索引避免间隙锁扩大

            4) 线程池任务互相依赖
               线程池只有 1 个线程，任务1 提交任务2 并等待结果 → 永久阻塞
               解决：禁止在池内任务中同步等待同一池的任务

            5) 对象锁 + 不同类加载器的类锁
               分布式/热部署环境下同一类的不同 Class 对象 → 伪共享锁
               解决：用统一的锁对象（如 Redis 分布式锁）

            ============ 在线排查四板斧 ============
            ① jps -l                    → 找到 Java 进程 PID
            ② jstack <pid>              → 打印线程栈，JVM 会直接告诉你 "Found one Java-level deadlock"
            ③ jconsole / jvisualvm      → 图形化，点「检测死锁」按钮
            ④ arthas（阿里开源，最推荐）：
                 thread -b                → 直接找出阻塞其他线程的元凶
                 thread -n 3              → CPU 占用前 3 的线程栈
                 thread --state BLOCKED   → 所有 BLOCKED 线程

            ---------- jstack 输出中的关键字 ----------
            "Found one Java-level deadlock:"  → 有死锁
            "waiting to lock"                 → 想拿但没拿到的锁
            "locked ownable synchronizers"     → 已持有的锁
            java.lang.Thread.State: BLOCKED   → 阻塞状态
            ======================================================
            """);

        // 强制结束
        t1.interrupt(); t2.interrupt();
        System.out.println("\n===== 3) 死锁的预防：用 tryLock 打破循环等待 =====");
        preventByTryLock();
    }

    static final ReentrantLock RA = new ReentrantLock();
    static final ReentrantLock RB = new ReentrantLock();

    static void preventByTryLock() throws Exception {
        Thread t1 = new Thread(() -> transfer(RA, RB, "线程1"));
        Thread t2 = new Thread(() -> transfer(RB, RA, "线程2"));
        t1.start(); t2.start();
        t1.join(3000); t2.join(3000);
        System.out.println("  两个线程都能结束（tryLock 超时后释放已持有的锁，打破循环等待）");
    }

    static void transfer(ReentrantLock first, ReentrantLock second, String name) {
        boolean gotFirst = false, gotSecond = false;
        try {
            gotFirst = first.tryLock(200, TimeUnit.MILLISECONDS);
            if (gotFirst) {
                Thread.sleep(100);      // 模拟业务耗时，制造竞争窗口
                gotSecond = second.tryLock(200, TimeUnit.MILLISECONDS);
                if (gotSecond) {
                    System.out.println("  " + name + " 成功拿到两把锁");
                } else {
                    System.out.println("  " + name + " 拿不到第二把锁，主动放弃（避免死锁）");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (gotSecond) second.unlock();
            if (gotFirst) first.unlock();
        }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
