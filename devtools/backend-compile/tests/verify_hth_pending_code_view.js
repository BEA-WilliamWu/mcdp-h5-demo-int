/* Run the entire production user-read constructor; API and Knockout are fixtures. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const source = fs.readFileSync(path.resolve(__dirname,
    "../../../consulting/channel/extensions/components/user-management/user-read/user-read.js"), "utf8");
function observable(value) {
    const result = function (next) { if (arguments.length) value = next; return value; };
    result.push = (...items) => value.push(...items);
    return result;
}
function page(channel, codeId, mode = "approval") {
    const calls = [], callbacks = [];
    let finishLiveRead;
    const user = {username: "USER@PARTY", partyId: {value: "PARTY"},
        applicationRoles: null, userGroupDTOs: null, userType: {enterpriseRoleId: "corporateuser", enterpriseRoleName: "Test"},
        address: {country: "HK"}, userGroups: [], userAccessPointRelationshipList: [],
        accessibleEntity: [], accessibleEntities: [], userPartyRelationshipDTOs: [], limitPackages: []};
    const snapshot = {userChannelType: channel, userID: user.username, cdcNo: "PARTY",
        userDTO: user, hthApiPasswordCodeId: codeId};
    const ko = {observable, observableArray: (items = []) => observable(items),
        pureComputed: fn => fn, utils: {extend: Object.assign, arrayForEach: (items, fn) => items.forEach(fn)}};
    const model = {
        fetchAccess() { return Promise.resolve({accessPointListDTO: []}); },
        listAccessPointGroup() { return Promise.resolve({accessPointGroupListDTO: []}); },
        readUser(id) { calls.push(["readUser", id]); return new Promise(resolve => { finishLiveRead = resolve; }); },
        getEnterpriseRoles() { return Promise.resolve({enterpriseRoleDTOs: []}); },
        readUserExtension(id) {
            calls.push(["readUserExtension", id]);
            return Promise.resolve({userExtensionDataDTO: {userChannelType: channel, hthApiPasswordCodeId: "OLD-ACTIVE"}});
        },
        fetchChildRole() { return {done(fn) { fn({applicationRoleDTOs: []}); return this; }}; },
        getEnbleItokenManagement() { return Promise.resolve({}); },
        getHthApiPasswordCodeMasked(...args) {
            calls.push(["masked", ...args]);
            return {done(fn) { callbacks.push(fn); return this; }};
        },
        revealHthApiPasswordCode(id) {
            calls.push(["reveal", id]);
            return {done(fn) { callbacks.push(fn); return this; }, fail() { return this; }};
        }
    };
    let Constructor;
    vm.runInNewContext(source, {define(_deps, factory) {
        Constructor = factory(ko, model, () => {}, {headers: {}, fieldname: {}, hthCodeStatuses: {}});
    }});
    const params = {username: user.username, countries: []};
    if (mode !== "ordinary") Object.assign(params, {data: snapshot, mode, taskCode: "MT_N_UUS"});
    const view = new Constructor({rootModel: {params}, baseModel: {registerComponent() {}, registerElement() {}},
        dashboard: {appData: {segment: "CORPORATE"}, headerName() {},
            userData: {userProfile: {partyId: {value: "PARTY"}}}}});
    return {view, calls, callbacks, snapshot, user, finishLiveRead};
}
async function main() {
    const first = page("HTH", "PENDING-ONE"), second = page("HTH", "PENDING-TWO");
    await Promise.resolve();
    for (const [opened, id] of [[first, "PENDING-ONE"], [second, "PENDING-TWO"]]) {
        assert.deepEqual(opened.calls.filter(call => call[0] === "masked"),
            [["masked", "PARTY", "USER@PARTY", id]], "Opening each pending order reads its own snapshot Code");
        assert.equal(opened.calls.filter(call => call[0] === "readUser").length, 1,
            "Existing profile data fetch remains unchanged");
        assert.equal(opened.view.userExtensionData(), opened.snapshot);
        assert.equal(opened.view.userFullData(), opened.user);
        assert.equal(opened.view.hthApiPasswordCodeCanReveal(), false, "Reveal waits for authorized metadata");
    }
    second.callbacks.shift()({status: {result: "SUCCESSFUL"}, codeId: "PENDING-TWO",
        codeStatus: "PENDING", canReveal: true, maskedCode: "******"});
    first.callbacks.shift()({status: {result: "SUCCESSFUL"}, codeId: "PENDING-ONE",
        codeStatus: "INVALID", canReveal: true, maskedCode: "******"});
    assert.equal(first.view.hthApiPasswordCodeId(), "PENDING-ONE");
    assert.equal(second.view.hthApiPasswordCodeId(), "PENDING-TWO");
    assert.equal(first.view.hthApiPasswordCodeStatus(), "INVALID");
    assert.equal(second.view.hthApiPasswordCodeStatus(), "PENDING");
    assert.equal(first.view.hthApiPasswordCode(), "******");
    assert.equal(first.view.hthApiPasswordCodeVisible(), false);
    first.finishLiveRead({userDTO: first.user});
    second.finishLiveRead({userDTO: second.user});
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(first.calls.filter(call => call[0] === "masked").length, 1, "Late live load must not send a second masked query");
    assert.equal(first.view.hthApiPasswordCodeId(), "PENDING-ONE", "Live old Code pointer must not overwrite first order's Code");
    assert.equal(second.view.hthApiPasswordCodeId(), "PENDING-TWO", "Live old Code pointer must not overwrite second order's Code");
    assert.equal(first.snapshot.hthApiPasswordCodeId, "PENDING-ONE", "Snapshot stays unchanged");
    first.view.toggleHthApiPasswordCodeVisible();
    assert.deepEqual(first.calls.at(-1), ["reveal", "PENDING-ONE"], "Eye uses the same historical Code ID");
    const noCode = page("HTH", null);
    assert.equal(noCode.calls.filter(call => call[0] === "masked").length, 0,
        "An order without a generated Code must not fall back to another order's Code");
    for (const control of [page("HTH", null, "ordinary"), page("BCO", null)]) {
        assert.equal(control.calls.filter(call => call[0] === "readUser").length, 1,
            "Ordinary HTH and existing BCO page retain their current fetch flow");
    }
    console.log("PASS: complete production user-read constructor: two pending snapshot IDs, masked metadata, historical eye, no stale overwrite, no-Code orders and unchanged ordinary/BCO fetch flow");
}
main().catch(error => { console.error(error); process.exitCode = 1; });
