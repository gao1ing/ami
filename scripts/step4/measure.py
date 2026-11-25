"""Run against the app connected to ami_bench. Sequential HTTP, 5 warmups, 40 samples."""
import argparse
import json
from math import ceil
from statistics import median
from time import perf_counter
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser()
parser.add_argument("--base-url", default="http://127.0.0.1:8080")
args = parser.parse_args()
payload = {"from": "2026-11-07T10:00:00Z", "to": "2026-11-07T10:15:00Z",
           "minKwh": 1, "page": 1, "size": 20}
request = Request(args.base_url.rstrip("/") + "/curve-readings/search",
                  json.dumps(payload).encode(), {"Content-Type": "application/json"})
samples = []
for run in range(45):
    start = perf_counter()
    with urlopen(request, timeout=30) as response:
        body = response.read()
    elapsed = (perf_counter() - start) * 1000
    result = json.loads(body)
    expected = {f"BENCH-{n:03}": (n + 40) % 40 / 10 for n in range(100) if (n + 40) % 40 >= 10}
    if (len(result["items"]) != 20 or not result["hasNext"]
            or len({item["deviceNo"] for item in result["items"]}) != 20
            or any(item["sampleTime"] != payload["from"] or item["curveType"] != "IMPORT_KWH"
                   or item["value"] != expected.get(item["deviceNo"]) for item in result["items"])):
        raise ValueError("Unexpected benchmark fixture or response")
    if run == 0:
        baseline = result
    elif result != baseline:
        raise ValueError("Page changed during measurement")
    if run >= 5:
        samples.append(elapsed)
print(f"HTTP n={len(samples)}, concurrency=1, median={median(samples):.2f} ms, "
      f"P95={sorted(samples)[ceil(.95 * len(samples)) - 1]:.2f} ms")
