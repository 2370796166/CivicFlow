"""Set fictional names on the named ordinary demo accounts; preserve login and roles."""
import json
import subprocess
from pathlib import Path

NAMES = {
    'demo_user': '陈嘉明', 'gz_demo_user2': '林晓彤', 'gz_demo_user3': '张志远',
    'gz_demo_disabled': '刘思怡', 'docker_6cc6ef76_user': '周梓涵',
    'docker_36bc8e97_user': '黄俊杰',
}
ROOT = Path(__file__).resolve().parents[2]

def sql(query):
    result = subprocess.run(['docker','exec','-i','civicflow-mysql','sh','-lc',
        'mysql --default-character-set=utf8mb4 --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" -N'],
        input=query, text=True, encoding='utf-8', capture_output=True)
    if result.returncode: raise RuntimeError('Demo name update failed')
    return result.stdout.strip()

if __name__ == '__main__':
    usernames=','.join("'"+name+"'" for name in NAMES)
    select=f"SELECT id,username,display_name FROM civicflow_auth.sys_user WHERE username IN ({usernames}) AND deleted=0 ORDER BY username;"
    backup=ROOT/'target/diagnostics/guangzhou-names-before.json'
    backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists(): backup.write_text(json.dumps(sql(select).splitlines(),ensure_ascii=False,indent=2),encoding='utf-8')
    updates=[]
    for username,name in NAMES.items():
        value="CONVERT(0x"+name.encode('utf-8').hex()+" USING utf8mb4)"
        updates.append(f"UPDATE civicflow_auth.sys_user SET display_name={value},version=version+1 WHERE username='{username}' AND deleted=0 AND NOT(display_name <=> {value});")
    sql('START TRANSACTION;\n'+'\n'.join(updates)+'\nCOMMIT;')
    print(sql(select))
