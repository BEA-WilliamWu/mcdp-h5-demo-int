/* Execute the production audit detail view model without a server or a privileged session. */
const fs = require("fs"), vm = require("vm"), assert = require("assert"), path = require("path"),
    root = path.resolve(__dirname, "../../.."),
    source = fs.readFileSync(path.join(root, "consulting/channel/components/audit/audit-log-results/audit-log-results.js"), "utf8");
let labels;
vm.runInNewContext(fs.readFileSync(path.join(root, "consulting/channel/extensions/resources/nls/hth-audit.js"), "utf8"), {define: x => { labels = x.root; }});
let model, blob, clicked = false;
const document = {createElement: () => ({click: () => { clicked = true; }}), body: {appendChild: () => {}, removeChild: () => {}}};
vm.runInNewContext(source, {define: (_dependencies, factory) => {
    model = factory({JsonTreeDataSource: function (data) { this.data = data; }}, {utils: {extend: Object.assign}}, {header: {auditlogmaintenance: "Audit"}}, labels);
}, document, Blob, URL: {createObjectURL: data => { blob = data; return "blob:test"; }, revokeObjectURL: () => {}}, setTimeout: fn => fn()});
function render(details) {
    return new model({rootModel: {params: {dataToPass: {}, auditDTO: {id: "AUDIT-1", status: "SUCCESS", auditDetailsDTOList: details}}},
        dashboard: {headerName: () => {}, loadComponent: () => {}}, baseModel: {incrementIdCount: () => 1}});
}
async function main() {
    const changes = Array.from({length: 600}, (_, i) => ({change: i % 2 ? "ADD" : "REMOVE", maskedAccountNumber: "****1234", apiMasterId: "API" + i})),
        hth = render([{auditType: "1", request: {}, response: {}}, {auditType: "2", operationName: "HTH_ONBOARDING_791", request: {hthOnboarding: {
            schemaVersion: "1", operation: "RESET", actorUserId: "ACTOR", targetUserId: "\t=HYPERLINK(test)",
            businessOutcome: "SUCCESS", idempotentReplay: false, encryptedCredentials: "SYNTHETIC-SECRET", code: "SYNTHETIC-CODE", accessChanges: changes
        }}}]);
    assert(hth.hthAuditRows.some(row => row.value === "false"));
    assert(hth.hthAuditRows.some(row => row.value === "API599"));
    assert(!JSON.stringify(hth.hthAuditRows).includes("SYNTHETIC"));
    hth.downloadHthAudit();
    const csv = await blob.text();
    assert(clicked && csv.includes("API599") && csv.includes("'\t=HYPERLINK(test)"));
    assert(!csv.includes("SYNTHETIC"));
    assert.strictEqual(render([{auditType: "1", request: {bcoField: "retained"}}]).hthAuditRows.length, 0);
    console.log("PASS: production UI keeps BCO unchanged; HTH details/export retain false and 600 service changes, omit unknown secrets, escape spreadsheet formulas");
}
main().catch(error => { console.error(error); process.exitCode = 1; });
