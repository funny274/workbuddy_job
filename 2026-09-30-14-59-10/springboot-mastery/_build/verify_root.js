// 验证根因
const ph = "\u0001" + "0" + "\u0001"; // 占位符 \u00010\u0001
console.log('占位符码点:', [...ph].map(c => c.codePointAt(0).toString(16)).join(' '));
const numRe = /\b(\d+\.?\d*[fFdDlL]?)\b/g;
console.log('数字正则是否匹配占位符内数字:', numRe.test(ph));
console.log('替换后:', ph.replace(/\b(\d+\.?\d*[fFdDlL]?)\b/g, '<span class="num">$1</span>')
  .split('').map(c => c === '\u0001' ? '[SOH]' : c).join(''));

// 验证 $ 替换问题：store[i] 里若含 $ 会被 replace 二次解释
const s = "价格 $100";
const replaced = "<span>".replace("<span>", s);
console.log('\n$ 替换测试(应原样保留 $100):', replaced);

// 验证字符串/注释 stash 后被 escapeHtml 二次转义的情况
const esc = t => t.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;");
console.log('\n双重转义测试:', esc('<span class="cmt">' + esc('a < b & c') + '</span>'));
