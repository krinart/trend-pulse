build:
	mvn clean package

run:
	java -jar target/trend-detection-1.0-SNAPSHOT.jar $(filter-out $@,$(MAKECMDGOALS))

%:
	@: