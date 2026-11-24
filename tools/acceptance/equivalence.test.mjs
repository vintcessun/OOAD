import test from 'node:test';
import assert from 'node:assert/strict';
import {comparableObservations} from './equivalence.mjs';
const role=(code,system)=>encodeURIComponent(JSON.stringify('@role:'+JSON.stringify([system,code])));
const edge=(a,b,status,system='fixture-role-race')=>({method:'POST',path:`/api/v1/roles/${role(a,system)}/inherits/${role(b,system)}`,status,code:status===200?'OK':'ROLE_CYCLE',responseDigest:status===200?'success-body':'cycle-body'});
const race=observations=>({scenario:'ROLE-03 concurrent reverse edges serialized with DB invariant',observations});
const canonical=row=>comparableObservations(row).map(o=>JSON.stringify(o)).sort();
test('symmetric concurrent race compares legal outcome sets, not scheduler winner',()=>{
  const first=race([edge('RACE_A','RACE_B',200),edge('RACE_B','RACE_A',409)]);
  const second=race([edge('RACE_B','RACE_A',200),edge('RACE_A','RACE_B',409)]);
  assert.deepEqual(canonical(first),canonical(second));
  assert.notEqual(first.observations[0].path,second.observations[0].path,'raw evidence must remain unchanged');
});
test('race comparison rejects both successful writes instead of hiding a cycle',()=>{
  assert.throws(()=>comparableObservations(race([edge('RACE_A','RACE_B',200),edge('RACE_B','RACE_A',200)])));
});
test('race comparison never masks wrong identities or duplicate directions',()=>{
  assert.throws(()=>comparableObservations(race([edge('OTHER_ROLE','RACE_B',200),edge('RACE_B','RACE_A',409)])));
  assert.throws(()=>comparableObservations(race([edge('RACE_A','RACE_B',200),edge('RACE_A','RACE_B',409)])));
});
test('race comparison rejects edges in the shared course graph',()=>{
  const course='course-project-management';
  assert.throws(()=>comparableObservations(race([edge('RACE_A','RACE_B',200,course),edge('RACE_B','RACE_A',409,course)])));
});
test('ordinary observations retain relationship direction and ordering exactly',()=>{
  const row={scenario:'ROLE-02 DAG paths',observations:[edge('RACE_A','RACE_B',200),edge('RACE_B','RACE_A',409)]};
  assert.equal(comparableObservations(row),row.observations);
  assert.notDeepEqual(row.observations,[...row.observations].reverse());
});
