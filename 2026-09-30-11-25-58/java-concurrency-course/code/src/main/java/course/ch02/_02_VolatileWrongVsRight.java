package course.ch02;

/**
 * ch02_02 volatile 正确用法 vs 经典误用
 *
 * 结论一：volatile 保证可见性 + 有序性，但不保证原子性 —— 不能用来做复合运算的计数。
 * 结论二：volatile boolean 做「状态标志位」是最经典、也最正确的用法。
 *
 * 运行：java src/main/java/course/ch02/_02_VolatileWrongVsRight.java
 */
public class _02_VolatileWrongVsRight {

    static volatile int wrongCount = 0;         // 误用：volatile 修饰的计数器
    static volatile boolean shutdown = false;   // 正用：状态标志位

    public static void main(String[] args) throws Exception {
        // ---------- 误用演示 ----------
        Thread[] ts = new Thread[10];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 100_000; j++) wrongCount++;
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        int expected = 10 * 100_000;
        System.out.println("volatile 计数器：期望 " + expected + "，实际 " + wrongCount
                + "，丢失 " + (expected - wrongCount));
        System.out.println("→ volatile 只能保证『每次都读到最新值』，无法让 ++ 整体原子\n");

        // ---------- 正用演示 ----------
        Thread daemon = new Thread(() -> {
            long n = 0;
            while (!shutdown) {         // 能可靠地看到 main 的修改
                n++;
            }
            System.out.println("优雅停止，共循环 " + n + " 次");
        }, "daemon");
        daemon.start();
        Thread.sleep(200);
        shutdown = true;                // 立即可见
        daemon.join();
        System.out.println("→ volatile boolean 做开关：正确且零成本");
    }
}
