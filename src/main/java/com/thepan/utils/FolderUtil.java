package com.thepan.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;

public class FolderUtil {
    private static String avatarFolder =  System.getProperty("user.dir") + "/" + "avatar";

    public static String getProjectFolder() {
        if (!StrUtil.isEmpty(avatarFolder) && !avatarFolder.endsWith("/")) {
            avatarFolder = avatarFolder + "/";
        }
        if (!FileUtil.isNotEmpty(new File(avatarFolder))) {
            FileUtil.mkdir(avatarFolder);
        }
        return avatarFolder;
    }

    public static void main(String[] args) {
        getProjectFolder();
    }
}
