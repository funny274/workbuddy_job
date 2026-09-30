package course.ch09;

import java.util.*;
import java.util.concurrent.*;

/**
 * ch09_01 并发容器全景 + 性能实测对比
 *
 * 运行：java src/main/java/course/ch09/_01_ConcurrentCollections.java
 */
public class _01_ConcurrentCollections {

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) 为什么 HashMap 并发下会死循环 / 数据丢失 =====");
        // 说明（不要真的跑，会卡死）
        System.out.println("""
            JDK7 的 HashMap 扩容用头插法，并发 transfer 会形成环形链表 → get 时死循环（CPU 100%）。
            JDK8 改成尾插法，解决了死循环，但仍有「数据覆盖」问题：
              两个线程同时 put 到同一个空桶，都会走 CAS/赋值，后写的覆盖先写的。
            结论：并发场景必须用 ConcurrentHashMap。
            """);

        System.out.println("===== 2) 常用并发容器职责划分 =====");
        System.out.println("""
            | 接口        | 并发实现                       | 特点/替换关系                    |
            |-------------|--------------------------------|----------------------------------|
            | Map         | ConcurrentHashMap              | 替代 Hashtable/synchronizedMap   |
            | List        | CopyOnWriteArrayList           | 读多写极少（配置、监听器列表）    |
            | Set         | ConcurrentHashMap.newKeySet()  | 底层就是 CHM                     |
            | Queue       | ConcurrentLinkedQueue          | 无界、CAS 无锁、高吞吐           |
            | BlockingQueue| ArrayBlockingQueue            | 有界数组、单锁双条件              |
            | BlockingQueue| LinkedBlockingQueue           | 有界/无界、双锁分离，吞吐更高     |
            | BlockingQueue| SynchronousQueue              | 容量0，直接移交（CachedThreadPool）|
            | BlockingQueue| PriorityBlockingQueue         | 无界优先队列                     |
            | BlockingQueue| DelayQueue                    | 延时队列（订单超时取消）          |
            | BlockingQueue| LinkedTransferQueue           | 更强的 SynchronousQueue          |
            | Deque       | ConcurrentLinkedDeque          | 无锁双端队列                     |
            | ~~Vector~~  | -                              | 全表锁，已淘汰                   |
            """);

        System.out.println("===== 3) Map 并发写性能实测（让线程写同一批 key 以制造真实冲突）=====");
        int threads = 8, ops = 200_000;
        int keySpace = 1000;    // 故意限制 key 空间 → 线程之间会命中同一个桶
        Map<Integer, Integer> syncMap = Collections.synchronizedMap(new HashMap<>());
        ConcurrentHashMap<Integer, Integer> chm = new ConcurrentHashMap<>();

        long t1 = time(() -> runParallel(threads, id -> {
            for (int i = 0; i < ops; i++) syncMap.put(i % keySpace, i);
        }));
        long t2 = time(() -> runParallel(threads, id -> {
            for (int i = 0; i < ops; i++) chm.put(i % keySpace, i);
        }));
        System.out.printf("  synchronizedMap      : %5d ms%n", t1);
        System.out.printf("  ConcurrentHashMap    : %5d ms  (快 %.1f 倍)%n", t2, (double) t1 / t2);
        System.out.println("  → synchronizedMap 是「一把大锁锁全表」，CHM 是「CAS + 桶级锁」");

        System.out.println("\n===== 4) CopyOnWriteArrayList 读性能优势 =====");
        List<Integer> cow = new CopyOnWriteArrayList<>();
        List<Integer> syncList = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 1000; i++) { cow.add(i); syncList.add(i); }

        long r1 = time(() -> runParallel(8, id -> {
            for (int i = 0; i < 1_000_000; i++) syncList.get(i % 1000);
        }));
        long r2 = time(() -> runParallel(8, id -> {
            for (int i = 0; i < 1_000_000; i++) cow.get(i % 1000);
        }));
        System.out.printf("  synchronizedList 读: %5d ms%n", r1);
        System.out.printf("  CopyOnWriteArrayList 读: %5d ms  (快 %.1f 倍)%n", r2, (double) r1 / r2);
        System.out.println("  → COW 读完全无锁（读的是快照），但写会复制整个数组，只适合读多写极少");

        System.out.println("\n===== 5) ConcurrentHashMap 常用 API 实战 =====");
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        // 原子复合操作（避免 check-then-act 竞态）
        map.put("a", 1);
        map.merge("a", 1, Integer::sum);                 // 原子累加，结果 2
        map.computeIfAbsent("b", k -> 100);              // 不存在才计算
        map.computeIfPresent("a", (k, v) -> v * 10);     // 存在才更新
        System.out.println("  merge + computeIfAbsent + computeIfPresent → " + map);

        // search / reduce / forEach（并发批量操作）
        int found = map.search(1, (k, v) -> v > 50 ? v : null);
        System.out.println("  search 第一个 >50 的值 = " + found);

        // 原子替换（CAS 语义）
        boolean replaced = map.replace("a", map.get("a"), 999);
        System.out.println("  replace(k, expect, new) = " + replaced + " → " + map);

        System.out.println("""
            
            ============== ConcurrentHashMap 关键设计 ==============
            【JDK7】Segment 分段锁：默认 16 段，每段一把 ReentrantLock。
                    并发度 = 段数，段数在初始化后不可变。

            【JDK8+】彻底重构：
              - 取消 Segment，直接「CAS + synchronized(桶头节点)」
              - 数组 + 链表 + 红黑树（链表长度 ≥8 且数组长度 ≥64 时树化）
              - size() 用 baseCount + CounterCell[] 分段计数（和 LongAdder 同思路）
              - 扩容支持多线程协同（helpTransfer）：每个线程负责一段迁移

            【为什么不用读写锁？】
              读写锁在「读多写少」时好，但 CHM 的场景是「读写混合、每个操作都很短」，
              桶级锁粒度更细，锁冲突概率远低于整表读写锁。

            【要点】
              ① key/value 都不能为 null（无法区分「不存在」和「值为 null」）
              ② size() 是「近似值」不是强一致（并发写入时）
              ③ 复合操作必须用 compute/merge/putIfAbsent，不能自己 get 再 put
              ④ 弱一致性迭代器：遍历时不会抛 ConcurrentModificationException，
                 但可能看不到最新修改，也可能看到部分修改
            =========================================================
            """);
    }

    interface Task { void run(int id); }

    static void runParallel(int threads, Task task) {
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int id = i;
            ts[i] = new Thread(() -> task.run(id));
            ts[i].start();
        }
        for (Thread t : ts) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    static long time(Runnable r) {
        long s = System.nanoTime();
        r.run();
        return (System.nanoTime() - s) / 1_000_000;
    }
}
