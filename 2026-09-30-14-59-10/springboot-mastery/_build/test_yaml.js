const fs=require('fs'),path=require('path');
const root=path.join(process.cwd(),'..');
const html=fs.readFileSync(path.join(root,'index.html'),'utf8');
const script=html.match(/<script>\n\(function\(\)\{([\s\S]*?)\}\)\(\);\n<\/script>/)[1];
const seg=script.slice(script.indexOf('function escapeHtml'), script.indexOf('document.querySelectorAll("pre[data-lang]")'));
const F=new Function(seg+'\n;return {escapeHtml,highlightYaml};')();

const tests = [
  ['缩进+行内注释', ['api:', '  log:', '    enabled: false   # 关闭', '    slow-threshold: 500  # 阈值'].join('\n')],
  ['简单键值+注释', ['server:', '  port: 8080   # 端口'].join('\n')],
  ['值含井号(引号内)', ['name: "a#b"   # 带井号的字符串'].join('\n')],
  ['纯注释行', ['# 这是注释', 'a: 1'].join('\n')],
  ['值含冒号(URL)', ['spring.datasource.url: jdbc:mysql://a?b=1'].join('\n')],
  ['无值键', ['spring:', '  main:'].join('\n')],
];
tests.forEach(([n, t], i) => {
  console.log('--- 用例'+(i+1)+' ['+n+'] ---');
  console.log(F.highlightYaml(t));
});
