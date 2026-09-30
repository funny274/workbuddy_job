package course.ch02;

/**
 * ch02_03 双重检查锁（DCL）单例的演进史
 *
 * 这是面试高频题，也是「volatile 保证有序性」最经典的实战场景。
 * 运行：java src/main/java/course/ch02/_03_DclSingleton.java
 */
public class _03_DclSingleton {

    // ============ 版本一：有 BUG 的 DCL ============
    static class BrokenDcl {
        private static BrokenDcl instance;      // 缺少 volatile

        private BrokenDcl() {}

        public static BrokenDcl getInstance() {
            if (instance == null) {                     // 第一次检查
                synchronized (BrokenDcl.class) {
                    if (instance == null) {             // 第二次检查
                        instance = new BrokenDcl();     // 问题就在这里！
                    }
                }
            }
            return instance;
        }
    }

    // ============ 版本二：正确的 DCL ============
    static class CorrectDcl {
        private static volatile CorrectDcl instance;   // 关键：volatile

        private CorrectDcl() {}

        public static CorrectDcl getInstance() {
            if (instance == null) {
                synchronized (CorrectDcl.class) {
                    if (instance == null) {
                        instance = new CorrectDcl();
                    }
                }
            }
            return instance;
        }
    }

    // ============ 版本三：静态内部类（推荐，无锁且优雅）============
    static class HolderSingleton {
        private HolderSingleton() {}

        private static class Holder {
            private static final HolderSingleton INSTANCE = new HolderSingleton();
        }

        public static HolderSingleton getInstance() {
            return Holder.INSTANCE;      // 由 JVM 类加载机制保证线程安全
        }
    }

    // ============ 版本四：枚举（《Effective Java》作者推荐，防反射/反序列化）============
    enum EnumSingleton {
        INSTANCE;
        public void doSomething() { System.out.println("枚举单例做业务"); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("""
            =========== 为什么无 volatile 的 DCL 是错的？===========
            new BrokenDcl() 在字节码层面是 3 步：
              ① memory = allocate();        // 分配内存
              ② ctorInstance(memory);       // 初始化对象（调用构造器）
              ③ instance = memory;          // 把引用指向内存地址
            其中 ② 和 ③ 可能被编译器/CPU 重排序为 ①→③→②。
            此时线程 B 在第一次检查处看到 instance != null（已经指向内存地址），
            就直接 return 了一个『构造器还没执行完的半成品对象』——
            使用它的字段就会读到默认值 0 / null。这叫『逸出』(Escape)。

            加上 volatile 后，②和③之间会插入 StoreStore 内存屏障，
            禁止这种重排序，问题消失。
            ======================================================
            """);

        System.out.println("正确 DCL 单例 = " + CorrectDcl.getInstance());
        System.out.println("静态内部类   = " + HolderSingleton.getInstance());
        EnumSingleton.INSTANCE.doSomething();

        // 并发验证：100 个线程同时拿，必须是同一个对象
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.Set<Integer> ids = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Thread[] ts = new Thread[100];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                ids.add(System.identityHashCode(CorrectDcl.getInstance()));
            });
            ts[i].start();
        }
        latch.countDown();
        for (Thread t : ts) t.join();
        System.out.println("100 个线程拿到的不同实例数量 = " + ids.size() + "（正确应为 1）");
    }
}
