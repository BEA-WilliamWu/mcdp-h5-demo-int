/* Exercise the production BCO detail view and activity filter with synthetic audit data. */
const fs = require("fs"), vm = require("vm"), assert = require("assert"), path = require("path"),
    root = path.resolve(__dirname, "../../.."),
    read = file => fs.readFileSync(path.join(root, "consulting/channel", file), "utf8"),
    detailSource = read("components/audit/audit-log-results/audit-log-results.js"),
    searchSource = read("extensions/components/audit/audit-log/audit-log.js"),
    resultsSource = read("extensions/components/audit/audit-log-search-results/audit-log-search-results.js");
let model, lastNavigation, dependencies;
const sandbox = {define: (deps, factory) => {
    dependencies = deps;
    model = factory({JsonTreeDataSource: function (data) { this.data = data; }}, {utils: {extend: Object.assign}}, {header: {auditlogmaintenance: "Audit"}});
}};
vm.createContext(sandbox);
vm.runInContext(detailSource, sandbox);
assert(!dependencies.some(dep => dep.includes("hth-audit")), "No missing HTH NLS dependency");
function render(request, response) {
    // Use same-realm objects: the actual view walks Array/Object with instanceof.
    const params = vm.runInContext("(" + JSON.stringify({rootModel: {params: {dataToPass: {selectedActivity: "MT_N_CUS"}, auditDTO: {
        resolvedRequestUrl: "/test", auditDetailsDTOList: [{auditType: "1", request, response}]
    }}}}) + ")", sandbox);
    let id = 0;
    params.dashboard = {headerName: () => {}, loadComponent: (...args) => {lastNavigation = args;}};
    params.baseModel = {incrementIdCount: () => ++id};
    return new model(params);
}
const changes = Array.from({length: 1000}, (_, i) => ({change: i % 2 ? "ADD" : "REMOVE", maskedAccountNumber: "****1234", apiMasterId: "API" + i})),
    hth = render({hthOnboarding: {operation: "ACCESS_EDIT", businessOutcome: "SUCCESS", idempotentReplay: false, accessChanges: changes}}, {attemptCount: 0});
assert(!JSON.stringify(hth.requestData.data).includes('"value":false'), 'Retain BCO falsy-value display behavior');
assert(JSON.stringify(hth.requestData.data).includes('"value":"API999"'));
assert(!JSON.stringify(hth.responseData.data).includes('"value":0'), 'Retain BCO zero-value display behavior');
const bco = render({bcoField: "retained", account: {displayValue: "****1234"}}, {result: "SUCCESSFUL"});
assert(JSON.stringify(bco.requestData.data).includes('"value":"retained"'));
assert(JSON.stringify(bco.responseData.data).includes('"value":"SUCCESSFUL"'));
bco.back();
assert.strictEqual(lastNavigation[0], "audit-log");
assert.strictEqual(lastNavigation[1].selectedActivity, "MT_N_CUS");

// Execute the real fetchActivities callback, including the existing BM/CM branching.
const start = searchSource.indexOf("AuditLogModel.fetchActivities().done("),
    end = searchSource.indexOf('if (rootParams.dashboard.userData.userProfile.roles.indexOf', start),
    activities = [
        {id: "MT_N_CUS", type: "NONFINANCIAL_TRANSACTION"},
        {id: "UAT_N_HAP_GEN", type: "MAINTENANCE"},
        {id: "UAT_N_HAP_REGEN", type: "MAINTENANCE"},
        ...["UAT_N_HUA_NEW", "UAT_N_HUA_EDT", "UAT_N_HUA_DEL", "BM_ONLY"].map(id => ({id, type: "ADMINISTRATION"})),
        {id: "NO_TYPE"}
    ];
function filter(isBM, roles) {
    const list = []; let loaded = false;
    vm.runInNewContext(searchSource.slice(start, end), {
        AuditLogModel: {fetchActivities: () => ({done: fn => fn({taskList: activities})})},
        ko: {utils: {arrayForEach: (items, fn) => items.forEach(fn)}},
        self: {isBM: () => isBM, activityEnum: list, activityEnumLoaded: value => {loaded = value;}},
        rootParams: {dashboard: {userData: {userProfile: {roles}}}}
    });
    assert(loaded);
    return list.map(item => item.id);
}
assert.deepStrictEqual(filter(false, ["corporateuser"]), ["MT_N_CUS", "UAT_N_HAP_GEN", "UAT_N_HAP_REGEN", "UAT_N_HUA_NEW", "UAT_N_HUA_EDT", "UAT_N_HUA_DEL"]);
assert.deepStrictEqual(filter(true, ["Administrator"]), activities.filter(item => item.type).map(item => item.id));
assert.deepStrictEqual(filter(false, ["administrator"]), []);
assert.deepStrictEqual(filter(true, ["corporateuser"]), []);

// The existing (unbound) detail handler still uses the detail API and keeps filters.
const resultHtml = read("extensions/components/audit/audit-log-search-results/audit-log-search-results.html");
// Preserve the original BCO Event cell; the existing detail handler remains available.
assert(resultHtml.includes('<span data-bind="text:activity"></span>'));
assert(!/click:\s*\$component\.openJSON/.test(resultHtml));
const openStart = resultsSource.indexOf("self.openJSON = function (data)"),
    openEnd = resultsSource.indexOf("self.paginationDataSource", openStart),
    self = {};
let requestedId;
vm.runInNewContext(resultsSource.slice(openStart, openEnd), {
    self, rootParams: {rootModel: {selectedActivity: "UAT_N_HUA_EDT"}, dashboard: {loadComponent: (...args) => {lastNavigation = args;}}},
    AuditLogSearchResultsModel: {searchAuditItem: id => {requestedId = id; return {done: fn => fn({auditDTO: {id}})};}}
});
self.openJSON({id: "AUDIT-791"});
assert.strictEqual(requestedId, "AUDIT-791");
assert.strictEqual(lastNavigation[0], "audit-log-results");
assert.strictEqual(lastNavigation[1].dataToPass.selectedActivity, "UAT_N_HUA_EDT");
console.log("PASS: production BCO JSON detail/back navigation; legacy falsy-value handling and 1000 HTH changes; CM HUA-only exception, existing BM filter, plain Event cell");

// Both dropdown and Event labels resolve the new IDs in every supported language.
for (const suffix of ["", "_zh-hans-cn", "_zh-hant"]) {
    const properties = kind => Object.fromEntries(fs.readFileSync(path.join(root, "consulting/config/resources/nls", kind + suffix + ".properties"), "utf8")
        .split(/\r?\n/).filter(line => line && !line.startsWith("#") && line.includes("="))
        .map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
    const high = properties("HighRiskTask"), common = properties("CommonTask"),
        entries = Object.entries(high).map(([taskCode, taskName]) => ({taskCode, taskName}));
    for (const base of ["MT_N_CUS", "MT_N_UUS", "MT_N_RUS"]) {
        assert.strictEqual(high[base + "_HTH"], "HTH " + common[base]);
        assert.strictEqual(common[base + "_HTH"], high[base + "_HTH"]);
        for (const id of (high[base] ? [base, base + "_HTH"] : [base + "_HTH"])) {
            const codes = [], observable = () => codes; observable.removeAll = () => {codes.length = 0;};
            let selected;
            vm.runInNewContext(searchSource.slice(searchSource.indexOf("//TaskCode Implementation Starts"), searchSource.indexOf("//TaskCode Implementation Ends")), {
                self: {taskCode: value => {selected = value;}, codesArray: observable,
                    highRiskTransaction: () => [high[id]], transactionEnumCode: () => entries,
                    selectedActivity: () => [], activityEnum: () => []}
            });
            assert.strictEqual(selected, id, "Original and HTH dropdown options select disjoint task IDs");
        }
    }
}
console.log("PASS: production High Risk selector separates BCO/HTH Create/Edit; dropdown and Event labels agree in three languages");
