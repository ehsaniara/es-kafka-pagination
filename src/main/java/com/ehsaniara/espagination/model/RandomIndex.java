package com.ehsaniara.espagination.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Jay Ehsaniara
 * https://github.com/ehsaniara
 */
@ToString
@Builder
@Setter
@Getter
public class RandomIndex {
    private UUID uuid;
    private LocalDateTime localDateTime;
}
