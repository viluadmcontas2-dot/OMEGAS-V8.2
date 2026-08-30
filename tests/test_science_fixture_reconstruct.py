import sys
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))

class FixtureReconstructTest(unittest.TestCase):
    def test_reconstructs_and_validates_canonical_fixture(self):
        from tools.science.reconstruct_fixture import reconstruct_fixture
        fixture = ROOT / 'tests/fixtures/science/episodes'
        with tempfile.TemporaryDirectory() as td:
            result = reconstruct_fixture(fixture, fixture / 'index.json', Path(td) / 'episodes.jsonl.gz')
        self.assertEqual(result['compressed_sha256'], '9fd4a4fda3d907af67c9c29c01b17b54cb607f13c3351b66aff553e962980d94')
        self.assertEqual(result['compressed_bytes'], 34846)
        self.assertEqual(result['episode_lines'], 1708)
        self.assertEqual(result['uncompressed_sha256'], 'ae050e6770143bd042cc0416fc66cbd91d5694d7ca7917e2d9cfdf078f34a8fd')

    def test_fails_closed_on_tampered_part(self):
        from tools.science.reconstruct_fixture import reconstruct_fixture
        src = ROOT / 'tests/fixtures/science/episodes'
        with tempfile.TemporaryDirectory() as td:
            td = Path(td)
            parts = td / 'parts'; parts.mkdir()
            for p in src.glob('*.part*'):
                (parts / p.name).write_bytes(p.read_bytes())
            target = sorted(parts.glob('*.part*'))[3]
            target.write_bytes(b'X' + target.read_bytes()[1:])
            with self.assertRaisesRegex(ValueError, 'sha256 mismatch'):
                reconstruct_fixture(parts, src / 'index.json', td / 'out.gz')

if __name__ == '__main__': unittest.main()
