# ElasticSearch High Volume Pagination in Parallel By Kafka

By Using Kafka we can easily horizontally scale our application to do asynchronous pagination in ElasticSearch.


![ES-Kafka](./es-kafka-pagination.png)


Let’s say you have an ElasticSearch Index of 1,000,000 documents and you need to run an operation on those documents. We already know how expensive the deep-paging in ElasticSearch is, especially _**index.max_result_window**_ and doing Search ‘from:’.

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

One way of overcoming the problem is to use search_after. In this case your process is becoming synchronous, which means you cannot call the second chunk of data without having the results from the first call, for example:

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

and results:

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

As you can see, the results give you a sort value (4098435132000) where you can use in your second call-in “search_after” to get the next chunk as:

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
and get the next sort value and use it in your next call.

This process is sequentially asynchronous, which means to get through 1,000,000 documents you need to call ES 1,000,000/10,000=100 times one after the other.

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

The producer first calls ES to get the count of the documents in the index. then divide the count by 500 which will be the number of documents you expect to be retrieved in every ES calls.


```java
public void paginationProcess() {
        log.debug("paginationProcess called");
        //call to ES and get the total count
        Response response = restHighLevelClient.getLowLevelClient()
                .performRequest(new Request("GET", String.format("%s/_count", INDEX_NAME)));

        ResponseCountDto responseCountDto = objectMapper.readValue(EntityUtils.toString(response.getEntity()), ResponseCountDto.class);

        log.debug("responseCountDto: {}", responseCountDto.getCount());

        //let say i want to have page size of 500 then count / 500

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

Consumer: which is receiving the slice number as a parameter. in-case of any error we retry it 5 times then put the message int to DLQ method.

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

