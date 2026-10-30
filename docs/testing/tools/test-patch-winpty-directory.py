"""Atomic patch rejection controls, independent of external source availability."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('extension',Path(__file__).with_name('patch-winpty-directory.py'))
extension=importlib.util.module_from_spec(spec);spec.loader.exec_module(extension)


class DirectoryPatchTest(unittest.TestCase):
    def fixture(self,root):
        files={
            'src/shared/AgentMsg.h':'        GetConsoleProcessList,',
            'src/agent/Agent.h':'    void handleGetConsoleProcessListPacket(ReadBuffer &packet);',
            'src/agent/Agent.cc':'#include "Agent.h"\n    case AgentMsg::GetConsoleProcessList:\nvoid Agent::handleGetConsoleProcessListPacket(ReadBuffer &packet)',
            'src/include/winpty.h':'WINPTY_API void winpty_free(winpty_t *wp);',
            'src/libwinpty/winpty.cc':'WINPTY_API void winpty_free(winpty_t *wp) {',
        }
        for name,text in files.items():
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(text.encode())
        return {name:hashlib.sha256(text.encode()).hexdigest() for name,text in files.items()}

    def test_hash_or_ambiguous_last_anchor_cannot_partially_modify_source(self):
        for ambiguous in [False,True]:
            with tempfile.TemporaryDirectory() as temp:
                root=Path(temp);expected=self.fixture(root);last=root/'src/libwinpty/winpty.cc'
                last.write_bytes(last.read_bytes()*2)
                if ambiguous:expected['src/libwinpty/winpty.cc']=hashlib.sha256(last.read_bytes()).hexdigest()
                before={name:(root/name).read_bytes() for name in expected}
                with patch.object(extension,'EXPECTED',expected),self.assertRaises(ValueError):extension.apply(root)
                self.assertEqual(before,{name:(root/name).read_bytes() for name in expected})
                self.assertFalse((root/'src/agent/jh-owned-directory.h').exists())

    def test_patch_is_bound_and_cannot_be_applied_twice(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);expected=self.fixture(root)
            with patch.object(extension,'EXPECTED',expected):result=extension.apply(root)
            self.assertEqual(5,len(result['changes']))
            self.assertIn(b'jhOwnedChildDirectory(m_childProcess', (root/'src/agent/Agent.cc').read_bytes())
            with self.assertRaises(ValueError):extension.apply(root)


if __name__=='__main__':unittest.main()
