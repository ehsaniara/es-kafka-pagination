package com.ehsaniara.espagination.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Jay Ehsaniara
 * https://github.com/ehsaniara
 */
@ToString
@Builder
@Setter
@Getter
public class PaginationDto {
    private int id;
    private int max;
}
