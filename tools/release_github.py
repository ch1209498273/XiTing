# -*- coding: utf-8 -*-
"""
GitHub Release 发布（通用版，取代写死版本的 release_github.py）。

用法：
    python tools/release_github.py 3.6.0 "更新说明第一行" ["正文..."]

做的事：
  1. 打 tag（已存在则先删本地+远端）
  2. 建 Release
  3. 上传 release APK 作为资产
  4. 标记为 latest
  5. 回读校验

不硬编码 cwd：之前那个脚本写死了 D:/tmp/XiTing-sync，
换台机器/换个目录就直接跑不动。
"""
import json
import os
import subprocess
import sys
import urllib.parse
import urllib.request

# Windows 控制台默认 GBK：打印「✅」会 UnicodeEncodeError。
# 坑在于它发生在**全部工作完成之后**，「成功」被报成异常。
try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

REPO = 'ch1209498273/XiTing'
API = 'https://api.github.com/repos/' + REPO
APK = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), '..',
    'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk'))


def token():
    r = subprocess.run(['git', 'credential', 'fill'],
                       input='protocol=https\nhost=github.com\n\n',
                       capture_output=True, text=True)
    for line in r.stdout.splitlines():
        if line.startswith('password='):
            return line.split('=', 1)[1]
    raise SystemExit('✗ 拿不到 GitHub token（git credential fill 没返回 password=）')


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    ver = sys.argv[1]
    name = sys.argv[2] if len(sys.argv) > 2 else 'v' + ver
    body = sys.argv[3] if len(sys.argv) > 3 else ''
    tag = 'v' + ver
    tk = token()
    hdr = {'Authorization': 'token ' + tk, 'Content-Type': 'application/json',
           'User-Agent': 'XiTing-Release'}

    if not os.path.exists(APK):
        raise SystemExit('✗ 找不到 APK：%s\n  先跑 gradle assembleRelease' % APK)
    size = os.path.getsize(APK)
    print('APK: %s (%.1f KB)' % (APK, size / 1024))

    # --- 1. tag：远端有就先删（v3.5.0 那次就是这么重建的）---
    subprocess.run(['git', 'tag', '-d', tag], capture_output=True)
    subprocess.run(['git', 'push', 'origin', ':refs/tags/' + tag], capture_output=True)
    r = subprocess.run(['git', 'tag', tag], capture_output=True, text=True)
    if r.returncode != 0:
        print('  (tag 已存在，复用)')
    r = subprocess.run(['git', 'push', 'origin', tag], capture_output=True, text=True)
    print('tag %s -> %s' % (tag, '已推送' if r.returncode == 0 else r.stderr.strip()[:80]))

    # --- 2. 删同名旧 release（tag 重推后旧的会变孤儿）---
    try:
        req = urllib.request.Request('%s/releases/tags/%s' % (API, tag), headers=hdr)
        with urllib.request.urlopen(req) as resp:
            old = json.loads(resp.read())
        req = urllib.request.Request('%s/releases/%s' % (API, old['id']),
                                     headers=hdr, method='DELETE')
        urllib.request.urlopen(req)
        print('旧 release 已删')
    except Exception:
        pass

    # --- 3. 建 release ---
    data = json.dumps({'tag_name': tag, 'name': name, 'body': body,
                       'draft': False, 'prerelease': False}).encode('utf-8')
    req = urllib.request.Request(API + '/releases', data=data, headers=hdr, method='POST')
    with urllib.request.urlopen(req) as resp:
        rel = json.loads(resp.read())
    rid = rel['id']
    print('Release: %s (id=%s)' % (rel['tag_name'], rid))

    # --- 4. 上传 APK ---
    with open(APK, 'rb') as f:
        blob = f.read()
    asset_name = 'XiTing-%s.apk' % ver
    # ⚠ 两个容易踩的点：
    # 1. 上传端点是 **uploads.github.com**，不是 api.github.com。
    #    用错会得到 404（路径不存在），而不是 403 —— 很容易误判成权限问题。
    # 2. 查询串要 urlencode：资产名可含点号等字符。
    q = urllib.parse.urlencode({'name': asset_name})
    url = 'https://uploads.github.com/repos/%s/releases/%s/assets?%s' % (REPO, rid, q)
    req = urllib.request.Request(url, data=blob, method='POST')
    req.add_header('Authorization', 'token ' + tk)
    req.add_header('Content-Type', 'application/vnd.android.package-archive')
    req.add_header('X-GitHub-Api-Version', '2022-11-28')
    with urllib.request.urlopen(req, timeout=300) as resp:
        asset = json.loads(resp.read())
    print('资产: %s %dKB state=%s' % (asset['name'], asset['size'] // 1024, asset['state']))

    # --- 5. 标记 latest ---
    req = urllib.request.Request('%s/releases/%s' % (API, rid),
                                 data=json.dumps({'make_latest': 'true'}).encode(),
                                 headers=hdr, method='PATCH')
    urllib.request.urlopen(req)

    # --- 6. 校验 ---
    req = urllib.request.Request(API + '/releases/latest', headers=hdr)
    with urllib.request.urlopen(req) as resp:
        d = json.loads(resp.read())
    print('\n[OK] latest = %s | %s' % (d['tag_name'], d['name']))
    print('   https://github.com/%s/releases/tag/%s' % (REPO, d['tag_name']))
    for a in d['assets']:
        print('   资产 %s %dKB' % (a['name'], a['size'] // 1024))


if __name__ == '__main__':
    main()
