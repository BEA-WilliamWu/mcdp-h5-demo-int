/* Execute production toggle callbacks, HTH handlers and KO conditions with isolated
 * config/HTTP/UI fixtures. Run: node consulting/tests/hth_onboarding/portal_toggles.cjs */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = process.env.HTH_PORTAL_TEST_ROOT || path.resolve(__dirname, '../../..');
const base = 'consulting/channel/extensions/';
const read = file => fs.readFileSync(path.join(root, base, file), 'utf8').replace(/\r\n/g, '\n');
const CM = 'ENABLE_HTH_CM_ONBOARDING', BM = 'ENABLE_HTH_BM_ONBOARDING';
let cases = 0;
const check = (actual, expected, label) => { assert.equal(actual, expected, label); cases++; };
function observable(value) {
    return function (next) { if (arguments.length) value = next; return value; };
}
function deferred() {
    let state, value;
    const success = [], failure = [];
    return {
        done(fn) { success.push(fn); if (state === 'ok') fn(value); return this; },
        fail(fn) { failure.push(fn); if (state === 'error') fn(value); return this; },
        resolve(data) { state = 'ok'; value = data; success.forEach(fn => fn(data)); },
        reject() { state = 'error'; failure.forEach(fn => fn()); }
    };
}
// Indentation-delimited production blocks (no reimplementation of gating logic).
function block(source, marker, closing) {
    const start = source.indexOf(marker);
    assert(start >= 0, marker);
    const line = source.lastIndexOf('\n', start) + 1;
    const indent = source.slice(line, start);
    assert(!indent.trim());
    const end = source.indexOf('\n' + indent + closing, start);
    assert(end > start, marker + ' close');
    return source.slice(start, end + indent.length + closing.length + 1);
}
const pages = [
    ['components/user-management/users-create/users-create.js', 'UsersCreateModel', 'isUserChannelTypeEnabled'],
    ['components/user-management/user-read/user-read.js', 'UserReadModel', 'isUserChannelTypeEnabled'],
    ['components/user-management/users-update/users-update.js', 'UsersUpdateModel', 'isUserChannelTypeEnabled'],
    ['components/account-access-management/summary/summary.js', 'ExclusionModel', 'isHthFeatureEnabled']
];
for (const [file, modelName, flagName] of pages) {
    const source = read(file);
    const init = source.slice(source.indexOf('self.' + flagName + ' ='), source.indexOf('const isBM'));
    const configStart = source.indexOf('const isBM');
    const failureStart = source.indexOf('}).fail(function () {', configStart);
    const configEnd = source.indexOf('});', failureStart);
    assert(configStart >= 0 && failureStart > configStart && configEnd > failureStart);
    const config = source.slice(configStart, configEnd + 3);
    for (const segment of ['ADMIN', 'CORPADMIN']) {
        const key = segment === 'ADMIN' ? BM : CM, other = key === BM ? CM : BM;
        for (const own of ['Y', 'N', undefined, 'true', 'ERROR']) {
            for (const opposite of ['Y', 'N']) {
                for (const configFirst of [false, true]) {
                    let maskedCalls = 0;
                    const request = deferred(), requests = [];
                    const self = {
                        accessLevel: () => 'USER', userAccessPageTitle: () => 'title',
                        userFullData: () => ({partyId: {value: 'fixture-party'}, username: 'fixture-user'}),
                        approverReview: () => false, userExtensionData: () => ({}),
                        updateHthApiPasswordCode: () => {}, hthApiPasswordPendingCodeId: () => null
                    };
                    const model = {
                        fetchCustomConfiguration: key => { requests.push(key); return request; },
                        getHthApiPasswordCodeMasked: () => { maskedCalls++; return deferred(); }
                    };
                    const context = vm.createContext({self, [modelName]: model,
                        ko: {observable, utils: {arrayFirst: (list, fn) => list.find(fn)}},
                        rootParams: {dashboard: {appData: {segment}, headerName: () => {}}},
                        hthCodeSnapshot: {cdcNo: 'fixture-party', userID: 'fixture-user', hthApiPasswordCodeId: 'fixture-code'}
                    });
                    vm.runInContext(init + '\n' + config, context);
                    check(requests.length, 1, file + ': one config request');
                    check(requests[0], key, file + ': current portal');
                    check(self[flagName](), flagName === 'isHthFeatureEnabled' ? 'N' : false, 'pending is disabled');
                    const resolve = () => own === 'ERROR' ? request.reject() : request.resolve({
                        listCustomConfigDTO: [{propertyId: other, propertyValue: opposite},
                            ...(own === undefined ? [] : [{propertyId: key, propertyValue: own}])]
                    });
                    if (configFirst) resolve();
                    // Both live-user and approval-snapshot callbacks must wait for configuration.
                    const waiters = [...source.matchAll(/hthToggleRequest\.done\(function \(\) \{/g)];
                    for (const match of waiters) {
                        const start = source.lastIndexOf('\n', match.index) + 1;
                        const indent = source.slice(start, match.index);
                        const end = source.indexOf('\n' + indent + '});', match.index);
                        vm.runInContext(source.slice(match.index, end + indent.length + 4), context);
                    }
                    if (!configFirst) {
                        check(maskedCalls, 0, 'no Code request before toggle arrives');
                        resolve();
                    }
                    const enabled = own === 'Y';
                    check(self[flagName](), flagName === 'isHthFeatureEnabled' ? (enabled ? 'Y' : 'N') : enabled,
                        file + ': independent of other portal, missing/failure disabled');
                    check(maskedCalls, enabled ? waiters.length : 0, file + ': gated Code request');
                }
            }
        }
    }
    // Hidden HTH controls must not remain callable while the current portal is disabled.
    const functions = file.includes('users-create')
        ? ['generateHthApiPasswordCode', 'regenerateHthApiPasswordCode', 'toggleHthApiPasswordCodeVisible',
            'createUser', 'reviewCreateUser', 'proceedWithReviewCreate']
        : file.includes('users-update') ? ['regenerateHthApiPasswordCode', 'toggleHthApiPasswordCodeVisible']
        : file.includes('user-read') ? ['toggleHthApiPasswordCodeVisible']
        : ['openHthLinkage', 'copyHthAccess', 'loadHthSummary'];
    for (const name of functions) {
        const self = {[flagName]: () => flagName === 'isHthFeatureEnabled' ? 'N' : false, userChannelType: () => 'HTH'};
        vm.runInNewContext(block(source, 'self.' + name + ' = function', '};'), {self});
        // Every other UI/HTTP dependency is deliberately absent: using it would throw.
        check(self[name](), undefined, file + ': disabled action ' + name);
    }
    if (file.includes('summary')) {
        const branchStart = source.indexOf('if (self.isHthMode()) {\n            rootParams.dashboard.headerName');
        const branch = source.slice(branchStart, source.indexOf('        self.showAccountAccess(true);', branchStart));
        for (const mode of ['HTH', 'BCO']) {
            for (const accessLevel of ['PARTY', 'USER']) {
                let hth = 0, bco = 0;
                const request = deferred();
                const self = {isHthMode: () => mode === 'HTH', userAccessPageTitle: () => '',
                    accessLevel: () => accessLevel, selectedUserId: () => 'fixture',
                    loadHthSummary: () => { hth++; }, getAllAccountsCount: () => { bco++; },
                    checkAccountAccessForUser: () => { bco++; }};
                vm.runInNewContext(branch, {self, hthToggleRequest: request,
                    rootParams: {dashboard: {headerName: () => {}}}});
                check(hth, 0, 'summary waits for portal toggle');
                check(bco, mode === 'BCO' ? 1 : 0, 'BCO load unchanged; HTH never falls back to BCO');
                request.resolve();
                check(hth, mode === 'HTH' ? 1 : 0, 'HTH summary scheduled after config');
            }
        }
    }
}
// Evaluate the real KO guard expressions for form sections, independently of Code state.
for (const file of ['partials/admin/create-panel.html', 'partials/admin/update-panel.html',
    'components/user-management/user-read/user-read.html', 'components/account-access-management/summary/summary.html']) {
    const conditions = [...read(file).matchAll(/<!-- ko if: ([^\n]+?) -->/g)]
        .map(m => m[1]).filter(s => s.includes('isUserChannelTypeEnabled()') || s.includes('isHthFeatureEnabled()'));
    assert(conditions.length, file + ': gated HTH region');
    for (const enabled of [false, true]) for (const codeId of [null, 'fixture']) {
        const self = {isUserChannelTypeEnabled: () => enabled, userChannelType: () => 'HTH',
            userExtensionData: () => ({userChannelType: 'HTH'}), hthApiPasswordCodeId: () => codeId,
            isHthMode: () => true, isHthFeatureEnabled: () => enabled ? 'Y' : 'N',
            showCopyOptions: () => true, setupNotCreated: () => true};
        for (const condition of conditions) {
            const visible = !!vm.runInNewContext(condition, {...self, $component: self});
            if (!enabled) check(visible, false, file + ': HTH region hidden');
        }
    }
}
// The edit-page toggle wraps only Channel Type, not document/contact/BCO role fields.
const editTemplate = read('partials/admin/update-panel.html');
const channelSection = editTemplate.match(/<!-- ko if: isUserChannelTypeEnabled -->[\s\S]*?<!-- \/ko -->/)[0];
check(channelSection.includes('userChannelTypeCorp'), true, 'Channel Type is gated');
for (const field of ['documentType', 'mobileNumber', 'emailId', 'usergrouplist', 'limitnRoles']) {
    check(channelSection.includes(field), false, 'BCO field remains outside HTH gate: ' + field);
}
// Exercise the real update-page model endpoint and both callback paths.
let editModel;
const calls = [];
vm.runInNewContext(read('components/user-management/users-update/model.js'), {
    define: (_, factory) => { editModel = factory({Deferred: deferred}, {getInstance: () => ({
        fetch: (options, params) => calls.push({options, params})
    })}); }
});
for (const key of [BM, CM]) {
    let enabled, failed = false;
    editModel.fetchCustomConfiguration(key).done(data => { enabled = data; }).fail(() => { failed = true; });
    const call = calls[calls.length - 1];
    check(call.options.url, 'customconfigurations/listwithsearchtext?summaryText={summaryText}', 'existing config endpoint');
    check(call.params.summaryText, key, 'selected portal key passed through');
    check(call.options.showMessage, false, 'config failure handled locally');
    if (key === BM) {
        call.options.success('fixture-config');
        check(enabled, 'fixture-config', 'config resolves');
    } else {
        call.options.error({});
        check(failed, true, 'config rejects');
    }
}
console.log('PASS portal toggles: ' + cases + ' checks; four shared pages, independent BM/CM flags, delayed config, Code callbacks/actions and BCO summary isolation.');
