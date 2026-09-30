package course.ch01;

/**
 * ch01_01 三种创建线程的方式 —— 最小可运行示例
 *
 * 运行：java src/main/java/course/ch01/_01_HelloThread.java
 */
public class _01_HelloThread {

    public static void main(String[] args) throws Exception {
        // 方式一：继承 Thread（不推荐，Java 单继承限制）
        Thread t1 = new Thread("worker-1") {
            @Override
            public void run() {
                System.out.println("[" + Thread.currentThread().getName() + "] 继承 Thread 的方式");
            }
        };

        // 方式二：实现 Runnable（推荐，任务与线程解耦）
        Thread t2 = new Thread(
                () -> System.out.println("[" + Thread.currentThread().getName() + "] 实现 Runnable 的方式"),
                "worker-2");

        // 方式三：Callable + FutureTask（有返回值、可抛异常）
        java.util.concurrent.FutureTask<String> t3 =
                new java.util.concurrent.FutureTask<>(() -> "Callable 的返回值");
        Thread t3Thread = new Thread(t3, "worker-3");

        t1.start();
        t2.start();
        t3Thread.start();

        System.out.println("[" + Thread.currentThread().getName() + "] 拿到：" + t3.get());

        // 对比：直接调用 run() 只是普通方法调用，不会开新线程
        Thread t4 = new Thread(() -> System.out.println(
                "[" + Thread.currentThread().getName() + "] 这是直接在 main 线程里同步执行的"));
        t4.run();          // 注意这里是 run() 不是 start()

        // 常用 API
        System.out.println("当前线程 = " + Thread.currentThread().getName());
        System.out.println("CPU 核心数 = " + Runtime.getRuntime().availableProcessors());
        System.out.println("isDaemon = " + Thread.currentThread().isDaemon());
    }
}
