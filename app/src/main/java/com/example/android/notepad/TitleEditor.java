package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

/**
 * 该Activity用于编辑笔记标题，显示一个包含EditText的浮动窗口
 */
public class TitleEditor extends Activity {

    /** 用于编辑笔记标题的特殊意图动作 */
    public static final String EDIT_TITLE_ACTION = "com.android.notepad.action.EDIT_TITLE";

    // 查询投影：返回笔记ID和标题
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
    };

    // 标题列在游标中的索引
    private static final int COLUMN_INDEX_TITLE = 1;

    // 用于查询笔记数据的游标
    private Cursor mCursor;
    // 用于输入标题的编辑框
    private EditText mText;
    // 当前编辑的笔记对应的URI
    private Uri mUri;

    /**
     * Activity首次创建时调用
     * 从启动意图中获取需要编辑的笔记URI，并初始化界面和数据
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置布局
        setContentView(R.layout.title_editor);

        // 从意图中获取需要编辑的笔记URI
        mUri = getIntent().getData();

        // 查询该URI对应的笔记数据（仅获取ID和标题）
        mCursor = managedQuery(
                mUri,        // 目标笔记的URI
                PROJECTION,  // 要查询的列
                null,        // 无筛选条件
                null,        // 无筛选参数
                null         // 无排序
        );

        // 获取编辑框实例
        mText = (EditText) this.findViewById(R.id.title);
    }

    /**
     * Activity即将进入前台时调用
     * 显示当前笔记的标题到编辑框
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (mCursor != null) {
            // 将游标移动到第一条记录（当前笔记）
            mCursor.moveToFirst();
            // 从游标中获取标题并显示到编辑框
            mText.setText(mCursor.getString(COLUMN_INDEX_TITLE));
        }
    }

    /**
     * Activity失去焦点时调用
     * 将编辑后的标题保存到数据库
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor != null) {
            // 创建用于更新数据的ContentValues
            ContentValues values = new ContentValues();
            // 存入编辑后的标题
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, mText.getText().toString());
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
            // 调用ContentResolver更新数据库中的标题
            getContentResolver().update(
                    mUri,    // 目标笔记的URI
                    values,  // 要更新的键值对
                    null,    // 无筛选条件
                    null     // 无筛选参数
            );
        }
    }

    /**
     * 处理"确定"按钮点击事件
     * 结束当前Activity
     */
    public void onClickOk(View v) {
        finish();
    }
}
