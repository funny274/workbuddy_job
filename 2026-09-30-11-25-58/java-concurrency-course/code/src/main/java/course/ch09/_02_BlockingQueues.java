package course.ch09;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ch09_02 阻塞队列全家桶实战 + 手写生产者消费者三种实现
 *
 * 运行：java src/main/java/course/ch09/_02_BlockingQueues.java
 */
public class _02_BlockingQueues {

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) ArrayBlockingQueue：有界数组队列 =====");
        testQueue(new ArrayBlockingQueue<>(10), "ArrayBlockingQueue");

        System.out.println("\n===== 2) LinkedBlockingQueue：双锁分离，吞吐更高 =====");
        testQueue(new LinkedBlockingQueue<>(10), "LinkedBlockingQueue");

        System.out.println("\n===== 3) SynchronousQueue：容量 0，一手交钱一手交货 =====");
        // SynchronousQueue 没有容量，put 必须等到有人 take
        SynchronousQueue<String> sq = new SynchronousQueue<>();
        Thread consumer = new Thread(() -> {
            try {
                Thread.sleep(500);
                System.out.println("  消费者取到: " + sq.take());
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        consumer.start();
        System.out.println("  put 前时间: " + System.currentTimeMillis() % 100000);
        sq.put("握手数据");            // 会阻塞直到消费者 take
        System.out.println("  put 返回时间: " + System.currentTimeMillis() % 100000 + "（阻塞了约 500ms）");

        System.out.println("\n===== 4) DelayQueue：延时队列（订单超时未支付自动取消）=====");
        DelayQueue<Order> dq = new DelayQueue<>();
        dq.put(new Order("ORDER-001", 300));
        dq.put(new Order("ORDER-002", 800));
        dq.put(new Order("ORDER-003", 100));
        long s = System.currentTimeMillis();
        while (!dq.isEmpty()) {
            Order o = dq.take();       // 只有到期的才能被取出
            System.out.printf("  [%4dms] 处理超时订单 %s%n", System.currentTimeMillis() - s, o.id);
        }
        System.out.println("  → DelayQueue 按「剩余延时」排序，未到期取不到");

        System.out.println("\n===== 5) PriorityBlockingQueue：带优先级的无界队列 =====");
        PriorityBlockingQueue<Task2> pq = new PriorityBlockingQueue<>();
        pq.put(new Task2("低优先级任务", 3));
        pq.put(new Task2("高优先级任务", 1));
        pq.put(new Task2("中优先级任务", 2));
        while (!pq.isEmpty()) System.out.println("  出队: " + pq.take().name);

        System.out.println("""
            
            ============== 阻塞队列三大类方法 ==============
            | 操作 | 抛异常        | 返回特殊值   | 阻塞          | 超时退出                    |
            |------|---------------|--------------|---------------|------------------------------|
            | 插入 | add(e)        | offer(e)     | put(e)        | offer(e, time, unit)         |
            | 移除 | remove()      | poll()       | take()        | poll(time, unit)             |
            | 检查 | element()     | peek()       | -             | -                            |
            记忆法：add/remove 会抛异常；offer/poll 返回 false/null；put/take 会阻塞。

            ============== 有界 vs 无界 ==============
            有界队列 + 拒绝策略 = 天然的背压（backpressure）机制，
            这是保护下游服务不被压垮的第一道防线。
            无界队列 = 把 OOM 风险藏起来，是最危险的「看起来没问题」。

            ============== ArrayBlockingQueue vs LinkedBlockingQueue ==============
            ArrayBlockingQueue：
              一把 ReentrantLock + 两个 Condition（notEmpty/notFull）
              → put 和 take 不能并行（互斥同一把锁）
            LinkedBlockingQueue：
              两把锁：putLock 和 takeLock，各带一个 Condition
              → 生产和消费可以真正并行，吞吐更高
              代价：需要维护 count 的原子性（用 AtomicInteger）
            =============================================
            """);
    }

    static void testQueue(BlockingQueue<Integer> q, String name) throws Exception {
        AtomicInteger consumed = new AtomicInteger();
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) q.put(i);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread[] cs = new Thread[3];
        for (int i = 0; i < cs.length; i++) {
            cs[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 34; j++) {
                        Integer v = q.poll(500, TimeUnit.MILLISECONDS);
                        if (v != null) consumed.incrementAndGet();
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }
        for (Thread c : cs) c.start();
        producer.start();
        producer.join();
        for (Thread c : cs) c.join();
        System.out.println("  " + name + " 生产 100，消费 " + consumed.get());
    }

    /** DelayQueue 元素必须实现 Delayed */
    static class Order implements Delayed {
        final String id;
        final long expireAt;
        Order(String id, long delayMs) {
            this.id = id;
            this.expireAt = System.currentTimeMillis() + delayMs;
        }
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expireAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }
        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }
    }

    static class Task2 implements Comparable<Task2> {
        final String name;
        final int priority;
        Task2(String name, int priority) { this.name = name; this.priority = priority; }
        @Override
        public int compareTo(Task2 o) { return Integer.compare(priority, o.priority); }
    }
}
