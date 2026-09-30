package course.ch02;

/**
 * ch02_01 可见性问题：没有 volatile 时，线程可能永远读不到别的线程的修改
 *
 * 运行：java src/main/java/course/ch02/_01_VisibilityProblem.java
 *   → 会打印「卡住了」或超时退出（JIT 把 stop 提升为循环不变量）
 */
public class _01_VisibilityProblem {

    static /* volatile */ boolean stop = false;
    static int counter = 0;

    public static void main(String[] args) throws Exception {
        Thread worker = new Thread(() -> {
            while (!stop) {          // 编译器可能优化成 while(true)
                counter++;
            }
            System.out.println("worker 正常退出，counter = " + counter);
        }, "worker");
        worker.start();

        Thread.sleep(1000);
        stop = true;                 // main 线程改了，worker 未必看得到
        System.out.println("main 已把 stop 置为 true");

        worker.join(2000);
        if (worker.isAlive()) {
            System.out.println(">>> 卡住了！worker 至今没看到 stop=true（可见性问题）");
            System.out.println(">>> 把上面 stop 字段的 volatile 打开，再试一次");
            worker.interrupt();
            System.exit(0);
        } else {
            System.out.println(">>> 本次侥幸通过（可能因为 JIT 没做优化 / 打印触发了内存屏障）");
        }
    }
}
