import urllib.request
import json

url = "https://clob.polymarket.com/book?token_id=73470541315377973562501025254719659796416871135081220986683321361000395461644"
req = urllib.request.Request(url)
try:
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        print(json.dumps(data, indent=2))
except Exception as e:
    print(e)
