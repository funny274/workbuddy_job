package course.ch07;

import java.util.concurrent.*;

/**
 * ch08_02 手写一个极简线程池 —— 理解 ThreadPoolExecutor 内部到底在做什么
 *
 * 运行：java src/main/java/course/ch07/_02_MyThreadPool.java
 */
public class _02_MyThreadPool {

    /** 极简线程池：固定线程数 + 有界队列 + 拒绝策略 */
    static class SimplePool implements AutoCloseable {
        private final BlockingQueue<Runnable> queue;
        private final Worker[] workers;
        private volatile boolean running = true;
        private final RejectPolicy rejectPolicy;

        enum RejectPolicy { ABORT, CALLER_RUNS, DISCARD }

        SimplePool(int poolSize, int queueCapacity, RejectPolicy policy) {
            this.queue = new ArrayBlockingQueue<>(queueCapacity);
            this.rejectPolicy = policy;
            this.workers = new Worker[poolSize];
            for (int i = 0; i < poolSize; i++) {
                workers[i] = new Worker("simple-pool-" + i);
                workers[i].start();          // 核心线程：启动即阻塞在 take() 上（预热）
            }
        }

        public void execute(Runnable task) {
            if (!running) throw new IllegalStateException("pool is shut down");
            if (queue.offer(task)) return;    // 入队成功，走正常路径

            // 队列满 → 走拒绝策略
            switch (rejectPolicy) {
                case ABORT -> throw new RejectedExecutionException("队列已满，任务被拒绝");
                case CALLER_RUNS -> {
                    System.out.println("  [拒绝] 队列满，" + Thread.currentThread().getName() + " 自己执行");
                    task.run();
                }
                case DISCARD -> System.out.println("  [拒绝] 静默丢弃");
            }
        }

        /** 工作线程：经典的「取任务-执行」死循环 */
        private class Worker extends Thread {
            Worker(String name) { super(name); setDaemon(true); }

            @Override
            public void run() {
                while (running) {
                    try {
                        Runnable task = queue.poll(100, TimeUnit.MILLISECONDS);  // 阻塞获取
                        if (task != null) {
                            task.run();          // 真正执行任务
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable t) {
                        // 关键：任务异常不能让工作线程死掉
                        System.err.println("任务执行异常: " + t);
                    }
                }
            }
        }

        public int queueSize() { return queue.size(); }

        @Override
        public void close() {
            running = false;
            for (Worker w : workers) w.interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 测试：3 线程 + 队列容量 3 + CallerRuns 策略 =====");
        try (SimplePool pool = new SimplePool(3, 3, SimplePool.RejectPolicy.CALLER_RUNS)) {
            for (int i = 1; i <= 12; i++) {
                final int id = i;
                pool.execute(() -> {
                    System.out.println("  执行任务 " + id + " by " + Thread.currentThread().getName());
                    try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            }
            Thread.sleep(2000);
        }

        System.out.println("\n===== 对比 JDK 原生 ThreadPoolExecutor 的源码骨架 =====");
        System.out.println("""
            《ThreadPoolExecutor.execute() 源码逐行拆解》：

            public void execute(Runnable command) {
                if (command == null) throw new NullPointerException();
                int c = ctl.get();                                  // ctl：高3位=状态, 低29位=线程数
                if (workerCountOf(c) < corePoolSize) {              // ① 核心线程没满
                    if (addWorker(command, true)) return;
                    c = ctl.get();
                }
                if (isRunning(c) && workQueue.offer(command)) {      // ② 尝试入队
                    int recheck = ctl.get();
                    if (! isRunning(recheck) && remove(command))     // 双重检查：池已停？
                        reject(command);
                    else if (workerCountOf(recheck) == 0)
                        addWorker(null, false);                      // 兜底：至少留一个线程
                }
                else if (!addWorker(command, false))                 // ③ 队列满 → 扩容到 max
                    reject(command);                                 // ④ 扩容失败 → 拒绝
            }

            ============ 关键设计点 ============
            ① ctl 一个 AtomicInteger 同时表达「运行状态」和「线程数量」：
               - 高 3 位：RUNNING / SHUTDOWN / STOP / TIDYING / TERMINATED
               - 低 29 位：workerCount（所以线程数上限约 5 亿）
               为什么要合并？→ 保证「判断状态」和「读线程数」的原子性，减少 CAS 操作。

            ② 为什么 worker 启动时先执行 firstTask，不为 null 就不去 take()？
               → 新建线程直接干活，省一次队列往返（快速路径优化）。

            ③ 为什么任务异常后线程不会死？
               → runWorker() 里用 try/finally，异常通过 afterExecute 钩子传递，
                 然后 while 循环继续 getTask()。

            ④ getTask() 的 4 个退出条件：
               - 线程数 > max 且队列空
               - 线程数 > core 且 poll 超时
               - 线程池状态 >= STOP
               - 线程池 SHUTDOWN 且队列空
            ====================================
            """);
    }
}
