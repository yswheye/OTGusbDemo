package com.zb.usbtest;

import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Created by Mr.zhou.<br/>
 * Describe：
 */

public class SDUtils {

    private static final String TAG = "文件工具类";

    public static String getSDPath() {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED); //判断sd卡是否存在
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();//获取跟目录
//            sdDir1 = Environment.getDataDirectory();
//            sdDir2 = Environment.getRootDirectory();
        } else {
            Log.d(TAG, "getSDPath: sd卡不存在");
        }
        Log.d(TAG, "getSDPath: " + sdDir.getAbsolutePath());
        return sdDir.getAbsolutePath();
    }
}
