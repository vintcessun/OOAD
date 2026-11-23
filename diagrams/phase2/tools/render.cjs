/* 使用本机 Chromium/Edge 对离线图册截图、导出 PNG 并核对交互。 */
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const assert = require('node:assert/strict');
const playwright = require(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
const out=path.resolve(__dirname,'..');
const browserPath=process.env.BROWSER_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
(async()=>{
  const browser=await playwright.chromium.launch({executablePath:browserPath,headless:true,args:['--disable-gpu','--no-sandbox'],downloadsPath:path.join(out,'data')});
  const page=await browser.newPage({viewport:{width:1500,height:1050},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const checks=[];
  const check=(name,ok)=>{checks.push({name,ok});assert.ok(ok,name)};
  try{
    for(const name of ['usecase-center','usecase-course','er-overview','architecture-runtime','architecture-modules']){
      await page.goto(pathToFileURL(path.join(out,`assets/${name}.svg`)).href);
      await page.locator('svg').waitFor();
      const bounds=await page.evaluate(()=>({width:Math.ceil(document.documentElement.getBoundingClientRect().width),height:Math.ceil(document.documentElement.getBoundingClientRect().height),view:document.querySelector('svg').getAttribute('viewBox')}));
      const [, ,w,h]=bounds.view.split(' ').map(Number);
      await page.setViewportSize({width:w,height:h});
      await page.locator('svg').screenshot({path:path.join(out,`assets/${name}.png`)});
      const overflow=await page.evaluate(()=>[...document.querySelectorAll('text')].filter(t=>{const b=t.getBBox(),v=t.ownerSVGElement.viewBox.baseVal;return b.x<0||b.y<0||b.x+b.width>v.width||b.y+b.height>v.height}).map(t=>t.textContent));
      check(`${name}：文本位于画布内`,overflow.length===0);
    }
    await page.setViewportSize({width:1500,height:1050});
    await page.goto(pathToFileURL(path.join(out,'index.html')).href);
    await page.waitForLoadState('load');
    await page.addStyleTag({content:'*,*::before,*::after{animation:none!important;transition:none!important}'});
    check('总览默认可见',await page.locator('#overview').isVisible());
    await page.screenshot({path:path.join(out,'assets/preview-desktop.png'),fullPage:true});
    await page.locator('nav a[href="#api"]').click();
    check('接口页签切换',await page.locator('#api').isVisible());
    check('中心默认显示68操作',await page.locator('.endpoint:visible').count()===68);
    await page.locator('#api-search').fill('addRoleInheritance');
    check('operationId检索',await page.locator('.endpoint:visible').count()===1);
    await page.locator('.endpoint:visible > summary').click();
    check('请求模型展开',await page.locator('.endpoint:visible .op-detail').isVisible());
    await page.locator('#api-search').fill('');
    await page.locator('#api-scope').selectOption('course');
    check('课程业务80操作',await page.locator('.endpoint:visible').count()===80);
    await page.locator('#api-method').selectOption('POST');
    check('HTTP方法筛选',await page.locator('.endpoint:visible').count()>0 && await page.locator('.endpoint:visible:not([data-method="POST"])').count()===0);
    await page.locator('#api-method').selectOption('all');
    await page.locator('#api-scope').selectOption('all');
    check('合计148个操作',await page.locator('.endpoint:visible').count()===148);
    await page.locator('#api-group').selectOption('角色、直接权限与继承');
    check('功能分组筛选',await page.locator('.endpoint:visible').count()===12);
    await page.screenshot({path:path.join(out,'assets/preview-api.png'),fullPage:false});
    await page.locator('nav a[href="#data"]').click();
    check('完整十五表字典',await page.locator('.dictionary').count()===15);
    const roleTable=page.locator('.dictionary').filter({has:page.locator('summary code', {hasText:/^sys_role$/})});
    check('角色字典包含角色码',await roleTable.locator('td code').filter({hasText:/^code$/}).count()===1);
    check('角色字典反映 V7',await roleTable.locator('td code').filter({hasText:/^identity_role$/}).count()===0);
    await page.locator('#data [data-zoom="+"]').click();
    check('图形缩放',await page.locator('#data .zoom-value').textContent()==='125%');
    await page.locator('#data [data-zoom="reset"]').click();
    await page.screenshot({path:path.join(out,'assets/preview-er.png'),fullPage:false});
    check('桌面无横向页面溢出',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    check('所有图可加载',await page.evaluate(()=>[...document.images].every(i=>!i.complete||i.naturalWidth>0)));
    const broken=await page.evaluate(async()=>{
      const links=[...document.querySelectorAll('a[href]')].map(a=>a.getAttribute('href')).filter(h=>!h.startsWith('#')&&!h.startsWith('http'));
      return [...new Set(links)];
    });
    const localMissing=broken.filter(p=>!fs.existsSync(path.resolve(out,p.split('#')[0])));
    check('本地链接目标存在',localMissing.length===0);
    await page.setViewportSize({width:390,height:844});
    await page.locator('nav a[href="#overview"]').click();
    await page.screenshot({path:path.join(out,'assets/preview-mobile.png'),fullPage:true});
    check('手机无横向页面溢出',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    check('无页面脚本错误',errors.length===0);
    const report=JSON.parse(fs.readFileSync(path.join(out,'data/verification.json'),'utf8'));
    report.status='static-and-browser-verified';report.browser={engine:'local Microsoft Edge / Chromium',viewport:[1500,1050],mobile:[390,844],checks,errors,note:'仅验证图册、提取一致性与浏览器交互；未执行产品业务测试。'};
    fs.writeFileSync(path.join(out,'data/verification.json'),JSON.stringify(report,null,2));
    console.log(JSON.stringify(report.browser,null,2));
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
