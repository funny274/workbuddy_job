package course.ch16;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ch16 综合实战：三个大厂级场景
 *
 * 场景一：高并发秒杀扣减库存（超卖防控）
 * 场景二：分布式限流器（令牌桶）
 * 场景三：多级缓存 + 缓存击穿防护
 *
 * 运行：java src/main/java/course/ch16/_01_SeckillStock.java
 */
public class _01_SeckillStock {

    // ============================================================
    // 场景一：秒杀库存扣减 —— 四种方案的演进
    // ============================================================
    static class StockService {
        private int stock = 100;                               // 方案1：裸 int
        private volatile int vStock = 100;                      // 方案2：volatile（无效）
        private int sStock = 100;                               // 方案3：synchronized
        private final AtomicInteger aStock = new AtomicInteger(100);  // 方案4：CAS
        private int lStock = 100;                               // 方案5：Lock
        private final ReentrantLock lock = new ReentrantLock();

        // 方案1：非线程安全
        public boolean deduct1() {
            if (stock > 0) { stock--; return true; }
            return false;
        }

        // 方案2：volatile 无效（check-then-act 不是原子操作）
        public boolean deduct2() {
            if (vStock > 0) { vStock--; return true; }
            return false;
        }

        // 方案3：synchronized（有效但性能一般）
        public synchronized boolean deduct3() {
            if (sStock > 0) { sStock--; return true; }
            return false;
        }

        // 方案4：CAS 无锁自旋 —— 秒杀场景首选（注意加失败重试上限防活锁）
        public boolean deduct4() {
            for (int retry = 0; retry < 100; retry++) {
                int cur = aStock.get();
                if (cur <= 0) return false;
                if (aStock.compareAndSet(cur, cur - 1)) return true;
            }
            return false;
        }

        // 方案5：Lock
        public boolean deduct5() {
            lock.lock();
            try {
                if (lStock > 0) { lStock--; return true; }
                return false;
            } finally { lock.unlock(); }
        }

        public int getRemaining(int scheme) {
            return switch (scheme) {
                case 1 -> stock; case 2 -> vStock; case 3 -> sStock;
                case 4 -> aStock.get(); case 5 -> lStock; default -> -1;
            };
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 场景一：秒杀库存扣减（库存 100，200 个线程抢）==========");
        int threads = 200, initialStock = 100;

        for (int scheme = 1; scheme <= 5; scheme++) {
            final int s = scheme;
            AtomicInteger success = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch end = new CountDownLatch(threads);
            // 每次用全新实例，并且用「同一个实例被多轮并发」放大竞态
            StockService svc = new StockService();

            for (int i = 0; i < threads; i++) {
                new Thread(() -> {
                    try {
                        start.await();
                        // 每个线程多尝试几轮，显著放大非原子操作的竞态窗口
                        for (int k = 0; k < 200; k++) {
                            boolean ok = switch (s) {
                                case 1 -> svc.deduct1();
                                case 2 -> svc.deduct2();
                                case 3 -> svc.deduct3();
                                case 4 -> svc.deduct4();
                                default -> svc.deduct5();
                            };
                            if (ok) success.incrementAndGet();
                        }
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { end.countDown(); }
                }).start();
            }
            start.countDown();
            end.await();

            int remaining = svc.getRemaining(s);
            int soldCount = initialStock - remaining;
            boolean correct = (remaining >= 0) && (soldCount == initialStock)
                              && (success.get() == initialStock);
            String status = correct ? "[正确]" : "[超卖/错误]";
            System.out.printf("  方案%d 成功扣减=%d 剩余库存=%d %s%n",
                    scheme, success.get(), remaining, status);
        }
        System.out.println("""
            → 方案1/2 会出现「超卖」（剩余库存为负数，或成功次数 > 库存）
            → 方案2 说明：volatile 解决不了 check-then-act 的原子性问题
            → 方案4 是秒杀场景的主流方案（配合 Redis 预扣减 + MQ 异步落库）

            ---------- 生产级秒杀架构要点 ----------
            ① 前端：答题、验证码、按钮置灰防重复提交
            ② 网关：限流（令牌桶）+ 黑名单
            ③ Redis：Lua 脚本原子预扣减库存（避免超卖）
            ④ MQ：削峰填谷，异步下单，快速返回「排队中」
            ⑤ DB：最终一致性扣减，配合唯一索引防重
            ⑥ 兜底：Redis 挂了的降级方案、库存回滚补偿
            ================================================
            """);

        System.out.println("\n========== 场景二：令牌桶限流器 ==========");
        rateLimiterDemo();

        System.out.println("\n========== 场景三：缓存击穿防护（单飞模式）==========");
        cacheBreakdownDemo();
    }

    // ============================================================
    // 场景二：令牌桶限流
    // ============================================================
    static class TokenBucketRateLimiter {
        private final long capacity;          // 桶容量
        private final long ratePerSec;        // 每秒补充令牌数
        private double tokens;                // 当前令牌数
        private long lastRefillNanos;
        private final ReentrantLock lock = new ReentrantLock();

        TokenBucketRateLimiter(long capacity, long ratePerSec) {
            this.capacity = capacity;
            this.ratePerSec = ratePerSec;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        public boolean tryAcquire() {
            lock.lock();
            try {
                refill();
                if (tokens >= 1) {
                    tokens -= 1;
                    return true;
                }
                return false;
            } finally { lock.unlock(); }
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0) return;
            double add = elapsed / 1_000_000_000.0 * ratePerSec;
            if (add > 0) {
                tokens = Math.min(capacity, tokens + add);
                lastRefillNanos = now;
            }
        }
    }

    static void rateLimiterDemo() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 50);  // 容量10，50 QPS
        AtomicInteger passed = new AtomicInteger(), rejected = new AtomicInteger();

        // 模拟 1 秒内 300 个请求
        long start = System.currentTimeMillis();
        Thread[] ts = new Thread[300];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                if (limiter.tryAcquire()) passed.incrementAndGet();
                else rejected.incrementAndGet();
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        long cost = System.currentTimeMillis() - start;
        System.out.printf("  300 个请求涌入，限流 50 QPS → 通过 %d，拒绝 %d，耗时 %dms%n",
                passed.get(), rejected.get(), cost);
        System.out.println("  → 令牌桶允许突发（桶容量蓄水），漏桶则严格恒定速率");

        System.out.println("""
              
              ---------- 四种限流算法对比 ----------
              | 算法     | 特点                             | 适用                |
              |----------|----------------------------------|---------------------|
              | 计数器   | 最简单，有临界问题（窗口边界双倍）| 粗粒度限流          |
              | 滑动窗口 | 解决临界问题，需存储时间戳        | 精确限流            |
              | 漏桶     | 恒定速率流出，不允许突发          | 保护下游稳定        |
              | 令牌桶   | 允许一定突发，最灵活              | 通用首选（Guava/sentinel）|

              ---------- 生产实现 ----------
              单机：Guava RateLimiter / Sentinel
              分布式：Redis + Lua 脚本（保证原子性），或 Redisson 的 RRateLimiter
              注意：分布式限流要处理 Redis 故障降级（通常降级为放行 + 事后统计）
              ==========================================
              """);
    }

    // ============================================================
    // 场景三：缓存击穿防护（单飞 SingleFlight 模式）
    // ============================================================
    static class CacheWithSingleFlight {
        private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CompletableFuture<String>> inflight = new ConcurrentHashMap<>();
        private final AtomicInteger dbCalls = new AtomicInteger();

        /** 关键：同一个 key 的并发请求只查一次 DB */
        public String get(String key) throws Exception {
            String cached = cache.get(key);
            if (cached != null) return cached;

            // computeIfAbsent 保证只有一个线程创建 Future
            CompletableFuture<String> future = inflight.computeIfAbsent(key, k -> {
                CompletableFuture<String> f = new CompletableFuture<>();
                // 异步去加载
                CompletableFuture.runAsync(() -> {
                    try {
                        String value = loadFromDb(k);        // 模拟查库
                        cache.put(k, value);
                        f.complete(value);
                    } catch (Exception e) {
                        f.completeExceptionally(e);
                    } finally {
                        inflight.remove(k);
                    }
                });
                return f;
            });
            return future.get(3, TimeUnit.SECONDS);
        }

        /** 模拟慢查询 */
        private String loadFromDb(String key) {
            dbCalls.incrementAndGet();
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "DB值[" + key + "]";
        }

        public int dbCallCount() { return dbCalls.get(); }
    }

    static void cacheBreakdownDemo() throws Exception {
        CacheWithSingleFlight cache = new CacheWithSingleFlight();
        int threads = 100;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger hits = new AtomicInteger();

        long start = System.currentTimeMillis();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    cache.get("hot-key");          // 100 个线程同时请求同一个热点 key
                    hits.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("err: " + e.getMessage());
                } finally { latch.countDown(); }
            }).start();
        }
        latch.await();
        long ms = System.currentTimeMillis() - start;

        System.out.printf("  100 个线程同时请求热点 key → 成功 %d 次，实际查库 %d 次，耗时 %dms%n",
                hits.get(), cache.dbCallCount(), ms);
        System.out.println("  → 单飞模式（SingleFlight）把 100 次 DB 查询合并成 1 次");
        System.out.println("     如果没有这层保护：100 次并发查库 → 缓存击穿 → DB 被打挂");

        System.out.println("""
              
              ==================== 缓存三大问题 ====================
              【1】缓存穿透：查询不存在的数据，每次都打到 DB
                 原因：恶意攻击 or 业务误用（如 id = -1）
                 解决：① 空值缓存（设短 TTL，如 60s）
                       ② 布隆过滤器前置拦截
                       ③ 参数校验（id > 0）

              【2】缓存击穿：热点 key 过期的瞬间，大量请求打到 DB
                 原因：热点数据 TTL 到期
                 解决：① 互斥锁重建（本项目演示的单飞模式）
                       ② 热点数据永不过期 + 后台异步更新
                       ③ 逻辑过期（value 里存过期时间，异步刷新）

              【3】缓存雪崩：大量 key 同时过期 or 缓存服务宕机
                 原因：统一 TTL、Redis 集群故障
                 解决：① TTL 加随机值（如 30min + random(0,5min)）
                       ② 多级缓存（本地 Caffeine + Redis）
                       ③ Redis 高可用（哨兵/集群）
                       ④ 熔断降级（Sentinel）

              ---------- 多级缓存架构 ----------
              L1: Caffeine（进程内，纳秒级，容量小）
              L2: Redis（分布式，毫秒级，容量大）
              L3: DB
              更新策略：先更新 DB，再删除缓存（Cache Aside），
                       配合延迟双删 or 订阅 binlog（Canal）异步刷新
              =======================================================
              """);
    }
}
