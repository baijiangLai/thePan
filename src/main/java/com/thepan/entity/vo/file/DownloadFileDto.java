package com.thepan.entity.vo.file;

import lombok.Data;

@Data
public class DownloadFileDto {
    private String downloadCode;
    private String fileId;
    private String fileName;
    private String filePath;
}
