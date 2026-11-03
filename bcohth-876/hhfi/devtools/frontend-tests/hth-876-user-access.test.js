/* Run production HTH view models with real Knockout; JET, OMB and HTTP are fixtures. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const ko = require(path.join(root, 'consulting/channel/framework/js/libs/oraclejet/8.3.0/js/libs/knockout/knockout-3.5.0.debug.js'));
const components = path.join(root, 'consulting/channel/extensions/components/account-access-management');
let nls;
vm.runInNewContext(fs.readFileSync(path.join(root, 'consulting/channel/extensions/resources/nls/access-management.js'), 'utf8'), {
    define(dependencies, factory) {
        const value = factory ? factory({}, {}, {}, {}) : dependencies;
        nls = value.root || value;
    }
});
class DataSource { constructor(data) { this.data = data; } }
const oj = {
    ArrayTableDataSource: DataSource, JsonTreeDataSource: DataSource,
    FlattenedTreeDataSource: DataSource, FlattenedTreeTableDataSource: DataSource
};
const chain = value => ({
    done(fn) { fn(value, 'success', {status: 202}); return this; },
    fail() { return this; }, always(fn) { fn(); return this; }
});
function page(component, params, snapshot) {
    const navigation = [], messages = [], events = [], sent = [], qualifications = [];
    const model = {
        save(payload, action) { sent.push({payload, action}); return chain({status: {}}); }
    };
    const service = {
        int2extAccNo: value => value,
        checkTransactionQualifiesOMB(task) { qualifications.push(task); return Promise.resolve(); }
    };
    const jquery = selector => ({
        hide() { return this; }, trigger(event) { events.push({selector, event}); return this; }
    });
    let Constructor;
    vm.runInNewContext(fs.readFileSync(path.join(components, component, `${component}.js`), 'utf8'), {
        define(_dependencies, factory) {
            Constructor = component === 'hth-account-linkage' ? factory(oj, ko, jquery, model, nls, service)
                : component === 'hth-api-service-mapping' ? factory(oj, ko, nls, service)
                    : factory(oj, ko, model, nls, service);
        }
    });
    const rootModel = {params};
    if (snapshot) rootModel.transactionDetails = {transactionSnapshot: snapshot};
    const view = new Constructor({
        rootModel,
        dashboard: {
            headerName() {}, switchModule() {},
            loadComponent(name, data) { navigation.push({name, data}); }
        },
        baseModel: {
            registerElement() {}, injectProps(target, key, value) { target[key] = value; },
            showMessages(_header, items) { messages.push(items); }
        }
    });
    return {view, navigation, messages, events, sent, qualifications};
}
const api = (code, selected = true) => ({apiCode: code, apiMasterId: `ID-${code}`, apiName: `Name ${code}`, selected});
const seed = () => [
    {accountNumber: '1001', accountType: 'CSA', selected: true,
        apiServices: [api('AUDIT_LOG'), api('BALANCE'), api('HISTORY', false)]},
    {accountNumber: '1002', accountType: 'CSA', selected: true,
        apiServices: [api('HISTORY'), api(' audit_log '), api('BALANCE', false)]},
    {accountNumber: '2001', accountType: 'TD', selected: true,
        apiServices: [api('TD_BALANCE'), api('AUDIT_LOG')]}
];
const codes = account => Array.from(account.apiServices, value => value.apiCode);
const noAudit = accounts => accounts.every(account => codes(account).every(code => code.trim().toUpperCase() !== 'AUDIT_LOG'));
const values = accounts => JSON.stringify(ko.toJS(accounts));

async function main() {
    for (const linkageType of ['RELATED', 'ASSOCIATED']) {
        const original = seed(), unchanged = values(original);
        const context = {partyId: 'PARTY', closeId: 'API@PARTY', accessPartyId: 'ACCESS', linkageType, setupStatus: 'ACTIVE'};
        const initial = page('hth-account-linkage', {
            hthLinkageContext: context, preloadedAccounts: original, originalAccounts: original, action: 'EDIT'
        });
        assert.ok(noAudit(initial.view.accounts()), 'Account navigation filters legacy service data');
        assert.ok(noAudit(initial.view.originalAccounts), 'Discard baseline uses the same filtering');
        initial.view.back();
        assert.equal(initial.navigation.at(-1).name, 'summary', 'Hiding Audit Log alone must not trigger discard modal');
        assert.equal(initial.events.length, 0);
        initial.view.next();
        const mapping = page('hth-api-service-mapping', initial.navigation.at(-1).data);
        assert.ok(noAudit(mapping.view.allAccounts));
        assert.deepEqual(codes(mapping.view.accounts()[0]), ['BALANCE', 'HISTORY']);
        mapping.view.applyFirstToAll();
        assert.equal(mapping.view.accounts()[1].apiServices.find(a => a.apiCode === 'BALANCE').selected(), true);
        assert.equal(mapping.view.accounts()[1].apiServices.find(a => a.apiCode === 'HISTORY').selected(), false);
        mapping.view.menuSelection('TRD');
        assert.equal(mapping.view.filteredAccounts()[0].accountNumber, '2001');
        assert.deepEqual(codes(mapping.view.filteredAccounts()[0]), ['TD_BALANCE']);
        mapping.view.menuSelection('CASA');
        await mapping.view.save();
        await Promise.resolve();
        assert.deepEqual(mapping.qualifications, ['UAT_N_HUA_EDT']);
        const review = page('review-hth-user-access', mapping.navigation.at(-1).data);
        assert.ok(noAudit(review.view.allAccounts));
        assert.ok(noAudit(review.view.payload().accounts));
        review.view.confirm();
        assert.equal(review.sent.length, 1);
        assert.ok(noAudit(review.sent[0].payload.accounts));
        review.view.back();
        const back = page('hth-api-service-mapping', review.navigation.at(-1).data);
        assert.ok(noAudit(back.view.allAccounts));
        back.view.back();
        const accountBack = page('hth-account-linkage', back.navigation.at(-1).data);
        assert.ok(noAudit(accountBack.view.accounts()));
        accountBack.view.cancel();
        assert.equal(accountBack.view.mode(), 'VIEW');
        assert.equal(accountBack.view.accounts()[1].apiServices.find(a => a.apiCode === 'HISTORY').selected(), true,
            'Cancel restores original business choices');
        assert.equal(values(original), unchanged, 'UI filtering must not mutate input snapshots');
        for (const model of [initial, mapping, review, back, accountBack]) model.view.dispose();
    }

    // Direct stale mapping data and observables still exclude the retired service.
    const observableAccounts = seed().map(account => ({...account, apiServices: ko.observableArray(account.apiServices), selected: ko.observable(true)}));
    const stale = page('hth-api-service-mapping', {accounts: observableAccounts, action: 'VIEW'});
    assert.ok(noAudit(stale.view.accounts()));
    const before = values(stale.view.accounts());
    stale.view.applyFirstToAll();
    stale.view.save();
    assert.equal(values(stale.view.accounts()), before, 'View stays read-only');
    assert.equal(stale.navigation.length, 0);
    stale.view.dispose();

    for (const action of ['CREATE', 'EDIT']) {
        const auditOnly = [{...seed()[0], apiServices: [api('AUDIT_LOG')]}];
        const blocked = page('hth-api-service-mapping', {accounts: auditOnly, action});
        blocked.view.save();
        assert.equal(blocked.messages.length, 1, 'Hidden service cannot satisfy one business API minimum');
        assert.equal(blocked.navigation.length, 0);
        assert.equal(blocked.qualifications.length, 0);
        blocked.view.dispose();
    }

    // Every deployed checker wrapper must remain read-only and preserve the stored object.
    const access = {partyId: 'PARTY', closeId: 'API@PARTY', accounts: seed()};
    const snapshots = [access, {hostToHostUserAccessDTO: access}, {transactionDTO: {data: {access}}}];
    for (const snapshot of snapshots) {
        const before = JSON.stringify(snapshot);
        const checker = page('review-hth-user-access', {mode: 'approval', taskCode: 'UAT_N_HUA_EDT'}, snapshot);
        assert.ok(noAudit(checker.view.accounts));
        assert.ok(noAudit(checker.view.payload().accounts));
        assert.equal(checker.view.accounts.length, 3);
        checker.view.confirm();
        assert.equal(checker.sent.length, 0, 'Checker does not submit maker requests');
        assert.equal(JSON.stringify(snapshot), before);
        checker.view.dispose();
    }
    const auditOnlyChecker = page('review-hth-user-access', {mode: 'approval'}, {
        ...access, accounts: [{...seed()[0], apiServices: [api('AUDIT_LOG')]}]
    });
    assert.equal(auditOnlyChecker.view.accounts[0].apiServices.length, 0, 'Legacy Audit-only pending remains viewable');
    auditOnlyChecker.view.dispose();
    const deletion = page('review-hth-user-access', {access, action: 'DELETE'});
    deletion.view.confirm();
    assert.equal(deletion.sent[0].action, 'DELETE');
    assert.ok(noAudit(deletion.sent[0].payload.accounts));
    deletion.view.dispose();
    console.log('PASS: 876 production UI: legacy data/snapshots, RELATED/ASSOCIATED, CASA/TD, copy, review/payload, min selection, read-only, Next/Back/Cancel and discard baseline');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
