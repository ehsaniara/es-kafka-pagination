# ElasticSearch Parallel Pagination by Kafka

By Using Kafka we can easily horizontally scale our application to do asynchronous pagination in ElasticSearch.

![ES-Kafka](./es-kafka-pagination.png)


Let’s say you have an ElasticSearch Index of 1,000,000 documents, and you need to run an operation on those documents. 

We already know how expensive the deep-paging in ElasticSearch is, especially index.max_result_window and doing Search ‘from:’.
```shell
GET /_search
{
  "from": 5,
  "size": 20,
  "query": {
    "match": {
      "user.id": "jay"
    }
  }
}
```

One way of overcoming the problem is to use **_search_after_**. In this case your process is becoming synchronous, which means you cannot call the second chunk of data without having the results from the first call, for example:

```shell
GET /_search
{
  "size": 10000,
  "query": {
    "match" : {
      "user.id" : "jay"
    }
  },
  "sort": [ 
    {"@timestamp": "asc"}
  ]
}
```

results:

```shell
{
  "took" : ...,
  "timed_out" : false,
  "_shards" : ...,
  "hits" : {
    "total" : ...,
    "max_score" : null,
    "hits" : [
      ...
      {
        "_source" : ...,
        "sort" : [                                
          4098435132000
        ]
      }
    ]
  }
}
```

As you can see, the results give you a sort value (**_4098435132000_**) where you can use in your second call-in “search_after” to get the next chunk as:

```shell
GET /_search
{
  "size": 10000,
  "query": {
    "match" : {
      "user.id" : "jay"
    }
  },
  "sort": [
    {"@timestamp": "asc"}
  ],
  "search_after": [                                
    4098435132000
  ]
}
```
then get the next sort value and use it in your next call.

This process is sequentially asynchronous, which means to get through 1,000,000 documents you need to call ES 1,000,000/10,000=**_100_** times one after the other.

### What can go wrong?
- let’s say your application dies or runs out of memory during these calls
- how to know where to start after it failed
- maybe this process is very slow for your application use-case


### Solution By using “Slice”

One call to ES to get the count of the documents (which in this case is 1M), then create a Kafka producer to put the sequences (0–100) into your Kafka topic and define your partition size as the number of your consumer app. Let’s say you have 10 consumer applications.

Create a Kafka consumer app, which is calling Elastic Search as:

```shell
GET /_search?scroll=1m
{
  "slice": {
     "id":0,
     "max":100  
  },
  "size": 10000,
  "sort": [
    {"@timestamp": "asc"}
  ]
}
```
The consumer gets the sequence number as a parameter and replaces it with the id. As long as all consumers have the same consumer “group.id” they can process these calls in parallel and have a resilient framework.

# Implementation with Java/Spring Cloud

Let's run your Elastic Search and Kafka in docker-compose by running the following command:

```shell
# Elastic Search
docker-compose -f docker-compose-es.yml up -d
# Kafka
docker-compose -f docker-compose-kafka.yml up -d
```

you can find the [docker-compose-es.yml](https://github.com/ehsaniara/es-kafka-pagination/blob/master/docker-compose-es.yml) and [docker-compose-kafka.yml](https://github.com/ehsaniara/es-kafka-pagination/blob/master/docker-compose-kafka.yml) in my Github account.

The producer first calls ES to get the count of the documents in the index, then divides the count by 500, which will be the number of documents you expect to be retrieved in every ES call.


```java
public void paginationProcess() {
    //call to ES and get the total count
    Response response = restHighLevelClient.getLowLevelClient()
        .performRequest(new Request("GET", String.format("%s/_count", INDEX_NAME)));

    ResponseCountDto responseCountDto = objectMapper.readValue(EntityUtils.toString(response.getEntity()), ResponseCountDto.class);

    log.debug("responseCountDto: {}", responseCountDto.getCount());
    
    int max = responseCountDto.getCount() / 500;
    log.debug("count: {} , max: {}", responseCountDto.getCount(), max);

    //producer
    IntStream.range(0, max).forEach(i -> paginationBinder.paginationOut()//
        .send(MessageBuilder.withPayload(//
            PaginationDto.builder()//
            .id(i)//slice id
            .max(max)// let say i want to have page size of 500 then: count / 500
            .build())//
            .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON).build()));
}
```

Then the Consumer receives the slice number as a parameter. In case of any error in the consumer method, we retry it 5 times, and then it puts the message into to DLQ method.

```shell
@StreamListener(PaginationBinder.PAGINATION_IN)
public void paginationProcess(@Payload PaginationDto paginationDto) {
    // Call ES
    log.debug("paginationProcess: {}", paginationDto);
    try {
        Request request = new Request("GET", String.format("%s/_search?scroll=1m", INDEX_NAME));
        //sorted by localDateTime and slice by id and max as parameters
        request.setJsonEntity(String.format("{\"slice\":{\"id\":%s,\"max\":%s},\"size\":10000,\"sort\":[{\"localDateTime\":\"asc\"}]}", paginationDto.getId(), paginationDto.getMax()));
        Response response = restHighLevelClient.getLowLevelClient().performRequest(request);
        //do something with the response ...
        log.debug("response: {}", response);
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

**_Note:_** Assume no new documents are inserting to the result set or at least within that period. Otherwise, we lose the consistency of the result during the pagination.


application.yml file
```yaml
spring:
  application:
    name: es-pagination

  cloud.stream:
    bindings:

      pagination-out:
        destination: pagination
        producer:
          partition-count: 10
      pagination-in:
        destination: pagination
        group: ${spring.application.name}.pagination-group
        consumer:
          maxAttempts: 5
      pagination-in-dlq:
        destination: paginationDLQ
        group: ${spring.application.name}.pagination-group

    kafka:
      streams:
        bindings:
          pagination-in:
            consumer:
              enableDlq: true
              dlqName: paginationDLQ
              autoCommitOnError: true
              autoCommitOffset: true
        binder:
          autoAddPartitions: true
          min-partition-count: 10
          configuration:
            commit.interval.ms: 100
            default.key.serde: org.apache.kafka.common.serialization.Serdes$StringSerde
            default.value.serde: org.apache.kafka.common.serialization.Serdes$StringSerde
```

#### Avoid toasting the memory
If the number of slices is bigger than the number of shards, the slice filter will become very slow on the first calls. It has a complexity of O(N) and a memory cost which equals N bits per slice where N is the total number of documents in the shard. After a few calls, the filter should be cached and subsequent calls should be faster, but you should limit the number of sliced queries you perform in parallel to avoid the memory explosion.

