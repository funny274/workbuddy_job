package course.ch07;

import java.util.concurrent.*;

/**
 * ch08_01 线程池七大参数与执行流程实测
 *
 * 运行：java src/main/java/course/ch07/_01_ThreadPoolParams.java
 */
public class _01_ThreadPoolParams {

    public static void main(String[] args) throws Exception {
        System.out.println("CPU 核心数 = " + Runtime.getRuntime().availableProcessors());

        System.out.println("\n===== 实验1：任务数在 corePoolSize 以内 → 只创建核心线程 =====");
        ThreadPoolExecutor p1 = new ThreadPoolExecutor(
                2, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(5),
                new NamedThreadFactory("pool-1"));
        for (int i = 0; i < 2; i++) p1.execute(sleepTask(200));
        Thread.sleep(100);
        report(p1, "2 个任务");

        System.out.println("\n===== 实验2：超过核心数 → 先入队列，队列满了才扩容 =====");
        ThreadPoolExecutor p2 = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2),
                new NamedThreadFactory("pool-2"));
        for (int i = 0; i < 2; i++) p2.execute(sleepTask(300));   // 占满核心线程
        Thread.sleep(50);
        for (int i = 0; i < 2; i++) p2.execute(sleepTask(300));   // 进队列
        Thread.sleep(50);
        report(p2, "核心2 + 队列2 后");
        p2.execute(sleepTask(300));                                // 队列满 → 创建第3个线程
        Thread.sleep(50);
        report(p2, "队列满后再提交1个");
        p2.execute(sleepTask(300));                                // 创建第4个线程
        Thread.sleep(50);
        report(p2, "再提交1个");

        Thread.sleep(2000);
        p1.shutdown(); p2.shutdown();

        System.out.println("""
            
            ========== 线程池执行流程（务必背下这个顺序）==========
            提交任务 →
              ① 当前线程数 < corePoolSize         → 直接 new 一个核心线程执行
              ② 核心线程已满 → 任务入 workQueue    → 排队等待
              ③ 队列已满 且 线程数 < maxPoolSize  → new 一个非核心线程
              ④ 队列满 且 线程数 = maxPoolSize    → 执行拒绝策略

            面试高频追问：为什么先入队列而不是先扩容？
              → 设计哲学：线程是稀缺资源。能用队列缓冲就缓冲，
                只有缓冲区也满了才证明「真的需要更多线程」。
              → 但也正因如此，用无界队列（LinkedBlockingQueue 默认容量 Integer.MAX_VALUE）
                会导致 maxPoolSize 完全失效、OOM 风险 —— 这是线上最常见的事故来源之一。

            ========== 七大参数 ==========
            | 参数            | 含义                       | 实践建议                      |
            |-----------------|----------------------------|-------------------------------|
            | corePoolSize    | 核心线程数（常驻）         | CPU密集=N+1，IO密集=2N+       |
            | maximumPoolSize | 最大线程数                 | 必须显式设置，别用无界队列    |
            | keepAliveTime   | 非核心线程空闲存活时间     | 默认 60s；allowCoreThreadTimeOut 可让核心也回收 |
            | unit            | 时间单位                   | -                             |
            | workQueue       | 任务队列                   | 有界！ArrayBlockingQueue 最稳  |
            | threadFactory   | 线程工厂（起名、守护、异常）| 必须自定义，方便排查          |
            | handler         | 拒绝策略                   | 按业务选，见下                |

            ========== 四种拒绝策略 ==========
            ① AbortPolicy（默认）    : 抛 RejectedExecutionException，业务需捕获处理
            ② CallerRunsPolicy       : 由提交任务的线程自己执行 → 天然的反压(backpressure)
            ③ DiscardPolicy          : 静默丢弃（危险！生产慎用）
            ④ DiscardOldestPolicy    : 丢掉队列里最老的，再尝试提交

            ================================================
            """);
    }

    static Runnable sleepTask(long ms) {
        return () -> {
            try { Thread.sleep(ms); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
    }

    static void report(ThreadPoolExecutor p, String stage) {
        System.out.printf("  [%s] 池大小=%d  核心=%d  活跃=%d  队列=%d  已完成=%d%n",
                stage, p.getPoolSize(), p.getCorePoolSize(), p.getActiveCount(),
                p.getQueue().size(), p.getCompletedTaskCount());
    }

    /** 自定义线程工厂：给线程起有意义的名字（排查问题必备） */
    static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger();
        NamedThreadFactory(String prefix) { this.prefix = prefix; }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.incrementAndGet());
            t.setUncaughtExceptionHandler((th, e) ->
                    System.err.println("线程 " + th.getName() + " 抛出异常: " + e));
            return t;
        }
    }
}
