"""Validate CI credential/cleanup boundaries without running Docker on a developer's machine."""
import contextlib,io,os,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import service
class ServiceSafetyTest(unittest.TestCase):
    def run_start(self,provided):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'env';calls=[];container='a'*64
            def docker(*args,env=None):
                calls.append((args,env))
                return container if args[0]=='run' else 'healthy'
            with patch.dict(os.environ,{'GITHUB_ACTIONS':'true','GITHUB_RUN_ID':'123','GITHUB_ENV':str(path),'MYSQL_ROOT_PASSWORD':provided},clear=True),patch.object(service,'docker',side_effect=docker),contextlib.redirect_stdout(io.StringIO()) as output:
                service.main('start')
            values=dict(line.split('=',1) for line in path.read_text().splitlines());root=values['MYSQL_ROOT_PASSWORD']
            self.assertTrue(root);self.assertIn('::add-mask::'+root,output.getvalue())
            self.assertTrue(all(root not in ' '.join(args) for args,_ in calls))
            self.assertEqual(root,calls[0][1]['MYSQL_ROOT_PASSWORD'])
            self.assertIn('127.0.0.1:13308:3306',calls[0][0]);self.assertIn('mysql:8.4.13',calls[0][0])
            return root
    def test_fork_without_secret_gets_random_256_bit_root(self):
        a=self.run_start('');b=self.run_start('');self.assertEqual(len(a),64);self.assertNotEqual(a,b)
    def test_main_supplied_secret_is_only_in_environment(self):
        self.assertEqual(self.run_start('test-only-sentinel'),'test-only-sentinel')
    def test_local_execution_refused(self):
        with patch.dict(os.environ,{},clear=True),patch.object(service,'docker') as docker:
            with self.assertRaises(AssertionError):service.main('start')
            docker.assert_not_called()
    def test_cleanup_refuses_other_job(self):
        with patch.dict(os.environ,{'GITHUB_ACTIONS':'true','GITHUB_RUN_ID':'123','MYSQL_CONTAINER':'a'*64},clear=True),patch.object(service,'docker',return_value='999') as docker:
            with self.assertRaises(AssertionError):service.main('stop')
            self.assertEqual(docker.call_count,1)
if __name__=='__main__':unittest.main()
