/* ============================================================
   Spring Boot 3.x 大厂级深度精讲 —— 内容数据
   结构：CHAPTERS = [{ id, badge, badgeLabel, badgeType, title, intro, sections:[{id,title,html}] }]
   注意：代码块统一写成 <pre data-lang="java" data-code="URI编码后的代码"></pre>
        为避免手工编码出错，此处用 encodeURIComponent 的函数包装。
   ============================================================ */
var CHAPTERS = [];

/* 工具：把代码转成 <pre> 标签（自动 URI 编码，避免破坏 HTML） */
function PRE(lang, code){
  return '<pre data-lang="' + lang + '" data-code="' + encodeURIComponent(code) + '"></pre>';
}
/* 工具：面试题卡片 */
function QA(n, q, a){
  return '<div class="qa"><div class="q"><span class="qn">Q' + n + '</span><span>' + q
       + '</span><span class="arrow">▶</span></div><div class="a">' + a + '</div></div>';
}
/* 工具：提示框 */
function BOX(type, title, body, icon){
  return '<div class="box ' + type + '"><div class="box-t">' + (icon||'') + ' ' + title + '</div>' + body + '</div>';
}

/* ============================================================
   第 0 章：快速复习与体系对齐
   ============================================================ */
CHAPTERS.push({
  id: "ch0",
  badge: "0",
  badgeLabel: "第 0 章",
  badgeType: "alt",
  title: "快速复习 · 从 SSM 到 Spring Boot 的认知对齐",
  intro: "这一章不是给初学者看的。它是给你的 12 年经验做一次「坐标系校准」——把你脑子里零散的 IoC、AOP、Servlet、DispatcherServlet 知识，重新挂到 Spring Boot 的骨架上。花 40 分钟读完，后面 6 天你会快很多。",
  sections: [
    {
      id: "s0-1",
      title: "先搞清楚：Spring Boot 到底解决了什么问题",
      html:
'<p>如果你已经写了多年 SSM（Spring + SpringMVC + MyBatis），你一定经历过这样的痛苦：</p>'+
'<ul>'+
'<li>新建一个项目，光 <code>web.xml</code>、<code>applicationContext.xml</code>、<code>spring-mvc.xml</code>、<code>mybatis-config.xml</code> 就要配半天；</li>'+
'<li>引入一个 Redis，要手动写 <code>RedisTemplate</code> 的 Bean 定义，还要管连接池参数；</li>'+
'<li>依赖版本冲突，<code>NoSuchMethodError</code>、<code>ClassNotFoundException</code> 天天见；</li>'+
'<li>打出来的包要丢进外置 Tomcat，本地跑个 main 方法都费劲。</li>'+
'</ul>'+
'<p>Spring Boot 没有发明新东西，它做的是三件事的极致化：<strong>约定优于配置（Convention over Configuration）</strong>、<strong>起步依赖（Starter）</strong>、<strong>自动配置（AutoConfiguration）</strong>。</p>'+

'<h4>一句话把三者串起来</h4>'+
'<div class="tbl-wrap"><table>'+
'<thead><tr><th>机制</th><th>解决什么</th><th>本质</th></tr></thead>'+
'<tbody>'+
'<tr><td><strong>起步依赖 Starter</strong></td><td>依赖版本冲突、手动找 jar</td><td>一个 Maven 聚合 POM，把一组协调好版本的依赖打包给你，底层靠 <code>spring-boot-dependencies</code> 做 <code>dependencyManagement</code> 统一版本</td></tr>'+
'<tr><td><strong>自动配置 AutoConfiguration</strong></td><td>海量样板 XML/Java Config 配置类</td><td>基于条件装配（<code>@Conditional</code>）+ SPI 机制，在 classpath 有某个类、且你没自己定义 Bean 时，自动帮你注册</td></tr>'+
'<tr><td><strong>约定优于配置</strong></td><td>配置项太多、命名不统一</td><td>默认加载 <code>application.yml</code>、默认端口 8080、默认包扫描根路径为主类所在包</td></tr>'+
'</tbody></table></div>'+

BOX("tip","核心认知：Boot 是「胶水 + 约定」，不是新框架",
'<p>Spring Boot 的所有能力，最终都落在 <code>ApplicationContext</code> 这个 IoC 容器上。你写的 <code>@Service</code>、<code>@RestController</code>，最终还是变成了 BeanDefinition 注册进容器。<strong>理解 Boot = 理解「容器在启动时被自动塞了哪些 BeanDefinition」</strong>，这个视角一立起来，后面源码就通了。</p>','💡')
    },
    {
      id: "s0-2",
      title: "IoC 容器：你真正需要重新理解的三个层次",
      html:
'<p>大部分人能背出「IoC 是控制反转，DI 是依赖注入」，但面试时被追问「反转了什么」，就答不到点上。真正要讲清楚的是三层：</p>'+

'<h4>第一层：控制权的转移</h4>'+
'<p>传统写法 <code>new UserService()</code>，对象的<strong>创建时机、创建方式、生命周期</strong>都由调用方决定。IoC 把这三件事交给了容器：你只声明「我需要一个 UserService」，容器决定什么时候 new、怎么 new（构造器/工厂/代理）、什么时候销毁。</p>'+

'<h4>第二层：容器怎么知道要创建什么</h4>'+
'<p>这是 <code>BeanDefinition</code>（Bean 定义）。它不是 Bean 本身，而是 Bean 的<strong>蓝图</strong>：类名、作用域、是否懒加载、构造参数、属性值、依赖关系。整个 Spring 的启动过程，本质就是<strong>三个阶段</strong>：</p>'+
'<div class="tbl-wrap"><table><thead><tr><th>阶段</th><th>做什么</th><th>关键类</th></tr></thead><tbody>'+
'<tr><td>① 定义阶段</td><td>扫描注解 / 读 XML，把每个 Bean 解析成 BeanDefinition，注册进 <code>BeanDefinitionRegistry</code></td><td><code>ConfigurationClassPostProcessor</code>、<code>ClassPathBeanDefinitionScanner</code></td></tr>'+
'<tr><td>② 实例化阶段</td><td>按 BeanDefinition 反射创建对象（含构造器推断、循环依赖处理）</td><td><code>AbstractAutowireCapableBeanFactory#doCreateBean</code></td></tr>'+
'<tr><td>③ 初始化阶段</td><td>属性填充、Aware 回调、<code>BeanPostProcessor</code> 前后置、<code>@PostConstruct</code>、AOP 代理</td><td><code>initializeBean</code></td></tr>'+
'</tbody></table></div>'+

'<h4>第三层：扩展点在哪</h4>'+
'<p>大厂面试最爱问的「Spring 有哪些扩展点」，本质是问你能不能在正确的时机插手容器的行为。按时间顺序，最常用的 6 个：</p>'+
'<ol class="steps">'+
'<li><div class="st-t">BeanDefinitionRegistryPostProcessor</div>在 BeanDefinition 全部注册后、Bean 实例化前执行，可以动态注册新 Bean。Spring Boot 的自动配置就靠它。</li>'+
'<li><div class="st-t">BeanFactoryPostProcessor</div>可修改已注册的 BeanDefinition（比如改属性值）。<code>@Value</code> 占位符替换就在这一步。</li>'+
'<li><div class="st-t">InstantiationAwareBeanPostProcessor</div>实例化前后插手，可返回代理对象替代原对象。</li>'+
'<li><div class="st-t">BeanPostProcessor#postProcessBeforeInitialization</div>初始化方法（@PostConstruct）之前。</li>'+
'<li><div class="st-t">InitializingBean#afterPropertiesSet / @PostConstruct</div>真正的初始化逻辑。</li>'+
'<li><div class="st-t">BeanPostProcessor#postProcessAfterInitialization</div><strong>AOP 动态代理就在这一步生成</strong>。</li>'+
'</ol>'+

BOX("interview","面试提醒：这一节是「源码追问」的入口",
'<p>面试官问「说说 Spring 启动流程」，标准答法是：<strong>资源定位 → BeanDefinition 注册 → BeanFactory 后置处理器 → Bean 实例化 → 初始化与 AOP 代理</strong>。你只要能把这条链路讲顺，再挑「三级缓存解决循环依赖」展开，就已经超过 80% 的候选人。</p>','🎯')
    },
    {
      id: "s0-3",
      title: "AOP：从「动态代理」讲到「为什么你的切面失效了」",
      html:
'<p>AOP 的面试失分点几乎都在<strong>失效场景</strong>，而不是概念本身。</p>'+

'<h4>底层：两种代理</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>代理方式</th><th>条件</th><th>特点</th></tr></thead><tbody>'+
'<tr><td>JDK 动态代理</td><td>目标类<strong>实现了接口</strong>（默认策略）</td><td>基于 <code>InvocationHandler</code>，生成实现同一接口的代理类</td></tr>'+
'<tr><td>CGLIB 代理</td><td>目标类<strong>没有接口</strong>，或用 <code>proxyTargetClass=true</code> 强制</td><td>生成子类，通过继承覆盖方法；<strong>无法代理 final 类/方法</strong></td></tr>'+
'</tbody></table></div>'+
'<p class="mini">Spring Boot 2.x 起，<code>AopAutoConfiguration</code> 默认把 <code>proxyTargetClass</code> 设为 <code>true</code>，即<strong>默认走 CGLIB</strong>。这是和传统 Spring 的一个重要差异。</p>'+

'<h4>自调用失效的根因</h4>'+
'<p>这是最经典的坑：</p>'+
PRE("java",
`@Service
public class OrderService {

    public void create() {
        // ❌ 这里调用 this.deduct()，走的是原始对象，不经过代理
        this.deduct();
    }

    @Transactional
    public void deduct() {
        // 事务不会生效
    }
}`)+
'<p>原因：代理对象只在<strong>外部通过引用调用时</strong>才生效。类内部 <code>this</code> 指向的是目标对象本身，不是代理对象，所以增强逻辑根本没机会执行。</p>'+
'<p><strong>三种解法</strong>：</p>'+
'<ul>'+
'<li>拆到另一个 Bean 里，通过注入调用（最推荐，符合单一职责）；</li>'+
'<li>注入自己：<code>@Autowired private OrderService self;</code>，用 <code>self.deduct()</code>（注意循环依赖，Spring 4.3+ 允许）；</li>'+
'<li>用 <code>AopContext.currentProxy()</code> 拿到当前代理对象（需开启 <code>exposeProxy=true</code>）。</li>'+
'</ul>'+

BOX("danger","其他常见失效场景",
'<ul style="margin:0"><li><strong>private / final / static 方法</strong>：代理无法覆盖，增强失效。</li>'+
'<li><strong>同类内调用</strong>：如上。</li>'+
'<li><strong>切点表达式写错</strong>：比如 <code>execution(* com.xxx.service.*.*(..))</code> 漏了子包，要写 <code>..</code>。</li>'+
'<li><strong>对象不是 Spring 管理的 Bean</strong>：手动 <code>new</code> 出来的对象没有代理。</li>'+
'<li><strong>@Transactional 在非 public 方法上</strong>：事务增强默认只对 public 生效。</li></ul>','⚠️')
    },
    {
      id: "s0-4",
      title: "Spring MVC 请求链路：一条请求的完整旅程",
      html:
'<p>面试必问「一个 HTTP 请求进来，Spring MVC 是怎么处理的」。这条链路你必须能一口气讲完，并且知道每一环的扩展点。</p>'+
'<ol class="steps">'+
'<li><div class="st-t">Tomcat（内嵌容器）接收请求</div>Connector 解析 HTTP 报文，交给 <code>Http11Processor</code>，最终通过 <code>Mapper</code> 找到对应的 Engine→Host→Context→Wrapper（即 Servlet），调用 <code>DispatcherServlet#service</code>。</li>'+
'<li><div class="st-t">DispatcherServlet 派发</div><code>doDispatch()</code> 是核心入口。先拿 <code>HandlerMapping</code> 找到可执行的 Handler（<code>HandlerExecutionChain</code>，含拦截器链）。</li>'+
'<li><div class="st-t">获取 HandlerAdapter 并前置拦截</div>根据 Handler 类型选适配器（如 <code>RequestMappingHandlerAdapter</code>），先执行 <code>preHandle</code>。</li>'+
'<li><div class="st-t">参数绑定（HandlerMethodArgumentResolver）</div>把请求参数解析成方法入参。<code>@RequestBody</code> 走 <code>HttpMessageConverter</code>，<code>@RequestParam</code> 走类型转换器。</li>'+
'<li><div class="st-t">执行 Controller 方法</div>你的业务代码在这里跑。</li>'+
'<li><div class="st-t">返回值处理（HandlerMethodReturnValueHandler）</div><code>@ResponseBody</code> 走 <code>RequestResponseBodyMethodProcessor</code>，通过 Jackson 序列化成 JSON。</li>'+
'<li><div class="st-t">后置拦截 + 异常处理</div><code>postHandle</code> → 若抛异常交给 <code>HandlerExceptionResolver</code> → <code>afterCompletion</code>。</li>'+
'</ol>'+

BOX("tip","扩展点速查",
'<p>拦截器：<code>HandlerInterceptor</code>（preHandle/postHandle/afterCompletion）<br>'+
'过滤器：<code>Filter</code>（更外层，在 DispatcherServlet 之前）<br>'+
'参数解析：<code>HandlerMethodArgumentResolver</code><br>'+
'返回值处理：<code>HandlerMethodReturnValueHandler</code><br>'+
'异常处理：<code>@ExceptionHandler</code> / <code>HandlerExceptionResolver</code><br>'+
'消息转换：<code>HttpMessageConverter</code></p>','💡')
    },
    {
      id: "s0-5",
      title: "Boot 2.x → 3.x 的关键断点（必看）",
      html:
'<p>你在存量项目里积累的经验，有一部分在 Boot 3.x 里已经变了。下面这张表是你从 2.x 迁移到 3.x 必须记住的差异，面试时答出这些细节非常加分。</p>'+
'<div class="tbl-wrap"><table><thead><tr><th>维度</th><th>Spring Boot 2.x</th><th>Spring Boot 3.x</th></tr></thead><tbody>'+
'<tr><td>JDK 基线</td><td>Java 8+</td><td><strong>Java 17+</strong>（强制）</td></tr>'+
'<tr><td>Jakarta EE</td><td><code>javax.*</code></td><td><strong><code>jakarta.*</code></strong>（Servlet 6.0）</td></tr>'+
'<tr><td>Spring 版本</td><td>Spring 5.x</td><td>Spring 6.x</td></tr>'+
'<tr><td>配置导入文件</td><td><code>META-INF/spring.factories</code>（所有场景通用）</td><td>自动配置改用 <code>META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports</code>，<code>spring.factories</code> 仅保留少量场景</td></tr>'+
'<tr><td>HTTP 客户端</td><td><code>RestTemplate</code> 为主</td><td><code>RestClient</code>（同步）/ <code>WebClient</code>（响应式）、<code>@HttpExchange</code> 声明式接口</td></tr>'+
'<tr><td>原生镜像</td><td>实验性</td><td>GraalVM Native Image 正式支持（AOT 处理）</td></tr>'+
'<tr><td>可观测性</td><td>Spring Cloud Sleuth</td><td>Micrometer Observation / Tracing（Sleuth 已废弃）</td></tr>'+
'<tr><td>线程模型</td><td>平台线程</td><td>可选<strong>虚拟线程</strong>（<code>spring.threads.virtual.enabled=true</code>）</td></tr>'+
'<tr><td>核心注解</td><td><code>@ConfigurationProperties</code> 无 <code>@EnableConfigurationProperties</code></td><td>引入 <code>@ConfigurationPropertiesScan</code> 扫描</td></tr>'+
'</tbody></table></div>'+

BOX("warn","迁移踩坑 Top 3",
'<ol style="margin:0"><li><strong>javax→jakarta 全量替换</strong>：不是简单改包名，第三方库（老版本 MyBatis、老版 Swagger、某些国密 SDK）如果不适配，会直接编译失败或运行期 <code>ClassNotFoundException</code>。</li>'+
'<li><strong>spring.factories 失效</strong>：自研 starter 必须改造，否则自动配置不生效，且是<strong>静默失败</strong>（不报错，就是不装配）。</li>'+
'<li><strong>循环依赖默认禁止</strong>：Boot 2.6 起 <code>spring.main.allow-circular-references</code> 默认 <code>false</code>，Spring 6 延续。老项目里 `@Autowired` 字段注入的互相依赖会启动失败。</li></ol>','⚠️')
    },
    {
      id: "s0-6",
      title: "7 天学习路线图（每天做什么）",
      html:
'<p>下面这张表是你这一周的行动纲领。建议每天固定 2–3 小时：<strong>1 小时读原理 + 1 小时动手敲代码 + 0.5 小时过面试题</strong>。不要只读不敲，Spring Boot 的知识「读一百遍不如 debug 一次启动流程」。</p>'+
'<div class="tbl-wrap"><table><thead><tr><th>天</th><th>主题</th><th>核心目标</th><th>产出</th></tr></thead><tbody>'+
'<tr><td><strong>Day 1</strong></td><td>启动原理与自动配置</td><td>能画出启动流程图，能手写一个自己的 starter</td><td>自研 starter 工程</td></tr>'+
'<tr><td><strong>Day 2</strong></td><td>Bean 生命周期与源码</td><td>能讲清三级缓存、循环依赖、Bean 创建全流程</td><td>断点调试笔记</td></tr>'+
'<tr><td><strong>Day 3</strong></td><td>Web 开发全链路</td><td>能写出全局异常、统一响应、参数校验、拦截器</td><td>RESTful API 小项目</td></tr>'+
'<tr><td><strong>Day 4</strong></td><td>数据访问与事务</td><td>能讲透事务传播、失效场景、连接池调优</td><td>多数据源 CRUD 案例</td></tr>'+
'<tr><td><strong>Day 5</strong></td><td>进阶特性与可观测性</td><td>掌握 AOP 实战、异步、缓存、Actuator、日志</td><td>可观测性配置</td></tr>'+
'<tr><td><strong>Day 6</strong></td><td>大厂场景题攻坚</td><td>幂等、限流、分布式锁、熔断、高并发调优</td><td>场景题答题卡</td></tr>'+
'<tr><td><strong>Day 7</strong></td><td>面试突击与串讲</td><td>高频八股逐条过关 + 项目包装</td><td>面试速查手册</td></tr>'+
'</tbody></table></div>'+

BOX("ok","使用建议",
'<p>每天学完后，回到侧边栏把当天的小节标题过一遍，能<strong>合上书复述出每个标题讲了什么</strong>，才算真正掌握。如果一个标题你复述不出来，说明那一节需要重看。</p>','✅')
    }
  ]
});

/* ============================================================
   DAY 1：启动原理与自动配置
   ============================================================ */
CHAPTERS.push({
  id: "day1",
  badge: "D1",
  badgeLabel: "Day 1",
  badgeType: "",
  title: "启动原理与自动配置 · 从 main 方法到容器就绪",
  intro: "今天的目标只有一个：把 SpringApplication.run() 这一行代码，拆成你能在白板上画出来的流程图。学完你会明白，为什么加一个 @SpringBootApplication 就能跑起来，以及如何亲手写一个企业级 starter。",
  sections: [
    {
      id: "s1-1",
      title: "1.1 @SpringBootApplication 到底做了什么",
      html:
'<p>几乎所有教程都说「<code>@SpringBootApplication</code> 是组合注解」，但真正重要的是它组合了哪三个，以及每个的作用边界。</p>'+
PRE("java",
`@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}`)+
'<h4>拆开看，它等价于三个注解</h4>'+
PRE("java",
`@SpringBootConfiguration   // 本质就是 @Configuration，标记这是一个配置类
@EnableAutoConfiguration   // 开启自动配置（核心中的核心）
@ComponentScan             // 包扫描：默认扫描主类所在包及其子包
public @interface SpringBootApplication { }`)+

'<div class="tbl-wrap"><table><thead><tr><th>注解</th><th>作用</th><th>容易踩的坑</th></tr></thead><tbody>'+
'<tr><td><code>@SpringBootConfiguration</code></td><td>标记为配置类，内部就是 <code>@Configuration</code></td><td>一个工程只能有一个 <code>@SpringBootConfiguration</code>，否则启动报错</td></tr>'+
'<tr><td><code>@EnableAutoConfiguration</code></td><td>通过 <code>@Import(AutoConfigurationImportSelector.class)</code> 触发自动配置</td><td>自动配置类<strong>不是无脑全加载</strong>，靠 <code>@Conditional</code> 过滤</td></tr>'+
'<tr><td><code>@ComponentScan</code></td><td>扫描 <code>@Component/@Service/@Repository/@Controller</code> 并注册 Bean</td><td><strong>默认只扫主类所在包及子包</strong>。主类放太深的包会导致 Bean 扫不到</td></tr>'+
'</tbody></table></div>'+

BOX("warn","最高频的坑：包结构问题",
'<p>如果你的主类在 <code>com.example.demo</code>，但你写了个 <code>com.example.other.FooService</code>，它是<strong>扫不到的</strong>。正确做法是主类放在项目最外层包，所有业务包在它下面。或者手动指定：<code>@SpringBootApplication(scanBasePackages = "com.example")</code>。</p>','⚠️')+

'<h4>必须吃透：@Configuration 的两种模式（proxyBeanMethods）</h4>'+
'<p>这是大厂面 <code>@Configuration</code> / <code>@Bean</code> 时<strong>区分度高、极易被追问</strong>的点，很多人只背过 <code>@Bean</code> 却答不上来它。</p>'+
'<p><code>@Configuration</code> 有一个 <code>proxyBeanMethods</code> 属性（默认 <code>true</code>），它决定了配置类是否被 CGLIB 增强：</p>'+
'<div class="tbl-wrap"><table><thead><tr><th>模式</th><th>写法</th><th>是否 CGLIB 代理</th><th>@Bean 方法内互相调用</th><th>典型用途</th></tr></thead><tbody>'+
'<tr><td><strong>Full 全模式</strong></td><td><code>@Configuration</code>（默认 <code>proxyBeanMethods=true</code>）</td><td>是</td><td><strong>调用 <code>otherBean()</code> 返回的是容器里的单例</strong>（被代理拦截，走 BeanFactory）</td><td>需要保证 Bean 单例语义的业务配置</td></tr>'+
'<tr><td><strong>Lite 轻模式</strong></td><td><code>@Configuration(proxyBeanMethods=false)</code></td><td>否</td><td><strong>每次调用都 new 一个新对象</strong>，不再走容器</td><td>启动更快、减少代理开销；自动配置类（`@AutoConfiguration`）推荐用</td></tr>'+
'</tbody></table></div>'+
'<p>为什么 Spring Boot 官方把大量自动配置类改成 <code>proxyBeanMethods = false</code>？因为 Full 模式下每个配置类都要 CGLIB 生成子类、启动更慢，而自动配置类里的 <code>@Bean</code> 方法<strong>几乎不会互相调用</strong>，没有必须代理的理由。</p>'+
PRE("java",
`@Configuration(proxyBeanMethods = false)   // Lite 模式：不生成 CGLIB 代理，启动更快
public class MyConfig {

    @Bean
    public FooService fooService() {
        return new FooService(barService());   // ⚠️ 每次调用都会 new 一个 BarService，而非取容器单例
    }

    @Bean
    public BarService barService() {
        return new BarService();
    }
}

// ===== 对比：Full 模式 =====
@Configuration   // proxyBeanMethods 默认 true
public class MyConfig2 {
    @Bean
    public FooService fooService() {
        return new FooService(barService());   // ✅ 被 CGLIB 拦截，返回容器中同一个 BarService 单例
    }
    @Bean
    public BarService barService() { return new BarService(); }
}`)+
BOX("interview","面试满分答法：proxyBeanMethods 是什么？",
'<p>「<code>@Configuration</code> 的 <code>proxyBeanMethods</code> 默认是 <code>true</code>，此时配置类会被 CGLIB 生成子类代理，我们叫 Full 模式。<strong>proxyBeanMethods=true 会让 <code>@Bean</code> 方法之间的相互调用走 BeanFactory 而不是普通 Java 方法调用</strong>——也就是说 A 方法里调 B 方法拿到的是容器里那个单例，保证了 Bean 的单例语义不被破坏。</p>'+
'<p>当设为 <code>false</code> 就是 Lite 模式，配置类不再被代理，<code>@Bean</code> 方法互相调用就变成普通 new，会创建新实例。Spring Boot 2.2+ 之后，官方大量自动配置类都显式写成 <code>proxyBeanMethods = false</code>，一是省掉 CGLIB 代理提升启动速度，二是这些配置类里 <code>@Bean</code> 方法本来就很少互相调用，没有代理的必要。面试时能主动说出这个优化动机，会明显加分。」</p>','🎯')+
BOX("warn","两个高频追问",
'<p><strong>Q1：@Configuration 和 @Component 都能定义 @Bean，区别在哪？</strong>单从「注册 Bean」看两者都能生效，但 <code>@Component</code> 标注的类<strong>永远不会被 CGLIB 代理</strong>，其内部 <code>@Bean</code> 方法互调一定是 new，等价于 Lite 模式。所以需要「单例语义 + 类内互调」时必须用 <code>@Configuration</code>。</p>'+
'<p><strong>Q2：@Bean 和 @Component 什么时候用哪个？</strong><code>@Component</code>（含 <code>@Service/@Repository/@Controller</code>）用于<strong>自己写的类</strong>，靠组件扫描；<code>@Bean</code> 用于<strong>第三方库的类</strong>（如 <code>DataSource</code>、<code>RestTemplate</code>、<code>ThreadPoolExecutor</code>），因为这些类没有 Spring 注解、不可改源码。这是最标准的回答口径。</p>','❓')
    },
    {
      id: "s1-2",
      title: "1.2 自动配置的实现原理（面试重灾区）",
      html:
'<p>这是 Spring Boot 面试的绝对核心。你要能讲清楚这条链路的每一步。</p>'+

'<h4>链路：从 @EnableAutoConfiguration 到条件装配</h4>'+
'<ol class="steps">'+
'<li><div class="st-t">@EnableAutoConfiguration 导入 AutoConfigurationImportSelector</div>'+
PRE("java",
`@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration { }`)+
'<code>@Import</code> 会把这个 Selector 注册进容器，Spring 回调它的 <code>selectImports()</code> 决定要导入哪些配置类。</li>'+

'<li><div class="st-t">AutoConfigurationImportSelector 加载候选配置类</div>'+
'核心方法 <code>getCandidateConfigurations()</code> 通过 <code>SpringFactoriesLoader</code> 读取：<br>'+
'<strong>Boot 2.x</strong>：<code>META-INF/spring.factories</code>，key 为 <code>EnableAutoConfiguration</code><br>'+
'<strong>Boot 3.x</strong>：<code>META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports</code>（一行一个全限定类名）。<br>'+
'<span class="mini">这个文件就是 Boot 官方 jar 里列出的「所有自动配置类清单」，约 140+ 个。</span></li>'+

'<li><div class="st-t">去重 + 排除 + 条件过滤</div>'+
'拿到全量候选后，会移除你通过 <code>@SpringBootApplication(exclude = XxxAutoConfiguration.class)</code> 排除的，然后交给 <code>AutoConfigurationImportFilter</code> 做一轮快速过滤（基于 <code>@ConditionalOnClass</code> 的元数据，避免加载不存在的类报错）。</li>'+

'<li><div class="st-t">最终由 @Conditional 系列注解精确裁决</div>'+
'每个自动配置类头上挂着一堆条件注解，只有全部满足才会生效。这才是「按需装配」的真正实现。</li>'+
'</ol>'+

'<h4>@Conditional 家族速查</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>注解</th><th>生效条件</th><th>典型用例</th></tr></thead><tbody>'+
'<tr><td><code>@ConditionalOnClass</code></td><td>classpath 存在指定类</td><td>引入了 <code>spring-boot-starter-data-redis</code> 才装配 Redis 相关 Bean</td></tr>'+
'<tr><td><code>@ConditionalOnMissingBean</code></td><td>容器中<strong>不存在</strong>该 Bean</td><td><strong>「你不配我就给你默认的」</strong>——用户自定义优先</td></tr>'+
'<tr><td><code>@ConditionalOnBean</code></td><td>容器中存在指定 Bean</td><td>依赖另一个 Bean 存在才装配</td></tr>'+
'<tr><td><code>@ConditionalOnProperty</code></td><td>配置项满足指定值</td><td><code>matchIfMissing=true</code> 实现「默认开启，配置可关」</td></tr>'+
'<tr><td><code>@ConditionalOnWebApplication</code></td><td>是 Web 应用</td><td>区分 Servlet / Reactive 环境</td></tr>'+
'<tr><td><code>@ConditionalOnMissingClass</code></td><td>classpath <strong>不存在</strong>指定类</td><td>与已有组件互斥</td></tr>'+
'<tr><td><code>@ConditionalOnResource</code></td><td>存在指定资源文件</td><td>检测配置文件是否存在</td></tr>'+
'</tbody></table></div>'+

BOX("interview","面试满分答法：自动配置原理（背这段）",
'<p>「Spring Boot 自动配置的核心是 <code>@EnableAutoConfiguration</code>，它通过 <code>@Import</code> 导入了 <code>AutoConfigurationImportSelector</code>。这个 Selector 的 <code>selectImports()</code> 方法会借助 <code>SpringFactoriesLoader</code>，从 <code>META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports</code>（Boot 3.x，2.x 是 spring.factories）中读取所有候选自动配置类的全限定名。</p>'+
'<p>拿到候选清单后，会先做去重和 exclude 排除，再经过 <code>@Conditional</code> 系列注解做条件裁决——比如 <code>@ConditionalOnClass</code> 判断类是否存在、<code>@ConditionalOnMissingBean</code> 保证用户自定义的 Bean 优先、<code>@ConditionalOnProperty</code> 判断配置开关。最终只有条件全部满足的配置类才会被注册为 BeanDefinition，从而实现按需装配、开箱即用。</p>'+
'<p>另外，所有自动配置类都通过 <code>@AutoConfiguration(after = ...)</code> 或 <code>@AutoConfigureOrder</code> 控制加载顺序，避免 Bean 依赖错乱。」</p>','🎯')
    },
    {
      id: "s1-3",
      title: "1.3 实战：手写一个企业级 Starter（完整可运行）",
      html:
'<p>面试官问「你有看过或写过 starter 吗」，能答出原理是及格，能<strong>现场说出完整步骤和关键文件</strong>才是优秀。下面是一个「接口调用日志 starter」的完整实现。</p>'+

'<h4>Step 1：建两个模块</h4>'+
PRE("bash",
`xxx-spring-boot-starter          # 只放依赖，不写代码（聚合模块）
xxx-spring-boot-autoconfigure    # 放自动配置代码`)+
'<p class="mini">大厂规范是拆两个模块，starter 只做依赖传递，autoconfigure 放逻辑。你也可以合并成一个，但面试时说得出「为什么要拆」更专业。</p>'+

'<h4>Step 2：定义配置属性类</h4>'+
PRE("java",
`@ConfigurationProperties(prefix = "api.log")
public class ApiLogProperties {

    /** 是否开启日志，默认 true */
    private boolean enabled = true;

    /** 慢接口阈值（毫秒），超过则 WARN */
    private long slowThreshold = 1000;

    /** 需要打印日志的包路径 */
    private String basePackage = "com.example";

    // getter / setter 省略（或用 Lombok @Data）
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getSlowThreshold() { return slowThreshold; }
    public void setSlowThreshold(long slowThreshold) { this.slowThreshold = slowThreshold; }
    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }
}`)+

'<h4>Step 3：定义 AOP 切面</h4>'+
PRE("java",
`@Aspect
public class ApiLogAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiLogAspect.class);

    private final ApiLogProperties properties;

    public ApiLogAspect(ApiLogProperties properties) {
        this.properties = properties;
    }

    // 环绕通知：统计耗时
    @Around("execution(* com.example..controller..*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();
        try {
            Object result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            if (cost > properties.getSlowThreshold()) {
                log.warn("[慢接口] {} 耗时 {}ms", method, cost);
            } else {
                log.info("[接口] {} 耗时 {}ms", method, cost);
            }
            return result;
        } catch (Throwable e) {
            log.error("[接口异常] {} : {}", method, e.getMessage());
            throw e;
        }
    }
}`)+

'<h4>Step 4：自动配置类（条件装配）</h4>'+
PRE("java",
`@AutoConfiguration                       // Boot 3.x 新注解，替代 @Configuration
@ConditionalOnProperty(prefix = "api.log", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiLogProperties.class)
public class ApiLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean                // 用户自定义优先
    public ApiLogAspect apiLogAspect(ApiLogProperties properties) {
        return new ApiLogAspect(properties);
    }
}`)+

BOX("tip","为什么用 @ConditionalOnMissingBean",
'<p>这是 starter 设计的黄金法则：<strong>永远不要覆盖用户的定义</strong>。如果用户自己写了一个 <code>ApiLogAspect</code>，你的自动配置就应该退让。这正是「约定优于配置」的精髓——有约定，但可覆盖。</p>','💡')+

'<h4>Step 5：注册自动配置（最关键的一步）</h4>'+
'<p>在 <code>src/main/resources/META-INF/spring/</code> 下新建文件：</p>'+
PRE("bash",
`# 文件名（Boot 3.x）：
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

# 文件内容（一行一个全限定类名）：
com.example.apilog.ApiLogAutoConfiguration`)+

BOX("danger","2.x 与 3.x 的写法差异（必须记住）",
'<p><strong>Boot 2.x</strong>：在 <code>META-INF/spring.factories</code> 中写：</p>'+
'<pre style="margin:8px 0"><code>org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\<br>com.example.apilog.ApiLogAutoConfiguration</code></pre>'+
'<p><strong>Boot 3.x</strong>：改用 <code>AutoConfiguration.imports</code> 文件。<strong>如果沿用 spring.factories，自动配置会静默失效，不报任何错</strong>——这是迁移时最难排查的问题之一。</p>','⚠️')+

'<h4>Step 6：使用</h4>'+
'<p>业务项目引入 starter 依赖后，无需任何配置即可生效；需要关闭时：</p>'+
PRE("yaml",
`api:
  log:
    enabled: false          # 关闭
    slow-threshold: 500     # 慢接口阈值 500ms`)+
PRE("java",
`// 或者直接在启动类排除
@SpringBootApplication(exclude = ApiLogAutoConfiguration.class)
public class Application { }`)+

BOX("ok","本案例的面试价值",
'<p>这个案例可以覆盖至少 4 个面试考点：① 自动配置原理落地；② <code>@ConditionalOnMissingBean</code> 的设计思想；③ <code>@ConfigurationProperties</code> 类型安全配置；④ AOP 环绕通知实战。面试时讲这个案例，比背原理更有说服力。</p>','✅')
    },
    {
      id: "s1-4",
      title: "1.4 SpringApplication.run() 启动流程全解析",
      html:
'<p>把 <code>run()</code> 拆成两大部分：<strong>构造 SpringApplication 实例</strong> 和 <strong>执行 run 方法</strong>。这是面试「说说启动流程」的标准答题骨架。</p>'+

'<h4>第一部分：new SpringApplication() 阶段</h4>'+
'<ul>'+
'<li>推断应用类型：<code>WebApplicationType</code>——SERVLET / REACTIVE / NONE。判断依据是 classpath 里有没有 <code>DispatcherServlet</code> 或 <code>DispatcherHandler</code>。</li>'+
'<li>从 <code>spring.factories</code> 加载 <code>ApplicationContextInitializer</code> 和 <code>ApplicationListener</code>。</li>'+
'<li>推断主启动类：通过 <code>StackTrace</code> 找到 main 方法所在类。</li>'+
'</ul>'+

'<h4>第二部分：run() 方法（背这个顺序）</h4>'+
'<ol class="steps">'+
'<li><div class="st-t">创建 BootstrapContext 并启动计时</div>记录启动耗时，用于日志输出。</li>'+
'<li><div class="st-t">发布 ApplicationStartingEvent</div>启动事件，此时还没创建容器。监听器可以在这里做最早期的工作。</li>'+
'<li><div class="st-t">准备 Environment（环境）</div>加载配置：命令行参数 > <code>application.yml/properties</code> > 环境变量 > 系统属性。发布 <code>ApplicationEnvironmentPreparedEvent</code>。</li>'+
'<li><div class="st-t">打印 Banner</div>就是控制台那个 Spring 字样。可以自定义 <code>banner.txt</code>。</li>'+
'<li><div class="st-t">创建 ApplicationContext 容器</div>根据应用类型创建 <code>AnnotationConfigServletWebServerApplicationContext</code>（Servlet 场景）。</li>'+
'<li><div class="st-t">prepareContext：准备上下文</div>设置 Environment、执行 <code>ApplicationContextInitializer</code>、注册主配置类（把启动类作为 BeanDefinition 注册进去）、发布 <code>ApplicationContextInitializedEvent</code>。</li>'+
'<li><div class="st-t">refreshContext：刷新上下文（最核心）</div>调用 <code>AbstractApplicationContext#refresh()</code>，12 个步骤的经典流程在这里跑完：<code>invokeBeanFactoryPostProcessors</code>（自动配置在这里生效）、<code>registerBeanPostProcessors</code>、<code>onRefresh</code>（<strong>创建内嵌 Tomcat</strong>）、<code>finishBeanFactoryInitialization</code>（<strong>实例化所有单例 Bean</strong>）。</li>'+
'<li><div class="st-t">afterRefresh</div>模板方法，Boot 默认为空。</li>'+
'<li><div class="st-t">停止计时并发布 ApplicationStartedEvent</div>启动完成。</li>'+
'<li><div class="st-t">调用 ApplicationRunner / CommandLineRunner</div>如果你实现了这两个接口，会在这里执行。适合做启动后预热（比如加载缓存）。</li>'+
'<li><div class="st-t">发布 ApplicationReadyEvent</div>至此应用完全就绪，可以接收请求。</li>'+
'</ol>'+

'<h4>启动事件顺序速记</h4>'+
PRE("bash",
`ApplicationStartingEvent                启动开始
ApplicationEnvironmentPreparedEvent     环境准备完成
ApplicationContextInitializedEvent      容器初始化完成
ApplicationPreparedEvent                容器 prepare 完成
ApplicationStartedEvent                 容器 refresh 完成（Tomcat 已启动）
ApplicationReadyEvent                   完全就绪（可服务）
ApplicationFailedEvent                  启动失败`)+

BOX("interview","面试考点：内嵌 Tomcat 是什么时候启动的",
'<p>答案是 <code>refresh()</code> 的 <code>onRefresh()</code> 阶段，由 <code>ServletWebServerApplicationContext</code> 重写实现，调用 <code>createWebServer()</code> 创建并启动内嵌容器。</p>'+
'<p>追问「Boot 能换 Undertow / Jetty 吗」：可以，排除 <code>spring-boot-starter-tomcat</code> 并引入对应 starter 即可。<strong>选型理由</strong>：Undertow 基于 NIO，内存占用更低、并发性能更好，适合高并发场景；Jetty 更轻量，适合嵌入式/长连接场景。</p>','🎯')
    },
    {
      id: "s1-5",
      title: "1.5 配置加载优先级与外部化配置",
      html:
'<p>「配置不生效」是生产环境最高频的问题之一。你必须清楚优先级顺序。</p>'+

'<h4>配置源优先级（从高到低，只列最常用的）</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>优先级</th><th>来源</th><th>说明</th></tr></thead><tbody>'+
'<tr><td>1（最高）</td><td>命令行参数</td><td><code>java -jar app.jar --server.port=9090</code></td></tr>'+
'<tr><td>2</td><td>Java 系统属性</td><td><code>-Dserver.port=9090</code></td></tr>'+
'<tr><td>3</td><td>操作系统环境变量</td><td><code>SERVER_PORT=9090</code>（K8s 常用）</td></tr>'+
'<tr><td>4</td><td>外部 config/ 目录配置文件</td><td>jar 同级的 <code>./config/application.yml</code></td></tr>'+
'<tr><td>5</td><td>jar 同级配置文件</td><td><code>./application.yml</code></td></tr>'+
'<tr><td>6</td><td>jar 内 config/ 目录</td><td><code>classpath:/config/application.yml</code></td></tr>'+
'<tr><td>7（最低）</td><td>jar 内根路径</td><td><code>classpath:/application.yml</code></td></tr>'+
'</tbody></table></div>'+

BOX("warn","容易搞反的一点",
'<p><strong>外部配置优先级高于内部配置</strong>。生产打包时把 <code>application.yml</code> 打进 jar，运维想覆盖就放一个 <code>config/application.yml</code> 在 jar 旁边，立即生效，无需重新打包。这是</p>'+
'<p>「配置与制品分离」的实践。</p>','⚠️')+

'<h4>多环境配置（profile）</h4>'+
PRE("yaml",
`# application.yml —— 主配置，指定激活环境
spring:
  profiles:
    active: dev          # 开发环境
  application:
    name: order-service`)+
PRE("yaml",
`# application-dev.yml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/order_dev
---
# application-prod.yml（多文档块写法，用 --- 分隔）
server:
  port: 80
spring:
  datasource:
    url: jdbc:mysql://prod-db:3306/order
    hikari:
      maximum-pool-size: 50`)+
PRE("bash",
`# 启动时指定环境（优先级高于配置文件）
java -jar app.jar --spring.profiles.active=prod

# 或环境变量（容器化首选）
export SPRING_PROFILES_ACTIVE=prod`)+

'<h4>@ConfigurationProperties vs @Value</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>对比项</th><th>@ConfigurationProperties</th><th>@Value</th></tr></thead><tbody>'+
'<tr><td>绑定方式</td><td>批量绑定，前缀匹配</td><td>单个属性</td></tr>'+
'<tr><td>类型安全</td><td>✅ 支持松散绑定、复杂嵌套</td><td>❌ 只支持简单类型，需 SpEL</td></tr>'+
'<tr><td>松散绑定</td><td><code>slow-threshold</code> / <code>slowThreshold</code> 都能绑</td><td>必须精确匹配</td></tr>'+
'<tr><td>校验</td><td>可配合 <code>@Validated</code> + JSR-303</td><td>不支持</td></tr>'+
'<tr><td>IDE 提示</td><td>✅ 需引入 <code>spring-boot-configuration-processor</code></td><td>❌</td></tr>'+
'<tr><td>推荐场景</td><td><strong>一组有层次的配置</strong></td><td>零散的单值</td></tr>'+
'</tbody></table></div>'+
PRE("java",
`@Component
@ConfigurationProperties(prefix = "order")
@Validated                                  // 开启校验
public class OrderProperties {

    @NotNull                                // JSR-303 校验
    private String payUrl;

    @Min(1) @Max(100)
    private int maxRetry = 3;

    private List<String> whiteList = new ArrayList<>();

    private Map<String, String> headers = new HashMap<>();
    // getter / setter 省略
}`)+

BOX("tip","@ConfigurationProperties 生效的三种方式",
'<p>① 类上加 <code>@Component</code>（最简单）；② 类上加 <code>@EnableConfigurationProperties(Xxx.class)</code>（starter 场景）；③ 主类加 <code>@ConfigurationPropertiesScan</code> 批量扫描（推荐，不用逐个声明）。</p>','💡')
    },
    {
      id: "s1-6",
      title: "1.6 启动流程 Debug 实战（动手环节）",
      html:
'<p>光看不如动手。按下面步骤在 IDEA 里 debug 一次，你对启动流程的理解会有质的飞跃。</p>'+
'<ol class="steps">'+
'<li><div class="st-t">在 SpringApplication#run 打上条件断点</div>打开 <code>SpringApplication</code> 类（Ctrl+N 搜索），在第 ~300 行 <code>run(String... args)</code> 方法上打断点。启动应用，观察调用栈。</li>'+
'<li><div class="st-t">在 refreshContext 处观察容器状态</div>断点走到 <code>refreshContext(context)</code> 时，在 Debug 面板的 Variables 里看 <code>context.beanFactory.beanDefinitionNames</code>，此时应该已经有几百个 BeanDefinition 了（自动配置塞进去的）。</li>'+
'<li><div class="st-t">观察 onRefresh 创建 Tomcat</div>Step Into 进入 <code>ServletWebServerApplicationContext#onRefresh</code>，你会看到 <code>createWebServer()</code>。往下跟到 <code>TomcatWebServer#initialize</code>，能看到端口绑定日志。</li>'+
'<li><div class="st-t">观察 Bean 实例化数量</div>在 <code>finishBeanFactoryInitialization</code> 之后打印 <code>beanFactory.getSingletonCount()</code>，能看到单例 Bean 数量。你也可以用 <code>/actuator/beans</code> 端点查看全部 Bean（需引入 actuator）。</li>'+
'</ol>'+

BOX("tip","实用技巧：打印自动配置报告",
'<p>启动时加 <code>--debug</code> 参数，或在 <code>application.yml</code> 里配置：</p>'+
'<pre style="margin:8px 0"><code>debug: true</code></pre>'+
'<p>控制台会输出 <strong>Positive matches（生效的自动配置）</strong> 和 <strong>Negative matches（未生效的自动配置及原因）</strong>。排查「为什么某个 Bean 没被自动装配」时，这是最快的定位手段。</p>','💡')+

QA(1, "Spring Boot 的启动流程说一下？",
'<p>分两阶段。</p>'+
'<p><strong>构造阶段</strong>：new SpringApplication 时推断应用类型（SERVLET/REACTIVE/NONE）、加载 Initializer 和 Listener、定位主类。</p>'+
'<p><strong>run 阶段</strong>：① 发布 ApplicationStartingEvent；② 准备 Environment（加载所有配置源）；③ 打印 Banner；④ 创建 ApplicationContext；⑤ prepareContext 注册主配置类；⑥ <strong>refreshContext 刷新容器</strong>——这一步最核心，包括 invokeBeanFactoryPostProcessors（自动配置在这里生效）、registerBeanPostProcessors、onRefresh（创建内嵌 Tomcat）、finishBeanFactoryInitialization（实例化所有单例 Bean）；⑦ 发布 ApplicationStartedEvent；⑧ 执行 Runner；⑨ 发布 ApplicationReadyEvent。</p>'+
'<p>整个过程中通过 6 个事件节点对外暴露扩展时机。</p>')+

QA(2, "自动配置的原理？@ConditionalOnMissingBean 为什么重要？",
'<p>原理见 1.2 节完整答法。<code>@ConditionalOnMissingBean</code> 的重要性在于它实现了<strong>「用户优先」的装配策略</strong>：自动配置只在用户没有自定义 Bean 时提供默认实现。这是 Spring Boot 能做到「开箱即用且可完全覆盖」的关键设计，也是写 starter 必须遵守的规范。</p>'+
'<p>它之所以能生效，是因为自动配置类<strong>在用户配置类之后加载</strong>（通过 <code>@AutoConfiguration(after=...)</code> 排序），此时用户 Bean 已注册，条件判断才能拿到正确结果。</p>')+

QA(3, "为什么 Spring Boot 3 要用 AutoConfiguration.imports 替代 spring.factories？",
'<p>三个原因：</p>'+
'<p>① <strong>职责解耦</strong>：spring.factories 是个大杂烩，监听器、初始化器、自动配置全塞一起，加载时全量读取效率低。独立文件可以只读需要的那一份。</p>'+
'<p>② <strong>性能优化</strong>：Boot 3 引入 AOT 处理和 <code>@AutoConfiguration</code> 元数据，独立文件更利于编译期分析，减少启动时的类加载。</p>'+
'<p>③ <strong>消除歧义</strong>：spring.factories 里同一个 key 多个 jar 都有时会合并，容易出意外；独立文件格式一行一个类，顺序可控。</p>')
    }
  ]
});

/* ============================================================
   DAY 2：Bean 生命周期与源码
   ============================================================ */
CHAPTERS.push({
  id: "day2",
  badge: "D2",
  badgeLabel: "Day 2",
  badgeType: "",
  title: "Bean 生命周期 · 三级缓存与循环依赖源码剖析",
  intro: "今天攻克 Spring 面试的「深水区」：Bean 从定义到销毁的完整生命周期、循环依赖的三级缓存解法、以及 Bean 作用域与线程安全。这部分能讲透，面试基本可以横着走。",
  sections: [
    {
      id: "s2-1",
      title: "2.1 Bean 生命周期：完整 12 步（面试必背）",
      html:
'<p>这是 Spring 面试出现频率最高的题之一。不要只背「实例化→初始化→销毁」，那是三句话版本，面试官一问细节就露馅。</p>'+

'<h4>完整流程（按代码执行顺序）</h4>'+
'<ol class="steps">'+
'<li><div class="st-t">BeanDefinition 定位与注册</div>扫描/读取配置，解析成 BeanDefinition 注册到 <code>BeanDefinitionRegistry</code>。</li>'+
'<li><div class="st-t">BeanFactoryPostProcessor 处理</div>可修改 BeanDefinition（如占位符替换）。</li>'+
'<li><div class="st-t">实例化前：InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation</div>可返回代理对象，若返回非 null 则跳过后续实例化，直接进入初始化后处理。</li>'+
'<li><div class="st-t">实例化：createBeanInstance</div>推断构造器 → 反射调用 <code>newInstance()</code>。<strong>@Autowired 构造器注入在这一步完成</strong>。</li>'+
'<li><div class="st-t">实例化后：postProcessAfterInstantiation + postProcessProperties</div>属性填充。<strong>@Autowired 字段注入在这一步</strong>（<code>AutowiredAnnotationBeanPostProcessor</code>）。</li>'+
'<li><div class="st-t">Aware 接口回调</div>依次注入容器相关对象：<code>BeanNameAware</code> → <code>BeanClassLoaderAware</code> → <code>BeanFactoryAware</code> → <code>EnvironmentAware</code> → <code>ApplicationContextAware</code>。</li>'+
'<li><div class="st-t">BeanPostProcessor#postProcessBeforeInitialization</div>前置处理，<code>@PostConstruct</code> 由 <code>CommonAnnotationBeanPostProcessor</code> 在这一步触发。</li>'+
'<li><div class="st-t">初始化：InitializingBean#afterPropertiesSet</div>然后是自定义 <code>init-method</code> 或 <code>@Bean(initMethod="...")</code>。</li>'+
'<li><div class="st-t">BeanPostProcessor#postProcessAfterInitialization</div><strong>AOP 动态代理在这里生成</strong>（<code>AbstractAutoProxyCreator</code>）。</li>'+
'<li><div class="st-t">注册销毁回调</div>若是单例且实现了 DisposableBean 或配置了 destroyMethod，注册到销毁回调列表。</li>'+
'<li><div class="st-t">使用 Bean</div>从容器获取使用。</li>'+
'<li><div class="st-t">销毁</div>容器关闭时：<code>@PreDestroy</code> → <code>DisposableBean#destroy</code> → <code>destroy-method</code>。</li>'+
'</ol>'+

'<h4>顺序记忆口诀</h4>'+
PRE("bash",
`实例化 → 属性填充 → Aware 回调 → 前置处理(BPP) → @PostConstruct
  → afterPropertiesSet → init-method → 后置处理(BPP，AOP 代理)
  → 使用 → @PreDestroy → destroy → destroy-method`)+

BOX("danger","最高频陷阱：AOP 代理在最后才生成",
'<p>因为代理是在 <code>postProcessAfterInitialization</code> 才生成的，所以：</p>'+
'<ul style="margin:0"><li>在 <code>@PostConstruct</code> 或 <code>afterPropertiesSet</code> 中调用本类的 <code>@Transactional</code> 方法，<strong>事务不生效</strong>（此时还没代理）。</li>'+
'<li>在 <code>BeanPostProcessor</code> 中注入的依赖，AOP 代理尚未生成，拿到的是原始对象引用。这也是为什么 <code>BeanPostProcessor</code> 自己不能依赖普通 Bean（会导致过早初始化）。</li></ul>','⚠️')+

'<h4>Bean 实例化时机</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>场景</th><th>实例化时机</th></tr></thead><tbody>'+
'<tr><td>默认单例（singleton）</td><td>容器启动时<strong>立即实例化</strong>（非懒加载）</td></tr>'+
'<tr><td><code>@Lazy</code> 标注</td><td>首次被获取时</td></tr>'+
'<tr><td>prototype 多例</td><td>每次 <code>getBean()</code> 都创建新实例，容器不管理销毁</td></tr>'+
'<tr><td>被 <code>FactoryBean</code> 生产</td><td>调用 <code>getObject()</code> 时；<code>&amp;beanName</code> 可取 FactoryBean 本身</td></tr>'+
'</tbody></table></div>'
    },
    {
      id: "s2-2",
      title: "2.2 循环依赖与三级缓存（终极考点）",
      html:
'<p>这道题能区分「背过」和「懂了」。我按面试官逐步深入的问法来组织。</p>'+

'<h4>什么是循环依赖</h4>'+
PRE("java",
`@Service
public class A {
    @Autowired
    private B b;
}

@Service
public class B {
    @Autowired
    private A a;      // A 依赖 B，B 又依赖 A → 循环
}`)+

'<h4>三级缓存是什么</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>级别</th><th>名称</th><th>存储内容</th><th>作用</th></tr></thead><tbody>'+
'<tr><td>一级</td><td><code>singletonObjects</code></td><td>完整可用的单例 Bean</td><td>最终成品</td></tr>'+
'<tr><td>二级</td><td><code>earlySingletonObjects</code></td><td>提前暴露的<strong>半成品</strong> Bean（已实例化未初始化）</td><td>避免重复创建早期引用</td></tr>'+
'<tr><td>三级</td><td><code>singletonFactories</code></td><td><code>ObjectFactory</code> 工厂（lambda，调用 getObject 可拿到早期引用）</td><td><strong>解决 AOP 代理问题</strong></td></tr>'+
'</tbody></table></div>'+

'<h4>解决流程（A ←→ B 为例）</h4>'+
'<ol class="steps">'+
'<li><div class="st-t">创建 A</div><code>getBean(A)</code> → 一级缓存没有 → 实例化 A（此时 A 是个空对象），把 <code>ObjectFactory</code> 放入<strong>三级缓存</strong>。</li>'+
'<li><div class="st-t">A 开始填充属性，发现依赖 B</div><code>getBean(B)</code> → 缓存都没有 → 实例化 B，把 B 的 ObjectFactory 放入三级缓存。</li>'+
'<li><div class="st-t">B 填充属性，发现依赖 A</div><code>getBean(A)</code> → 一级没有 → 二级没有 → <strong>三级命中</strong>！调用 <code>ObjectFactory.getObject()</code>，得到 A 的早期引用（如果 A 需要 AOP，这里会生成代理对象）。</li>'+
'<li><div class="st-t">把早期引用放入二级缓存，删除三级缓存</div>关键：<strong>同一时刻只保留一份</strong>。</li>'+
'<li><div class="st-t">B 完成初始化，放入一级缓存</div>B 是个完整 Bean 了。</li>'+
'<li><div class="st-t">A 拿到 B，完成属性填充与初始化，放入一级缓存</div>循环解开。</li>'+
'</ol>'+

BOX("interview","面试官追问：为什么要三级缓存？二级缓存不够吗？",
'<p>这是最关键的追问，答错就前功尽弃。</p>'+
'<p><strong>核心原因：为了 AOP 代理。</strong></p>'+
'<p>如果只有二级缓存，那就必须在 Bean 实例化后<strong>立即</strong>决定是否生成代理并放入二级缓存。问题是：<strong>不是所有 Bean 都需要 AOP</strong>，Spring 无法在实例化阶段就知道是否需要代理（因为判断条件是 <code>@Transactional</code>、切面匹配等，需要完整信息）。</p>'+
'<p>三级缓存放的是 <code>ObjectFactory</code>（一个 lambda），<strong>把「是否生成代理」的决策延迟到真正被需要时</strong>。只有发生循环依赖、别人来取早期引用时，才调用 <code>getObject()</code> 执行 <code>getEarlyBeanReference()</code> 生成代理。这样：</p>'+
'<ul style="margin:0"><li>没有循环依赖的 Bean，正常走完整生命周期，代理在 BPP 阶段生成，行为一致；</li>'+
'<li>有循环依赖的 Bean，提前生成代理并缓存到二级，避免重复生成不同代理对象。</li></ul>'+
'<p><strong>一句话总结：三级缓存是为了「延迟决策」，保证无论是否循环依赖，最终拿到的都是同一个代理对象。</strong></p>','🎯')+

'<h4>哪些循环依赖解决不了</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>场景</th><th>能否解决</th><th>原因</th></tr></thead><tbody>'+
'<tr><td>单例 + 字段注入（@Autowired）</td><td>✅ 能</td><td>可以先用无参构造创建空对象，提前暴露引用</td></tr>'+
'<tr><td><strong>单例 + 构造器注入</strong></td><td>❌ <strong>不能</strong></td><td>构造器必须拿到完整依赖才能创建对象，无法提前暴露半成品</td></tr>'+
'<tr><td>prototype 多例</td><td>❌ 不能</td><td>不缓存，每次都新建，无法提前暴露</td></tr>'+
'<tr><td>@Async 代理的循环依赖</td><td>❌ 常报错</td><td>代理生成时机特殊，易出现 BeanCurrentlyInCreationException</td></tr>'+
'</tbody></table></div>'+

BOX("warn","Boot 2.6+ 默认禁止循环依赖",
'<p>Spring Boot 2.6 起 <code>spring.main.allow-circular-references</code> 默认为 <code>false</code>，需要显式开启：</p>'+
'<pre style="margin:8px 0"><code>spring:<br>  main:<br>    allow-circular-references: true</code></pre>'+
'<p><strong>但生产上不建议开启</strong>——循环依赖本身是设计问题（违反单一职责），正确做法是重构：抽离第三方 Bean、改用 <code>@Lazy</code> 打破循环、或用事件机制解耦。</p>','⚠️')+

QA(1, "Spring 如何解决循环依赖？",
'<p>通过三级缓存。一级缓存 <code>singletonObjects</code> 存完整 Bean；二级 <code>earlySingletonObjects</code> 存提前暴露的半成品；三级 <code>singletonFactories</code> 存 ObjectFactory 工厂。</p>'+
'<p>流程上：创建 A 时先实例化（空对象）并把 ObjectFactory 放入三级缓存 → 填充属性发现依赖 B → 创建 B → B 填充属性发现依赖 A → 从三级缓存调用 getObject() 拿到 A 的早期引用（若需 AOP 则在此生成代理），放入二级缓存并清三级 → B 完成放入一级 → A 拿到 B 完成初始化放入一级。</p>'+
'<p>三级缓存的核心价值是<strong>延迟代理决策</strong>，保证循环依赖场景下拿到的是同一个代理对象。</p>')+

QA(2, "构造器注入的循环依赖为什么解决不了？",
'<p>因为构造器注入要求<strong>在实例化阶段就必须拿到完整依赖</strong>。而三级缓存的解法前提是「先创建空对象、提前暴露引用、再填充属性」——构造器注入下，连 new 都完成不了，自然无法提前暴露半成品引用，所以只能抛 <code>BeanCurrentlyInCreationException</code>。</p>'+
'<p>这也是 Spring 官方推荐构造器注入、但遇到循环依赖需要改用 setter/字段注入或 @Lazy 的原因。</p>')+

QA(3, "如何用代码验证三级缓存？",
'<p>在 <code>DefaultSingletonBeanRegistry</code> 的 <code>getSingleton(String, boolean)</code> 方法打断点，观察 <code>this.singletonObjects</code>、<code>this.earlySingletonObjects</code>、<code>this.singletonFactories</code> 三个 Map 的 size 变化。</p>'+
'<p>或者在 A、B 的构造器与 setter 上打断点，观察调用顺序：会看到 A 构造 → B 构造 → B 的 setter(注入 A 早期引用) → A 的 setter(注入 B)。</p>')
    },
    {
      id: "s2-3",
      title: "2.3 Bean 作用域与线程安全",
      html:
'<div class="tbl-wrap"><table><thead><tr><th>作用域</th><th>说明</th><th>创建时机</th></tr></thead><tbody>'+
'<tr><td><code>singleton</code>（默认）</td><td>容器内唯一实例</td><td>启动时（可 @Lazy 延迟）</td></tr>'+
'<tr><td><code>prototype</code></td><td>每次获取新建</td><td>每次 getBean</td></tr>'+
'<tr><td><code>request</code></td><td>每个 HTTP 请求一个实例</td><td>请求进入时</td></tr>'+
'<tr><td><code>session</code></td><td>每个会话一个实例</td><td>会话创建时</td></tr>'+
'<tr><td><code>application</code></td><td>ServletContext 级别</td><td>应用启动时</td></tr>'+
'<tr><td><code>websocket</code></td><td>每个 WebSocket 会话</td><td>连接建立时</td></tr>'+
'</tbody></table></div>'+

BOX("danger","经典陷阱：单例 Bean 注入多例 Bean 失效",
'<p>单例 Bean 只在创建时注入一次依赖，如果你注入了 prototype 的 Bean，那个 prototype 也会变成「事实上的单例」。</p>'+
'<p><strong>解法一</strong>：用 <code>@Lookup</code> 注解（Spring 用 CGLIB 生成子类动态获取）。</p>'+
'<p><strong>解法二</strong>：注入 <code>ObjectProvider&lt;Foo&gt;</code>，每次 <code>getObject()</code> 拿新实例。</p>'+
'<p><strong>解法三</strong>：实现 <code>ApplicationContextAware</code>，每次 <code>context.getBean(Foo.class)</code>。</p>','⚠️')+
PRE("java",
`@Component
public class SingletonBean {

    // 方式一：@Lookup（推荐，无侵入）
    @Lookup
    protected PrototypeBean getPrototypeBean() {
        return null;   // 方法体会被 CGLIB 覆盖
    }

    // 方式二：ObjectProvider（Spring 4.3+）
    @Autowired
    private ObjectProvider<PrototypeBean> provider;

    public void doSomething() {
        PrototypeBean pb1 = getPrototypeBean();
        PrototypeBean pb2 = provider.getObject();
        // pb1 != pb2，都是新实例
    }
}`)+

'<h4>单例 Bean 的线程安全问题</h4>'+
'<p>这是高频场景题。<strong>Spring 单例 Bean 本身不保证线程安全</strong>——它只是保证只有一个实例，但多个线程同时访问这个实例时，如果存在可变成员变量，就会有并发问题。</p>'+
'<div class="cols">'+
'<div class="col bad"><h5>❌ 有状态的 Bean（危险）</h5>'+
PRE("java",
`@Service
public class OrderService {
    // 可变成员变量 → 线程不安全！
    private int counter = 0;

    public void create() {
        counter++;      // 并发下会丢数据
        // ...
    }
}`)+
'</div>'+
'<div class="col good"><h5>✅ 无状态的 Bean（安全）</h5>'+
PRE("java",
`@Service
public class OrderService {
    // 只有依赖注入，无可变状态
    private final OrderMapper mapper;

    public OrderService(OrderMapper mapper) {
        this.mapper = mapper;
    }

    public void create(Order o) {
        mapper.insert(o);   // 方法内局部变量，安全
    }
}`)+
'</div></div>'+

BOX("tip","结论与最佳实践",
'<p><strong>Controller、Service、DAO 一律设计为无状态</strong>（只有 final 依赖，无可变成员变量），这是 Spring 应用线程安全的根本保障。如果确实需要状态，用 <code>ThreadLocal</code>（记得 remove 防泄漏）或改为 prototype 作用域。</p>','💡')+

'<h4>@Autowired 注入方式对比</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>方式</th><th>优点</th><th>缺点</th><th>推荐度</th></tr></thead><tbody>'+
'<tr><td>构造器注入</td><td>依赖不可变(final)、启动即暴露循环依赖、便于测试</td><td>依赖多时构造器参数长</td><td>⭐⭐⭐⭐⭐ 官方推荐</td></tr>'+
'<tr><td>Setter 注入</td><td>灵活，可重新注入</td><td>依赖可变，可能 NPE</td><td>⭐⭐⭐ 可选依赖场景</td></tr>'+
'<tr><td>字段注入</td><td>代码简洁</td><td>无法 final、测试困难、<strong>隐藏循环依赖</strong></td><td>⭐⭐ 不推荐</td></tr>'+
'</tbody></table></div>'+

BOX("tip","Spring 官方的一句话",
'<p>「Since you can mix constructor-based and setter-based DI, it is a good rule of thumb to use constructors for <strong>mandatory dependencies</strong> and setter methods or configuration methods for <strong>optional dependencies</strong>.」——构造函数用于必需依赖，setter 用于可选依赖。字段注入是官方明确不推荐的。</p>','💡')
    },
    {
      id: "s2-4",
      title: "2.4 手写 IoC 容器（理解本质的最佳方式）",
      html:
'<p>想彻底搞懂 IoC，最好的办法是自己实现一个最简版。下面这个 60 行的容器，能让你真正理解 <code>getBean</code> 在做什么。</p>'+

PRE("java",
`/**
 * 极简 IoC 容器：支持 @Component 扫描、@Autowired 注入、单例缓存
 */
public class MiniApplicationContext {

    // 一级缓存：单例池
    private final Map<String, Object> singletonObjects = new HashMap<>();

    // 类名 -> Class
    private final Map<String, Class<?>> classMap = new HashMap<>();

    private final String basePackage;

    public MiniApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    /** 启动容器 */
    public void refresh() throws Exception {
        // 1. 扫描指定包下所有类，找出带 @Component 的
        scanPackage(basePackage);
        // 2. 实例化所有 Bean（简化版，按类名排序保证顺序稳定）
        for (Class<?> clazz : classMap.values()) {
            getBean(clazz.getName());
        }
    }

    /** 扫描包（简化：只扫指定包，不含子包） */
    private void scanPackage(String pkg) throws Exception {
        String path = pkg.replace('.', '/');
        URL url = getClass().getClassLoader().getResource(path);
        if (url == null) return;
        File dir = new File(url.toURI());
        for (File f : dir.listFiles()) {
            if (f.getName().endsWith(".class")) {
                String className = pkg + "." + f.getName().replace(".class", "");
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Component.class)) {
                    classMap.put(className, clazz);
                }
            }
        }
    }

    /** 核心：获取或创建 Bean */
    @SuppressWarnings("unchecked")
    public <T> T getBean(String className) throws Exception {
        // 1. 单例缓存命中，直接返回
        if (singletonObjects.containsKey(className)) {
            return (T) singletonObjects.get(className);
        }
        Class<?> clazz = classMap.get(className);
        if (clazz == null) throw new RuntimeException("未找到 Bean: " + className);

        // 2. 实例化（简化：无参构造）
        Object instance = clazz.getDeclaredConstructor().newInstance();
        // 3. 提前放入缓存（模拟三级缓存的思路，解决循环依赖）
        singletonObjects.put(className, instance);

        // 4. 属性注入
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                Class<?> fieldType = field.getType();
                Object dependency = getBean(fieldType.getName());
                field.setAccessible(true);
                field.set(instance, dependency);   // 反射注入
            }
        }
        return (T) instance;
    }
}`)+

BOX("ok","从这个 60 行代码你能悟到什么",
'<p>① <strong>容器本质就是个 Map</strong>（单例池），这跟 <code>singletonObjects</code> 完全对应。</p>'+
'<p>② <strong>提前放入缓存</strong>这一步，正是三级缓存解决循环依赖的核心思想——先占坑，再补依赖。</p>'+
'<p>③ <strong>反射是 IoC 的技术底座</strong>，没有反射就没有依赖注入。</p>'+
'<p>④ 真实 Spring 之所以复杂，是因为它还要处理：BeanDefinition 解析、各种扩展点、AOP 代理、作用域、懒加载、生命周期回调。但骨架就是上面这些。</p>','✅')+

QA(1, "Bean 的生命周期说一下？",
'<p>十二步：① BeanDefinition 注册；② BeanFactoryPostProcessor 处理；③ 实例化前 BPP 回调；④ <strong>实例化（构造器注入在此）</strong>；⑤ 实例化后 + 属性填充（字段注入在此）；⑥ Aware 接口回调（BeanNameAware/BeanFactoryAware/ApplicationContextAware 等）；⑦ BPP 前置处理（@PostConstruct 触发）；⑧ afterPropertiesSet + init-method；⑨ <strong>BPP 后置处理（AOP 代理生成）</strong>；⑩ 注册销毁回调；⑪ 使用；⑫ 销毁（@PreDestroy → destroy → destroy-method）。</p>'+
'<p>关键提醒：AOP 代理在第 ⑨ 步才生成，所以在 @PostConstruct 中调用本类的 @Transactional 方法不会生效。</p>')+

QA(2, "单例 Bean 是线程安全的吗？",
'<p><strong>不是</strong>。Spring 只保证单例 Bean 只有一个实例，不负责它的线程安全。</p>'+
'<p>是否安全取决于 Bean 有没有可变状态：如果只有 final 的依赖注入、方法内使用局部变量，就是安全的（无状态）；如果有可变的成员变量（如 <code>private int counter</code>），并发访问就会出问题。</p>'+
'<p>实践规范：Controller/Service/DAO 一律设计为无状态。需要请求级别的状态就用 <code>ThreadLocal</code>（务必在 finally 中 remove，防止线程池复用导致内存泄漏/数据串号）。</p>')
    }
  ]
});

/* ============================================================
   DAY 3：Web 开发全链路
   ============================================================ */
CHAPTERS.push({
  id: "day3",
  badge: "D3",
  badgeLabel: "Day 3",
  badgeType: "",
  title: "Web 开发全链路 · 从 RESTful 设计到统一治理",
  intro: "今天从「能写接口」升级到「写生产级接口」。包含 RESTful 设计规范、参数校验、全局异常处理、统一响应、拦截器实战。这些是大厂代码规约的硬性要求，也是面试「你们项目接口怎么设计的」的答题素材。",
  sections: [
    {
      id: "s3-1",
      title: "3.1 RESTful 接口设计规范",
      html:
'<p>大厂对接口设计有明确规约。面试时能说出规范的细节，比只会 <code>@GetMapping</code> 加分很多。</p>'+

'<h4>URL 设计原则</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>原则</th><th>正确示例</th><th>错误示例</th></tr></thead><tbody>'+
'<tr><td>用名词复数表示资源</td><td><code>GET /api/v1/orders</code></td><td><code>GET /api/v1/getOrderList</code></td></tr>'+
'<tr><td>用 HTTP 方法表达动作</td><td><code>DELETE /orders/1</code></td><td><code>POST /orders/delete?id=1</code></td></tr>'+
'<tr><td>层级用路径表达</td><td><code>GET /users/1/orders</code></td><td><code>GET /getOrdersByUserId?userId=1</code></td></tr>'+
'<tr><td>过滤用查询参数</td><td><code>GET /orders?status=PAID&amp;page=1</code></td><td><code>GET /orders/paid/page/1</code></td></tr>'+
'<tr><td>小写 + 连字符</td><td><code>/order-items</code></td><td><code>/orderItems</code>、<code>/Order_Items</code></td></tr>'+
'<tr><td>版本号放路径</td><td><code>/api/v1/orders</code></td><td>无版本控制</td></tr>'+
'</tbody></table></div>'+

'<h4>HTTP 方法与状态码</h4>'+
'<div class="cols">'+
'<div class="col"><h5>方法语义（幂等性关键）</h5>'+
'<ul style="margin:0;font-size:13.5px">'+
'<li><code>GET</code> 查询 — 幂等</li>'+
'<li><code>POST</code> 新增 — <strong>非幂等</strong></li>'+
'<li><code>PUT</code> 全量更新 — 幂等</li>'+
'<li><code>PATCH</code> 局部更新 — 通常非幂等</li>'+
'<li><code>DELETE</code> 删除 — 幂等</li>'+
'</ul></div>'+
'<div class="col"><h5>常用状态码</h5>'+
'<ul style="margin:0;font-size:13.5px">'+
'<li><code>200</code> 成功 · <code>201</code> 创建成功</li>'+
'<li><code>400</code> 参数错误 · <code>401</code> 未认证</li>'+
'<li><code>403</code> 无权限 · <code>404</code> 资源不存在</li>'+
'<li><code>500</code> 服务端异常</li>'+
'</ul></div></div>'+

BOX("interview","幂等性是大厂高频追问",
'<p>面试官会问：「为什么 PUT 是幂等的，POST 不是？」</p>'+
'<p>答：幂等指<strong>同一请求执行多次，结果与执行一次相同</strong>。<code>PUT /orders/1 {status: PAID}</code> 执行 100 次，订单 1 的状态都是 PAID，所以幂等；<code>POST /orders {amount: 100}</code> 执行 100 次会创建 100 个订单，所以非幂等。</p>'+
'<p>延伸追问：「POST 如何做幂等？」→ 引入<strong>幂等号（requestId/业务唯一键）</strong>，服务端用 Redis 或唯一索引做去重。详见 Day 6。</p>','🎯')+

'<h4>统一响应结构（生产必备）</h4>'+
PRE("java",
`@Data
public class Result<T> implements Serializable {

    /** 业务码：0 成功，非 0 失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 链路追踪 ID，便于排障 */
    private String traceId;

    /** 时间戳 */
    private long timestamp = System.currentTimeMillis();

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(0);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}`)+

BOX("tip","为什么要有统一响应体",
'<p>① <strong>前端处理统一</strong>：只判断 <code>code</code>，不用管 HTTP 状态码散落各处；</p>'+
'<p>② <strong>便于埋点</strong>：可以在切面里统一统计接口成功率；</p>'+
'<p>③ <strong>兼容网关</strong>：网关层可以统一解析 code 做降级。</p>'+
'<p>注意：<strong>HTTP 状态码语义不能丢</strong>，业务失败时 HTTP 也应返回 200（因为服务本身是正常的），业务异常通过 code 区分；但参数校验失败等应返回 400。</p>','💡')
    },
    {
      id: "s3-2",
      title: "3.2 参数校验：JSR-303 完整实战",
      html:
'<p>手写 if 判断参数是大厂严令禁止的。必须用 Bean Validation 注解。</p>'+

'<h4>依赖（Boot 3.x）</h4>'+
PRE("xml",
`<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>`)+

'<h4>常用校验注解</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>注解</th><th>作用</th><th>示例</th></tr></thead><tbody>'+
'<tr><td><code>@NotNull</code></td><td>不能为 null（空字符串可以通过）</td><td>Long id</td></tr>'+
'<tr><td><code>@NotBlank</code></td><td>字符串不能为 null 且长度>0（去空格）</td><td>String name</td></tr>'+
'<tr><td><code>@NotEmpty</code></td><td>集合/数组/字符串非空</td><td>List&lt;String&gt;</td></tr>'+
'<tr><td><code>@Size(min,max)</code></td><td>长度范围</td><td>String / Collection</td></tr>'+
'<tr><td><code>@Min / @Max</code></td><td>数值范围</td><td>int age</td></tr>'+
'<tr><td><code>@DecimalMin/Max</code></td><td>BigDecimal 范围</td><td>金额</td></tr>'+
'<tr><td><code>@Pattern</code></td><td>正则匹配</td><td>手机号、邮箱</td></tr>'+
'<tr><td><code>@Email</code></td><td>邮箱格式</td><td>String email</td></tr>'+
'<tr><td><code>@Positive</code></td><td>正数</td><td>数量</td></tr>'+
'<tr><td><code>@Past / @Future</code></td><td>时间前后</td><td>LocalDate</td></tr>'+
'<tr><td><code>@Valid</code></td><td><strong>嵌套对象校验</strong>（加在对象属性上）</td><td>List&lt;@Valid Item&gt;</td></tr>'+
'</tbody></table></div>'+

'<h4>完整实战案例</h4>'+
PRE("java",
`@Data
public class OrderCreateReq {

    @NotBlank(message = "订单名称不能为空")
    @Size(max = 64, message = "订单名称不能超过64字符")
    private String orderName;

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须为正数")
    private Long userId;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    @Pattern(regexp = "^1[3-9]\\\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 嵌套校验：必须加 @Valid 才生效 */
    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<OrderItem> items;

    @Data
    public static class OrderItem {
        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @Min(value = 1, message = "数量至少为1")
        private Integer quantity;
    }
}`)+
PRE("java",
`@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @PostMapping
    public Result<Long> create(@RequestBody @Validated OrderCreateReq req) {
        // 走到这里说明参数已通过校验
        Long orderId = orderService.create(req);
        return Result.success(orderId);
    }

    // 分组校验：同一个 DTO 更新时 id 必填，新增时不需要
    public interface CreateGroup {}
    public interface UpdateGroup {}

    @Data
    public static class UserReq {
        @Null(groups = CreateGroup.class, message = "新增时不能传ID")
        @NotNull(groups = UpdateGroup.class, message = "修改时ID必填")
        private Long id;
    }

    @PostMapping("/group")
    public Result<Void> createWithGroup(
            @RequestBody @Validated(CreateGroup.class) UserReq req) {
        return Result.success(null);
    }
}`)+

BOX("danger","三个高频坑",
'<p><strong>坑 1：嵌套对象校验失效。</strong>如果 <code>items</code> 是个对象或集合，不加 <code>@Valid</code>，里面字段的校验注解<strong>完全不生效</strong>，且没有任何提示。这是最容易漏的。</p>'+
'<p><strong>坑 2：@Valid vs @Validated。</strong><code>@Valid</code> 是 JSR-303 标准注解，用在方法参数/属性上；<code>@Validated</code> 是 Spring 的，支持<strong>分组校验</strong>，用在类上可实现方法级校验。<strong>分组只能用 @Validated</strong>。</p>'+
'<p><strong>坑 3：方法级校验。</strong>要在 Service 方法参数上做校验，类上必须加 <code>@Validated</code>，否则 <code>@NotBlank</code> 等注解静默失效，抛的是 <code>ConstraintViolationException</code>（不是 <code>MethodArgumentNotValidException</code>），需要单独处理。</p>','⚠️')+

'<h4>全局异常处理（含校验异常）</h4>'+
PRE("java",
`@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 处理 @RequestBody 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, msg);
    }

    /** 处理方法级校验失败（@Validated 在类上） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(400, msg);
    }

    /** 处理 @RequestParam 类型转换失败 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(400, "参数类型错误: " + e.getName());
    }

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 兜底：未预期的异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);          // 必须打完整堆栈
        return Result.fail(500, "系统繁忙，请稍后重试");   // 不暴露内部细节
    }
}`)+

BOX("ok","生产级异常处理三条铁律",
'<p>① <strong>日志打全堆栈，返回给前端的信息要脱敏</strong>。绝不把 <code>e.getMessage()</code> 直接返回（可能暴露 SQL、表名、类名）。</p>'+
'<p>② <strong>必须配 <code>@ExceptionHandler(Exception.class)</code> 兜底</strong>。否则任何未处理异常都会返回 Spring 默认错误页（泄露堆栈信息）。</p>'+
'<p>③ <strong>业务异常与系统异常分离</strong>。业务异常（如余额不足）用 WARN 级别，系统异常用 ERROR 级别并接入告警。</p>','✅')
    },
    {
      id: "s3-3",
      title: "3.3 拦截器 / 过滤器 / AOP 的选择",
      html:
'<p>面试常问「拦截器和过滤器的区别，什么时候用哪个」。这是考察你对请求处理链路的理解深度。</p>'+

'<h4>三者的位置与能力对比</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>维度</th><th>Filter 过滤器</th><th>Interceptor 拦截器</th><th>AOP 切面</th></tr></thead><tbody>'+
'<tr><td>规范</td><td>Servlet 规范</td><td>Spring MVC 规范</td><td>Spring AOP</td></tr>'+
'<tr><td>作用位置</td><td>DispatcherServlet <strong>之前</strong></td><td>DispatcherServlet <strong>之内</strong>，Handler 前后</td><td>方法级别，更精细</td></tr>'+
'<tr><td>能否拿到 Handler 信息</td><td>❌ 不能</td><td>✅ 能（HandlerMethod，可读注解）</td><td>✅ 能（MethodSignature）</td></tr>'+
'<tr><td>能否拿到 Bean</td><td>只能通过 WebApplicationContext 拿</td><td>✅ 直接注入</td><td>✅ 直接注入</td></tr>'+
'<tr><td>典型用途</td><td>编码、跨域、请求包装、XSS 过滤</td><td>登录校验、权限、日志、耗时统计</td><td>事务、缓存、埋点、分布式锁</td></tr>'+
'<tr><td>执行顺序</td><td>最先</td><td>中间</td><td>最内层</td></tr>'+
'</tbody></table></div>'+

BOX("tip","一句话选择原则",
'<p><strong>需要包装/修改请求响应（如读取 body、改编码）用 Filter；需要知道「要执行哪个 Controller 方法」用 Interceptor；需要在方法前后做横切逻辑且能拿到参数返回值用 AOP。</strong></p>'+
'<p>实际项目中：登录鉴权用 Interceptor（因为要读 <code>@LoginRequired</code> 这类注解），操作日志用 AOP（因为要拿方法参数和返回值）。</p>','💡')+

'<h4>实战 1：登录鉴权拦截器</h4>'+
PRE("java",
`// 1. 自定义注解，标记需要登录
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {
    /** 需要的角色，为空表示只校验登录 */
    String[] roles() default {};
}

// 2. 拦截器实现
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String TOKEN_HEADER = "Authorization";

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // 非 Controller 方法（如静态资源）直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 方法或类上没有 @LoginRequired，放行
        LoginRequired anno = handlerMethod.getMethodAnnotation(LoginRequired.class);
        if (anno == null) {
            anno = handlerMethod.getBeanType().getAnnotation(LoginRequired.class);
        }
        if (anno == null) return true;

        // 校验 token
        String token = request.getHeader(TOKEN_HEADER);
        if (StringUtils.isBlank(token)) {
            writeUnauthorized(response, "未登录");
            return false;
        }
        UserContext user = tokenService.parse(token);
        if (user == null) {
            writeUnauthorized(response, "登录已过期");
            return false;
        }
        // 校验角色
        if (anno.roles().length > 0 && !user.hasAnyRole(anno.roles())) {
            writeForbidden(response);
            return false;
        }
        // 放入 ThreadLocal，供后续业务使用
        UserContextHolder.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理 ThreadLocal，防止线程池复用导致数据串号
        UserContextHolder.remove();
    }

    private void writeUnauthorized(HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\\"code\\":401,\\"message\\":\\"" + msg + "\\"}");
    }
    private void writeForbidden(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\\"code\\":403,\\"message\\":\\"无权限\\"}");
    }
}

// 3. 注册拦截器
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")           // 拦截范围
                .excludePathPatterns(                 // 放行路径
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/actuator/**",
                        "/error"
                );
    }
}`)+

BOX("danger","ThreadLocal 内存泄漏（大厂必问）",
'<p><code>UserContextHolder</code> 用 ThreadLocal 存用户信息，<strong>必须在 finally 或 afterCompletion 中 remove()</strong>。</p>'+
'<p>原因：Tomcat 用线程池，线程会被复用。如果不清理，下一个请求（可能是别的用户）会读到上一个用户的数据，造成<strong>越权访问</strong>；同时 ThreadLocal 的 key 是弱引用、value 是强引用，不清理会导致 value 无法回收，最终 OOM。</p>'+
'<p>进阶：阿里 TransmittableThreadLocal（TTL）用于线程池场景下传递上下文。</p>','⚠️')+

'<h4>实战 2：过滤器实现请求体重复读取</h4>'+
'<p>场景：需要在 Filter 中读取 <code>@RequestBody</code> 的内容（如做签名校验），但 <code>InputStream</code> 只能读一次，读完 Controller 就拿不到参数了。</p>'+
PRE("java",
`/**
 * 解决方案：用 HttpServletRequestWrapper 包装，把 body 缓存到 byte[]
 */
public class RepeatedlyReadRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    public RepeatedlyReadRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // 一次性读完并缓存
        this.body = StreamUtils.copyToByteArray(request.getInputStream());
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(),
                StandardCharsets.UTF_8));
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public int read() { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener l) { }
        };
    }

    /** 供业务方直接取原始 body 字符串 */
    public String getBodyString() {
        return new String(body, StandardCharsets.UTF_8);
    }
}`)+
PRE("java",
`@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SignFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 只包装 JSON 请求
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            RepeatedlyReadRequestWrapper wrapper = new RepeatedlyReadRequestWrapper(request);
            String body = wrapper.getBodyString();
            // 在这里做验签
            if (!checkSign(wrapper, body)) {
                response.setStatus(400);
                response.getWriter().write("{\\"code\\":400,\\"message\\":\\"签名校验失败\\"}");
                return;
            }
            chain.doFilter(wrapper, response);   // 传包装后的对象
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean checkSign(HttpServletRequest req, String body) {
        String sign = req.getHeader("X-Sign");
        // 实际用 HMAC-SHA256 等算法
        return sign != null && !sign.isEmpty();
    }
}`)+

BOX("ok","这个案例的面试价值",
'<p>「InputStream 只能读一次」是 Servlet 的经典问题。能说出用 <code>HttpServletRequestWrapper</code> 缓存请求体，说明你有真实的框架层问题解决经验，而不是只会写 CRUD。这个技巧在生产中用于：签名验签、请求日志、防重放攻击。</p>','✅')
    },
    {
      id: "s3-4",
      title: "3.4 跨域、文件上传与静态资源",
      html:
'<h4>跨域（CORS）三种解法</h4>'+
PRE("java",
`// 方式一：全局配置（推荐）
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")        // 注意：allowCredentials(true) 时不能用 "*"
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);                     // 预检请求缓存 1 小时
    }
}`)+
PRE("java",
`// 方式二：注解级别（精确控制）
@RestController
public class DemoController {

    @CrossOrigin(origins = "https://a.example.com", maxAge = 3600)
    @GetMapping("/api/data")
    public Result<String> data() {
        return Result.success("ok");
    }
}`)+
PRE("yaml",
`# 方式三：配置在网关层（生产推荐，避免每个服务都配）`)+

BOX("warn","跨域的两个关键认知",
'<p><strong>① 跨域是浏览器行为，不是服务端行为。</strong>Postman、curl 不受同源策略限制，所以「本地 Postman 能通，浏览器不行」是这个原因。</p>'+
'<p><strong>② 复杂请求会先发 OPTIONS 预检。</strong>带自定义 Header、<code>Content-Type: application/json</code> 的请求会先发 OPTIONS。<strong>如果网关/安全框架拦截了 OPTIONS 请求，跨域就会失败</strong>——这是生产排障的经典坑。要在网关放行 OPTIONS。</p>','⚠️')+

'<h4>文件上传（含大小限制）</h4>'+
PRE("yaml",
`spring:
  servlet:
    multipart:
      max-file-size: 10MB          # 单文件大小限制（默认 1MB）
      max-request-size: 50MB       # 整个请求大小限制
      file-size-threshold: 1MB     # 超过此大小写入临时磁盘`)+
PRE("java",
`@PostMapping("/upload")
public Result<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) {
        return Result.fail(400, "文件不能为空");
    }
    // 1. 校验文件类型（不要只信原始文件名）
    String originalName = file.getOriginalFilename();
    String ext = StringUtils.getFilenameExtension(originalName);
    if (!Set.of("jpg", "jpeg", "png", "pdf").contains(ext.toLowerCase())) {
        return Result.fail(400, "不支持的文件类型");
    }
    // 2. 校验魔术字节（防止改扩展名绕过）
    String realType = detectRealType(file.getInputStream());
    // 3. 生成随机文件名，防路径穿越
    String fileName = UUID.randomUUID() + "." + ext;
    Path target = Paths.get("/data/upload").resolve(fileName).normalize();
    if (!target.startsWith("/data/upload")) {          // 防 ../../ 攻击
        return Result.fail(400, "非法路径");
    }
    Files.createDirectories(target.getParent());
    file.transferTo(target.toFile());
    return Result.success(fileName);
}`)+

BOX("danger","文件上传安全三要点",
'<p><strong>① 必须校验文件类型</strong>：不能只校验扩展名（可伪造），要读文件头魔术字节。</p>'+
'<p><strong>② 必须防路径穿越</strong>：原始文件名里的 <code>../../etc/passwd</code> 会导致写入任意路径。要么用 UUID 重命名，要么用 <code>normalize()</code> + <code>startsWith</code> 校验。</p>'+
'<p><strong>③ 必须限制大小</strong>：默认只有 1MB，不配会导致大文件上传直接失败（报 <code>MaxUploadSizeExceededException</code>）。</p>'+
'<p><strong>④ 上传目录不放在 classpath 内</strong>，否则重新部署文件丢失，且可能被当作静态资源访问（上传木马）。</p>','⚠️')+

QA(1, "拦截器和过滤器的区别？什么时候用哪个？",
'<p>核心区别在<strong>作用位置和信息可见度</strong>：</p>'+
'<p>Filter 是 Servlet 规范，在 DispatcherServlet <strong>之前</strong>执行，无法知道最终会执行哪个 Controller 方法，也不能直接注入 Spring Bean（需通过 WebApplicationContext）。适合做编码设置、跨域、请求包装、XSS 过滤。</p>'+
'<p>Interceptor 是 Spring MVC 规范，在 DispatcherServlet <strong>之内</strong>、Handler 前后执行，能拿到 <code>HandlerMethod</code>（可读取方法/类上的注解），能直接依赖注入。适合做登录鉴权、权限校验、耗时统计。</p>'+
'<p>AOP 作用在方法级别，能拿到方法参数和返回值，适合事务、缓存、埋点、分布式锁。</p>'+
'<p>选择原则：需要包装请求响应用 Filter；需要知道目标方法信息的横切逻辑用 Interceptor；需要方法参数/返回值的用 AOP。</p>')+

QA(2, "统一异常处理怎么做？为什么要兜底？",
'<p>用 <code>@RestControllerAdvice</code> + <code>@ExceptionHandler</code>。要点：</p>'+
'<p>① 分类处理：参数校验异常（<code>MethodArgumentNotValidException</code>、<code>ConstraintViolationException</code>）返回 400；业务异常返回对应业务码；未预期异常返回 500。</p>'+
'<p>② <strong>必须有 <code>@ExceptionHandler(Exception.class)</code> 兜底</strong>，否则未捕获异常会走 Spring 默认错误页，<strong>把堆栈信息暴露给前端</strong>，这是严重的安全隐患。</p>'+
'<p>③ 日志打完整堆栈（ERROR 级别），但返回给前端的消息要脱敏，不能直接返回 <code>e.getMessage()</code>。</p>'+
'<p>④ 可以结合 <code>@Order</code> 控制多个 Advice 的优先级，或继承 <code>ResponseEntityExceptionHandler</code> 复用 Spring 默认处理。</p>')
    }
  ]
});


/* ============================================================
   DAY 4：数据访问与事务
   ============================================================ */
CHAPTERS.push({
  "id": "day4",
  "badge": "D4",
  "badgeLabel": "Day 4",
  "badgeType": "",
  "title": "数据访问与事务 · 从 CRUD 到事务失效排查",
  "intro": "数据层是面试重灾区：事务传播行为、事务失效的 8 种场景、连接池调优、多数据源、分页插件深坑。结合你 12 年 Oracle / OceanBase / GoldenDB 迁移经验，这块是你的主场，要答得比别人深。",
  "sections": [
    {
      "id": "s4-1",
      "title": "4.1 Spring 事务：传播行为与隔离级别",
      "html": "<h4>事务传播行为（7 种，面试必问前 4 种）</h4><div class=\"tbl-wrap\"><table><thead><tr><th>传播行为</th><th>含义</th><th>典型场景</th></tr></thead><tbody><tr><td><code>REQUIRED</code>（默认）</td><td>有事务就加入，没有就新建</td><td>99% 的业务方法</td></tr><tr><td><code>REQUIRES_NEW</code></td><td><strong>挂起当前事务</strong>，新建独立事务</td><td>记流水日志：业务失败但日志必须留下</td></tr><tr><td><code>NESTED</code></td><td>嵌套事务（Savepoint 实现），子事务回滚不影响父</td><td>批量处理，部分失败不回滚全部</td></tr><tr><td><code>SUPPORTS</code></td><td>有就用，没有就以非事务方式执行</td><td>查询方法</td></tr><tr><td><code>NOT_SUPPORTED</code></td><td>挂起当前事务，以非事务方式执行</td><td>某些不能有事务的操作</td></tr><tr><td><code>MANDATORY</code></td><td>必须存在事务，否则抛异常</td><td>强制要求上层开启事务</td></tr><tr><td><code>NEVER</code></td><td>必须不存在事务，否则抛异常</td><td>极少用</td></tr></tbody></table></div><div class=\"box interview\"><div class=\"box-t\">🎯 REQUIRES_NEW 与 NESTED 的关键区别</div><p><strong>REQUIRES_NEW</strong>：会<strong>挂起</strong>外层事务（外层连接归还但事务未提交），用<strong>新的数据库连接</strong>开新事务。外层回滚<strong>不影响</strong>内层。</p><p><strong>NESTED</strong>：不挂起，用<strong>同一个连接</strong>，通过 JDBC Savepoint 实现。内层回滚只回滚到 Savepoint，外层回滚则内层也回滚。</p><p>结论：需要「无论如何都提交」用 REQUIRES_NEW；需要「部分失败不回滚全部」用 NESTED。注意 NESTED 只支持 <code>DataSourceTransactionManager</code>（JDBC），JTA 不支持。</p></div><h4>@Transactional 关键属性</h4><pre data-lang=\"java\" data-code=\"%40Transactional(%0A%20%20%20%20%2F%2F%201.%20%E4%BC%A0%E6%92%AD%E8%A1%8C%E4%B8%BA%0A%20%20%20%20propagation%20%3D%20Propagation.REQUIRED%2C%0A%20%20%20%20%2F%2F%202.%20%E9%9A%94%E7%A6%BB%E7%BA%A7%E5%88%AB%EF%BC%88%E9%BB%98%E8%AE%A4%E7%94%A8%E6%95%B0%E6%8D%AE%E5%BA%93%E7%9A%84%EF%BC%89%0A%20%20%20%20isolation%20%3D%20Isolation.REPEATABLE_READ%2C%0A%20%20%20%20%2F%2F%203.%20%E8%B6%85%E6%97%B6%EF%BC%88%E7%A7%92%EF%BC%89%EF%BC%8C%E8%B6%85%E6%97%B6%E8%87%AA%E5%8A%A8%E5%9B%9E%E6%BB%9A%0A%20%20%20%20timeout%20%3D%2030%2C%0A%20%20%20%20%2F%2F%204.%20%E5%8F%AA%E8%AF%BB%E4%BC%98%E5%8C%96%EF%BC%88MySQL%20%E4%BC%9A%E5%81%9A%E4%BC%98%E5%8C%96%E6%8F%90%E7%A4%BA%EF%BC%89%0A%20%20%20%20readOnly%20%3D%20false%2C%0A%20%20%20%20%2F%2F%205.%20%E9%81%87%E5%88%B0%E5%93%AA%E4%BA%9B%E5%BC%82%E5%B8%B8%E5%9B%9E%E6%BB%9A%EF%BC%88%E9%BB%98%E8%AE%A4%20RuntimeException%20%2B%20Error%EF%BC%89%0A%20%20%20%20rollbackFor%20%3D%20Exception.class%2C%0A%20%20%20%20%2F%2F%206.%20%E9%81%87%E5%88%B0%E5%93%AA%E4%BA%9B%E5%BC%82%E5%B8%B8%E4%B8%8D%E5%9B%9E%E6%BB%9A%0A%20%20%20%20noRollbackFor%20%3D%20BizNotRollbackException.class%0A)%0Apublic%20void%20doBusiness()%20%7B%20%7D\"></pre><div class=\"box danger\"><div class=\"box-t\">⚠️ 最容易踩的坑：默认不回滚受检异常</div><p><code>@Transactional</code> <strong>默认只在遇到 <code>RuntimeException</code> 和 <code>Error</code> 时回滚</strong>。如果你抛的是 <code>Exception</code>（受检异常，如 <code>IOException</code>），<strong>事务不会回滚</strong>，数据已经写进去了！</p><p>生产规范：<strong>永远显式写 <code>rollbackFor = Exception.class</code></strong>。这一条是大厂代码规约的强制要求。</p></div><h4>try-catch 吞掉异常也会导致不回滚</h4><pre data-lang=\"java\" data-code=\"%2F%2F%20%E9%94%99%E8%AF%AF%E5%86%99%E6%B3%95%EF%BC%9A%E5%BC%82%E5%B8%B8%E8%A2%AB%E5%90%9E%E4%BA%86%EF%BC%8CSpring%20%E6%84%9F%E7%9F%A5%E4%B8%8D%E5%88%B0%EF%BC%8C%E4%BA%8B%E5%8A%A1%E7%85%A7%E6%A0%B7%E6%8F%90%E4%BA%A4%0A%40Transactional%0Apublic%20void%20wrong()%20%7B%0A%20%20%20%20try%20%7B%0A%20%20%20%20%20%20%20%20orderMapper.insert(order)%3B%0A%20%20%20%20%20%20%20%20throw%20new%20RuntimeException(%22%E6%A8%A1%E6%8B%9F%E5%BC%82%E5%B8%B8%22)%3B%0A%20%20%20%20%7D%20catch%20(Exception%20e)%20%7B%0A%20%20%20%20%20%20%20%20log.error(%22%E5%87%BA%E9%94%99%E4%BA%86%22%2C%20e)%3B%20%20%20%20%20%20%2F%2F%20%E5%90%9E%E6%8E%89%E5%BC%82%E5%B8%B8%20-%3E%20%E4%BA%8B%E5%8A%A1%E4%B8%8D%E5%9B%9E%E6%BB%9A%EF%BC%81%0A%20%20%20%20%7D%0A%7D%0A%0A%2F%2F%20%E6%AD%A3%E7%A1%AE%E5%86%99%E6%B3%95%E4%B8%80%EF%BC%9A%E4%B8%8D%E5%90%9E%EF%BC%8C%E8%AE%A9%E5%AE%83%E6%8A%9B%E5%87%BA%E5%8E%BB%0A%40Transactional(rollbackFor%20%3D%20Exception.class)%0Apublic%20void%20correct1()%20%7B%0A%20%20%20%20orderMapper.insert(order)%3B%0A%20%20%20%20throw%20new%20BizException(%22%E4%B8%9A%E5%8A%A1%E5%A4%B1%E8%B4%A5%22)%3B%0A%7D%0A%0A%2F%2F%20%E6%AD%A3%E7%A1%AE%E5%86%99%E6%B3%95%E4%BA%8C%EF%BC%9A%E7%A1%AE%E5%AE%9E%E8%A6%81%E5%A4%84%E7%90%86%EF%BC%8C%E5%B0%B1%E6%89%8B%E5%8A%A8%E6%A0%87%E8%AE%B0%E5%9B%9E%E6%BB%9A%0A%40Transactional(rollbackFor%20%3D%20Exception.class)%0Apublic%20void%20correct2()%20%7B%0A%20%20%20%20try%20%7B%0A%20%20%20%20%20%20%20%20orderMapper.insert(order)%3B%0A%20%20%20%20%20%20%20%20%2F%2F%20...%0A%20%20%20%20%7D%20catch%20(Exception%20e)%20%7B%0A%20%20%20%20%20%20%20%20log.error(%22%E5%87%BA%E9%94%99%E4%BA%86%22%2C%20e)%3B%0A%20%20%20%20%20%20%20%20TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()%3B%20%20%2F%2F%20%E6%89%8B%E5%8A%A8%E6%A0%87%E8%AE%B0%0A%20%20%20%20%7D%0A%7D\"></pre>"
    },
    {
      "id": "s4-2",
      "title": "4.2 事务失效的 8 大场景（面试必答）",
      "html": "<p>这道题是「懂没懂」的分水岭。我按出现频率排序。</p><div class=\"tbl-wrap\"><table><thead><tr><th>#</th><th>失效场景</th><th>原因</th><th>解法</th></tr></thead><tbody><tr><td>1</td><td><strong>自调用</strong>（同类内方法互调）</td><td>this 调用不走代理</td><td>拆 Bean / 注入 self / AopContext</td></tr><tr><td>2</td><td>方法非 <strong>public</strong></td><td>代理只增强 public 方法</td><td>改为 public</td></tr><tr><td>3</td><td>异常被 <strong>try-catch 吞掉</strong></td><td>Spring 感知不到异常</td><td>抛出或手动 setRollbackOnly</td></tr><tr><td>4</td><td>抛的是<strong>受检异常</strong>未配 rollbackFor</td><td>默认只回滚 RuntimeException</td><td>加 <code>rollbackFor=Exception.class</code></td></tr><tr><td>5</td><td>类未被 <strong>Spring 管理</strong></td><td>没有代理对象</td><td>加 @Service 等注解</td></tr><tr><td>6</td><td>数据库引擎不支持事务</td><td>MyISAM 不支持</td><td>改用 InnoDB</td></tr><tr><td>7</td><td>方法被 <strong>final / static</strong> 修饰</td><td>CGLIB 无法覆盖</td><td>去掉修饰符</td></tr><tr><td>8</td><td>多线程调用</td><td>事务是 ThreadLocal 绑定，子线程不共享</td><td>子线程内自己开事务</td></tr></tbody></table></div><h4>场景 8 详解：多线程下事务失效</h4><pre data-lang=\"java\" data-code=\"%40Transactional(rollbackFor%20%3D%20Exception.class)%0Apublic%20void%20wrongAsync()%20%7B%0A%20%20%20%20orderMapper.insert(order)%3B%0A%20%20%20%20%2F%2F%20%E6%96%B0%E7%BA%BF%E7%A8%8B%E9%87%8C%E7%9A%84%E4%BA%8B%E5%8A%A1%E6%98%AF%E7%8B%AC%E7%AB%8B%E7%9A%84%EF%BC%8C%E4%B8%BB%E7%BA%BF%E7%A8%8B%E5%9B%9E%E6%BB%9A%E4%B8%8D%E4%BC%9A%E5%BD%B1%E5%93%8D%E5%AE%83%0A%20%20%20%20new%20Thread(()%20-%3E%20%7B%0A%20%20%20%20%20%20%20%20logMapper.insert(logEntry)%3B%20%20%20%20%20%20%2F%2F%20%E4%B8%8D%E5%8F%97%E4%B8%BB%E4%BA%8B%E5%8A%A1%E6%8E%A7%E5%88%B6%0A%20%20%20%20%7D).start()%3B%0A%20%20%20%20throw%20new%20BizException(%22%E5%A4%B1%E8%B4%A5%22)%3B%20%20%20%20%20%20%20%2F%2F%20%E4%B8%BB%E4%BA%8B%E5%8A%A1%E5%9B%9E%E6%BB%9A%EF%BC%8C%E4%BD%86%E6%97%A5%E5%BF%97%E5%B7%B2%E5%86%99%E5%85%A5%0A%7D\"></pre><h4>场景 1 的三种解法（代码）</h4><pre data-lang=\"java\" data-code=\"%40Service%0Apublic%20class%20OrderService%20%7B%0A%0A%20%20%20%20private%20final%20OrderService%20self%3B%20%20%20%20%20%20%2F%2F%20%E8%A7%A3%E6%B3%95%E4%BA%8C%EF%BC%9A%E6%B3%A8%E5%85%A5%E8%87%AA%E5%B7%B1%0A%20%20%20%20private%20final%20LogService%20logService%3B%20%20%2F%2F%20%E8%A7%A3%E6%B3%95%E4%B8%80%EF%BC%9A%E6%8B%86%20Bean%0A%0A%20%20%20%20public%20OrderService(OrderService%20self%2C%20LogService%20logService)%20%7B%0A%20%20%20%20%20%20%20%20this.self%20%3D%20self%3B%0A%20%20%20%20%20%20%20%20this.logService%20%3D%20logService%3B%0A%20%20%20%20%7D%0A%0A%20%20%20%20public%20void%20outer()%20%7B%0A%20%20%20%20%20%20%20%20%2F%2F%20%E8%A7%A3%E6%B3%95%E4%B8%80%EF%BC%9A%E8%B0%83%E7%94%A8%E5%8F%A6%E4%B8%80%E4%B8%AA%20Bean%20%E7%9A%84%E6%96%B9%E6%B3%95%0A%20%20%20%20%20%20%20%20logService.saveLog()%3B%0A%0A%20%20%20%20%20%20%20%20%2F%2F%20%E8%A7%A3%E6%B3%95%E4%BA%8C%EF%BC%9A%E9%80%9A%E8%BF%87%E4%BB%A3%E7%90%86%E5%AF%B9%E8%B1%A1%E8%B0%83%E7%94%A8%EF%BC%88Spring%204.3%2B%20%E6%94%AF%E6%8C%81%E8%87%AA%E6%B3%A8%E5%85%A5%EF%BC%89%0A%20%20%20%20%20%20%20%20self.inner()%3B%0A%0A%20%20%20%20%20%20%20%20%2F%2F%20%E8%A7%A3%E6%B3%95%E4%B8%89%EF%BC%9AAopContext%EF%BC%88%E9%9C%80%20%40EnableAspectJAutoProxy(exposeProxy%3Dtrue)%EF%BC%89%0A%20%20%20%20%20%20%20%20%2F%2F%20((OrderService)%20AopContext.currentProxy()).inner()%3B%0A%20%20%20%20%7D%0A%0A%20%20%20%20%40Transactional(rollbackFor%20%3D%20Exception.class)%0A%20%20%20%20public%20void%20inner()%20%7B%0A%20%20%20%20%20%20%20%20%2F%2F%20%E4%BA%8B%E5%8A%A1%E7%94%9F%E6%95%88%0A%20%20%20%20%7D%0A%7D\"></pre><div class=\"box ok\"><div class=\"box-t\">✅ 面试答题模板（背这段）</div><p>「事务失效的根本原因是 <strong>Spring 事务基于 AOP 代理</strong>，凡是导致增强逻辑无法执行、或异常无法传播到事务拦截器的情况，都会失效。主要分三类：</p><p><strong>一类是代理没生效</strong>：自调用（this 调用不走代理）、方法非 public、方法 final/static、类没被 Spring 管理、多线程调用。</p><p><strong>二类是异常没被感知</strong>：try-catch 吞掉异常、抛出的是受检异常但没配 rollbackFor（默认只回滚 RuntimeException）。</p><p><strong>三类是底层不支持</strong>：数据库引擎是 MyISAM 不支持事务。</p><p>生产规范上，我们团队要求所有 <code>@Transactional</code> 都必须显式写 <code>rollbackFor = Exception.class</code>，且禁止在事务方法里写 try-catch 吞异常。」</p></div>"
    },
    {
      "id": "s4-3",
      "title": "4.3 MyBatis / MyBatis-Plus 实战要点",
      "html": "<h4>井号占位符与美元占位符的区别（高频八股）</h4><div class=\"tbl-wrap\"><table><thead><tr><th>对比</th><th>#{}（预编译）</th><th>${}（拼接）</th></tr></thead><tbody><tr><td>原理</td><td>预编译占位符，生成 <code>?</code>，用 PreparedStatement 参数绑定</td><td>字符串直接拼接进 SQL</td></tr><tr><td>SQL 注入</td><td>安全</td><td><strong>有注入风险</strong></td></tr><tr><td>适用场景</td><td>所有参数传值</td><td>仅用于动态表名/列名/排序字段</td></tr><tr><td>示例</td><td><code>where id = #{id}</code> 变成 <code>where id = ?</code></td><td><code>order by ${column}</code> 变成 <code>order by name</code></td></tr></tbody></table></div><div class=\"box danger\"><div class=\"box-t\">⚠️ 美元占位符的安全用法</div><p>当必须用 <code>${}</code>（如动态排序字段）时，<strong>必须在 Java 层做白名单校验</strong>：</p><pre style=\"margin:8px 0\"><code>private static final Set&lt;String&gt; ALLOWED_SORT = Set.of(\"create_time\", \"amount\", \"id\");<br><br>if (!ALLOWED_SORT.contains(sortField)) {<br>    throw new BizException(\"非法排序字段\");<br>}</code></pre></div><h4>MyBatis-Plus 常用配置与插件</h4><pre data-lang=\"java\" data-code=\"%40Configuration%0A%40MapperScan(%22com.example.**.mapper%22)%20%20%20%20%20%20%20%20%2F%2F%20%E6%8C%87%E5%AE%9A%20Mapper%20%E6%89%AB%E6%8F%8F%E8%B7%AF%E5%BE%84%0Apublic%20class%20MybatisPlusConfig%20%7B%0A%0A%20%20%20%20%2F**%20%E5%88%86%E9%A1%B5%E6%8F%92%E4%BB%B6%EF%BC%88%E5%BF%85%E9%A1%BB%E9%85%8D%EF%BC%8C%E5%90%A6%E5%88%99%20Page%20%E6%9F%A5%E8%AF%A2%E4%B8%8D%E7%94%9F%E6%95%88%EF%BC%8C%E8%BF%94%E5%9B%9E%E5%85%A8%E9%87%8F%E6%95%B0%E6%8D%AE%EF%BC%89%20*%2F%0A%20%20%20%20%40Bean%0A%20%20%20%20public%20MybatisPlusInterceptor%20mybatisPlusInterceptor()%20%7B%0A%20%20%20%20%20%20%20%20MybatisPlusInterceptor%20interceptor%20%3D%20new%20MybatisPlusInterceptor()%3B%0A%20%20%20%20%20%20%20%20%2F%2F%20%E5%88%86%E9%A1%B5%E6%8F%92%E4%BB%B6%0A%20%20%20%20%20%20%20%20interceptor.addInnerInterceptor(new%20PaginationInnerInterceptor(DbType.MYSQL))%3B%0A%20%20%20%20%20%20%20%20%2F%2F%20%E4%B9%90%E8%A7%82%E9%94%81%E6%8F%92%E4%BB%B6%0A%20%20%20%20%20%20%20%20interceptor.addInnerInterceptor(new%20OptimisticLockerInnerInterceptor())%3B%0A%20%20%20%20%20%20%20%20%2F%2F%20%E9%98%B2%E6%AD%A2%E5%85%A8%E8%A1%A8%E6%9B%B4%E6%96%B0%E4%B8%8E%E5%88%A0%E9%99%A4%EF%BC%88%E7%94%9F%E4%BA%A7%E5%BF%85%E9%A1%BB%E5%8A%A0%EF%BC%81%EF%BC%89%0A%20%20%20%20%20%20%20%20interceptor.addInnerInterceptor(new%20BlockAttackInnerInterceptor())%3B%0A%20%20%20%20%20%20%20%20return%20interceptor%3B%0A%20%20%20%20%7D%0A%0A%20%20%20%20%2F**%20%E8%87%AA%E5%8A%A8%E5%A1%AB%E5%85%85%E5%88%9B%E5%BB%BA%E6%97%B6%E9%97%B4%E3%80%81%E6%9B%B4%E6%96%B0%E6%97%B6%E9%97%B4%20*%2F%0A%20%20%20%20%40Component%0A%20%20%20%20public%20class%20MyMetaObjectHandler%20implements%20MetaObjectHandler%20%7B%0A%20%20%20%20%20%20%20%20%40Override%0A%20%20%20%20%20%20%20%20public%20void%20insertFill(MetaObject%20metaObject)%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20this.strictInsertFill(metaObject%2C%20%22createTime%22%2C%20LocalDateTime.class%2C%20LocalDateTime.now())%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20this.strictInsertFill(metaObject%2C%20%22updateTime%22%2C%20LocalDateTime.class%2C%20LocalDateTime.now())%3B%0A%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%40Override%0A%20%20%20%20%20%20%20%20public%20void%20updateFill(MetaObject%20metaObject)%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20this.strictUpdateFill(metaObject%2C%20%22updateTime%22%2C%20LocalDateTime.class%2C%20LocalDateTime.now())%3B%0A%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%7D%0A%7D\"></pre><div class=\"box danger\"><div class=\"box-t\">⚠️ 分页插件不生效的经典坑</div><p>如果忘记配置 <code>PaginationInnerInterceptor</code>，MyBatis-Plus 的 <code>Page</code> 查询<strong>不会报错，但会返回全量数据</strong>。这是一个极其危险的静默失败——生产上可能导致全表扫描 + OOM。</p><p><strong>排查方法</strong>：打开 SQL 日志（<code>logging.level.com.example.mapper=DEBUG</code>），看实际执行的 SQL 有没有 <code>LIMIT</code>。</p></div><h4>乐观锁与逻辑删除</h4><pre data-lang=\"java\" data-code=\"%40Data%0A%40TableName(%22t_order%22)%0Apublic%20class%20Order%20%7B%0A%0A%20%20%20%20%40TableId(type%20%3D%20IdType.ASSIGN_ID)%20%20%20%20%20%20%20%20%20%20%2F%2F%20%E9%9B%AA%E8%8A%B1%E7%AE%97%E6%B3%95%E7%94%9F%E6%88%90%20ID%0A%20%20%20%20private%20Long%20id%3B%0A%0A%20%20%20%20%2F**%20%E4%B9%90%E8%A7%82%E9%94%81%E7%89%88%E6%9C%AC%E5%8F%B7%EF%BC%9A%E5%BF%85%E9%A1%BB%E6%A0%87%20%40Version%20*%2F%0A%20%20%20%20%40Version%0A%20%20%20%20private%20Integer%20version%3B%0A%0A%20%20%20%20%2F**%20%E9%80%BB%E8%BE%91%E5%88%A0%E9%99%A4%E6%A0%87%E8%AE%B0%EF%BC%9A0%20%E6%9C%AA%E5%88%A0%E9%99%A4%EF%BC%8C1%20%E5%B7%B2%E5%88%A0%E9%99%A4%20*%2F%0A%20%20%20%20%40TableLogic%0A%20%20%20%20private%20Integer%20deleted%3B%0A%0A%20%20%20%20%40TableField(fill%20%3D%20FieldFill.INSERT)%0A%20%20%20%20private%20LocalDateTime%20createTime%3B%0A%0A%20%20%20%20%40TableField(fill%20%3D%20FieldFill.INSERT_UPDATE)%0A%20%20%20%20private%20LocalDateTime%20updateTime%3B%0A%7D%0A%0A%2F%2F%20%E4%BD%BF%E7%94%A8%E4%B9%90%E8%A7%82%E9%94%81%EF%BC%9A%E5%85%88%E6%9F%A5%E5%87%BA%E6%9D%A5%EF%BC%8C%E6%94%B9%E5%AE%8C%E5%86%8D%E6%9B%B4%E6%96%B0%EF%BC%8CMP%20%E4%BC%9A%E8%87%AA%E5%8A%A8%E5%B8%A6%20version%20%E6%9D%A1%E4%BB%B6%0A%2F%2F%20UPDATE%20t_order%20SET%20amount%20%3D%20%3F%2C%20version%20%3D%20version%20%2B%201%0A%2F%2F%20%20%20WHERE%20id%20%3D%20%3F%20AND%20version%20%3D%20%3F%0A%2F%2F%20%E8%BF%94%E5%9B%9E%E5%BD%B1%E5%93%8D%E8%A1%8C%E6%95%B0%200%20%E8%A1%A8%E7%A4%BA%E8%A2%AB%E5%85%B6%E4%BB%96%E7%BA%BF%E7%A8%8B%E6%94%B9%E8%BF%87%EF%BC%8C%E9%9C%80%E8%A6%81%E9%87%8D%E8%AF%95%0AOrder%20order%20%3D%20orderMapper.selectById(1L)%3B%0Aorder.setAmount(new%20BigDecimal(%22100%22))%3B%0Aint%20rows%20%3D%20orderMapper.updateById(order)%3B%0Aif%20(rows%20%3D%3D%200)%20%7B%0A%20%20%20%20throw%20new%20BizException(%22%E6%95%B0%E6%8D%AE%E5%B7%B2%E8%A2%AB%E4%BF%AE%E6%94%B9%EF%BC%8C%E8%AF%B7%E9%87%8D%E8%AF%95%22)%3B%0A%7D\"></pre><pre data-lang=\"yaml\" data-code=\"mybatis-plus%3A%0A%20%20global-config%3A%0A%20%20%20%20db-config%3A%0A%20%20%20%20%20%20logic-delete-field%3A%20deleted%20%20%20%20%20%20%23%20%E5%85%A8%E5%B1%80%E9%80%BB%E8%BE%91%E5%88%A0%E9%99%A4%E5%AD%97%E6%AE%B5%0A%20%20%20%20%20%20logic-delete-value%3A%201%20%20%20%20%20%20%20%20%20%20%20%20%23%20%E5%B7%B2%E5%88%A0%E9%99%A4%0A%20%20%20%20%20%20logic-not-delete-value%3A%200%20%20%20%20%20%20%20%20%23%20%E6%9C%AA%E5%88%A0%E9%99%A4\"></pre><div class=\"box warn\"><div class=\"box-t\">⚠️ 逻辑删除的坑</div><p>逻辑删除后，<code>selectById</code> 会自动带上 <code>WHERE deleted = 0</code>，看起来很方便。但：</p><p>① <strong>唯一索引会冲突</strong>：删除后想再插入相同业务键的数据会被唯一索引拦住。解法：把 <code>deleted</code> 字段放进唯一索引，删除时写入时间戳而非固定 1。</p><p>② <strong>数据量膨胀</strong>：表会越来越大，需要定期归档。</p><p>③ <strong>关联查询容易漏</strong>：手写 SQL 的 join 不会自动带逻辑删除条件。</p></div>"
    },
    {
      "id": "s4-4",
      "title": "4.4 连接池调优（HikariCP）",
      "html": "<p>Spring Boot 2.x 起默认使用 <strong>HikariCP</strong>（性能最好的连接池）。调优参数就那几个，但讲清楚原理能在面试中加分。</p><h4>核心参数</h4><pre data-lang=\"yaml\" data-code=\"spring%3A%0A%20%20datasource%3A%0A%20%20%20%20url%3A%20jdbc%3Amysql%3A%2F%2Fdb%3A3306%2Forder%3FuseUnicode%3Dtrue%26characterEncoding%3Dutf8%26serverTimezone%3DAsia%2FShanghai%26useSSL%3Dfalse%0A%20%20%20%20username%3A%20root%0A%20%20%20%20password%3A%20my_password_here%0A%20%20%20%20driver-class-name%3A%20com.mysql.cj.jdbc.Driver%0A%20%20%20%20hikari%3A%0A%20%20%20%20%20%20%23%20%3D%3D%3D%3D%3D%20%E8%BF%9E%E6%8E%A5%E6%95%B0%20%3D%3D%3D%3D%3D%0A%20%20%20%20%20%20maximum-pool-size%3A%2020%20%20%20%20%20%20%20%20%20%20%20%20%23%20%E6%9C%80%E5%A4%A7%E8%BF%9E%E6%8E%A5%E6%95%B0%EF%BC%88%E6%A0%B8%E5%BF%83%E5%8F%82%E6%95%B0%EF%BC%89%0A%20%20%20%20%20%20minimum-idle%3A%2010%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%23%20%E6%9C%80%E5%B0%8F%E7%A9%BA%E9%97%B2%E8%BF%9E%E6%8E%A5%E6%95%B0%0A%20%20%20%20%20%20%23%20%3D%3D%3D%3D%3D%20%E8%B6%85%E6%97%B6%20%3D%3D%3D%3D%3D%0A%20%20%20%20%20%20connection-timeout%3A%2030000%20%20%20%20%20%20%20%20%23%20%E8%8E%B7%E5%8F%96%E8%BF%9E%E6%8E%A5%E8%B6%85%E6%97%B6%EF%BC%88ms%EF%BC%89%EF%BC%8C%E9%BB%98%E8%AE%A4%2030s%0A%20%20%20%20%20%20idle-timeout%3A%20600000%20%20%20%20%20%20%20%20%20%20%20%20%20%23%20%E7%A9%BA%E9%97%B2%E8%BF%9E%E6%8E%A5%E8%B6%85%E6%97%B6%EF%BC%88ms%EF%BC%89%EF%BC%8C%E9%BB%98%E8%AE%A4%2010min%0A%20%20%20%20%20%20max-lifetime%3A%201800000%20%20%20%20%20%20%20%20%20%20%20%20%23%20%E8%BF%9E%E6%8E%A5%E6%9C%80%E5%A4%A7%E5%AD%98%E6%B4%BB%E6%97%B6%E9%97%B4%EF%BC%88ms%EF%BC%89%EF%BC%8C%E9%BB%98%E8%AE%A4%2030min%0A%20%20%20%20%20%20%23%20%3D%3D%3D%3D%3D%20%E5%81%A5%E5%BA%B7%E6%A3%80%E6%9F%A5%20%3D%3D%3D%3D%3D%0A%20%20%20%20%20%20connection-test-query%3A%20SELECT%201%20%20%23%20%E6%A0%A1%E9%AA%8C%E8%BF%9E%E6%8E%A5%E6%98%AF%E5%90%A6%E5%8F%AF%E7%94%A8%0A%20%20%20%20%20%20pool-name%3A%20OrderHikariPool%20%20%20%20%20%20%20%23%20%E8%BF%9E%E6%8E%A5%E6%B1%A0%E5%90%8D%E7%A7%B0%EF%BC%88%E4%BE%BF%E4%BA%8E%E6%8E%92%E6%9F%A5%EF%BC%89\"></pre><div class=\"box interview\"><div class=\"box-t\">🎯 maximum-pool-size 到底该设多少？</div><p>这是最能体现深度的问题。很多人的答案是「越大越好」，这是错的。</p><p><strong>正确思路</strong>：</p><p>① <strong>连接数不是越多越好</strong>。数据库的连接是有限资源，每个连接占内存和线程。连接数过多反而因为锁竞争、上下文切换导致性能下降。PostgreSQL 官方甚至建议 <code>连接数 = 核心数 * 2 + 有效磁盘数</code>。</p><p>② <strong>计算公式</strong>：假设 QPS 需要 5000，单个请求 DB 操作耗时 20ms，则并发连接数约等于 5000 x 0.02 = 100。但要考虑实例数——如果部署 5 个实例，每个实例配 20 个连接就够（总 100）。</p><p>③ <strong>必须考虑数据库上限</strong>：MySQL 默认 <code>max_connections=151</code>。如果 10 个服务实例每个配 50，就是 500，直接打爆数据库。这是生产事故的常见原因。</p><p>④ <strong>实践建议</strong>：单实例 10-20 个（业务简单）到 50 个（高并发服务），配合压测调整。<strong>关键是「所有实例的最大连接数之和小于数据库 max_connections 的 80%」</strong>。</p></div><div class=\"box danger\"><div class=\"box-t\">⚠️ max-lifetime 必须小于数据库的 wait_timeout</div><p>MySQL 默认 <code>wait_timeout=28800</code>（8 小时），Hikari 默认 <code>max-lifetime=30min</code>，看起来没问题。</p><p>但如果数据库端配置被改小（有些云数据库默认 60 秒或 300 秒），而连接池不知道，就会拿到<strong>已被数据库关闭的死连接</strong>，报 <code>Communications link failure</code>。</p><p><strong>规范：Hikari 的 max-lifetime 应比数据库的 wait_timeout 小 30 秒以上</strong>，保证连接在数据库关闭它之前被连接池主动回收。</p></div><h4>连接池监控（生产必做）</h4><pre data-lang=\"yaml\" data-code=\"management%3A%0A%20%20endpoints%3A%0A%20%20%20%20web%3A%0A%20%20%20%20%20%20exposure%3A%0A%20%20%20%20%20%20%20%20include%3A%20health%2Cmetrics%2Cprometheus%0A%20%20metrics%3A%0A%20%20%20%20tags%3A%0A%20%20%20%20%20%20application%3A%20order-service\"></pre><p>接入 Prometheus 后关注这几个指标：</p><ul><li><code>hikaricp_connections_active</code> 活跃连接数——持续接近 maximum 说明连接不够</li><li><code>hikaricp_connections_pending</code> 等待获取连接的线程数——<strong>大于 0 就要告警</strong></li><li><code>hikaricp_connections_timeout_total</code> 获取连接超时次数——<strong>大于 0 说明已经影响业务</strong></li></ul>"
    },
    {
      "id": "s4-5",
      "title": "4.5 多数据源与分库分表",
      "html": "<p>你做过 Oracle 到 OceanBase / GoldenDB 的迁移，这是很好的面试素材。这一节讲清楚多数据源的技术实现。</p><h4>方案一：AbstractRoutingDataSource 动态数据源</h4><pre data-lang=\"java\" data-code=\"%2F**%201.%20%E5%AE%9A%E4%B9%89%E5%8A%A8%E6%80%81%E6%95%B0%E6%8D%AE%E6%BA%90%EF%BC%9A%E6%A0%B9%E6%8D%AE%20ThreadLocal%20%E5%86%B3%E5%AE%9A%E7%94%A8%E5%93%AA%E4%B8%AA%E5%BA%93%20*%2F%0Apublic%20class%20DynamicDataSource%20extends%20AbstractRoutingDataSource%20%7B%0A%0A%20%20%20%20%40Override%0A%20%20%20%20protected%20Object%20determineCurrentLookupKey()%20%7B%0A%20%20%20%20%20%20%20%20return%20DataSourceContextHolder.get()%3B%0A%20%20%20%20%7D%0A%7D%0A%0A%2F**%202.%20ThreadLocal%20%E6%8C%81%E6%9C%89%E5%BD%93%E5%89%8D%E6%95%B0%E6%8D%AE%E6%BA%90%20key%20*%2F%0Apublic%20class%20DataSourceContextHolder%20%7B%0A%20%20%20%20private%20static%20final%20ThreadLocal%3CString%3E%20CONTEXT%20%3D%20new%20ThreadLocal%3C%3E()%3B%0A%0A%20%20%20%20public%20static%20void%20set(String%20key)%20%7B%20CONTEXT.set(key)%3B%20%7D%0A%20%20%20%20public%20static%20String%20get()%20%7B%20return%20CONTEXT.get()%3B%20%7D%0A%20%20%20%20public%20static%20void%20clear()%20%7B%20CONTEXT.remove()%3B%20%7D%20%20%20%20%20%20%2F%2F%20%E5%BF%85%E9%A1%BB%E6%B8%85%E7%90%86%EF%BC%81%0A%7D%0A%0A%2F**%203.%20%E9%85%8D%E7%BD%AE%E5%A4%9A%E6%95%B0%E6%8D%AE%E6%BA%90%20*%2F%0A%40Configuration%0Apublic%20class%20DataSourceConfig%20%7B%0A%0A%20%20%20%20%40Bean(%22master%22)%0A%20%20%20%20%40ConfigurationProperties(%22spring.datasource.master%22)%0A%20%20%20%20public%20DataSource%20masterDataSource()%20%7B%0A%20%20%20%20%20%20%20%20return%20DataSourceBuilder.create().build()%3B%0A%20%20%20%20%7D%0A%0A%20%20%20%20%40Bean(%22slave%22)%0A%20%20%20%20%40ConfigurationProperties(%22spring.datasource.slave%22)%0A%20%20%20%20public%20DataSource%20slaveDataSource()%20%7B%0A%20%20%20%20%20%20%20%20return%20DataSourceBuilder.create().build()%3B%0A%20%20%20%20%7D%0A%0A%20%20%20%20%40Bean%0A%20%20%20%20%40Primary%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%2F%2F%20%E9%BB%98%E8%AE%A4%E6%95%B0%E6%8D%AE%E6%BA%90%0A%20%20%20%20public%20DataSource%20dynamicDataSource(%0A%20%20%20%20%20%20%20%20%20%20%20%20%40Qualifier(%22master%22)%20DataSource%20master%2C%0A%20%20%20%20%20%20%20%20%20%20%20%20%40Qualifier(%22slave%22)%20DataSource%20slave)%20%7B%0A%20%20%20%20%20%20%20%20Map%3CObject%2C%20Object%3E%20targetMap%20%3D%20new%20HashMap%3C%3E()%3B%0A%20%20%20%20%20%20%20%20targetMap.put(%22master%22%2C%20master)%3B%0A%20%20%20%20%20%20%20%20targetMap.put(%22slave%22%2C%20slave)%3B%0A%20%20%20%20%20%20%20%20DynamicDataSource%20ds%20%3D%20new%20DynamicDataSource()%3B%0A%20%20%20%20%20%20%20%20ds.setTargetDataSources(targetMap)%3B%0A%20%20%20%20%20%20%20%20ds.setDefaultTargetDataSource(master)%3B%0A%20%20%20%20%20%20%20%20return%20ds%3B%0A%20%20%20%20%7D%0A%7D%0A%0A%2F**%204.%20%E8%87%AA%E5%AE%9A%E4%B9%89%E6%B3%A8%E8%A7%A3%20%2B%20AOP%20%E5%88%87%E6%8D%A2%E6%95%B0%E6%8D%AE%E6%BA%90%20*%2F%0A%40Target(ElementType.METHOD)%0A%40Retention(RetentionPolicy.RUNTIME)%0Apublic%20%40interface%20DS%20%7B%0A%20%20%20%20String%20value()%20default%20%22master%22%3B%0A%7D%0A%0A%40Aspect%0A%40Component%0A%40Order(Ordered.HIGHEST_PRECEDENCE)%20%20%20%20%20%20%2F%2F%20%E5%BF%85%E9%A1%BB%E5%9C%A8%E4%BA%8B%E5%8A%A1%E5%88%87%E9%9D%A2%E4%B9%8B%E5%89%8D%E6%89%A7%E8%A1%8C%0Apublic%20class%20DataSourceAspect%20%7B%0A%0A%20%20%20%20%40Around(%22%40annotation(ds)%22)%0A%20%20%20%20public%20Object%20around(ProceedingJoinPoint%20pjp%2C%20DS%20ds)%20throws%20Throwable%20%7B%0A%20%20%20%20%20%20%20%20try%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20DataSourceContextHolder.set(ds.value())%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20return%20pjp.proceed()%3B%0A%20%20%20%20%20%20%20%20%7D%20finally%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20DataSourceContextHolder.clear()%3B%20%20%20%20%20%20%2F%2F%20%E5%BF%85%E9%A1%BB%E6%B8%85%E7%90%86%0A%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%7D%0A%7D\"></pre><pre data-lang=\"java\" data-code=\"%40Service%0Apublic%20class%20UserService%20%7B%0A%0A%20%20%20%20%40DS(%22master%22)%0A%20%20%20%20public%20void%20insert(User%20user)%20%7B%20userMapper.insert(user)%3B%20%7D%0A%0A%20%20%20%20%40DS(%22slave%22)%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%2F%2F%20%E8%AF%BB%E8%B5%B0%E4%BB%8E%E5%BA%93%0A%20%20%20%20public%20User%20getById(Long%20id)%20%7B%20return%20userMapper.selectById(id)%3B%20%7D%0A%7D\"></pre><div class=\"box danger\"><div class=\"box-t\">⚠️ 多数据源 + 事务的三个坑</div><p><strong>① 数据源切换必须发生在事务开启之前。</strong>因为事务一旦开启，连接就已经绑定到 ThreadLocal 了。所以切面 <code>@Order</code> 必须比事务切面<strong>更靠前</strong>（值更小）。</p><p><strong>② 跨库事务无法用本地事务解决。</strong><code>@Transactional</code> 只对当前数据源有效，跨库写必须用分布式事务（Seata、TCC、消息最终一致性）。</p><p><strong>③ ThreadLocal 必须 clear。</strong>和方法调用栈绑定，用 try-finally 保证清理，否则线程复用会导致后续请求走错库——这个 bug 极难排查。</p></div><div class=\"box tip\"><div class=\"box-t\">💡 结合你的迁移经验怎么答</div><p>面试问「做过数据库迁移吗」，可以这样组织：</p><p>「做过 Oracle 到 OceanBase、GoldenDB 的迁移。<strong>技术上主要挑战有三点</strong>：① <strong>语法兼容</strong>——Oracle 的 <code>ROWNUM</code>、<code>NVL</code>、<code>DECODE</code>、序列 <code>SEQ.NEXTVAL</code> 都需要改写；② <strong>数据类型差异</strong>——<code>NUMBER</code>、<code>VARCHAR2</code>、<code>DATE</code>（含时分秒）要映射到目标库类型；③ <strong>性能回归</strong>——执行计划不同，部分复杂 SQL 需要重建索引或改写（如把 <code>OR</code> 改成 <code>UNION ALL</code>）。</p><p><strong>工程上</strong>：用双写 + 数据校验（抽样比对 + 全量 checksum）保证一致性，灰度切流逐步放量，保留回滚能力。」</p></div><div class=\"qa\"><div class=\"q\"><span class=\"qn\">Q1</span><span>Spring 事务传播行为有哪些？REQUIRED 和 REQUIRES_NEW 区别？</span><span class=\"arrow\">▶</span></div><div class=\"a\"><p>7 种：REQUIRED（默认，有则加入无则新建）、REQUIRES_NEW（挂起当前，新建独立事务）、NESTED（Savepoint 嵌套）、SUPPORTS、NOT_SUPPORTED、MANDATORY、NEVER。</p><p>REQUIRED 与 REQUIRES_NEW 的核心区别：<strong>REQUIRED 复用外层事务连接，内层失败会导致整个事务回滚；REQUIRES_NEW 挂起外层事务，用自己的新连接开独立事务，外层回滚不影响内层。</strong></p><p>典型场景：记录操作日志。业务失败要回滚，但日志必须保留，就用 REQUIRES_NEW。</p></div></div><div class=\"qa\"><div class=\"q\"><span class=\"qn\">Q2</span><span>@Transactional 什么时候会失效？</span><span class=\"arrow\">▶</span></div><div class=\"a\"><p>八种场景：自调用、非 public 方法、final/static 方法、异常被 try-catch 吞掉、抛受检异常未配 rollbackFor、类未被 Spring 管理、数据库引擎不支持事务（MyISAM）、多线程调用。</p><p>根因是事务基于 AOP 代理，凡是让代理增强失效或异常无法传播的情况都会失效。</p><p>生产规范：显式写 <code>rollbackFor = Exception.class</code>，禁止吞异常，禁止事务方法内自调用。</p></div></div>"
    }
  ]
});

/* ============================================================
   DAY 5：进阶特性与可观测性
   ============================================================ */
CHAPTERS.push({
  id: "day5",
  badge: "D5",
  badgeLabel: "Day 5",
  badgeType: "",
  title: "进阶特性与可观测性 · AOP 实战 / 异步 / 缓存 / 监控",
  intro: "今天补齐「工程能力」这块拼图：AOP 实战（分布式锁、幂等、日志）、异步与线程池配置、缓存抽象、Actuator 监控、日志体系。这些是大厂生产环境的基础设施，面试问「你们怎么保障稳定性」时的答题弹药。",
  sections: [
    {
      id: "s5-1",
      title: "5.1 AOP 实战：三个生产级切面",
      html:
'<p>Day 0 讲了 AOP 原理，今天讲三个真实生产场景的切面实现。这三个案例都能直接写进简历。</p>'+

'<h4>案例 1：操作日志切面（可扩展为审计日志）</h4>'+
PRE("java",
`/** 自定义注解 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {
    /** 操作类型：CREATE / UPDATE / DELETE / QUERY */
    String type();
    /** 操作描述 */
    String desc() default "";
}

/** 切面 */
@Aspect
@Component
public class OpLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OpLogAspect.class);

    private final OpLogService opLogService;

    public OpLogAspect(OpLogService opLogService) {
        this.opLogService = opLogService;
    }

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - start;
            // 异步记录，不阻塞主流程
            OpLogRecord record = new OpLogRecord();
            record.setType(opLog.type());
            record.setDesc(opLog.desc());
            record.setMethod(pjp.getSignature().toShortString());
            record.setArgs(serializeArgs(pjp.getArgs()));
            record.setSuccess(error == null);
            record.setErrorMsg(error == null ? null : error.getMessage());
            record.setCostMs(cost);
            record.setUserId(UserContextHolder.getUserId());
            record.setOpTime(LocalDateTime.now());
            opLogService.asyncSave(record);
        }
    }

    /** 序列化参数（需处理 HttpServletRequest 等不可序列化对象） */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        try {
            Object[] filtered = Arrays.stream(args)
                    .filter(a -> !(a instanceof HttpServletRequest)
                              && !(a instanceof HttpServletResponse)
                              && !(a instanceof MultipartFile))
                    .toArray();
            String json = JSON.toJSONString(filtered);
            return json.length() > 2000 ? json.substring(0, 2000) : json;   // 截断防爆库
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }
}

/** 使用 */
@RestController
public class OrderController {

    @PostMapping("/orders")
    @OpLog(type = "CREATE", desc = "创建订单")
    public Result<Long> create(@RequestBody @Validated OrderCreateReq req) {
        return Result.success(orderService.create(req));
    }
}`)+

BOX("danger","切面参数序列化的坑",
'<p><code>HttpServletRequest</code>、<code>MultipartFile</code> 等对象<strong>无法被 Jackson 序列化</strong>，直接 <code>JSON.toJSONString(args)</code> 会抛异常甚至死循环。</p>'+
'<p>必须过滤掉这些类型。另外要注意：<strong>日志内容必须截断</strong>（如限制 2000 字符），否则一个大对象的 JSON 可能几 MB，把数据库和日志系统打爆。</p>','⚠️')+

'<h4>案例 2：分布式锁切面</h4>'+
PRE("java",
`/** 分布式锁注解 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    /** SpEL 表达式，用于生成锁 key，如 "#req.orderId" */
    String key();
    /** 等待获取锁的时间（秒） */
    long waitTime() default 3;
    /** 锁自动释放时间（秒），防死锁 */
    long leaseTime() default 10;
}

@Aspect
@Component
public class DistributedLockAspect {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockAspect.class);

    private final RedissonClient redissonClient;

    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(lockAnno)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock lockAnno) throws Throwable {
        // 1. 解析 SpEL 得到锁 key
        String lockKey = SpElUtils.parse(lockAnno.key(), pjp.getArgs(),
                pjp.getSignature().getParameterTypes());
        RLock lock = redissonClient.getLock("lock:" + lockKey);
        boolean locked = false;
        try {
            // 2. 尝试加锁
            locked = lock.tryLock(lockAnno.waitTime(), lockAnno.leaseTime(), TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException("操作过于频繁，请稍后重试");
            }
            // 3. 执行业务
            return pjp.proceed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("获取锁被中断");
        } finally {
            // 4. 释放锁：必须判断是否当前线程持有，否则会释放别人的锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}`)+

BOX("interview","为什么用 Redisson 而不是自己写 SETNX",
'<p>手写 <code>SET key value NX EX 10</code> 有三个致命问题：</p>'+
'<p><strong>① 误删别人的锁</strong>：A 持有锁超时自动释放，B 拿到锁，此时 A 执行完删除 key，把 B 的锁删了。解法：value 存唯一标识（UUID+线程ID），删除前校验。</p>'+
'<p><strong>② 锁过期业务未完成</strong>：业务执行超过 leaseTime，锁自动释放，其他线程进入。解法：看门狗（watchdog）自动续期。</p>'+
'<p><strong>③ 校验+删除不是原子操作</strong>：必须用 Lua 脚本保证原子性。</p>'+
'<p>Redisson 把这三个问题都解决了：UUID+线程ID 作为 value、Lua 脚本原子删除、<strong>看门狗默认每 10 秒续期至 30 秒</strong>（注意：只有不显式指定 leaseTime 时才启用看门狗）。</p>','🎯')+

'<h4>案例 3：幂等切面（基于注解 + Redis）</h4>'+
PRE("java",
`@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /** 幂等 key 的 SpEL 表达式 */
    String key();
    /** 幂等有效期（秒） */
    long expire() default 60;
    /** 触发幂等时提示语 */
    String message() default "重复请求，请勿重复提交";
}

@Aspect
@Component
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;

    public IdempotentAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(idem)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idem) throws Throwable {
        String key = "idem:" + SpElUtils.parse(idem.key(), pjp.getArgs(),
                pjp.getSignature().getParameterTypes());
        // setIfAbsent 等价于 SETNX，原子操作
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", idem.expire(), TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(success)) {
            throw new BizException(idem.message);
        }
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            // 业务失败，删除 key 允许重试
            redisTemplate.delete(key);
            throw t;
        }
        // 注意：成功时不删除，让 key 自然过期，防止短时间内重复提交
    }
}

/** 使用 */
@PostMapping("/orders")
@Idempotent(key = "#req.requestId", expire = 60)
public Result<Long> create(@RequestBody @Validated OrderCreateReq req) {
    return Result.success(orderService.create(req));
}`)+

BOX("warn","幂等的三种实现对比",
'<p><strong>① Redis SETNX</strong>（上面这种）：性能好，但依赖 Redis 可用性；适合短时间重复提交场景。</p>'+
'<p><strong>② 数据库唯一索引</strong>：最可靠（强一致），但性能差且需要设计业务唯一键；适合订单号、支付流水这类强一致场景。</p>'+
'<p><strong>③ 状态机</strong>：依赖业务状态流转（如订单只能从「待支付」到「已支付」），用 <code>UPDATE ... WHERE status = 待支付</code> 加乐观锁。<strong>这是最优雅的方式</strong>，因为它同时解决了并发和幂等。</p>'+
'<p>生产上通常「状态机为主 + Redis 防重为辅」组合使用。</p>','⚠️')
    },
    {
      id: "s5-2",
      title: "5.2 异步任务与线程池（生产级配置）",
      html:
'<h4>为什么必须自定义线程池</h4>'+
'<p><code>@Async</code> 默认用的是 <code>SimpleAsyncTaskExecutor</code>，它<strong>每次请求都新建一个线程，不复用</strong>。高并发下会创建海量线程，直接 OOM。生产必须自定义线程池。</p>'+

PRE("java",
`@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：CPU 密集型 = 核数 + 1；IO 密集型 = 核数 * 2（或按 QPS 估算）
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(64);
        // 队列容量：必须设置，避免无界队列导致 OOM
        executor.setQueueCapacity(500);
        // 空闲线程存活时间
        executor.setKeepAliveSeconds(60);
        // 线程名前缀：排查问题时能快速定位
        executor.setThreadNamePrefix("async-order-");
        // 拒绝策略：CallerRunsPolicy —— 队列满时由调用线程执行，形成背压
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待任务完成后关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** 统一处理异步方法的异常（否则异常会被静默吞掉！） */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            LoggerFactory.getLogger(AsyncConfig.class)
                    .error("异步任务执行失败: {}", method.getName(), ex);
        };
    }
}`)+

BOX("danger","@Async 失效的 4 个场景",
'<p><strong>① 自调用</strong>：同类内 this 调用不走代理（和事务一样）。</p>'+
'<p><strong>② 方法非 public</strong>：代理只增强 public 方法。</p>'+
'<p><strong>③ 返回值错误</strong>：有返回值的方法只能返回 <code>Future</code>/<code>CompletableFuture</code>，返回其他类型会一直阻塞等待，实际变成同步。</p>'+
'<p><strong>④ 类没被 Spring 管理</strong>，或忘了加 <code>@EnableAsync</code>。</p>'+
'<p><strong>⑤ 异常被吞</strong>：异步方法抛异常不会传播到调用方，必须配 <code>AsyncUncaughtExceptionHandler</code> 或返回 <code>CompletableFuture</code> 在外部处理，否则异常静默丢失——这是最难发现的 bug。</p>','⚠️')+

'<h4>@Async + @Transactional 的组合陷阱</h4>'+
PRE("java",
`// 错误写法：异步和事务在同一个方法上
@Async
@Transactional(rollbackFor = Exception.class)
public void wrong() {
    // 问题 1：@Async 把方法丢到子线程，@Transactional 在子线程开事务
    // 问题 2：子线程的异常无法被外层捕获，事务回滚也无意义
    // 问题 3：调用方以为数据已写入，实际上异步还没执行完
}

// 正确写法：拆分
@Service
public class OrderService {

    private final AsyncService asyncService;

    @Transactional(rollbackFor = Exception.class)
    public void createOrder(Order order) {
        orderMapper.insert(order);
        // 事务提交后再触发异步（用事件机制，见 5.3）
        asyncService.sendNotify(order.getId());
    }
}

@Service
public class AsyncService {
    @Async
    public void sendNotify(Long orderId) {
        // 此时主事务可能还未提交，如果这里查库会查不到数据！
        // 解法：用 TransactionSynchronization 在事务提交后执行
    }
}`)+

BOX("tip","用 TransactionSynchronization 保证事务提交后再异步",
'<pre style="margin:8px 0"><code>TransactionSynchronizationManager.registerSynchronization(<br>    new TransactionSynchronization() {<br>        @Override<br>        public void afterCommit() {<br>            asyncService.sendNotify(orderId);   // 事务已提交，能查到数据<br>        }<br>    });</code></pre>'+
'<p>这是大厂高频场景题：「事务提交后发消息 / 发通知」的标准做法。要用 <code>afterCommit</code> 而不是直接调用，否则异步线程可能读不到未提交的数据。</p>','💡')+

'<h4>虚拟线程（Boot 3.2+ 新特性）</h4>'+
PRE("yaml",
`spring:
  threads:
    virtual:
      enabled: true      # 一行开启虚拟线程`)+
'<p>开启后，Tomcat 的请求处理和 <code>@Async</code> 任务都会使用虚拟线程（JDK 21+）。</p>'+
'<div class="tbl-wrap"><table><thead><tr><th>对比</th><th>平台线程</th><th>虚拟线程</th></tr></thead><tbody>'+
'<tr><td>资源占用</td><td>1MB 栈空间/线程</td><td>几 KB，可创建百万级</td></tr>'+
'<tr><td>适用场景</td><td>CPU 密集型</td><td><strong>IO 密集型</strong>（大量等待）</td></tr>'+
'<tr><td>切换成本</td><td>内核态切换，较贵</td><td>用户态切换，极廉价</td></tr>'+
'<tr><td>注意事项</td><td>—</td><td><strong>不能池化</strong>；<code>synchronized</code> 阻塞会 pin 住载体线程</td></tr>'+
'</tbody></table></div>'+
BOX("warn","虚拟线程的坑",
'<p>① <strong>不要池化虚拟线程</strong>——它本来就是廉价的一次性对象，池化反而增加开销。</p>'+
'<p>② <strong>避免在虚拟线程里用 <code>synchronized</code> 做长时间阻塞</strong>，会导致载体线程被 pin 住（JDK 21 已优化部分场景，但仍建议用 <code>ReentrantLock</code>）。</p>'+
'<p>③ <strong>ThreadLocal 慎用</strong>：虚拟线程数量巨大，ThreadLocal 会放大内存占用。推荐用 <code>ScopedValue</code>（JDK 21 预览）。</p>','⚠️')
    },
    {
      id: "s5-3",
      title: "5.3 事件机制与缓存抽象",
      html:
'<h4>Spring 事件机制（解耦利器）</h4>'+
PRE("java",
`/** 1. 定义事件 */
public class OrderCreatedEvent extends ApplicationEvent {
    private final Long orderId;
    private final BigDecimal amount;

    public OrderCreatedEvent(Object source, Long orderId, BigDecimal amount) {
        super(source);
        this.orderId = orderId;
        this.amount = amount;
    }
    public Long getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
}

/** 2. 发布事件 */
@Service
public class OrderService {

    private final ApplicationEventPublisher publisher;

    public OrderService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createOrder(Order order) {
        orderMapper.insert(order);
        // 发布事件（此时事务还未提交）
        publisher.publishEvent(new OrderCreatedEvent(this, order.getId(), order.getAmount()));
    }
}

/** 3. 监听事件 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    /** 同步监听：事务未提交时执行 */
    @EventListener
    public void onCreated(OrderCreatedEvent event) {
        log.info("订单创建: {}", event.getOrderId());
    }

    /** 事务提交后执行（推荐） */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OrderCreatedEvent event) {
        // 事务已提交，可以安全地发消息/发通知
        log.info("事务已提交，发送通知: {}", event.getOrderId());
    }

    /** 异步监听 */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void asyncAfterCommit(OrderCreatedEvent event) {
        // 异步 + 事务提交后
    }
}`)+

BOX("tip","事件机制 vs 直接调用",
'<p><strong>优点</strong>：解耦。订单服务不需要知道谁关心订单创建，后续加「发短信」「加积分」「发消息」都不用改订单代码。</p>'+
'<p><strong>缺点</strong>：调用链不直观，调试困难；<strong>默认是同步的</strong>，监听器抛异常会影响发布方（除非加 <code>@Async</code>）。</p>'+
'<p><strong>选择原则</strong>：同一进程内的解耦用事件；跨服务的解耦用 MQ（RocketMQ / Kafka）。</p>','💡')+

'<h4>缓存抽象（@Cacheable）</h4>'+
PRE("java",
`@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 默认 30 分钟过期
                .entryTtl(Duration.ofMinutes(30))
                // key 用 String 序列化（可读性强，便于排查）
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // value 用 JSON 序列化（可读、跨语言）
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                // 禁止缓存 null（防缓存穿透）
                .disableCachingNullValues()
                // 缓存 key 前缀
                .computePrefixWith(cacheName -> "cache:" + cacheName + ":");
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .withCacheConfiguration("user",
                        config.entryTtl(Duration.ofMinutes(10)))   // user 缓存单独设 10 分钟
                .build();
    }
}`)+
PRE("java",
`@Service
public class UserService {

    /** 查询缓存：key 为 user::1 */
    @Cacheable(value = "user", key = "#id", unless = "#result == null")
    public User getById(Long id) {
        log.info("查询数据库: {}", id);      // 只有缓存未命中才会打印
        return userMapper.selectById(id);
    }

    /** 更新时刷新缓存 */
    @CachePut(value = "user", key = "#user.id")
    public User update(User user) {
        userMapper.updateById(user);
        return userMapper.selectById(user.id);
    }

    /** 删除时清除缓存 */
    @CacheEvict(value = "user", key = "#id")
    public void delete(Long id) {
        userMapper.deleteById(id);
    }

    /** 清空整个缓存区 */
    @CacheEvict(value = "user", allEntries = true)
    public void clearAll() { }
}`)+

BOX("danger","缓存三大问题（面试必问）",
'<p><strong>① 缓存穿透</strong>：查询不存在的数据，每次都打到数据库（可能是恶意攻击）。<br>'+
'解法 a：缓存空值（<code>@Cacheable</code> 不配 <code>unless="#result==null"</code>，或手动缓存空对象，设置短过期时间）；<br>'+
'解法 b：布隆过滤器（Bloom Filter）预判 key 是否存在。</p>'+
'<p><strong>② 缓存击穿</strong>：某个热点 key 过期瞬间，大量请求同时打到数据库。<br>'+
'解法：加分布式锁，只让一个线程去加载，其他线程等待（Redisson 的 <code>RLock</code> 或本地双检锁）。</p>'+
'<p><strong>③ 缓存雪崩</strong>：大量 key 同时过期，或 Redis 整个挂了。<br>'+
'解法 a：过期时间加随机值（<code>ttl + random(0, 300s)</code>），避免同时失效；<br>'+
'解法 b：多级缓存（本地 Caffeine + Redis）；<br>'+
'解法 c：Redis 高可用（哨兵 / 集群）+ 限流降级。</p>','⚠️')+

BOX("warn","@Cacheable 自调用失效",
'<p>和 <code>@Transactional</code>、<code>@Async</code> 一样，<strong>同类内调用 <code>@Cacheable</code> 方法不走代理，缓存不生效</strong>。这是 AOP 代理的通用限制，记住这一条就能解释所有类似的「注解失效」问题。</p>','⚠️')
    },
    {
      id: "s5-4",
      title: "5.4 Actuator 监控与健康检查",
      html:
'<h4>依赖与配置</h4>'+
PRE("xml",
`<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>`)+
PRE("yaml",
`management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health,info,metrics,prometheus,loggers,threaddump,heapdump
        # 注意：默认只暴露 health 和 info，其余需显式开启
  endpoint:
    health:
      show-details: when-authorized     # 详情只给授权用户看
    shutdown:
      enabled: false                    # 危险！生产禁止远程关闭
  health:
    db:
      enabled: true
    redis:
      enabled: true
    diskspace:
      threshold: 1GB                    # 磁盘空间低于此值健康检查失败
  metrics:
    tags:
      application: order-service        # 所有指标加上应用名标签
  server:
    port: 9090                          # 监控端口独立（推荐，不对外暴露）`)+

'<h4>核心端点说明</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>端点</th><th>作用</th><th>生产建议</th></tr></thead><tbody>'+
'<tr><td><code>/actuator/health</code></td><td>健康检查（K8s 存活/就绪探针）</td><td>必须开启，用于负载均衡摘除故障节点</td></tr>'+
'<tr><td><code>/actuator/metrics</code></td><td>各项指标（JVM、HTTP、DB、连接池）</td><td>接入 Prometheus</td></tr>'+
'<tr><td><code>/actuator/prometheus</code></td><td>Prometheus 抓取格式</td><td>生产必开</td></tr>'+
'<tr><td><code>/actuator/loggers</code></td><td>动态调整日志级别（无需重启！）</td><td>排障神器，强烈推荐</td></tr>'+
'<tr><td><code>/actuator/threaddump</code></td><td>导出线程堆栈</td><td>排查死锁、线程池满</td></tr>'+
'<tr><td><code>/actuator/heapdump</code></td><td>下载堆转储文件</td><td>排查 OOM，<strong>会 STW，慎用</strong></td></tr>'+
'<tr><td><code>/actuator/beans</code></td><td>查看所有 Bean</td><td>排障用，生产建议关</td></tr>'+
'<tr><td><code>/actuator/env</code></td><td>查看所有配置</td><td><strong>会泄露密码！生产必须关或脱敏</strong></td></tr>'+
'</tbody></table></div>'+

BOX("danger","Actuator 安全风险（生产事故高发区）",
'<p><strong>① <code>/actuator/env</code> 会明文返回所有配置</strong>，包括数据库密码、Redis 密码、AK/SK。生产必须关闭，或引入 <code>SanitizingFunction</code> 脱敏。</p>'+
'<p><strong>② <code>/actuator/heapdump</code> 可下载完整堆内存</strong>，攻击者能从中提取敏感数据（如用户密码、Token）。必须做认证。</p>'+
'<p><strong>③ <code>/actuator/shutdown</code> 可远程关闭应用</strong>，默认关闭但有人手动开启过，务必检查。</p>'+
'<p><strong>规范做法</strong>：监控端点走<strong>独立的内部端口</strong>（如 9090），不通过网关对外暴露；或加 Spring Security 鉴权。</p>','⚠️')+

'<h4>自定义健康检查（业务级探活）</h4>'+
PRE("java",
`/**
 * 场景：应用启动后需要预热缓存，预热完成前不应接收流量
 */
@Component
public class CacheWarmupHealthIndicator implements HealthIndicator {

    private final AtomicBoolean warmedUp = new AtomicBoolean(false);

    @Override
    public Health health() {
        if (warmedUp.get()) {
            return Health.up()
                    .withDetail("warmup", "completed")
                    .build();
        }
        return Health.status("WARMING_UP")      // 自定义状态
                .withDetail("warmup", "in progress")
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        // 执行预热逻辑
        warmedUp.set(true);
    }
}`)+
PRE("yaml",
`# K8s 中配置就绪探针，WARMING_UP 时不接收流量`)+
'<p>配合 K8s 的 <code>readinessProbe</code>，就能实现「预热完成才接入流量」，避免启动瞬间的请求超时。</p>'
    },
    {
      id: "s5-5",
      title: "5.5 日志体系与链路追踪",
      html:
'<h4>日志配置规范</h4>'+
PRE("yaml",
`logging:
  level:
    root: info
    com.example: debug                    # 只开自己包的 debug
    org.springframework.web: info
  file:
    name: /var/log/app/order-service.log
  logback:
    rollingpolicy:
      max-file-size: 100MB                # 单文件上限
      max-history: 30                     # 保留 30 天
      total-size-cap: 10GB                # 总大小上限
  pattern:
    # 关键：加上 traceId，便于串联一次请求的所有日志
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n"`)+

'<h4>MDC + TraceId 实现请求链路串联</h4>'+
PRE("java",
`/**
 * 用 Filter 为每个请求生成 traceId，放入 MDC
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 优先用上游传下来的（网关生成的），保证全链路一致
        String traceId = request.getHeader("X-Trace-Id");
        if (StringUtils.isBlank(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        try {
            MDC.put(TRACE_ID, traceId);
            response.setHeader("X-Trace-Id", traceId);      // 返回给前端便于排障
            chain.doFilter(request, response);
        } finally {
            MDC.clear();        // 必须清理，否则线程复用会导致 traceId 串号
        }
    }
}`)+

BOX("warn","MDC 在异步线程中丢失",
'<p>MDC 底层是 <code>ThreadLocal</code>，<strong>子线程拿不到父线程的 MDC 内容</strong>。异步任务打印的日志会丢失 traceId。</p>'+
'<p><strong>解法</strong>：包装线程池的 TaskDecorator，在提交任务时把 MDC 上下文复制过去。</p>'+
'<pre style="margin:8px 0"><code>executor.setTaskDecorator(runnable -> {<br>    Map&lt;String, String&gt; ctx = MDC.getCopyOfContextMap();<br>    return () -&gt; {<br>        if (ctx != null) MDC.setContextMap(ctx);<br>        try { runnable.run(); } finally { MDC.clear(); }<br>    };<br>});</code></pre>','⚠️')+

'<h4>Micrometer Tracing（Boot 3.x 替代 Sleuth）</h4>'+
PRE("xml",
`<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>`)+
PRE("yaml",
`management:
  tracing:
    sampling:
      probability: 0.1        # 采样率 10%（生产不要 100%，否则压力大）
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans`)+

BOX("tip","可观测性三支柱",
'<p><strong>Metrics（指标）</strong>：Prometheus + Grafana，看趋势和告警。关注 QPS、RT、错误率、连接池、JVM 内存。</p>'+
'<p><strong>Tracing（链路追踪）</strong>：Zipkin / SkyWalking / Jaeger，看单次请求经过哪些服务、哪一段慢。</p>'+
'<p><strong>Logging（日志）</strong>：ELK / Loki，看具体错误内容。</p>'+
'<p>面试问「你们怎么排查线上问题」，按这三支柱组织答案：<strong>先看告警指标定位到哪个服务 → 用 traceId 在链路系统中找到慢的那一段 → 查日志看具体异常</strong>。这个回答框架很加分。</p>','💡')+

QA(1, "@Async 有哪些失效场景？",
'<p>① <strong>自调用</strong>：同类内 this 调用不走代理。<br>'+
'② <strong>方法非 public</strong>：代理只增强 public。<br>'+
'③ <strong>返回值类型错误</strong>：有返回值只能返回 Future/CompletableFuture，否则退化成同步。<br>'+
'④ <strong>忘记 @EnableAsync</strong> 或类未被 Spring 管理。<br>'+
'⑤ <strong>异常被静默吞掉</strong>：异步方法抛异常不会传播给调用方，必须配 <code>AsyncUncaughtExceptionHandler</code>。</p>'+
'<p>另外，<code>@Async</code> 默认使用 <code>SimpleAsyncTaskExecutor</code>，<strong>不复用线程</strong>，生产必须自定义线程池，并设置队列容量和合理的拒绝策略。</p>')+

QA(2, "缓存穿透、击穿、雪崩的区别和解法？",
'<p><strong>穿透</strong>：查询<strong>不存在</strong>的数据，绕过缓存直击数据库。解法：缓存空值 + 布隆过滤器。</p>'+
'<p><strong>击穿</strong>：<strong>单个热点 key 过期</strong>，大量并发请求同时打到数据库。解法：分布式锁只放一个线程加载，或热点数据永不过期（逻辑过期）。</p>'+
'<p><strong>雪崩</strong>：<strong>大量 key 同时过期</strong>或 Redis 宕机，数据库瞬间被打爆。解法：过期时间加随机值、多级缓存、Redis 高可用 + 熔断降级。</p>')+

QA(3, "Actuator 有什么安全风险？",
'<p><code>/actuator/env</code> 会明文返回所有配置（含数据库密码等敏感信息）；<code>/actuator/heapdump</code> 可下载堆内存（能提取用户数据）；<code>/actuator/shutdown</code> 可远程停止应用。</p>'+
'<p>规范做法：监控端点走独立内部端口不对外暴露、引入 Spring Security 做认证、对敏感配置做脱敏（SanitizingFunction）、确保 shutdown 端点关闭。</p>')
    }
  ]
});

/* ============================================================
   DAY 6：大厂场景题攻坚
   ============================================================ */
CHAPTERS.push({
  id: "day6",
  badge: "D6",
  badgeLabel: "Day 6",
  badgeType: "warm",
  title: "大厂场景题攻坚 · 高并发 / 分布式 / 性能调优",
  intro: "今天不谈语法，只谈「生产事故」和「架构决策」。这些是大厂三面、四面的核心考察内容，也是区分「会用框架」和「能扛系统」的分界线。每题都给出完整的答题框架。",
  sections: [
    {
      id: "s6-1",
      title: "6.1 高并发接口设计：秒杀场景完整方案",
      html:
'<p>秒杀是大厂面试的经典场景题，几乎必问。它考察的是<strong>分层削峰</strong>的架构思维。</p>'+

'<h4>核心矛盾</h4>'+
'<p>瞬间流量（10 万 QPS）远大于数据库承载能力（几千 TPS），必须在链路上层层拦截。</p>'+

'<h4>五层削峰架构</h4>'+
'<ol class="steps">'+
'<li><div class="st-t">第一层：CDN + 静态化</div>商品详情页静态化推到 CDN，只有「下单」按钮走后端。这一层就能挡掉 90% 的流量。</li>'+
'<li><div class="st-t">第二层：网关限流 + 黑名单</div>Nginx / Spring Cloud Gateway 做 IP 限流、用户频次限流；拦截黄牛（风控识别）。</li>'+
'<li><div class="st-t">第三层：Redis 预扣库存（核心）</div>库存预热到 Redis，用 <strong>Lua 脚本</strong>保证「判断库存 + 扣减」的原子性。这一步挡住绝大部分无效请求。</li>'+
'<li><div class="st-t">第四层：MQ 异步下单</div>扣减成功后不直接写库，而是发消息到 RocketMQ，由消费端异步创建订单。实现流量削峰填谷。</li>'+
'<li><div class="st-t">第五层：数据库最终扣减 + 唯一索引兜底</div>消费端落库时用 <code>UPDATE stock = stock - 1 WHERE stock > 0</code> 兜底，并用唯一索引防重复下单。</li>'+
'</ol>'+

'<h4>核心代码：Redis Lua 原子扣减</h4>'+
PRE("java",
`@Service
public class SeckillService {

    private static final String STOCK_KEY = "seckill:stock:";
    private static final String USER_SET_KEY = "seckill:users:";

    private final StringRedisTemplate redisTemplate;
    private final RocketMQTemplate mqTemplate;

    /** Lua 脚本：原子执行「判断重复购买 + 判断库存 + 扣减 + 记录用户」 */
    private static final String SECKILL_SCRIPT =
        // 1. 判断用户是否已经买过
        "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then " +
        "   return -2 " +                       // -2 表示重复下单
        "end " +
        // 2. 判断库存
        "local stock = tonumber(redis.call('get', KEYS[1])) " +
        "if stock == nil or stock <= 0 then " +
        "   return -1 " +                       // -1 表示库存不足
        "end " +
        // 3. 扣减库存 + 记录用户
        "redis.call('decr', KEYS[1]) " +
        "redis.call('sadd', KEYS[2], ARGV[1]) " +
        "return 0";                             // 0 表示成功

    public SeckillResult seckill(Long seckillId, Long userId) {
        String stockKey = STOCK_KEY + seckillId;
        String userSetKey = USER_SET_KEY + seckillId;

        // 执行 Lua 脚本（原子操作）
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(SECKILL_SCRIPT, Long.class),
                Arrays.asList(stockKey, userSetKey),
                userId.toString());

        if (result == null) throw new BizException("系统异常");
        if (result == -2) throw new BizException("您已参与过本次秒杀");
        if (result == -1) throw new BizException("商品已售罄");

        // 扣减成功，发异步消息创建订单（削峰）
        SeckillMessage msg = new SeckillMessage(seckillId, userId);
        mqTemplate.sendOneWay("SECKILL_ORDER_TOPIC", JSON.toJSONString(msg));

        return SeckillResult.accepted();     // 返回「排队中」，前端轮询结果
    }
}`)+

'<h4>为什么必须用 Lua 脚本</h4>'+
'<div class="cols">'+
'<div class="col bad"><h5>❌ 分步操作（有并发问题）</h5>'+
PRE("java",
`// 两个线程同时执行：
Long stock = redis.get(stockKey);   // 都读到 1
if (stock > 0) {
    // 都判断通过 → 超卖！
    redis.decr(stockKey);           // 变成 -1
}`)+
'</div>'+
'<div class="col good"><h5>✅ Lua 脚本（原子）</h5>'+
PRE("java",
`// Redis 单线程执行 Lua，
// 判断和扣减是一个原子整体，
// 不存在中间状态被其他请求插入`)+
'</div></div>'+

BOX("interview","秒杀场景的追问清单",
'<p><strong>Q：Redis 扣减成功但消息发送失败怎么办？</strong><br>'+
'A：用本地消息表（事务消息）——在同一事务里写「扣减记录」和「待发消息」，后台定时任务补偿发送。或者先落库再扣 Redis。</p>'+
'<p><strong>Q：Redis 挂了怎么办？</strong><br>'+
'A：Redis 集群 + 哨兵保证高可用；极端情况降级为「直接查库 + 数据库行锁」，牺牲性能保正确性。同时用 <code>UPDATE ... WHERE stock > 0</code> 做最终兜底，绝不允许超卖。</p>'+
'<p><strong>Q：怎么防止同一用户重复抢？</strong><br>'+
'A：Lua 脚本里的 <code>SISMEMBER</code> 判断（如上）；数据库层再加 <code>UNIQUE KEY (seckill_id, user_id)</code> 唯一索引兜底。</p>'+
'<p><strong>Q：前端怎么知道结果？</strong><br>'+
'A：异步下单场景下，立即返回「排队中」，前端轮询订单结果接口（或长连接推送）。</p>','🎯')
    },
    {
      id: "s6-2",
      title: "6.2 分布式锁的三种实现与选型",
      html:
'<div class="tbl-wrap"><table><thead><tr><th>实现</th><th>原理</th><th>优点</th><th>缺点</th><th>适用</th></tr></thead><tbody>'+
'<tr><td><strong>Redis</strong></td><td>SETNX + 过期时间</td><td>性能最高（10万+ QPS）、实现简单</td><td>主从切换可能丢锁；非强一致</td><td>绝大多数场景</td></tr>'+
'<tr><td><strong>ZooKeeper</strong></td><td>临时顺序节点 + Watch</td><td><strong>强一致</strong>、锁释放及时（会话断开自动删节点）</td><td>性能较低、依赖 ZK 集群</td><td>对一致性要求极高</td></tr>'+
'<tr><td><strong>数据库</strong></td><td>唯一索引 / <code>SELECT ... FOR UPDATE</code></td><td>实现最简单、无需额外组件</td><td>性能差、易死锁、DB 压力大</td><td>并发极低或已有 DB 兜底</td></tr>'+
'</tbody></table></div>'+

'<h4>Redis 分布式锁的三个致命问题与解法</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>问题</th><th>场景</th><th>解法</th></tr></thead><tbody>'+
'<tr><td><strong>误删别人的锁</strong></td><td>A 超时释放，B 拿到锁，A 执行完删了 B 的锁</td><td>value 存 UUID+线程ID，删除前用 <strong>Lua 脚本校验</strong></td></tr>'+
'<tr><td><strong>锁过期业务未完</strong></td><td>业务跑了 15s，锁 10s 就过期了</td><td><strong>看门狗自动续期</strong>（Redisson 每 10s 续到 30s）</td></tr>'+
'<tr><td><strong>主从切换丢锁</strong></td><td>主节点写入锁后宕机，锁还没同步到从，从升主后另一线程拿到锁</td><td>RedLock（多节点投票）；<strong>或接受该风险 + 业务兜底</strong></td></tr>'+
'</tbody></table></div>'+

'<h4>手写一个正确的 Redis 锁（面试手写题）</h4>'+
PRE("java",
`/**
 * 正确的 Redis 分布式锁实现
 * 要点：唯一 value + Lua 原子删除 + 续期
 */
public class RedisLock {

    private static final String UNLOCK_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "   return redis.call('del', KEYS[1]) " +
        "else " +
        "   return 0 " +
        "end";

    private final StringRedisTemplate redis;
    private final String lockKey;
    /** 唯一标识：UUID + 线程ID，确保只删自己的锁 */
    private final String lockValue;
    /** 锁过期时间（ms） */
    private final long expireMs;
    /** 续期任务 */
    private ScheduledExecutorService renewer;

    public RedisLock(StringRedisTemplate redis, String lockKey, long expireMs) {
        this.redis = redis;
        this.lockKey = lockKey;
        this.expireMs = expireMs;
        this.lockValue = UUID.randomUUID() + ":" + Thread.currentThread().getId();
    }

    /** 加锁：SET key value NX PX expireMs （一条命令保证原子性） */
    public boolean tryLock(long waitMs) {
        long deadline = System.currentTimeMillis() + waitMs;
        do {
            Boolean ok = redis.opsForValue().setIfAbsent(
                    lockKey, lockValue, Duration.ofMillis(expireMs));
            if (Boolean.TRUE.equals(ok)) {
                startRenewal();          // 启动看门狗续期
                return true;
            }
            try {
                Thread.sleep(50);        // 自旋重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return false;
    }

    /** 解锁：Lua 脚本保证「校验 + 删除」原子性 */
    public void unlock() {
        stopRenewal();
        redis.execute(new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                Collections.singletonList(lockKey), lockValue);
    }

    /** 看门狗：每 expireMs/3 续期一次 */
    private void startRenewal() {
        renewer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lock-renewer");
            t.setDaemon(true);
            return t;
        });
        renewer.scheduleAtFixedRate(() -> {
            // 只有锁还在自己手上才续期
            String script =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "   return redis.call('pexpire', KEYS[1], ARGV[2]) " +
                "else return 0 end";
            redis.execute(new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(lockKey),
                    lockValue, String.valueOf(expireMs));
        }, expireMs / 3, expireMs / 3, TimeUnit.MILLISECONDS);
    }

    private void stopRenewal() {
        if (renewer != null) renewer.shutdownNow();
    }
}`)+

BOX("tip","生产建议：直接用 Redisson",
'<p>上面这段代码是「理解原理」用的。<strong>生产上直接用 Redisson</strong>，它已经正确实现了这些细节，并且提供了可重入锁、公平锁、读写锁、联锁（MultiLock）等高级能力。</p>'+
'<pre style="margin:8px 0"><code>RLock lock = redissonClient.getLock("order:" + orderId);<br>lock.lock(10, TimeUnit.SECONDS);   // 注意：指定了 leaseTime 就没有看门狗<br>try { /* 业务 */ } finally { lock.unlock(); }</code></pre>'+
'<p><strong>关键坑</strong>：<code>lock()</code> 不传 leaseTime 才有看门狗自动续期；传了 leaseTime 就固定过期，业务超时会导致锁提前释放。</p>','💡')+

QA(1, "Redis 分布式锁有什么问题？怎么解决？",
'<p>三个核心问题：</p>'+
'<p>① <strong>误删别人的锁</strong>：A 业务超时锁自动释放，B 拿到锁，A 执行完后删除 key 把 B 的锁删了。解法：value 存唯一标识（UUID+线程ID），删除前用 Lua 脚本校验 value 是否匹配。</p>'+
'<p>② <strong>锁提前过期</strong>：业务执行时间超过锁过期时间。解法：看门狗机制自动续期（Redisson 默认 30 秒，每 10 秒续一次）。</p>'+
'<p>③ <strong>主从切换丢锁</strong>：主节点加锁后未同步就宕机，从节点升主后其他线程能拿到同一把锁。解法：RedLock 对多个独立 Master 加锁，超过半数成功才算成功；但 RedLock 本身有争议（Martin Kleppmann 质疑其依赖时钟），很多团队选择接受该风险并用业务幂等兜底。</p>')+

QA(2, "为什么推荐 Redisson 而不是自己实现？",
'<p>因为自己实现容易漏掉细节：Lua 原子性、唯一 value、看门狗续期、可重入（同一线程多次加锁要计数）、锁释放通知其他等待线程。Redisson 用 <code>RMap + 发布订阅</code> 实现了阻塞等待（避免无脑自旋消耗 CPU），并正确处理了上述所有边界情况。</p>')
    },
    {
      id: "s6-3",
      title: "6.3 接口性能优化：从 3 秒到 50 毫秒",
      html:
'<p>面试场景题：「我们有个接口响应 3 秒，你怎么优化？」这是我建议你准备的标准答题框架——<strong>先定位，再分层优化</strong>。</p>'+

'<h4>第一步：定位瓶颈（不能上来就优化）</h4>'+
'<ol class="steps">'+
'<li><div class="st-t">看监控指标</div>QPS、RT、错误率、CPU、内存、GC 频率、连接池等待数。先确认是「整体慢」还是「某段时间慢」。</li>'+
'<li><div class="st-t">看调用链</div>用 SkyWalking / Zipkin 看这一次请求在哪个环节耗时最长——是 DB、Redis、还是下游 RPC。</li>'+
'<li><div class="st-t">看慢 SQL 日志</div>如果瓶颈在 DB，开启慢查询日志（<code>long_query_time=1</code>），或 MySQL 的 <code>performance_schema</code>。</li>'+
'<li><div class="st-t">看线程栈</div><code>jstack</code> 看线程在等什么（等锁？等 IO？等连接池？）。</li>'+
'</ol>'+

'<h4>第二步：分层优化清单</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>层级</th><th>优化手段</th><th>预期收益</th></tr></thead><tbody>'+
'<tr><td><strong>SQL 层</strong></td><td>加索引、避免 <code>SELECT *</code>、避免函数作用于索引列、用 <code>EXPLAIN</code> 检查执行计划、大表分页用「游标分页」而非 <code>LIMIT 100000,10</code></td><td>最高，常见 10-100 倍</td></tr>'+
'<tr><td><strong>数据库层</strong></td><td>读写分离、分库分表、冗余字段避免 join</td><td>高</td></tr>'+
'<tr><td><strong>缓存层</strong></td><td>热点数据加 Redis、本地缓存 Caffeine、多级缓存</td><td>高</td></tr>'+
'<tr><td><strong>应用层</strong></td><td>无状态化便于扩容、合理线程池、异步化（不阻塞主流程）</td><td>中</td></tr>'+
'<tr><td><strong>并发层</strong></td><td>串行改并行（<code>CompletableFuture</code> 聚合多个 RPC）</td><td>中-高</td></tr>'+
'<tr><td><strong>数据量层</strong></td><td>减少返回字段（DTO 裁剪）、分页、按需加载</td><td>中</td></tr>'+
'<tr><td><strong>JVM 层</strong></td><td>合理堆大小、更换 GC（G1/ZGC）、减少对象创建</td><td>中</td></tr>'+
'</tbody></table></div>'+

'<h4>实战：串行改并行（最见效的手段之一）</h4>'+
PRE("java",
`// 优化前：串行调用 3 个 RPC，总耗时 = 200 + 300 + 150 = 650ms
public OrderDetailVO getOrderDetail(Long orderId) {
    Order order = orderService.get(orderId);                 // 200ms
    User user = userService.get(order.getUserId());          // 300ms
    List<Item> items = itemService.list(orderId);            // 150ms
    return assemble(order, user, items);
}

// 优化后：并行调用，总耗时 = max(200, 300, 150) = 300ms
public OrderDetailVO getOrderDetail(Long orderId) {
    Order order = orderService.get(orderId);                 // 必须先拿到 order 才能并行

    // 用 CompletableFuture 并行执行两个独立调用
    CompletableFuture<User> userFuture = CompletableFuture
            .supplyAsync(() -> userService.get(order.getUserId()), bizExecutor);
    CompletableFuture<List<Item>> itemsFuture = CompletableFuture
            .supplyAsync(() -> itemService.list(orderId), bizExecutor);

    // 等待两个都完成（设置超时，避免无限等待）
    try {
        CompletableFuture.allOf(userFuture, itemsFuture)
                .get(500, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        // 超时降级：返回不含用户信息的订单（保证核心可用）
        userFuture.cancel(true);
        return assemble(order, null, itemsFuture.getNow(Collections.emptyList()));
    } catch (Exception e) {
        throw new BizException("查询失败");
    }

    return assemble(order, userFuture.join(), itemsFuture.join());
}

// ===== 完整的类结构（线程池作为字段声明在使用之前）=====
@Service
public class OrderDetailService {

    private final OrderService orderService;
    private final UserService userService;
    private final ItemService itemService;

    /** 必须用自定义线程池：默认的 ForkJoinPool.commonPool 线程数只有 CPU 核数-1，
     *  且被全 JVM 共享，IO 密集任务会互相影响。 */
    private final Executor bizExecutor = new ThreadPoolExecutor(
            16, 32, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadFactoryBuilder().setNameFormat("biz-async-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public OrderDetailService(OrderService orderService, UserService userService,
                              ItemService itemService) {
        this.orderService = orderService;
        this.userService = userService;
        this.itemService = itemService;
    }

    public OrderDetailVO getOrderDetail(Long orderId) {
        Order order = orderService.get(orderId);        // 必须先拿到 order 才能并行

        // 并行执行两个独立调用
        CompletableFuture<User> userFuture = CompletableFuture
                .supplyAsync(() -> userService.get(order.getUserId()), bizExecutor);
        CompletableFuture<List<Item>> itemsFuture = CompletableFuture
                .supplyAsync(() -> itemService.list(orderId), bizExecutor);

        try {
            CompletableFuture.allOf(userFuture, itemsFuture)
                    .get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            userFuture.cancel(true);
            return assemble(order, null, itemsFuture.getNow(Collections.emptyList()));
        } catch (Exception e) {
            throw new BizException("查询失败");
        }
        return assemble(order, userFuture.join(), itemsFuture.join());
    }
}`)+

BOX("danger","CompletableFuture 的三个坑",
'<p><strong>① 必须用自定义线程池。</strong>默认 <code>ForkJoinPool.commonPool()</code> 的线程数是 CPU 核数-1，且被全 JVM 共享，IO 密集任务会互相影响。</p>'+
'<p><strong>② 必须设置超时。</strong><code>allOf().get()</code> 无超时会永久阻塞，下游服务挂了你的接口也挂了。</p>'+
'<p><strong>③ 异常处理。</strong>CompletableFuture 里的异常会被包装成 <code>CompletionException</code>，必须 <code>exceptionally()</code> 或 <code>handle()</code> 处理，否则静默丢失。</p>','⚠️')+

'<h4>实战：大表深度分页优化</h4>'+
PRE("sql",
`-- 慢：LIMIT 1000000, 10 会扫描并丢弃前 100 万行
SELECT * FROM t_order ORDER BY id LIMIT 1000000, 10;

-- 快：游标分页（记住上次的最大 id），只扫描 10 行
SELECT * FROM t_order WHERE id > 1000000 ORDER BY id LIMIT 10;

-- 或者用子查询先定位 id（覆盖索引避免回表）
SELECT o.* FROM t_order o
JOIN (SELECT id FROM t_order ORDER BY id LIMIT 1000000, 10) t ON o.id = t.id;`)+

BOX("ok","面试答题框架（背这个结构）",
'<p>「我一般按四步走：</p>'+
'<p><strong>第一步定位</strong>——先看监控（RT、QPS、GC）和调用链，确定瓶颈在哪一层，不能凭感觉优化。</p>'+
'<p><strong>第二步分层排查</strong>——如果是 DB，看慢 SQL 和 <code>EXPLAIN</code>；如果是应用，看线程栈在等什么；如果是下游，看 RPC 耗时分布。</p>'+
'<p><strong>第三步优化</strong>——优先做收益最大的：加索引、加缓存、串行改并行、异步化、减少数据量。</p>'+
'<p><strong>第四步验证</strong>——用压测（JMeter / wrk）对比优化前后，并观察监控指标确认没有引入新问题（比如缓存导致数据不一致）。</p>'+
'<p>实际做过的一个案例：一个订单详情接口 800ms，通过调用链发现 3 个 RPC 串行执行占了 650ms，用 CompletableFuture 并行化后降到 300ms，再加 Redis 缓存用户信息后稳定在 80ms。」</p>','✅')
    },
    {
      id: "s6-4",
      title: "6.4 JVM 与 Tomcat 生产调优",
      html:
'<h4>容器化环境的 JVM 参数（Boot 3.x + JDK 17）</h4>'+
PRE("bash",
`# 推荐参数（容器内，JDK 17）
java -jar app.jar \\
  # ===== 内存：容器环境下建议显式指定，不要依赖默认 1/4 =====
  -Xms2g -Xmx2g \\                       # 堆固定大小，避免动态扩缩容抖动
  -XX:MaxMetaspaceSize=512m \\           # 元空间上限，防类加载泄露
  -XX:MaxDirectMemorySize=512m \\        # 堆外内存上限（Netty/NIO 用）
  # ===== GC：G1 是 JDK 17 默认，一般不用改 =====
  -XX:+UseG1GC \\
  -XX:MaxGCPauseMillis=200 \\            # 目标停顿时间
  -XX:G1HeapRegionSize=8m \\
  -XX:InitiatingHeapOccupancyPercent=45 \\ # 触发并发 GC 的堆占用比
  # ===== OOM 现场保留（关键！）=====
  -XX:+HeapDumpOnOutOfMemoryError \\
  -XX:HeapDumpPath=/var/log/app/heap.hprof \\
  -XX:+ExitOnOutOfMemoryError \\         # OOM 后立即退出，让 K8s 重启（避免僵死）
  # ===== 其他 =====
  -XX:+UseContainerSupport \\            # 识别容器内存限制（JDK 10+ 默认开启）
  -Duser.timezone=Asia/Shanghai \\
  -Dfile.encoding=UTF-8`)+

BOX("interview","为什么容器里必须显式设 -Xmx",
'<p>JDK 10 之前，JVM 识别不到容器的 cgroup 内存限制，会按<strong>物理机内存</strong>的 1/4 来设堆，导致堆远超容器限制，被 K8s OOMKilled。</p>'+
'<p>JDK 10+ 通过 <code>UseContainerSupport</code>（默认开启）能正确识别，但仍然建议<strong>显式指定</strong>，因为：① 堆之外的元空间、直接内存、线程栈也占内存，总内存 = 堆 + 元空间 + 直接内存 + 栈 × 线程数 + JVM 自身开销，容器 limit 要留足余量（一般堆占 limit 的 60-70%）；② 显式设置避免不同 JDK 版本默认行为差异。</p>'+
'<p><strong>反例</strong>：容器 limit 4G，设 <code>-Xmx4g</code>，结果堆还没满就被 OOMKilled（因为还有其他内存占用）。</p>','🎯')+

'<h4>Tomcat 线程池调优</h4>'+
PRE("yaml",
`server:
  tomcat:
    # 最大工作线程数：默认 200
    threads:
      max: 400
      min-spare: 50                # 最小空闲线程，默认 10
    # 最大连接数：默认 8192，表示能同时接受多少连接
    max-connections: 10000
    # 等待队列长度：默认 100，满了会拒绝连接
    accept-count: 200
    # 最大请求头大小
    max-http-header-size: 16KB
    # 连接超时（ms）
    connection-timeout: 20000
  # 优雅停机：等待正在处理的请求完成
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s`)+

'<h4>Tomcat 三个关键参数的关系（面试常问）</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>参数</th><th>含义</th><th>满了会怎样</th></tr></thead><tbody>'+
'<tr><td><code>max-connections</code></td><td>能同时建立的最大连接数（含 keep-alive 空闲连接）</td><td>超过则新连接被放入 <code>accept-count</code> 队列</td></tr>'+
'<tr><td><code>accept-count</code></td><td>TCP 已连接但未被处理的等待队列</td><td>超过则客户端连接被拒绝（connection reset）</td></tr>'+
'<tr><td><code>threads.max</code></td><td>真正处理请求的工作线程数</td><td>请求在队列中等待，等不到则超时</td></tr>'+
'</tbody></table></div>'+
'<p class="mini">关系：<code>max-connections</code> >= <code>threads.max</code> + <code>min-spare</code>，否则线程数成为瓶颈。</p>'+

BOX("tip","线程数怎么定",
'<p>Tomcat 默认 200 线程对绝大多数场景够用。<strong>加大线程数不总是好事</strong>——线程上下文切换有成本，且每个线程占约 1MB 栈空间。</p>'+
'<p>实践方法：压测找到「QPS 不再增长但响应时间开始上升」的拐点，那个点的线程数就是最优值。一般 IO 密集的服务配 200-500，CPU 密集的 50-100 就够。</p>'+
'<p><strong>Boot 3.2 + JDK 21 的虚拟线程</strong>：如果开启了 <code>spring.threads.virtual.enabled=true</code>，Tomcat 会用虚拟线程处理请求，此时 <code>threads.max</code> 的约束就被放宽了（虚拟线程可以创建百万级），IO 密集场景吞吐提升明显。</p>','💡')+

'<h4>优雅停机（生产必须配置）</h4>'+
'<p>K8s 滚动更新时，如果不配置优雅停机，正在处理的请求会被直接断开，用户看到 502。</p>'+
PRE("yaml",
`server:
  shutdown: graceful            # 开启优雅停机
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # 最多等待 30 秒`)+
'<p>配合 K8s 的 <code>preStop</code> 钩子（sleep 几秒等负载均衡摘除），才能实现真正的无损发布。</p>'
    },
    {
      id: "s6-5",
      title: "6.5 云原生部署与 GraalVM 原生镜像",
      html:
'<h4>Dockerfile 最佳实践（分层构建）</h4>'+
PRE("bash",
`# ===== 阶段一：构建 =====
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
# 先只复制 pom.xml，利用 Docker 层缓存加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B
# 再复制源码编译
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== 阶段二：运行（镜像小得多）=====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安全：用非 root 用户运行（K8s 安全基线要求）
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /build/target/*.jar app.jar

# 时区
ENV TZ=Asia/Shanghai

# JVM 参数通过环境变量注入，便于灵活调整
ENV JAVA_OPTS="-Xms1g -Xmx1g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"

EXPOSE 8080
# 用 exec 形式（不是 shell 形式），确保 JVM 是 PID 1，能正确接收 SIGTERM
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]`)+

BOX("danger","Docker 部署的三个坑",
'<p><strong>① PID 1 问题。</strong>如果用 <code>ENTRYPOINT java -jar app.jar</code> 的 shell 形式，JVM 不是 PID 1，收不到 <code>SIGTERM</code>，优雅停机会失效。<strong>必须用 exec 形式</strong>（JSON 数组），或加 <code>--init</code>。</p>'+
'<p><strong>② 时区错误。</strong>容器默认 UTC，日志时间比北京时间少 8 小时，排查问题时会看错时间线。<strong>必须设 <code>TZ=Asia/Shanghai</code></strong> 或 JVM 参数 <code>-Duser.timezone</code>。</p>'+
'<p><strong>③ 用 root 运行。</strong>违反安全基线，容器逃逸风险高。必须创建非 root 用户。</p>','⚠️')+

'<h4>GraalVM 原生镜像（Boot 3.x 正式支持）</h4>'+
'<p>把 Java 应用编译成<strong>本地机器码</strong>，启动时间从 2 秒降到 50 毫秒，内存占用减半。适合 Serverless / FaaS / 快速扩容场景。</p>'+
PRE("bash",
`# 1. 引入 AOT 插件
mvn -Pnative native:compile

# 2. 或者用 Buildpacks 直接构建镜像（无需本地装 GraalVM）
mvn spring-boot:build-image -Pnative

# 3. 启动时间对比
# 传统 JVM:  Started in 2.135 seconds
# 原生镜像:  Started in 0.058 seconds`)+

'<div class="tbl-wrap"><table><thead><tr><th>维度</th><th>JVM 模式</th><th>原生镜像</th></tr></thead><tbody>'+
'<tr><td>启动时间</td><td>1-3 秒</td><td><strong>30-100 毫秒</strong></td></tr>'+
'<tr><td>内存占用</td><td>200MB+</td><td><strong>50-80MB</strong></td></tr>'+
'<tr><td>峰值吞吐</td><td><strong>更高</strong>（JIT 优化）</td><td>略低</td></tr>'+
'<tr><td>构建时间</td><td>几十秒</td><td><strong>几分钟</strong></td></tr>'+
'<tr><td>兼容性</td><td>无限制</td><td><strong>反射/动态代理需配置</strong>，部分库不支持</td></tr>'+
'</tbody></table></div>'+

BOX("warn","原生镜像的限制",
'<p>核心是<strong>AOT（提前编译）无法处理运行时的动态特性</strong>：</p>'+
'<p>① <strong>反射</strong>：需要 <code>reflect-config.json</code> 声明，或 Spring 的 AOT 处理器自动推断（但自定义反射仍需手工配置）。</p>'+
'<p>② <strong>动态代理</strong>：CGLIB 代理在原生镜像里有兼容问题。</p>'+
'<p>③ <strong>延迟加载 / 条件装配</strong>：构建时就固定了，运行时无法改变。</p>'+
'<p>④ <strong>第三方库兼容</strong>：一些依赖字节码操作的库（如老版本 MyBatis、部分监控 Agent）不支持。</p>'+
'<p>所以原生镜像更适合<strong>无状态、依赖简单</strong>的服务；复杂业务系统仍建议用 JVM 模式。</p>','⚠️')

    }
  ]
});

/* ============================================================
   DAY 7：面试突击与项目包装
   ============================================================ */
CHAPTERS.push({
  id: "day7",
  badge: "D7",
  badgeLabel: "Day 7",
  badgeType: "warm",
  title: "面试突击与项目包装 · 高频八股 + 场景题速查",
  intro: "最后一天做两件事：把高频八股过一遍形成条件反射，以及把你的 12 年经验包装成面试官想听的叙事。这一章的内容建议反复读，面试前一晚再看一遍。",
  sections: [
    {
      id: "s7-1",
      title: "7.1 高频八股速查表（30 题一问一答）",
      html:
'<p>下面这些是 Spring Boot 相关面试出现频率最高的 30 题。每题给的都是<strong>可以直接说出口的答法</strong>，不是知识点罗列。</p>'+

QA(1, "Spring Boot 和 Spring 的区别？",
'<p>Spring 是一个 IoC/AOP 容器框架，需要大量手动配置（XML 或 Java Config）。Spring Boot 建立在 Spring 之上，通过<strong>自动配置、起步依赖、内嵌容器</strong>三大机制简化开发，做到开箱即用。</p>'+
'<p>更本质地说：<strong>Spring Boot 不是替代 Spring，而是 Spring 的「脚手架」</strong>——它帮你把 Spring 的配置工作自动化了。Boot 里的所有能力最终都落在 Spring 的 ApplicationContext 上。</p>')+

QA(2, "Spring Boot 的优缺点？",
'<p><strong>优点</strong>：① 快速构建独立应用，无需外置容器；② 自动配置省去大量样板代码；③ 起步依赖解决版本冲突；④ Actuator 提供开箱即用的监控；⑤ 生态丰富，与 Spring Cloud 无缝集成。</p>'+
'<p><strong>缺点</strong>：① 自动配置是「黑盒」，出问题排查成本高（需要看自动配置报告）；② 依赖体积大，启动较慢；③ 版本升级可能引入不兼容（如 2.x 到 3.x 的 jakarta 包名变更）；④ 过度依赖约定，团队不规范时容易混乱。</p>')+

QA(3, "Spring Boot 自动配置原理？",
'<p>（详见 Day 1.2）核心链路：<code>@EnableAutoConfiguration</code> → <code>@Import(AutoConfigurationImportSelector)</code> → <code>SpringFactoriesLoader</code> 读取 <code>AutoConfiguration.imports</code>（3.x）/ <code>spring.factories</code>（2.x）→ 去重排除 → <code>@Conditional</code> 系列条件裁决 → 生效的配置类注册为 BeanDefinition。</p>')+

QA(4, "如何自定义 starter？",
'<p>（详见 Day 1.3）四步：① 建 <code>xxx-spring-boot-starter</code> 和 <code>xxx-spring-boot-autoconfigure</code> 两个模块；② 定义 <code>@ConfigurationProperties</code> 属性类；③ 写 <code>@AutoConfiguration</code> 配置类，用 <code>@ConditionalOnMissingBean</code> 保证用户优先；④ 在 <code>META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports</code> 注册。</p>')+

QA(5, "Spring Boot 启动流程？",
'<p>（详见 Day 1.4）分构造和 run 两阶段。run 阶段核心：发布 Starting 事件 → 准备 Environment → 创建 ApplicationContext → prepareContext → <strong>refreshContext（自动配置生效、onRefresh 创建 Tomcat、finishBeanFactoryInitialization 实例化 Bean）</strong> → 发布 Started 事件 → 执行 Runner → 发布 Ready 事件。</p>')+

QA(6, "Bean 的生命周期？",
'<p>（详见 Day 2.1）12 步：BeanDefinition 注册 → BeanFactoryPostProcessor → 实例化前 BPP → 实例化（构造器注入）→ 属性填充（字段注入）→ Aware 回调 → BPP 前置（@PostConstruct）→ afterPropertiesSet + init-method → <strong>BPP 后置（AOP 代理生成）</strong> → 注册销毁回调 → 使用 → 销毁。</p>')+

QA(7, "Spring 如何解决循环依赖？为什么需要三级缓存？",
'<p>（详见 Day 2.2）三级缓存：<code>singletonObjects</code>（成品）、<code>earlySingletonObjects</code>（半成品）、<code>singletonFactories</code>（ObjectFactory）。流程是「先实例化空对象并暴露工厂 → 填充属性时从三级缓存拿到早期引用 → 完成初始化」。</p>'+
'<p><strong>三级缓存的核心价值是延迟 AOP 代理的决策</strong>：如果只有二级缓存，就必须在实例化后立即决定是否代理，但那时无法判断（需要切面匹配信息）。用 ObjectFactory 把决策推迟到「真正被需要」时，保证无论是否循环依赖都拿到同一个代理对象。</p>')+

QA(8, "Bean 作用域有哪些？单例 Bean 线程安全吗？",
'<p>作用域：singleton（默认）、prototype、request、session、application、websocket。</p>'+
'<p>单例 Bean <strong>不保证线程安全</strong>。Spring 只保证只有一个实例。安全性取决于有无可变状态——只注入 final 依赖、用局部变量的「无状态 Bean」是安全的；有可变成员变量则不安全。实践规范：Controller/Service/DAO 一律无状态。</p>')+

QA(9, "BeanFactory 和 ApplicationContext 的区别？",
'<p><code>BeanFactory</code> 是最基础的 IoC 容器接口，提供 Bean 的获取与管理，<strong>懒加载</strong>（getBean 时才创建）。</p>'+
'<p><code>ApplicationContext</code> 继承自 BeanFactory，是完整容器，增加了：<strong>启动时预实例化单例 Bean</strong>、国际化（MessageSource）、事件发布（ApplicationEventPublisher）、资源加载（ResourcePatternResolver）、AOP 集成等企业级能力。</p>'+
'<p>一句话：<strong>BeanFactory 是发动机，ApplicationContext 是整车</strong>。</p>')+

QA(10, "Spring AOP 的实现原理？JDK 代理和 CGLIB 的区别？",
'<p>原理：基于动态代理，在目标方法前后织入增强逻辑。</p>'+
'<p><strong>JDK 动态代理</strong>：要求目标类实现接口，通过 <code>InvocationHandler</code> 生成实现同一接口的代理类。<strong>CGLIB</strong>：通过 ASM 生成目标类的子类并覆盖方法，无需接口，但无法代理 final 类和 final 方法。</p>'+
'<p>Spring Boot 2.x 起，<code>AopAutoConfiguration</code> 默认 <code>proxyTargetClass=true</code>，即<strong>默认使用 CGLIB</strong>。</p>')+

QA(11, "AOP 有哪些通知类型？执行顺序？",
'<p>五种：<code>@Before</code>（前置）、<code>@AfterReturning</code>（返回后）、<code>@AfterThrowing</code>（异常后）、<code>@After</code>（最终，相当于 finally）、<code>@Around</code>（环绕，功能最强）。</p>'+
'<p>AspectJ 5.2.7+ 的执行顺序：<code>@Around</code> 前半 → <code>@Before</code> → 目标方法 → <code>@AfterReturning</code> / <code>@AfterThrowing</code> → <code>@After</code> → <code>@Around</code> 后半。</p>'+
'<p>多个切面时用 <code>@Order</code> 或实现 <code>Ordered</code> 控制顺序，<strong>值越小优先级越高</strong>。</p>')+

QA(12, "@Transactional 什么时候失效？",
'<p>（详见 Day 4.2）八种：自调用、非 public 方法、final/static 方法、异常被 try-catch 吞掉、抛受检异常未配 rollbackFor、类未被 Spring 管理、数据库引擎不支持（MyISAM）、多线程调用。</p>')+

QA(13, "事务传播行为？REQUIRED 和 REQUIRES_NEW 区别？",
'<p>（详见 Day 4.1）七种传播行为。REQUIRED 复用外层事务，内层失败导致全部回滚；REQUIRES_NEW 挂起外层、用新连接开独立事务，外层回滚不影响内层。NESTED 用同一连接的 Savepoint 实现。</p>')+

QA(14, "Spring 事务的隔离级别？",
'<p>五种：<code>DEFAULT</code>（用数据库默认）、<code>READ_UNCOMMITTED</code>（脏读）、<code>READ_COMMITTED</code>（不可重复读）、<code>REPEATABLE_READ</code>（幻读，MySQL 默认）、<code>SERIALIZABLE</code>（串行）。</p>'+
'<p>注意：Spring 的隔离级别是通过设置数据库连接的 <code>transactionIsolation</code> 实现的，<strong>是否真正生效取决于数据库驱动</strong>。MySQL InnoDB 的 RR 级别通过 MVCC + 间隙锁基本解决了幻读。</p>')+

QA(15, "Spring MVC 请求处理流程？",
'<p>（详见 Day 0.4）Tomcat 接收 → DispatcherServlet#doDispatch → HandlerMapping 找 Handler → HandlerAdapter → 拦截器 preHandle → 参数解析（ArgumentResolver）→ 执行 Controller → 返回值处理（ReturnValueHandler）→ 拦截器 postHandle → 异常处理（若有）→ afterCompletion。</p>')+

QA(16, "拦截器和过滤器的区别？",
'<p>（详见 Day 3.3）Filter 是 Servlet 规范，在 DispatcherServlet 之前，拿不到 Handler 信息；Interceptor 是 Spring MVC 的，在 DispatcherServlet 之内，能拿到 HandlerMethod 和方法注解；AOP 作用在方法级别，能拿到参数和返回值。</p>')+

QA(17, "如何做全局异常处理？",
'<p>用 <code>@RestControllerAdvice</code> + <code>@ExceptionHandler</code>。要点：分类处理不同异常（校验异常返回 400、业务异常返回业务码、其他返回 500）；<strong>必须有 <code>@ExceptionHandler(Exception.class)</code> 兜底</strong>防止堆栈泄露；日志打完整堆栈但返回信息脱敏。</p>')+

QA(18, "参数校验怎么做？@Valid 和 @Validated 的区别？",
'<p>用 JSR-303 注解（<code>@NotNull</code>、<code>@NotBlank</code>、<code>@Size</code> 等）+ <code>spring-boot-starter-validation</code>。</p>'+
'<p><code>@Valid</code> 是 JSR-303 标准注解；<code>@Validated</code> 是 Spring 的，<strong>额外支持分组校验</strong>。嵌套对象校验必须加 <code>@Valid</code>，否则内部校验静默失效。</p>')+

QA(19, "Spring Boot 配置加载优先级？",
'<p>（详见 Day 1.5）从高到低：命令行参数 > Java 系统属性 > 环境变量 > 外部 config/ 目录 > 外部同级目录 > jar 内 config/ > jar 内根路径。<strong>外部配置优先于 jar 内部配置</strong>，这是配置与制品分离的基础。</p>')+

QA(20, "@ConfigurationProperties 和 @Value 的区别？",
'<p><code>@ConfigurationProperties</code> 支持批量绑定、松散绑定（<code>slow-threshold</code> 和 <code>slowThreshold</code> 都能绑）、复杂类型嵌套、JSR-303 校验、IDE 提示；<code>@Value</code> 只支持单个简单值加 SpEL。</p>'+
'<p>规范：<strong>一组有层次的配置用 ConfigurationProperties，零散单值用 @Value</strong>。</p>')+

QA(21, "Spring Boot 如何实现热部署？",
'<p>引入 <code>spring-boot-devtools</code>，修改代码后自动重启。原理是<strong>双类加载器</strong>：base ClassLoader 加载不变的第三方 jar，restart ClassLoader 加载自己写的类，重启时只丢弃 restart ClassLoader 重建，所以比冷启动快。</p>'+
'<p>注意：① devtools 只在开发环境使用，打包时会被自动排除（<code>&lt;optional&gt;true&lt;/optional&gt;</code>）；② 生产禁用，因为会带来安全和性能问题。</p>')+

QA(22, "Spring Boot 如何实现优雅停机？",
'<p>配置 <code>server.shutdown=graceful</code> 和 <code>spring.lifecycle.timeout-per-shutdown-phase=30s</code>。原理是收到 SIGTERM 后，先停止接收新请求，等待正在处理的请求完成（最多超时时间），再关闭容器和线程池。</p>'+
'<p>K8s 场景还需配合 <code>preStop</code> 钩子（sleep 5-10 秒），等 Service 摘除该 Pod 后再开始停机，否则会有请求打到正在关闭的 Pod 上。</p>')+

QA(23, "Spring Boot 如何做单元测试？",
'<p>核心注解：<code>@SpringBootTest</code>（启动完整容器）、<code>@WebMvcTest</code>（只加载 Web 层）、<code>@DataJpaTest</code>、<code>@MybatisTest</code>、<code>@MockBean</code>（替换容器中的 Bean）、<code>@TestConfiguration</code>。</p>'+
'<p>最佳实践：① <strong>切片测试</strong>优先（<code>@WebMvcTest</code> 比 <code>@SpringBootTest</code> 快得多）；② 用 <code>MockMvc</code> 测接口；③ 测试类加 <code>@Transactional</code> 自动回滚（注意这只对 JPA/JDBC 有效）；④ 外部依赖（DB、Redis、MQ）用 Testcontainers 启动真实容器。</p>')+

QA(24, "Spring Boot 的 Profile 怎么用？",
'<p>三种方式：① <code>spring.profiles.active=dev</code> 在配置文件中指定；② 启动参数 <code>--spring.profiles.active=prod</code>；③ 环境变量 <code>SPRING_PROFILES_ACTIVE</code>（容器化首选）。</p>'+
'<p>一个配置文件内可用 <code>---</code> 分隔多个文档块。代码中可用 <code>@Profile("dev")</code> 让某个 Bean 只在特定环境生效。</p>'+
'<p>进阶：<code>spring.profiles.include</code> 组合多个 profile；<code>spring.config.import</code>（Boot 2.4+）导入外部配置（如 Nacos、Config Server）。</p>')+

QA(25, "Spring Boot 如何处理跨域？",
'<p>三种方式：① <code>WebMvcConfigurer#addCorsMappings</code> 全局配置；② <code>@CrossOrigin</code> 注解级别；③ 网关层统一处理（生产推荐）。</p>'+
'<p>关键认知：跨域是<strong>浏览器行为</strong>（同源策略），Postman 不受限；复杂请求会先发 OPTIONS 预检，<strong>必须保证网关/安全框架放行 OPTIONS</strong>；<code>allowCredentials=true</code> 时 <code>allowedOrigins</code> 不能用 <code>*</code>，必须用 <code>allowedOriginPatterns</code>。</p>')+

QA(26, "Spring Boot 的 Actuator 有什么用？有什么安全风险？",
'<p>（详见 Day 5.4）提供健康检查、指标采集、动态日志级别调整、线程/堆转储等运维端点。</p>'+
'<p>安全风险：<code>/env</code> 泄露配置（含密码）、<code>/heapdump</code> 泄露内存数据、<code>/shutdown</code> 可远程停机。规范做法：独立内部端口、加认证、敏感项脱敏、关闭危险端点。</p>')+

QA(27, "Spring Boot 如何实现异步？@Async 的原理和坑？",
'<p>类上加 <code>@EnableAsync</code>，方法上加 <code>@Async</code>。原理是 AOP 代理，方法调用被拦截后提交到线程池执行。</p>'+
'<p>坑：① 自调用失效；② 非 public 方法失效；③ <strong>默认用 SimpleAsyncTaskExecutor 不复用线程，必须自定义线程池</strong>；④ 异常被静默吞掉，需配 <code>AsyncUncaughtExceptionHandler</code>；⑤ 与 <code>@Transactional</code> 组合时要注意事务边界，需要「事务提交后执行」应改用 <code>@TransactionalEventListener</code>。</p>')+

QA(28, "Spring Boot 如何做缓存？缓存三大问题？",
'<p>用 <code>@EnableCaching</code> + <code>@Cacheable/@CachePut/@CacheEvict</code>，底层可接 Redis / Caffeine / 多级缓存。</p>'+
'<p>三大问题（详见 Day 5.3）：<strong>穿透</strong>（查不存在的数据，解法：缓存空值 + 布隆过滤器）、<strong>击穿</strong>（热点 key 过期，解法：分布式锁 + 逻辑过期）、<strong>雪崩</strong>（大量 key 同时失效，解法：过期时间加随机值 + 多级缓存 + 高可用）。</p>')+

QA(29, "Spring Boot 3 相比 2 有哪些重大变化？",
'<p>（详见 Day 0.5）① JDK 基线提升到 17；② <code>javax.*</code> → <code>jakarta.*</code>；③ 自动配置注册文件改为 <code>AutoConfiguration.imports</code>；④ 引入 <code>@AutoConfiguration</code> 注解；⑤ HTTP 客户端新增 <code>RestClient</code>；⑥ 可观测性用 Micrometer Observation 替代 Sleuth；⑦ 支持 GraalVM 原生镜像；⑧ 支持虚拟线程；⑨ 循环依赖默认禁止。</p>')+

QA(30, "你项目中遇到过最难的问题是什么？怎么解决的？",
'<p>这是<strong>行为面试题</strong>，比技术题更能拉开差距。回答结构用 STAR：</p>'+
'<p><strong>S（背景）</strong>：一句话说清场景和影响面，如「生产环境订单接口偶发超时，影响约 3% 的下单请求」。</p>'+
'<p><strong>T（任务）</strong>：你的目标是定位并解决。</p>'+
'<p><strong>A（行动）</strong>：重点讲<strong>排查过程</strong>——看了哪些指标、排除了哪些可能、怎么定位到根因。这里要体现方法论（如「先看监控确认不是整体问题 → 用 traceId 找到具体慢在 DB → EXPLAIN 发现索引失效 → 原因是隐式类型转换」）。</p>'+
'<p><strong>R（结果）</strong>：量化收益，如「接口 P99 从 3.2s 降到 180ms，超时率降到 0.01%」。</p>'+
'<p><strong>加分点</strong>：最后补一句「事后我们做了两件事防止复发：加了慢 SQL 告警、把索引规范写进了代码评审清单」。这体现你有<strong>系统性改进意识</strong>，而不是只解决单点问题。</p>')
    },
    {
      id: "s7-2",
      title: "7.2 项目场景题答题框架",
      html:
'<p>大厂面试后半程几乎都是「场景题」。下面给几个高频场景的答题框架，你可以直接套用。</p>'+

'<h4>框架一：技术方案设计题</h4>'+
'<p>题目示例：「设计一个分布式 ID 生成器」「设计一个短链接系统」。</p>'+
'<ol class="steps">'+
'<li><div class="st-t">先问清需求边界</div>QPS 量级？是否需要严格递增？可用性要求（能否容忍重复）？ID 长度限制？<strong>这步最重要——面试官在考察你会不会先澄清需求。</strong></li>'+
'<li><div class="st-t">给出 2-3 个候选方案并对比</div>UUID（简单但无序、太长）、数据库自增（有序但依赖单点、性能差）、雪花算法（有序、高可用，但依赖时钟）、Redis INCR（高性能但依赖 Redis、需持久化）。</li>'+
'<li><div class="st-t">选定方案并说明理由</div>结合需求取舍，说明为什么选它。</li>'+
'<li><div class="st-t">补充异常与边界处理</div>时钟回拨怎么办？Redis 挂了怎么办？如何监控？</li>'+
'<li><div class="st-t">给出可扩展方向</div>如果 QPS 再涨 10 倍，怎么改？</li>'+
'</ol>'+

'<h4>框架二：线上问题排查题</h4>'+
'<p>题目示例：「线上接口突然变慢，你怎么排查？」</p>'+
'<div class="tbl-wrap"><table><thead><tr><th>步骤</th><th>动作</th><th>工具/命令</th></tr></thead><tbody>'+
'<tr><td>① 确认影响面</td><td>是个别实例还是全部？是某接口还是全部？什么时间开始的？</td><td>Grafana 看板、告警记录</td></tr>'+
'<tr><td>② 快速止血</td><td>若影响严重，先降级/回滚/扩容，再慢慢查根因</td><td>配置中心、发布系统</td></tr>'+
'<tr><td>③ 定位层次</td><td>CPU？内存？IO？网络？下游依赖？</td><td><code>top</code>、<code>jstat</code>、<code>iostat</code>、调用链</td></tr>'+
'<tr><td>④ 定位代码</td><td>线程栈看线程在等什么</td><td><code>jstack</code>、<code>arthas</code></td></tr>'+
'<tr><td>⑤ 根因分析</td><td>找到具体原因（慢 SQL、锁竞争、内存泄漏、GC 频繁）</td><td>堆转储分析、慢查询日志</td></tr>'+
'<tr><td>⑥ 修复+复盘</td><td>修复并加监控防止复发</td><td>—</td></tr>'+
'</tbody></table></div>'+

BOX("tip","Arthas 是大厂排查神器",
'<p>阿里的开源诊断工具，生产环境必备。几个高频命令：</p>'+
'<pre style="margin:8px 0"><code># 查看方法调用耗时（不修改代码！）<br>watch com.example.OrderService create "{params, returnObj}" -x 2 -b<br><br># 追踪调用链路耗时<br>trace com.example.OrderService create<br><br># 反编译线上代码，确认发布版本<br>jad com.example.OrderService<br><br># 查看方法调用统计<br>monitor -c 5 com.example.OrderService create</code></pre>','💡')+

'<h4>框架三：技术选型题</h4>'+
'<p>题目示例：「为什么用 RocketMQ 而不是 Kafka？」「为什么用 OceanBase 而不是 MySQL？」</p>'+
'<p><strong>答题要点</strong>：不要只说「A 比 B 好」，要说<strong>「在什么约束下选 A」</strong>。</p>'+
'<div class="tbl-wrap"><table><thead><tr><th>维度</th><th>要说的内容</th></tr></thead><tbody>'+
'<tr><td>业务需求</td><td>吞吐量、延迟、消息可靠性、顺序性、事务消息</td></tr>'+
'<tr><td>团队现状</td><td>已有技术栈、运维能力、学习成本</td></tr>'+
'<tr><td>成本</td><td>机器成本、人力成本、迁移成本</td></tr>'+
'<tr><td>风险</td><td>社区活跃度、是否有大厂背书、遇到问题能否解决</td></tr>'+
'</tbody></table></div>'+
'<p>举例（RocketMQ vs Kafka）：<strong>「我们选 RocketMQ，因为业务需要事务消息和延迟消息，Kafka 原生不支持；且团队已在阿里云体系内，RocketMQ 运维成本更低。代价是吞吐量不如 Kafka，但我们的量级（峰值 5 万 TPS）RocketMQ 完全够用。」</strong>——这个回答体现了需求导向的思维。</p>'
    },
    {
      id: "s7-3",
      title: "7.3 把你的 12 年经验包装成亮点",
      html:
'<p>你有 4 年 C++ + 8 年 Java，做过配置中心迁移、Oracle 到 OceanBase/GoldenDB 迁移、CRM 核心模块。这些都是<strong>很好的素材</strong>，关键是怎么组织。</p>'+

'<h4>原则：用「问题-方案-收益」三段式，而不是罗列功能</h4>'+
'<div class="cols">'+
'<div class="col bad"><h5>❌ 平淡的写法</h5>'+
'<p style="font-size:13.5px">「负责 CRM 客户中心、订单中心、产品中心的开发。」</p>'+
'<p class="mini">问题：看不出技术含量，像是「参与了」，面试官无法追问。</p>'+
'</div>'+
'<div class="col good"><h5>✅ 有亮点的写法</h5>'+
'<p style="font-size:13.5px">「主导客户中心从单体拆分。<strong>问题</strong>：原单体应用 30 万行代码，客户查询接口 P99 达 2.3s，且与其他模块耦合无法独立扩容。<strong>方案</strong>：按业务边界拆分出客户中心服务，用 Nacos 做配置与注册中心，通过领域事件解耦跨模块调用，热点客户数据加 Redis 多级缓存。<strong>收益</strong>：接口 P99 降至 120ms，独立扩容后支撑了双十一 3 倍流量增长。」</p>'+
'</div></div>'+

'<h4>你的三个高价值素材，建议这样写</h4>'+

'<h5>素材 1：配置中心迁移（本地配置文件 → Nacos + DB）</h5>'+
PRE("bash",
`项目背景：200+ 个微服务分散管理配置，改一个配置要逐个服务重启，事故频发。
技术挑战：
  1. 配置热更新：如何做到不重启生效？
  2. 灰度能力：如何只对部分实例生效？
  3. 一致性：多实例配置如何保证最终一致？
  4. 回滚：配置写错如何快速回滚？
方案：
  - 基于 Nacos 的 @RefreshScope + 配置监听实现热更新
  - 配置分环境 + 分租户隔离，用 Nacos 命名空间
  - 关键配置接入 DB 存储 + 审批流，变更留痕可审计
  - 配置变更推送 Arthas 校验 + 全量实例一致性巡检
收益：配置发布从 30 分钟（逐台重启）降到 10 秒（自动推送），
      因配置错误导致的线上事故下降 90%`)+

'<h5>素材 2：Oracle → OceanBase/GoldenDB 迁移</h5>'+
PRE("bash",
`项目背景：原 Oracle 数据库授权成本高、扩展性受限，需迁移至国产化数据库。
技术挑战：
  1. 语法兼容：ROWNUM / NVL / DECODE / 序列 / 层次查询
  2. 数据类型映射：NUMBER / VARCHAR2 / DATE(含时分秒) / CLOB
  3. 性能回归：执行计划差异导致部分 SQL 慢 10 倍以上
  4. 数据一致性：亿级数据迁移如何校验
方案：
  - 自研 SQL 语法转换工具（正则 + AST 解析），自动化改写 8000+ 条 SQL
  - 双写 + 影子库比对，灰度切流（1% → 10% → 50% → 100%）
  - 抽样 + 全量 checksum 双重校验，差异数据自动修复
  - 慢 SQL 专项优化：重建索引、把 OR 改写为 UNION ALL、大表分页改游标
收益：迁移零数据丢失，核心接口性能与 Oracle 持平或更优，
      数据库年授权成本节省 XX 万`)+

'<h5>素材 3：CRM 核心业务模块</h5>'+
'<p>客户中心 / 订单中心 / 产品中心是典型的<strong>业务复杂型</strong>项目，要从「业务复杂度处理」角度讲：</p>'+
'<ul>'+
'<li><strong>客户中心</strong>：客户数据模型复杂（多层级、多变体、标签体系）。讲你怎么做<strong>数据模型设计</strong>和<strong>查询性能优化</strong>（如标签查询用倒排索引、复杂条件用 ES）。</li>'+
'<li><strong>订单中心</strong>：状态机复杂、并发冲突多。讲你怎么用<strong>状态机 + 乐观锁</strong>保证订单流转正确，怎么用<strong>幂等设计</strong>防止重复下单。</li>'+
'<li><strong>产品中心</strong>：数据量大、更新频繁。讲你怎么做<strong>多级缓存 + 缓存一致性</strong>（Cache Aside + 延迟双删 + 版本号）。</li>'+
'</ul>'+

BOX("ok","简历/面试的核心心法",
'<p>面试官不关心你「做了什么」，只关心你「解决了什么难问题、用了什么方法、产生了什么收益」。</p>'+
'<p><strong>三个必答要素</strong>：① 量化（P99 从 2.3s 到 120ms）；② 有技术深度（能说出方案对比和取舍）；③ 有系统性思考（事后怎么防止复发）。</p>'+
'<p>另外，<strong>不要隐瞒你做过 C++</strong>。12 年跨语言经验说明你的学习能力和底层功底，这是加分项。「4 年 C++ 让我对内存模型、指针、性能有更底层的理解，转 Java 后这些认知帮我更好地做 JVM 调优」——这个回答很加分。</p>','✅')
    },
    {
      id: "s7-4",
      title: "7.4 面试前 24 小时冲刺清单",
      html:
'<p>面试前一天，按这个清单过一遍，确保高频考点不翻车。</p>'+

'<h4>必背清单（能一口气说出来）</h4>'+
'<div class="tbl-wrap"><table><thead><tr><th>#</th><th>考点</th><th>关键记忆点</th><th>自查</th></tr></thead><tbody>'+
'<tr><td>1</td><td>自动配置原理</td><td>@EnableAutoConfiguration → Selector → imports 文件 → @Conditional</td><td>☐</td></tr>'+
'<tr><td>2</td><td>启动流程</td><td>Environment → Context → refresh（onRefresh 起 Tomcat、finish 实例化 Bean）</td><td>☐</td></tr>'+
'<tr><td>3</td><td>Bean 生命周期</td><td>12 步，特别是 AOP 代理在最后一步</td><td>☐</td></tr>'+
'<tr><td>4</td><td>三级缓存</td><td>为什么要三级——延迟代理决策</td><td>☐</td></tr>'+
'<tr><td>5</td><td>事务失效 8 种</td><td>自调用 / 非 public / 吞异常 / 受检异常 / 非托管 / 引擎 / final / 多线程</td><td>☐</td></tr>'+
'<tr><td>6</td><td>传播行为</td><td>REQUIRED vs REQUIRES_NEW vs NESTED</td><td>☐</td></tr>'+
'<tr><td>7</td><td>AOP 代理类型</td><td>JDK 动态代理 vs CGLIB，Boot 默认 CGLIB</td><td>☐</td></tr>'+
'<tr><td>8</td><td>过滤器 vs 拦截器</td><td>位置、能否拿 Handler、典型用途</td><td>☐</td></tr>'+
'<tr><td>9</td><td>缓存三问题</td><td>穿透 / 击穿 / 雪崩 + 各自解法</td><td>☐</td></tr>'+
'<tr><td>10</td><td>分布式锁</td><td>三个问题 + Lua + 看门狗</td><td>☐</td></tr>'+
'<tr><td>11</td><td>2.x → 3.x 变化</td><td>jakarta 包名、imports 文件、JDK 17</td><td>☐</td></tr>'+
'<tr><td>12</td><td>#{} vs ${}</td><td>预编译 vs 拼接，注入风险</td><td>☐</td></tr>'+
'</tbody></table></div>'+

'<h4>面试中的三个关键技巧</h4>'+
'<ol class="steps">'+
'<li><div class="st-t">先给结论，再展开</div>面试官问「循环依赖怎么解决」，先说「用三级缓存」，再解释细节。<strong>不要绕圈子</strong>，面试官会以为你不知道答案。</li>'+
'<li><div class="st-t">主动暴露知识边界</div>如果某个点你不确定，说「这部分我了解的是 X，但具体实现细节我需要在实践中验证」。<strong>诚实比胡编好得多</strong>，胡编被追问会很难看。</li>'+
'<li><div class="st-t">主动引导到你熟悉的领域</div>答完问题后加一句「这块我们项目里还遇到了 Y 问题，我的解法是……」。<strong>把面试官引到你的主场</strong>，这是高级技巧。</li>'+
'</ol>'+

BOX("tip","反问环节问什么",
'<p>面试最后的「你有什么想问的」不是走过场，是加分机会。推荐问：</p>'+
'<p>① 「团队目前的技术栈和架构是怎样的？有哪些技术挑战？」——展示你对技术的关注。</p>'+
'<p>② 「这个岗位入职后前三个月的核心目标是什么？」——展示你务实、想快速产出。</p>'+
'<p>③ 「团队怎么做代码评审和技术方案评审？」——展示你重视工程规范。</p>'+
'<p><strong>不要问</strong>：加班多不多、工资多少、能不能远程（这些留给 HR 环节）。</p>','💡')+

BOX("ok","最后的话",
'<p>这 7 天的内容覆盖了 Spring Boot 从源码到生产实践的完整体系。但请记住：<strong>面试考察的不是你记住了多少，而是你能否用这些知识解决实际问题</strong>。</p>'+
'<p>建议你在学完后做一件事：挑一个你自己的项目，用这套知识体系重新审视一遍——哪些地方可以优化？哪些问题你现在能解释了？这个过程会让知识真正内化。</p>'+
'<p>祝面试顺利。你 12 年的经验是真实的资产，这份教程只是帮你把它们放到正确的位置上。</p>','✅')
    }
  ]
});

/* ============ 完成渲染标记 ============ */
window.__CONTENT_READY__ = true;

