/* Run with node; exercises production error presentation with isolated UI doubles. */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');
const root = path.resolve(__dirname, '../..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const channel = 'consulting/channel/';
const source = read(channel + 'extensions/components/host-to-host/api-password/api-password.js');
const start = source.indexOf('        self.showSubmissionError = function');
const end = source.indexOf('        self.submit = function', start);
const sql = read('consulting/db/branch_change_history/20260907_HTH_API_Password/5_HTH_API_Password_Error_Messages.sql');
let cases = 0;
for (const [file, locale] of [['hth-api-password.js', 'en'], ['zh-CN/hth-api-password.js', 'zh-hans-cn'], ['zh-Hant/hth-api-password.js', 'zh-hant']]) {
    let nls;
    vm.runInNewContext(read(channel + 'extensions/resources/nls/' + file), {define: data => { nls = data.root || data; }});
    let shown;
    const self = {nls};
    vm.runInNewContext(source.slice(start, end), {self, params: {baseModel: {showMessages: (_, messages, type) => {
        assert.equal(type, 'ERROR'); shown = messages[0];
    }}}});
    for (let i = 1; i <= 10; i++) {
        const code = 'DIGX_CZ_HTH_API_PASSWORD_' + String(i).padStart(3, '0');
        const expected = nls.submissionErrors[code];
        assert(expected);
        assert(sql.includes("('" + code + "', '" + expected + "',\n     '" + locale + "'"));
        for (const body of [{result: 'SUCCESSFUL', message: {code, type: 'ERROR'}}, {status: {message: {code, type: 'ERROR'}}}]) {
            self.showSubmissionError({responseJSON: body});
            assert.equal(shown, expected);
            assert(!shown.includes(code));
            cases++;
        }
    }
    self.showSubmissionError({responseJSON: {message: {code: 'UNKNOWN'}}});
    assert.equal(shown, nls.submissionError);
    self.showSubmissionError({hthInputError: nls.encryptionError});
    assert.equal(shown, nls.encryptionError);
    if (locale === 'en') {
        assert.equal(nls.submissionErrors.DIGX_CZ_HTH_API_PASSWORD_003, 'The HTH API Password Code has expired. Please contact your Authorized Person to generate a new code.');
        assert.equal(nls.submissionErrors.DIGX_CZ_HTH_API_PASSWORD_002, 'The HTH API password code you entered is invalid, please enter it again');
        assert.equal(nls.passwordMismatch, 'The passwords do not match.');
    }
}
console.log('PASS: ' + cases + ' localized response cases, AC wording, SQL alignment and fallbacks');
