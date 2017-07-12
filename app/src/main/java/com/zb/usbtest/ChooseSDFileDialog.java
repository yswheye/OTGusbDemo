package com.zb.usbtest;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Mr.zhou.<br/>
 * Describe：
 */

public class ChooseSDFileDialog extends Dialog {

    private static final String TAG = "选择文件对话框";

    private GridView gv_list;
    private List<File> fileList;
    private SDFileAdapter adapter;

    private TextView tv_title;

    private ChooseFileCallBack callBack;
    private String rootPath = SDUtils.getSDPath();
    private String cPath = rootPath;

    public ChooseSDFileDialog(@NonNull Context context) {
        super(context);
        fileList = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View root = LayoutInflater.from(getContext()).inflate(R.layout.v_dialog_file_choose, null);
        setContentView(root);
        gv_list = (GridView) root.findViewById(R.id.dialog_file_gv);
        gv_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File file = fileList.get(position);
                if (!file.isFile()) {
                    cPath += "/" + file.getName();
                    tv_title.setText(cPath);
                    addFile2List(file);
                }
            }
        });
        gv_list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {//长按选择文件或文件夹
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                File file = fileList.get(position);
                dismiss();
                if (callBack != null) {
                    callBack.onChoose(file);
                }
                return true;
            }
        });
        tv_title = (TextView) root.findViewById(R.id.dialog_file_tv_title);
        tv_title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {//点击标题返回上一级
                if (!cPath.equals(rootPath)) {
                    cPath = cPath.substring(0, cPath.lastIndexOf("/"));
                    tv_title.setText(cPath);
                    addFile2List(new File(cPath));
                }
            }
        });
        //读取根目录文件和文件夹
        tv_title.setText(cPath);
        File rootFolder = new File(cPath);
        addFile2List(rootFolder);
    }

    private void addFile2List(File folder) {
        fileList.clear();
        for (File f : folder.listFiles()) {
            fileList.add(f);
        }
        Collections.sort(fileList, new Comparator<File>() {//简单排序 文件夹在前 文件在后
            @Override
            public int compare(File f1, File f2) {
                if (f1.isFile()) {
                    if (f2.isFile()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else {
                    if (f2.isFile()) {
                        return -1;
                    } else {
                        return 0;
                    }
                }
            }
        });
        if (adapter == null) {
            adapter = new SDFileAdapter(fileList);
            gv_list.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void show() {
        super.show();
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
    }

    public void setCallBack(ChooseFileCallBack callBack) {
        this.callBack = callBack;
    }

    public interface ChooseFileCallBack {
        void onChoose(File f);
    }

    private static class SDFileAdapter extends BaseAdapter {

        private List<File> fileList;

        public SDFileAdapter(List<File> fileList) {
            this.fileList = fileList;
        }

        @Override
        public int getCount() {
            return fileList.size();
        }

        @Override
        public Object getItem(int position) {
            return fileList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gv_file, parent, false);
                vh = new ViewHolder();
                vh.tv_name = (TextView) convertView.findViewById(R.id.item_file_tv_name);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            //设置图标
            File file = fileList.get(position);
            int iconId = file.isDirectory() ? R.drawable.icon_folder_40 : R.drawable.icon_file_40;
            setDrawableTopIcon(vh.tv_name, iconId, 4);
            vh.tv_name.setText(file.getName());
            return convertView;
        }

        static class ViewHolder {
            TextView tv_name;
        }

        public void setDrawableTopIcon(TextView textView, int iconId, int dPadding) {
            Drawable drawable = ContextCompat.getDrawable(textView.getContext(), iconId);
            drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
            textView.setCompoundDrawables(null, drawable, null, null);
            textView.setCompoundDrawablePadding(DimenUtils.dip2px(textView.getContext(), dPadding));
        }
    }
}
