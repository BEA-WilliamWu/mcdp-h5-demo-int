/* Execute the production AMD view models; UI and network calls stay local. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const componentRoot = path.join(root, 'consulting/channel/extensions/components/host-to-host');
const nlsFile = path.join(root, 'consulting/channel/extensions/resources/nls/host-to-host-management.js');
let nls;
vm.runInNewContext(fs.readFileSync(nlsFile, 'utf8'), {define: value => { nls = value.root || value; }});

const observable = initial => {
    let current = initial;
    return function(value) {
        if (arguments.length) current = value;
        return current;
    };
};
const ko = {
    observable,
    observableArray: observable,
    pureComputed: definition => typeof definition === 'function' ? definition : function(value) {
        return arguments.length ? definition.write(value) : definition.read();
    }
};
const api = (code, selected = true) => ({apiCode: code, apiName: `Name ${code}`, selected});
const record = apis => ({
    partyId: 'TEST-PARTY', accountNumber: 'TEST-ACCOUNT', hostToHostApiChannelEnabled: true,
    correlateUamClientId: 'TEST-CLIENT', chargeAccount: 'TEST-CHARGE', apiAuthorizations: apis
});
const chain = value => ({
    done(fn) { fn(value, 'success', {}); return this; },
    fail() { return this; },
    always(fn) { fn(); return this; }
});
function create(component, data, options = {}) {
    const navigations = [], sent = [], errors = [];
    const model = {
        getEligibleChargeAccounts: () => chain([{accountNumber: 'TEST-CHARGE'}]),
        validateUamClientId: () => chain(options.validationResponse || record([])),
        submit: value => { sent.push(value); return chain({status: {}}); },
        edit: value => { sent.push(value); return chain({status: {}}); },
        disable: value => { sent.push(value); return chain({status: {}}); }
    };
    let ViewModel;
    vm.runInNewContext(fs.readFileSync(path.join(componentRoot, component, `${component}.js`), 'utf8'), {
        define: (_dependencies, factory) => { ViewModel = factory(ko, model, nls); },
        document: {getElementById: () => ({})}
    });
    const rootModel = {params: {data, mode: options.approval ? 'approval' : undefined}};
    if (options.snapshot) rootModel.transactionDetails = () => ({transactionSnapshot: options.snapshot});
    const view = new ViewModel({
        rootModel,
        baseModel: {
            registerElement() {},
            showMessages: (_unused, messages) => errors.push(messages),
            showComponentValidationErrors: () => true
        },
        dashboard: {
            headerName() {}, switchModule() {},
            loadComponent: (name, params) => navigations.push({name, params})
        }
    });
    return {view, navigations, sent, errors};
}
const codes = values => Array.from(values, value => typeof value === 'string' ? value : value.apiCode);

// View, edit, select-all, back-state restoration, and submission use the same filtered catalogue.
for (const useFlowState of [false, true]) {
    const original = record([api('ACCOUNT_ACTIVITY'), api('AUDIT_LOG'), api('BALANCE_INQUIRY', false)]);
    const data = {record: original, flowState: {editMode: true}};
    if (useFlowState) {
        data.flowState.apiAuthorizations = original.apiAuthorizations;
        data.flowState.selectedApiCodes = ['AUDIT_LOG', 'ACCOUNT_ACTIVITY'];
    }
    const {view, navigations} = create('host-to-host-details', data);
    assert.deepEqual(codes(view.availableApis()), ['ACCOUNT_ACTIVITY', 'BALANCE_INQUIRY']);
    assert.deepEqual(codes(view.selectedApiCodes()), ['ACCOUNT_ACTIVITY']);
    view.allApisSelected(true);
    assert.deepEqual(codes(view.selectedApiCodes()), ['ACCOUNT_ACTIVITY', 'BALANCE_INQUIRY']);
    view.onSubmit();
    const next = navigations.at(-1).params.data;
    assert.equal(navigations.at(-1).name, 'host-to-host-review');
    assert.deepEqual(codes(next.record.apiAuthorizations), ['ACCOUNT_ACTIVITY', 'BALANCE_INQUIRY']);
    assert.deepEqual(codes(next.flowState.selectedApiCodes), ['ACCOUNT_ACTIVITY', 'BALANCE_INQUIRY']);
    assert.equal(original.apiAuthorizations.length, 3, 'Do not rewrite the original snapshot');
    view.onApiChannelChange({detail: {value: false}});
    assert.equal(view.isSubmitDisabled(), false, 'Disable company still works');
    view.onApiChannelChange({detail: {value: true}});
    assert.deepEqual(codes(view.availableApis()), ['ACCOUNT_ACTIVITY', 'BALANCE_INQUIRY']);
}

// A hidden/forged Audit Log selection cannot satisfy the existing business API minimum.
{
    const {view} = create('host-to-host-details', {
        record: record([api('AUDIT_LOG'), api('BALANCE_INQUIRY', false)]), flowState: {editMode: true}
    });
    assert.equal(view.isSubmitDisabled(), true);
    view.selectedApiCodes(['AUDIT_LOG']);
    assert.equal(view.isSubmitDisabled(), true);
}

// New-enable validation can still return an old catalogue during a rolling deployment.
{
    const {view} = create('host-to-host-details', {
        record: record([]), flowState: {editMode: true, showAuthorizationSettings: false}
    }, {validationResponse: record([api(' audit_log '), api('ACCOUNT_ACTIVITY')])});
    view.onNext();
    assert.deepEqual(codes(view.availableApis()), ['ACCOUNT_ACTIVITY']);
    assert.deepEqual(codes(view.selectedApiCodes()), ['ACCOUNT_ACTIVITY']);
}

// Old maker and checker snapshots remain readable, but cannot display/re-submit Audit Log.
for (const approval of [false, true]) {
    const original = record([api('AUDIT_LOG'), api('ACCOUNT_ACTIVITY')]);
    const data = approval ? {} : {record: original, action: 'edit'};
    const {view, sent} = create('host-to-host-review', data, {approval, snapshot: approval && original});
    assert.deepEqual(codes(view.selectedApis()), ['ACCOUNT_ACTIVITY']);
    view.onConfirm();
    assert.equal(sent.length, approval ? 0 : 1);
    if (!approval) assert.deepEqual(codes(sent[0].apiAuthorizations), ['ACCOUNT_ACTIVITY']);
    assert.equal(original.apiAuthorizations.length, 2);
}
console.log('PASS: 881 BM view/edit/enable/select-all/submit and legacy checker snapshots');
