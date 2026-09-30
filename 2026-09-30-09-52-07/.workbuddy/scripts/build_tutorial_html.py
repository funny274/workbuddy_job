# -*- coding: utf-8 -*-
"""把 Spring AI 教程 Markdown 转成带左侧目录树的自包含 HTML。"""
import html
import json
import re
import sys
from pathlib import Path

import markdown
from markdown.extensions.toc import slugify

SRC = Path("Spring-AI-2.0-全面学习教程.md")
OUT = Path(".tmp/springai-tutorial.html")

text = SRC.read_text(encoding="utf-8")

# ── 1) 代码块语言登记，便于高亮 ───────────────────────────────
md = markdown.Markdown(
    extensions=[
        "extra",          # tables / fenced_code / attr_list / def_list ...
        "codehilite",     # 代码高亮
        "sane_lists",
        "nl2br",
        "toc",
    ],
    extension_configs={
        "codehilite": {"css_class": "hl", "guess_lang": False, "linenums": False},
        "toc": {"permalink": False},
    },
    output_format="html5",
)

body = md.convert(text)

# ── 2) 给 h1/h2/h3 注入 id（用与原生 TOC 相同的 slug 规则）────
def slug(t):
    return slugify(t, "-")

counter = {"h": 0}
used = set()

def make_id(title, lvl):
    # slugify 对纯 emoji / 纯符号标题会产出空串，退化为序号保证锚点稳定且非空
    s = slug(title)
    if not s.strip("-"):
        s = f"sec-{counter['h'] + 1}"
    base = f"h{lvl}-{s}"[:120]
    candidate = base
    n = 1
    while candidate in used:
        n += 1
        candidate = f"{base}-{n}"
    used.add(candidate)
    return candidate

def inject(m):
    lvl = int(m.group(1))
    attrs = m.group(2) or ""
    inner = m.group(3)
    plain = re.sub(r"<[^>]+>", "", inner)
    plain = html.unescape(plain).strip()
    if not plain:
        return m.group(0)
    hid = make_id(plain, lvl)
    counter["h"] += 1
    # 去掉原有 id
    attrs = re.sub(r'\sid="[^"]*"', "", attrs)
    return f'<h{lvl}{attrs} id="{hid}" data-title="{html.escape(plain, quote=True)}">{inner}</h{lvl}>'

body = re.sub(r"<h([123])([^>]*)>(.*?)</h\1>", inject, body, flags=re.S)

# ── 3) 从渲染结果抽取目录树 ─────────────────────────────────
tree = []
for m in re.finditer(r'<h([123])([^>]*) id="([^"]+)"[^>]*data-title="([^"]*)"', body):
    tree.append({"lvl": int(m.group(1)), "id": m.group(3), "title": html.unescape(m.group(4))})

# ── 4) 提取 hero 摘要（前言 blockquote）─────────────────────
hero_summary = "Spring AI 2.0.1 / Spring Boot 4.1.x ｜ 27 章正文 + 3 个附录 ｜ 从零到生产级 AI 应用"

CSS = r"""
:root{
  --bg:#f7f8fa; --panel:#ffffff; --text:#1f2328; --muted:#6b7280;
  --border:#e5e7eb; --accent:#2563eb; --accent-soft:#dbeafe;
  --code-bg:#f6f8fa; --code-border:#e5e7eb;
  --sidebar-w:340px; --shadow:0 1px 3px rgba(0,0,0,.06),0 1px 2px rgba(0,0,0,.04);
}
*{box-sizing:border-box}
html{scroll-behavior:smooth;scroll-padding-top:24px}
body{
  margin:0;background:var(--bg);color:var(--text);
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
  line-height:1.78;font-size:16px;-webkit-font-smoothing:antialiased;
}
/* ── 侧栏 ── */
#sidebar{
  position:fixed;top:0;left:0;bottom:0;width:var(--sidebar-w);
  background:var(--panel);border-right:1px solid var(--border);
  display:flex;flex-direction:column;z-index:40;
}
.sb-head{padding:18px 18px 12px;border-bottom:1px solid var(--border);flex:none}
.sb-title{font-size:15px;font-weight:700;line-height:1.4;margin:0 0 4px}
.sb-meta{font-size:12px;color:var(--muted);line-height:1.5}
.sb-search{position:relative;padding:12px 18px 10px;flex:none}
#filter{
  width:100%;padding:9px 12px 9px 34px;font-size:13px;
  border:1px solid var(--border);border-radius:8px;background:var(--bg);
  color:var(--text);outline:none;font-family:inherit;
}
#filter:focus{border-color:var(--accent);background:#fff;box-shadow:0 0 0 3px var(--accent-soft)}
.sb-search svg{position:absolute;left:29px;top:21px;opacity:.45}
.sb-tools{display:flex;gap:8px;padding:0 18px 10px;flex:none}
.sb-tools button{
  flex:1;font-size:12px;padding:5px 8px;border:1px solid var(--border);
  background:var(--bg);color:var(--muted);border-radius:6px;cursor:pointer;font-family:inherit;
}
.sb-tools button:hover{border-color:var(--accent);color:var(--accent)}
#tree{overflow-y:auto;flex:1;padding:4px 10px 40px}
#tree::-webkit-scrollbar{width:8px}
#tree::-webkit-scrollbar-thumb{background:#d3d7de;border-radius:4px}
#tree::-webkit-scrollbar-thumb:hover{background:#b8bec8}
.node{display:block;text-decoration:none;color:var(--text);border-radius:6px;
  padding:4px 8px;font-size:13px;line-height:1.5;margin:1px 0;position:relative}
.node:hover{background:var(--accent-soft);color:var(--accent)}
.node.active{background:var(--accent);color:#fff;font-weight:600}
.node.active .cnt{color:rgba(255,255,255,.8)}
.n1{font-weight:700;font-size:13.5px;margin-top:9px;padding-top:7px;border-top:1px solid var(--border)}
.n1:first-child{border-top:none;margin-top:0}
.n2{padding-left:20px}
.n3{padding-left:34px;font-size:12.5px;color:var(--muted);padding-top:3px;padding-bottom:3px}
.n3:hover{color:var(--accent)}
.n3.active{color:#fff}
.cnt{float:right;font-size:11px;color:var(--muted);font-weight:400;margin-left:6px}
.grp{display:none}
.grp.open{display:block}
.twist{position:absolute;left:4px;top:6px;width:14px;height:14px;line-height:14px;
  text-align:center;font-size:9px;color:var(--muted);transition:transform .15s;cursor:pointer}
.grp.open+.node .twist,.twist.open{transform:rotate(90deg)}
.n1{padding-left:22px}
.hidden{display:none!important}
/* ── 正文 ── */
#main{margin-left:var(--sidebar-w);padding:0 0 120px}
.hero{padding:56px 48px 28px;border-bottom:1px solid var(--border);background:var(--panel)}
.hero h1{margin:0 0 10px;font-size:30px;line-height:1.3;letter-spacing:-.4px}
.hero p{margin:0;color:var(--muted);font-size:14px}
.content{max-width:900px;margin:0 auto;padding:32px 48px 0}
h1{font-size:25px;margin:52px 0 16px;padding-bottom:10px;border-bottom:2px solid var(--accent);
   line-height:1.35;scroll-margin-top:20px}
h2{font-size:20px;margin:36px 0 12px;padding-left:11px;border-left:4px solid var(--accent);
   line-height:1.4;scroll-margin-top:20px}
h3{font-size:16.5px;margin:26px 0 10px;color:#111827;scroll-margin-top:20px}
p{margin:11px 0}
a{color:var(--accent);text-decoration:none}
a:hover{text-decoration:underline}
ul,ol{padding-left:24px;margin:10px 0}
li{margin:4px 0}
blockquote{margin:14px 0;padding:12px 18px;background:#fff;border-left:4px solid var(--accent);
  border-radius:0 8px 8px 0;color:#374151;box-shadow:var(--shadow)}
blockquote p{margin:6px 0}
hr{border:none;border-top:1px solid var(--border);margin:36px 0}
table{border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;
  background:var(--panel);border-radius:8px;overflow:hidden;box-shadow:var(--shadow);display:table}
th,td{border:1px solid var(--border);padding:9px 12px;text-align:left;vertical-align:top}
th{background:#f0f3f7;font-weight:600}
tr:nth-child(even) td{background:#fafbfc}
code{font-family:"SFMono-Regular",Consolas,"Liberation Mono",Menlo,monospace;
  font-size:13px;background:var(--code-bg);padding:2px 5px;border-radius:4px;
  border:1px solid var(--code-border);color:#b91c1c}
pre{background:var(--code-bg);border:1px solid var(--code-border);border-radius:8px;
  padding:14px 16px;overflow-x:auto;margin:14px 0;box-shadow:var(--shadow)}
pre code{background:none;border:none;padding:0;color:inherit;font-size:13px;line-height:1.6}
/* 高亮 */
.hl .k{color:#a626a4;font-weight:600}
.hl .s,.hl .s1,.hl .s2{color:#50a14f}
.hl .c1,.hl .c,.hl .cm{color:#9aa5b1;font-style:italic}
.hl .n{color:#1f2328}
.hl .nf{color:#4078f2}
.hl .nc{color:#c18401}
.hl .mi,.hl .mf{color:#986801}
.hl .o{color:#0184bc}
.hl .nt{color:#e45649}
.hl .nd{color:#c18401}
.hl .kn{color:#a626a4}
.hl .kd{color:#a626a4}
.hl .kt{color:#c18401}
.hl .err{color:#e45649}
/* 返回顶部 */
#top{position:fixed;right:26px;bottom:26px;width:42px;height:42px;border-radius:50%;
  background:var(--accent);color:#fff;border:none;cursor:pointer;font-size:17px;
  box-shadow:0 4px 12px rgba(37,99,235,.35);opacity:0;pointer-events:none;
  transition:opacity .2s;z-index:50}
#top.show{opacity:1;pointer-events:auto}
#burger{display:none;position:fixed;top:14px;left:14px;z-index:60;width:42px;height:42px;
  border-radius:10px;border:1px solid var(--border);background:var(--panel);
  font-size:18px;cursor:pointer;box-shadow:var(--shadow)}
@media(max-width:1024px){
  :root{--sidebar-w:0px}
  #sidebar{transform:translateX(-100%);transition:transform .22s;width:320px}
  #sidebar.open{transform:none;box-shadow:0 0 40px rgba(0,0,0,.18)}
  #main{margin-left:0}
  #burger{display:block}
  .content{padding:24px 20px 0}
  .hero{padding:64px 24px 24px}
  .hero h1{font-size:23px}
}
"""

JS = r"""
(function(){
  var tree=document.getElementById('tree');
  var filter=document.getElementById('filter');
  var nodes=Array.prototype.slice.call(tree.querySelectorAll('.node'));
  var heads=Array.prototype.slice.call(document.querySelectorAll('h1[id],h2[id],h3[id]'));

  // 折叠：h1 后面到下一个 h1 之间的内容归入该组
  nodes.forEach(function(n){
    if(n.dataset.lvl==='1'){
      var tw=document.createElement('span');
      tw.className='twist';tw.textContent='▶';
      tw.onclick=function(e){
        e.preventDefault();e.stopPropagation();
        var g=n.nextElementSibling;
        if(g&&g.classList.contains('grp')){g.classList.toggle('open');tw.classList.toggle('open');}
      };
      n.insertBefore(tw,n.firstChild);
    }
  });
  function openAll(open){
    tree.querySelectorAll('.grp').forEach(function(g){g.classList.toggle('open',open);});
    tree.querySelectorAll('.twist').forEach(function(t){t.classList.toggle('open',open);});
  }

  // 滚动高亮
  var current=null, ticking=false;
  function onScroll(){
    if(ticking)return;ticking=true;
    requestAnimationFrame(function(){
      var y=window.scrollY+80, hit=heads[0];
      for(var i=0;i<heads.length;i++){
        if(heads[i].offsetTop<=y)hit=heads[i];else break;
      }
      if(hit&&hit!==current){
        current=hit;
        nodes.forEach(function(n){n.classList.toggle('active',n.dataset.target===hit.id);});
        var act=tree.querySelector('.node.active');
        if(act){
          var r=act.getBoundingClientRect(),tr=tree.getBoundingClientRect();
          if(r.top<tr.top+40||r.bottom>tr.bottom-40){
            tree.scrollTop+=r.top-tr.top-tr.height*0.35;
          }
        }
      }
      ticking=false;
    });
  }
  window.addEventListener('scroll',onScroll,{passive:true});

  // 搜索过滤
  filter.addEventListener('input',function(){
    var q=filter.value.trim().toLowerCase();
    if(!q){nodes.forEach(function(n){n.classList.remove('hidden');});openAll(false);return;}
    openAll(true);
    nodes.forEach(function(n){
      var hit=n.textContent.toLowerCase().indexOf(q)>-1;
      n.classList.toggle('hidden',!hit);
    });
  });
  filter.addEventListener('keydown',function(e){
    if(e.key==='Escape'){filter.value='';filter.dispatchEvent(new Event('input'));}
  });

  // 返回顶部
  var top=document.getElementById('top');
  top.onclick=function(){window.scrollTo({top:0,behavior:'smooth'});};
  window.addEventListener('scroll',function(){top.classList.toggle('show',window.scrollY>500);},{passive:true});

  // 移动端抽屉
  var burger=document.getElementById('burger'),sb=document.getElementById('sidebar');
  burger.onclick=function(){sb.classList.toggle('open');};
  tree.addEventListener('click',function(e){
    if(e.target.classList.contains('node')&&window.innerWidth<=1024)sb.classList.remove('open');
  });
  onScroll();
})();
"""

# 去掉原 md 里手写的"目录"段（侧栏已替代）：正文与目录树同步移除
body = re.sub(r'<h[12][^>]*id="[^"]*"[^>]*data-title="目录"[^>]*>.*?</h[12]>', "", body, count=1, flags=re.S)
tree = [t for t in tree if t["title"] != "目录"]

# ── 5) 构建目录树 HTML ───────────────────────────────────────
def build_tree(items):
    out = []
    open_grp = False
    for it in items:
        lvl, hid, title = it["lvl"], it["id"], it["title"]
        esc = html.escape(title)
        if lvl == 1:
            if open_grp:
                out.append("</div>")
            out.append(f'<div class="grp open">')
            open_grp = True
            out.append(f'<a class="node n1" data-lvl="1" data-target="{hid}" href="#{hid}">{esc}</a>')
        elif lvl == 2:
            out.append(f'<a class="node n2" data-lvl="2" data-target="{hid}" href="#{hid}">{esc}</a>')
        else:
            out.append(f'<a class="node n3" data-lvl="3" data-target="{hid}" href="#{hid}">{esc}</a>')
    if open_grp:
        out.append("</div>")
    return "\n".join(out)

tree_html = build_tree(tree)
chapters = sum(1 for t in tree if t["lvl"] == 1)
sections = sum(1 for t in tree if t["lvl"] == 2)
subs = sum(1 for t in tree if t["lvl"] == 3)


page = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Spring AI 2.0 全面学习教程</title>
<style>{CSS}</style>
</head>
<body>
<button id="burger" aria-label="目录">☰</button>
<aside id="sidebar">
  <div class="sb-head">
    <h2 class="sb-title">Spring AI 2.0 全面学习教程</h2>
    <div class="sb-meta">{chapters} 章 · {sections} 节 · {subs} 小节</div>
  </div>
  <div class="sb-search">
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg>
    <input id="filter" type="search" placeholder="搜索章节（如 RAG / Advisor）" autocomplete="off">
  </div>
  <div class="sb-tools">
    <button id="expandAll" type="button">全部展开</button>
    <button id="collapseAll" type="button">全部折叠</button>
  </div>
  <nav id="tree">{tree_html}</nav>
</aside>
<div id="main">
  <header class="hero">
    <h1>Spring AI 2.0 全面学习教程</h1>
    <p>{html.escape(hero_summary)}</p>
  </header>
  <div class="content">{body}</div>
</div>
<button id="top" type="button" title="回到顶部">↑</button>
<script>{JS}</script>
</body>
</html>
"""

OUT.write_text(page, encoding="utf-8")
print(json.dumps({
    "out": str(OUT),
    "bytes": len(page.encode("utf-8")),
    "chapters": chapters,
    "sections": sections,
    "subs": subs,
}, ensure_ascii=False))
