package course.ch05;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ch05_02 用 Condition 手写有界阻塞队列 —— 对比 wait/notify 的优雅之处
 *
 * 关键点：一把锁可以有多个条件队列，实现「生产者只唤醒消费者、消费者只唤醒生产者」，
 *         避免 wait/notify 那种「notifyAll 唤醒一大片却只有一半能用」的惊群问题。
 *
 * 运行：java src/main/java/course/ch05/_02_ConditionQueue.java
 */
public class _02_ConditionQueue {

    /** 完全模拟 ArrayBlockingQueue 的实现思路 */
    static class BoundedQueue<E> {
        private final Object[] items;
        private int putIndex, takeIndex, count;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull  = lock.newCondition();   // 生产者在此等待
        private final Condition notEmpty = lock.newCondition();   // 消费者在此等待

        BoundedQueue(int capacity) { items = new Object[capacity]; }

        public void put(E e) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (count == items.length) {
                    notFull.await();                     // 队列满，生产者挂起
                }
                items[putIndex] = e;
                if (++putIndex == items.length) putIndex = 0;   // 环形数组
                count++;
                notEmpty.signal();                       // 只叫醒一个消费者（精准通知）
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        public E take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (count == 0) {
                    notEmpty.await();                    // 队列空，消费者挂起
                }
                E e = (E) items[takeIndex];
                items[takeIndex] = null;                 // help GC
                if (++takeIndex == items.length) takeIndex = 0;
                count--;
                notFull.signal();                        // 只叫醒一个生产者
                return e;
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            lock.lock();
            try { return count; } finally { lock.unlock(); }
        }
    }

    public static void main(String[] args) throws Exception {
        BoundedQueue<Integer> q = new BoundedQueue<>(5);
        long start = System.currentTimeMillis();
        int total = 200;

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= total; i++) {
                    q.put(i);
                    if (i % 50 == 0) System.out.println("已生产 " + i + " 个");
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "producer");

        Thread[] consumers = new Thread[3];
        java.util.concurrent.atomic.AtomicInteger consumed = new java.util.concurrent.atomic.AtomicInteger();
        for (int i = 0; i < consumers.length; i++) {
            consumers[i] = new Thread(() -> {
                try {
                    while (true) {
                        Integer v = q.take();
                        if (v == 0) {         // 毒丸，用来优雅停止
                            q.put(0);         // 转交给下一个消费者
                            break;
                        }
                        consumed.incrementAndGet();
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "consumer-" + i);
        }

        for (Thread c : consumers) c.start();
        producer.start();
        producer.join();
        Thread.sleep(200);
        q.put(0);        // 投毒丸
        for (Thread c : consumers) c.join(2000);

        System.out.println("消费总数 = " + consumed.get() + "，期望 " + total
                + "，耗时 " + (System.currentTimeMillis() - start) + " ms");

        System.out.println("""
            
            ============ Condition vs wait/notify ============
            1) 一个 Lock 可 newCondition() 出多个条件队列：
               - notFull  → 队列满时生产者等待
               - notEmpty → 队列空时消费者等待
               各自独立，signal 精准唤醒目标角色，避免无效唤醒。

            2) 三种等待方法：
               await()          : 等价 wait()
               awaitUninterruptibly() : 不可中断
               awaitNanos(n) / await(time, unit) : 超时等待，返回剩余时间
               awaitUntil(deadline) : 等到绝对时间

            3) 底层实现：每个 Condition 内部维护一个单向等待队列（firstWaiter/lastWaiter），
               节点复用的是 AQS 的 Node，状态为 Node.CONDITION。
               signal 时把节点从条件队列转移到 AQS 的同步队列，等待被唤醒后重新抢锁。

            4) 注意：Condition 必须在 lock() 和 unlock() 之间使用，
               否则抛 IllegalMonitorStateException。
            ==================================================
            """);
    }
}
