# Returns a list of all tags sorted by the number of tags

import re
import json

import requests
from bs4 import BeautifulSoup

indices = ['123'] + [chr(ord('a')+i) for i in range(26)]
tags = dict()

count_regex = re.compile(r".+\((\d+)\)$")

for index in indices:
    url = f'https://hitomi.la/alltags-{index}.html'

    soup = BeautifulSoup(requests.get(url).text, 'html.parser')

    for item in soup.select('.content li'):
        tag = item.a.text
        count = int(count_regex.match(item.text).group(1))

        tags[tag] = count

tag_regex = re.compile(r".+:(.+)$")
def clean(tag):
    match = tag_regex.match(tag)

    if match:
        return match.group(1)
    else:
        return tag

temp = dict()
for k, v in tags.items():
    tag = clean(k)

    if tag in temp:
        if v > temp[tag]:
            temp[tag] = v
    else:
        temp[tag] = v

tags = sorted(temp, key=temp.get, reverse=True)

print(json.dumps(tags, indent=4))