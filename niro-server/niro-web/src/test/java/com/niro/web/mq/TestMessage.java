package com.niro.web.mq;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TestMessage {
    private String id;
    private String content;
    private LocalDateTime timestamp;
    private String orderNo;
}
