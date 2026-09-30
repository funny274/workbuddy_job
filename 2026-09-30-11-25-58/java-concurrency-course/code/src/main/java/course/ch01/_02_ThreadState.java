package course.ch01;

/**
 * ch01_02 线程的 6 种状态流转（实测）
 *
 * 运行：java src/main/java/course/ch01/_02_ThreadState.java
 */
public class _02_ThreadState {

    public static void main(String[] args) throws Exception {
        // 1) NEW：创建了但没 start
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);     // TIMED_WAITING
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "demo");
        System.out.println("1. 刚 new 出来        : " + t.getState());

        // 2) RUNNABLE
        t.start();
        Thread.sleep(50);
        System.out.println("2. start 之后         : " + t.getState());

        // 3) TIMED_WAITING（sleep / wait(超时) / join(超时) / parkNanos）
        Thread.sleep(100);
        System.out.println("3. 在 sleep 中        : " + t.getState());

        // 4) WAITING（wait() / join() / park()）
        Object lock = new Object();
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                try {
                    lock.wait();          // 释放锁并进入 WAITING
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "waiter");
        waiter.start();
        Thread.sleep(100);
        System.out.println("4. 在 wait 中         : " + waiter.getState());

        synchronized (lock) {
            lock.notifyAll();             // 唤醒 → BLOCKED / RUNNABLE
        }
        waiter.join();                    // 主线程 WAITING

        System.out.println("5. 阻塞在 sync 上     : " + blockedStateDemo());

        t.join();
        System.out.println("6. 执行完毕           : " + t.getState());
        System.out.println("   已终止线程再 start : ");
        try {
            t.start();                    // 只能 start 一次
        } catch (IllegalThreadStateException e) {
            System.out.println("   → 抛 IllegalThreadStateException：" + e.getClass().getSimpleName());
        }
    }

    /** 构造一个 BLOCKED 状态的线程并返回其状态 */
    private static String blockedStateDemo() throws InterruptedException {
        Object lock = new Object();
        Thread holder = new Thread(() -> {
            synchronized (lock) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        holder.start();
        Thread.sleep(50);

        Thread blocked = new Thread(() -> {
            synchronized (lock) { /* 抢不到锁，停在 BLOCKED */ }
        }, "blocked");
        blocked.start();
        Thread.sleep(100);
        String s = blocked.getState().toString();
        blocked.join();
        holder.join();
        return s;
    }
}
