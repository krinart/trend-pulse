jbuild:
	mvn clean package -Dmaven.test.skip=true

jrun:
	java -jar target/trend-detection-1.0-SNAPSHOT.jar -l -1

dbuild:
	docker-compose build --no-cache

drun:
	docker-compose up -d

connect:
	docker-compose exec -ti taskmanager-1 /bin/bash

frun:
	docker-compose exec taskmanager-1 java -jar /opt/flink/usrlib/trend-detection-1.0-SNAPSHOT.jar -l -1 -p /opt/flink/data/messages_rows.json

frunc:
	docker exec -it java-jobmanager-1 flink run /opt/flink/usrlib/trend-detection-1.0-SNAPSHOT.jar -p /opt/flink/data/messages_rows_with_id_v26_500.json -l -1 -o /tmp/output
