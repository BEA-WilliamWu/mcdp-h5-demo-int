/* Production form/model/AMD encryption -> request DTO -> JCA -> envelope parsing.
 * HTTP, UI widgets and storage are isolated. No UAT credentials or database are used.
 */
const fs = require('fs'), path = require('path'), vm = require('vm');
const crypto = require('crypto'), assert = require('assert'), cp = require('child_process');
const readline = require('readline');
const root = path.resolve(__dirname, '../..');
const app = path.join(root, 'consulting/channel');
const javaRoot = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin') : '';
const java = name => javaRoot ? path.join(javaRoot, name) : name;
const tmp = fs.mkdtempSync('/tmp/hth-session-transport-');
const base = path.join(root, 'consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost');
const service = fs.readFileSync(path.join(base, 'service/HostToHostApiPassword.java'), 'utf8');
const method = service.slice(service.indexOf('  private List<String> decryptCredentials('), service.indexOf('  private void validateRequest'));
const jars = fs.readdirSync(path.join(root, 'consulting/middleware/lib/OBDX_FW_LIB')).filter(f => f.endsWith('.jar')).map(f => path.join(root, 'consulting/middleware/lib/OBDX_FW_LIB', f));
const requestSource = path.join(root, 'consulting/middleware/projects/common/com.ofss.digx.cz.bea.app.xface/src/com/ofss/digx/cz/bea/app/hosttohost/dto/HostToHostApiPasswordRequestDTO.java');
const source = `import java.io.*;import java.util.*;import java.util.logging.*;import com.fasterxml.jackson.databind.*;import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordTransport;import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordTransport.KeyMaterial;import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordRequestDTO;import com.ofss.digx.infra.thread.ThreadAttribute;
public class SessionWire {
static final Logger LOGGER=Logger.getLogger("wire");
${method}
interface Checked {void run() throws Exception;}
static void reject(Checked c) throws Exception {try {c.run();throw new AssertionError("expected failure");}catch(java.security.GeneralSecurityException expected){}}
public static void main(String[] args)throws Exception {
long now=System.currentTimeMillis();
HthApiPasswordTransport.KeyMaterial key=HthApiPasswordTransport.create("TEST_OWNER",now);
ByteArrayOutputStream out=new ByteArrayOutputStream();new ObjectOutputStream(out).writeObject(key);
ThreadAttribute.set(HthApiPasswordTransport.THREAD_ATTRIBUTE,new ObjectInputStream(new ByteArrayInputStream(out.toByteArray())).readObject());
System.out.println(key.getKeyId()+" "+key.getModulus()+" "+key.getPublicExponent());System.out.flush();
BufferedReader in=new BufferedReader(new InputStreamReader(System.in));SessionWire service=new SessionWire();
for(String op:new String[]{"SETUP","RESET"}) {
 HostToHostApiPasswordRequestDTO request=new ObjectMapper().readValue(in.readLine(),HostToHostApiPasswordRequestDTO.class);
 String encrypted=request.getEncryptedCredentials();
 if(!key.getKeyId().equals(request.getTransportKeyId()))throw new AssertionError();
 if(!service.decryptCredentials(request,"TEST_OWNER",op).equals(Arrays.asList("TransportTest123","123456")))throw new AssertionError();
 reject(()->HthApiPasswordTransport.decrypt(key,"OTHER",key.getKeyId(),encrypted,now));
 reject(()->HthApiPasswordTransport.decrypt(key,"TEST_OWNER","wrong",encrypted,now));
 reject(()->HthApiPasswordTransport.decrypt(key,"TEST_OWNER",key.getKeyId(),encrypted,now+600000));
 reject(()->HthApiPasswordTransport.decrypt(null,"TEST_OWNER",key.getKeyId(),encrypted,now));
 reject(()->HthApiPasswordTransport.decrypt(key,"TEST_OWNER",key.getKeyId(),"invalid!",now));
 try {service.decryptCredentials(request,"TEST_OWNER",op.equals("SETUP")?"RESET":"SETUP");throw new AssertionError();}catch(Exception expected){if(!"DIGX_CZ_HTH_API_PASSWORD_010".equals(expected.getMessage()))throw expected;}
 request.setRequestId("different-request");
 try {service.decryptCredentials(request,"TEST_OWNER",op);throw new AssertionError();}catch(Exception expected){if(!"DIGX_CZ_HTH_API_PASSWORD_010".equals(expected.getMessage()))throw expected;}
 System.out.println(op+"_PASS");System.out.flush();
}
ThreadAttribute.clear(HthApiPasswordTransport.THREAD_ATTRIBUTE);
System.out.println("JAVA_PASS");
}}
`;
fs.writeFileSync(path.join(tmp, 'SessionWire.java'), source);
let child;
(async () => {
    cp.execFileSync(java('javac'), ['-proc:none','--release','8','-encoding','UTF-8','-cp',jars.join(path.delimiter),'-d',tmp,path.join(base,'util/HthApiPasswordTransport.java'),requestSource,path.join(tmp,'SessionWire.java')]);
    child = cp.spawn(java('java'), ['-cp',[tmp,...jars].join(path.delimiter),'SessionWire'], {stdio:['pipe','pipe','pipe']});
    let stderr = ''; child.stderr.on('data', chunk => { stderr += chunk; });
    const exited = new Promise(resolve => child.on('exit', code => resolve(code)));
    const lines = readline.createInterface({input:child.stdout})[Symbol.asyncIterator]();
    const first = await lines.next(); assert(!first.done, stderr);
    const [keyId, modulus, publicExponent] = first.value.split(' ');
    let key = {keyId,modulus,publicExponent};
    let currentOperation, submissions = 0;
    const submit = async (method, options) => {
        assert.equal(method,currentOperation === 'SETUP' ? 'POST' : 'PUT');
        assert.equal(options.url,'hostToHostApiPassword/'+currentOperation.toLowerCase());
        assert.equal(options.version,'cz/v1');
        const payload=JSON.parse(options.data);
        assert.deepEqual(Object.keys(payload).sort(),['encryptedCredentials','requestId','transportKeyId']);
        assert.equal(payload.transportKeyId,keyId);
        assert(!options.data.includes('TransportTest123'));
        child.stdin.write(options.data+'\n');
        assert.equal((await lines.next()).value,currentOperation+'_PASS',stderr);
        submissions++;
        return {status:{result:'SUCCESSFUL'}};
    };
    const cache = {
        knockout:{observable:initial=>{let value=initial;return function(next){if(arguments.length)value=next;return value;};}},
        baseService:{getInstance:()=>({
            fetch:options=>{
                assert.equal(options.version,'cz/v1');
                if(options.url==='hostToHostApiPassword/status')return Promise.resolve({});
                assert.equal(options.url,'hostToHostApiPassword/status?transport=true');
                assert.equal(options.throttle,false);
                return Promise.resolve({transportKey:key});
            },
            add:options=>submit('POST',options),update:options=>submit('PUT',options)
        })}
    };
    function load(id) {
        if (cache[id]) return cache[id];
        if(id.startsWith('ojs/'))return {};
        if(id.startsWith('ojL10n!'))return load(id.slice(7)).root;
        let value;
        vm.runInNewContext(fs.readFileSync(path.join(app,id+'.js'),'utf8'), {
            window:{crypto:crypto.webcrypto},navigator:{appName:'Netscape',appVersion:'5'},Uint8Array,
            document:{getElementById:()=>({})},
            require:(deps,callback)=>callback(...deps.map(load)),
            define:(deps,factory)=>{value=factory?factory(...deps.map(dep=>load(dep.startsWith('.')?path.posix.join(path.posix.dirname(id),dep):dep))):deps;}
        },{filename:id});
        return (cache[id]=value);
    }
    const encrypt = load('extensions/components/host-to-host/api-password/transport');
    const Form = load('extensions/components/host-to-host/api-password/api-password');
    for (const op of ['SETUP','RESET']) {
        currentOperation=op;
        const errors=[];
        const form=new Form({data:{mode:op},dashboard:{headerName:()=>{}},baseModel:{
            showComponentValidationErrors:()=>true,showMessages:(_,messages)=>errors.push(...messages)
        }});
        form.newPassword('TransportTest123');form.confirmPassword('TransportTest123');form.passwordCode('123456');
        form.submit();
        const deadline=Date.now()+10000;
        while(form.submitting()&&Date.now()<deadline)await new Promise(resolve=>setTimeout(resolve,10));
        assert.equal(form.submitting(),false,'Form submission timeout');
        assert.deepEqual(errors,[]);
        assert.equal(form.showConfirmation(),true);
        assert.equal(form.newPassword(),null);assert.equal(form.passwordCode(),null);
        const result = await encrypt('TransportTest123','123456','00000000-0000-4000-8000-000000000001',op);
        assert.equal(result.transportKeyId,keyId);
        assert(!result.encryptedCredentials.includes('TransportTest123'));
        const again = await encrypt('TransportTest123','123456','00000000-0000-4000-8000-000000000001',op);
        assert.notEqual(result.encryptedCredentials,again.encryptedCredentials);
    }
    assert.equal(submissions,2);
    child.stdin.end();
    assert.equal((await lines.next()).value,'JAVA_PASS');
    assert.equal(await exited,0,stderr);
    key=null;await assert.rejects(encrypt('p','c','r','SETUP'));
    key={keyId:'id',modulus:'ff',publicExponent:'10001'};await assert.rejects(encrypt('p','c','r','RESET'));
    console.log('PASS: SETUP/RESET form -> model POST/PUT -> real request DTO -> JCA -> production parser; serialized session key, owner/key/expiry/missing-key, operation/request binding, randomized ciphertext, public-key failures and form secret cleanup');
})().catch(error => {console.error(error);process.exitCode=1;}).finally(()=>{if(child)child.kill();fs.rmSync(tmp,{recursive:true,force:true});});
