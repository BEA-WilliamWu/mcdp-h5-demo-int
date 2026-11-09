/* Run production view models with real Knockout; no backend requests or mutations. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const channel = path.join(root, 'consulting/channel');
const koPath = path.join(channel, 'framework/js/libs/oraclejet/8.3.0/js/libs/knockout/knockout-3.5.0.debug.js');
const ko = require(koPath);
const source = file => fs.readFileSync(path.join(channel, 'extensions', file), 'utf8');
class DataSource { constructor(data) { this.data = data; } }
const oj = {ArrayTableDataSource: DataSource, PagingTableDataSource: DataSource};
const chain = value => ({done(fn) { fn(value); return this; }, fail() { return this; }});
const nls = {header: {}, headers: {}, fieldname: {}, info: {}, hthCodeStatuses: {}};

function constructor(file, dependencies = {}) {
    let View;
    vm.runInNewContext(source(file), {define(names, factory) {
        View = factory(...names.map(name => dependencies[name]
            || ({knockout: ko, 'ojs/ojcore': oj})[name]
            || (name.startsWith('ojL10n!') ? nls : {})));
    }});
    return View;
}
function context(segment, rootModel = {}) {
    const navigation = [], registrations = [];
    return {
        rootModel, navigation, registrations,
        dashboard: {appData: {segment}, headerName() {}, loadComponent(name, params) { navigation.push({name, params}); }},
        baseModel: {registerElement(name) { registrations.push(name); }, registerComponent() {},
            showMessages() {}, injectProps(target, key, value) { target[key] = value; }}
    };
}

async function lists() {
    const hth = {userChannelType: 'HTH', closeId: 'API@PARTY', userData: {username: 'API', firstName: 'API', lockStatus: 'UNLOCK'}},
        bco = {userChannelType: 'BCO', userData: {username: 'BCO', firstName: 'BCO', lockStatus: 'UNLOCK'}},
        legacy = {userData: {username: 'LEGACY', firstName: 'Legacy', lockStatus: 'UNLOCK'}};
    const rows = [hth, bco, legacy];
    const View = constructor('components/user-management/user-search-list/user-search-list.js', {
        'framework/js/configurations/config': {authentication: {type: 'IDCSAuthenticator'}},
        platform: {getInstance: () => Promise.resolve(() => true)}
    });
    for (const segment of ['ADMIN', 'CORPADMIN']) {
        for (const dataSource of [rows, [hth], []]) {
            const ctx = context(segment, {params: {}, cancelButtonFlag: ko.observable(true)});
            ctx.dataSource = dataSource;
            const view = new View(ctx);
            await Promise.resolve();
            assert.deepEqual(Array.from(view.userList(), u => u.username),
                dataSource.filter(u => segment === 'CORPADMIN' || u !== hth).map(u => u.userData.username));
            assert.ok(view.paginationDataSource, 'Empty/filtered-empty searches still have a table data source');
            if (dataSource.length && segment === 'CORPADMIN') {
                assert.ok(view.dynamicCols().some(c => c.field === 'userChannelType'));
            } else if (segment === 'ADMIN') {
                assert.ok(!view.dynamicCols().some(c => c.field === 'userChannelType'));
                view.showUserDetails({username: 'API', userChannelType: 'HTH'});
                view.edit({username: 'API', userChannelType: 'HTH'});
                view.editChannelAccess({username: 'API', userChannelType: 'HTH'});
                assert.equal(ctx.navigation.length, 0, 'Stale HTH row handlers do not navigate in BM');
            }
        }
    }
    assert.deepEqual(rows, [hth, bco, legacy], 'Filtering does not remove users from the shared response array');

    // The account list receives HTH channel metadata in its extension dictionary.
    const users = [
        {username: 'API', firstName: 'API', dictionaryArray: [{nameValuePairDTOArray: [
            {name: 'userChannelType', value: 'HTH'}, {name: 'hthAccessSetupDone', value: 'true'},
            {name: 'closeId', value: 'API@PARTY'}]}]},
        {username: 'BCO', firstName: 'BCO', accountAccessSetupDone: false},
        {username: 'READY', firstName: 'Ready', accountAccessSetupDone: true}
    ];
    const UserList = constructor('components/common/user-list-details/user-list-details.js', {
        './model': {fetchAssociatedUserForParty: () => chain({userDTOList: users})},
        'extensions/generic/service-extension': {checkTransactionQualifiesOMB: () => Promise.resolve()}
    });
    for (const allowHthUsers of [false, true, undefined]) {
        const ctx = context('ADMIN', {partyID: ko.observable('PARTY'), selectedUserData: ko.observable()});
        if (allowHthUsers !== undefined) ctx.allowHthUsers = allowHthUsers;
        const view = new UserList(ctx);
        assert.equal(view.userList().some(u => u.userChannelType === 'HTH'), allowHthUsers !== false,
            'Only an explicit false changes the shared component behavior');
        assert.equal(view.userList().find(u => u.username === 'BCO').accountAccessSetupDone, false);
        view.showUserAccountAccess({username: 'API', userChannelType: 'HTH'});
        assert.equal(!!view.selectedUserData(), allowHthUsers !== false);
        view.selectedUserData(undefined);
        view.showUserAccountAccess({username: 'BCO', userChannelType: 'BCO', accountAccessSetupDone: false});
        await Promise.resolve();
        assert.equal(view.selectedUserData().username, 'BCO', 'BCO retains its existing OMB navigation');
    }
    const Empty = constructor('components/common/user-list-details/user-list-details.js', {
        './model': {fetchAssociatedUserForParty: () => chain({userDTOList: [users[0]]})}
    });
    const empty = new Empty({...context('ADMIN', {partyID: ko.observable('PARTY')}), allowHthUsers: false});
    assert.equal(empty.userList().length, 0);
    assert.equal(empty.userListDetailsDataSource.data.length, 0);
}

function deniedEntries() {
    const cases = [
        ['user-management/users-update/users-update', {params: {userExtensionData: {userChannelType: 'HTH'}}}],
        ['user-management/users-update/users-update', {params: {transactionId: 'PENDING', data: {userChannelType: 'HTH'}}}],
        ['user-management/review-user-create/review-user-create', {params: {data: {userChannelType: 'HTH'}}}],
        ['user-management/review-user-update/review-user-update', {params: {data: {userChannelType: 'HTH'}}}],
        ['user-management/review-user-update/review-user-update', {userExtensionData: ko.observable(), params: {data: {userChannelType: 'HTH'}}}],
        ['user-management/review-user-status/review-user-status', {params: {userExtensionData: {userChannelType: 'HTH'}}}],
        ['user-management/review-user-channel-access/review-user-channel-access', {params: {userExtensionData: {userChannelType: 'HTH'}}}],
        ['user-management/user-read/user-read', {params: {data: {userChannelType: 'HTH', hthApiPasswordCodeId: 'PENDING-ID'}}}],
        ['account-access-management/summary/summary', {params: {channelMode: 'HTH'}}],
        ['account-access-management/summary/summary', {params: {selectedUserChannelType: 'HTH'}}],
        ...['hth-account-linkage', 'hth-api-service-mapping', 'review-hth-user-access']
            .map(c => [`account-access-management/${c}/${c}`, {params: {mode: 'approval'}}])
    ];
    for (const segment of ['ADMIN', 'CORP', undefined]) {
        for (const [file, model] of cases) {
            const ctx = context(segment, model);
            const view = new (constructor(`components/${file}.js`))(ctx);
            assert.equal(ko.unwrap(view.hthPageAllowed), false, `${file}: only CM allows HTH`);
            assert.equal(ctx.navigation.length, 1);
            assert.equal(ctx.navigation[0].name, 'access-denied');
            assert.deepEqual(ctx.registrations, ['access-denied']);
        }
    }
}

function approvalLookup() {
    // Approval snapshots may only contain username; decide after the target's live extension arrives.
    for (const name of ['review-user-status', 'review-user-channel-access']) {
        for (const segment of ['ADMIN', 'CORPADMIN']) {
            for (const userChannelType of ['HTH', 'BCO']) {
                let receive;
                const model = {readUserExtension() { return {done(fn) { receive = fn; }}; }};
                const ctx = context(segment, {params: {mode: 'approval', taskCode: 'UM_N_ULS_L',
                    data: {username: 'USER', lockStatus: 'LOCK', lockReason: 'test', deleteStatus: true}}});
                const view = new (constructor(`components/user-management/${name}/${name}.js`, {'./model': model}))(ctx);
                assert.equal(ctx.navigation.length, 0);
                receive({userExtensionDataDTO: {userChannelType}});
                const denied = segment === 'ADMIN' && userChannelType === 'HTH';
                assert.equal(view.hthPageAllowed(), !denied);
                assert.equal(view.dataLoaded(), !denied);
                assert.equal(ctx.navigation.length, denied ? 1 : 0);
                if (denied) view.confirm(); // No write model is provided: an accidental submit fails this test.
            }
        }
    }
}

async function browserBindings() {
    const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
    const browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_PATH});
    try {
        const page = await browser.newPage();
        await page.setContent('<main id="test"></main>');
        await page.addScriptTag({path: koPath});
        const guarded = [
            'user-management/user-read/user-read', 'user-management/users-update/users-update',
            'user-management/review-user-create/review-user-create', 'user-management/review-user-update/review-user-update',
            'user-management/review-user-status/review-user-status', 'user-management/review-user-channel-access/review-user-channel-access',
            'account-access-management/summary/summary', 'account-access-management/hth-account-linkage/hth-account-linkage',
            'account-access-management/hth-api-service-mapping/hth-api-service-mapping',
            'account-access-management/review-hth-user-access/review-hth-user-access'
        ].map(file => source(`components/${file}.html`));
        await page.evaluate(htmls => {
            const el = document.querySelector('#test');
            for (const html of htmls) {
                el.innerHTML = html;
                ko.applyBindings({hthPageAllowed: false}, el);
                if (el.textContent.trim() || el.querySelector('button, oj-button, oj-input-text, oj-table')) {
                    throw new Error('Blocked page still renders its contents');
                }
                ko.cleanNode(el);
            }
        }, guarded);

        // Exercise the actual create-channel observable subscription and the actual radio markup.
        const js = source('components/user-management/users-create/users-create.js');
        const init = js.slice(js.indexOf('        self.hthAvailable ='), js.indexOf('        /**\r\n         * Generate HTH'));
        const html = source('partials/admin/create-panel.html');
        const start = html.indexOf('<!-- ko if: hthAvailable -->');
        const radio = html.slice(start, html.indexOf('<!-- /ko -->', start) + '<!-- /ko -->'.length);
        await page.evaluate(({init, radio}) => {
            const el = document.querySelector('#test');
            for (const segment of ['ADMIN', 'CORPADMIN']) {
                const self = {selectedChildRole: ko.observableArray(['VIEWER']), childRoleEnums: ko.observableArray([]),
                    nls: {fieldname: {userChannelType: 'User Channel Type'}, required: {userChannelType: 'Required'}}};
                new Function('self', 'ko', 'rootParams', init)(self, ko, {dashboard: {appData: {segment}}});
                self.$component = self;
                el.innerHTML = radio;
                ko.applyBindings(self, el);
                if (!!el.querySelector('oj-radioset') !== (segment === 'CORPADMIN')) throw new Error('Channel visibility mismatch');
                self.userChannelType('HTH');
                if (self.userChannelType() !== (segment === 'CORPADMIN' ? 'HTH' : 'BCO')) throw new Error('BM channel escaped its BCO default');
                ko.cleanNode(el);
            }
        }, {init, radio});
    } finally { await browser.close(); }
}

(async () => {
    await lists();
    deniedEntries();
    approvalLookup();
    await browserBindings();
    console.log('PASS: CM/BM user filtering, shared-list opt-in, empty results, BCO navigation, direct/pending entry guards and browser bindings');
})().catch(error => {console.error(error); process.exitCode = 1;});
