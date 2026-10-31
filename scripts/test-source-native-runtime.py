"""Protect current product classes, native scope and entry identity at staging."""
import importlib.util
import io
from pathlib import Path
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('stage',Path(__file__).with_name('stage-source-native-runtime.py'))
stage=importlib.util.module_from_spec(spec);spec.loader.exec_module(stage)


def jar(entries):
    data=io.BytesIO()
    with zipfile.ZipFile(data,'w') as z:
        for name,raw in entries.items():z.writestr(name,raw)
    return data.getvalue()


class SourceRuntimeTest(unittest.TestCase):
    def test_current_product_bytes_and_exact_native_scope(self):
        base={name:b'MZoriginal' for name in stage.WINDOWS_NATIVE}
        base['trusted/Main.class']=b'current bytecode'
        base['META-INF/LICENSE']=b'current license'
        preview={name:b'MZsource' for name in stage.WINDOWS_NATIVE}
        preview.update({'trusted/Main.class':base['trusted/Main.class'],'META-INF/LICENSE':base['META-INF/LICENSE'],
                        'META-INF/jharness/source-native-preview.json':b'controlled metadata'})
        replacements={name:dict(sha256=stage.sha(b'MZsource')) for name in stage.WINDOWS_NATIVE}
        stage.validate_runtime(jar(base),jar(preview),replacements)
        for name in ['trusted/Main.class','META-INF/LICENSE',next(iter(stage.WINDOWS_NATIVE))]:
            with self.subTest(drift=name),self.assertRaises(ValueError):
                stage.validate_runtime(jar(base),jar(dict(preview,**{name:b'drift'})),replacements)
        with self.assertRaises(ValueError):stage.validate_runtime(jar(base),jar(dict(preview,extra=b'extra')),replacements)
        altered=dict(replacements);altered.pop(next(iter(altered)))
        with self.assertRaises(ValueError):stage.validate_runtime(jar(base),jar(preview),altered)


if __name__=='__main__':unittest.main()
