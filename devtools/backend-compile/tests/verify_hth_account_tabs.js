/* Exercise production HTH pages with bundled Knockout; JET rendering/events are fixtures. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.resolve(__dirname, "../../..");
const ko = require(path.join(root, "consulting/channel/framework/js/libs/oraclejet/8.3.0/js/libs/knockout/knockout-3.5.0.debug.js"));
const components = path.join(root, "consulting/channel/extensions/components/account-access-management");
const nls = {
    navLabels: {CASA: "Current and Savings", TD: "Time Deposits"},
    pageTitle: {hthUserAccess: "User Account & Services Access"},
    headers: {hthApiMapping: "API Services"}, notes: {}, info: {}, fieldname: {}
};
class DataSource {
    constructor(data) { this.data = data; }
}
const oj = {
    ArrayTableDataSource: DataSource, JsonTreeDataSource: DataSource,
    FlattenedTreeDataSource: DataSource, FlattenedTreeTableDataSource: DataSource
};
const service = {
    int2extAccNo(number) { return number; },
    checkTransactionQualifiesOMB() { return Promise.resolve(); }
};
function constructor(component) {
    let result;
    vm.runInNewContext(fs.readFileSync(path.join(components, component, `${component}.js`), "utf8"), {
        define(_dependencies, factory) {
            result = component === "hth-account-linkage"
                ? factory(oj, ko, () => ({trigger() {}}), {}, nls, service)
                : factory(oj, ko, nls, service);
        }
    });
    return result;
}
function page(component, params) {
    const navigation = [], messages = [];
    const model = new (constructor(component))({
        rootModel: {params},
        dashboard: {
            headerName() {},
            loadComponent(name, data) { navigation.push({name, data}); },
            switchModule() {}
        },
        baseModel: {showMessages(_title, text) { messages.push(text); }}
    });
    return {model, navigation, messages};
}
function seed() {
    return [
        {accountNumber: "CSA-1", accountType: "CSA", selected: true, apiServices: [
            {apiCode: "BALANCE", selected: true}, {apiCode: "HISTORY", selected: false}
        ]},
        {accountNumber: "CSA-2", accountType: "CSA", selected: false, apiServices: [
            {apiCode: "BALANCE", selected: false}, {apiCode: "HISTORY", selected: false}
        ]},
        {accountNumber: "TD-1", accountType: "TD", selected: true, apiServices: [
            {apiCode: "TD_BALANCE", selected: true}
        ]}
    ];
}
function accountSelections(model) {
    return Array.from(model.accounts(), account => [account.accountNumber, account.selected()]);
}
function values(model) {
    return JSON.stringify(ko.toJS(model.allAccounts || model.accounts()));
}
function event(value, updatedFrom) { return {detail: {value, updatedFrom}}; }

async function main() {
    const accounts = seed();
    const context = {username: "USER@PARTY", setupStatus: "ACTIVE"};
    const linkage = page("hth-account-linkage", {
        hthLinkageContext: context, preloadedAccounts: accounts,
        originalAccounts: accounts, action: "EDIT"
    });
    const model = linkage.model;
    // JET fires external valueChanged when the one-way Map All binding refreshes.
    const selectionSubscription = model.activeAllSelection.subscribe(value => {
        model.toggleAll(event(value, "external"));
    });
    model.toggleAll(event(model.activeAllSelection(), "external"));
    assert.equal(model.accounts()[0].selected(), true,
        "Rendering a partially selected CASA tab must preserve its saved checked account");
    const original = values(model);
    for (let index = 0; index < 3; index += 1) {
        model.menuSelection("TRD");
        assert.equal(model.accountDataSource().data[0].accountNumber, "TD-1");
        model.menuSelection("CASA");
        assert.equal(values(model), original, "TD to CASA keeps checked and unchecked rows and service selections");
        assert.deepEqual(Array.from(model.accountDataSource().data, account => account.accountNumber), ["CSA-1", "CSA-2"]);
    }
    // Also retain edits made since loading, rather than restoring server defaults on each switch.
    model.accounts()[0].selected(false);
    model.accounts()[1].selected(true);
    const edited = values(model);
    model.menuSelection("TRD");
    model.menuSelection("CASA");
    assert.equal(values(model), edited, "Switching tabs preserves the latest manual selections");
    model.toggleAll(event(["ALL"], "internal"));
    assert.deepEqual(accountSelections(model), [["CSA-1", true], ["CSA-2", true], ["TD-1", true]]);
    model.toggleAll(event([], "internal"));
    assert.deepEqual(accountSelections(model), [["CSA-1", false], ["CSA-2", false], ["TD-1", true]],
        "User Map All/clear applies only to the currently displayed account type");
    model.toggleAll(event(["ALL"], "external"));
    assert.equal(model.accounts()[0].selected(), false, "Programmatic header changes cannot select accounts either");
    model.menuSelection("TRD");
    model.toggleAll(event([], "internal"));
    model.menuSelection("CASA");
    model.toggleAll(event(["ALL"], "internal"));
    model.accounts()[1].selected(false);
    model.menuSelection("TRD");
    model.menuSelection("CASA");
    assert.deepEqual(accountSelections(model), [["CSA-1", true], ["CSA-2", false], ["TD-1", false]]);
    const checked = values(model);
    model.next();
    const next = linkage.navigation.at(-1);
    assert.equal(next.name, "hth-api-service-mapping");
    const mapping = page(next.name, next.data);
    assert.equal(mapping.model.accounts().length, 1, "Mapping receives precisely the selected accounts");
    mapping.model.accounts()[0].apiServices[1].selected(true);
    const apiEdited = values(mapping.model);
    mapping.model.menuSelection("TRD");
    mapping.model.menuSelection("CASA");
    assert.equal(values(mapping.model), apiEdited, "API checkbox edits survive an empty TD tab and return to CASA");
    mapping.model.back();
    const back = page("hth-account-linkage", mapping.navigation.at(-1).data);
    assert.deepEqual(accountSelections(back.model), accountSelections(model), "Back from API mapping retains account choices");
    assert.equal(back.model.accounts()[0].apiServices[1].selected(), true, "Back retains API choices too");
    assert.equal(values(model), checked, "The second step does not alter the previous page's account model");
    const twoTabs = page("hth-api-service-mapping", {
        hthLinkageContext: {...context, initialAccountType: "TD"}, accounts: seed(), action: "EDIT"
    });
    assert.equal(twoTabs.model.activeAccountType(), "TD");
    twoTabs.model.filteredAccounts()[0].apiServices[0].selected(false);
    twoTabs.model.menuSelection("CASA");
    twoTabs.model.filteredAccounts()[0].apiServices[1].selected(true);
    const twoTabEdits = values(twoTabs.model);
    for (let index = 0; index < 3; index += 1) {
        twoTabs.model.menuSelection("TRD");
        assert.equal(twoTabs.model.filteredAccounts()[0].apiServices[0].selected(), false);
        twoTabs.model.menuSelection("CASA");
        assert.equal(twoTabs.model.filteredAccounts()[0].apiServices[1].selected(), true);
        assert.equal(values(twoTabs.model), twoTabEdits, "Both tabs retain independent API edits");
    }
    twoTabs.model.dispose();
    const view = page("hth-account-linkage", {
        hthLinkageContext: context, preloadedAccounts: seed(), action: "VIEW"
    });
    view.model.toggleAll(event([], "internal"));
    assert.equal(view.model.accounts()[0].selected(), true, "View mode remains read-only");
    view.model.dispose(); back.model.dispose(); mapping.model.dispose();
    selectionSubscription.dispose(); model.dispose();
    await Promise.resolve();
    console.log("PASS: real Knockout + production HTH pages: checked/unchecked CASA and TD rows, external JET events, manual edits, Map All/clear, API selections, Next/Back and read-only mode");
}
main().catch(error => { console.error(error); process.exitCode = 1; });
