package course.ch14;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * ch14_01 ForkJoinPool：分治 + 工作窃取
 *
 * 运行：java src/main/java/course/ch14/_01_ForkJoin.java
 */
public class _01_ForkJoin {

    public static void main(String[] args) throws Exception {
        System.out.println("CPU 核心数 = " + Runtime.getRuntime().availableProcessors());

        System.out.println("\n===== 1) 手写 RecursiveTask：并行归并排序 =====");
        int[] arr = new Random(42).ints(20_000_000, 0, 100_000).toArray();
        int[] copy = arr.clone();

        long s1 = System.currentTimeMillis();
        Arrays.sort(copy);
        long serialMs = System.currentTimeMillis() - s1;

        long s2 = System.currentTimeMillis();
        ForkJoinPool pool = new ForkJoinPool();
        MergeSortTask task = new MergeSortTask(arr, 0, arr.length, 10_000);
        pool.invoke(task);
        long parallelMs = System.currentTimeMillis() - s2;

        System.out.printf("  Arrays.sort（单线程）: %5d ms%n", serialMs);
        System.out.printf("  ForkJoin 并行归并    : %5d ms%n", parallelMs);

        System.out.println("\n===== 2) ForkJoinPool 任务提交方式 =====");
        ForkJoinPool fjp = new ForkJoinPool(4);
        // execute / submit / invoke 三种
        System.out.println("  invoke(task)      → 提交并阻塞等待结果（最常用）");
        System.out.println("  submit(task)      → 提交并返回 ForkJoinTask，不阻塞");
        System.out.println("  execute(task)     → 提交无返回值任务");
        System.out.println("  pool.invokeAll(c) → 批量提交 Collection<Callable>");

        // 并行流底层就是 ForkJoinPool.commonPool
        long s3 = System.currentTimeMillis();
        long sum = IntStream.rangeClosed(1, 50_000_000)
                .asLongStream()
                .parallel()
                .sum();
        System.out.printf("%n  并行流求和 = %d，耗时 %d ms%n", sum, System.currentTimeMillis() - s3);
        System.out.println("  → parallelStream 默认使用 ForkJoinPool.commonPool()");
        System.out.println("    commonPool 线程数 = CPU 核数 - 1，被全 JVM 共享");
        System.out.println("""
                【生产注意】可以自定义池提交：
                    try (ForkJoinPool custom = new ForkJoinPool(32)) {
                        long r = custom.submit(() ->
                            list.parallelStream().map(...).collect(...)).get();
                    }
                """);
        fjp.shutdown();
        pool.shutdown();

        System.out.println("""
            
            ============== ForkJoinPool 核心机制 ==============
            【1】分治（Divide and Conquer）
               把大任务 fork() 拆成小任务，递归到足够小就 compute() 直接算，
               然后 join() 合并结果。阈值（threshold）很关键：
                 太大 → 拆得不够，并行度低
                 太小 → 任务太碎，调度开销超过收益

            【2】工作窃取（Work-Stealing）—— 它的灵魂
               每个工作线程有自己的一条双端队列（Deque）：
                 - 自己的任务从队头取（LIFO，符合栈式递归的局部性）
                 - 空闲时从别人的队尾「偷」任务（FIFO，偷大任务粒度）
               优点：负载自动均衡，不会出现「一个线程忙死，其他闲着」

               为什么自己用 LIFO，偷别人用 FIFO？
                 因为递归拆分的任务里，越靠后拆出来的越「大」，
                 偷大的能一次偷到更多工作量，减少窃取次数（降低竞争）。

            【3】与 ThreadPoolExecutor 的区别
               ThreadPoolExecutor：共享一条任务队列，所有线程抢同一个队列头
                                   → 队列头是热点，需要加锁/CAS
               ForkJoinPool      ：每个线程一条队列，减少竞争，支持窃取

            【4】适用场景
               CPU 密集型的分治计算：排序、归并、树遍历、大数组聚合
               不适合：IO 密集（线程会被阻塞，无法窃取走阻塞）
                       任务粒度极细（调度开销大于收益）

            【5】ForkJoinTask 三个子类
               RecursiveAction ：无返回值
               RecursiveTask<V>：有返回值（本项目演示）
               CountedCompleter ：JDK8+，更灵活，CompletableFuture 底层用到
            ====================================================
            """);
    }

    /** 并行归并排序 */
    static class MergeSortTask extends RecursiveTask<int[]> {
        private final int[] arr;
        private final int lo, hi;
        private final int threshold;

        MergeSortTask(int[] arr, int lo, int hi, int threshold) {
            this.arr = arr; this.lo = lo; this.hi = hi; this.threshold = threshold;
        }

        @Override
        protected int[] compute() {
            if (hi - lo <= threshold) {
                // 足够小，直接串行排序（避免任务过碎）
                int[] sub = Arrays.copyOfRange(arr, lo, hi);
                Arrays.sort(sub);
                return sub;
            }
            int mid = (lo + hi) >>> 1;
            MergeSortTask left = new MergeSortTask(arr, lo, mid, threshold);
            MergeSortTask right = new MergeSortTask(arr, mid, hi, threshold);

            left.fork();                       // 异步提交左半部分
            int[] rightResult = right.compute(); // 当前线程算右半部分（不浪费）
            int[] leftResult = left.join();     // 等左半部分

            return merge(leftResult, rightResult);
        }

        private int[] merge(int[] a, int[] b) {
            int[] r = new int[a.length + b.length];
            int i = 0, j = 0, k = 0;
            while (i < a.length && j < b.length) r[k++] = a[i] <= b[j] ? a[i++] : b[j++];
            while (i < a.length) r[k++] = a[i++];
            while (j < b.length) r[k++] = b[j++];
            return r;
        }
    }
}
