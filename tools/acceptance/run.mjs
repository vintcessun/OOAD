import { provision } from './fixture.mjs';
import { runSuite } from './suite.mjs';
import { comparableObservations } from './equivalence.mjs';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { tmpdir } from 'node:os';
import assert from 'node:assert/strict';

const args=process.argv.slice(2);
const selection=args.find(a=>a.startsWith('--module='))?.split('=')[1]??'both';
if(!['both','structured','ddd'].includes(selection)||args.some(a=>!a.startsWith('--module=')&&!a.startsWith('--report='))) {console.error('Usage: node run.mjs [--module=both|structured|ddd] [--report=absolute-directory]');process.exit(2);}
const output=resolve(args.find(a=>a.startsWith('--report='))?.slice(9)??join(tmpdir(),`rbac1-acceptance-report-${Date.now()}`));
const report={format:'rbac1-http-acceptance-v1',startedAt:new Date().toISOString(),command:'node tools/acceptance/run.mjs '+args.filter(a=>a.startsWith('--module=')).join(' '),rows:[],modules:{},exitCode:null};
await mkdir(output,{recursive:true});
let interrupted=false;
// Cleanup always finishes before exit; never signal any process other than owned children.
for(const sig of ['SIGINT','SIGTERM']) process.on(sig,()=>{interrupted=true;});
for(const module of selection==='both'?['structured','ddd']:[selection]) {
  if(interrupted) break;
  let f;
  try {
    f=await provision(module);report.modules[module]={database:f.name,logDirectory:f.dir,ports:f.ports,jarSha256:f.jarSha256};
    await runSuite(f,async row=>{report.rows.push({module,...row});console.log(`${module.padEnd(10)} | ${row.status.padEnd(4)} | ${row.scenario}`);});
  } catch(e) {
    if(e.fixture) report.modules[module]=e.fixture;
    if(e.cleanupFailed) report.rows.push({module,scenario:'ENVIRONMENT cleanup after failed readiness',status:'fail',classification:'ENVIRONMENT_BLOCKED',error:'Owned cleanup failed; inspect listed database and log directory'});
    // Provisioning errors are never a business RED. JVM startup/schema failures require log diagnosis.
    report.rows.push({module,scenario:'ENVIRONMENT provisioning/readiness',status:'fail',classification:'ENVIRONMENT_BLOCKED',error:'Provisioning failed; verify JAVA_HOME JDK21, built jar, dedicated MySQL/Redis, privileges and JVM logs. No business verdict.'});
    console.error(`${module} | ENVIRONMENT_BLOCKED | ${e.message.includes('password')?'configuration error (redacted)':e.message}`);
  } finally {
    if(f) try {await f.cleanup();} catch {report.rows.push({module,scenario:'ENVIRONMENT cleanup',status:'fail',classification:'ENVIRONMENT_BLOCKED',error:'Owned resource cleanup failed; inspect listed database and external log directory'});}
  }
}
if(selection==='both') {
  const s=report.rows.filter(r=>r.module==='structured'&&!r.scenario.startsWith('ENVIRONMENT'));
  const d=report.rows.filter(r=>r.module==='ddd'&&!r.scenario.startsWith('ENVIRONMENT'));
  let failure='Both backend runs must complete with the same scenario count';
  try {
    assert.ok(s.length>0&&d.length>0,'Both backend runs must complete');assert.equal(s.length,d.length);
    for(let i=0;i<s.length;i++) {
      failure=`Scenario failed or the backend scenario names differ: ${s[i].scenario}`;
      assert.equal(s[i].scenario,d[i].scenario);assert.equal(s[i].status,'pass',`Structured contract failed: ${s[i].scenario}`);assert.equal(d[i].status,'pass',`DDD contract failed: ${d[i].scenario}`);
      failure=`HTTP status, error code or normalized response mismatch: ${s[i].scenario}`;
      const canonical=rows=>rows.map(r=>JSON.stringify(r)).sort();assert.deepEqual(canonical(comparableObservations(s[i])),canonical(comparableObservations(d[i])));
      failure=`Normalized result or DB mismatch: ${s[i].scenario}`;
      assert.deepEqual(s[i].result,d[i].result);
    }
    report.rows.push({module:'comparison',scenario:'T21 normalized success/error/DB equivalence',status:'pass'});
  } catch {
    report.rows.push({module:'comparison',scenario:'T21 normalized success/error/DB equivalence',status:'fail',classification:report.rows.some(r=>r.classification==='ENVIRONMENT_BLOCKED')?'ENVIRONMENT_BLOCKED':'CONTRACT_RED',error:failure});
    console.error(`comparison | fail | ${failure}`);
  }
}
report.finishedAt=new Date().toISOString();
report.exitCode=interrupted||report.rows.some(r=>r.classification==='ENVIRONMENT_BLOCKED')?2:report.rows.some(r=>r.status==='fail')?1:0;
report.summary={pass:report.rows.filter(r=>r.status==='pass').length,fail:report.rows.filter(r=>r.status==='fail').length};
await writeFile(join(output,'report.json'),JSON.stringify(report,null,2));
console.log(`JSON table: ${join(output,'report.json')} | exit ${report.exitCode}`);
process.exitCode=report.exitCode;
