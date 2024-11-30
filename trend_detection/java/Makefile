jbuild:
	mvn clean package

jrun:
	java -jar target/trend-detection-1.0-SNAPSHOT.jar -l 100

dbuild:
	docker-compose build --no-cache

drun:
	docker-compose up -d

connect:
	docker-compose exec -ti taskmanager-1 /bin/bash

frun:
	docker-compose exec taskmanager-1 java -jar /opt/flink/usrlib/trend-detection-1.0-SNAPSHOT.jar -l 10 -p /opt/flink/data/messages_rows.json
