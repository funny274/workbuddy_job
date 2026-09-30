// 端到端渲染模拟：完全按 index.html 脚本逻辑，把 pre[data-lang] 替换为高亮后的 HTML
const fs=require('fs'),path=require('path');
const root=path.join(process.cwd(),'..');
const content=fs.readFileSync(path.join(root,'content.js'),'utf8');
const html=fs.readFileSync(path.join(root,'index.html'),'utf8');

// 抽取高亮函数
const script=html.match(/<script>\n\(function\(\)\{([\s\S]*?)\}\)\(\);\n<\/script>/)[1];
const seg=script.slice(script.indexOf('function escapeHtml'), script.indexOf('document.querySelectorAll("pre[data-lang]")'));
const F=new Function(seg+'\n;return {escapeHtml,highlightJava,highlightXml,highlightSql,highlightYaml,highlightShell};')();

// 执行 content.js 取 CHAPTERS
global.window = {};
const CH=new Function(content+'\n;return CHAPTERS;')();

// 收集所有 data-code
const all=[];
CH.forEach(ch=>ch.sections.forEach(s=>{
  const re=/<pre data-lang="([a-z]+)" data-code="([^"]*)"><\/pre>/g;
  let m; while((m=re.exec(s.html))!==null) all.push({lang:m[1], raw:m[2]});
}));

console.log('运行期 pre[data-lang] 总数:', all.length);

const fnMap={java:F.highlightJava, xml:F.highlightXml, html:F.highlightXml, sql:F.highlightSql, yaml:F.highlightYaml, bash:F.highlightShell, sh:F.highlightShell};
let bad=0, langStat={};
all.forEach((b,i)=>{
  langStat[b.lang]=(langStat[b.lang]||0)+1;
  let code; try{ code=decodeURIComponent(b.raw); }catch(e){ console.log(`[!] 块${i+1} decodeURIComponent 失败: ${e.message}`); bad++; return; }
  const f=fnMap[b.lang]||F.escapeHtml;
  const out=f(code);
  // 检查残留私有区占位符
  if(/[\uE000-\uE002]/.test(out)) { console.log(`[!] 块${i+1}(${b.lang}) 残留占位符`); bad++; }
  // 检查未转义裸标签（排除我们生成的 span）
  const stripped=out.replace(/<\/?span[^>]*>/g,'');
  if(/<(?!\/?span)/.test(stripped)) { console.log(`[!] 块${i+1}(${b.lang}) 存在未转义裸标签`); bad++; }
  // 检查 span 是否闭合
  const so=(out.match(/<span/g)||[]).length, sc=(out.match(/<\/span>/g)||[]).length;
  if(so!==sc){ console.log(`[!] 块${i+1}(${b.lang}) span 不配对 ${so}/${sc}`); bad++; }
});
console.log('语言分布:', JSON.stringify(langStat));
console.log('渲染异常总数:', bad);

// 抽样打印几个真实块的渲染结果
console.log('\n===== 抽样：第一个 yaml 块 =====');
const y=all.find(x=>x.lang==='yaml');
console.log(F.highlightYaml(decodeURIComponent(y.raw)).slice(0,400));
console.log('\n===== 抽样：第一个 bash 块 =====');
const bsh=all.find(x=>x.lang==='bash');
console.log(F.highlightShell(decodeURIComponent(bsh.raw)).slice(0,300));
console.log('\n===== 抽样：唯一 sql 块 =====');
const sq=all.find(x=>x.lang==='sql');
console.log(F.highlightSql(decodeURIComponent(sq.raw)).slice(0,400));
