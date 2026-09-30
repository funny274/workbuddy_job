#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Java 并发编程课程 —— 主构建脚本

用法: python build.py
产出: index.html（完全自包含的单文件教程）
"""

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from assets import CSS, JS                       # noqa: E402
from chapters_a import Writer                    # noqa: E402
import chapters_a, chapters_b, chapters_c        # noqa: E402

OUT = HERE / "index.html"

HTML_HEAD = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Java 并发编程完全教程 · 从入门到 10 年专家</title>
<meta name="description" content="18 章 · 33 个可运行案例 · 覆盖锁、AQS、线程池、并发容器、虚拟线程，对齐华为/阿里/腾讯面试要求">
<style>
%s
</style>
</head>
<body>
<div class="topbar"><div class="topbar-bar"></div></div>
<button class="menu-btn" aria-label="切换目录">≡</button>
<div class="layout">

<aside class="sidebar">
  <div class="side-head">
    <div class="side-title"><span class="logo">JC</span>Java 并发编程教程</div>
    <div class="side-sub">18 章 · 33 个可运行案例<br>从入门到 10 年专家水平</div>
    <div class="side-progress"><div class="side-progress-bar"></div></div>
  </div>
  <div class="side-search-wrap">
    <input class="side-search" type="text" placeholder="搜索章节…" aria-label="搜索章节">
  </div>
  <nav class="side-nav">
    <ul>
%s
    </ul>
  </nav>
  <div class="side-foot">
    所有代码在 JDK 25.0.3 LTS 实测通过<br>
    兼容 JDK 17 / 21
  </div>
</aside>

<main class="main">
<article class="content">
%s
</article>
</main>

</div>
<script>
%s
</script>
</body>
</html>
"""


def main():
    w = Writer()

    chapters_a.ch_intro(w)
    chapters_a.ch01(w)
    chapters_a.ch02(w)
    chapters_a.ch03(w)
    chapters_a.ch04(w)
    chapters_a.ch05(w)
    chapters_a.ch06(w)
    chapters_b.ch07(w)
    chapters_b.ch08(w)
    chapters_b.ch09(w)
    chapters_b.ch10(w)
    chapters_b.ch11(w)
    chapters_b.ch12(w)
    chapters_c.ch13(w)
    chapters_c.ch14(w)
    chapters_c.ch15(w)
    chapters_c.ch16(w)
    chapters_c.ch17(w)
    chapters_c.ch18(w)
    chapters_c.appendix(w)

    html = HTML_HEAD % (CSS, "\n".join(w.toc), "\n".join(w.body), JS)
    OUT.write_text(html, encoding="utf-8")

    size_kb = len(html.encode("utf-8")) / 1024
    print("[OK] 已生成 %s" % OUT)
    print("     大小: %.1f KB" % size_kb)
    print("     章节: %d" % len(w.toc))


if __name__ == "__main__":
    main()
