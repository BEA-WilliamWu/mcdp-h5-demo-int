/* Execute the production HTH UI blocks with Knockout/API fixtures; no browser deployment is implied. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.resolve(__dirname, "../../..");
const ui = path.join(root, "consulting/channel/extensions/components/user-management");
const update = fs.readFileSync(path.join(ui, "users-update/users-update.js"), "utf8").replace(/\r\n/g, "\n");
const read = fs.readFileSync(path.join(ui, "user-read/user-read.js"), "utf8").replace(/\r\n/g, "\n");
const model = fs.readFileSync(path.join(ui, "user-read/model.js"), "utf8").replace(/\r\n/g, "\n");
const observable = function (value) {
    return function (next) { if (arguments.length) value = next; return value; };
};
function block(source, start, end) {
    const offset = source.indexOf(start);
    assert(offset !== -1, start);
    const finish = source.indexOf(end, offset);
    assert(finish !== -1, end);
    return source.slice(offset, finish);
}
function assignment(source, name, indent) {
    return block(source, `self.${name} = function`, `\n${" ".repeat(indent)}};`) + `\n${" ".repeat(indent)}};`;
}
const initialization = block(update, "const hthDraft =", "    if (!isModified)");
let maskedCallback, generateCallback;
const self = {userFullData: observable({partyId: {value: "PARTY"}, username: "USER"})};
const context = vm.createContext({self, ko: {observable}, isModified: false,
    rootParams: {rootModel: {params: {}}, baseModel: {showMessages() { throw new Error("Unexpected error"); }}},
    UsersUpdateModel: {
        getHthApiPasswordCodeMasked() { return {done(callback) { maskedCallback = callback; }}; },
        generateHthApiPasswordCode() { return {done(callback) { generateCallback = callback; return this; }, fail() { return this; }}; }
    }, $: () => ({trigger() {}})});
vm.runInContext(initialization, context);
vm.runInContext(block(update, "UsersUpdateModel.getHthApiPasswordCodeMasked(", "\n      }"), context);
maskedCallback({status: {result: "SUCCESSFUL"}, codeId: "OLD", codeStatus: "ACTIVE", maskedCode: "******"});
assert.equal(self.hthApiPasswordCodeId(), "OLD");
const payloadFields = [...update.matchAll(/hthApiPasswordCodeId: (self\.hthApiPassword\w+\(\))/g)];
assert.equal(payloadFields.length, 2);
assert.equal(vm.runInContext(payloadFields[1][1], context), undefined,
    "Displaying the old Code must not submit it for approval again");
vm.runInContext(assignment(update, "regenerateHthApiPasswordCode", 4), context);
self.regenerateHthApiPasswordCode();
generateCallback({status: {result: "SUCCESSFUL"}, codeId: "NEW", codeStatus: "PENDING", code: "654321"});
assert.equal(vm.runInContext(payloadFields[1][1], context), "NEW");
maskedCallback({status: {result: "SUCCESSFUL"}, codeId: "OLD", codeStatus: "ACTIVE", maskedCode: "******"});
assert.equal(self.hthApiPasswordCodeId(), "NEW", "Late masked response must not overwrite the generated draft");
assert.equal(self.hthApiPasswordCodeStatus(), "PENDING");
const reviewFields = block(update, "hthApiPasswordCodeId: self.", "\n      };");
const draft = vm.runInContext(`({${reviewFields}})`, context);
const resumed = {};
vm.runInNewContext(initialization, {self: resumed, ko: {observable}, isModified: true,
    rootParams: {rootModel: {params: {data: draft}}}});
assert.equal(resumed.hthApiPasswordPendingCodeId(), "NEW", "Back from review retains the new submitted Code ID");
assert.equal(resumed.hthApiPasswordCodeStatus(), "PENDING");

const snapshot = {userChannelType: "HTH", hthApiPasswordCodeId: "REJECTED-DRAFT"};
const reader = {userFullData: self.userFullData, userExtensionData: observable(snapshot), approverReview: observable(false)};
for (const name of ["Code", "CodeId", "CodeStatus", "CodePurpose", "CodeExpiryTime", "CodeCanReveal", "CodeVisible"])
    reader["hthApiPassword" + name] = observable();
let readArguments;
const readerContext = vm.createContext({self: reader, UserReadModel: {getHthApiPasswordCodeMasked(...args) {
    readArguments = args; return {done(callback) { callback({status: {result: "SUCCESSFUL"}, codeId: "OLD", codeStatus: "ACTIVE", canReveal: true, maskedCode: "******"}); }};
}}});
vm.runInContext(assignment(read, "updateHthApiPasswordCode", 8), readerContext);
const readCall = block(read.slice(read.lastIndexOf("UserReadModel.getHthApiPasswordCodeMasked(")),
    "UserReadModel.getHthApiPasswordCodeMasked(", "\n            }");
vm.runInContext(readCall, readerContext);
assert.equal(readArguments[2], null, "Ordinary page queries the approved/history Code, not a rejected saved pointer");
assert.equal(reader.hthApiPasswordCodeId(), "OLD");
assert.equal(snapshot.hthApiPasswordCodeId, "REJECTED-DRAFT", "UI display must not mutate the transaction snapshot");
reader.approverReview(true); vm.runInContext(readCall, readerContext);
assert.equal(readArguments[2], "REJECTED-DRAFT", "Approval page requests the exact snapshot Code ID");
reader.updateHthApiPasswordCode({status: {result: "SUCCESSFUL"}});
assert.equal(reader.hthApiPasswordCodeId(), null, "No approved Code clears stale metadata");
assert.equal(reader.hthApiPasswordCodeCanReveal(), false);
reader.updateHthApiPasswordCode({status: {result: "SUCCESSFUL"}, codeId: "USED-OLD", codeStatus: "USED", canReveal: true, maskedCode: "******"});
assert.equal(reader.hthApiPasswordCodeStatus(), "USED");
assert.equal(reader.hthApiPasswordCodeCanReveal(), true, "Previously approved historical Code keeps its authorized eye reveal");

let fetchOptions, fetchParameters;
const getMasked = (block(model, "getHthApiPasswordCodeMasked: function", "\n    };") + "\n").trim().replace("getHthApiPasswordCodeMasked: ", "");
const fetch = vm.runInNewContext(`(${getMasked})`, {$: {Deferred() { return {resolve() {}, reject() {}}; }},
    baseService: {fetch(options, params) { fetchOptions = options; fetchParameters = params; }}});
fetch("PARTY", "USER"); assert(!fetchOptions.url.includes("codeId="));
fetch("PARTY", "USER", "DRAFT"); assert(fetchOptions.url.includes("codeId={codeId}")); assert.equal(fetchParameters.codeId, "DRAFT");
console.log("PASS: production UI blocks: separate displayed/draft Code, no stale resubmission, late-response guard, review return, ordinary vs approval reads, unchanged snapshot and historical eye reveal");
