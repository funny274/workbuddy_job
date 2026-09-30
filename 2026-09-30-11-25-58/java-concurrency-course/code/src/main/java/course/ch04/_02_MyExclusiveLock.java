package course.ch04;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * ch04_02 手写一把不可重入的独占锁 —— 理解独占模式
 *
 * 运行：java src/main/java/course/ch04/_02_MyExclusiveLock.java
 */
public class _02_MyExclusiveLock {

    /** 最简独占锁：state=0 空闲，state=1 已占用 */
    static class MyLock {
        private static class Sync extends AbstractQueuedSynchronizer {
            @Override
            protected boolean tryAcquire(int arg) {
                if (compareAndSetState(0, 1)) {          // 0→1 CAS 成功就拿到锁
                    setExclusiveOwnerThread(Thread.currentThread());
                    return true;
                }
                return false;
            }

            @Override
            protected boolean tryRelease(int arg) {
                if (getState() == 0) throw new IllegalMonitorStateException();
                setExclusiveOwnerThread(null);
                setState(0);                              // 释放：不需要 CAS，因为只有持有者能释放
                return true;
            }

            @Override
            protected boolean isHeldExclusively() {
                return getState() == 1;
            }
        }

        private final Sync sync = new Sync();

        public void lock()   { sync.acquire(1); }
        public boolean tryLock() { return sync.tryAcquire(1); }
        public void unlock() { sync.release(1); }
    }

    static int unsafe = 0;
    static int safe = 0;
    static final MyLock LOCK = new MyLock();

    public static void main(String[] args) throws Exception {
        int n = 10, times = 100_000;
        Thread[] ts = new Thread[n];
        for (int i = 0; i < n; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < times; j++) {
                    unsafe++;                   // 不加锁
                    LOCK.lock();                // 用我们手写的锁
                    try {
                        safe++;
                    } finally {
                        LOCK.unlock();
                    }
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();

        System.out.println("期望     : " + (n * times));
        System.out.println("不用锁   : " + unsafe + "   丢失 = " + (n * times - unsafe));
        System.out.println("手写AQS锁: " + safe + "   丢失 = " + (n * times - safe));

        // 演示不可重入的后果
        System.out.println("\n===== 不可重入的坑 =====");
        Thread t = new Thread(() -> {
            LOCK.lock();
            System.out.println("外层拿到锁");
            System.out.println("尝试再次 lock（不可重入）...");
            boolean acquired = LOCK.tryLock();
            System.out.println("第二次 tryLock 结果 = " + acquired + "  → 同一线程也拿不到，会死锁！");
            LOCK.unlock();
        });
        t.setDaemon(true);
        t.start();
        t.join(1000);
        if (t.isAlive()) {
            System.out.println("（线程已卡住，被强制结束）");
        }

        System.out.println("""
            
            ============ 为什么 ReentrantLock 能重入？============
            它的 tryAcquire 里：
              if (c == 0) { CAS 抢锁; setExclusiveOwnerThread(current); }
              else if (current == getExclusiveOwnerThread()) {
                  int nextc = c + arg;        // 关键：state 累加，记录重入次数
                  setState(nextc);
                  return true;
              }
            释放时 state 递减，减到 0 才真正释放。这就是「重入计数器」。
            ======================================================
            """);
    }
}
