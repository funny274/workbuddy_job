# -*- coding: utf-8 -*-
"""
Redis 大师之路 - 内容装配脚本
把 shell.html 与各章节 fragment 拼成最终单文件 index.html
"""
import io, os, re, sys

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, "src")

def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, s):
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

# 章节顺序（同时决定侧边栏顺序）
CHAPTERS = [
    ("c00-roadmap",    "课程总览与学习路线"),
    ("c01-install",    "Day1 · 环境与部署（大厂标准姿势）"),
    ("c02-datatype",   "Day1 · 八大数据结构与场景选型"),
    ("c03-command",    "Day1 · 命令行与 Java 客户端实战"),
    ("c04-thread",     "Day2 · 为什么 Redis 这么快（线程模型）"),
    ("c05-struct",     "Day2 · 底层数据结构源码级剖析"),
    ("c06-expire",     "Day2 · 过期删除与内存淘汰"),
    ("c07-persist",    "Day2 · 持久化 RDB / AOF / 混合"),
    ("c08-repl",       "Day3 · 主从复制与哨兵"),
    ("c09-cluster",    "Day3 · Cluster 集群与槽迁移"),
    ("c10-cache",      "Day4 · 缓存三大问题与一致性"),
    ("c11-lock",       "Day4 · 分布式锁与 Lua/Function"),
    ("c12-hotkey",     "Day4 · 大Key/热Key 与线上治理"),
    ("c13-new8",       "Day4 · Redis 8.x 新特性全景"),
    ("c14-java",       "Day5 · Java 生态：Jedis/Lettuce/Redisson/Spring"),
    ("c15-tuning",     "Day5 · 性能调优与可观测性"),
    ("c16-scenario",   "Day5 · 大厂场景题实战手册"),
    ("c17-interview",  "Day5 · 面试八股深度问答（60 题四档分级）"),
    ("c18-checklist",  "Day5 · 生产上线 Checklist 与学习进度追踪"),
]

def build():
    shell = read(os.path.join(SRC, "shell.html"))
    nav_items = []
    bodies = []
    for cid, title in CHAPTERS:
        path = os.path.join(SRC, cid + ".html")
        if not os.path.exists(path):
            print("  [skip] missing fragment: %s" % cid)
            continue
        frag = read(path)
        # 一个 fragment 里可能包含多个 <section>（如 c04-thread 内含 c05-struct），
        # 全部提取出来，保证侧边栏不遗漏任何锚点
        found = re.findall(
            r'<section id="([^"]+)" class="anchor" data-title="([^"]+)"', frag)
        if not found:
            found = [(cid, title)]
        nav_items.extend(found)
        bodies.append(frag)

    nav_html = "\n".join(
        '      <a class="nav-link" href="#%s" data-target="%s">%s</a>' % (cid, cid, t)
        for cid, t in nav_items
    )
    content_html = "\n\n".join(bodies)

    html = shell.replace("<!--NAV-->", nav_html).replace("<!--CONTENT-->", content_html)
    out = os.path.join(BASE, "index.html")
    write(out, html)
    print("  [ok] built %s (%d chapters, %d anchors, %.1f KB)"
          % (out, len(bodies), len(nav_items), len(html.encode("utf-8")) / 1024.0))

if __name__ == "__main__":
    build()
