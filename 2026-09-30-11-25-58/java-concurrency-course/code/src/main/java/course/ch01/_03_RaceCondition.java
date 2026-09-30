package course.ch01;

/**
 * ch01_03 第一个并发 Bug：竞态条件（Race Condition）
 *
 * 反复运行，你会看到不同的结果 —— 这就是并发的“不确定性”。
 * 运行：java src/main/java/course/ch01/_03_RaceCondition.java
 */
public class _03_RaceCondition {

    static int unsafeCount = 0;
    static final Object LOCK = new Object();
    static int safeCount = 0;

    public static void main(String[] args) throws Exception {
        int threads = 10;
        int times = 100_000;

        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < times; j++) {
                    unsafeCount++;                 // 非原子操作：读-改-写
                    synchronized (LOCK) {
                        safeCount++;               // 加锁后正确
                    }
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();

        int expected = threads * times;
        System.out.println("期望值      : " + expected);
        System.out.println("非同步结果  : " + unsafeCount + "   丢失 = " + (expected - unsafeCount));
        System.out.println("同步结果    : " + safeCount + "   丢失 = " + (expected - safeCount));

        // 用字节码视角解释：i++ 实际是三条指令
        System.out.println("""

                ------------------- 原因分析 -------------------
                unsafeCount++ 在字节码层面是 3 步：
                  iload  -> 把值从主内存/工作内存读入操作数栈
                  iinc   -> 在栈上 +1
                  istore -> 写回
                两个线程可能都读到 100，各自 +1 后都写回 101 —— 一次自增凭空消失。
                这就是“读-改-写”非原子性导致的丢失更新（Lost Update）。
                -------------------------------------------------
                """);
    }
}
