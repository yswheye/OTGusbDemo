package com.zb.usbtest;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.fs.UsbFileOutputStream;
import com.github.mjdev.libaums.partition.Partition;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Mr.Zhou
 */
public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, View.OnClickListener {

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private ScrollView sv;
    private TextView tv_msg;
    private TextView tv_title;
    private TextView tv_sdFileCopy;
    //列表相关
    private GridView gv_list;
    private List<UsbFile> usbFiles = new ArrayList<>();
    private UsbFileAdapter adapter;

    private UsbMassStorageDevice[] storageDevices;

    private UsbFile cFolder;//当前目录

    private ExecutorService executorService;

    private ProgressDialog dialog_wait;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindView();
        registerReceiver();
        redDeviceList();//一开始就需要尝试读取一次
        init();
    }

    private void init() {
        //等待框
        dialog_wait = new ProgressDialog(this);
        dialog_wait.setCancelable(false);
        dialog_wait.setCanceledOnTouchOutside(false);
        //线程
        executorService = Executors.newCachedThreadPool();//30大小的线程池
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUsbReceiver != null) {//有注册就有注销
            unregisterReceiver(mUsbReceiver);
            mUsbReceiver = null;
        }
    }

    private void registerReceiver() {
        //监听otg插入 拔出
        IntentFilter usbDeviceStateFilter = new IntentFilter();
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, usbDeviceStateFilter);
        //注册监听自定义广播
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
    }

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_USB_PERMISSION://接受到自定义广播
                    setMsg("接收到自定义广播");
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {  //允许权限申请
                        if (usbDevice != null) {  //Do something
                            setMsg("用户已授权，可以进行读取操作");
                            readDevice(getUsbMass(usbDevice));
                        } else {
                            setMsg("未获取到设备信息");
                        }
                    } else {
                        setMsg("用户未授权，读取失败");
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_ATTACHED://接收到存储设备插入广播
                    UsbDevice device_add = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device_add != null) {
                        setMsg("接收到存储设备插入广播，尝试读取");
                        redDeviceList();
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED://接收到存储设备拔出广播
                    UsbDevice device_remove = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device_remove != null) {
                        setMsg("接收到存储设备拔出广播");
                        tv_title.setText("U盘文件目录");//恢复默认文字
                        usbFiles.clear();//清除
                        adapter.notifyDataSetChanged();//更新界面
                        cFolder = null;
                    }
                    break;
            }
        }
    };

    private UsbMassStorageDevice getUsbMass(UsbDevice usbDevice) {
        for (UsbMassStorageDevice device : storageDevices) {
            if (usbDevice.equals(device.getUsbDevice())) {
                return device;
            }
        }
        return null;
    }

    private void redDeviceList() {
        setMsg("开始读取设备列表...");
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        //获取存储设备
        storageDevices = UsbMassStorageDevice.getMassStorageDevices(this);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        for (UsbMassStorageDevice device : storageDevices) {//可能有几个 一般只有一个 因为大部分手机只有1个otg插口
            if (usbManager.hasPermission(device.getUsbDevice())) {//有就直接读取设备是否有权限
                setMsg("检测到有权限，直接读取");
                readDevice(device);
            } else {//没有就去发起意图申请
                setMsg("检测到设备，但是没有权限，进行申请");
                usbManager.requestPermission(device.getUsbDevice(), pendingIntent); //该代码执行后，系统弹出一个对话框，
            }
        }
        if (storageDevices.length == 0) setMsg("未检测到有任何存储设备插入");
    }

    private void readDevice(UsbMassStorageDevice device) {
        // before interacting with a device you need to call init()!
        try {
            device.init();//初始化
//          Only uses the first partition on the device
            Partition partition = device.getPartitions().get(0);
            FileSystem currentFs = partition.getFileSystem();
//fileSystem.getVolumeLabel()可以获取到设备的标识
//通过FileSystem可以获取当前U盘的一些存储信息，包括剩余空间大小，容量等等
//            Log.d(TAG, "Capacity: " + currentFs.getCapacity());
//            Log.d(TAG, "Occupied Space: " + currentFs.getOccupiedSpace());
//            Log.d(TAG, "Free Space: " + currentFs.getFreeSpace());
//            Log.d(TAG, "Chunk size: " + currentFs.getChunkSize());
            UsbFile root = currentFs.getRootDirectory();//获取根目录
            String deviceName = currentFs.getVolumeLabel();//获取设备标签
            setMsg("正在读取U盘" + deviceName);
            tv_title.setText(deviceName);//设置标题
            cFolder = root;//设置当前文件对象
            addFile2List();//添加文件
        } catch (Exception e) {
            e.printStackTrace();
            setMsg("读取失败，异常：" + e.getMessage());
        }
    }


    @Override
    public void onClick(View v) {//点击标题 返回上一级目录
        if (v.getId() == R.id.main_tv_title) {
            if (cFolder != null) {
                if (!cFolder.isRoot()) {//如果不是根目录
                    cFolder = cFolder.getParent();
                    String cPath = tv_title.getText().toString();
                    tv_title.setText(cPath.substring(0, cPath.lastIndexOf("/")));//从0到最后一级目录
                    addFile2List();
                }
            }
        } else if (v.getId() == R.id.main_tv_sdFileChoose) {
            ChooseSDFileDialog sdFileDialog = new ChooseSDFileDialog(this);
            sdFileDialog.setCallBack(new ChooseSDFileDialog.ChooseFileCallBack() {
                @Override
                public void onChoose(final File f) {
                    if (cFolder == null) {//表示当前目录根本不存在
                        setMsg("未检测到U盘目录");
                    } else {
                        if (usbFiles != null) {
                            for (UsbFile uf : usbFiles) {
                                if (uf.getName().equals(f.getName())) {
                                    if (uf.isDirectory()) {
                                        setMsg("目录" + f.getName() + "已存在，请删除后再试");
                                        return;
                                    } else {
                                        if (uf.getLength() == f.length()) {
                                            setMsg(f.getName() + "已存在，不需要写入");
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                        dialog_wait.setMessage("正在写入" + f.getName() + ",请耐心等待...");
                        dialog_wait.show();
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                readSDFile(f, cFolder);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setMsg(f.getName() + "已写入到U盘中");
                                        dialog_wait.dismiss();
                                        adapter.notifyDataSetChanged();
                                    }
                                });
                            }
                        });
                    }
                }
            });
            sdFileDialog.show();
        }
    }

    private void readSDFile(final File f, UsbFile folder) {
        UsbFile usbFile = null;
        if (f.isDirectory()) {//如果选择是个文件夹
            try {
                usbFile = folder.createDirectory(f.getName());
                if (folder == cFolder) {//如果是在当前目录 就添加到集合中
                    usbFiles.add(usbFile);
                }
                for (File sdFile : f.listFiles()) {
                    readSDFile(sdFile, usbFile);
                }
            } catch (IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setMsg("创建目录" + f.getName() + "时,IO异常");
                    }
                });
            }
        } else {//如果选了一个文件
            try {
                usbFile = folder.createFile(f.getName());
                if (folder == cFolder) {//如果是在当前目录 就添加到集合中
                    usbFiles.add(usbFile);
                }
                saveSDFile2OTG(usbFile, f);
            } catch (IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setMsg("创建文件" + f.getName() + "时,IO异常");
                    }
                });
            }
        }
    }

    private void saveSDFile2OTG(final UsbFile usbFile, final File f) {
        try {//开始写入
            FileInputStream fis = new FileInputStream(f);//读取选择的文件的
            UsbFileOutputStream uos = new UsbFileOutputStream(usbFile);
            redFileStream(uos, fis);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setMsg("文件" + f.getName() + "已写入U盘");
                }
            });
        } catch (final Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setMsg("写入" + e.getMessage() + "出错,执行删除");
                    try {
                        usbFile.delete();
                    } catch (IOException e1) {
                        setMsg("删除" + usbFile.getName() + "失败");
                    }
                }
            });
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {//点击item 进入该目录
        UsbFile file = usbFiles.get(position);
        if (file.isDirectory()) {//如果是文件夹
            cFolder = file;
            tv_title.append("/" + cFolder.getName());
            addFile2List();
        } else {
            readFile(file);
        }
    }

    private void readFile(final UsbFile uFile) {
        String sdPath = SDUtils.getSDPath();//获取sd根目录 创建一个同名文件
        String filePath = sdPath + "/" + uFile.getName();
        final File f = new File(filePath);
        if (f.exists()) {
            setMsg("文件已存在：" + filePath);
        } else {
            setMsg("文件不存在，正在读取到" + filePath + "...");
            try {
                f.createNewFile();
                //设置视图
                dialog_wait.setMessage("正在读取" + uFile.getName() + "...");
                dialog_wait.show();
                //执行线程
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            FileOutputStream os = new FileOutputStream(f);
                            InputStream is = new UsbFileInputStream(uFile);
                            redFileStream(os, is);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    dialog_wait.dismiss();
                                    setMsg("文件已读取到sd卡中：" + f.getAbsolutePath());
                                }
                            });
                        } catch (final Exception e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    dialog_wait.dismiss();
                                    setMsg("文件读取时出错：" + e.getMessage());
                                }
                            });
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                setMsg("创建文件时异常，有可能是没有读取权限，6.0需要动态申请");
            }
        }
    }

    private void redFileStream(OutputStream os, InputStream is) throws IOException {
        /**
         *  写入文件到U盘同理 要获取到UsbFileOutputStream后 通过
         *  f.createNewFile();调用 在U盘中创建文件 然后获取os后
         *  可以通过输出流将需要写入的文件写到流中即可完成写入操作
         */
        int bytesRead = 0;
        byte[] buffer = new byte[1024 * 8];
        while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        os.flush();
        os.close();
        is.close();
    }

    private void addFile2List() {//添加文件和文件夹到gridView显示
        try {
            usbFiles.clear();
            for (UsbFile file : cFolder.listFiles()) {
                usbFiles.add(file);
            }
            Collections.sort(usbFiles, new Comparator<UsbFile>() {//简单排序 文件夹在前 文件在后
                @Override
                public int compare(UsbFile oFile1, UsbFile oFile2) {
                    if (oFile1.isDirectory()) return -1;
                    else return 1;
                }
            });
            if (adapter == null) {
                adapter = new UsbFileAdapter(usbFiles);
                gv_list.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }
        } catch (IOException e) {
            e.printStackTrace();
            setMsg("读取出错IO异常：" + e.getMessage());
        }
    }

    private void bindView() {
        sv = (ScrollView) findViewById(R.id.main_sv);
        tv_msg = (TextView) findViewById(R.id.main_tv_msg);
        tv_msg.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                tv_msg.setText("");
                setMsg("清空消息");
                return true;
            }
        });
        tv_sdFileCopy = (TextView) findViewById(R.id.main_tv_sdFileChoose);
        tv_sdFileCopy.setOnClickListener(this);
        tv_title = (TextView) findViewById(R.id.main_tv_title);
        tv_title.setOnClickListener(this);
        gv_list = (GridView) findViewById(R.id.main_gv);
        gv_list.setOnItemClickListener(this);
        gv_list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final UsbFile file = usbFiles.get(position);
                final String name = file.isDirectory() ? "文件目录" : "文件";
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("确认删除" + name + file.getName() + "?")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    file.delete();
                                    usbFiles.remove(file);
                                    adapter.notifyDataSetChanged();
                                    setMsg(name + "已删除");
                                } catch (IOException e) {
                                    setMsg(name + "删除失败");
                                }
                            }
                        })
                        .setNegativeButton("取消", null).show();
                return true;
            }
        });
    }

    private void setMsg(String msg) {
        tv_msg.append(msg + "\n");
        sv.fullScroll(ScrollView.FOCUS_DOWN);//滚动到底部
    }

    private static class UsbFileAdapter extends BaseAdapter {

        private List<UsbFile> usbFiles;

        public UsbFileAdapter(@NonNull List<UsbFile> usbFiles) {
            this.usbFiles = usbFiles;
        }

        @Override
        public int getCount() {
            return usbFiles.size();
        }

        @Override
        public Object getItem(int position) {
            return usbFiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                vh = new ViewHolder();
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gv_file, parent, false);
                vh.tv_name = (TextView) convertView.findViewById(R.id.item_file_tv_name);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            UsbFile file = usbFiles.get(position);
            //设置图标
            int iconId = file.isDirectory() ? R.drawable.icon_folder_40 : R.drawable.icon_file_40;
            setDrawableTopIcon(vh.tv_name, iconId, 4);
            //设置文件或文件目录名称
            vh.tv_name.setText(file.getName());
            return convertView;
        }

        public void setDrawableTopIcon(TextView textView, int iconId, int dPadding) {
            Drawable drawable = ContextCompat.getDrawable(textView.getContext(), iconId);
            drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
            textView.setCompoundDrawables(null, drawable, null, null);
            textView.setCompoundDrawablePadding(DimenUtils.dip2px(textView.getContext(), dPadding));
        }

        static class ViewHolder {
            TextView tv_name;
        }
    }

}
