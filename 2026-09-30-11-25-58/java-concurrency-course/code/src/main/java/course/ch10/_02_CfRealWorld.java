package course.ch10;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * ch10_02 CompletableFuture 实战：商品详情页聚合（互联网大厂高频场景）
 *
 * 场景：打开一个商品详情页，需要并行调用 6 个下游服务：
 *   商品基本信息、库存、价格、优惠券、评价、推荐
 * 串行 = 各服务耗时之和；并行 = 最慢那个的耗时。
 *
 * 运行：java src/main/java/course/ch10/_02_CfRealWorld.java
 */
public class _02_CfRealWorld {

    static final ExecutorService BIZ_POOL = new ThreadPoolExecutor(
            16, 32, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200),
            r -> { Thread t = new Thread(r, "detail-pool"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.CallerRunsPolicy());

    // ---------- 模拟的 RPC 客户端 ----------
    static String getProductInfo(String sku)      { return rpc("商品信息[" + sku + "]", 120); }
    static Integer getStock(String sku)           { return Integer.parseInt(rpc("88", 80)); }
    static Long getPrice(String sku)              { return Long.parseLong(rpc("19900", 100)); }
    static List<String> getCoupons(String sku, Long price) {
        return Arrays.asList(rpc("满199减20|满99减10", 150).split("\\|"));
    }
    static String getReviews(String sku)          { return rpc("好评率98%", 200); }
    static String getRecommend(String sku)        { return rpc("推荐：同款A/同款B", 250); }

    static String rpc(String result, long costMs) {
        sleep(costMs);
        return result;
    }

    public static void main(String[] args) throws Exception {
        String sku = "SKU-10086";

        // ============ 方式一：完全串行 ============
        System.out.println("===== 方式一：串行调用 =====");
        long s1 = System.currentTimeMillis();
        String info1 = getProductInfo(sku);
        Integer stock1 = getStock(sku);
        Long price1 = getPrice(sku);
        List<String> coupons1 = getCoupons(sku, price1);
        String reviews1 = getReviews(sku);
        String rec1 = getRecommend(sku);
        long serialMs = System.currentTimeMillis() - s1;
        System.out.println("  总耗时 = " + serialMs + "ms");

        // ============ 方式二：CompletableFuture 并行编排 ============
        System.out.println("\n===== 方式二：CompletableFuture 并行编排 =====");
        long s2 = System.currentTimeMillis();

        // 阶段1：无依赖的 4 个服务，全部并行发起
        CompletableFuture<String> infoF    = CompletableFuture.supplyAsync(() -> getProductInfo(sku), BIZ_POOL);
        CompletableFuture<Integer> stockF  = CompletableFuture.supplyAsync(() -> getStock(sku), BIZ_POOL);
        CompletableFuture<Long> priceF     = CompletableFuture.supplyAsync(() -> getPrice(sku), BIZ_POOL);
        CompletableFuture<String> reviewF  = CompletableFuture.supplyAsync(() -> getReviews(sku), BIZ_POOL);

        // 阶段2：优惠券依赖价格 → 用 thenCompose 串联
        CompletableFuture<List<String>> couponF = priceF.thenComposeAsync(
                p -> CompletableFuture.supplyAsync(() -> getCoupons(sku, p), BIZ_POOL), BIZ_POOL);

        // 推荐服务不阻塞主流程 → 给它加超时兜底
        CompletableFuture<String> recF = CompletableFuture
                .supplyAsync(() -> getRecommend(sku), BIZ_POOL)
                .orTimeout(300, TimeUnit.MILLISECONDS)
                .exceptionally(e -> "推荐服务降级：默认推荐");

        // 等待所有结果
        CompletableFuture.allOf(infoF, stockF, priceF, reviewF, couponF, recF).join();
        long parallelMs = System.currentTimeMillis() - s2;

        System.out.println("  商品 = " + infoF.join());
        System.out.println("  库存 = " + stockF.join());
        System.out.println("  价格 = " + priceF.join() + " 分");
        System.out.println("  优惠 = " + couponF.join());
        System.out.println("  评价 = " + reviewF.join());
        System.out.println("  推荐 = " + recF.join());
        System.out.printf("  总耗时 = %dms（串行 %dms，提速 %.1f 倍）%n",
                parallelMs, serialMs, (double) serialMs / parallelMs);

        // ============ 方式三：AnyOf 竞速 + 超时降级 ============
        System.out.println("\n===== 方式三：多数据源竞速（谁快用谁）=====");
        String fastest = (String) CompletableFuture.anyOf(
                CompletableFuture.supplyAsync(() -> rpc("主库数据", 300), BIZ_POOL),
                CompletableFuture.supplyAsync(() -> rpc("缓存数据", 20), BIZ_POOL),
                CompletableFuture.supplyAsync(() -> rpc("从库数据", 150), BIZ_POOL)
        ).get();
        System.out.println("  取到最快返回的 = " + fastest);

        // ============ 方式四：超时 + 降级 + 兜底（生产级模板）============
        System.out.println("\n===== 方式四：生产级「超时降级」模板 =====");
        String result = withFallback("核心服务", 100,
                () -> rpc("真实数据", 400),            // 实际耗时 400ms
                "兜底默认值");                          // 超时后的降级值
        System.out.println("  结果 = " + result);

        String result2 = withFallback("核心服务", 500,
                () -> rpc("真实数据", 100),
                "兜底默认值");
        System.out.println("  结果 = " + result2);

        System.out.println("""
            
            ============== 生产级 CompletableFuture 使用规范 ==============
            【必须做】
            ① 永远传入自定义线程池，不要用默认的 ForkJoinPool.commonPool()
               原因：commonPool 的线程数是 CPU-1，被 parallelStream / 其他业务共用，
                     一个慢任务会拖垮全站。这是最容易被忽视的生产事故。

            ② 每个下游调用都加超时：orTimeout(ms) 或 completeOnTimeout(value, ms)
               没有超时的 RPC 调用 = 埋了一颗定时炸弹。

            ③ 线程池隔离：不同下游用不同池
               否则「推荐服务慢」会把「价格服务」的线程全部占满 → 级联雪崩。

            ④ 一定要处理异常：exceptionally / handle
               否则异常会被静默吞掉，接口返回 null，排查困难。

            【常见坑】
            × 在 thenApply 里做阻塞操作（如 RPC）→ 会占住线程池线程
              → 应该用 thenApplyAsync 并传入独立线程池
            × 忘了 join/get → 主线程提前返回，异步任务白跑
            × allOf 之后忘记处理各个子任务的异常 → join 会抛 CompletionException
              → 建议用 allOf 后逐个 handle 兜底，而不是直接 join
            × 嵌套 CompletableFuture 不扁平化（用 thenApply 返回 CF<CF<T>>）
              → 应该用 thenCompose
            ===============================================================
            """);

        BIZ_POOL.shutdown();
    }

    /** 超时降级通用模板 */
    static String withFallback(String name, long timeoutMs,
                                Supplier<String> business, String fallback) {
        try {
            return CompletableFuture.supplyAsync(business, BIZ_POOL)
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(e -> {
                        System.out.println("    [" + name + "] 超时/异常，触发降级：" + shortMsg(e));
                        return fallback;
                    })
                    .get();
        } catch (Exception e) {
            return fallback;
        }
    }

    static String shortMsg(Throwable e) {
        Throwable c = e.getCause() != null ? e.getCause() : e;
        return c.getClass().getSimpleName();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
