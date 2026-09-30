# Java 泛型完全指南 —— 10 年资深开发水准

> 本文定位：不是入门教程，而是把泛型从"会用"推到"精通"的完整知识体系。
> 覆盖：语言规范层（JLS）、编译器实现层（类型擦除/桥接方法）、运行时层（反射 Type 体系）、框架实战层（Spring/Jackson/MyBatis/集合源码）、面试层。

---

# 第一章 为什么需要泛型：从历史演进理解设计动机

## 1.1 泛型之前的黑暗时代

Java 1.4 及以前，集合只能装 Object：

```java
List list = new ArrayList();
list.add("hello");
list.add(42);              // 编译器不拦
String s = (String) list.get(1);  // 运行时 ClassCastException
```

两个核心问题：
1. **类型安全靠程序员自觉**，错误推迟到运行时才暴露；
2. **取值必须强制转型**，代码噪音大。

## 1.2 泛型的本质

泛型 = **参数化类型（Parameterized Type）**。把"类型"本身作为参数，在编译期完成类型检查，把运行时错误提前到编译期。

关键认知：**泛型的全部价值就是"编译期检查 + 免去显式强转"**。运行期它几乎不存在（因为类型擦除）。理解了这句话，后面的擦除机制就顺理成章。

## 1.3 兼容性决策：擦除式泛型

Java 5 引入泛型时面临选择：
- C# 路线：**具体化（reified）**，泛型信息保留到运行时，`List<int>` 和 `List<String>` 是不同类型，代价是新旧集合类型不兼容（C# 搞了 `ArrayList` / `List<T>` 两套）；
- Java 路线：**擦除（erasure）**，`List<String>` 和 `List<Integer>` 运行时都是 `List`，保证旧代码二进制兼容，代价是运行时丢失泛型信息。

Java 选了擦除，这是理解 Java 泛型一切"怪异行为"的总钥匙：
- 不能 `new T()`、不能 `T.class`、不能 `new T[]`；
- 不能 `instanceof List<String>`；
- 静态方法/字段不能用类的类型参数；
- `List<String>` 不是 `List<Object>` 的子类型。

---

# 第二章 泛型基础语法（快速过一遍，查漏补缺）

## 2.1 三种形态

```java
// 1. 泛型类
public class Box<T> {
    private T value;
    public T get() { return value; }
    public void set(T value) { this.value = value; }
}

// 2. 泛型接口 —— 最典型的就是 Comparable<T>、Comparator<T>
public interface Comparable<T> {
    int compareTo(T o);
}

// 3. 泛型方法 —— 类型参数声明在返回值之前
public static <T> T getFirst(List<T> list) {
    return list.get(0);
}

// 调用时类型推断
List<String> list = List.of("a", "b");
String s = getFirst(list);              // 推断 T = String
Box<String> box = new Box<>();          // 菱形推断（Java 7+）
```

## 2.2 类型参数命名约定（团队规范级）

| 约定 | 含义 |
|---|---|
| `T` | Type，通用类型 |
| `E` | Element，集合元素（`List<E>`） |
| `K` / `V` | Key / Value（`Map<K,V>`） |
| `R` | Return，方法返回类型（如 `Function<T,R>`、`Stream.map`） |
| `U`、`S` | 第二、第三个类型参数 |
| `?` | 未知类型（通配符） |

## 2.3 多类型参数与边界

```java
// 多参数：Map<K,V>、Function<T,R>
public interface BiFunction<T, U, R> {
    R apply(T t, U u);
}

// 上界限定：T 必须是 Number 或其子类
public class Stats<T extends Number> {
    private T num;
    public double doubleValue() {
        return num.doubleValue();   // 没有边界的话这里编译不过
    }
}

// 多重边界：类在前，接口在后，& 连接（最多一个类）
public static <T extends Comparable<T> & Serializable> T max(Collection<T> c) { ... }
```

**面试细节**：为什么多重边界中类必须放最前？——两个层面：① JLS 语法规定：边界中若含类，类必须排第一且最多一个，其后只能是接口（类要写进字节码的 extends 位置，接口写进 implements 位置，两个位置只能各出现一次）；② 擦除时用**最左边界**作为该类型参数的擦除目标。

## 2.4 泛型方法 vs 泛型类中的方法（易混淆点）

```java
public class Box<T> {
    // 这不是泛型方法！用的是类的 T
    public T get() { ... }

    // 这才是泛型方法：自己声明了新的 <E>，与类的 T 无关
    public <E> void print(E e) { System.out.println(e); }

    // 这是编译错误：静态上下文不能引用类的类型参数 T
    // public static void staticMethod(T t) { }

    // 静态泛型方法必须自己声明类型参数
    public static <T> T staticGet(Box<T> box) { return box.get(); }
}
```

原因：类的类型参数 `T` 属于**实例**（对象创建时确定），静态成员属于**类**（类加载时确定，早于任何实例），所以静态方法无法访问实例级的 T。

---

# 第三章 类型擦除：一切怪异行为的根源（核心中的核心）

## 3.1 擦除规则

编译器把每个类型参数替换为其**擦除目标**：
- 无边界 `<T>` → `Object`
- `<T extends Number>` → `Number`
- `<T extends Comparable<T> & Serializable>` → `Comparable`（第一个边界）

```java
public class Box<T extends Number> {
    private T value;
    public T get() { return value; }
}
// 编译后等价于：
public class Box {
    private Number value;
    public Number get() { return value; }
}
```

验证：`new Box<Integer>().getClass() == new Box<Double>().getClass()` 返回 `true`。JVM 里只有一份 `Box.class`。（注意：本章的 Box 声明了 `T extends Number`，所以 `Box<String>` 编译不过，验证和后文示例都用 Number 的子类做实参；3.2 节另作说明。）

## 3.2 调用侧的合成：checkcast 注入

调用方拿到的值，编译器自动插入强转（本节假设 Box 为无边界的 `Box<T>`，强转目标是 Object；若声明为有边界 `Box<T extends Number>`，调用点强转目标是边界 Number）：

```java
Box<String> box = new Box<>();
box.set("hello");
String s = box.get();
// 编译后实际是：
String s = (String) box.get();   // get() 返回 Object，编译器插入 checkcast String
```

所以类型安全并没有消失，只是**从"运行时容器自己保证"变成了"编译器在调用点强制保证"**。

## 3.3 桥接方法（Bridge Method）—— 高频面试题

当泛型类被子类化并指定具体类型参数时，为了多态依然成立，编译器会生成合成方法：

```java
public interface Supplier<T> {
    T get();
}

public class StringSupplier implements Supplier<String> {
    public String get() { return "hello"; }   // 只写了这个
}
```

编译后 `StringSupplier` 里实际有**两个** `get()`：

```java
public class StringSupplier implements Supplier {
    public String get() { return "hello"; }        // 你写的
    public Object get() { return get(); }          // 编译器生成的桥接方法
    // 桥接方法字节码：invokevirtual get()Ljava/lang/String; 然后 areturn
}
```

为什么需要？擦除后接口方法签名是 `Object get()`。如果 `StringSupplier` 只有 `String get()`，它就没有"实现"接口方法，多态调用 `Supplier s = ss; s.get()` 会在运行时 `AbstractMethodError`。

用反射可以观察到：

```java
for (Method m : StringSupplier.class.getDeclaredMethods()) {
    System.out.println(m + " bridge=" + m.isBridge() + " synthetic=" + m.isSynthetic());
}
// public java.lang.String StringSupplier.get()  bridge=false synthetic=false
// public java.lang.Object StringSupplier.get()  bridge=true  synthetic=true
```

**实战坑**：基于反射的框架（如 Spring MVC 参数解析、Mockito、AOP）需要识别并跳过桥接方法：`Method mostSpecificMethod = AopUtils.getMostSpecificMethod(...)`；`BridgeMethodResolver.findBridgedMethod(method)`（Spring 提供的工具）。自己写反射工具时，`isBridge()` 为 true 的方法不要注册为回调/事件监听器，否则同一个监听器会被注册两次。

另一个经典场景：**协变返回类型的桥接**。

```java
class Parent { Parent clone() {...} }              // 覆写 Object.clone() 收窄返回类型
// 编译器生成桥接：protected Object clone() { return clone(); }
```

这就是 Java 5 有了泛型之后才能实现的"协变返回类型（covariant return type）"的底层机制。

## 3.4 擦除导致的硬性限制（全部要背下来）

```java
public class Container<T> {

    // ✗ 1. 不能 new T()——擦除后是 new Object()
    // T t = new T();

    // ✗ 2. 不能 T.class——擦除后是 Object.class
    // Class<T> c = T.class;

    // ✗ 3. 不能 new 泛型数组——数组协变 + 运行时类型检查，与擦除冲突
    // T[] arr = new T[10];
    // ✗ 不能 new ArrayList<T>[10]、new ArrayList<String>[10]
    //   （new ArrayList<?>[10] 合法）

    // ✗ 4. 泛型类不能继承 Throwable
    // public class MyException<T> extends Exception { }  // 编译错误

    // ✗ 5. catch 不能用类型参数或参数化类型
    // try { ... } catch (T e) { }                        // 编译错误

    // ✗ 6. 静态成员不能用类的 T（见 2.4）

    // ✗ 7. instanceof 不能带泛型
    // if (obj instanceof List<String>) { }               // 编译错误
    // if (obj instanceof List<?>) { }                    // ✓ 合法（无界通配符）
    // if (obj instanceof List<String> list) { }          // ✗ instanceof 模式匹配（Java 16+）也不行
    //                                                     //   放宽只发生在 switch 模式匹配（见 9.6）
    // if (obj instanceof List<?> list) { }               // ✓ 合法

    // ✗ 8. 不能重载"擦除后签名相同"的方法
    // void f(List<String> l) {}
    // void f(List<Integer> l) {}   // 编译错误：erasure clash

    // ✗ 9. 不能实现"同一接口的两个不同参数化版本"
    // class X implements Comparable<String>, Comparable<Integer> {}  // 编译错误
}
```

第 9 条要展开说：为什么？擦除后两个 `compareTo` 都变成 `compareTo(Object)`，签名冲突。而 Java 中数组可以协变（`Integer[]` 是 `Number[]` 的子类型），泛型不能（`List<Integer>` 不是 `List<Number>` 的子类型）——因为数组是**具体化**的，运行时知道自己的元素类型并做 `ArrayStoreException` 检查；泛型被擦除了，无法运行时检查。**"数组是具体化可协变的，泛型是不可协变可擦除的"**——这句话是 Effective Java 第 28 条"列表优于数组"的理论根基。

## 3.5 例外：声明处的泛型如何留存（匿名类与子类）

```java
Box<String> box = new Box<>() {};    // 匿名子类会通过签名记录泛型信息
```

匿名内部类（以及普通子类）会在 class 文件的 `Signature` 属性中记录泛型信息——这就是后面第五章"运行时获取泛型"的原理基础。

---

# 第四章 通配符与型变：PECS（大厂代码可读性的分水岭）

## 4.1 子类型关系

```java
Integer 是 Number 的子类型 ✓
List<Integer> 不是 List<Number> 的子类型 ✗
List<Integer> 是 List<? extends Number> 的子类型 ✓
List<Number> 是 List<? super Integer> 的子类型 ✓
```

数组可协变，集合泛型默认**不型变（invariant）**——术语注意：invariant 描述"型变行为"，不要与"不可变（immutable）"混淆。**为什么泛型设计成不型变？** 反例：

```java
List<Integer> ints = new ArrayList<>();
// 假设 List<Integer> 是 List<Number> 的子类型：
List<Number> nums = ints;     // 编译器若允许
nums.add(3.14);               // 往 Integer 列表里塞了 double！
Integer i = ints.get(0);      // ClassCastException
```

协变写入会破坏类型安全，所以泛型选择了不型变 + 通配符提供**受控的**型变能力。

## 4.2 PECS 原则（Producer Extends, Consumer Super）

这是 Joshua Bloch 在 Effective Java 第 31 条提出的核心口诀：

- **如果参数是生产者（你只从中读取 T），用 `? extends T`**
- **如果参数是消费者（你只往里写入 T），用 `? super T`**
- **既要读又要写 → 精确类型，不用通配符**

```java
// 生产者场景：从 src 读元素放进 dst
public static <T> void copy(List<? super T> dst, List<? extends T> src) {
    for (int i = 0; i < src.size(); i++)
        dst.set(i, src.get(i));     // 读 src：extends 保证读出的每个元素都是 T
                                    // 写 dst：super 保证容器装得下任何 T
}
```

**为什么 `? extends T` 不能写？**

```java
List<? extends Number> list = new ArrayList<Integer>();
list.add(1);        // ✗ 编译错误！
list.add(3.14);     // ✗ 编译错误！
Number n = list.get(0);  // ✓ 读取没问题
```

`? extends Number` 的真实含义是"**某个** Number 的未知子类型"。这个 list 可能是 `List<Integer>` 也可能是 `List<Double>`，往里放任何东西都无法保证类型正确——编译器干脆禁止一切写入（除了 `null`）。它只知道**上界**（元素是 Number 或其子类），不知道**具体类型**，所以读安全、写不安全。

**为什么 `? super T` 读出来只能是 Object？**

```java
List<? super Integer> list = new ArrayList<Number>();
list.add(1);              // ✓ 放 Integer 绝对安全（任何 Integer 的父容器都能装 Integer）
Object o = list.get(0);   // 只能读到 Object
Number n = list.get(0);   // ✗ 容器里可能装的是 Object、Serializable...
```

`? super Integer` 表示"**某个** Integer 的未知父类型"，读出来的元素只能保证是 Object。

## 4.3 三种通配符速查

| 形式 | 含义 | 读 | 写 |
|---|---|---|---|
| `List<String>` | 精确类型 | T | T |
| `List<? extends T>` | T 或 T 的某个子类 | T（协变读） | ✗（除 null） |
| `List<? super T>` | T 或 T 的某个父类 | Object | T（逆变写） |
| `List<?>` | 未知类型 | Object | ✗（除 null） |

## 4.4 通配符捕获（Wildcard Capture）—— 高级面试题

```java
public static void reverse(List<?> list) { }        // 想反转任意列表
```

但 `list.set(i, list.get(j))` 编译不过：`get` 返回 `capture#1`，`set` 要 `capture#2`，两个捕获类型被认为是不同的。解法：**私有辅助泛型方法捕获通配符**：

```java
public static void reverse(List<?> list) { rev(list); }

private static <T> void rev(List<T> list) {
    for (int i = 0, j = list.size() - 1; i < j; i++, j--)
        list.set(i, list.set(j, list.get(i)));      // ✓ T 精确类型，可读可写
}
```

原理：`capture helper` 模式——把"未知但确定"的类型用私有泛型方法的 T 捕获住，方法内部 T 就是一个精确类型。这个模式叫 **capture conversion**，是 JLS 5.1.10 定义的编译器行为。**趣闻**：JDK 的 `Collections.swap` 其实没有用捕获辅助方法，而是直接用了原始类型（raw type）并留了注释说明"捕获通配符需要一个补充的私有方法"——所以实战中遇到同样场景，推荐你写捕获辅助方法（类型安全），不要学 JDK 用 raw type。面试能说出"通配符捕获 + 私有泛型辅助方法"就是资深水平。

## 4.5 通配符使用的边界感（什么时候不要用）

Effective Java 第 31 条中的附注：**不要用通配符类型作为返回类型**。它会强迫调用方代码里出现通配符：

```java
// ✗ 反面教材
public static List<? extends Number> getNumbers() { ... }
List<? extends Number> nums = getNumbers();
nums.add(1);  // 编译错误，用户懵了

// ✓ 返回精确类型
public static List<Number> getNumbers() { ... }
```

规则：**通配符只出现在方法参数（API 灵活性），类型参数出现在返回值和内部状态**。看看 `Collections.max` 的真实签名体会什么叫极致灵活：

```java
public static <T extends Object & Comparable<? super T>> T max(Collection<? extends T> coll)
```

拆解：
- `<T extends Object & Comparable<? super T>>`：T 必须能与自己比较；`Comparable<? super T>` 而非 `Comparable<T>`，因为一个类实现 `Comparable<父类>` 是完全合法的（如 `java.sql.Timestamp implements Comparable<java.util.Date>`），这样约束更宽松、适用面更广；
- `Collection<? extends T>`：参数是生产者，用 extends；
- `T extends Object & ...`：显式写 `Object` 是为了**保证擦除后是 Object**（保留二进制兼容——泛型加入前该方法签名就是 `Comparable max(Collection)`）。

---

# 第五章 运行时泛型：Type 体系与反射（框架开发必备）

擦除不是"全部擦光"。**类文件中保留了泛型的"静态痕迹"**，凡是"声明位置"的泛型信息都留了 Signature 属性：

- 类的泛型声明（`class Foo extends ArrayList<String>`）
- 字段声明的泛型（`Field.getGenericType()`）
- 方法签名/返回值的泛型（`Method.getGenericReturnType()`）
- 构造器参数的泛型
- 父类/接口的泛型参数

只有**运行期动态创建的对象**（`new ArrayList<String>()` 这个实例本身）不带泛型。一句话：**声明处泛型可反射获取，使用处泛型被擦除**。

## 5.1 java.lang.reflect.Type 继承体系

```
Type (标记接口，Java 5)
 ├── Class<T>                  —— 原始类型，如 String.class、List.class（擦除后）
 ├── ParameterizedType         —— 参数化类型，如 List<String>、Map<K,V>
 │     ├── getRawType()        → Class，原始类型 List
 │     ├── getActualTypeArguments() → Type[]，泛型实参 [String]
 │     └── getOwnerType()      → 嵌套类的外围类型
 ├── TypeVariable<D>           —— 类型变量，如 T、E
 │     ├── getName()           → "T"
 │     ├── getBounds()         → 上界，如 [Number]
 │     └── getGenericDeclaration() → 声明它的类/方法
 ├── GenericArrayType          —— 泛型数组，如 T[]、List<String>[]
 │     └── getGenericComponentType() → Type
 └── WildcardType              —— 通配符，如 ? extends Number
       ├── getUpperBounds()    → [Number]
       └── getLowerBounds()    → [Integer]（? super Integer 时）
```

这段必须能徒手默写 + 讲清每个接口对应哪种声明形式——这是 Java 框架面试的常青题。

## 5.2 核心套路：通过子类化保留泛型（Super Type Token）

```java
Map<String, List<Integer>> map = new HashMap<>();
// map.getClass() 只是 HashMap，拿不到 String, List<Integer>

// 但匿名子类可以：
Map<String, List<Integer>> map2 = new HashMap<>() {};   // 匿名类用菱形需 Java 9+
// 编译后匿名类 Signature: Ljava/util/HashMap<Ljava/lang/String;Ljava/util/List<Ljava/lang/Integer;>;>;
```

封装成通用工具（这就是 Jackson `TypeReference`、Gson `TypeToken`、Guava `TypeToken` 的原理）：

```java
public abstract class TypeReference<T> {
    private final Type type;

    protected TypeReference() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType pt) {
            this.type = pt.getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("必须带上泛型参数，如 new TypeReference<List<String>>() {}");
        }
    }

    public Type getType() { return type; }
}

// 使用：
Type t = new TypeReference<Map<String, List<User>>>() {}.getType();
```

**为什么必须加 `{}`？** 不加 `{}` 时 `new TypeReference<T>()` 创建的是 TypeReference 本身的实例，`getGenericSuperclass()` 返回的 ParameterizedType 的实参仍是类型变量 T（TypeVariable，未绑定具体类型）。加 `{}` 创建匿名子类，泛型实参在**编译期**就被固化进子类的 Signature，运行时才能读出来。同时注意：`new TypeReference<T>() {}` 写在泛型方法里、引用外层 T 时，运行时拿到的是擦除后的边界，一样没用——**Token 必须在"具体类型已知"的调用点构造**。

## 5.3 泛型方法反射工具（背下来，直接可用）

```java
/** 获取字段声明的泛型实参，如 List<String> field → String */
public static Class<?> getFieldElementType(Field field) {
    Type type = field.getGenericType();
    if (type instanceof ParameterizedType pt) {
        Type arg = pt.getActualTypeArguments()[0];
        if (arg instanceof Class<?> c) return c;
    }
    return null;
}

/** 获取父类上的泛型实参：class UserRepo implements Repository<User, Long> → [User, Long] */
public static List<Class<?>> getSuperclassTypeArguments(Class<?> clazz, Class<?> genericSuper) {
    for (Type t : clazz.getGenericInterfaces()) {
        if (t instanceof ParameterizedType pt && pt.getRawType() == genericSuper) {
            return Arrays.stream(pt.getActualTypeArguments())
                         .filter(a -> a instanceof Class)
                         .map(a -> (Class<?>) a)
                         .toList();
        }
    }
    Type sup = clazz.getGenericSuperclass();
    if (sup instanceof ParameterizedType pt && pt.getRawType() == genericSuper) {
        return Arrays.stream(pt.getActualTypeArguments())
                     .filter(a -> a instanceof Class)
                     .map(a -> (Class<?>) a)
                     .toList();
    }
    return List.of();
}
```

## 5.4 递归类型边界（F-Bounded Polymorphism）

```java
// 注意：JDK 的 Comparable 声明其实只是 interface Comparable<T>，没有递归边界；
// 递归边界是 Collections.max 等 API 在"使用处"施加的约束：
public <T extends Comparable<? super T>> T max(Collection<? extends T> c)
// 而 Enum<E extends Enum<E>> 才是 JDK 中"声明处递归边界"的真实案例（见 5.5）
```

`T extends Comparable<T>` 表示"T 必须能与 T 比较"，解决了"父类比较器被子类复用"的问题。看一个实际例子：

```java
// Builder 模式 + 自引用泛型（Spark/MyBatis-Plus 风格链式 API 的根基）
public abstract class AbstractBuilder<B extends AbstractBuilder<B>> {
    @SuppressWarnings("unchecked")
    protected B self() { return (B) this; }

    public B timeout(long ms) { return self(); }
    public B retries(int n)   { return self(); }
}

public class HttpBuilder extends AbstractBuilder<HttpBuilder> {
    public HttpBuilder url(String u) { return self(); }
}

// 链式调用时方法返回 HttpBuilder 而不是 AbstractBuilder，IDE 补全完整：
HttpBuilder b = new HttpBuilder().timeout(100).retries(3).url("https://x.com");
```

没有自引用边界的话，`.timeout()` 返回父类型，后面 `.url()` 就调不到了。MyBatis-Plus 的 `QueryWrapper`/`LambdaQueryWrapper`、Spring Security 的 DSL（`HttpSecurity` 配置链）全是这个套路。

## 5.5 自限界与 CRTP 的坑

`class Enum<E extends Enum<E>>` —— JDK 枚举的真实定义。含义："E 必须是继承自 Enum<E> 的枚举类型"，保证了 `compareTo` 只接受同种枚举。理解它需要理解"自引用泛型用于表达'本类型'"，面试讲得出 Enum 为什么这么设计就是加分项。

---

# 第六章 大厂实战场景（本文最值钱的部分）

## 6.1 场景一：统一返回体 Result<T> 与优雅链式 API

几乎所有公司后端规范的第一条。泛型在其中的正确用法：

```java
@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0; r.data = data;
        return r;
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code; r.message = msg;
        return r;
    }

    // 链式转换：map 只改 data 的类型 —— 函数式泛型的典型应用
    public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        Result<R> r = new Result<>();
        r.code = this.code; r.message = this.message;
        r.data = this.data == null ? null : mapper.apply(this.data);
        return r;
    }
}

// Controller 中
return Result.ok(userList).map(list -> list.stream().map(UserVO::new).toList());
```

**资深细节**：`Function<? super T, ? extends R>` 而不是 `Function<T, R>`——参考 `Stream.map` 的真实签名。消费者用 super、生产者用 extends（PECS 在函数式接口上的应用：`Function` 的入参是消费者，返回值是生产者）。

## 6.2 场景二：泛型 DAO / Repository 基类（CRUD 不重复造轮子）

```java
// 基类利用"子类化保留泛型"自动感知实体类型
public abstract class BaseDao<T, ID> {

    protected final Class<T> entityClass;
    protected final Class<ID> idClass;

    @SuppressWarnings("unchecked")
    protected BaseDao() {
        // 关键：this.getClass() 是具体子类（UserDao），从它身上挖泛型
        Type[] args = ((ParameterizedType) this.getClass()
                .getGenericSuperclass()).getActualTypeArguments();
        this.entityClass = (Class<T>) args[0];
        this.idClass = (Class<ID>) args[1];
    }

    protected EntityManager em;   // 由 JPA 容器/构造器注入，此处略

    public T findById(ID id) { return em.find(entityClass, id); }
    public List<T> findAll() {
        return em.createQuery("select e from " + entityClass.getSimpleName() + " e", entityClass)
                 .getResultList();
    }
}

public class UserDao extends BaseDao<User, Long> { }
// UserDao 里免费获得 findById/findAll，实体类型自动识别
```

MyBatis-Plus 的 `BaseMapper<T>`、Spring Data JPA 的 `JpaRepository<T, ID>`、公司内部的 BaseDao 框架，全是这套机制。**注意**：Spring Data 甚至不用 `getGenericSuperclass`，而是用 `ResolvableType.forClass(UserDao.class).as(JpaRepository.class).getGeneric(0).resolve()` 处理多层继承 + 泛型变量绑定，比手写反射健壮得多。

## 6.3 场景三：Spring 泛型依赖注入与事件监听

Spring 4+ 支持按泛型注入和按泛型分发事件，底层是 `ResolvableType`：

```java
// 泛型事件
public class EntityEvent<T> {
    private final T entity;
    public EntityEvent(T entity) { this.entity = entity; }
    public T getEntity() { return entity; }
}

// 按泛型参数区分监听器 —— 编译期就决定了谁消费谁
@Component
public class UserEventListener
        implements ApplicationListener<EntityEvent<User>> {
    public void onApplicationEvent(EntityEvent<User> event) { ... }
}

@Component
public class OrderEventListener
        implements ApplicationListener<EntityEvent<Order>> {
    public void onApplicationEvent(EntityEvent<Order> event) { ... }
}

// 发布
publisher.publishEvent(new EntityEvent<>(user));  // 只有 UserEventListener 收到
```

Spring 如何做到？发布事件时用 `ResolvableType.forInstance(event)` 解析出 `EntityEvent<User>` 这个泛型签名，与各监听器声明的泛型参数做匹配——匹配发生在**运行时反射读取声明处 Signature** 之上。同类的还有：
- 泛型注入：`@Autowired Repository<User> userRepo;`（按完整泛型签名区分 bean）
- `Converter<String, T>` 按源/目标泛型对注册与查找
- `ParameterizedTypeReference<List<User>>` 配合 RestTemplate/WebClient：

```java
List<User> users = restTemplate.exchange(
        url, HttpMethod.GET, null,
        new ParameterizedTypeReference<List<User>>() {})   // 又是 Super Type Token
        .getBody();
```

**为什么必须用 ParameterizedTypeReference 而不能直接 List<User> 接收？** 因为响应反序列化发生在运行时，`List<User>` 局部变量的泛型无法通过标准反射 API 获取——字节码里虽有 LocalVariableTypeTable 属性，但它默认不生成（需 `-g:vars` 编译选项）且只能靠解析字节码读取，反射 API 触达不到。必须用匿名子类把类型固化到类签名里。

## 6.4 场景四：Jackson / Fastjson 的泛型反序列化

```java
// 错误写法（能编译，带 unchecked 警告）：运行时只知道 List，元素被反序列化成 LinkedHashMap
List<User> users = objectMapper.readValue(json, List.class);

// 正确姿势 1：TypeReference
List<User> users = objectMapper.readValue(json,
        new TypeReference<List<User>>() {});

// 正确姿势 2：JavaType
JavaType type = objectMapper.getTypeFactory()
        .constructCollectionType(List.class, User.class);

// 正确姿势 3：方法参数传递泛型（把泛型作为"值"在代码中流动）
public <T> T parse(String json, Class<T> clazz) {
    return objectMapper.readValue(json, clazz);
}

// 嵌套泛型必须用 Type，Class 表达不了 Map<String, List<User>>
public <T> T parseDeep(String json, TypeReference<T> ref) {
    return objectMapper.readValue(json, ref);
}
```

这是日常开发最高频的泛型+擦除实战坑：**把 Class<T> 当"运行时类型令牌"传递**。`Class<T>` 之所以能当令牌，因为 Class 对象是具体化的——`User.class` 在运行时真实存在。模式：

```java
// 类型令牌 + 工厂：编译器保证 annotate 返回的正是 T 类型容器
public class TypeSafeContainer {
    private final Map<Class<?>, Object> map = new HashMap<>();

    public <T> void put(Class<T> type, T value) { map.put(type, value); }
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) { return type.cast(map.get(type)); }
}
// 这就是 Effective Java 第 33 条"类型安全的异构容器"
// 局限：同一个 Class 只能存一个值；无法表达 List<String> 这种参数化类型
```

## 6.5 场景五：函数式接口与 Stream 泛型推断

```java
// Stream API 的完整泛型链条
<R> Stream<R> map(Function<? super T, ? extends R> mapper);
Stream<T> filter(Predicate<? super T> predicate);
<R, A> R collect(Collector<? super T, A, R> collector);

// 链式推断：每一步的 T/R 都在编译期传播
Map<String, Integer> result = users.stream()
        .filter(u -> u.getAge() > 18)          // 推断 u: User
        .collect(Collectors.toMap(
                User::getName,                  // key 类型推断为 String
                User::getAge));                 // value 推断为 Integer
```

**泛型推断（Target Typing）规则**要掌握到"能解释报错"的程度：

```java
// 1. 推断依据调用上下文（目标类型），多级传播
List<String> list = new ArrayList<>();          // 菱形推断 OK

// 2. 方法实参位置的菱形推断：Java 8+ 已支持（目标类型会传播进实参）
process(new ArrayList<>());                     // ✓ Java 8+ 正常推断；Java 7 编译报错

// 3. 真正的经典坑：无目标类型的上下文，菱形会退化成 Object
new ArrayList<>().add("x");                     // 推断为 ArrayList<Object>
var list = new ArrayList<>();                   // ✗ var 没有目标类型 → ArrayList<Object>
var list2 = new ArrayList<String>();            // ✓ 用 var 必须显式带泛型

// 4. 条件表达式中的推断
List<String> l = flag ? new ArrayList<>() : new ArrayList<>();  // Java 8+ OK
```

**Collector 的三类型参数 `<T, A, R>`**：T 输入元素、A 累加器中间态、R 最终结果。理解 `Collectors.toList()` 返回 `Collector<T, ?, List<T>>`、`groupingBy` 返回 `Collector<T, ?, Map<K, List<T>>>`，就能看懂任何收集器的嵌套组合——这是大厂代码 review 中 stream 复杂度的门槛。

## 6.6 场景六：泛型缓存 / 本地缓存工具（类型安全的 key）

Guava Cache / Caffeine 场景中，用"泛型 key"实现类型安全的分类缓存：

```java
public class TypedCache {
    private final ConcurrentHashMap<String, ConcurrentHashMap<Class<?>, Object>> cache
            = new ConcurrentHashMap<>();

    /** computeIfAbsent 风格：类型安全 + 原子加载 */
    public <T> T computeIfAbsent(String group, Class<T> type, Supplier<T> loader) {
        var byType = cache.computeIfAbsent(group, k -> new ConcurrentHashMap<>());
        Object v = byType.computeIfAbsent(type, k -> loader.get());
        return type.cast(v);
    }
}

// 使用：
Config cfg = cache.computeIfAbsent("app", Config.class, this::loadConfig);
User   usr = cache.computeIfAbsent("session", User.class, this::loadSession);
// 取的时候不需要强转，且编译器阻止 Config.class 配 User 的取值
```

## 6.7 场景七：泛型 + 策略模式（消除 if-else 的类型安全版本）

```java
public interface Handler<T, R> {
    R handle(T request);
}

public class HandlerRegistry {
    private final Map<Class<?>, Handler<?, ?>> handlers = new HashMap<>();

    public <T, R> void register(Class<T> type, Handler<T, R> handler) {
        handlers.put(type, handler);
    }

    @SuppressWarnings("unchecked")
    public <T, R> R dispatch(T request) {
        Handler<T, R> h = (Handler<T, R>) handlers.get(request.getClass());
        if (h == null) throw new IllegalStateException("no handler for " + request.getClass());
        return h.handle(request);
    }
}
```

这就是各类"通用消息路由 / 规则引擎 / 消息处理器分发器"的骨架。**承认现实**：`Map<Class<?>, Handler<?, ?>>` 中取出 Handler 必须经一次受控的 unchecked cast——泛型系统的表达力边界就在这里，用 `@SuppressWarnings("unchecked")` + 注释说明不变量（key 类型与 value 泛型参数一致性由 register 方法保证）是资深工程师的正规操作，而不是试图绕开。

## 6.8 场景八：集合框架源码中的泛型（读源码必备）

```java
// Collections.copy —— PECS 教科书
public static <T> void copy(List<? super T> dest, List<? extends T> src)

// Collections.sort
public static <T extends Comparable<? super T>> void sort(List<T> list)
// 思考：为什么是 Comparable<? super T> 而不是 Comparable<T>？
// 答：子类可以复用父类的 compareTo 实现（Timestamp 复用 Date 的）

// Comparator 静态工厂的嵌套推断
public static <T, U> Comparator<T> comparing(
        Function<? super T, ? extends U> keyExtractor,
        Comparator<? super U> keyComparator)

// 组合：这就是为什么 users 排序代码可以无限链接
Comparator<User> c = Comparator.comparing(User::getDept, Comparator.comparing(Dept::getLevel))
                               .thenComparing(User::getAge, Comparator.reverseOrder())
                               .thenComparing(User::getName);

// Optional —— 泛型不可变容器的典范
public final class Optional<T> {
    public <U> Optional<U> map(Function<? super T, ? extends U> mapper);
    public <U> Optional<U> flatMap(Function<? super T, ? extends Optional<? extends U>> mapper);
    // 注意：Java 9 把 flatMap 的签名从 Optional<U> 放宽为 ? extends Optional<? extends U>
    // map vs flatMap：mapper 返回 Optional<U> 时，
    // map 产出 Optional<Optional<U>>（嵌套，几乎总不是你想要的），
    // flatMap 产出 Optional<U>（展平）——mapper 返回 Optional 时永远用 flatMap
}

// CompletableFuture —— 泛型 + 异步组合
<U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn);
<U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);
// thenApply vs thenCompose 的关系 = map vs flatMap，面试必考
```

## 6.9 场景九：泛型在 RPC / 中间件 SDK 中的运用

以一个简化版 MQ 消费 SDK 为例，看 SDK 如何用泛型约束用户代码：

```java
// 泛型消费者接口：消息体类型由订阅者声明
public interface MessageListener<T> {
    void onMessage(T message);
}

// 泛型注册：编译期保证"消息类型 ↔ 处理器类型"一致
public class ConsumerClient {
    public <T> void subscribe(String topic, Class<T> clazz, MessageListener<T> listener) {
        // 内部：反序列化用 clazz 作类型令牌，回调 listener 前已 cast
    }
}

// 用户侧：topic + 类型 + 处理器三者在编译期绑定，不会出现 String 消息调 User 处理器
client.subscribe("order-create", OrderMessage.class, msg -> {
    // msg 已被推断为 OrderMessage
});
```

同理可参考 RocketMQ 的 `MessageListenerConcurrently`、Kafka 的 `Consumer<K,V>`、Redisson 的 `RBucket<T>`、gRPC 的 `StreamObserver<T>`——**SDK 设计的核心思路：用泛型参数把"数据类型契约"显式化，编译期锁死**。

## 6.10 场景十：@SuppressWarnings("unchecked") 的正确纪律

```java
// 什么时候必须压警告：
// 1. 数组到泛型集合的转换
// 2. 类型令牌容器的 get（Map<Class<?>, Object>）
// 3. 反射获取泛型后的 cast
// 4. varargs 泛型（见第七章）

// 纪律（Effective Java 第 27 条）：
// 1. 只在最"确定安全"的最小范围使用——宁可方法级，不要类级
// 2. 每处加注释说明为什么安全（不变量是什么）
// 3. 用 @SafeVarargs 标注真正安全的泛型 varargs 方法
```

---

# 第七章 泛型与数组、可变参数（堆污染专题）

## 7.1 泛型数组为什么被禁止

```java
T[] a = new T[10];              // ✗
List<String>[] lsa = new List<String>[10];   // ✗
```

原因：数组在运行时检查元素写入（ArrayStoreException），但擦除后运行时不知道 T 是什么，检查无从谈起。反例推演：

```java
// 假如允许 new List<String>[10]：
Object[] oa = lsa;              // 数组协变，合法
oa[0] = List.of(42);            // 运行时数组检查失效（擦除后都是 ArrayList）
String s = lsa[0].get(0);       // ClassCastException，且没有 @SuppressWarnings 机会
```

**合法替代**：

```java
// 1. 声明可以，创建不行（unchecked cast 换取）
@SuppressWarnings("unchecked")
T[] a = (T[]) new Object[10];       // 仅限内部使用、不外泄引用

// 2. 用 List<T> 替代 T[]（首选，Effective Java 第 28 条）
List<T> list = new ArrayList<>();
```

为什么 `(T[]) new Object[10]` "内部使用安全、外泄危险"？——因为数组真正的运行时类型是 `Object[]`，如果外部有人拿到它赋给 `String[]`，写入检查又失效了。JDK 里 `ArrayList` 内部就是 `Object[] elementData` + `(E[])` 的受控 cast（`elementData(int index)` 返回时强转），这是标准范本。

## 7.2 泛型可变参数与堆污染（Heap Pollution）

```java
static <T> void dangerous(List<T>... lists) { }   // 编译有 warning
// 传参后 lists 的运行时类型是 List[]，元素写入会绕过泛型检查
```

经典堆污染案例（Effective Java 第 32 条）：

```java
static <T> T[] toArray(T... args) {
    return args;   // 返回的 T[] 实际是 Object[]（调用点创建的）
}
static <T> T[] pickTwo(T a, T b, T c) {
    return toArray(a, b, c);   // 更危险
}
String[] attrs = pickTwo("a", "b", "c");   // 运行时 ClassCastException！
```

规则：
- **自己的泛型 varargs 方法只"读"不"写"不"外泄"数组就是安全的**，加 `@SafeVarargs`（可标注 static / final / private 方法，Java 9 起也允许构造器——本质要求是方法不能被覆写）；
- JDK 标准范本：`Arrays.asList`、`Collections.addAll`、`List.of` 都有 `@SafeVarargs`；
- Java 9+ 优先用 `List.of(...)` 而不是自己写 `Arrays.asList`。

## 7.3 varargs + 泛型的 DSL 应用

```java
@SafeVarargs
public final <T> List<T> listOf(T first, T... rest) { ... }

// Builder 中的键值对收集
@SafeVarargs
public final MapBuilder put(Map.Entry<String, Object>... entries) { ... }
```

---

# 第八章 设计规范与 Code Review 清单（资深级审查视角）

## 8.1 API 设计规范

1. **API 边界上尽量泛型化，内部实现允许 Object + cast**。接口面向调用者表达契约，实现细节不暴露。
2. **参数用通配符（PECS），返回值与字段用精确类型**。
3. **优先 `<T extends Comparable<? super T>>` 这种宽容边界**，而不是 `<T extends Comparable<T>>`。
4. **能用类型令牌（Class<T> / TypeReference）解决的运行时泛型问题，不要用反射硬挖**。
5. **泛型嵌套不超过 2 层**：`Map<String, List<User>>` 可以，`Map<String, Map<String, List<Map<K,V>>>>` 说明该建 DTO/值对象了。
6. **public API 中的泛型命名**要有语义：`<REQ, RESP>` 优于 `<T, R>`（对外 SDK），内部工具类用约定缩写即可。
7. **抽象基类挖泛型的写法（`(ParameterizedType) getClass().getGenericSuperclass()`）只允许出现在继承层级 ≤ 2 的框架类中**，多层继承必须用 Spring 的 `ResolvableType` / `GenericTypeResolver`，否则泛型变量（TypeVariable）嵌套会让手写反射崩溃。

## 8.2 Code Review 高频问题清单

| # | 问题 | 严重度 |
|---|---|---|
| 1 | 返回类型用了 `? extends`/`? super` | 中：传染调用方 |
| 2 | 反序列化 `List.class` / `Map.class` 直接收 | 高：运行时 LinkedHashMap/ArrayList |
| 3 | `Class<?>` 当 Map key 却在 get 时乱 cast | 中：需保证 register/get 成对 |
| 4 | 泛型 varargs 方法外泄数组引用 | 高：堆污染 |
| 5 | 桥接方法被反射框架重复注册 | 中：监听器双回调 |
| 6 | 静态方法用类的 T | 低：根本编译不过，但说明概念不清 |
| 7 | `equals`/`instanceof` 写成 `instanceof List<String>` | 低：编译不过 |
| 8 | Stream 中 `map` 应为 `flatMap`（Optional/集合展平） | 中：逻辑 bug |
| 9 | unchecked cast 压警告范围过大（类级/方法块级） | 中：埋雷 |
| 10 | 多层泛型继承手写 getGenericSuperclass | 高：泛型变量解析必崩 |

## 8.3 面试高频题速答（30 秒版本）

**Q1：泛型的实现原理？**
类型擦除。编译期把类型参数替换为边界（默认 Object），调用点插入 checkcast，子类化时生成桥接方法保证多态。目的：与 Java 5 前的二进制兼容。

**Q2：List<String> 和 List<Integer> 在运行时是同一个类吗？**
是。运行时只有原始类型：`new ArrayList<String>()` 和 `new ArrayList<Integer>()` 的 `getClass()` 都是同一个 `java.util.ArrayList` 类对象（前提是同一实现类）。泛型信息只保留在类文件的 Signature 属性中，供反射读取**声明处**使用。

**Q3：为什么 `List<String>` 不是 `List<Object>` 的子类型？**
防止协变写入破坏类型安全：如果允许，就能往 `List<String>`（以 List<Object> 引用）里 add Integer。数组允许协变是因为它有运行时 ArrayStoreException 检查兜底，泛型擦除后没有，所以干脆设计为不变，用通配符表达受控型变。

**Q4：extends 和 super 怎么选？**
PECS：生产者 extends，消费者 super，又读又写用精确类型。

**Q5：`<T extends Comparable<? super T>>` 中 `? super T` 的意义？**
允许子类复用父类的比较器实现。例：`Timestamp implements Comparable<Date>`，若写成 `Comparable<T>` 则 Timestamp 无法参与 sort。

**Q6：泛型能用在 catch / instanceof / new T() 吗？**
都不能（类型擦除）。instanceof 可用 `List<?>` 无界形式；要 new T 得传 `Class<T>` 令牌或 `Supplier<T>`。

**Q7：泛型类可以有静态方法用 T 吗？**
不行，T 属于实例。静态方法需要自己声明 `<T>`，且这是不同的 T。

**Q8：运行时如何拿到 List<User> 里的 User？**
声明处泛型可反射获得：字段 `getGenericType()`、父类 `getGenericSuperclass()`、匿名子类 Super Type Token（`new TypeReference<List<User>>() {}`）。局部变量的泛型运行时不可靠。

**Q9：什么是桥接方法？**
编译器为实现泛型接口/覆写泛型父类方法的子类生成的合成方法（签名是擦除后的），内部委托给具体类型版本，维持多态。`Method.isBridge()` 识别，Spring 有 `BridgeMethodResolver`。

**Q10：什么是堆污染？**
泛型变量的运行时类型与声明类型不一致（如泛型 varargs 数组被写入异构类型、unchecked cast 泄漏），最终在使用点爆 ClassCastException，且异常位置远离污染点，难排查。`@SafeVarargs` 用于声明"此泛型 varargs 方法只读安全"。

**Q11：泛型方法重载为什么 `f(List<String>)` 和 `f(List<Integer>)` 冲突？**
擦除后签名都是 `f(List)`，JVM 层面重复定义。

**Q12：`List<?>` 和 `List<Object>` 的区别？**
`List<Object>` 可以装任何 Object，可读可写；`List<?>` 是"某个未知元素类型的 List"，可读（得 Object），除 null 外不可写。`List<?>` 可以接受 `List<String>`、`List<Integer>` 等，`List<Object>` 只接受 `List<Object>`。

---

# 第九章 进阶补遗（冲一线大厂/框架组）

## 9.1 泛型与重载解析的交互（编译器行为）

```java
void f(List<String> l) { }
void f(List<Integer> l) { }     // 编译错误（同擦除）

void g(Object o) { }
void g(String s) { }

List<String> list = List.of("a");
g(list);                        // 选择 g(Object)：重载解析基于编译期静态类型，
                                // List<String> 与 String 不是引用兼容关系——
                                // 泛型信息参与解析，但按"引用子类型关系"而非运行时值判断

// 面试冷知识：Java 8 泛型推断的增强使 lambda 重载歧义减少，
// 但 m(x -> {})  这种多 lambda 重载仍可能歧义，需显式 cast
```

## 9.2 泛型继承与型变的完整判定表

```java
// 给定：class A {}  class B extends A {}
//       List<B> lb;  List<A> la;  List<? extends A> lext;  List<? super B> lsup;

lb instanceof List           // true（泛型被擦除）
lb = la;                     // ✗
la = lb;                     // ✗
lext = lb;                   // ✓ 协变视图
lsup = la;                   // ✓ 逆变视图
List<? extends A> lext2 = new ArrayList<B>();   // ✓
List<? super B>  lsup2 = new ArrayList<A>();    // ✓
```

记忆法：**extends = 协变 = 读视图（只出）**，**super = 逆变 = 写视图（只进）**——和函数返回值/参数位置一致（协变返回、逆变参数，类型系统通用规律）。

## 9.3 泛型与函数式接口的组合设计模式

```java
// 模式：泛型管道（Pipeline）—— 数据网关/风控引擎常用骨架
public class Pipeline<I, O> {
    private final Function<? super I, ? extends O> func;

    private Pipeline(Function<? super I, ? extends O> func) { this.func = func; }

    public static <I> Pipeline<I, I> start() {
        return new Pipeline<>(Function.identity());
    }

    public <R> Pipeline<I, R> then(Function<? super O, ? extends R> next) {
        return new Pipeline<>(func.andThen(next));
    }

    public O execute(I input) { return func.apply(input); }
}

// 用法：类型在编译期全程传播，接错编译报错
String out = Pipeline.<Order>start()
        .then(this::validate)
        .then(this::calculate)
        .then(Order::toString)
        .execute(order);
```

## 9.4 深挖：Signature 属性与字节码

```java
// javap -v 观察：
public class Box<T extends Number> {
// Signature: LBox<TT;>;          ← 类签名
// T 的声明: <T:Ljava/lang/Number;>   ← Signature 属性里的类型变量定义
}
// 编译产物中：
// - 描述符（descriptor）：擦除后的 JVM 类型，方法表/调用用这个
// - 签名（Signature）：完整泛型信息，反射 API 读这个
```

理解"描述符 vs 签名"的双轨制，就能理解为什么"运行时既有擦除又有泛型"——两套信息并存，JVM 执行用描述符，反射读签名。

## 9.5 Valhalla 展望（加分项）

Project Valhalla 计划引入**具体化泛型（reified generics）与特化（specialization）**：`ArrayList<int>` 直接装基本类型消除装箱。截至 2026 年（JDK 25 LTS 已发布），**具体化泛型仍未落地**——JEP 218（Generics over Primitive Types）、JEP 401（Value Classes and Objects，预览推进中）在渐进推进，兼容包袱（`List<String>` 与 `List` 的二进制兼容）决定了官方只能渐进式改造。**当下的官方 workaround**：基本类型特化流（`IntStream`/`ToIntFunction`/`Comparator.comparingInt` 等）避免装箱。面试聊到泛型缺陷时提这个能体现技术视野。

## 9.6 Java 9~21 泛型相关演进（技术更新，补齐版本视野）

```java
// 1. record + 泛型（Java 16+）：泛型不可变数据载体的官方姿势
public record Page<T>(List<T> items, long total) {}
public record Pair<K, V>(K key, V value) {}

// 2. sealed + 泛型（Java 17+）+ switch 模式匹配（Java 21）：ADT 风格的错误处理
public sealed interface ApiResult<T> permits Ok, Err {}
public record Ok<T>(T value) implements ApiResult<T> {}
public record Err<T>(int code, String message) implements ApiResult<T> {}

static <T> String describe(ApiResult<T> r) {
    return switch (r) {
        case Ok<T>(var value)          -> "成功: " + value;
        case Err<T>(int code, var msg) -> "失败[" + code + "]: " + msg;
    };   // sealed 保证穷尽性，无需 default —— 泛型 + 密封 + 解构的组合拳
}

// 3. 泛型模式匹配的放宽边界（与 instanceof 的关键差异）：
//    switch 中当模式类型相对 selector 是"无条件精确"时，允许泛型模式
static void test(Collection<String> c) {
    switch (c) {
        case List<String> l -> ...     // ✓ 合法：运行时若是 List 必然是 List<String>（不型变保证）
        case Set<String> s  -> ...     // ✓
    }
}
// 而 instanceof List<String> 依旧非法（除非 List<?>）—— instanceof 未做此放宽（见 3.4 第 7 条）

// 4. var 与泛型推断（Java 10+）：见 6.5 的 Object 退化坑

// 5. Optional API 演进（Java 9）：
//    flatMap 签名放宽为 Function<? super T, ? extends Optional<? extends U>>
//    新增 or / ifPresentOrElse / stream()

// 6. @SafeVarargs 扩展（Java 9）：构造器也可标注
// 7. 匿名内部类可用菱形推断（Java 9+，类型必须可指名）
```

**要点**：Java 8 之后泛型语言机制本身几乎没有变化（擦除体系稳定），演进集中在**泛型与 record、sealed、模式匹配的组合**上。2026 年面试聊泛型，能把这些"组合拳"讲清楚，比单纯背诵擦除规则更稀缺。

---

# 第十章 刻意练习（检验是否达到资深水平）

**练习 1**：手写一个 `TypeToken<T>`（参考 5.2），支持 `new TypeToken<Map<String, List<User>>>() {}`，并能打印完整泛型树（递归遍历 Type 各子类型）。

**练习 2**：实现 `TypeSafeContainer`（6.4），再扩展支持 `List<String>` 这类参数化类型的 key（提示：key 换成 Type，比较时处理 TypeVariable 绑定）。

**练习 3**：手写 `Collections.max` 的完整签名并解释每个通配符为什么这么写（对照 4.5）。

**练习 4**：写一个泛型 `EventBus<T extends Event>`，支持按事件子类型注册/分发（参考 6.7 + Spring 事件），要求：编译期保证监听器泛型与注册事件类型一致；说明哪里需要 unchecked cast、不变量是什么。

**练习 5**：写一个自引用泛型 Builder（5.4），三层继承（Base → HttpBuilder → RetryHttpBuilder），保证链式调用全程返回最具体类型。体会 `self()` 为什么必须 unchecked cast。

**练习 6**：给 `reverse(List<?>)` 写捕获辅助方法（4.4），然后解释：如果不捕获，为什么 `list.set(i, list.get(j))` 编译不过。

**练习 7（源码题）**：阅读 `Optional.map` 与 `flatMap` 源码，解释两者泛型签名的差异；再解释 `CompletableFuture.thenApply` 与 `thenCompose` 的对应关系。

**练习 8（排错题）**：`objectMapper.readValue(json, new TypeReference<Result<List<User>>>() {})` 里 User 全变成了 LinkedHashMap，请给出至少三种可能原因（提示：Result 泛型字段是否明确、是否多层嵌套丢失 Token、fastjson/Jackson 版本对泛型字段的处理差异）。

---

# 附：知识地图（一页总览）

```
Java 泛型
├── 语法层：泛型类/接口/方法、边界 <T extends X & Y>、命名约定
├── 擦除层
│   ├── 擦除规则（边界替换 + 调用点 checkcast）
│   ├── 桥接方法（多态 + 协变返回）
│   ├── 硬限制（new T / T.class / T[] / static T / catch T / instanceof 带参）
│   └── 双轨制：descriptor（执行）vs Signature（反射）
├── 型变层
│   ├── 不型变(invariant) vs 协变 vs 逆变
│   ├── PECS（生产者 extends / 消费者 super）
│   ├── 通配符捕获（私有泛型辅助方法）
│   └── 反面规则（通配符不进返回值）
├── 运行时层
│   ├── Type 体系五接口（Class/ParameterizedType/TypeVariable/GenericArrayType/WildcardType）
│   ├── Super Type Token（匿名子类固化泛型）
│   └── 类型令牌 Class<T>（异构容器）
├── 实战层
│   ├── Result<T> / 统一响应与链式 map
│   ├── BaseDao / BaseMapper（子类挖泛型）
│   ├── Spring：ResolvableType、泛型事件、泛型注入、ParameterizedTypeReference
│   ├── Jackson：TypeReference / JavaType
│   ├── Stream / Optional / CompletableFuture（PECS + 推断）
│   └── SDK 设计：泛型契约锁死类型
├── 演进层
│   ├── record/sealed + 泛型 + 模式匹配（Java 16~21）
│   ├── var 推断退化坑（Java 10+）
│   └── Valhalla 具体化泛型（未落地，渐进推进）
└── 陷阱层
    ├── 堆污染（泛型 varargs / 数组外泄）
    ├── 桥接方法重复注册
    ├── 反序列化泛型丢失
    └── unchecked cast 纪律（最小范围 + 注释不变量）
```

---

*完。按 1→5 章顺序精读建立体系，第 6 章对照公司代码验证，第 9-10 章用于冲高阶面试与框架源码阅读。*
