/* Real JET 8.3/Knockout form in an isolated browser. No UAT or notification requests. */
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const http = require('http');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '../../consulting/channel');
const component = 'extensions/components/host-to-host/api-password/';
const jet = 'framework/js/libs/oraclejet/8.3.0/';
const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/') {
        res.setHeader('Content-Type', 'text/html');
        res.end(`<!doctype html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="/${jet}css/libs/oj/v8.3.0/alta/oj-alta-min.css">
<link rel="stylesheet" href="/framework/css/obdx-font.css">
<link rel="stylesheet" href="/${component}api-password.css">
<style>body {max-width:1000px;margin:24px auto;} h2 {color:#005582;}</style></head>
<body><main class="api-password-container" id="view"></main>
<script src="/${jet}js/libs/require/require.js"></script>
<script>
require.config({baseUrl:'/'});
define('framework/js/configurations/config', [], function(){return {oracleJet:{baseUrl:'framework/js/libs/oraclejet',version:'8.3.0',hostedAt:'local'}};});
define('${component}model', [], function(){return {status:function(){return Promise.resolve({});}};});
define('${component}transport', [], function(){return function(){return Promise.reject(new Error('fixture encryption failure'));};});
require(['framework/js/configurations/path-config'], function(){
    require(['knockout','${component}api-password','text!${component}api-password.html','ojs/ojcontext','ojs/ojknockout'], function(ko,Form,html,Context){
        window.form=new Form({data:{mode:new URLSearchParams(location.search).get('mode')||'RESET'},
            dashboard:{headerName:function(){},switchModule:function(){},loadComponent:function(){}},
            baseModel:{registerComponent:function(){},showComponentValidationErrors:function(){return true;},showMessages:function(){}}});
        document.getElementById('view').innerHTML=html;
        ko.applyBindings(form,document.getElementById('view'));
        Context.getContext(document.getElementById('view')).getBusyContext().whenReady().then(function(){window.ready=true;});
    });
});
</script></body></html>`);
        return;
    }
    const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
        res.writeHead(404); res.end(); return;
    }
    res.setHeader('Content-Type', ({'.js':'application/javascript','.css':'text/css','.html':'text/html','.woff':'font/woff','.woff2':'font/woff2'})[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
});
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const browser = await chromium.launch({headless:true, executablePath:process.env.BROWSER_PATH});
    try {
        const page = await browser.newPage({viewport:{width:1100,height:850}});
        const errors=[]; page.on('pageerror', e=>errors.push(e.message));
        for (const mode of ['SETUP','RESET']) {
            await page.goto(`http://127.0.0.1:${server.address().port}/?mode=${mode}`);
            await page.waitForFunction(()=>window.ready);
            assert.deepEqual(errors,[]);
            const first=page.locator('#hthApiNewPassword input');
            const second=page.locator('#hthApiConfirmPassword input');
            const code=page.locator('#hthApiPasswordCode input');
            const buttons=page.locator('.password-visibility-toggle');
            assert.equal(await buttons.count(),2);
            await first.fill('TestPassword123'); await second.fill('TestPassword123'); await code.fill('123456');
            await code.press('Tab');
            assert.equal(await first.getAttribute('type'),'password');
            await buttons.nth(0).click();
            assert.equal(await first.getAttribute('type'),'text');
            assert.equal(await first.inputValue(),'TestPassword123');
            assert.equal(await second.getAttribute('type'),'password');
            assert.equal(await code.getAttribute('type'),'password');
            assert.equal(await buttons.nth(0).getAttribute('aria-pressed'),'true');
            await buttons.nth(1).focus(); await page.keyboard.press('Space');
            assert.equal(await second.getAttribute('type'),'text');
            await buttons.nth(0).focus(); await page.keyboard.press('Enter');
            assert.equal(await first.getAttribute('type'),'password');
            const values=await page.evaluate(()=>[form.newPassword(),form.confirmPassword(),form.passwordCode()]);
            assert.deepEqual(values,['TestPassword123','TestPassword123','123456']);
            await page.evaluate(()=>form.submitting(true));
            assert(await buttons.nth(0).isDisabled()); assert(await buttons.nth(1).isDisabled());
            await page.evaluate(()=>{form.submitting(false);form.cancel();});
            await page.waitForFunction(()=>document.querySelector('#hthApiNewPassword input').value==='');
            assert.equal(await first.inputValue(),''); assert.equal(await second.inputValue(),'');
            assert.equal(await second.getAttribute('type'),'password');
            assert.equal(await buttons.nth(1).getAttribute('aria-pressed'),'false');
            await first.fill('TestPassword123'); await second.fill('TestPassword123'); await code.fill('123456');
            await buttons.nth(0).click(); await buttons.nth(1).click();
            if(process.env.SCREENSHOT_PATH && mode==='RESET') await page.screenshot({path:process.env.SCREENSHOT_PATH});
            await page.evaluate(()=>form.submit());
            await page.waitForFunction(()=>!form.submitting() && document.querySelector('#hthApiNewPassword input').value==='');
            assert.equal(await first.inputValue(),''); assert.equal(await first.getAttribute('type'),'password');
            assert.equal(await second.getAttribute('type'),'password');
            assert.equal(await code.getAttribute('type'),'password');
            await page.setViewportSize({width:390,height:850});
            const bounds=await buttons.nth(0).boundingBox(); assert(bounds.x+bounds.width<=390);
            await page.setViewportSize({width:1100,height:850});
            assert.deepEqual(errors,[]);
        }
        console.log('PASS: real JET/Knockout SETUP and RESET; independent eye toggles, keyboard/ARIA, retained values, masked Code, submit/cancel cleanup and mobile layout');
    } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>server.close());
