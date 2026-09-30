package course.ch08;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ch08_03 线程池生产级实践：优雅关闭、监控、参数动态调整、异常处理
 *
 * 运行：java src/main/java/course/ch08/_01_PoolBestPractice.java
 */
public class _01_PoolBestPractice {

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) 三种关闭方式对比 =====");
        shutdownVsShutdownNow();

        System.out.println("\n===== 2) 优雅关闭模板（生产可用）=====");
        gracefulShutdown();

        System.out.println("\n===== 3) 任务异常的处理陷阱 =====");
        exceptionTrap();

        System.out.println("\n===== 4) 线程池监控指标 =====");
        monitor();

        System.out.println("""
            
            ============== 生产级线程池最佳实践 ==============
            【创建】禁止用 Executors 快捷方法：
              × Executors.newFixedThreadPool(n)   → 无界队列，OOM
              × Executors.newCachedThreadPool()   → max=Integer.MAX_VALUE，线程爆炸
              × Executors.newSingleThreadExecutor() → 无界队列，OOM
              √ new ThreadPoolExecutor(...) 手动指定七大参数
              （阿里 Java 开发手册强制要求，原因就是上面两个坑）

            【参数估算】
              CPU 密集：N + 1      （N = CPU 核数，+1 是为偶发缺页中断兜底）
              IO  密集：2N ~ 2N + 1（甚至更高，取决于阻塞系数 W/C）
                        精确公式：线程数 = N * (1 + WT/CT)
                        WT=等待时间, CT=计算时间
                        例：CT=50ms, WT=450ms, N=8 → 8*(1+9) = 80

            【队列选择】
              强烈建议 ArrayBlockingQueue 或有界 LinkedBlockingQueue
              容量估算：核心线程数 / 单个任务处理耗时 * 可容忍等待秒数

            【隔离】
              不同业务用不同线程池（避免相互拖垮）
              下游 RPC 调用、DB 查询、日志写盘 → 各自独立池
              这就是 Hystrix / Sentinel 线程池隔离的思想

            【动态调整】（面试加分项）
              setCorePoolSize / setMaximumPoolSize 支持运行时修改，
              配合配置中心（Nacos/Apollo）可实现不重启调参。

            【监控】（必做）
              核心指标：poolSize / activeCount / queueSize / completedTaskCount
                       + 拒绝次数（需自己包一层 handler 计数）
              → 接入 Micrometer / Prometheus，queueSize 持续增长要告警
            =================================================
            """);
    }

    static void shutdownVsShutdownNow() throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        for (int i = 0; i < 5; i++) {
            final int id = i;
            pool.execute(() -> {
                try { Thread.sleep(500); } catch (InterruptedException e) {
                    System.out.println("  任务" + id + " 被中断");
                    Thread.currentThread().interrupt();
                }
            });
        }
        Thread.sleep(100);
        System.out.println("  提交 5 个任务后，队列长度 = " + pool.getQueue().size());

        // shutdown()：不接受新任务，但把队列里的跑完
        pool.shutdown();
        System.out.println("  调用 shutdown() 后 isShutdown=" + pool.isShutdown()
                + "，队列还在跑，剩余 = " + pool.getQueue().size());
        boolean done = pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  等待完成 = " + done + "，isTerminated=" + pool.isTerminated());

        // 对比 shutdownNow
        ThreadPoolExecutor pool2 = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        for (int i = 0; i < 5; i++) pool2.execute(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread.sleep(50);
        java.util.List<Runnable> dropped = pool2.shutdownNow();   // 返回未执行的任务
        System.out.println("  shutdownNow() 返回未执行任务数 = " + dropped.size()
                + "（立即中断线程 + 清空队列）");
        pool2.awaitTermination(3, TimeUnit.SECONDS);
    }

    static void gracefulShutdown() throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                r -> { Thread t = new Thread(r, "biz-pool-" + System.nanoTime() % 1000); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy());

        for (int i = 0; i < 10; i++) pool.execute(() -> {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // ---------- 优雅关闭标准模板 ----------
        pool.shutdown();                                    // ① 拒绝新任务
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {   // ② 等待已有任务完成
                pool.shutdownNow();                          // ③ 超时则强制中断
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("线程池未能正常关闭");
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();                              // ④ 自己也中断时补充一次
            Thread.currentThread().interrupt();
        }
        System.out.println("  已完成 10 个任务，线程池优雅关闭成功");
    }

    static void exceptionTrap() throws Exception {
        System.out.println("  陷阱A：submit() 提交的任务异常被 Future 吞掉，不打印也不触发 handler");
        ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        Future<?> f = p1.submit(() -> { throw new RuntimeException("submit 里的异常"); });
        try {
            f.get();                       // 必须 get() 才能拿到异常
        } catch (ExecutionException e) {
            System.out.println("    → 通过 future.get() 拿到：" + e.getCause().getMessage());
        }
        p1.shutdown();

        System.out.println("  陷阱B：execute() 提交的任务异常会触发 UncaughtExceptionHandler");
        ThreadPoolExecutor p2 = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "exec-pool");
                    t.setUncaughtExceptionHandler((th, e) ->
                            System.out.println("    → UncaughtExceptionHandler 捕获：" + e.getMessage()));
                    return t;
                });
        p2.execute(() -> { throw new RuntimeException("execute 里的异常"); });
        Thread.sleep(200);
        p2.shutdown();

        System.out.println("  陷阱C：重写 afterExecute 统一兜底（推荐）");
        System.out.println("    → protected void afterExecute(Runnable r, Throwable t)");
        System.out.println("       如果是 FutureTask，需要 ((FutureTask)r).get() 才能取出异常");
    }

    static void monitor() throws Exception {
        java.util.concurrent.atomic.AtomicInteger rejected = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10),
                new ThreadFactory() {
                    final AtomicInteger seq = new AtomicInteger();
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "monitor-" + seq.incrementAndGet());
                    }
                },
                new ThreadPoolExecutor.AbortPolicy());

        // 故意提交超出容量的任务，演示拒绝策略 + 计数
        for (int i = 0; i < 25; i++) {
            try {
                pool.execute(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            } catch (RejectedExecutionException e) {
                rejected.incrementAndGet();
            }
        }
        System.out.println("  容量 = 8 线程 + 10 队列 = 18，实际提交 25 个 → 被拒绝 " + rejected.get() + " 个");
        Thread.sleep(50);
        printMetrics(pool, "运行中");
        Thread.sleep(500);
        printMetrics(pool, "执行后");
        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);
        System.out.println("""
            
            ---------- 自定义扩展点（继承 ThreadPoolExecutor）----------
            protected void beforeExecute(Thread t, Runnable r)  // 任务执行前（可埋点计时）
            protected void afterExecute(Runnable r, Throwable t)// 任务执行后（可统计耗时）
            protected void terminated()                          // 池终止时（可告警）
            典型用途：耗时分布统计、拒绝计数、TraceId 透传
            ----------------------------------------------------------
            """);
    }

    static void printMetrics(ThreadPoolExecutor p, String tag) {
        System.out.printf("  [%s] 池大小=%d 活跃=%d 队列=%d(剩余容量=%d) 最大曾达=%d 完成=%d 总任务=%d%n",
                tag, p.getPoolSize(), p.getActiveCount(), p.getQueue().size(),
                p.getQueue().remainingCapacity(), p.getLargestPoolSize(),
                p.getCompletedTaskCount(), p.getTaskCount());
    }
}
