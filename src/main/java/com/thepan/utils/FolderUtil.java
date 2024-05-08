package com.thepan.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;

public class FolderUtil {
    private static String projectFolder =  System.getProperty("user.dir");


    public static String getProjectFolder() {
        if (!StrUtil.isEmpty(projectFolder) && !projectFolder.endsWith("/")) {
            projectFolder = projectFolder + "/";
        }
        if (!FileUtil.isNotEmpty(new File(projectFolder))) {
            FileUtil.mkdir(projectFolder);
        }
        return projectFolder;
    }

    public static void main(String[] args) {
        getProjectFolder();
    }
}
