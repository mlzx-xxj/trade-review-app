"""A fresh checkout must retain the original AVD evidence without line-ending edits."""
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest
from xml.etree import ElementTree as E

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('retained_full',ROOT/'scripts/v210-full-evidence.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)


class RetainedEvidenceTest(unittest.TestCase):
    def test_original_files_and_actual_financial_proof_remain_verifiable(self):
        root=ROOT/'docs/v210-full';index=json.loads((root/'comparison.json').read_text())
        for key in ('screenshotSha256','originalEvidenceSha256'):
            for name,expected in index[key].items():
                path=root/name
                self.assertTrue(path.resolve().is_relative_to(root.resolve()))
                self.assertEqual(expected,hashlib.sha256(path.read_bytes()).hexdigest(),name)
        for side in ('before','after'):
            for api in (29,35):
                folder=root/'evidence'/side/f'api{api}'
                xmls=list(folder.glob('*.xml'));self.assertTrue(xmls)
                totals={k:sum(int(E.parse(p).getroot().get(k,0)) for p in xmls) for k in ('tests','failures','errors','skipped')}
                self.assertEqual(index['verifiedSources'][f'{side}-api{api}']['junit'],totals)
                for profile in ('regular-light','regular-dark','narrow-light','narrow-dark'):
                    first=json.loads((folder/f'v210-full-{profile}-api{api}.json').read_text())
                    second=json.loads((folder/f'v210-full-reopen-{profile}-api{api}.json').read_text())
                    checker.verify(first,second,api,profile)
