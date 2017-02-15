package seiko.neiko.ui.main;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import seiko.neiko.R;
import seiko.neiko.dao.SourceApi;
import seiko.neiko.dao.db.SiteDbApi;
import seiko.neiko.dao.engine.DdSource;
import seiko.neiko.dao.mPath;
import seiko.neiko.rx.RxBus;
import seiko.neiko.rx.RxEvent;
import seiko.neiko.ui.CacheActivity;
import seiko.neiko.app.ActivityBase;
import seiko.neiko.ui.sited.SitedActivity;
import seiko.neiko.ui.search.SearchActivity;
import seiko.neiko.ui.AboutActivity;
import seiko.neiko.ui.down.Download1Activity;
import seiko.neiko.utils.Base64Util;
import seiko.neiko.utils.FileUtil;
import seiko.neiko.utils.HintUtil;
import seiko.neiko.utils.HttpUtil;

/**
 * Created by Seiko on 2016/11/9. YiKu
 */

public class MainActivity extends ActivityBase implements NavigationView.OnNavigationItemSelectedListener {

    @BindView(R.id.viewpager)
    ViewPager mviewpager;
    @BindView(R.id.tabstrip)
    TabLayout mtabLayout;
    @BindView(R.id.layout)
    DrawerLayout mLayout;
    @BindView(R.id.id_nv_menu)
    NavigationView mNav;

    /** 页面集合 */
    private List<Fragment> fragmentList;
    private boolean flag = false;

    @Override
    public int getLayoutId() {return R.layout.activity_main;}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragmentList = new ArrayList<>();
        fragmentList.add(new MainSiteDFragment());
        fragmentList.add(new MainLikeFragment());
        fragmentList.add(new MainHistFragment());

        mviewpager.setAdapter(new MyFragStatePagerAdapter(getSupportFragmentManager()));
        mviewpager.setCurrentItem(1);
        mviewpager.setOffscreenPageLimit(3);
        mtabLayout.setupWithViewPager(mviewpager);

        mLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                flag = true;
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                flag = false;
            }
        });
        mNav.setNavigationItemSelectedListener(this);

        if (forIntent(getIntent())) {
            mviewpager.setCurrentItem(0);
        }
    }


    /** 监听滑动菜单 */
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.layout);
        drawer.closeDrawers();
        int id = item.getItemId();
        switch (id) {
            case R.id.nav_download:
                openActivity(Download1Activity.class);
                return true;
            case R.id.nav_sited:
                openActivity(SitedActivity.class);
                return true;
            case R.id.nav_cache:
                openActivity(CacheActivity.class);
                return true;
            case R.id.nav_about:
                openActivity(AboutActivity.class);
                return true;
            case R.id.nav_exit:
                System.exit(0);
                return true;
        }
        return false;
    }

    /* 设置按钮 */
    @OnClick(R.id.nav_set)
    void nav_set() {
        if (flag) {
            mLayout.closeDrawers();
        } else {
            mLayout.openDrawer(GravityCompat.START);
        }
    }

    /* 搜索按钮 */
    @OnClick(R.id.nav_searchs)
    void nav_search() {
        SearchActivity.allSource = true;
        SearchActivity.key = null;
        openActivity(SearchActivity.class);
    }

    /**
     * 定义自己的ViewPager适配器。
     * 也可以使用FragmentPagerAdapter。关于这两者之间的区别，可以自己去搜一下。
     */
    private class MyFragStatePagerAdapter extends FragmentStatePagerAdapter {

        String[] pagers = new String[]{"主页", "收藏",  "历史"};

        MyFragStatePagerAdapter(FragmentManager fm) {super(fm);}

        @Override
        public Fragment getItem(int position) {return fragmentList.get(position);}

        @Override
        public int getCount() {return fragmentList.size();}

        @Override
        public CharSequence getPageTitle(int position) {return pagers[position];}

    }

    /** 安装sited插件操作 */
    @Override
    protected void onNewIntent(Intent intent) {super.onNewIntent(intent);}

    protected boolean forIntent(Intent intent) {
        if (intent == null)
            return false;

        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action))
            return false;

        Uri uri = intent.getData();
        if (uri == null)
            return false;

        String scheme = uri.getScheme();

        /* 通过网络打开的链接 例如：http://sited.moear.org/addin/site1041.sited.xml */
        if (scheme.equals("sited")) {
            if(!"data".equals(uri.getHost())) return false;

            String webUrl = Base64Util.decode(uri.getQuery()).replace("sited://", "http://");
            if(!webUrl.contains(".sited")) return false;

            HttpUtil.get(webUrl, (code, text) -> addSource(text));
            return true;
        }

        /* 通过本地打开的链接 例如：file:///storage/emulated/0/Tencent/QQfile_recv/kuaikan.sited */
        if (scheme.equals("file")) {
            try {
                ContentResolver cr = this.getContentResolver();
                String sited = FileUtil.toString(cr.openInputStream(uri));
                addSource(sited);
                return true;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return false;
    }

    /** 添加插件 */
    private void addSource(String s) {
        if (TextUtils.isEmpty(s)) return;

        DdSource sd = SourceApi.getDefault().loadSource(s, null);
        if (sd != null) {
            String path = mPath.getSitedPath(sd.title);
            if (!TextUtils.isEmpty(path)) {
                File file = new File(path);
                if (file.isDirectory()) {
                    HintUtil.show(sd.title + "：已更新");
                } else {
                    HintUtil.show(sd.title + "：已安装");
                }
            }
            RxBus.getDefault().post(new RxEvent(RxEvent.EVENT_MAIN_SITED, sd.title, sd.url));
            FileUtil.saveText2Sdcard(mPath.getSitedPath(sd.title), s);
//            SiteDbApi.addSource(sd, sd.sited, true);
        }
    }

}
