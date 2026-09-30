// 用 CDP 控制 headless Chrome，滚动到指定元素并截图
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = 9333;
const url = 'file:///C:/Users/issuser/WorkBuddy/2026-09-30-14-59-10/springboot-mastery/index.html';
const outDir = path.join(__dirname, 'shot');

const child = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
  '--remote-debugging-port=' + PORT, '--window-size=1440,1200', url
], { stdio: 'ignore' });

function get(p){ return new Promise((res,rej)=>{ http.get('http://127.0.0.1:'+PORT+p, r=>{ let d=''; r.on('data',c=>d+=c); r.on('end',()=>res(JSON.parse(d))); }).on('error',rej); }); }
function sleep(ms){ return new Promise(r=>setTimeout(r,ms)); }

(async ()=>{
  let targets;
  for(let i=0;i<40;i++){
    try{ targets = await get('/json/list'); if(targets.find(t=>t.type==='page'&&t.webSocketDebuggerUrl)) break; }catch(e){}
    await sleep(300);
  }
  const page = targets.find(t=>t.type==='page'&&t.webSocketDebuggerUrl);
  if(!page){ console.log('找不到页面'); child.kill(); return; }

  const WebSocket = require('ws');
  let ws;
  try{ ws = new WebSocket(page.webSocketDebuggerUrl); }catch(e){ console.log('需要 ws 模块:', e.message); child.kill(); return; }
  await new Promise(r=>ws.on('open',r));
  let id=0; const pend={};
  ws.on('message', m=>{ const o=JSON.parse(m); if(o.id&&pend[o.id]){ pend[o.id](o.result); delete pend[o.id]; } });
  function send(method, params){ id++; const myId=id; ws.send(JSON.stringify({id:myId,method,params:params||{}})); return new Promise(r=>pend[myId]=r); }

  await send('Runtime.enable');
  await send('Page.enable');
  await sleep(2500);

  // 找到第一个含代码块的元素并滚动到它
  const r = await send('Runtime.evaluate', { expression: `
    (function(){
      var pre = document.querySelector('pre.hl');
      if(!pre) return {ok:false, n: document.querySelectorAll('pre.hl').length};
      pre.scrollIntoView({block:'center'});
      return {ok:true, total: document.querySelectorAll('pre.hl').length, text: pre.innerText.slice(0,120)};
    })()
  `, returnByValue:true });
  console.log('查找代码块:', JSON.stringify(r.result.value));
  await sleep(800);

  const shot = await send('Page.captureScreenshot', { format:'png' });
  fs.writeFileSync(path.join(outDir,'codeblock.png'), Buffer.from(shot.data,'base64'));
  console.log('已截图 codeblock.png');

  // 再滚到 mid 位置
  await send('Runtime.evaluate', { expression: `window.scrollTo(0, document.body.scrollHeight*0.35);` });
  await sleep(600);
  const shot2 = await send('Page.captureScreenshot', { format:'png' });
  fs.writeFileSync(path.join(outDir,'mid.png'), Buffer.from(shot2.data,'base64'));
  console.log('已截图 mid.png');

  ws.close(); child.kill();
  process.exit(0);
})().catch(e=>{ console.log('错误:', e.message); child.kill(); process.exit(1); });
