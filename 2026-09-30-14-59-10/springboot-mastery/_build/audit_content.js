const fs=require('fs'),path=require('path');
const root=path.join(process.cwd(),'..');
const content=fs.readFileSync(path.join(root,'content.js'),'utf8');

console.log('===== 1. 技术事实核查 =====');

// 1.1 检查 spring.factories 提法（2.x用spring.factories，3.x用AutoConfiguration.imports）
const sf=(content.match(/spring\.factories/g)||[]).length;
const imp=(content.match(/AutoConfiguration\.imports/g)||[]).length;
console.log('spring.factories 出现:', sf, '| AutoConfiguration.imports 出现:', imp);

// 1.2 三级缓存键名
['singletonObjects','earlySingletonObjects','singletonFactories'].forEach(k=>{
  console.log('  '+k+':', (content.match(new RegExp(k,'g'))||[]).length);
});

// 1.3 事务传播行为是否齐全
['REQUIRED','REQUIRES_NEW','NESTED','SUPPORTS','NOT_SUPPORTED','MANDATORY','NEVER'].forEach(p=>{
  const n=(content.match(new RegExp('\\b'+p+'\\b','g'))||[]).length;
  if(n===0) console.log('  [缺失] 传播行为', p); 
});
console.log('  传播行为核查完毕');

// 1.4 虚拟线程配置项
console.log('  spring.threads.virtual.enabled:', (content.match(/spring\.threads\.virtual\.enabled/g)||[]).length);
// 1.5 JDK 版本
console.log('  JDK17 提及:', (content.match(/JDK\s*17|JDK17|17\b/g)||[]).length);
console.log('  Spring Boot 3:', (content.match(/Spring Boot 3|Boot 3\./g)||[]).length);
// 1.6 GraalVM
console.log('  GraalVM 提及:', (content.match(/GraalVM/g)||[]).length);
// 1.7 AOT
console.log('  AOT 提及:', (content.match(/\bAOT\b/g)||[]).length);

console.log('\n===== 2. 潜在技术错误扫描 =====');

// 2.1 检查循环依赖：Spring Boot 2.6+ 默认禁止
if(/循环依赖/.test(content)){
  console.log('  循环依赖章节存在');
  if(/allow-circular-references/.test(content)) console.log('  ✓ 提及 allow-circular-references 开关');
  else console.log('  [遗漏] 未提及 spring.main.allow-circular-references（2.6+默认false）');
}
// 2.2 检查 @Configuration proxyBeanMethods
console.log('  proxyBeanMethods:', (content.match(/proxyBeanMethods/g)||[]).length);
// 2.3 HikariCP
console.log('  HikariCP:', (content.match(/Hikari/g)||[]).length);
// 2.4 @Transactional 失效场景数量
const failSec = (content.match(/失效/g)||[]).length;
console.log('  "失效" 提及次数:', failSec);

console.log('\n===== 3. 检查易错的具体断言 =====');
// 3.1 CGLIB 默认
if(/proxyTargetClass/.test(content)) console.log('  ✓ proxyTargetClass 提及');
// 3.2 检查是否错误声称"@Transactional 默认回滚所有异常"
if(/默认.*回滚.*所有异常/.test(content)) console.log('  [!] 可能存在"默认回滚所有异常"的错误表述');
else console.log('  ✓ 未发现"默认回滚所有异常"错误表述');
// 3.3 检查 RuntimeException
console.log('  RuntimeException/Error 回滚表述:', (content.match(/RuntimeException|Error/g)||[]).length);

console.log('\n===== 4. 代码块语言分布 =====');
const langs={};
(content.match(/PRE\("([a-z]+)"/g)||[]).forEach(s=>{const l=s.match(/"([a-z]+)"/)[1];langs[l]=(langs[l]||0)+1;});
console.log(' ', JSON.stringify(langs));

console.log('\n===== 5. 章节 ID 唯一性 =====');
const ids=[...content.matchAll(/id\s*:\s*"([^"]+)"/g)].map(m=>m[1]);
const dup=ids.filter((x,i)=>ids.indexOf(x)!==i);
console.log('  id 总数:', ids.length, '| 重复:', dup.length ? [...new Set(dup)] : '无');

console.log('\n===== 6. HTML 标签配对（全站） =====');
function balance(html, tag){
  const open=(html.match(new RegExp('<'+tag+'(\\s|>)','g'))||[]).length;
  const close=(html.match(new RegExp('</'+tag+'>','g'))||[]).length;
  return {open,close,ok:open===close};
}
['div','table','tr','td','th','ul','ol','li','p','pre','h3','h4','span','code','strong'].forEach(t=>{
  const b=balance(content,t);
  if(!b.ok) console.log('  [!] <'+t+'> 不配对: open='+b.open+' close='+b.close);
});
console.log('  标签配对检查完毕');
