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
            assert.equal(failure, false);
            assert.equal(shown, expected);
            assert(!shown.includes(code));
            cases++;
        }
    }
    failure = false;
    self.showSubmissionError({hthSyncFailure: true, status: 0});
    assert.equal(failure, false);
    assert.equal(shown, nls.syncFailureMessage);
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

// Exercise the real AMD form, model and transport together; only HTTP/UI are fixtures.
(async function () {
    const crypto = require('node:crypto');
    const pair = crypto.generateKeyPairSync('rsa', {modulusLength: 2048});
    const publicKey = pair.publicKey.export({type: 'spki', format: 'der'}).toString('base64');
    const jwk = pair.publicKey.export({format: 'jwk'});
    const transportSource = read(channel + 'extensions/components/host-to-host/api-password/transport.js');
    const modelSource = read(channel + 'extensions/components/host-to-host/api-password/model.js');
    let BigInteger, encryptForDsp;
    const cryptoGlobals = {
        TextEncoder, Uint8Array, Promise, navigator: {appName: 'Netscape'},
        window: {crypto: crypto.webcrypto, atob: value => Buffer.from(value, 'base64').toString('binary')}
    };
    vm.runInNewContext(read(channel + 'framework/js/plugins/encryption/libs/js-big-integer.js'), {
        ...cryptoGlobals, define: (_, factory) => { BigInteger = factory(); }
    });
    vm.runInNewContext(read(channel + 'extensions/components/host-to-host/api-password/dsp-encrypt.js'), {
        ...cryptoGlobals, define: (_, factory) => { encryptForDsp = factory(BigInteger); }
    });
    const template = read(channel + 'extensions/components/host-to-host/api-password/api-password.html');
    let nls;
    vm.runInNewContext(read(channel + 'extensions/resources/nls/hth-api-password.js'), {define: value => { nls = value.root; }});
    assert(template.includes('!showConfirmation() && !showSyncFailure()'));
    const businessError = code => ({status: 400, responseJSON: {result: 'SUCCESSFUL', message: {code, type: 'ERROR'}}});
    const scenarios = [
        {name: 'public key HTTP 400 / 009', phase: 'key', error: businessError('DIGX_CZ_HTH_API_PASSWORD_009'), text: nls.submissionErrors.DIGX_CZ_HTH_API_PASSWORD_009},
        {name: 'public key HTTP 400 / 010', phase: 'key', error: businessError('DIGX_CZ_HTH_API_PASSWORD_010'), text: nls.submissionErrors.DIGX_CZ_HTH_API_PASSWORD_010},
        {name: 'public key unstructured rejection', phase: 'key', error: {status: 400}, text: nls.encryptionError},
        {name: 'save HTTP 400 invalid code', phase: 'write', error: businessError('DIGX_CZ_HTH_API_PASSWORD_002'), text: nls.submissionErrors.DIGX_CZ_HTH_API_PASSWORD_002},
        {name: 'save HTTP 500 / 009', phase: 'write', error: {...businessError('DIGX_CZ_HTH_API_PASSWORD_009'), status: 500}, text: nls.submissionErrors.DIGX_CZ_HTH_API_PASSWORD_009},
        {name: 'save network failure', phase: 'write', error: {status: 0}, text: nls.syncFailureMessage},
        {name: 'save HTTP 200 with business error', phase: 'write', response: {result: 'SUCCESSFUL', message: {type: 'ERROR'}}, text: nls.syncFailureMessage}
    ];
    let flows = 0;
    for (const mode of ['SETUP', 'RESET']) {
        for (const scenario of scenarios) {
            const calls = [], messages = [];
            let navigating = 0, fail = true, Model, Encrypt, ViewModel;
            const baseService = {
                fetch: options => {
                    assert.equal(options.showMessage, false);
                    if (options.url === 'hostToHostApiPassword/status') {
                        return Promise.resolve({setupState: mode === 'RESET' ? 'ACTIVE' : 'REQUIRED'});
                    }
                    assert(options.url.startsWith('hostToHostApiPassword/status?transport=true&requestId='));
                    calls.push({phase: 'key', options});
                    if (fail && scenario.phase === 'key') return Promise.reject(scenario.error);
                    return Promise.resolve({transportKey: {
                        keyId: 'test-key', dspPublicKey: publicKey,
                        modulus: Buffer.from(jwk.n, 'base64url').toString('hex'), publicExponent: '010001'
                    }});
                },
                add: options => write('POST', options),
                update: options => write('PUT', options)
            };
            function write(method, options) {
                assert.equal(options.showMessage, false);
                assert.equal(options.url, 'hostToHostApiPassword/' + (mode === 'RESET' ? 'reset' : 'setup'));
                assert.equal(method, mode === 'RESET' ? 'PUT' : 'POST');
                const payload = JSON.parse(options.data);
                assert(!options.data.includes('Browser123'));
                assert(payload.encryptedCredentials && payload.dspEncryptedPassword && payload.transportKeyId);
                calls.push({phase: 'write', method, payload});
                if (fail && scenario.phase === 'write') {
                    return scenario.error ? Promise.reject(scenario.error) : Promise.resolve(scenario.response);
                }
                return Promise.resolve({status: {result: 'SUCCESSFUL'}, setupState: 'ACTIVE'});
            }
            class RSAKey {
                setPublic(modulus, exponent) { assert(modulus); assert.equal(exponent, '010001'); }
                encryptb64(value) {
                    return crypto.publicEncrypt({key: pair.publicKey, padding: crypto.constants.RSA_PKCS1_PADDING}, Buffer.from(value)).toString('base64');
                }
            }
            const observable = initial => {
                let value = initial;
                return function (next) { if (arguments.length) value = next; return value; };
            };
            const globals = {
                window: {
                    crypto: crypto.webcrypto,
                    atob: value => Buffer.from(value, 'base64').toString('binary'),
                    btoa: value => Buffer.from(value, 'binary').toString('base64')
                },
                TextEncoder, Uint8Array, Promise,
                document: {getElementById: id => id === 'hthApiPasswordTracker' ? {} : null}
            };
            vm.runInNewContext(modelSource, {...globals, define: (_, factory) => { Model = factory({getInstance: () => baseService}); }});
            vm.runInNewContext(transportSource, {...globals, define: (_, factory) => { Encrypt = factory({getInstance: () => baseService}, RSAKey, encryptForDsp); }});
            vm.runInNewContext(source, {...globals, define: (_, factory) => { ViewModel = factory({observable}, Model, Encrypt, nls); }});
            const form = new ViewModel({data: {embedded: true, mode}, dashboard: {
                switchModule: () => { navigating++; }, loadComponent: () => { navigating++; }
            }, baseModel: {
                showComponentValidationErrors: () => true,
                showMessages: (_, text, type) => { assert.equal(type, 'ERROR'); messages.push(text[0]); }
            }});
            await new Promise(resolve => setImmediate(resolve));
            const requestId = form.requestId;
            async function submit() {
                form.newPassword('Browser123');
                form.confirmPassword('Browser123');
                form.passwordCode('314159');
                form.submit();
                for (let i = 0; form.submitting() && i < 1000; i++) await new Promise(resolve => setTimeout(resolve, 2));
                assert.equal(form.submitting(), false, scenario.name);
            }
            await submit();
            assert.deepEqual(calls.map(call => call.phase), scenario.phase === 'key' ? ['key'] : ['key', 'write']);
            assert.deepEqual(messages, [scenario.text], scenario.name);
            assert.equal(form.showSyncFailure(), false, scenario.name);
            assert.equal(form.showConfirmation(), false, scenario.name);
            assert.equal(form.requestId, requestId);
            assert.equal(navigating, 0);
            assert.equal(form.newPassword(), null);
            assert.equal(form.confirmPassword(), null);
            assert.equal(form.passwordCode(), null);
            // Retrying retains the ID; server-side reservation/replay checks remain authoritative.
            fail = false;
            calls.length = 0;
            await submit();
            assert.deepEqual(calls.map(call => call.phase), ['key', 'write']);
            assert(calls[0].options.url.endsWith('requestId=' + requestId));
            assert.equal(calls[1].payload.requestId, requestId);
            assert.equal(form.showSyncFailure(), false);
            assert.equal(form.showConfirmation(), true);
            assert.notEqual(form.requestId, requestId);
            assert.equal(navigating, 0);
            flows++;
        }
    }
    console.log('PASS: ' + flows + ' SETUP/RESET error-and-retry flows; key failure prevents save, errors keep form, success shows confirmation');
})().catch(error => { console.error(error); process.exitCode = 1; });
