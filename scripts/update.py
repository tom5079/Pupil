import json
import sys

tags = {tag: u'' for tag in json.loads(''.join(sys.stdin.readlines()))}

try:
    with open(sys.argv[1], encoding='utf-8') as f:    
        lang = json.load(f)
except:
    lang = dict()

for tag in tags:
    if tag in lang and lang[tag]:
        tags[tag] = lang[tag]

with open(sys.argv[1], 'w', encoding='utf-8') as f:
    json.dump(tags, f, indent=4, ensure_ascii=False)