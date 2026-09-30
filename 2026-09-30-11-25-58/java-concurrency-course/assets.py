#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Java 并发编程课程 —— HTML 构建脚本（第一部分：CSS 与工具函数）"""

CSS = r"""
:root{
  --bg:#ffffff; --bg-soft:#f8f9fb; --bg-code:#f6f8fa; --bg-side:#fbfbfd;
  --border:#e5e7eb; --border-soft:#eef0f3;
  --text:#1f2328; --text-soft:#57606a; --text-faint:#8b949e;
  --accent:#0969da; --accent-soft:#ddf4ff;
  --green:#1a7f37; --green-bg:#dafbe1;
  --red:#cf222e; --red-bg:#ffebe9;
  --orange:#9a6700; --orange-bg:#fff8c5;
  --purple:#8250df; --purple-bg:#fbefff;
  --radius:8px;
  --mono:"JetBrains Mono","Cascadia Code","Fira Code",Consolas,Monaco,monospace;
  --sans:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
}
*{box-sizing:border-box;margin:0;padding:0}
html{scroll-behavior:smooth}
body{font-family:var(--sans);background:var(--bg);color:var(--text);line-height:1.75;font-size:15px;-webkit-font-smoothing:antialiased}

.layout{display:flex;min-height:100vh}

/* 侧边栏 */
.sidebar{width:292px;flex:0 0 292px;background:var(--bg-side);border-right:1px solid var(--border);
  height:100vh;position:sticky;top:0;display:flex;flex-direction:column}
.side-head{padding:20px 20px 14px;border-bottom:1px solid var(--border-soft)}
.side-title{font-size:15px;font-weight:700;letter-spacing:-.01em;display:flex;align-items:center;gap:8px}
.side-title .logo{width:22px;height:22px;border-radius:5px;background:linear-gradient(135deg,#0969da,#8250df);
  display:flex;align-items:center;justify-content:center;color:#fff;font-size:12px;font-weight:800;flex:0 0 22px}
.side-sub{font-size:12px;color:var(--text-faint);margin-top:6px;line-height:1.5}
.side-progress{margin-top:12px;height:4px;background:var(--border);border-radius:2px;overflow:hidden}
.side-progress-bar{height:100%;width:0;background:var(--accent);transition:width .15s}
.side-search-wrap{padding:12px 16px 8px}
.side-search{width:100%;padding:7px 10px;font-size:13px;border:1px solid var(--border);
  border-radius:6px;background:var(--bg);color:var(--text);outline:none;font-family:var(--sans)}
.side-search:focus{border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}
.side-nav{flex:1;overflow-y:auto;padding:4px 10px 30px}
.side-nav ul{list-style:none;margin:0}
.side-nav>ul>li>a{display:flex;align-items:center;gap:9px;padding:7px 10px;font-size:13.5px;
  color:var(--text-soft);text-decoration:none;border-radius:6px;line-height:1.45;transition:background .12s,color .12s}
.side-nav>ul>li>a:hover{background:#eef1f5;color:var(--text)}
.side-nav>ul>li>a.active{background:var(--accent-soft);color:var(--accent);font-weight:600}
.side-nav>ul>li.hide{display:none}
.ch-num{flex:0 0 26px;font-family:var(--mono);font-size:11px;color:var(--text-faint);font-weight:600}
.side-nav a.active .ch-num{color:var(--accent)}
.side-foot{padding:12px 20px;border-top:1px solid var(--border-soft);font-size:11.5px;color:var(--text-faint);line-height:1.6}

/* 主区 */
.main{flex:1;min-width:0}
.content{max-width:920px;width:100%;margin:0 auto;padding:56px 56px 120px}

.chapter{padding-bottom:24px;scroll-margin-top:24px}
.chapter+.chapter{border-top:1px solid var(--border);padding-top:56px;margin-top:56px}
.ch-header{margin-bottom:28px}
.ch-badge{display:inline-block;font-family:var(--mono);font-size:12px;font-weight:700;
  color:var(--accent);background:var(--accent-soft);padding:3px 9px;border-radius:5px;margin-bottom:12px}
.ch-header h2{font-size:29px;font-weight:700;letter-spacing:-.02em;line-height:1.3}
.ch-sub{color:var(--text-soft);font-size:14.5px;margin-top:8px}
h3{font-size:19px;font-weight:650;margin:38px 0 14px;letter-spacing:-.01em;
  scroll-margin-top:24px;padding-bottom:8px;border-bottom:1px solid var(--border-soft)}
h4{font-size:16px;font-weight:650;margin:26px 0 10px}
p{margin:13px 0}
ul,ol{margin:13px 0 13px 24px}
li{margin:6px 0}
a{color:var(--accent);text-decoration:none}
a:hover{text-decoration:underline}
strong{font-weight:650;color:#0d1117}
code{font-family:var(--mono);font-size:.885em;background:var(--bg-code);padding:1.5px 5px;
  border-radius:4px;color:#953800;border:1px solid var(--border-soft)}
pre code{background:none;padding:0;border:none;color:inherit}

/* 代码块 */
.code-block{margin:16px 0;border:1px solid var(--border);border-radius:var(--radius);
  overflow:hidden;background:var(--bg-code)}
.code-head{display:flex;align-items:center;gap:12px;padding:9px 14px;background:#eef1f4;
  border-bottom:1px solid var(--border);font-size:12.5px}
.code-file{font-family:var(--mono);font-weight:600;color:var(--text);flex:1;min-width:0;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.code-meta{color:var(--text-faint);font-size:11.5px;flex:0 0 auto}
.code-toggle{flex:0 0 auto;font-size:11.5px;font-family:var(--sans);padding:3px 10px;
  border-radius:5px;cursor:pointer;border:1px solid var(--border);background:var(--bg);color:var(--text-soft)}
.code-toggle:hover{border-color:var(--accent);color:var(--accent)}
.code-block pre.code{margin:0;padding:16px 18px;overflow-x:auto;font-family:var(--mono);
  font-size:12.5px;line-height:1.68;color:#1f2328;max-height:660px}
.code-block.collapsed pre.code{max-height:300px;position:relative;overflow:hidden}
.code-block.collapsed pre.code::after{content:"";position:absolute;left:0;right:0;bottom:0;height:80px;
  background:linear-gradient(transparent,var(--bg-code));pointer-events:none}

/* 实测输出 */
.output-block{margin:16px 0;border:1px solid #c8e1c9;border-radius:var(--radius);overflow:hidden;background:#f4fbf5}
.output-head{padding:8px 14px;background:#e3f5e5;border-bottom:1px solid #c8e1c9;font-size:12px;
  font-weight:600;color:var(--green);display:flex;align-items:center;gap:7px}
.output-head::before{content:"\25B6";font-size:9px}
.output-block pre.output{margin:0;padding:14px 18px;overflow-x:auto;font-family:var(--mono);
  font-size:12.5px;line-height:1.65;color:#123d1a;white-space:pre}

/* ASCII 图 */
pre.ascii{margin:16px 0;padding:16px 18px;overflow-x:auto;font-family:var(--mono);font-size:12.5px;
  line-height:1.6;background:#fbfcfd;border:1px solid var(--border);border-radius:var(--radius);color:#374151}

/* 提示框 */
.callout{margin:18px 0;border-radius:var(--radius);border:1px solid var(--border);
  background:var(--bg-soft);overflow:hidden}
.callout-title{display:flex;align-items:center;gap:8px;padding:10px 15px;font-weight:650;font-size:13.5px}
.callout-icon{width:19px;height:19px;border-radius:50%;display:flex;align-items:center;
  justify-content:center;font-size:11px;font-weight:800;flex:0 0 19px;color:#fff}
.callout-body{padding:2px 15px 13px;font-size:14px;color:var(--text-soft)}
.callout-body p:first-child{margin-top:0}
.callout-body p:last-child{margin-bottom:0}
.callout-body pre{margin:10px 0;padding:12px 14px;background:#fff;border:1px solid var(--border-soft);
  border-radius:6px;font-family:var(--mono);font-size:12.5px;overflow-x:auto;color:#1f2328;line-height:1.6}
.callout.info{background:#f2f8ff;border-color:#c8dcf5}
.callout.info .callout-title{color:var(--accent);background:#e6f0fd}
.callout.info .callout-icon{background:var(--accent)}
.callout.warn{background:#fffaf0;border-color:#f0dfb0}
.callout.warn .callout-title{color:var(--orange);background:#fff5d9}
.callout.warn .callout-icon{background:var(--orange)}
.callout.danger{background:#fff7f6;border-color:#f5c8c4}
.callout.danger .callout-title{color:var(--red);background:#ffebe9}
.callout.danger .callout-icon{background:var(--red)}
.callout.success{background:#f3fcf5;border-color:#c0e6c6}
.callout.success .callout-title{color:var(--green);background:#e3f5e5}
.callout.success .callout-icon{background:var(--green)}
.callout.interview{background:#faf5ff;border-color:#dfc9f5}
.callout.interview .callout-title{color:var(--purple);background:#f4e9ff}
.callout.interview .callout-icon{background:var(--purple)}

/* 表格 */
.table-wrap{overflow-x:auto;margin:16px 0;border:1px solid var(--border);border-radius:var(--radius)}
table{border-collapse:collapse;width:100%;font-size:13.5px;background:var(--bg)}
th{background:#f4f6f8;text-align:left;padding:10px 14px;font-weight:650;font-size:13px;
  border-bottom:1px solid var(--border);white-space:nowrap;color:var(--text)}
td{padding:10px 14px;border-bottom:1px solid var(--border-soft);vertical-align:top;line-height:1.6}
tr:last-child td{border-bottom:none}
tbody tr:hover{background:#fafbfc}

/* 顶部进度条 */
.topbar{position:fixed;top:0;left:292px;right:0;height:3px;background:transparent;z-index:100}
.topbar-bar{height:100%;width:0;background:linear-gradient(90deg,#0969da,#8250df)}

/* 移动端 */
.menu-btn{display:none;position:fixed;top:12px;left:12px;z-index:200;width:40px;height:40px;
  border-radius:8px;border:1px solid var(--border);background:var(--bg);cursor:pointer;
  align-items:center;justify-content:center;font-size:17px;box-shadow:0 2px 8px rgba(0,0,0,.08)}
@media(max-width:1000px){
  .sidebar{position:fixed;left:0;top:0;z-index:150;transform:translateX(-100%);
    transition:transform .22s ease;box-shadow:2px 0 20px rgba(0,0,0,.12)}
  .sidebar.open{transform:translateX(0)}
  .menu-btn{display:flex}
  .topbar{left:0}
  .content{padding:68px 22px 90px}
  .ch-header h2{font-size:23px}
}
@media(min-width:1001px){.sidebar{transform:none!important}}
"""

JS = r"""
function toggleCode(btn){
  var block = btn.closest('.code-block');
  var collapsed = block.classList.toggle('collapsed');
  btn.textContent = collapsed ? '展开完整代码' : '收起代码';
}
(function(){
  // 侧边栏高亮 + 进度条
  var sections = Array.prototype.slice.call(document.querySelectorAll('.chapter'));
  var links = Array.prototype.slice.call(document.querySelectorAll('.side-nav a'));
  var bar = document.querySelector('.side-progress-bar');
  var topbar = document.querySelector('.topbar-bar');

  function onScroll(){
    var y = window.scrollY || document.documentElement.scrollTop;
    var docH = document.documentElement.scrollHeight - window.innerHeight;
    var pct = docH > 0 ? Math.min(100, (y / docH) * 100) : 0;
    if(bar) bar.style.width = pct + '%';
    if(topbar) topbar.style.width = pct + '%';

    var current = sections[0];
    for(var i=0;i<sections.length;i++){
      if(sections[i].offsetTop - 90 <= y) current = sections[i];
    }
    if(current){
      links.forEach(function(a){
        a.classList.toggle('active', a.getAttribute('data-target') === current.id);
      });
    }
  }
  window.addEventListener('scroll', onScroll, {passive:true});
  window.addEventListener('resize', onScroll);

  // 移动端菜单
  var btn = document.querySelector('.menu-btn');
  var sidebar = document.querySelector('.sidebar');
  if(btn){
    btn.addEventListener('click', function(){ sidebar.classList.toggle('open'); });
    links.forEach(function(a){
      a.addEventListener('click', function(){ sidebar.classList.remove('open'); });
    });
  }

  // 搜索过滤
  var search = document.querySelector('.side-search');
  if(search){
    search.addEventListener('input', function(){
      var q = this.value.trim().toLowerCase();
      document.querySelectorAll('.side-nav>ul>li').forEach(function(li){
        var t = li.textContent.toLowerCase();
        li.classList.toggle('hide', q !== '' && t.indexOf(q) === -1);
      });
    });
  }

  onScroll();
})();
"""
