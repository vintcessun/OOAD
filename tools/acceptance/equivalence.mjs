import assert from 'node:assert/strict';

/** Only this deliberately symmetric concurrent race has multiple valid outcomes.
 * Raw observations remain in the report. No ordinary relationship/path/order is dropped.
 */
export function comparableObservations(row) {
  if (!row.scenario.startsWith('ROLE-03 concurrent reverse edges')) return row.observations;
  const edges=[];
  const observations=row.observations.map(observation=>{
    if(observation.method!=='POST'||!observation.path.includes('/inherits/')) return observation;
    const match=observation.path.match(/^\/api\/v1\/roles\/([^/]+)\/inherits\/([^/?]+)$/);
    assert.ok(match,'Race must retain registered role paths');
    const role=encoded=>{
      const alias=JSON.parse(decodeURIComponent(encoded));
      assert.ok(alias.startsWith('@role:'),'Race requires typed role references');
      const key=JSON.parse(alias.slice(6));
      assert.equal(key[0],'fixture-role-race');
      assert.equal(key.length,2);assert.ok(['RACE_A','RACE_B'].includes(key[1]));
      return key[1];
    };
    const from=role(match[1]),to=role(match[2]);assert.notEqual(from,to);
    edges.push({from,to,status:observation.status,code:observation.code});
    return {...observation,path:'/api/v1/roles/@symmetric-race-pair/inherits/@opposite'};
  });
  assert.equal(edges.length,2);assert.notEqual(edges[0].from,edges[1].from);
  assert.equal(edges.filter(e=>e.status===200&&e.code==='OK').length,1);
  assert.equal(edges.filter(e=>e.status===409&&e.code==='ROLE_CYCLE').length,1);
  return observations;
}
