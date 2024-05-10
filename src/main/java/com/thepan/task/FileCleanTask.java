package com.thepan.task;


import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.enums.FileDelFlagEnums;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.service.FileInfoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FileCleanTask {

    @Resource
    private FileInfoService fileInfoService;

    @Scheduled(fixedDelay = 1000 * 60 * 3)
    public void execute() {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        fileInfoQuery.setQueryExpire(true);
        List<FileInfo> fileInfoList = fileInfoService.findListByParam(fileInfoQuery);
        Map<String, List<FileInfo>> fileInfoMap = new HashMap<>();
        for (FileInfo fileInfo : fileInfoList) {
            String userId = fileInfo.getUserId();
            // 如果 Map 中不存在该用户ID的键，则创建一个新的 List，并将文件信息对象加入其中
            if (!fileInfoMap.containsKey(userId)) {
                fileInfoMap.put(userId, new ArrayList<>());
            }
            // 将文件信息对象加入对应用户ID的 List 中
            fileInfoMap.get(userId).add(fileInfo);
        }

        for (Map.Entry<String, List<FileInfo>> entry : fileInfoMap.entrySet()) {
            List<String> fileIds = new ArrayList<>();

            List<FileInfo> fileInfos = entry.getValue();
            for (FileInfo fileInfo : fileInfos) {
                fileIds.add(fileInfo.getFileId());
            }
            fileInfoService.delFileBatch(entry.getKey(), String.join(",", fileIds), false);
        }
    }
}
