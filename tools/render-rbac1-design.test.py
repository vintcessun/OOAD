"""Renderer checks only. These are NOT RBAC business/integration tests."""
import importlib.util
from pathlib import Path
import unittest
import hashlib
import tempfile
import copy
import json
import re
import runpy
from xml.etree import ElementTree
from unittest import mock

SCRIPT = Path(__file__).with_name('render-rbac1-design.py')
spec = importlib.util.spec_from_file_location('rbac_design', SCRIPT)
design = importlib.util.module_from_spec(spec)
spec.loader.exec_module(design)

class RendererChecks(unittest.TestCase):
    def setUp(self):
        design.INDEX.clear()
        design.REFS.clear()
        self.path = 'rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/RoleHierarchy.java'
        source = (design.ROOT / self.path).read_text(encoding='utf-8')
        design.INDEX['com.xmu.rbac.ddd.domain.RoleHierarchy'] = {
            'path': self.path, 'source': source, 'sha256': design.sha(design.ROOT / self.path),
            'bytecode': 'PENDING_COMPILE', 'signatures': []}

    def test_actual_behavior_method(self):
        name = design.key('RoleHierarchy', 'ddd')
        self.assertIn('link(', design.method(name, 'link'))
        self.assertEqual(design.REFS[-1]['method'], 'link')

    def test_source_only_signature_uses_declaration_and_keeps_nested_type(self):
        name = 'com.xmu.rbac.ddd.application.AuthorizationApplicationService'
        path = 'rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java'
        source = (design.ROOT / path).read_text(encoding='utf-8')
        design.INDEX[name] = {'path': path, 'source': source, 'sha256': design.sha(design.ROOT / path),
                              'bytecode': 'PENDING_COMPILE', 'signatures': []}
        signature = design.method(name, 'stableSimulation')
        self.assertTrue(signature.startswith('private SimulationResult stableSimulation('), signature)
        self.assertIn('Permission.Kind kind', signature)

    def test_nonexistent_method_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Missing method declaration'):
            design.method(design.key('RoleHierarchy', 'ddd'), 'inventedValidateAcyclic')

    def test_nonexistent_class_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Class not unique/existing'):
            design.key('RoleHierarchyPolicyDoesNotExist', 'ddd')

    def test_missing_compiled_signature_is_rejected(self):
        name = design.key('RoleHierarchy', 'ddd')
        design.INDEX[name]['bytecode'] = 'COMPILED'
        with self.assertRaisesRegex(ValueError, 'javap missing method'):
            design.method(name, 'link')

    def test_text_hash_normalizes_only_crlf(self):
        with tempfile.TemporaryDirectory(prefix='rbac-design-eol-') as directory:
            for suffix in ['.java','.yml','.json','.py','.adoc']:
                path=Path(directory)/('input'+suffix)
                lf='class 示例 {\n  int value;\n}\n'.encode('utf-8')
                path.write_bytes(lf);expected=design.sha(path)
                path.write_bytes(lf.replace(b'\n',b'\r\n'))
                self.assertEqual(design.sha(path),expected,suffix)
                path.write_bytes(lf.replace(b'\n',b'\r'))
                self.assertNotEqual(design.sha(path),expected,'standalone CR must not normalize')
                path.write_bytes(lf+b' ')
                self.assertNotEqual(design.sha(path),expected,'whitespace must not disappear')
                path.write_bytes(b'\xef\xbb\xbf'+lf)
                self.assertNotEqual(design.sha(path),expected,'BOM must not disappear')

    def test_binary_hash_never_normalizes_crlf(self):
        with tempfile.TemporaryDirectory(prefix='rbac-design-binary-') as directory:
            for suffix in ['.class','.jar','.png','.dat']:
                path=Path(directory)/('input'+suffix)
                data=b'\x00binary\r\npayload\xff'
                path.write_bytes(data)
                self.assertEqual(design.sha(path),hashlib.sha256(data).hexdigest(),suffix)
                original=design.sha(path)
                path.write_bytes(data.replace(b'\r\n',b'\n'))
                self.assertNotEqual(design.sha(path),original,suffix)

    def test_fifteen_physical_tables(self):
        ddl = (design.ROOT / 'rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql').read_text(encoding='utf-8')
        self.assertEqual(len(re.findall(r'CREATE TABLE\s+\w+', ddl)), 15)

    def test_package_views_distinguish_imports_from_port_realizations(self):
        design.collect(inspect_bytecode=False)
        layout = runpy.run_path(str(design.ROOT / design.PACKAGE_LAYOUT))
        graphs = dict(layout['package_views'](design.INDEX))
        for name, source, target, marker, *caption in (
            ('structured-packages', 'service', 'mapper', 'openArrow'),
            ('structured-packages', 'dto', 'entity', 'openArrow', '将请求输入映射到实体'),
            ('ddd-packages', 'interfaces.rest', 'interfaces.rest.dto', 'openArrow'),
            ('ddd-packages', 'interfaces.rest.dto', 'application', 'openArrow'),
            ('ddd-packages', 'application', 'domain.port', 'openArrow'),
            ('ddd-packages', 'infrastructure.persistence', 'domain.port', 'triangle'),
            ('ddd-packages', 'infrastructure.persistence', 'infrastructure.persistence.repository', 'openArrow'),
        ):
            self.assertTrue(graphs[name].startswith('<svg'), 'Package views must use the reference SVG template')
            graph = ElementTree.fromstring(graphs[name])
            svg = '{http://www.w3.org/2000/svg}'
            self.assertIn("'Segoe UI', 'Microsoft YaHei'", graph.find(svg+'g').get('font-family'))
            for edge in graph.iter(svg+'g'):
                if edge.get('data-source') == source and edge.get('data-target') == target:
                    self.assertEqual(edge.find(svg+'polyline').get('marker-end'), f'url(#{marker})')
                    if caption:
                        self.assertEqual(''.join(edge.find(svg+'text').itertext()), caption[0])
                    break
            else:
                self.fail(f'{name} is missing {source} → {target}')

    def test_package_view_rejects_realization_when_implements_is_removed(self):
        design.collect(inspect_bytecode=False)
        adapter = 'com.xmu.rbac.ddd.infrastructure.persistence.JpaAuthorizationRepositoryAdapter'
        design.INDEX[adapter]['source'] = design.INDEX[adapter]['source'].replace(' implements AuthorizationRepository', '')
        layout = runpy.run_path(str(design.ROOT / design.PACKAGE_LAYOUT))
        with self.assertRaisesRegex(ValueError, 'JpaAuthorizationRepositoryAdapter.*AuthorizationRepository'):
            list(layout['package_views'](design.INDEX))

    def test_fixed_er_layout_preserves_columns_and_foreign_keys(self):
        ddl = (design.ROOT / 'rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql').read_text(encoding='utf-8')
        with mock.patch.object(design, 'dot_render') as render, mock.patch.object(design, 'write'):
            design.er()
        name, graph, *engines = render.call_args_list[0].args
        self.assertEqual((name, engines), ('schema-15-er', ['neato']))

        expected_tables = dict(re.findall(r'CREATE TABLE (\w+)\s*\((.*?)\) ENGINE=', ddl, re.S))
        actual_tables = dict(re.findall(r'^(sys_\w+) \[pos="[^"]+",label=<(.*?)>\];$', graph, re.M | re.S))
        self.assertEqual(actual_tables.keys(), expected_tables.keys())
        expected_fks = []
        for table, body in expected_tables.items():
            fields = [line.strip().rstrip(',') for line in body.splitlines()
                      if re.match(r'\s*\w+\s+(?:BIGINT|TINYINT|VARCHAR|CHAR|BOOLEAN|INT|TIMESTAMP|JSON)', line)]
            rows = ElementTree.fromstring(actual_tables[table]).findall('TR')[1:]
            self.assertEqual([' '.join(''.join(cell.itertext()) for cell in row.findall('TD')[1:])
                              for row in rows], fields, table)
            expected_fks.extend((table, target, f'{field} → {pk}')
                                for field, target, pk in re.findall(r'FOREIGN KEY\((\w+)\) REFERENCES (\w+)\((\w+)\)', body))
        self.assertCountEqual(re.findall(r'^(sys_\w+) -> (sys_\w+) \[label="([^"]+)",pos=', graph, re.M),
                              expected_fks)

class RuntimeEvidenceChecks(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='rbac-evidence-mode-')
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.archivePath=design.ROOT/'deploy/deployment-evidence.json'
        self.archiveBytes=self.archivePath.read_bytes()
        self.evidence=json.loads(self.archiveBytes)
        self.local=json.loads((design.ROOT/'docs/acceptance/local-results.json').read_text(encoding='utf-8'))
        self.shared=json.loads((design.ROOT/'docs/acceptance/shared-http.json').read_text(encoding='utf-8'))
        # Synthetic binary fixtures only: never update the real archived SHA.
        for module,artifact in self.evidence['prerequisites']['canonicalArtifacts'].items():
            path=self.root/artifact['path'];path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(b'ZIP fixture '+module.encode()+bytes([13,10,0,255]))
            artifact['sha256']=design.sha(path)
            short=module.removeprefix('rbac-')
            if short in self.shared['modules']:self.shared['modules'][short]['jarSha256']=artifact['sha256']

    def tearDown(self):
        self.assertEqual(self.archivePath.read_bytes(),self.archiveBytes,'Do not rewrite archived deployment SHA to fit a rebuild')

    def snapshot(self,mode=None):
        kwargs={'root':self.root}
        if mode is not None:kwargs['runtime_evidence']=mode
        return design.deployment_snapshot(self.evidence,self.local,self.shared,**kwargs)

    def changed_jar(self):
        path=self.root/self.evidence['prerequisites']['canonicalArtifacts']['rbac-structured']['path']
        path.write_bytes(b'New Linux/JDK packaging; not historical deployed bytes')
        return path

    def test_default_current_matches_only_exact_binary_bytes(self):
        result=self.snapshot()
        self.assertEqual(result['runtimeEvidenceMode'],'current')
        self.assertTrue(result['artifactsMatchCurrentBuild'])
        self.assertFalse(result['currentRuntimeVerified'],'Even a byte match is not a live probe')
        self.assertTrue(all(v['matchesRecordedCanonical'] for v in result['localJars'].values()))

    def test_default_current_still_rejects_new_artifact_mismatch(self):
        self.changed_jar()
        with self.assertRaisesRegex(ValueError,'Current JAR differs.*rbac-structured'):self.snapshot()

    def test_default_current_still_rejects_missing_jar(self):
        self.changed_jar().unlink()
        with self.assertRaisesRegex(ValueError,'Canonical JAR missing'):self.snapshot()

    def test_archived_rebuild_does_not_claim_current_acceptance(self):
        self.changed_jar();before=copy.deepcopy(self.evidence)
        result=self.snapshot('archived')
        self.assertEqual(self.evidence,before)
        self.assertFalse(result['artifactsMatchCurrentBuild'])
        self.assertFalse(result['currentRuntimeVerified'])
        self.assertFalse(result['currentBuildAcceptanceClaim'])
        self.assertTrue(result['archivedLocalFinalDeploymentAccepted'])
        for value in result['localJars'].values():
            self.assertFalse(value['comparisonPerformed'])
            self.assertIsNone(value['matchesRecordedCanonical'])
        self.assertIn('artifactsMatchCurrentBuild=false',design.runtime_evidence_note(result))
        self.assertIn('not current CI/build acceptance',design.runtime_evidence_note(result))
        self.assertEqual({m:(r['serviceCount'],r['passedProbes']) for m,r in result['rounds'].items()}, {'rbac-structured':(7,11),'rbac-ddd':(7,11)})

    def test_archived_never_promotes_accidental_same_bytes(self):
        result=self.snapshot('archived')
        self.assertFalse(result['artifactsMatchCurrentBuild'])
        self.assertFalse(result['currentRuntimeVerified'])

    def test_archived_compile_only_may_lack_current_jars(self):
        for artifact in self.evidence['prerequisites']['canonicalArtifacts'].values():(self.root/artifact['path']).unlink()
        result=self.snapshot('archived')
        self.assertTrue(all(not v['exists'] and v['sha256'] is None for v in result['localJars'].values()))
        self.assertFalse(result['artifactsMatchCurrentBuild'])

    def test_archived_rejects_missing_or_corrupt_historical_evidence(self):
        original=copy.deepcopy(self.evidence)
        mutations=[
            lambda e:e.clear(),
            lambda e:e['rounds'].pop('rbac-ddd'),
            lambda e:e['rounds']['rbac-ddd']['services'].pop(),
            lambda e:e['rounds']['rbac-ddd']['probes'].pop(),
            lambda e:e['rounds']['rbac-ddd']['probes'][0].update(passed=False),
            lambda e:e['rounds']['rbac-ddd'].update(nodes=[]),
            lambda e:e['rounds']['rbac-ddd']['services'][0].update(imageMatchesLocalBuild=False),
            lambda e:e['rounds']['rbac-ddd']['services'][0].update(expectedLocalImageId='sha256:'+'0'*64),
            lambda e:e['prerequisites'].pop('canonicalArtifacts'),
            lambda e:e['prerequisites']['canonicalArtifacts']['rbac-ddd'].update(sha256='corrupt'),
            lambda e:e.update(localFinalDeploymentAccepted=False),
        ]
        for i,mutate in enumerate(mutations):
            with self.subTest(corruption=i):
                self.evidence=copy.deepcopy(original);mutate(self.evidence)
                with self.assertRaises(ValueError):self.snapshot('archived')

    def test_both_modes_reject_corrupt_historical_shared_identity(self):
        self.shared['modules']['structured']['jarSha256']='0'*64
        for mode in ['current','archived']:
            with self.subTest(mode=mode),self.assertRaisesRegex(ValueError,'Shared HTTP canonical identity mismatch'):self.snapshot(mode)

    def test_unknown_mode_is_not_an_escape_hatch(self):
        with self.assertRaisesRegex(ValueError,'Unknown runtime evidence mode'):self.snapshot('ignore')

    def test_default_check_cannot_silently_accept_archived_snapshot(self):
        (self.root/'source-index.json').write_text(json.dumps({'runtimeEvidenceMode':'archived'}),encoding='utf-8')
        with mock.patch.object(design,'OUT',self.root),mock.patch('sys.argv',['renderer','--check']):
            with self.assertRaisesRegex(SystemExit,'Evidence mode differs'):design.main()

    def test_archived_check_rejects_promoted_acceptance_metadata(self):
        for field in ['currentRuntimeVerified','artifactsMatchCurrentBuild']:
            with self.subTest(field=field):
                data={'runtimeEvidenceMode':'archived','currentRuntimeVerified':False,'artifactsMatchCurrentBuild':False}
                data[field]=True
                (self.root/'source-index.json').write_text(json.dumps(data),encoding='utf-8')
                with mock.patch.object(design,'OUT',self.root),mock.patch('sys.argv',['renderer','--check','--runtime-evidence=archived']):
                    with self.assertRaisesRegex(SystemExit,'Invalid saved runtime/build acceptance claims'):design.main()

    def test_default_current_check_rechecks_jar_bytes(self):
        self.changed_jar()
        for path,data in [('deploy/deployment-evidence.json',self.evidence),('docs/acceptance/local-results.json',self.local),('docs/acceptance/shared-http.json',self.shared)]:
            target=self.root/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(data),encoding='utf-8')
        data={'runtimeEvidenceMode':'current','currentRuntimeVerified':False,'artifactsMatchCurrentBuild':True,'hashRules':design.HASH_RULES,'sources':{}}
        (self.root/'source-index.json').write_text(json.dumps(data),encoding='utf-8')
        with mock.patch.object(design,'OUT',self.root),mock.patch.object(design,'ROOT',self.root),mock.patch('sys.argv',['renderer','--check']):
            with self.assertRaisesRegex(ValueError,'Current JAR differs.*rbac-structured'):design.main()

if __name__ == '__main__':
    unittest.main(verbosity=2)
