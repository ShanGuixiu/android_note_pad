package com.example.android.notepad;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 笔记编辑页面
 * 支持创建、编辑、保存、删除笔记，包含标题、内容及时间记录功能
 */
public class NoteEditor extends Activity {
    // 日志标签
    private static final String TAG = "NoteEditor";
    // 保存状态的键
    private static final String ORIGINAL_CONTENT = "origContent";
    // 状态常量：编辑现有笔记
    private static final int STATE_EDIT = 0;
    // 状态常量：新建笔记
    private static final int STATE_INSERT = 1;

    // 全局变量
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mTitleText; // 标题输入框
    private LinedEditText mContentText; // 带线条的内容编辑框
    private TextView mCreateTimeTv; // 创建时间显示
    private TextView mModifyTimeTv; // 修改时间显示
    private String mOriginalContent; // 原始内容（用于撤销）
    private SimpleDateFormat mDateFormat; // 时间格式化器

    /**
     * 带线条的自定义编辑框
     * 在每行文字下方绘制横线，模拟笔记本效果
     */
    public static class LinedEditText extends EditText {
        private final Rect mRect;
        private final Paint mPaint;

        // 布局加载器使用的构造方法
        public LinedEditText(Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x80000000); // 深灰色线条
        }

        /**
         * 绘制编辑框内容及线条
         *
         * @param canvas 绘图画布
         */
        @Override
        protected void onDraw(Canvas canvas) {
            // 获取文字行数
            int count = getLineCount();
            Rect r = mRect;
            Paint paint = mPaint;

            // 为每行文字绘制下划线
            for (int i = 0; i < count; i++) {
                // 获取当前行的基线位置
                int baseline = getLineBounds(i, r);
                // 绘制横线（位于基线下方1dp处）
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // 调用父类方法完成其余绘制
            super.onDraw(canvas);
        }
    }

    /**
     * 初始化Activity
     * 根据意图判断是新建还是编辑笔记，初始化数据和视图
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_editor);

        // 初始化时间格式化器（北京时间，格式：年-月-日 时:分）
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        mDateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // 绑定视图控件
        mTitleText = (EditText) findViewById(R.id.note_title);
        mContentText = (LinedEditText) findViewById(R.id.note);
        mCreateTimeTv = (TextView) findViewById(R.id.create_time);
        mModifyTimeTv = (TextView) findViewById(R.id.modify_time);

        // 获取启动意图
        final Intent intent = getIntent();
        final String action = intent.getAction();

        // 判断操作类型：编辑、新建或粘贴
        if (Intent.ACTION_EDIT.equals(action)) {
            mState = STATE_EDIT;
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action) || Intent.ACTION_PASTE.equals(action)) {
            mState = STATE_INSERT;
            // 插入新笔记
            mUri = getContentResolver().insert(intent.getData(), null);
            if (mUri == null) {
                Log.e(TAG, "插入新笔记失败：" + intent.getData());
                finish();
                return;
            }
            setResult(RESULT_OK, new Intent().setAction(mUri.toString()));
        } else {
            Log.e(TAG, "未知操作，退出");
            finish();
            return;
        }

        // 查询笔记数据（包含ID、标题、内容、创建时间、修改时间）
        mCursor = managedQuery(
                mUri,
                new String[]{
                        NotePad.Notes._ID,
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_NOTE,
                        NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                },
                null,
                null,
                null
        );

        // 处理粘贴操作
        if (Intent.ACTION_PASTE.equals(action)) {
            performPaste();
            mState = STATE_EDIT;
        }

        // 恢复保存的状态
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }

        // 应用背景设置
        applyBackgroundSetting();
    }

    /**
     * 应用背景颜色设置
     */
    private void applyBackgroundSetting() {
        SharedPreferences prefs = getSharedPreferences("NotePrefs", MODE_PRIVATE);
        // 1. 获取列表页保存的颜色资源ID（默认值与列表页保持一致）
        int bgColorRes = prefs.getInt("bg_color", R.color.bg_light_gray);
        // 2. 转换为实际颜色值
        int bgColor = getResources().getColor(bgColorRes);

        // 3. 设置根布局背景（外围区域颜色，与列表页保持一致）
        View rootView = findViewById(android.R.id.content);
        rootView.setBackgroundColor(bgColor);

        // 4. 内容编辑区文字颜色调浅（解决颜色太深问题）
        mContentText.setTextColor(getResources().getColor(R.color.text_dark_gray));
        mTitleText.setTextColor(getResources().getColor(R.color.text_black));
    }

    /**
     * 恢复数据并显示
     * 在Activity进入前台时调用，加载笔记内容和时间信息
     */
    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();

        if (mCursor != null) {
            mCursor.requery();
            mCursor.moveToFirst();

            // 设置标题栏
            if (mState == STATE_EDIT) {
                String title = mCursor.getString(1); // COLUMN_NAME_TITLE的索引为1
                setTitle(String.format(getString(R.string.title_edit), title));
            } else {
                setTitle(getString(R.string.title_create));
            }

            // 加载标题和内容
            mTitleText.setText(mCursor.getString(1));
            String content = mCursor.getString(2); // COLUMN_NAME_NOTE的索引为2
            mContentText.setTextKeepState(content);
            if (mOriginalContent == null) {
                mOriginalContent = content;
            }

            // 加载并显示时间
            long createTime = mCursor.getLong(3); // COLUMN_NAME_CREATE_DATE的索引为3
            long modifyTime = mCursor.getLong(4); // COLUMN_NAME_MODIFICATION_DATE的索引为4
            mCreateTimeTv.setText("创建时间：" + mDateFormat.format(new Date(createTime)));
            mModifyTimeTv.setText("修改时间：" + mDateFormat.format(new Date(modifyTime)));
        } else {
            setTitle(getString(R.string.error_title));
            mContentText.setText(getString(R.string.error_message));
        }
    }

    /**
     * 保存当前状态
     * 在Activity可能被销毁时调用，保存原始内容
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    /**
     * 暂停时保存数据
     * 在Activity失去焦点时调用，自动保存笔记内容
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor != null) {
            String title = mTitleText.getText().toString().trim();
            String content = mContentText.getText().toString();

            // 内容为空且是新建笔记则删除
            if (isFinishing() && TextUtils.isEmpty(content) && mState == STATE_INSERT) {
                setResult(RESULT_CANCELED);
                deleteNote();
            } else if (mState == STATE_EDIT) {
                updateNote(content, title);
            } else if (mState == STATE_INSERT) {
                updateNote(content, title);
                mState = STATE_EDIT;
            }
        }
    }

    /**
     * 创建菜单
     * 加载菜单资源并添加额外操作项
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        // 仅为已保存的笔记添加额外菜单
        if (mState == STATE_EDIT) {
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null
            );
        }
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * 准备菜单
     * 根据笔记是否修改决定是否显示撤销选项
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mCursor != null) {
            String savedContent = mCursor.getString(2);
            String currentContent = mContentText.getText().toString();
            menu.findItem(R.id.menu_revert).setVisible(!savedContent.equals(currentContent));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * 处理菜单点击
     * 响应保存、删除、撤销等操作
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            updateNote(mContentText.getText().toString(), mTitleText.getText().toString().trim());
            finish();
            return true;
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
            return true;
        } else if (id == R.id.menu_revert) {
            cancelNote();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 执行粘贴操作
     * 从剪贴板获取内容并应用到当前笔记
     */
    private void performPaste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {
            ClipData.Item item = clip.getItemAt(0);
            Uri uri = item.getUri();
            String text = null;
            String title = null;

            // 处理剪贴板中的笔记数据
            if (uri != null) {
                ContentResolver cr = getContentResolver();
                if (NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {
                    Cursor orig = cr.query(uri, new String[]{
                            NotePad.Notes.COLUMN_NAME_TITLE,
                            NotePad.Notes.COLUMN_NAME_NOTE
                    }, null, null, null);
                    if (orig != null && orig.moveToFirst()) {
                        title = orig.getString(0);
                        text = orig.getString(1);
                        orig.close();
                    }
                }
            }

            // 处理文本数据
            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            updateNote(text, title);
        }
    }

    /**
     * 更新笔记内容
     *
     * @param content 笔记内容
     * @param title   笔记标题（可为null）
     */
    @SuppressLint("SetTextI18n")
    private void updateNote(String content, String title) {
        ContentValues values = new ContentValues();
        long currentTime = System.currentTimeMillis();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, currentTime);

        // 处理标题
        if (mState == STATE_INSERT) {
            // 新建笔记时自动生成标题
            if (TextUtils.isEmpty(title)) {
                int length = content.length();
                title = content.substring(0, Math.min(30, length));
                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, currentTime);
        }
        if (!TextUtils.isEmpty(title)) {
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }

        // 更新内容
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, content);

        // 提交更新
        getContentResolver().update(mUri, values, null, null);

        // 更新界面时间显示
        mModifyTimeTv.setText("修改时间：" + mDateFormat.format(new Date(currentTime)));
    }

    /**
     * 撤销操作
     * 恢复笔记到原始状态或删除新建笔记
     */
    private void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                mCursor.close();
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, mCursor.getString(1));
                getContentResolver().update(mUri, values, null, null);
                mContentText.setText(mOriginalContent);
                mTitleText.setText(mCursor.getString(1));
            } else if (mState == STATE_INSERT) {
                deleteNote();
                finish();
            }
        }
    }

    /**
     * 删除笔记
     * 从数据库中移除当前笔记
     */
    private void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mContentText.setText("");
            mTitleText.setText("");
        }
    }

    /**
     * 取消按钮点击事件
     */
    public void onClickCancel(View v) {
        cancelNote();
    }
}