/* Exercise the production component optimizer and load its output after cleanup. */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const vm = require('vm');
const crypto = require('crypto');
const root = path.resolve(__dirname, '../..');
const channel = path.join(root, 'consulting/channel');
const build = path.join(channel, '_build');
const requirejs = require(path.join(build, 'node_modules/requirejs'));
const terser = require(path.join(build, 'node_modules/terser'));
const component = 'extensions/components/host-to-host/api-password';
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'hth-password-bundle-'));

(async () => {
    const input = path.join(tmp, 'destInt');
    const output = path.join(tmp, 'dist', component, 'loader.js');
    fs.mkdirSync(path.join(input, component), {recursive: true});
    fs.mkdirSync(path.dirname(output), {recursive: true});
    fs.symlinkSync(path.join(channel, 'extensions/resources'), path.join(input, 'extensions/resources'));
    fs.symlinkSync(path.join(channel, 'framework'), path.join(input, 'framework'));
    for (const name of fs.readdirSync(path.join(channel, component))) {
        if (!/\.(js|html|css)$/.test(name)) continue;
        let content = fs.readFileSync(path.join(channel, component, name), 'utf8');
        if (name.endsWith('.js')) {
            const minified = await terser.minify(content);
            if (minified.error) throw minified.error;
            content = minified.code;
        }
        fs.writeFileSync(path.join(input, component, name), content);
    }
    // Resolve build plugins absolutely because the isolated destInt is outside channel.
    const paths = Object.fromEntries(['text', 'css', 'ojL10n', 'css-builder', 'normalize']
        .map(name => [name, path.join(build, 'tmp', name)]));
    await new Promise((resolve, reject) => requirejs.optimize({
        baseUrl: input,
        mainConfigFile: path.join(build, 'build-config-components.js'),
        paths,
        name: component + '/loader',
        out: output,
        optimize: 'none', optimizeCss: 'none', preserveLicenseComments: false,
        exclude: ['text', 'css', 'ojL10n', 'load'],
        pragmasOnSave: {excludeRequireCss: true}
    }, resolve, reject));
    const bundled = fs.readFileSync(output, 'utf8');
    const modules = new Map();
    const cache = new Map();
    const messages = [];
    const requests = [];
    cache.set('knockout', {observable: initial => {
        let value = initial;
        return function (next) { if (arguments.length) value = next; return value; };
    }});
    cache.set('base-models/css', {getComponentName: () => 'api-password', transformTemplate: html => html});
    cache.set('baseService', {getInstance: () => ({fetch: options => {
        requests.push(options.url);
        if (options.url.endsWith('?transport=true')) return Promise.resolve({});
        return Promise.resolve({});
    }})});
    const define = (name, deps, factory) => {
        assert.equal(typeof name, 'string');
        if (!Array.isArray(deps)) { factory = deps; deps = []; }
        modules.set(name, {deps, factory});
    };
    const context = {
        define, window: {crypto: crypto.webcrypto}, navigator: {appName: 'Netscape', appVersion: '5'},
        document: {getElementById: () => ({})}, Uint8Array,
        require: () => { throw new Error('Unexpected dynamic script request after component cleanup'); }
    };
    vm.runInNewContext(bundled, context, {filename: output});
    assert(modules.has(component + '/transport'), 'Transport must be defined inside loader.js');
    assert(!fs.existsSync(path.join(tmp, 'dist', component, 'transport.js')));
    function load(id) {
        if (cache.has(id)) return cache.get(id);
        if (id.startsWith('ojs/')) return {};
        if (id === 'ojL10n!extensions/resources/nls/hth-api-password') {
            // Resource bundles remain separately deployable after component cleanup.
            vm.runInNewContext(fs.readFileSync(path.join(channel, id.slice(7) + '.js'), 'utf8'), {
                define: resource => cache.set(id, resource.root)
            });
            return cache.get(id);
        }
        if (!modules.has(id) && id.startsWith('framework/js/plugins/encryption/')) {
            // Framework JS survives component cleanup and is an external bundle dependency.
            vm.runInNewContext(fs.readFileSync(path.join(channel, id + '.js'), 'utf8'), {
                ...context, define: (deps, factory) => modules.set(id, {deps, factory})
            }, {filename: id});
        }
        assert(modules.has(id), 'Unexpected standalone dependency: ' + id);
        const {deps, factory} = modules.get(id);
        const args = deps.map(dep => {
            if (dep === 'module') return {id};
            const parts = dep.split('!');
            const resource = parts.pop();
            parts.push(resource.startsWith('.') ? path.posix.join(path.posix.dirname(id), resource) : resource);
            return load(parts.join('!'));
        });
        const result = typeof factory === 'function' ? factory(...args) : factory;
        cache.set(id, result);
        return result;
    }
    const Loader = load(component + '/loader');
    const form = new Loader.viewModel({data: {mode: 'SETUP'}, dashboard: {headerName: () => {}},
        baseModel: {showComponentValidationErrors: () => true, showMessages: (_, list) => messages.push(...list)}});
    form.newPassword('TransportTest123'); form.confirmPassword('TransportTest123'); form.passwordCode('123456');
    form.submit();
    const deadline = Date.now() + 5000;
    while (form.submitting() && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(form.submitting(), false);
    assert(requests.includes('hostToHostApiPassword/status?transport=true'));
    assert.equal(messages[0], form.nls.encryptionError);
    console.log('PASS: minified production loader includes transport; standalone transport absent; bundled form reaches key request and handles failure without dynamic script loading');
})().catch(error => { console.error(error); process.exitCode = 1; })
    .finally(() => fs.rmSync(tmp, {recursive: true, force: true}));
