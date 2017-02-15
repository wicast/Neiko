package seiko.neiko.ui.section1;

import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import seiko.neiko.dao.SourceApi;
import seiko.neiko.dao.engine.DdSource;
import seiko.neiko.models.Book;
import seiko.neiko.models.BookPage;
import seiko.neiko.models.DownSectionBean;
import seiko.neiko.models.ImgUrlBean;
import seiko.neiko.app.FragmentBase;
import seiko.neiko.utils.FileUtil;

import static seiko.neiko.dao.mPath.getSectionDownPath;
import static seiko.neiko.dao.mPath.getSectionImgPath;

/**
 * Created by Seiko on 2016/12/26. Y
 */

public abstract class Section1FragmentBase extends FragmentBase {

    public static int dtype;
    public static List<Book> imgList;
    public static int imgindex;
    public static String bookUrl;
    public static String bookName;
    public static List<BookPage> bookPages;
    //===========================
    protected DdSource source;
    //===========================
    protected int top = 0; //下拉辅助值
    protected int bot = 0; //上滑辅助值
    //===========================
    public static int t;  //上下拉加载的加减值，正常为1
    //===========================
    protected Section1View section1View;

    public Section1FragmentBase() {
        source = SourceApi.getDefault().getByUrl(bookUrl);
        bookPages = new ArrayList<>();        //章节内容集合
    }


    //==============================================
    /** 判断是否到list底部 */
    protected boolean isBottom(int index) {
        if (index < 0 || index > imgList.size() - 1) {
            toast("没有了");
            return true;
        }
        return false;
    }


    //==============================================
    /** 读取本地图片链接 */
    @Nullable
    protected List<Book> readPath(String name) {
        String path1 = getSectionDownPath(source.title, bookName, name);
        DownSectionBean bean = FileUtil.get(path1 + "/SectionBean", DownSectionBean.class);
        if (bean != null) {
            List<ImgUrlBean> list = bean.getList();

            List<Book> sectionList = new ArrayList<>();
            for (ImgUrlBean bean1:list) {
                Book book = new Book();
                book.setSection_url(bean1.getUrl());
                book.setPath(getSectionImgPath(path1, bean1));
                sectionList.add(book);
            }
            Log.d("AnimeSection1Activity", "读取本地数据");
            return sectionList;
        }
        return null;
    }


    //==============================================
    /** 开放接口 */
    protected void setSection1View(Section1View section1View) {this.section1View = section1View;}
}