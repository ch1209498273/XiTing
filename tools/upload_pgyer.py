# -*- coding: utf-8 -*-
"""蒲公英自动上传（apiv2）：python tools/upload_pgyer.py <apk路径> [更新说明]"""
import sys, json, urllib.request, urllib.error, uuid, os

# Windows 控制台默认 GBK，打印「✓」会 UnicodeEncodeError ——
# 坑在于它发生在**上传成功之后**，于是「成功」被报成「失败」，很容易误判。
try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

API_KEY = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '物料', '发布', 'pgyer_api_key.txt')).read().strip()
SHORT_URL = 'xipingtingju'
URL = 'https://www.pgyer.com/apiv2/app/upload'

def upload(apk, desc):
    boundary = uuid.uuid4().hex
    lines = []
    def field(name, value):
        lines.append(('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n%s\r\n' % (boundary, name, value)).encode())
    field('_api_key', API_KEY)
    field('buildShortcutUrl', SHORT_URL)
    field('buildUpdateDescription', desc)
    field('buildInstallType', '1')  # 公开安装
    fname = os.path.basename(apk)
    lines.append(('--%s\r\nContent-Disposition: form-data; name="file"; filename="%s"\r\n'
                  'Content-Type: application/vnd.android.package-archive\r\n\r\n' % (boundary, fname)).encode())
    body = b''.join(lines) + open(apk, 'rb').read() + ('\r\n--%s--\r\n' % boundary).encode()
    req = urllib.request.Request(URL, data=body, method='POST')
    req.add_header('Content-Type', 'multipart/form-data; boundary=%s' % boundary)
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            d = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        print('HTTP', e.code, e.read().decode()[:300]); sys.exit(1)
    if d.get('code') == 0:
        b = d['data']
        print('[OK] 上传成功')
        print('  版本:', b.get('buildVersion'), '(', b.get('buildBuildVersion'), ')')
        print('  短链: https://www.pgyer.com/', b.get('buildShortcutUrl'))
        print('  更新时间:', b.get('buildUpdated'))
    else:
        print('上传失败:', d.get('message'), '| code:', d.get('code'))
        sys.exit(1)

if __name__ == '__main__':
    apk = sys.argv[1]
    desc = sys.argv[2] if len(sys.argv) > 2 else '版本更新'
    upload(apk, desc)
