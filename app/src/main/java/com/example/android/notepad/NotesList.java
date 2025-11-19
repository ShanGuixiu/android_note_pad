package com.example.android.notepad;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


/**
 * 笔记列表
 */
public class NotesList extends ListActivity {

    // 用于日志和调试
    private static final String TAG = "NotesList";

    /**
     * 游标适配器所需的列
     */
    private static final String[] PROJECTION = new String[]{
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_NOTE
    };

    /**
     * 标题列的索引
     */
    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_NAME_MODIFICATION_DATE = 2;
    private static final int COLUMN_INDEX_NOTE = 3;

    // 原有变量保持不变，新增搜索相关变量
    private EditText mSearchEditText;
    private String mCurrentSearchQuery = "";
    private Cursor mOriginalCursor; // 保存原始游标用于恢复

    private ContentObserver contentObserver;

    /**
     * onCreate在Android从头开始启动此Activity时调用。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置自定义布局
        setContentView(R.layout.notes_list);

        // 获取ActionBar并设置样式
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // 设置标题栏背景为白色
            actionBar.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            // 设置标题字体颜色为黑色
            actionBar.setTitle(Html.fromHtml("<font color='#000000'>" + getTitle() + "</font>"));
        }

        getListView().setDivider(null);
        getListView().setDividerHeight(0);
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        getListView().setOnCreateContextMenuListener(this);

        // 获取原始游标并保存
        loadNotesData();

        // 初始化适配器（使用原始游标）
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        };
        int[] viewIDs = {
                android.R.id.text1,
                R.id.update_time
        };

        CustomCursorAdapter adapter = new CustomCursorAdapter(
                this,
                R.layout.noteslist_item,
                mOriginalCursor,
                dataColumns,
                viewIDs
        );
        setListAdapter(adapter);

        // 初始化搜索框
        initSearchView();

        // 初始化背景
        updateBackground();
        contentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                performSearch();
            }
        };
        // 注册内容观察者以监听数据库变化
        getContentResolver().registerContentObserver(
                NotePad.Notes.CONTENT_URI,
                true,
                contentObserver
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重新加载数据以确保游标有效
        loadNotesData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 可以在这里做清理工作
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理游标
        if (mOriginalCursor != null && !mOriginalCursor.isClosed()) {
            mOriginalCursor.close();
        }
        getContentResolver().unregisterContentObserver(contentObserver);
    }

    // 新增：初始化搜索框
    private void initSearchView() {
        mSearchEditText = (EditText) findViewById(R.id.et_search);
        // 文本变化监听器 - 实时搜索
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mCurrentSearchQuery = s.toString().trim();
                performSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 软键盘搜索按钮监听
        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // 隐藏软键盘
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
    }


    private void loadNotesData() {
        // 直接查询而不使用 managedQuery
        Cursor cursor = getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                null,
                null,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        if (mOriginalCursor != null && !mOriginalCursor.isClosed()) {
            mOriginalCursor.close();
        }
        mOriginalCursor = cursor;

        // 初始化或更新适配器
        CustomCursorAdapter adapter = (CustomCursorAdapter) getListAdapter();
        if (adapter == null) {
            String[] dataColumns = {
                    NotePad.Notes.COLUMN_NAME_TITLE,
                    NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
            };
            int[] viewIDs = {
                    android.R.id.text1,
                    R.id.update_time
            };

            adapter = new CustomCursorAdapter(
                    this,
                    R.layout.noteslist_item,
                    mOriginalCursor,
                    dataColumns,
                    viewIDs
            );
            setListAdapter(adapter);
        } else {
            adapter.changeCursor(mOriginalCursor);
        }
    }

    // 执行搜索逻辑
    private void performSearch() {
        Cursor filteredCursor;

        if (TextUtils.isEmpty(mCurrentSearchQuery)) {
            // 使用普通查询代替 managedQuery
            filteredCursor = getContentResolver().query(
                    getIntent().getData(),
                    PROJECTION,
                    null,
                    null,
                    NotePad.Notes.DEFAULT_SORT_ORDER
            );
        } else {
            // 构建搜索条件：标题或内容包含搜索关键词
            String selection = NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR "
                    + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?";
            String[] selectionArgs = new String[]{
                    "%" + mCurrentSearchQuery + "%",
                    "%" + mCurrentSearchQuery + "%"
            };

            // 使用普通查询代替 managedQuery
            filteredCursor = getContentResolver().query(
                    getIntent().getData(),
                    PROJECTION,
                    selection,
                    selectionArgs,
                    NotePad.Notes.DEFAULT_SORT_ORDER
            );
        }

        // 更新适配器数据
        CustomCursorAdapter adapter = (CustomCursorAdapter) getListAdapter();
        if (adapter != null) {
            // 关闭旧游标后再设置新游标
            Cursor oldCursor = adapter.getCursor();
            adapter.changeCursor(filteredCursor);
            if (oldCursor != null && !oldCursor.isClosed()) {
                oldCursor.close();
            }
        }
    }

    // 重写状态保存方法
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存当前搜索关键词
        outState.putString("SEARCH_QUERY", mCurrentSearchQuery);
    }

    // 重写状态恢复方法
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 恢复搜索状态
        if (savedInstanceState.containsKey("SEARCH_QUERY")) {
            mCurrentSearchQuery = savedInstanceState.getString("SEARCH_QUERY");
            mSearchEditText.setText(mCurrentSearchQuery);
        }
    }


    /**
     * 当用户第一次点击设备的菜单按钮时调用此方法
     * 对于此Activity。Android传入一个填充了项的Menu对象。
     * <p>
     * 设置一个提供插入选项以及一系列替代操作的菜单
     * 对于此Activity。其他想要处理笔记的应用程序可以"注册"自己
     * 在Android中，通过提供包含ALTERNATIVE类别和
     * mime类型NotePad.Notes.CONTENT_TYPE。如果他们这样做，onCreateOptionsMenu()中的代码
     * 将包含意图过滤器的Activity添加到其选项列表中。实际上，
     * 菜单将为用户提供可以处理笔记的其他应用程序。
     *
     * @param menu 要向其添加菜单项的Menu对象
     * @return 始终为True。菜单应该被显示
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 从XML资源填充菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // 生成可以对整个列表执行的任何其他操作。
        // 在正常安装中，这里没有发现其他操作，
        // 但这允许其他应用程序扩展我们的菜单并添加自己的操作。
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // 如果剪贴板上有数据，则启用粘贴菜单项
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);


        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        // 如果剪贴板包含项，则启用菜单上的粘贴选项
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            // 如果剪贴板为空，则禁用菜单的粘贴选项
            mPasteItem.setEnabled(false);
        }

        // 获取当前显示的笔记数量
        final boolean haveItems = getListAdapter().getCount() > 0;

        // 如果列表中有任何笔记（这意味着其中一个
        // 被选中），那么我们需要生成可以对当前选择执行的操作。
        // 这将是我们自己的特定操作与任何可以找到的扩展的组合。
        if (haveItems) {

            // 这是选中的项
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // 创建一个包含一个元素的Intents数组。这将用于发送意图
            // 基于选中的菜单项
            Intent[] specifics = new Intent[1];


            // 将数组中的Intent设置为对选中笔记的URI执行EDIT操作
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // 创建一个包含一个元素的菜单项数组。这将包含EDIT选项
            MenuItem[] items = new MenuItem[1];

            // 创建一个没有特定操作的Intent，使用选中笔记的URI
            Intent intent = new Intent(null, uri);

            /* 向Intent添加ALTERNATIVE类别，以笔记ID URI作为其数据。
             * 这将Intent准备为在菜单中分组替代选项的位置。
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * 向菜单添加替代选项
             */
            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,  // 将Intents添加为替代组中的选项
                    Menu.NONE,                  // 不需要唯一的项ID
                    Menu.NONE,                  // 替代项不需要排序
                    null,                       // 调用者的名称不排除在组外
                    specifics,                  // 这些特定选项必须首先出现
                    intent,                     // 这些Intent对象映射到specifics中的选项
                    Menu.NONE,                  // 不需要标志
                    items                       // 从specifics到Intents映射生成的菜单项
            );
            // 如果存在编辑菜单项，为其添加快捷键
            if (items[0] != null) {

                // 将编辑菜单项快捷键设置为数字"1"，字母"e"
                items[0].setShortcut('1', 'e');
            }
        } else {
            // 如果列表为空，从菜单中移除所有现有的替代操作
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }

        // 显示菜单
        return true;
    }

    /**
     * 当用户从菜单中选择一个选项但列表中没有选中项时调用此方法。
     * 如果选项是INSERT，则发送一个带有ACTION_INSERT动作的新Intent。
     * 传入Intent中的数据被放入新Intent中。实际上，这会触发NotePad应用程序中的NoteEditor活动。
     * <p>
     * 如果该项不是INSERT，则很可能是来自另一个应用程序的替代选项。
     * 调用父方法来处理该项。
     *
     * @param item 用户选择的菜单项
     * @return 如果选中的是INSERT菜单项，则为True；否则，返回调用父方法的结果
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            /*
             * 使用Intent启动新Activity。Activity的意图过滤器
             * 必须有ACTION_INSERT动作。没有设置类别，所以默认为DEFAULT。
             * 实际上，这会启动NotePad中的NoteEditor Activity。
             */
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;
        } else if (item.getItemId() == R.id.menu_paste) {
            /*
             * 使用Intent启动新Activity。Activity的意图过滤器
             * 必须有ACTION_PASTE动作。没有设置类别，所以默认为DEFAULT。
             * 实际上，这会启动NotePad中的NoteEditor Activity。
             */
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        }
        // 添加背景切换处理
        if (item.getItemId() == R.id.menu_change_bg) {
            showBackgroundChooser();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    // 在showBackgroundChooser方法中调整背景选项
    private void showBackgroundChooser() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择背景色");
        // 背景选项（仅淡色系）
        final String[] bgOptions = {"浅灰", "浅蓝", "浅绿", "浅粉", "浅紫"};
        final int[] bgColors = {
                R.color.bg_light_gray,
                R.color.bg_light_blue,
                R.color.bg_light_green,
                R.color.bg_light_pink,
                R.color.bg_light_purple
        };

        builder.setItems(bgOptions, (dialog, which) -> {
            // 保存选中的背景色
            SharedPreferences prefs = getSharedPreferences("NotePrefs", MODE_PRIVATE);
            prefs.edit().putInt("bg_color", bgColors[which]).apply();
            // 更新界面背景
            updateBackground();
        });
        builder.show();
    }

    // 更新背景
    private void updateBackground() {
        SharedPreferences prefs = getSharedPreferences("NotePrefs", MODE_PRIVATE);
        int bgColor = prefs.getInt("bg_color", R.color.bg_light_gray);
        int colorValue = getResources().getColor(bgColor);

        // 1. 更新根布局背景
        View rootView = findViewById(android.R.id.content);
        rootView.setBackgroundColor(colorValue);

        // 2. 更新列表背景（保持透明，继承根布局）
        getListView().setBackgroundColor(Color.TRANSPARENT);
        // 3. 更新适配器中的列表项背景
        CustomCursorAdapter adapter = (CustomCursorAdapter) getListAdapter();
        if (adapter != null) {
            adapter.updateBackgroundColor(bgColor);
        }
    }


    /**
     * 当用户在列表中长按点击笔记时调用此方法。NotesList将自己注册为
     * ListView的上下文菜单处理程序（这是在onCreate()中完成的）。
     * <p>
     * 唯一可用的选项是复制和删除。
     * <p>
     * 长按点击等同于上下文点击。
     *
     * @param menu     要向其添加项的ContextMenu对象
     * @param view     正在为其构造上下文菜单的View
     * @param menuInfo 与视图关联的数据
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {

        // 菜单项的数据
        AdapterView.AdapterContextMenuInfo info;

        // 尝试获取ListView中被长按的项的位置
        try {
            // 将传入的数据对象转换为AdapterView对象的类型
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // 如果菜单对象无法转换，记录错误
            Log.e(TAG, "错误的menuInfo", e);
            return;
        }

        /*
         * 获取与选中位置的项相关联的数据。getItem()返回
         * ListView的支持适配器与该项相关联的任何内容。在NotesList中，
         * 适配器将笔记的所有数据与其列表项相关联。因此，
         * getItem()以Cursor的形式返回该数据。
         */
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        // 如果游标为空，则由于某种原因适配器无法从提供者获取数据，因此向调用者返回null
        if (cursor == null) {
            // 由于某种原因，请求的项不可用，不执行任何操作
            return;
        }

        // 从XML资源填充菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);


        // 将菜单标题设置为选中笔记的标题
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // 附加其他可以处理它的活动的菜单项
        // 这会查询系统中任何实现了我们数据的ALTERNATIVE_ACTION的活动，为每个找到的活动添加一个菜单项
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * 当用户从上下文菜单中选择一项时调用此方法（请参见onCreateContextMenu()）。
     * 实际处理的菜单项只有DELETE和COPY。其他任何项都是替代选项，应进行默认处理。
     *
     * @param item 选中的菜单项
     * @return 如果菜单项是DELETE，且不需要默认处理，则为True；否则为False，这会触发该项的默认处理
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // 菜单项的数据
        AdapterView.AdapterContextMenuInfo info;
        try {
            // 将项中的数据对象转换为AdapterView对象的类型
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // 如果对象无法转换，记录错误
            Log.e(TAG, "错误的menuInfo", e);

            // 触发菜单项的默认处理
            return false;
        }
        // 将选中的笔记ID附加到随传入Intent发送的URI
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * 获取菜单项的ID并将其与已知动作进行比较
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // 启动活动以查看/编辑当前选中的项
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) { // 开始复制
            // 获取剪贴板服务的句柄
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            // 将笔记URI复制到剪贴板。实际上，这会复制笔记本身
            clipboard.setPrimaryClip(ClipData.newUri(   // 包含URI的新剪贴板项
                    getContentResolver(),               // 用于检索URI信息的解析器
                    "Note",                             // 剪贴板项的标签
                    noteUri));                          // URI

            // 返回给调用者并跳过进一步处理
            return true;
            // 结束复制
        } else if (id == R.id.context_delete) {
            // 通过传入笔记ID格式的URI从提供者中删除笔记
            // 请参见关于在UI线程上执行提供者操作的介绍性说明
            getContentResolver().delete(
                    noteUri,  // 提供者的URI
                    null,     // 不需要where子句，因为只传入了单个笔记ID
                    null      // 不使用where子句，因此不需要where参数
            );

            // 返回给调用者并跳过进一步处理
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * 当用户点击显示列表中的笔记时调用此方法。
     * <p>
     * 此方法处理传入的PICK（从提供者获取数据）或GET_CONTENT（获取或创建数据）动作。
     * 如果传入的动作是EDIT，此方法发送一个新的Intent来启动NoteEditor。
     *
     * @param l        包含点击项的ListView
     * @param v        单个项的View
     * @param position v在显示列表中的位置
     * @param id       点击项的行ID
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // 从传入的URI和行ID构造一个新的URI
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // 从传入的Intent中获取动作
        String action = getIntent().getAction();

        // 处理笔记数据请求
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // 将结果设置为返回给调用此Activity的组件。结果包含新的URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {

            // 发送一个Intent来启动可以处理ACTION_EDIT的Activity。
            // Intent的数据是笔记ID URI。实际上，这会调用NoteEdit。
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }

    /**
     * 自定义游标适配器，用于动态设置列表项背景色
     */
    /**
     * 自定义游标适配器，用于动态设置列表项背景色和显示创建时间
     */
    private class CustomCursorAdapter extends SimpleCursorAdapter {
        private int mBgColor;
        // 时间格式化器（yyyy-MM-dd HH:mm）
        private final SimpleDateFormat dateFormat;

        public CustomCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to, 0);
            // 初始化时间格式化器并设置时区
            dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

            // 初始化背景色
            SharedPreferences prefs = getSharedPreferences("NotePrefs", MODE_PRIVATE);
            mBgColor = prefs.getInt("bg_color", R.color.bg_light_gray);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (view != null) {
                // 设置列表项整体背景（保留原功能）
                view.setBackgroundColor(getResources().getColor(mBgColor));

                // 新增：处理创建时间显示
                Cursor cursor = getCursor();  // 获取当前游标
                if (cursor.moveToPosition(position)) {  // 移动游标到当前位置
                    // 获取时间控件
                    TextView timeTv = (TextView) view.findViewById(R.id.update_time);
                    if (timeTv != null) {
                        // 从游标获取创建时间（毫秒值）
                        long createTime = cursor.getLong(COLUMN_NAME_MODIFICATION_DATE);
//                        System.out.println("createTime = " + createTime);
//                        System.out.println(dateFormat.format(new Date(createTime)));
//                        System.out.println(System.currentTimeMillis());
//                        System.out.println(dateFormat.format(System.currentTimeMillis()));
                        // 格式化时间并设置到控件
                        timeTv.setText(dateFormat.format(new Date(createTime)));
                    }
                }

                // 如果需要恢复卡片容器背景，取消下面注释
                // LinearLayout cardContainer = view.findViewById(R.id.card_container);
                // if (cardContainer != null) {
                //     cardContainer.setBackgroundColor(getDarkerColor(mBgColor));
                // }
            }
            return view;
        }

        // 辅助方法：将颜色调深一点作为卡片背景（
        private int getDarkerColor(int colorResId) {
            int color = getResources().getColor(colorResId);
            float factor = 0.95f; // 调深5%
            int a = Color.alpha(color);
            int r = Math.round(Color.red(color) * factor);
            int g = Math.round(Color.green(color) * factor);
            int b = Math.round(Color.blue(color) * factor);
            return Color.argb(a, Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
        }

        // 更新背景色并刷新列表
        public void updateBackgroundColor(int colorResId) {
            mBgColor = colorResId;
            notifyDataSetChanged();
        }
    }
}