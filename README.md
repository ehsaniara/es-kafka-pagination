# ElasticSearch Pagination by using Kafka

![ES-Kafka](es-kafka-pagination.png)
### Start

Let's first start the ElasticSearch in docker:
```shell
docker-compose -f docker-compose-es.yml up -d
```

Then start your kafka cluster
```shell
docker-compose -f docker-compose-kafka.yml up -d
```
This is just a simple example for kafka docker-compose

Now you can start the application, you can check it by calling [localhost:8080](http://localhost:8080) from your browser.


Let's insert some dummy data in your index, by running following line after you start the application.

```shell
curl http://localhost:8080/test/init
```

Start the pagination by calling the kafka producer

```shell
curl http://localhost:8080/test
```
If you check the application log you ll notice the asynchronous call into ElasticSearch by multiple consumers.