// 真实审计：从 index.html 提取 highlightJava 及辅助函数，在 Node 中真实执行
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');

// 提取 <script> 主体（第 304-517 行之间的 IIFE）
const m = html.match(/<script>\n\(function\(\)\{([\s\S]*?)\}\)\(\);\n<\/script>/);
if (!m) { console.log('无法提取脚本'); process.exit(1); }
let src = m[1];

// 去掉依赖 DOM 的部分：只取 escapeHtml / JAVA_KW / highlight* 定义
const start = src.indexOf('function escapeHtml');
const end = src.indexOf('document.querySelectorAll("pre[data-lang]")');
const funcs = src.slice(start, end);

// 在沙箱中求值这些函数
const sandbox = {};
const factory = new Function(funcs + '\n;return {escapeHtml, highlightJava, highlightXml, highlightSql, highlightYaml, highlightShell};');
const F = factory();

// ---------- 测试 1：基本字符串/注释/注解保护 ----------
const t1 = `@Service
public class Foo {
  // 我是注释 with "quotes"
  private String url = "http://a.com/x?y=1&z=2";
  private char c = 'x';
  public int calc(){ return 42; }
}`;
const r1 = F.highlightJava(t1);
console.log('=== 测试1 输出 ===');
console.log(r1);
console.log('--- 残留 \\u0001 占位符数量:', (r1.match(/\u0001/g) || []).length);
console.log('--- 是否含未转义裸 < :', /<(?!\/?span\b)/.test(r1) ? '是(BUG)' : '否(OK)');

// ---------- 测试 2：$ 特殊替换字符 ----------
const t2 = `String s = "价格是 $100 和 $$200"; // $1 $& 测试
int x = 1;`;
const r2 = F.highlightJava(t2);
console.log('\n=== 测试2 输出 ===');
console.log(r2);

// ---------- 测试 3：泛型尖括号 ----------
const t3 = `Map<String, List<Integer>> m = new HashMap<>();`;
const r3 = F.highlightJava(t3);
console.log('\n=== 测试3 输出 ===');
console.log(r3);

// ---------- 测试 4：中文与数字 ----------
const t4 = `// 中文注释
long id = 1000000L;
double d = 3.14d;`;
const r4 = F.highlightJava(t4);
console.log('\n=== 测试4 输出 ===');
console.log(r4);

// ---------- 测试 5：全部真实 Java 代码块 ----------
const content = fs.readFileSync(path.join(root, 'content.js'), 'utf8');
const blocks = [];
// PRE("java", `....`) —— 语言双引号，代码体为反引号模板字面量
const re = /PRE\(\s*"(java|yaml|bash|xml|sql|sh)"\s*,\s*`([\s\S]*?)`\s*\)/g;
let mm;
const byLang = {};
while ((mm = re.exec(content)) !== null) {
  byLang[mm[1]] = (byLang[mm[1]] || 0) + 1;
  if (mm[1] === 'java') blocks.push(mm[2]);
}
console.log('各语言代码块统计:', JSON.stringify(byLang));
console.log('\n=== 抽取 Java 代码块数量:', blocks.length, '===');
let fail = 0;
blocks.forEach((c, i) => {
  const out = F.highlightJava(c);
  const ph = (out.match(/\u0001/g) || []).length;
  const badHtml = /<(?!\/?span\b)[a-zA-Z!\/]/.test(out.replace(/&lt;|&gt;|&amp;/g, ''));
  if (ph > 0 || badHtml) {
    fail++;
    console.log(`[!] 块${i + 1} 占位符残留=${ph} 裸标签=${badHtml}`);
  }
});
console.log(`Java 高亮: ${blocks.length} 块, 异常 ${fail}`);

// ---------- 测试 6：其他语言 ----------
const xmlSample = `<bean id="a" class="com.X"><property name="p" value="1 & 2"/></bean><!-- c -->`;
console.log('\n=== XML 高亮 ===');
console.log(F.highlightXml(xmlSample));
const sqlSample = `SELECT id, name FROM t_user WHERE age > 18 AND name = 'it''s'; -- comment`;
console.log('\n=== SQL 高亮 ===');
console.log(F.highlightSql(sqlSample));
const yamlSample = `server:
  port: 8080
# 注释
spring.datasource.url: jdbc:mysql://a?b=1`;
console.log('\n=== YAML 高亮 ===');
console.log(F.highlightYaml(yamlSample));
const shSample = `# build
mvn clean package -DskipTests
java -jar app.jar
echo "done"`;
console.log('\n=== Shell 高亮 ===');
console.log(F.highlightShell(shSample));
