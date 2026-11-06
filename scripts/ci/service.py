"""GitHub-runner-only MySQL. Forks get a fresh random credential, never repository secrets."""
import os,re,secrets,subprocess,sys,time
IMAGE='mysql:8.4.13';BIND='127.0.0.1:13308:3306'
def docker(*args,env=None):
    return subprocess.check_output(['docker',*args],env=env,text=True,stderr=subprocess.PIPE).strip()
def main(mode):
    assert os.environ.get('GITHUB_ACTIONS')=='true','CI service only'
    run=os.environ['GITHUB_RUN_ID'];assert run.isdigit()
    if mode=='start':
        root=os.environ.get('MYSQL_ROOT_PASSWORD') or secrets.token_hex(32)
        assert '\n' not in root and '\r' not in root
        print('::add-mask::'+root,flush=True)
        env=os.environ.copy();env['MYSQL_ROOT_PASSWORD']=root;container=None
        try:
            container=docker('run','--detach','--label','leaseguard.ci.run='+run,'--env','MYSQL_ROOT_PASSWORD','--publish',BIND,'--health-cmd','mysqladmin ping --silent','--health-interval=2s','--health-timeout=5s','--health-retries=60',IMAGE,env=env)
            assert re.fullmatch(r'[a-f0-9]{64}',container),'Unexpected container identity'
            deadline=time.monotonic()+150
            while True:
                status=docker('inspect','--format','{{.State.Health.Status}}',container)
                if status=='healthy':break
                if status=='unhealthy' or time.monotonic()>deadline:raise RuntimeError('MySQL health deadline')
                time.sleep(.5)
            with open(os.environ['GITHUB_ENV'],'a',encoding='utf-8') as out:
                out.write('MYSQL_CONTAINER='+container+'\nMYSQL_ROOT_PASSWORD='+root+'\n')
        except Exception:
            if container and re.fullmatch(r'[a-f0-9]{64}',container):docker('rm','--force',container)
            raise
        print('Loopback-only MySQL 8.4 healthy; credential masked; full database tests required')
    elif mode=='stop':
        container=os.environ.get('MYSQL_CONTAINER')
        if not container:return
        assert re.fullmatch(r'[a-f0-9]{64}',container)
        assert docker('inspect','--format','{{index .Config.Labels "leaseguard.ci.run"}}',container)==run,'Refuse another job container'
        docker('rm','--force',container)
    else:raise ValueError('start or stop required')
if __name__=='__main__':main(sys.argv[1])
