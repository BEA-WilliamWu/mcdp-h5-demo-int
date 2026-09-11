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
        isHthFirstLoginFlowDone: outcome !== 'firstLogin',
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
    if (outcome === 'firstLogin') {
        assert.equal(calls, 0); assert.deepEqual(events, []);
        assert.equal(context.hthApiPasswordCheckFinished, false);
        context.isHthFirstLoginFlowDone = true;
        context.self.loadHthApiPasswordSetup();
        await tick(); assert.equal(calls, 1);
        resolve({setupState: 'REQUIRED'}); await tick();
        assert.deepEqual(events, ['HTH']); return;
    }
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
const ko = require(path.join(root, 'consulting/channel/framework/js/libs/oraclejet/8.3.0/js/libs/knockout/knockout-3.5.0.debug.js'));
const observable = ko.observable;
async function menu(channel, fail) {
    let constructor, calls = 0;
    const ko = {observable, observableArray: value => observable(value || []), utils: {extend: Object.assign, arrayForEach: (a, f) => a.forEach(f)}};
    vm.runInNewContext(read('extensions/components/security/security-menu/security-menu.js'), {
        define: (_, factory) => { constructor = factory(ko, {}, {
            getHthApiPasswordStatus: () => { calls++; return fail ? Promise.reject(new Error('503')) : Promise.resolve({setupState: 'ACTIVE', resetAllowed: true}); },
            getEnbleItokenManagement: () => ({done: () => ({always: fn => fn()})})
        }, {header: 'Security'}, {}, userContext); }
    });
    const model = new constructor({rootModel: {params: {}}, dashboard: {headerName() {}, userData: {userProfile: profile(channel)}},
        baseModel: {registerComponent() {}, small: () => false, cordovaDevice: () => false}});
    await tick();
    assert.equal(calls, 0);
    const ids = Array.from(model.listItem, item => item.id);
    assert(ids.includes('changePassword')); assert(ids.includes('setSecurityQuestion'));
    assert.equal(ids.includes('changeHthApiPassword'), false);
}
async function profileNavigation(channel, state, resetAllowed, outcome, mobile = false, merchant = false) {
    let constructor, resolve, reject, calls = 0;
    const navigation = [], registrations = [], headers = [];
    const request = new Promise((a, b) => { resolve = a; reject = b; });
    vm.runInNewContext(read('extensions/components/security/side-menu/side-menu.js'), {
        define: (_, factory) => { constructor = factory(ko, {header: 'Profile'}, userContext,
            {status: () => { calls++; return request; }},
            {menuLabel: 'HTH API Password', profileHeader: 'Profile'}, function ArrayDataProvider(data) { this.data = data; }); }
    });
    const user = profile(channel);
    if (merchant) user.roles.push('Merchant_TL');
    const dashboard = {userData: {userProfile: user}, headerName: name => headers.push(name),
        loadComponent: (name, data) => navigation.push([name, data.mode])};
    const baseModel = {registerComponent: (name, parent) => registrations.push([name, parent]), small: () => mobile};
    const page = new constructor({rootModel: {params: {}}, dashboard, baseModel});
    page.getRootContext({});
    const originalIds = merchant ? ['MyProfile', 'securityAndLogin'] : ['MyProfile', 'alerts', 'securityAndLogin'];
    assert.deepEqual(Array.from(page.sideMenuList(), item => item.id), originalIds);
    if (!mobile) assert.equal(page.menuSelectionForSideMenu(), 'profile');
    await tick();
    assert.equal(calls, channel === 'HTH' ? 1 : 0);
    if (channel === 'HTH') {
        if (outcome === 'dispose') page.dispose();
        if (outcome === 'failure') reject(new Error('403'));
        else resolve({setupState: state, resetAllowed});
        await tick();
    }
    const visible = channel === 'HTH' && state === 'ACTIVE' && !outcome;
    assert.deepEqual(Array.from(page.sideMenuList(), item => item.id), visible ? [...originalIds, 'hthApiPassword'] : originalIds);
    if (visible) {
        page.selectedSideMenuItem('hthApiPassword');
        assert.deepEqual(registrations[registrations.length - 1], ['api-password', 'host-to-host']);
        if (mobile) {
            assert.deepEqual(navigation, [['api-password', 'RESET']]);
        } else {
            assert.equal(page.menuSelectionForSideMenu(), 'api-password');
            assert.equal(page.showDetailParams.mode, 'RESET');
            assert.equal(page.showDetailParams.embedded, true);
            assert.equal(headers[headers.length - 1], 'Profile');
            page.getRootContext({});
            assert.equal(page.selectedSideMenuItem(), 'hthApiPassword', 'descendant rendering must retain selection');
            let Form;
            vm.runInNewContext(read('extensions/components/host-to-host/api-password/api-password.js'), {
                define: (_, factory) => { Form = factory(ko, {status: () => Promise.resolve({})}, () => {}, {}); },
                window: {crypto: require('crypto').webcrypto}, Uint8Array
            });
            const headerCount = headers.length;
            const form = new Form({rootModel: {params: {mode: 'SETUP'}}, data: page.showDetailParams, dashboard, baseModel});
            assert.equal(form.isSetup, false, 'embedded RESET data overrides route mode');
            assert.equal(headers.length, headerCount, 'inline form must retain Profile heading');
            form.newPassword('SyntheticTest123'); form.confirmPassword('SyntheticTest123'); form.passwordCode('123456');
            form.cancel();
            assert.equal(page.selectedSideMenuItem(), 'MyProfile');
            assert.equal(page.menuSelectionForSideMenu(), 'profile');
            assert.equal(form.newPassword(), null); assert.equal(form.confirmPassword(), null); assert.equal(form.passwordCode(), null);
            assert.deepEqual(navigation, [], 'desktop navigation stays inside Profile');
        }
    }
    if (outcome !== 'dispose') page.dispose();
    const count = registrations.length;
    page.selectedSideMenuItem('securityAndLogin');
    assert.equal(registrations.length, count, 'disposed navigation subscription must stop');
}
async function repeatLoginReminder() {
    for (const name of ['login-form-web', 'login-form-mobile']) {
        const login = read(`extensions/components/login/${name}/${name}.js`);
        const reset = login.match(/sessionStorage\.setItem\("hthApiPasswordSetupPromptLoaded", "false"\);/);
        assert(reset, name + ' resets the prompt at login');
        const storage = new Map();
        const sessionStorage = {getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value)};
        async function visit(state, firstLoginFlowDone = true) {
            let calls = 0;
            const events = [];
            const context = {
                isHthApiPasswordUser: true, isHthFirstLoginFlowDone: firstLoginFlowDone,
                hthApiPasswordCheckPending: false, hthApiPasswordCheckFinished: false, hthApiPasswordTimer: null,
                self: {openBounceBackReminder: () => events.push('BCO'), hthApiPasswordSetupState() {}},
                sessionStorage, setTimeout: () => 1, clearTimeout() {},
                DashboardModel: {getHthApiPasswordStatus: () => { calls++; return Promise.resolve({setupState: state}); }},
                $: () => ({trigger: () => events.push('HTH')})
            };
            vm.runInNewContext(body, context); context.self.loadHthApiPasswordSetup(); await tick();
            return {calls, events};
        }
        vm.runInNewContext(reset[0], {sessionStorage});
        assert.deepEqual(await visit('REQUIRED', false), {calls: 0, events: []});
        assert.deepEqual(await visit('REQUIRED'), {calls: 1, events: ['HTH']});
        assert.deepEqual(await visit('REQUIRED'), {calls: 0, events: ['BCO']});
        vm.runInNewContext(reset[0], {sessionStorage});
        assert.deepEqual(await visit('CODE_REQUIRED'), {calls: 1, events: ['HTH']});
        vm.runInNewContext(reset[0], {sessionStorage});
        assert.deepEqual(await visit('ACTIVE'), {calls: 1, events: ['BCO']});
    }
}
(async () => {
    await repeatLoginReminder();
    await dashboard('BCO'); await dashboard(null);
    for (const result of ['REQUIRED', 'CODE_REQUIRED', 'ACTIVE', 'NOT_APPLICABLE', 'reject', 'throw', 'timeout', 'dispose', 'firstLogin']) await dashboard('HTH', result);
    await menu('BCO'); await menu(null); await menu('HTH', false); await menu('HTH', true);
    await profileNavigation('BCO', 'ACTIVE', true); await profileNavigation(null, 'ACTIVE', true);
    await profileNavigation('BCO', 'ACTIVE', true, null, false, true);
    for (const state of ['ACTIVE', 'REQUIRED', 'CODE_REQUIRED', 'NOT_APPLICABLE', 'LOCKED', 'UNKNOWN']) {
        await profileNavigation('HTH', state, false); await profileNavigation('HTH', state, true);
    }
    await profileNavigation('HTH', 'ACTIVE', false, 'failure');
    await profileNavigation('HTH', 'ACTIVE', false, 'dispose');
    await profileNavigation('HTH', 'ACTIVE', false, null, true);
    await profileNavigation('HTH', 'ACTIVE', false, null, false, true);
    assert(!read('extensions/components/base-components/profile/profile.html').includes('changeHthApiPassword'));
    assert(!read('extensions/components/base-components/profile/profile.js').includes('host-to-host/api-password'));
    console.log('PASS: HTH/BCO classification, BCO zero status requests, Profile left navigation without RESET Code, inline/mobile RESET mode, cancel/secret cleanup, stable selection, original security menus, failure/disposal, login reminders and first-login completion');
})().catch(error => { console.error(error); process.exitCode = 1; });
