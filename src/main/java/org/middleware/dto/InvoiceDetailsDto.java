package org.middleware.dto;

import java.util.List;

public class InvoiceDetailsDto {

    private String uid;
    private String status;
    private String createdAt;
    private List<ItemDto> items;
    private Object extra;
}
