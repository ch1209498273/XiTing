"""真机验收驱动：坐标一律从 uiautomator dump 反查，不硬编码。
用法：python drive.py <动作> [参数]
动作：nav <页签文字> / tap <文字或resource-id后缀> / dump <输出名> / shot <输出名> / swipe
"""
import re, subprocess, sys, os, time
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
ADB = r"C:/Users/MR/AppData/Local/Android/Sdk/platform-tools/adb.exe"
SERIAL = "3B164H006DY00000"
E = os.path.dirname(os.path.abspath(__file__))

def sh(*a):
    return subprocess.run([ADB, '-s', SERIAL] + list(a), capture_output=True).stdout.decode('utf-8', 'replace')

def nodes():
    sh('shell', 'uiautomator', 'dump', '/sdcard/p.xml')
    raw = sh('exec-out', 'cat', '/sdcard/p.xml')
    return list(ET.fromstring(raw).iter('node'))

def center(match):
    for n in nodes():
        a = n.attrib
        rid = a.get('resource-id', '')
        if (a.get('text') or '') == match or rid.endswith('/' + match) or match in (a.get('text') or ''):
            m = [int(x) for x in re.findall(r'\d+', a['bounds'])]
            return (m[0] + m[2]) // 2, (m[1] + m[3]) // 2
    return None

def tap(match, wait=3):
    xy = center(match)
    if not xy:
        print('MISS', match); return False
    sh('shell', 'input', 'tap', str(xy[0]), str(xy[1])); time.sleep(wait)
    print('tap', match, '->', xy); return True

def texts():
    return [(n.attrib.get('text') or '') for n in nodes() if n.attrib.get('text')]

def shot(name):
    png = subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p'], capture_output=True).stdout
    open(os.path.join(E, name + '.png'), 'wb').write(png)
    print('saved', name + '.png', len(png), 'bytes')

def dump(name):
    sh('shell', 'uiautomator', 'dump', '/sdcard/d.xml')
    raw = sh('exec-out', 'cat', '/sdcard/d.xml')
    open(os.path.join(E, name + '.xml'), 'w', encoding='utf-8').write(raw)
    print('saved', name + '.xml', len(raw), 'chars')

def swipe(x1, y1, x2, y2):
    sh('shell', 'input', 'swipe', str(x1), str(y1), str(x2), str(y2), '450'); time.sleep(2)

if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'tap': tap(sys.argv[2])
    elif cmd == 'shot': shot(sys.argv[2])
    elif cmd == 'dump': dump(sys.argv[2])
    elif cmd == 'texts': print('\n'.join(texts()))
    elif cmd == 'swipe': swipe(*[int(x) for x in sys.argv[2:6]])
