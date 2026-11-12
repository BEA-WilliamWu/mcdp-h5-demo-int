/* Run with node; exercises production error presentation with isolated UI doubles. */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');
const root = path.resolve(__dirname, '../../..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const channel = 'consulting/channel/';
const source = read(channel + 'extensions/components/host-to-host/api-password/api-password.js');
const start = source.indexOf('        self.showSubmissionError = function');
const end = source.indexOf('        self.submit = function', start);
const sql = read('consulting/db/branch_change_history/hosttohost/hth_onboarding.sql').replace(/\r\n/g, '\n');
let cases = 0;
for (const [file, locale] of [['hth-api-password.js', 'en'], ['zh-CN/hth-api-password.js', 'zh-hans-cn'], ['zh-Hant/hth-api-password.js', 'zh-hant']]) {
    let nls;
    vm.runInNewContext(read(channel + 'extensions/resources/nls/' + file), {define: data => { nls = data.root || data; }});
    let shown;
    let failure = false;
    const self = {nls, showSyncFailure: value => { failure = value; }};
    vm.runInNewContext(source.slice(start, end), {self, params: {baseModel: {showMessages: (_, messages, type) => {
        assert.equal(type, 'ERROR'); shown = messages[0];
    }}}});
    for (let i = 1; i <= 10; i++) {
        const code = 'DIGX_CZ_HTH_API_PASSWORD_' + String(i).padStart(3, '0');
        const expected = nls.submissionErrors[code];
        assert(expected);
        assert(sql.includes("('" + code + "', '" + expected + "',\n     '" + locale + "'"));
        for (const body of [{result: 'SUCCESSFUL', message: {code, type: 'ERROR'}}, {status: {message: {code, type: 'ERROR'}}}]) {
            failure = false;
            self.showSubmissionError({responseJSON: body});
            if (i === 9) { assert.equal(failure, true); }
            else { assert.equal(failure, false); assert.equal(shown, expected); assert(!shown.includes(code)); }
            cases++;
        }
    }
    failure = false;
    self.showSubmissionError({hthSyncFailure: true, status: 0});
    assert.equal(failure, true);
    self.showSubmissionError({responseJSON: {message: {code: 'UNKNOWN'}}});
    assert.equal(shown, nls.submissionError);
    self.showSubmissionError({hthInputError: nls.encryptionError});
    assert.equal(shown, nls.encryptionError);
    if (locale === 'en') {
        assert.equal(nls.submissionErrors.DIGX_CZ_HTH_API_PASSWORD_003, 'The HTH API Password Code has expired. Please contact your Authorized Person to generate a new code.');
        assert.equal(nls.submissionErrors.DIGX_CZ_HTH_API_PASSWORD_002, 'The HTH API password code you entered is invalid, please enter it again');
        assert.equal(nls.passwordMismatch, 'The passwords do not match.');
        assert.equal(nls.syncFailureHeader, 'Fail');
        assert.equal(nls.syncFailureMessage, 'Password synchronization failed. Please try again later.');
    }
}
console.log('PASS: ' + cases + ' localized response cases, AC wording, SQL alignment and fallbacks');

// Execute the full view model: UNKNOWN must not present a usable form or start encryption/write.
(async function () {
    for (const state of ['UNKNOWN', 'REQUIRED', 'ACTIVE']) {
        let ViewModel, encryptionCalls = 0, writes = 0, validations = 0;
        const observable = initial => {
            let value = initial;
            return function (next) { if (arguments.length) { value = next; } return value; };
        };
        const Model = {
            status: () => Promise.resolve({setupState: state}),
            setup: () => { writes++; }, reset: () => { writes++; }
        };
        vm.runInNewContext(source, {
            define: (_, factory) => { ViewModel = factory({observable}, Model, () => { encryptionCalls++; }, {}); },
            window: {crypto: require('node:crypto').webcrypto},
            document: {getElementById: () => null}, Promise, Uint8Array
        });
        const form = new ViewModel({data: {embedded: true}, baseModel: {
            showComponentValidationErrors: () => { validations++; return true; }
        }});
        await new Promise(resolve => setImmediate(resolve));
        assert.equal(form.showSyncFailure(), state === 'UNKNOWN');
        assert.equal(form.showConfirmation(), false);
        if (state === 'UNKNOWN') {
            form.submit();
            await new Promise(resolve => setImmediate(resolve));
            assert.equal(encryptionCalls, 0);
            assert.equal(writes, 0);
            assert.equal(validations, 0);
            assert.equal(form.newPassword(), null);
        }
    }
    console.log('PASS: UNKNOWN status shows failure and blocks submission; known-status rendering unchanged');
})().catch(error => { console.error(error); process.exitCode = 1; });
