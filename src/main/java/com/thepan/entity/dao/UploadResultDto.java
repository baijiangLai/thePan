package com.thepan.entity.dao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

@Data
public class UploadResultDto implements Serializable {
    private String fileId;
    private String status;
}
