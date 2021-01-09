package com.ehsaniara.espagination.binder;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

/**
 * Jay Ehsaniara
 * https://github.com/ehsaniara
 */
public interface PaginationBinder {

    String PAGINATION_IN = "pagination-in";

    String PAGINATION_OUT = "pagination-out";

    String PAGINATION_IN_DLQ = "pagination-in-dlq";

    @Output(PAGINATION_OUT)
    MessageChannel paginationOut();

    @Input(PAGINATION_IN)
    SubscribableChannel paginationIn();

    @Input(PAGINATION_IN_DLQ)
    SubscribableChannel paginationInDLQ();

}
