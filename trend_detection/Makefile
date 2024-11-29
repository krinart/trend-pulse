build:
	docker-compose build --no-cache

connect:
	docker-compose exec jobmanager /bin/bash

run:
	docker-compose exec jobmanager /opt/venv/bin/python run_flink.py

runc:
	docker-compose exec jobmanager flink run -py run_flink.py
