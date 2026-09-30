import io, os

TARGET = r"C:/Users/issuser/WorkBuddy/2026-09-30-14-59-10/springboot-mastery/content.js"

part = r'''
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
'''

with io.open(TARGET, "a", encoding="utf-8") as f:
    f.write(part)
print("Day7 appended, size =", os.path.getsize(TARGET))
