package com.ehsaniara.espagination.service;

import com.ehsaniara.espagination.binder.PaginationBinder;
import com.ehsaniara.espagination.model.PaginationDto;
import com.ehsaniara.espagination.model.RandomIndex;
import com.ehsaniara.espagination.model.ResponseCountDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Jay Ehsaniara
 * https://github.com/ehsaniara
 */
@Slf4j
@Service
@AllArgsConstructor
public class KafkaService {

    private final PaginationBinder paginationBinder;
    private final RestHighLevelClient restHighLevelClient;
    private final ObjectMapper objectMapper;

    static String INDEX_NAME = "random-data";

    @SneakyThrows
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

    @StreamListener(PaginationBinder.PAGINATION_IN_DLQ)
    public void paginationProcessDlQ(@Payload Message<PaginationDto> dtoMessage) {
        log.error("paginationProcessDlQ msg: {}", dtoMessage.getPayload().toString());
    }

    //adding some dummy data into the index (random-data)
    public void initEs() {
        IntStream.range(0, 100_000).parallel().forEach(i -> {
            IndexRequest indexRequest = new IndexRequest(INDEX_NAME);
            try {
                log.debug("i: {}", i);
                indexRequest.source(objectMapper.writeValueAsString(RandomIndex.builder()
                        .uuid(UUID.randomUUID())
                        .localDateTime(LocalDateTime.now())
                        .build()), XContentType.JSON);
                restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

}
