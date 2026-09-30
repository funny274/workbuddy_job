package course.ch13;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ch13_01 无锁队列原理：从阻塞队列到 RingBuffer（Disruptor 核心思想）
 *
 * 运行：java src/main/java/course/ch13/_01_RingBuffer.java
 */
public class _01_RingBuffer {

    /**
     * 单生产者单消费者（SPSC）无锁环形队列
     * 核心思想：用两个序号 + volatile 代替锁，生产者和消费者完全独立推进
     */
    static class SpscRingBuffer<T> {
        private final Object[] buffer;
        private final int mask;                  // 容量必须是 2 的幂，mask = capacity - 1
        private volatile long head = 0;          // 消费者序号（下一个要读的位置）
        private volatile long tail = 0;          // 生产者序号（下一个要写的位置）

        SpscRingBuffer(int capacity) {
            if (Integer.bitCount(capacity) != 1) throw new IllegalArgumentException("必须 2 的幂");
            this.buffer = new Object[capacity];
            this.mask = capacity - 1;
        }

        /** 生产者：不需要锁，只需要保证不与消费者读同一格 */
        public boolean publish(T item) {
            long currentTail = tail;
            if (currentTail - head >= buffer.length) {
                return false;                    // 队列满
            }
            buffer[(int) (currentTail & mask)] = item;
            tail = currentTail + 1;              // volatile 写，立刻对消费者可见
            return true;
        }

        /** 消费者 */
        @SuppressWarnings("unchecked")
        public T consume() {
            long currentHead = head;
            if (currentHead >= tail) {
                return null;                     // 队列空
            }
            T item = (T) buffer[(int) (currentHead & mask)];
            head = currentHead + 1;
            return item;
        }

        public long size() { return tail - head; }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) 无锁环形队列功能验证 =====");
        SpscRingBuffer<Integer> rb = new SpscRingBuffer<>(16);
        for (int i = 0; i < 10; i++) rb.publish(i);
        System.out.println("  放入 10 个后 size = " + rb.size());
        StringBuilder sb = new StringBuilder();
        Integer v;
        while ((v = rb.consume()) != null) sb.append(v).append(" ");
        System.out.println("  依次取出: " + sb);
        System.out.println("  取出后 size = " + rb.size());

        System.out.println("\n===== 2) 性能对比：无锁 RingBuffer vs 阻塞队列 =====");
        int items = 10_000_000;
        long t1 = testBlockingQueue(items);
        long t2 = testRingBuffer(items);
        System.out.printf("  ArrayBlockingQueue : %5d ms%n", t1);
        System.out.printf("  无锁 RingBuffer    : %5d ms  (快 %.1f 倍)%n", t2, (double) t1 / t2);

        System.out.println("""
            
            ============== 为什么 RingBuffer 这么快？==============
            1) 数组预先分配，永不 GC（无对象创建 = 无 GC 停顿）
            2) 序号单调递增，用位运算 & (capacity-1) 代替取模 %
            3) 生产者只用 volatile long tail，消费者只用 volatile long head
               → 没有锁竞争，没有 CAS 失败重试
            4) 内存顺序友好：消费者和生产者访问不同的缓存行（配合缓存行填充效果更好）

            ---------- 与 BlockingQueue 的本质区别 ----------
            ArrayBlockingQueue：
              put/take 都要先 lock()，队列满/空时线程 park 挂起（涉及内核态切换）
              → 每次操作有锁开销 + 可能的上下文切换开销

            无锁 RingBuffer：
              用「序号差值」判断满/空，无需挂起线程
              生产者只管推，消费者只管取，各自独立推进
              → 但要自己处理「队列满怎么办」和「没有数据怎么办」（自旋或降级）

            ---------- Disruptor 的三大杀手锏 ----------
            ① RingBuffer + 序号栅栏（SequenceBarrier）
               支持多消费者、消费者组、依赖链（如 handlerA → handlerB）
            ② 消除伪共享
               每个 Sequence 都做了缓存行填充（64 字节对齐）
            ③ 批量处理（Batch EventProcessor）
               一次取一批，摊薄序号 CAS 的成本
            → 官方基准测试：比 ArrayBlockingQueue 快一个数量级
            ======================================================
            """);
    }

    static long testBlockingQueue(int items) throws Exception {
        BlockingQueue<Integer> q = new ArrayBlockingQueue<>(1024);
        CountDownLatch done = new CountDownLatch(1);
        AtomicLong sum = new AtomicLong();
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < items; i++) {
                    sum.addAndGet(q.take());
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            done.countDown();
        });
        consumer.start();
        long s = System.nanoTime();
        for (int i = 0; i < items; i++) q.put(i);
        done.await();
        return (System.nanoTime() - s) / 1_000_000;
    }

    static long testRingBuffer(int items) throws Exception {
        SpscRingBuffer<Integer> rb = new SpscRingBuffer<>(1024);
        CountDownLatch done = new CountDownLatch(1);
        AtomicLong sum = new AtomicLong();
        Thread consumer = new Thread(() -> {
            long got = 0;
            while (got < items) {
                Integer v = rb.consume();
                if (v != null) {
                    sum.addAndGet(v);
                    got++;
                }
            }
            done.countDown();
        });
        consumer.start();
        long s = System.nanoTime();
        for (int i = 0; i < items; i++) {
            while (!rb.publish(i)) {
                Thread.onSpinWait();      // 队列满则自旋等待
            }
        }
        done.await();
        return (System.nanoTime() - s) / 1_000_000;
    }
}
