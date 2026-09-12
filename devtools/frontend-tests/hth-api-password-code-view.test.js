/* Production Knockout bindings in an isolated browser; no application requests are sent. */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '../..');
const channel = path.join(root, 'consulting/channel');
const read = file => fs.readFileSync(path.join(channel, file), 'utf8');
const source = read('extensions/components/user-management/user-read/user-read.js');
const html = read('extensions/components/user-management/user-read/user-read.html');
const init = source.slice(source.indexOf('        self.hthApiPasswordCode ='), source.indexOf('        if (rootParams.data)'));
const loadStart = source.indexOf('            if (self.userExtensionData() && self.userExtensionData().userChannelType === "HTH")');
const load = source.slice(loadStart, source.indexOf('            self.dataLoaded(true);', loadStart));
const toggle = source.slice(source.indexOf('        self.toggleHthApiPasswordCodeVisible ='), source.lastIndexOf('    };'));
const markup = html.slice(html.indexOf('        <!-- HTH Code lifecycle'), html.indexOf('        <!-- ko if : approverReview -->', html.indexOf('        <!-- HTH Code lifecycle')));
let nls, editNls;
vm.runInNewContext(read('extensions/resources/nls/user-read.js'), {define: (_, factory) => { nls = factory({}, {}, {}, {}).root; }});
vm.runInNewContext(read('extensions/resources/nls/user-management.js'), {define: (dependencies, factory) => {
    editNls = factory(...dependencies.map(() => ({}))).root;
}});
assert.equal(nls.info.hthUsed, editNls.info.hthUsed);
assert(init && load && toggle && markup);

(async () => {
    const browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_PATH});
    try {
        const page = await browser.newPage();
        await page.setContent('<main id="view"></main>');
        await page.addScriptTag({path: path.join(channel, 'framework/js/libs/oraclejet/8.3.0/js/libs/knockout/knockout-3.5.0.debug.js')});
        const result = await page.evaluate(({init, load, toggle, markup, nls}) => {
            let assertions = 0;
            function check(value, label) { if (!value) throw new Error(label); assertions++; }
            const view = document.getElementById('view');
            for (const userChannelType of ['HTH', 'BCO']) {
                let maskedCalls = 0, revealCalls = 0, maskedResponse, revealResponse;
                const self = {
                    nls, userExtensionData: ko.observable({userChannelType}),
                    userFullData: ko.observable({partyId: {value: 'PARTY'}, username: 'USER'})
                };
                const rootParams = {baseModel: {showMessages() { throw new Error('Unexpected request error'); }}};
                const UserReadModel = {
                    getHthApiPasswordCodeMasked(party, user) {
                        check(party === 'PARTY' && user === 'USER', 'Request uses target user');
                        maskedCalls++;
                        return {done(callback) { maskedResponse = callback; }};
                    },
                    revealHthApiPasswordCode(id) {
                        check(id === 'CODE_ID', 'Reveal uses current Code id');
                        revealCalls++;
                        return {done(callback) { revealResponse = callback; return {fail() {}}; }};
                    }
                };
                new Function('ko', 'self', 'UserReadModel', 'rootParams', init + toggle + load)(ko, self, UserReadModel, rootParams);
                self.$component = self;
                view.innerHTML = markup;
                ko.applyBindings(self, view);
                const response = (codeStatus, code) => ({status: {result: 'SUCCESSFUL'}, codeId: 'CODE_ID',
                    codeStatus, code, canReveal: true, maskedCode: '******'});
                if (userChannelType === 'BCO') {
                    check(maskedCalls === 0 && !view.textContent.trim(), 'BCO makes no Code request and displays no HTH content');
                } else {
                    check(maskedCalls === 1, 'HTH loads masked status once');
                    maskedResponse(response('USED'));
                    check(view.textContent.includes(nls.info.hthUsed), 'USED message renders directly on User view');
                    check(!view.querySelector('a.switch-button'), 'Used Code has no reveal button');
                    self.toggleHthApiPasswordCodeVisible();
                    check(revealCalls === 0, 'Used Code cannot trigger reveal');
                    for (const status of ['PENDING', 'ACTIVE']) {
                        maskedResponse(response(status));
                        check(!view.textContent.includes(nls.info.hthUsed) && view.querySelector('a.switch-button'), status + ' can reveal');
                    }
                    self.toggleHthApiPasswordCodeVisible();
                    revealResponse(response('ACTIVE', '123456'));
                    check(view.textContent.includes('123456'), 'Active Code reveal displays returned value');
                    self.toggleHthApiPasswordCodeVisible();
                    check(!view.textContent.includes('123456'), 'Hide restores mask');
                    self.toggleHthApiPasswordCodeVisible();
                    revealResponse(response('USED', '123456'));
                    check(view.textContent.includes(nls.info.hthUsed) && !view.textContent.includes('123456'), 'Consumed during reveal updates status and clears plaintext');
                    check(!view.querySelector('a.switch-button'), 'Consumed during reveal removes button');
                    maskedResponse({...response('ACTIVE'), canReveal: false});
                    check(!view.querySelector('a.switch-button'), 'Backend reveal flag is respected');
                    maskedResponse(response('EXPIRED'));
                    check(!view.querySelector('a.switch-button'), 'Expired Code has no reveal button');
                }
                ko.cleanNode(view);
                view.innerHTML = '';
            }
            return assertions;
        }, {init, load, toggle, markup, nls});
        console.log('PASS: ' + result + ' production User-view DOM assertions: USED notice, reveal lifecycle, async updates and BCO isolation');
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
