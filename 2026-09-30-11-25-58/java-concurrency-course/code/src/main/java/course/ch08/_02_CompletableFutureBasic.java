package course.ch08;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ch08_04 CompletableFuture 完全指南（上）：创建、链式、组合
 *
 * 运行：java src/main/java/course/ch08/_02_CompletableFutureBasic.java
 */
public class _02_CompletableFutureBasic {

    static final ExecutorService POOL = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "cf-pool");
        t.setDaemon(true);
        return t;
    });

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) 四种创建方式 =====");
        // ① 已完成的
        CompletableFuture<String> f1 = CompletableFuture.completedFuture("立即完成");
        // ② 无返回值的异步任务
        CompletableFuture<Void> f2 = CompletableFuture.runAsync(
                () -> System.out.println("    runAsync 在 " + Thread.currentThread().getName()), POOL);
        // ③ 有返回值的异步任务
        CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> "supplyAsync 结果", POOL);
        // ④ 手动完成（用于回调式 API 桥接）
        CompletableFuture<String> f4 = new CompletableFuture<>();
        new Thread(() -> f4.complete("手动完成")).start();

        System.out.println("    " + f1.get() + " / " + f3.get() + " / " + f4.get());
        f2.get();

        System.out.println("\n===== 2) 串行链式：thenApply / thenAccept / thenRun =====");
        String chainResult = CompletableFuture
                .supplyAsync(() -> 10, POOL)
                .thenApply(v -> {                       // 有入参有返回：转换
                    System.out.println("    thenApply 在 " + Thread.currentThread().getName());
                    return v * 2;
                })
                .thenApply(v -> v + 5)
                .thenApply(v -> "最终结果=" + v)
                .get();
        System.out.println("    " + chainResult);
        System.out.println("    注意：thenApply 在「上一个任务的执行线程」上运行（同线程续跑）");

        System.out.println("\n===== 3) 带 Async 后缀：切换到别的线程 =====");
        CompletableFuture.supplyAsync(() -> "A", POOL)
                .thenApplyAsync(v -> {
                    System.out.println("    thenApplyAsync 在 " + Thread.currentThread().getName());
                    return v + "B";
                }, POOL)
                .thenAcceptAsync(v -> System.out.println("    thenAcceptAsync 拿到 " + v), POOL)
                .get();

        System.out.println("\n===== 4) 异常处理三件套 =====");
        // handle：无论成功失败都执行，可转换结果
        String h = CompletableFuture.<String>supplyAsync(() -> {
            throw new RuntimeException("模拟失败");
        }, POOL).handle((v, e) -> e != null ? "handle 兜底: " + e.getMessage() : v).get();
        System.out.println("    handle      → " + h);

        // exceptionally：只在异常时兜底
        String ex = CompletableFuture.<String>supplyAsync(() -> {
            throw new RuntimeException("boom");
        }, POOL).exceptionally(e -> "exceptionally 兜底: " + e.getCause().getMessage()).get();
        System.out.println("    exceptionally → " + ex);

        // whenComplete：观察结果但不改变它
        String wc = CompletableFuture.supplyAsync(() -> "正常值", POOL)
                .whenComplete((v, e) -> System.out.println("    whenComplete 观察到: v=" + v + ", e=" + e))
                .get();
        System.out.println("    whenComplete → 返回值不变: " + wc);

        System.out.println("\n===== 5) 组合：thenCompose（扁平化）vs thenCombine（合并）=====");
        // thenCompose：第二个任务依赖第一个的结果（串行）
        String compose = CompletableFuture.supplyAsync(() -> "用户ID=100", POOL)
                .thenCompose(uid -> CompletableFuture.supplyAsync(() -> "根据[" + uid + "]查到订单", POOL))
                .get();
        System.out.println("    thenCompose → " + compose);

        // thenCombine：两个独立任务并行，都完成后合并
        long t0 = System.currentTimeMillis();
        CompletableFuture<String> ca = CompletableFuture.supplyAsync(() -> { sleep(300); return "A"; }, POOL);
        CompletableFuture<String> cb = CompletableFuture.supplyAsync(() -> { sleep(300); return "B"; }, POOL);
        String combined = ca.thenCombine(cb, (a, b) -> a + "+" + b).get();
        System.out.println("    thenCombine → " + combined
                + "，耗时 " + (System.currentTimeMillis() - t0) + "ms（并行，非 600ms）");

        System.out.println("\n===== 6) 多任务编排：allOf / anyOf =====");
        long t1 = System.currentTimeMillis();
        List<CompletableFuture<String>> tasks = Arrays.asList("服务1", "服务2", "服务3").stream()
                .map(name -> CompletableFuture.supplyAsync(() -> {
                    sleep(200);
                    return name + "结果";
                }, POOL))
                .collect(Collectors.toList());

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        List<String> results = tasks.stream().map(CompletableFuture::join).collect(Collectors.toList());
        System.out.println("    allOf 全部完成 → " + results
                + "，耗时 " + (System.currentTimeMillis() - t1) + "ms");

        Object any = CompletableFuture.anyOf(
                CompletableFuture.supplyAsync(() -> { sleep(500); return "慢"; }, POOL),
                CompletableFuture.supplyAsync(() -> { sleep(100); return "快"; }, POOL)
        ).get();
        System.out.println("    anyOf 第一个完成 → " + any);

        System.out.println("""
            
            ============ 方法速查表 ============
            | 方法             | 入参→出参      | 用途                        |
            |------------------|---------------|-----------------------------|
            | thenApply        | T→U           | 转换结果（串行）            |
            | thenAccept       | T→void        | 消费结果                    |
            | thenRun          | void→void     | 不关心结果，跑下一步        |
            | thenCompose      | T→CF<U>       | 依赖前一结果返回新 CF（扁平化）|
            | thenCombine      | (T,U)→V       | 两个独立 CF 结果合并        |
            | allOf            | CF[]→CF<Void> | 全部完成                    |
            | anyOf            | CF[]→CF<Object>| 任一完成                   |
            | handle           | (T,E)→U       | 成功/失败都处理，可改变结果 |
            | exceptionally    | E→T           | 只处理异常，兜底返回        |
            | whenComplete     | (T,E)→void    | 只观察不改变结果            |

            ---------- 三个必知要点 ----------
            ① 没有 Async 后缀的方法：如果任务已完成，用调用者线程执行；
               如果未完成，用「完成那个任务的线程」执行。→ 可能阻塞调用者，
               也可能在任意线程跑，容易踩坑。生产建议显式用带 Async 的版本并传线程池。
            ② 默认线程池是 ForkJoinPool.commonPool()，
               parallelStream 也用它 → 共用会互相影响，必须传自己的池。
            ③ allOf 返回 CompletableFuture<Void>，拿不到各个结果，
               需要自己 join 每个子任务（本项目演示了标准写法）。
            ====================================
            """);

        POOL.shutdown();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
