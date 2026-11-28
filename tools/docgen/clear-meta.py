"""DOCX의 작성자·마지막 저장한 사람·회사 값을 비운다. 다른 부분은 그대로 옮긴다.

사용: python3 clear-meta.py <파일.docx> ...
"""
import re
import sys
import zipfile

# 값만 비우고 요소는 남김. 요소가 없으면 그대로 둠
CLEAR = {
    "docProps/core.xml": ["dc:creator", "cp:lastModifiedBy"],
    "docProps/app.xml": ["Company", "Manager"],
}


def clear(xml, tags):
    for tag in tags:
        t = re.escape(tag)
        xml = re.sub(r"<%s(\s[^>]*)?>.*?</%s>" % (t, t), lambda m: "<%s%s/>" % (tag, m.group(1) or ""), xml, flags=re.S)
    return xml


def rewrite(path):
    with zipfile.ZipFile(path) as z:
        items = [(i, z.read(i.filename)) for i in z.infolist()]
    with zipfile.ZipFile(path, "w") as z:
        for info, data in items:
            tags = CLEAR.get(info.filename)
            if tags:
                data = clear(data.decode("utf-8"), tags).encode("utf-8")
            z.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED)


for p in sys.argv[1:]:
    rewrite(p)
    print("저장 정보 비움:", p)
