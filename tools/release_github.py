# -*- coding: utf-8 -*-
"""GitHub Release v3.5.0 发布（纯 urllib，无 curl 依赖）"""
import json, os, urllib.request

TOKEN = None
# 从 git credential 获取
import subprocess
cwd = r'D:/tmp/XiTing-sync'
r = subprocess.run(['git', 'credential', 'fill'],
                   input='protocol=https\nhost=github.com\n\n',
                   capture_output=True, text=True, cwd=cwd)
for line in r.stdout.splitlines():
    if line.startswith('password='):
        TOKEN = line.split('=', 1)[1]
        break

API = 'https://api.github.com/repos/ch1209498273/XiTing'
HDR = {'Authorization': f'token {TOKEN}', 'Content-Type': 'application/json',
       'User-Agent': 'XiTing-Release'}
UP_HDR = dict(HDR); UP_HDR['Content-Type'] = 'application/vnd.android.package-archive'

# 1. 删除旧 release
try:
    req = urllib.request.Request(f'{API}/releases/tags/v3.5.0', headers=HDR)
    with urllib.request.urlopen(req) as r:
        old = json.loads(r.read())
    req = urllib.request.Request(f'{API}/releases/{old["id"]}', headers=HDR, method='DELETE')
    urllib.request.urlopen(req)
    print('旧 release 已删')
except Exception as e:
    print(f'删旧: {e}')

# 2. 删除旧 tag
import subprocess
subprocess.run(['git', 'push', 'origin', ':refs/tags/v3.5.0'],
               capture_output=True, cwd=cwd)
subprocess.run(['git', 'tag', '-d', 'v3.5.0'], capture_output=True, cwd=cwd)
subprocess.run(['git', 'tag', 'v3.5.0'], capture_output=True, cwd=cwd)
subprocess.run(['git', 'push', 'origin', 'v3.5.0'], capture_output=True, cwd=cwd)
print('tag 已重建')

# 3. 创建 release
body_text = (
    "## v3.5.0 \u00b7 \u7cbe\u7075\u91cd\u753b + \u6027\u80fd\u4f18\u5316 + \u56fe\u9274 & streak\n\n"
    "### \u6027\u80fd\n"
    "- \ud83d\udce6 **APK 159KB**\uff08\u4ece 660KB \u5927\u5e45\u7f29\u51cf\uff09\n"
    "- \u7cbe\u7075\u6e32\u67d3\u6bcf\u5e27\u5206\u914d\u5f52\u96f6\uff0clint \u5168\u6e05\n\n"
    "### \u7cbe\u7075\n"
    "- \ud83c\udfa8 \u4e94\u5f62\u6001\u5168\u90e8\u91cd\u753b\n"
    "- \u6362\u80a4\u4ece\u8272\u76f8\u65cb\u8f6c\u6539\u4e3a\u7ed8\u5236\u53c2\u6570\u5316\n"
    "- \u7cbe\u7075\u56fe\u9274\u516d\u6b3e\u6362\u8272\u76ae\u80a4\n"
    "- \u8fde\u7eed\u542c\u5267\u5929\u6570 streak \u5c55\u793a\n\n"
    "### \u4fee\u590d\n"
    "- API 26/29 \u542f\u52a8\u5d29\u6e83\n"
    "- \u9000\u51fa\u786e\u8ba4\u6761\u5b9a\u4f4d\u5230\u60ac\u6d6e\u7403\u6b63\u65c1\n"
    "- \u5907\u4efd\u5b64\u513f\u884c\u81ea\u6108\n\n"
    "**\u4e0b\u8f7d**\uff1a\u89c1\u4e0b\u65b9 Assets\u3002"
)

release_data = json.dumps({
    "tag_name": "v3.5.0",
    "name": "v3.5.0 \u00b7 \u7cbe\u7075\u91cd\u753b & 159KB",
    "body": body_text,
    "draft": False,
    "prerelease": False
}).encode('utf-8')

req = urllib.request.Request(f'{API}/releases', data=release_data, headers=HDR, method='POST')
with urllib.request.urlopen(req) as r:
    rel = json.loads(r.read())
    rid = rel['id']
    print(f'Release: {rel["tag_name"]} id={rid}')

# 4. 上传 APK
apk = os.path.normpath(os.path.join(cwd, '..', '..', '..', '..', 
    'AI\u4efb\u52a1', 'zcode', '\u606f\u5c4f\u542c\u5267', 
    '\u606f\u5c4f\u542c\u5267-v3.5.0.apk'))
# 直接用私有仓库的构建产物
apk = r'D:\AI任务\zcode\息屏听剧\app\build\outputs\apk\release\app-release.apk'
print('上传:', apk, os.path.getsize(apk), 'bytes')

boundary = '----XiTingFormBoundary7MHl'
with open(apk, 'rb') as f:
    apk_data = f.read()

meta = (f'----{boundary}\r\n'
        f'Content-Disposition: form-data; name="file"; '
        f'filename="XiTing-v3.5.0.apk"\r\n'
        f'Content-Type: application/vnd.android.package-archive\r\n\r\n').encode()
tail = f'\r\n----{boundary}--\r\n'.encode()
upload_body = meta + apk_data + tail

upload_url = f'{API}/releases/{rid}/assets?name=XiTing-v3.5.0.apk'
req = urllib.request.Request(upload_url, data=upload_body, method='POST')
req.add_header('Authorization', f'token {TOKEN}')
req.add_header('Content-Type', 'application/vnd.android.package-archive')
with urllib.request.urlopen(req) as r:
    asset = json.loads(r.read())
    print(f'资产: {asset["name"]} {asset["size"]//1024}KB state={asset["state"]}')

# 5. mark latest
req = urllib.request.Request(f'{API}/releases/{rid}',
    data=json.dumps({"make_latest": "true"}).encode(),
    headers={**HDR, 'Content-Type': 'application/json'}, method='PATCH')
urllib.request.urlopen(req)

# 6. 验证
req = urllib.request.Request(f'{API}/releases/latest', headers=HDR)
with urllib.request.urlopen(req) as r:
    d = json.loads(r.read())
    print(f'\u2705 Latest: {d["tag_name"]} | {d["name"]} | assets: {[(a["name"], a["size"]//1024) for a in d["assets"]]}')
