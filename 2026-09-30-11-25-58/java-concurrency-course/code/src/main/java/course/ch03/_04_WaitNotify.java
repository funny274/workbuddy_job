package course.ch03;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;

/**
 * ch03_04 wait / notify / notifyAll —— 手写生产者消费者
 *
 * 三个必须背下来的铁律：
 *   ① wait() 必须在 synchronized 块里调用，否则抛 IllegalMonitorStateException
 *   ② 必须用 while 而不是 if 判断条件（防「虚假唤醒」Spurious Wakeup）
 *   ③ 优先用 notifyAll() 而不是 notify()（notify 可能唤醒错对象导致信号丢失）
 *
 * 运行：java src/main/java/course/ch03/_04_WaitNotify.java
 */
public class _04_WaitNotify {

    static final int CAPACITY = 3;
    static final Queue<Integer> queue = new LinkedList<>();
    static volatile boolean finished = false;

    public static void main(String[] args) throws Exception {
        int producers = 2, consumers = 2, itemsPerProducer = 10;
        CountDownLatch done = new CountDownLatch(producers);
        long start = System.currentTimeMillis();

        for (int i = 0; i < producers; i++) {
            final int pid = i;
            new Thread(() -> {
                for (int j = 0; j < itemsPerProducer; j++) {
                    produce(pid * 100 + j);
                }
                done.countDown();
            }, "producer-" + i).start();
        }

        for (int i = 0; i < consumers; i++) {
            new Thread(() -> {
                while (!finished || !queue.isEmpty()) {
                    Integer v = consume();
                    if (v != null) {
                        // 模拟处理耗时
                        try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
            }, "consumer-" + i).start();
        }

        done.await();
        finished = true;
        Thread.sleep(500);
        System.out.println("\n全部完成，耗时 " + (System.currentTimeMillis() - start) + " ms");

        // 演示 ①：不在同步块里 wait → 抛异常
        try {
            new Object().wait();
        } catch (IllegalMonitorStateException e) {
            System.out.println("演示① 未持有锁就 wait → " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    static void produce(int value) {
        synchronized (queue) {
            while (queue.size() == CAPACITY) {          // 铁律②：用 while
                try {
                    System.out.println(Thread.currentThread().getName() + " 队列满，等待...");
                    queue.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            queue.offer(value);
            System.out.println(Thread.currentThread().getName() + " 生产 " + value + " 队列=" + queue);
            queue.notifyAll();                          // 铁律③：notifyAll
        }
    }

    static Integer consume() {
        synchronized (queue) {
            while (queue.isEmpty()) {
                if (finished) return null;
                try {
                    queue.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                if (queue.isEmpty() && finished) return null;
            }
            Integer v = queue.poll();
            queue.notifyAll();
            return v;
        }
    }
}
