"""Substitution boundary controls; no native code is executed."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('assembler', Path(__file__).with_name('assemble-native-preview.py'))
assembler = importlib.util.module_from_spec(spec)
spec.loader.exec_module(assembler)


class PreviewBoundaryTest(unittest.TestCase):
    def test_source_substitution_preserves_java_and_rejects_drift(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); base = root/'accepted.jar'; source = root/'source.dll'
            name = 'resources/com/pty4j/native/win/x86-64/winpty.dll'
            source.write_bytes(b'MZsource fixture')
            with zipfile.ZipFile(base, 'w') as jar:
                jar.writestr(name, b'MZoriginal fixture')
                jar.writestr('trusted/Main.class', b'unchanged bytecode fixture')
                jar.writestr('META-INF/LICENSE', b'unchanged legal fixture')
            inventory = {name: assembler.digest(b'MZoriginal fixture')}
            replacements = {name: dict(path=source, sha256=assembler.digest(source.read_bytes()), origin='controlled receipt')}
            expected = assembler.digest(base.read_bytes())
            result = assembler.assemble(base, root/'preview.jar', replacements, expected, inventory, 'Controlled fixture')
            self.assertEqual(result['preservedEntries'], 2)
            with zipfile.ZipFile(root/'preview.jar') as preview:
                self.assertEqual(preview.read('trusted/Main.class'), b'unchanged bytecode fixture')
                self.assertEqual(preview.read(name), source.read_bytes())
            cases = [
                ('base-drift', replacements, '0'*64, inventory),
                ('old-native-drift', replacements, expected, {name:'0'*64}),
                ('source-drift', {name:dict(replacements[name], sha256='0'*64)}, expected, inventory),
                ('java-replacement', {'trusted/Main.class':replacements[name]}, expected,
                 dict(inventory, **{'trusted/Main.class':assembler.digest(b'unchanged bytecode fixture')})),
                ('missing-origin', {name:dict(replacements[name], origin='')}, expected, inventory),
            ]
            for label, items, base_sha, old in cases:
                with self.subTest(label=label), self.assertRaises(ValueError):
                    assembler.assemble(base, root/(label+'.jar'), items, base_sha, old, 'Controlled fixture')
                self.assertFalse((root/(label+'.jar')).exists())
            with self.assertRaises(ValueError):
                assembler.assemble(base, base, replacements, expected, inventory, 'Controlled fixture')
            with self.assertRaises(ValueError):
                assembler.assemble(base, root/'preview.jar', replacements, expected, inventory, 'Controlled fixture')


if __name__ == '__main__':
    unittest.main()
