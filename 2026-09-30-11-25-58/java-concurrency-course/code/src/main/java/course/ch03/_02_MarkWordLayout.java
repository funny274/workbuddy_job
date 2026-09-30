package course.ch03;

import org.openjdk.jol.info.ClassLayout;

/**
 * ch03_02 对象头与 Mark Word —— 亲眼看看「锁升级」把状态写在哪
 *
 * 依赖：org.openjdk.jol:jol-core
 * 编译运行（Maven 工程）：mvn -q compile exec:java -Dexec.mainClass=course.ch03._02_MarkWordLayout
 *
 * 快速命令行方式：
 *   mvn -q dependency:copy-dependencies -DoutputDirectory=target/lib
 *   java -cp "target/classes;target/lib/*" course.ch03._02_MarkWordLayout
 */
public class _02_MarkWordLayout {

    public static void main(String[] args) throws Exception {
        Object obj = new Object();

        System.out.println("=========== 1) 无锁状态（Normal / 无偏向）===========");
        // 注意：JDK 15+ 默认关闭偏向锁，所以初始通常直接是「无锁」
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        synchronized (obj) {
            System.out.println("=========== 2) 轻量级锁状态（正在被持有）===========");
            // 自己持有锁，Mark Word 里会指向栈帧中的 Lock Record
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        }

        System.out.println("=========== 3) 释放锁后回到无锁 ===========");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        System.out.println("""
            ================= Mark Word 结构（64 位 JVM）=================
            无锁      ： [unused:25 | identity_hashcode:31 | unused:1 | age:4 | biased_lock:1 | lock:2=01]
            偏向锁    ： [thread:54 | epoch:2 | unused:1 | age:4 | biased_lock:1=1 | lock:2=01]
            轻量级锁  ： [ptr_to_lock_record:62                                | lock:2=00]
            重量级锁  ： [ptr_to_monitor:62                                   | lock:2=10]
            GC 标记   ： [                                空               | lock:2=11]

            → 最后 2 位（lock）就是锁状态标识，这就是「锁升级」的物理载体。
            → 注意：Mark Word 是 64 位且内容会随锁状态变化，所以偏向锁会占用 hashCode 的位置，
              「调用了 identityHashCode 的对象无法再进入偏向锁」。
            =============================================================
            """);
    }
}
