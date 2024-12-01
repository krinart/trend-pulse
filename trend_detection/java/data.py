import json
import pandas as pd

data = json.load(open("data/trend_messages_v25.json"))
with open("java/data/messages_rows_with_id.json", "a") as f:
	for row in data:
		f.write(json.dumps(row) + "\n")


