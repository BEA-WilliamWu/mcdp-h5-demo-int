/* Evaluate the production template's KO conditions/text bindings with each Code state.
 * This checks visible content/actions; Oracle JET/browser layout still needs UAT verification. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../..');
const source = fs.readFileSync(path.join(root, 'consulting/channel/extensions/partials/admin/update-panel.html'), 'utf8');
const section = source.slice(source.indexOf('<!-- BCOH2H-787: HTH API Password Information section'),
    source.indexOf('<!-- End BCOH2H-787 HTH API Password section (update) -->'));
assert(section.length > 0);
function loadNls(relative) {
    let nls;
    vm.runInNewContext(fs.readFileSync(path.join(root, relative), 'utf8'), {
        define: (deps, factory) => { nls = factory ? factory(...deps.map(() => ({}))).root : deps; }
    });
    return nls;
}
function render(state, nls, channel = 'HTH') {
    const context = {
        userExtensionData: () => ({userChannelType: channel}),
        hthApiPasswordCodeId: () => state ? 'fixture-code-id' : null,
        hthApiPasswordCodeStatus: () => state,
        isReGeneratePasswordCode: () => false,
        hthApiPasswordCodeVisible: () => false,
        hthApiPasswordCode: () => { throw new Error('Should not reveal Code'); },
        $component: {nls}, $baseModel: {format: text => text}
    };
    const stack = [true];
    const result = {text: [], buttons: 0, reveal: 0};
    const tokens = section.matchAll(/<!--\s*ko if:\s*([\s\S]*?)-->|<!--\s*\/ko\s*-->|<[^>]+>/g);
    for (const token of tokens) {
        if (token[1]) {
            stack.push(stack[stack.length - 1] && !!vm.runInNewContext(token[1], context));
        } else if (/^<!--\s*\/ko/.test(token[0])) {
            assert(stack.length > 1, 'Unbalanced KO close');
            stack.pop();
        } else if (stack[stack.length - 1]) {
            if (token[0].startsWith('<oj-button ')) result.buttons++;
            if (token[0].includes('click:toggleHthApiPasswordCodeVisible')) result.reveal++;
            const text = /data-bind="text:\s*([^"]+)"/.exec(token[0]);
            if (text) result.text.push(vm.runInNewContext(text[1], context));
        }
    }
    assert.equal(stack.length, 1);
    return result;
}
for (const file of ['consulting/channel/extensions/resources/nls/user-management.js',
    'consulting/translations/extensions/resources/nls/zh-CN/user-management.js',
    'consulting/translations/extensions/resources/nls/zh-Hant/user-management.js']) {
    const nls = loadNls(file);
    for (const state of ['', 'PENDING', 'ACTIVE', 'EXPIRED', 'USED', 'INVALID', 'UNKNOWN', 'IN_PROGRESS']) {
        const result = render(state, nls);
        assert.equal(result.buttons, ['ACTIVE', 'UNKNOWN', 'IN_PROGRESS'].includes(state) ? 0 : 1, file + '/' + state);
        assert.equal(result.reveal, state === 'ACTIVE' ? 1 : 0);
        if (['UNKNOWN', 'IN_PROGRESS'].includes(state)) {
            const label = state === 'UNKNOWN' ? 'hthCodeUnknown' : 'hthCodeInProgress';
            assert(nls.info[label] && nls.info[label + 'Message']);
            assert(result.text.includes(nls.fieldname.hthApiPasswordCodeStatus));
            assert(result.text.includes(nls.info[label]));
            assert(result.text.includes(nls.info[label + 'Message']));
        }
        assert.deepEqual(render(state, nls, 'BCO'), {text: [], buttons: 0, reveal: 0});
    }
}
console.log('PASS: production edit template, eight Code states x three locales, BCO isolation, pending notices and action restrictions');
