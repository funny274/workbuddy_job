package course.ch10;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ch10_01 ThreadLocal 原理、内存泄漏与实战
 *
 * 运行：java src/main/java/course/ch10/_01_ThreadLocalDeep.java
 */
public class _01_ThreadLocalDeep {

    // ---------- 正确用法：static final + 用完 remove ----------
    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    // ---------- 反面教材：用 ThreadLocal 存大对象又不 remove ----------
    private static final ThreadLocal<byte[]> LEAK = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        System.out.println("===== 1) 基础：每个线程有自己独立的值 =====");
        Thread t1 = new Thread(() -> {
            CONTEXT.set(new UserContext("张三", "TENANT-A"));
            System.out.println("  线程1 读到: " + CONTEXT.get());
        }, "t1");
        Thread t2 = new Thread(() -> {
            CONTEXT.set(new UserContext("李四", "TENANT-B"));
            System.out.println("  线程2 读到: " + CONTEXT.get());
        }, "t2");
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("  main 线程读到: " + CONTEXT.get() + "（未设置，返回 null）");

        System.out.println("\n===== 2) ThreadLocal 的实现结构 =====");
        System.out.println("""
            很多人以为：ThreadLocal 是一个 Map，key 是线程，value 是值。
            实际上完全相反！

                Thread 对象里有一个字段：
                    ThreadLocal.ThreadLocalMap threadLocals;

                ThreadLocalMap 的 Entry：
                    static class Entry extends WeakReference<ThreadLocal<?>> {
                        Object value;                          // 强引用
                        Entry(ThreadLocal<?> k, Object v) {
                            super(k);                          // key 是弱引用！
                            value = v;
                        }
                    }

            → 所以数据是存在「线程」身上的，ThreadLocal 只是访问它的「钥匙」。
            → 这也是为什么每个线程天然隔离，不需要任何同步。

            ============== 内存泄漏问题（面试必问）==============
            引用链：Thread → ThreadLocalMap → Entry → value（强引用）
                                     ↑
                                  key（弱引用）→ ThreadLocal 对象

            当 ThreadLocal 对象被置为 null（外部无强引用）：
              ① key 是弱引用 → GC 时 key 被回收，变成 null
              ② 但 value 是强引用，且被 Thread 引用着 → value 无法回收
              ③ 如果线程长期存活（线程池！），value 就一直堆着 → 内存泄漏

            ---------- 为什么用弱引用？（这是设计的精妙处）----------
            用强引用的话，ThreadLocal 对象即使外部不用了，也会因为被 Map 引用而
            永远无法回收 → 泄漏更严重（连 ThreadLocal 对象本身都漏了）。
            用弱引用，至少 ThreadLocal 对象能回收，泄漏范围缩小到 value。

            ---------- 三重防御 ----------
            ① ThreadLocal 的 set/get/remove 会顺带清理 key=null 的 Entry（expungeStaleEntry）
            ② 强烈建议：显式声明为 private static final（保证外部一直有强引用，
               避免 key 被提前回收导致 Entry 变成「真·僵尸」）
            ③ 最关键：用完必须 remove()，尤其是在线程池里！
            ====================================================
            """);

        System.out.println("===== 3) 不 remove 的后果演示（线程池场景）=====");
        ExecutorService pool = Executors.newFixedThreadPool(1);
        // 第一次任务：设置了值，故意不清理
        pool.submit(() -> {
            LEAK.set(new byte[10 * 1024 * 1024]);    // 10MB
            System.out.println("  任务1 在 " + Thread.currentThread().getName()
                    + " 上放了 10MB，未清理");
        }).get();
        // 第二次任务：同一个线程，居然能读到上次的残留（也可能被 GC 掉，取决于时机）
        pool.submit(() -> {
            System.out.println("  任务2 在 " + Thread.currentThread().getName()
                    + " 上读到残留 = " + (LEAK.get() != null ? "有！脏数据！" : "null"));
            LEAK.remove();     // 正确姿势
        }).get();
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("  → 线程复用导致上下文串味，这就是「脏数据」事故的根源");

        System.out.println("\n===== 4) 正确姿势：try-finally 模板 =====");
        System.out.println("""
            private static final ThreadLocal<Ctx> CTX = new ThreadLocal<>();

            public void doBusiness() {
                CTX.set(new Ctx(...));          // 或 ThreadLocal.withInitial(...)
                try {
                    // ... 业务逻辑，期间可以随意 CTX.get()
                } finally {
                    CTX.remove();               // 必须！finally 保证异常也清理
                }
            }

            或者用 JDK8 的 withInitial 简化：
            private static final ThreadLocal<SimpleDateFormat> SDF =
                ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
            → 但 SDF 依然建议在 finally 里 remove（线程池场景）
            """);

        System.out.println("===== 5) 典型应用场景 =====");
        System.out.println("""
            ① 用户上下文 / 租户隔离：把 userId、tenantId、traceId 透传到各层
               （Spring 的 RequestContextHolder 就是这么实现的）
            ② 事务上下文：Spring 的 TransactionSynchronizationManager 用
               ThreadLocal 存 Connection / TransactionStatus
            ③ 非线程安全工具的复用：SimpleDateFormat、Random
               → 但更好的方案是 DateTimeFormatter（本身线程安全）
            ④ 链路追踪：MDC（SLF4J 的 Mapped Diagnostic Context）底层就是 ThreadLocal
            ⑤ 数据库多数据源路由、分库分表的路由键

            ---------- 跨线程传递问题（重要！）----------
            ThreadLocal 不能被子线程继承，new Thread 后 CTX.get() 是 null。
            解决方案：
            ① InheritableThreadLocal：子线程创建时复制父线程的值
               缺点：线程池里线程是复用的，创建时机不对，值会不对
            ② 阿里开源的 TransmittableThreadLocal (TTL)：
               配合 TtlExecutors.getTtlExecutorService(pool) 包装线程池，
               能在「提交任务时」把当前线程的上下文快照传递过去
               这是线程池场景下唯一正确的方案
            -------------------------------------------
            """);

        // 演示 InheritableThreadLocal
        System.out.println("===== 6) InheritableThreadLocal 演示 =====");
        InheritableThreadLocal<String> itl = new InheritableThreadLocal<>();
        itl.set("父线程的值");
        new Thread(() -> System.out.println("  子线程读到: " + itl.get())).start();
        Thread.sleep(200);
        System.out.println("  （对比普通 ThreadLocal 在子线程中会是 null）");
    }

    record UserContext(String name, String tenant) {
        @Override public String toString() { return "UserContext{name=" + name + ", tenant=" + tenant + "}"; }
    }
}
