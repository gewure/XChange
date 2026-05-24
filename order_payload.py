import urllib.request
import json
url = "https://docs.polymarket.com/clob/reference/create-order"
req = urllib.request.Request(url)
try:
    with urllib.request.urlopen(req) as response:
        print(response.read().decode())
except Exception as e:
    print(e)
