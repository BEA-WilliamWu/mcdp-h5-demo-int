/* Run with node; evaluates production AMD modules with isolated UI/network doubles. */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');
const root = path.resolve(__dirname, '../..');
const read = file => fs.readFileSync(path.join(root, 'consulting/channel', file), 'utf8');
let userContext;
vm.runInNewContext(read('extensions/components/host-to-host/api-password/user-context.js'), {
    define: (_, factory) => { userContext = factory(); }
});
const profile = value => ({dictionaryArray: [{nameValuePairDTOArray: [
    {name: 'userChannelType', value}
]}], roles: []});
for (const value of [null, {}, profile('BCO'), profile(null)]) {
    assert.equal(userContext.isHthUser(value), false);
}
assert.equal(userContext.isHthUser(profile(' hth ')), true);
const source = read('framework/elements/core/dashboard/dashboard.js');
const begin = source.indexOf('    self.loadHthApiPasswordSetup = function()');
const end = source.indexOf('    /**', begin);
const body = source.slice(begin, end);
const tick = () => new Promise(setImmediate);
async function dashboard(channel, outcome) {
    let resolve, reject, timer, calls = 0;
    const events = [], request = new Promise((a, b) => { resolve = a; reject = b; });
    const context = {
        isHthApiPasswordUser: userContext.isHthUser(profile(channel)),
        hthApiPasswordCheckPending: false, hthApiPasswordCheckFinished: false,
        hthApiPasswordTimer: null,
        self: {openBounceBackReminder: () => events.push('BCO'), hthApiPasswordSetupState: () => {}},
        sessionStorage: {getItem: () => null, setItem: () => {}},
        setTimeout: (fn, ms) => { assert.equal(ms, 5000); timer = fn; return 1; },
        clearTimeout: () => {},
        DashboardModel: {getHthApiPasswordStatus: () => {
            calls++;
            if (outcome === 'throw') throw new Error('transport unavailable');
            return request;
        }},
        $: () => ({trigger: () => events.push('HTH')})
    };
    vm.runInNewContext(body, context);
    context.self.loadHthApiPasswordSetup();
    await tick();
    if (channel !== 'HTH') {
        assert.equal(calls, 0); assert.deepEqual(events, ['BCO']); return;
    }
    if (outcome === 'throw') { assert.deepEqual(events, ['BCO']); return; }
    context.self.loadHthApiPasswordSetup();
    await tick(); assert.equal(calls, 1);
    if (outcome === 'timeout') {
        timer(); assert.deepEqual(events, ['BCO']);
        resolve({setupState: 'REQUIRED'}); await tick();
        assert.deepEqual(events, ['BCO']);
    } else if (outcome === 'dispose') {
        const dispose = source.slice(source.indexOf('    self.dispose = function () {'));
        vm.runInNewContext(dispose.slice(0, dispose.indexOf('    };') + 6), Object.assign(context, {
            resizeHandler: {dispose() {}}, pinReminder: {dispose() {}}, signerPinReminder: {dispose() {}}
        }));
        context.self.dispose(); timer(); resolve({setupState: 'REQUIRED'}); await tick();
        assert.deepEqual(events, []);
    } else {
        if (outcome === 'reject') reject(new Error('503'));
        else resolve({setupState: outcome});
        await tick();
        assert.deepEqual(events, [outcome === 'REQUIRED' || outcome === 'CODE_REQUIRED' ? 'HTH' : 'BCO']);
    }
}
function observable(value) {
    const result = function(next) { if (arguments.length) value = next; return value; };
    result.subscribe = () => {}; result.push = item => value.push(item); return result;
}
async function menu(channel, fail) {
    let constructor, calls = 0;
    const ko = {observable, observableArray: observable, utils: {extend: Object.assign, arrayForEach: (a, f) => a.forEach(f)}};
    vm.runInNewContext(read('extensions/components/security/security-menu/security-menu.js'), {
        define: (_, factory) => { constructor = factory(ko, {}, {
            getHthApiPasswordStatus: () => { calls++; return fail ? Promise.reject(new Error('503')) : Promise.resolve({setupState: 'ACTIVE', resetAllowed: true}); },
            getEnbleItokenManagement: () => ({done: () => ({always: fn => fn()})})
        }, {header: 'Security'}, {}, userContext); }
    });
    const model = new constructor({rootModel: {params: {}}, dashboard: {headerName() {}, userData: {userProfile: profile(channel)}},
        baseModel: {registerComponent() {}, small: () => false, cordovaDevice: () => false}});
    await tick();
    assert.equal(calls, channel === 'HTH' ? 1 : 0);
    const ids = Array.from(model.listItem(), item => item.id);
    assert(ids.includes('changePassword')); assert(ids.includes('setSecurityQuestion'));
    assert.equal(ids.includes('changeHthApiPassword'), channel === 'HTH' && !fail);
}
(async () => {
    await dashboard('BCO'); await dashboard(null);
    for (const result of ['REQUIRED', 'CODE_REQUIRED', 'ACTIVE', 'NOT_APPLICABLE', 'reject', 'throw', 'timeout', 'dispose']) await dashboard('HTH', result);
    await menu('BCO'); await menu(null); await menu('HTH', false); await menu('HTH', true);
    console.log('PASS: profile classification, BCO zero requests, HTH menus, duplicate suppression, failure, timeout, late response and disposal');
})().catch(error => { console.error(error); process.exitCode = 1; });
