package org.noear.sited;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by yuety on 15/8/2. Y
 */
public class SdSource {

    protected final SdAttributeList attrs = new SdAttributeList();

    protected int schema = 0;
    private boolean isDebug;//是否为调试模式

    public String url_md5;
    public String url;  //源首页
    public String title; //标题
    public String expr;//匹配源的表达式


    public void release() {
        js.release();
        attrs.clear();
        head.release();
        body.release();

        script.release();
    }

    private String _encode;//编码
    String encode() {return _encode;}

    private String _ua;
    String ua() {
        if (TextUtils.isEmpty(_ua))
            return Util.defUA;
        else
            return _ua;
    }

    protected String _cookies;
    public String cookies() {return _cookies;}
    public void setCookies(String cookies) {_cookies = cookies;}

//    public void delCache(String key) {
//        Util.cache.delete(key);
//    }
    //-------------------------------

    protected SdNodeSet head;
    protected SdNodeSet body;
    public SdNodeSet getBody() {return body;}

    //-------------------------------
    JsEngine js;//不能作为属性
    SdJscript script;

    private Element root;
    protected String  xmlBodyName;
    protected String  xmlHeadName;
    protected String  xmlScriptName;

//    public JsEngine getJs() {
//        return js;
//    }
    //
    //--------------------------------
    //
    protected SdSource() {}

    public SdSource(Application app, String xml) throws Exception {
        doInit(app, xml);

        xmlHeadName = "head";
        xmlBodyName = "body";
        xmlScriptName = "script";

        doLoad(app);
    }

    protected void doInit(Application app, String xml) throws Exception {

        Util.tryInitCache(app.getApplicationContext());

        root = Util.getXmlroot(xml);

        {
            NamedNodeMap temp = root.getAttributes();
            for (int i = 0, len = temp.getLength(); i < len; i++) {
                Node p = temp.item(i);
                attrs.set(p.getNodeName(), p.getNodeValue());
            }
        }

        {
            NodeList temp = root.getChildNodes();
            for (int i = 0, len = temp.getLength(); i < len; i++) {
                Node p = temp.item(i);

                if (p.getNodeType() == Node.ELEMENT_NODE && !p.hasAttributes() && p.hasChildNodes()) {
                    if(p.getChildNodes().getLength()==1) {
                        Node p2 = p.getFirstChild();
                        if (p2.getNodeType() == Node.TEXT_NODE) {
                            attrs.set(p.getNodeName(), p2.getNodeValue());
                        }
                    }
                }
            }
        }

        schema  = attrs.getInt("schema");
        isDebug = attrs.getInt("debug") > 0;
    }

    protected void doLoad(Application app){
        xmlHeadName = attrs.getString("head", xmlHeadName);
        xmlBodyName = attrs.getString("body", xmlBodyName);
        xmlScriptName = attrs.getString("script", xmlScriptName);

        //1.head
        head = Util.createNodeSet(this);
        head.buildForNode(Util.getElement(root, xmlHeadName));

        if(schema == 0) {
            head.attrs = this.attrs;
        } else {
            head.attrs.addAll(this.attrs);
        }

        //2.body
        body = Util.createNodeSet(this);
        body.buildForNode(Util.getElement(root, xmlBodyName));

        title = head.attrs.getString("title");
        expr  = head.attrs.getString("expr");
        url   = head.attrs.getString("url");
        url_md5 = Util.md5(url);

        _encode = head.attrs.getString("encode");
        _ua     = head.attrs.getString("ua");

        //----------
        //3.script :: 放后面
        //
        js = new JsEngine(app, this);
        script = new SdJscript(this, Util.getElement(root, xmlScriptName));
        script.loadJs(app, js);

        root = null;
    }

    protected boolean DoCheck(String url, String cookies, boolean isFromAuto) {return true;}

    protected void DoTraceUrl(String url, String args, SdNode config) {}


    //
    //------------
    //
    public boolean isMatch(String url) {
        Pattern pattern = Pattern.compile(expr);
        Matcher m = pattern.matcher(url);

        return m.find();
    }

    public void loadJs(String jsCode){js.loadJs(jsCode);}

    public String callJs(SdNode config, String funAttr, String... args) {
        return js.callJs(config.attrs.getString(funAttr), args);
    }
    //-------------



    private String parse(SdNode config, String url, String html) {
        Log.v("parse", url);
        Log.v("parse", html == null ? "null" : html);

        if(TextUtils.isEmpty(config.parse)) {
            return html;
        }

        if ("@null".equals(config.parse)) { //如果是@null，说明不需要通过js解析
            return html;
        } else {
            String temp = js.callJs(config.parse, url, html);

            if (temp == null) {
                Log.v("parse.rst", "null" + "\r\n\n");
            } else {
                Log.v("parse.rst", temp + "\r\n\n");
            }
            return temp;
        }
    }

    private String parseUrl(SdNode config, String url, String html) {
        Log.v("parseUrl", url);
        Log.v("parseUrl", html == null ? "null" : html);

        String temp = js.callJs(config.parseUrl, url, html);

        if(temp == null)
            return "";
        else
            return temp;
    }


    //
    //---------------------------------------
    //
    public void getNodeViewModel(ISdViewModel viewModel, SdNodeSet nodeSet, boolean isUpdate, SdSourceCallback callback) {

        __AsyncTag tag = new __AsyncTag();
        DataContext dataContext = new DataContext();

        for (ISdNode node : nodeSet.nodes()) {
            SdNode n = (SdNode) node;
            doGetNodeViewModel2(viewModel, isUpdate, tag, n.url, null, n, dataContext, callback);
        }

        if (tag.total == 0) {
            callback.run(1);
        }
    }

    public void getNodeViewModel(ISdViewModel viewModel, boolean isUpdate, String key, int page, SdNode config, SdSourceCallback callback) {

        try {
            __AsyncTag tag = new __AsyncTag();
            DataContext dataContext = new DataContext();

            doGetNodeViewModel1(viewModel, isUpdate, tag, config.url, key, page, config, dataContext, callback);
        }catch (Exception ex){
            callback.run(1);
        }
    }

    public void getNodeViewModel(ISdViewModel viewModel, boolean isUpdate, int page, String url, SdNode config, SdSourceCallback callback) {

        config.url = url;

        __AsyncTag tag = new __AsyncTag();
        DataContext dataContext = new DataContext();

        doGetNodeViewModel1(viewModel, isUpdate, tag, url, null, page, config, dataContext, callback);
    }

    private void doGetNodeViewModel1(final ISdViewModel viewModel,
                                     final boolean isUpdate,
                                     final __AsyncTag tag, String url, String key, int page,
                                     final SdNode config,
                                     final DataContext dataContext,
                                     final SdSourceCallback callback) {

        final HttpMessage msg = new HttpMessage();

        page += config.getAddPage(); //加上增减量

        if (key != null && !TextUtils.isEmpty(config.getAddKey())) {//如果有补充关键字
            key = key + " " + config.getAddKey();
        }

        if (key == null)
            msg.url = config.getUrl(config.url, page);
        else
            msg.url = config.getUrl(config.url, key, page);

        if (TextUtils.isEmpty(msg.url)) {
            callback.run(-3);
            return;
        }

        if (!TextUtils.isEmpty(msg.url)) {
            msg.rebuild(config);

            if ("post".equals(config.method)) {
                msg.rebuildForm(page, key);
            } else {
                msg.url = msg.url.replace("@page", page + "");
                if (key != null) {
                    msg.url = msg.url.replace("@key", Util.urlEncode(key, config));
                }
            }

            final int pageX = page;
            final String keyX = key;

            msg.callback = new HttpCallback() {
                @Override
                public void run(Integer code, HttpMessage sender, String text, String url302) {
                    tag.value++;
                    if (code == 1) {

                        if(TextUtils.isEmpty(url302)) {
                            url302 = sender.url;
                        }

                        if (!TextUtils.isEmpty(config.parseUrl)) { //url需要解析出来(多个用;隔开)
                            List<String> newUrls = new ArrayList<>();
                            String[] rstUrls = parseUrl(config, url302, text).split(";");

                            for(String url1 : rstUrls) {
                                if(url1.length() == 0)
                                    continue;

                                if(url1.startsWith(Util.NEXT_CALL)){
                                    HttpMessage msg0 = new HttpMessage();
                                    msg0.url = url1.replace(Util.NEXT_CALL,"")
                                            .replace("GET::","")
                                            .replace("POST::","");

                                    msg0.rebuild(config);

                                    if(url1.indexOf("POST::") > 0) {
                                        msg0.method = "post";
                                        msg0.rebuildForm(pageX, keyX);
                                    } else {
                                        msg0.method = "get";
                                    }

                                    msg0.callback = msg.callback;

                                    Util.http(SdSource.this, isUpdate, msg0);
                                } else {
                                    newUrls.add(url1);
                                }
                            }

                            if(newUrls.size() > 0) {
                                doParseUrl_Aft(viewModel, config, isUpdate, newUrls, sender.form, tag, dataContext, callback);
                            }
                            return;
                        }
                        else {
                            doParse_noAddin(viewModel, config, url302, text);
                        }
                    }

                    //callback.run(code);
                    if (tag.total == tag.value) {
                        callback.run(code);
                    }
                }
            };

            tag.total++;
            Util.http(this, isUpdate, msg);
        }

        if (config.hasAdds()) {
            //2.2 获取副内容（可能有多个）
            for (SdNode n1 : config.adds()) {
                if (n1.isEmptyUrl())
                    continue;

                String urlA = (TextUtils.isEmpty(n1.url) ? url : n1.url);
                doGetNodeViewModel1(viewModel, isUpdate, tag, urlA, key, page, n1, dataContext, callback);
            }
        }
    }


    public void getNodeViewModel(ISdViewModel viewModel, boolean isUpdate, String url, SdNode config, SdSourceCallback callback) {
        getNodeViewModel(viewModel, isUpdate, url, config, null, callback);
    }

    public void getNodeViewModel(final ISdViewModel viewModel,
                                 final boolean isUpdate,
                                 final String url,
                                 final SdNode config,
                                 final Map<String,String> args,
                                 final SdSourceCallback callback) {
        //需要对url进行转换成最新的格式（可能之前的旧的格式缓存）

        try {
//            if (!DoCheck(url, cookies(), true)) {
//                Log.d("SdSource", "getNodeViewModel:99");
//                callback.run(99);
//                return;
//            }

            __AsyncTag tag = new __AsyncTag();
            DataContext dataContext = new DataContext();
            doGetNodeViewModel2(viewModel, isUpdate, tag, url, args, config, dataContext, callback);

            if (tag.total == 0) {
                callback.run(1);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            callback.run(-1);
        }
    }

    private void doGetNodeViewModel2(final ISdViewModel viewModel,
                                     final boolean isUpdate,
                                     final __AsyncTag tag, String url,
                                     final Map<String, String> args,
                                     final SdNode config,
                                     final DataContext dataContext,
                                     final SdSourceCallback callback) {
        //需要对url进行转换成最新的格式（可能之前的旧的格式缓存）
        if (config.isEmpty())
            return;

        if (config.hasItems() && TextUtils.isEmpty(config.parse)) {
            viewModel.loadByConfig(config);
        }

        if ("@null".equals(config.method)) {
            String url2 = config.getUrl(url, args);

            if (TextUtils.isEmpty(config.parse))
                viewModel.loadByJson(config, url2);
            else
                viewModel.loadByJson(config, parse(config, url2, Util.toJson(args)));
            return;
        }

        if (!TextUtils.isEmpty(config.parse) && !TextUtils.isEmpty(url)) {//如果没有url 和 parse，则不处理
            final HttpMessage msg = new HttpMessage();
            if(args!=null) {
                msg.form = args;
            }

            //为doParseUrl_Aft服务(要在外围)
            //Map<Integer, String> dataList = new HashMap<>();//如果有多个地址，需要排序

            //2.获取主内容
            msg.url = config.getUrl(url,args);

            //有缓存的话，可能会变成同步了
            msg.rebuild(config);
            msg.rebuildForm(args);

            msg.callback = new HttpCallback() {
                @Override
                public void run(Integer code, HttpMessage sender, String text, String url302) {
                    tag.value++;

                    if (code == 1) {

                        if(TextUtils.isEmpty(url302)) {
                            url302 = sender.url;
                        }

                        if (!TextUtils.isEmpty(config.parseUrl)) { //url需要解析出来(多个用;隔开)
                            List<String> newUrls = new ArrayList<>();
                            String[] rstUrls = parseUrl(config, url302, text).split(";");

                            for(String url1 : rstUrls) {
                                if (url1.length() == 0)
                                    continue;

                                if (url1.startsWith(Util.NEXT_CALL)) {
                                    Util.log(SdSource.this, "CALL::url=", url1);

                                    HttpMessage msg0 = new HttpMessage();
                                    msg0.url = url1.replace(Util.NEXT_CALL, "")
                                            .replace("GET::", "")
                                            .replace("POST::", "");

                                    msg0.rebuild(config);

                                    if (url1.indexOf("POST::") > 0) {
                                        msg0.method = "post";
                                        msg0.rebuildForm(args);
                                    } else {
                                        msg0.method = "get";
                                    }
                                    msg0.callback = msg.callback;

                                    tag.total++;
                                    Util.http(SdSource.this, isUpdate, msg0);
                                } else {
                                    newUrls.add(url1);
                                }
                            }

                            if(newUrls.size()>0) {
                                doParseUrl_Aft(viewModel, config, isUpdate, newUrls, args, tag, dataContext, callback);
                            }
                            return;//下面的代码被停掉
                        }
                        else {
                            doParse_hasAddin(viewModel, config, url302, text);
                        }
                    }

                    if (tag.total == tag.value) {
                        callback.run(code);
                    }
                }
            };

            tag.total++;
            Util.http(this, isUpdate, msg);
        }

        if (config.hasAdds()) {
            //2.2 获取副内容（可能有多个）
            for (SdNode n1 : config.adds()) {
                if (n1.isEmptyUrl())
                    continue;

                String urlA = (TextUtils.isEmpty(n1.url) ? url : n1.url);
                doGetNodeViewModel2(viewModel,isUpdate,tag,urlA,args,n1,dataContext,callback);
            }
        }
    }

    private void doParseUrl_Aft(final ISdViewModel viewModel,
                                final SdNode config,
                                final boolean isUpdate, List<String> newUrls,
                                final Map<String, String> args,
                                final __AsyncTag tag,
                                final DataContext dataContext,
                                final SdSourceCallback callback) {
        for (final String newUrl2 : newUrls) {
            tag.total++;
            //tag.num --;

            final HttpMessage msg = new HttpMessage(config, newUrl2, tag.total, args);
            msg.callback = new HttpCallback() {
                @Override
                public void run(Integer code2, HttpMessage sender, String text2, String url302) {
                    tag.value++;

                    if (code2 == 1) {
                        if(TextUtils.isEmpty(url302)) {
                            url302 = newUrl2;
                        }
                        doParse_noAddinForTmp(dataContext, config, url302, text2, sender.tag);
                    }

                    if (tag.total == tag.value) {
                        for(SdNode cfg : dataContext.nodes()){

                            DataBlock dataList = dataContext.get(cfg);
                            List<String> jsonList = new ArrayList<>();

                            for (Integer i = 1; i <= tag.total; i++) { //安排序加载内容
                                if (dataList.containsKey(i)) {
                                    jsonList.add(dataList.get(i));
                                }
                            }

                            String[] strAry = new String[jsonList.size()];
                            jsonList.toArray(strAry);
                            viewModel.loadByJson(cfg, strAry);
                        }

                        callback.run(code2);
                    }
                }
            };
            Util.http(this, isUpdate, msg);
        }
    }

    private void doParse_noAddin(ISdViewModel viewModel, SdNode config, String url, String text) {
        String json = this.parse(config, url, text);
        if (isDebug)
            Util.log(this, config, url, json, 0);

        if (json != null)
            viewModel.loadByJson(config, json);
    }

    private void doParse_hasAddin(ISdViewModel viewModel, SdNode config, String url, String text) {
        String json = this.parse(config, url, text);

        if (isDebug) {
            Util.log(this, config, url, json, 0);
        }

        if (json != null) {
            viewModel.loadByJson(config, json);

            if (config.hasAdds()) { //没有url的add
                for (SdNode n2 : config.adds()) {
                    if (!n2.isEmptyUrl())
                        continue;

                    String json2 = this.parse(n2, url, text);
                    if (isDebug)
                        Util.log(this, n2, url, json2, 0);


                    if (json2 != null)
                        viewModel.loadByJson(n2, json2);
                }
            }
        }
    }

    private void doParse_noAddinForTmp(DataContext dataContext, SdNode config, String url, String text, int tag) {
        String json = this.parse(config, url, text);

        if (isDebug)
            Util.log(this, config, url, json,tag);

        if (json != null)
            dataContext.add(config, tag, json);
    }
}
