package course.ch04;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * ch04_01 手写一个 AQS 同步器 —— 这是理解 AQS 最快的路径
 *
 * 目标：实现一个「同一时刻最多允许 N 个线程通过」的共享锁（信号量语义）。
 * 通过这个例子你会彻底搞懂：
 *   - state 的语义由子类自己定义
 *   - tryAcquireShared / tryReleaseShared 的返回值含义
 *   - 为什么 AQS 把「排队」这件事帮你做好了
 *
 * 运行：java src/main/java/course/ch04/_01_MySharedLock.java
 */
public class _01_MySharedLock {

    /** 允许 N 个线程同时进入的共享锁 */
    static class MySharedLock {
        private final Sync sync;

        MySharedLock(int permits) {
            this.sync = new Sync(permits);
        }

        private static class Sync extends AbstractQueuedSynchronizer {
            Sync(int permits) {
                setState(permits);          // state 表示「剩余可用许可数」
            }

            /** 共享模式获取：返回值 >=0 表示成功，负数表示失败 */
            @Override
            protected int tryAcquireShared(int acquires) {
                for (;;) {
                    int available = getState();
                    int remaining = available - acquires;
                    if (remaining < 0) {
                        return remaining;                     // 许可不够，去排队
                    }
                    if (compareAndSetState(available, remaining)) {
                        return remaining;                     // CAS 成功，拿到许可
                    }
                    // CAS 失败 → for(;;) 自旋重试
                }
            }

            /** 共享模式释放：返回 true 表示可能唤醒后继节点 */
            @Override
            protected boolean tryReleaseShared(int releases) {
                for (;;) {
                    int current = getState();
                    int next = current + releases;
                    if (next < current) throw new Error("Maximum permit count exceeded");
                    if (compareAndSetState(current, next)) {
                        return true;
                    }
                }
            }

            /** 给外面的 lock() / unlock() 用的门面 */
            void acquireShared1() { acquireShared(1); }
            void releaseShared1() { releaseShared(1); }
            int available() { return getState(); }
        }

        public void lock()   { sync.acquireShared1(); }
        public void unlock() { sync.releaseShared1(); }
        public int available() { return sync.available(); }
    }

    static final MySharedLock LOCK = new MySharedLock(3);   // 最多 3 个并发

    public static void main(String[] args) throws Exception {
        System.out.println("初始许可数 = " + LOCK.available() + "，启动 10 个线程抢 3 个许可\n");
        Thread[] ts = new Thread[10];
        for (int i = 0; i < ts.length; i++) {
            final int id = i;
            ts[i] = new Thread(() -> {
                LOCK.lock();
                try {
                    System.out.printf("线程%-2d 拿到许可，剩余=%d，当前并发中...%n",
                            id, LOCK.available());
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    LOCK.unlock();
                    System.out.printf("线程%-2d 释放许可，剩余=%d%n", id, LOCK.available());
                }
            }, "t" + i);
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();

        System.out.println("""
            ================== AQS 设计精髓 ==================
            AQS 把「同步器」拆成两半：
              ① 通用骨架（AQS 自己实现，你复用）：
                 - volatile int state + CLH 双向队列
                 - 获取失败的线程如何入队、阻塞（LockSupport.park）
                 - 释放时如何唤醒后继（unparkSuccessor）
              ② 业务规则（子类必须实现，你定制）：
                 - tryAcquire / tryRelease        （独占模式）
                 - tryAcquireShared / tryReleaseShared（共享模式）
                 - isHeldExclusively               （是否被独占）

            模板方法模式：AQS 定义流程，你只填空。
            所有返回值语义（务必记牢）：
              tryAcquire          : true=成功, false=失败
              tryRelease          : true=完全释放, false=还没释放干净
              tryAcquireShared    : >=0 成功（返回剩余量）, <0 失败
              tryReleaseShared    : true=可以唤醒后继
            ==================================================
            """);
    }
}
