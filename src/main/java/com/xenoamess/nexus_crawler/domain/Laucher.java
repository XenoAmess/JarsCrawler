package com.xenoamess.nexus_crawler.domain;

import com.xenoamess.nexus_crawler.utils.HCUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nutz.lang.Stopwatch;
import org.nutz.log.Log;
import org.nutz.log.Logs;

/**
 * Start Class
 *
 * @Author 蛋蛋
 * @DATE 2017年11月11日15:41:11
 */
public class Laucher {

    public void run() {
        Log log = Logs.get();
        final Laucher laucher = new Laucher();
        List<String> urls;
        try {
            urls = laucher.getUrls();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (urls.size() == 0) {
            log.debug("未查到爬取地址,请检查配置文件!");
            return;
        }
        ExecutorService executorService = Executors.newFixedThreadPool(10); // 创建ExecutorService 连接池创建固定的10个初始线程
        for (final String url : urls) {
            if (url.length() < 10) {
                continue;
            }
            //使用线程池提高效率
            //开启对应线程
            executorService.execute(() -> laucher.crawleing(url));
        }
    }


    public static void main(String[] args) {
        new Laucher().run();
    }

    /**
     * 获取a标签集合
     *
     * @param finalUrl
     * @return
     */
    @NotNull
    public Elements getElements(@NotNull String finalUrl) {
        CloseableHttpClient client = HCUtils.getClient();
        HttpGet get = HCUtils.getHttpGet(finalUrl);
        try {
            CloseableHttpResponse response = client.execute(get);
            String s = EntityUtils.toString(response.getEntity(), "utf-8");

            Document doc = Jsoup.parse(s);

//            Elements elements = doc.getElementsByAttributeValue("cellspacing", "10");
//            if (elements == null || elements.size() == 0) {
//                return new Elements();
//            }
//            return elements.get(0).getElementsByTag("a");

            return doc.getElementsByTag("a");
        } catch (IOException ignored) {
        }
        return new Elements();
    }

    /**
     * 获取a标签集合
     *
     * @param beginUrl 当前页面URL
     * @param linkUrl 标签url
     * @return
     */
    @NotNull
    public Pair<String, Elements> getElements(@NotNull String beginUrl, @Nullable String linkUrl) {

        String url;
        Elements res;
        if (linkUrl == null) {
            url = beginUrl;
            res = getElements(url);
            return Pair.of(url, res);
        }

        if (beginUrl.endsWith("/")) {
            beginUrl = beginUrl.substring(0, beginUrl.length() - 1);
        }
        if (linkUrl.startsWith("/")) {
            linkUrl = linkUrl.substring(1);
        }

        url = beginUrl + "/" + linkUrl;
        res = getElements(url);
        if (!CollectionUtils.isEmpty(res)) {
            return Pair.of(url, res);
        }
        url = beginUrl + "/" + linkUrl.replace("/", "");
        res = getElements(url);
        if (!CollectionUtils.isEmpty(res)) {
            return Pair.of(url, res);
        }
        url = linkUrl;
        res = getElements(url);
        if (!CollectionUtils.isEmpty(res)) {
            return Pair.of(url, res);
        }
        Logs.get().error("getElements fails for " + beginUrl + " , " + linkUrl);
        url = beginUrl + "/" + linkUrl;
        return Pair.of(url, res);
    }

    public List<String> getUrls() throws IOException {
        URL resource = this.getClass().getClassLoader().getResource("crawlers.text");

        List<String> res = new ArrayList<>();
        try (
                FileInputStream fileInputStream = new FileInputStream(resource.getPath());
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
        ) {
            res.add(bufferedReader.readLine());
        }
        return res;
    }


    public void crawleing(String url) {
        Log log = Logs.get();
        Stopwatch sw = Stopwatch.begin();
        int totalCount = 0;
        log.debug("抓取地址:" + url);
        Elements projects = getElements(url, null).getRight();
        log.debug("发现 [" + projects.size() + "] 个开源项目");
        if (!CollectionUtils.isEmpty(projects)) {
//            System.out.println(projects.size());
            for (int i = 0; i < projects.size(); i++) {
                Element element = projects.get(i);
                if ((element.html()).contains("Directory") || (element.html()).contains(".")) {
                    continue;
                }
                log.debug("开始爬取第" + i + "个项目,名称: [" + element.html() + "]...");
                Pair<String, Elements> pair2 = getElements(url, element.attr("href"));
                String url2 = pair2.getLeft();
                Elements modules = pair2.getRight();
                if (CollectionUtils.isEmpty(modules)) {
                    continue;
                }
                log.debug("项目: [" + element.html() + "] 下有 [" + modules.size() + "] 个子项目...");
                for (int j = 0; j < modules.size(); j++) {
                    Element model = modules.get(j);
                    if (model.html().contains("Directory")) {
                        continue;
                    }
                    if (model.html().contains(".")) {
                        //说明直接就是版本号,没有子项目

                        if (model.html().contains("Directory") || model.html().contains("../")) {
                            continue;
                        }
                        log.debug("开始爬取第 [" + j + "]个版本,版本号: [" + model.html() + "]");

                        Pair<String, Elements> pair3 = getElements(url2, model.attr("href"));
                        String url3 = pair3.getLeft();
                        Elements jars = pair3.getRight();
                        if (CollectionUtils.isEmpty(jars)) {
                            continue;
                        }
                        log.debug("该版本下发现: [" + jars.size() + "] 个文件");
                        int jarCount = 0;
                        int outher = 0;
                        for (int l = 0; l < jars.size(); l++) {
                            Element jar = jars.get(l);
                            String jarName = jar.html();
                            if (jarName.contains("Directory") || jarName.contains("../")) {
                                continue;
                            }


                            log.debug("发现文件:" + jar.html());
                            download(url3, jar.attr("href"));
                            jarCount++;
                            totalCount++;
//                            System.out.println(jar.html());
//                            if(jar.html().lastIndexOf("sha1") > 0 ||jar.html().lastIndexOf("Parent") > 0){
//
//                            }
                            log.debug("项目: [" + element.html() + "] 下的第 [" + j + "]个子项目,名称: [" + model.html() + "] " +
                                    "下的第 [" + j + "]个版本,名称为: [" + jar.html() + "] 下共有: [" + jarCount + "] 个jar包,其他文件:" +
                                    " [" + outher + "] 个");
                        }

                    } else {//获取子项目...
                        log.debug("开始爬取 [" + element.html() + "] 项目下第[" + j + "]个子项目,名称: [" + model.html() + "]");

                        Pair<String, Elements> pair3 = getElements(url2, model.attr("href"));
                        String url3 = pair3.getLeft();
                        Elements verions = pair3.getRight();
                        log.debug("子项目: [" + model.html() + "] 下有 [" + verions.size() + "] 个版本");
                        for (int k = 0; k < verions.size(); k++) {
                            Element version = verions.get(k);
                            if (version.html().contains("Directory") || version.html().contains("../")) {
                                continue;
                            }
                            log.debug("开始爬去第 [" + k + "]个版本,版本号: [" + version.html() + "]");
                            Pair<String, Elements> pair4 = getElements(url3, version.attr("href"));
                            String url4 = pair4.getLeft();
                            Elements jars = pair4.getRight();
                            if (CollectionUtils.isEmpty(jars)) {
                                continue;
                            }
                            log.debug("该版本下发现: [" + jars.size() + "] 个文件");
                            int jarCount = 0;
                            for (int l = 0; l < jars.size(); l++) {
                                Element jar = jars.get(l);
                                String jarName = jar.html();
                                if (jarName.contains("Directory") || jarName.contains("../")) {
                                    continue;
                                }

                                log.debug("发现文件:" + jar.html());
                                download(url3, jar.attr("href"));
                                jarCount++;
                                totalCount++;

                                log.debug("项目: [" + element.html() + "] 下的第 [" + j + "]个子项目,名称: [" + model.html() +
                                        "] 下的第 [" + k + "]个版本,名称为: [" + jar.html() + "] 下共有: [" + jarCount + "] " +
                                        "个文件。");
                            }
                        }
                    }

                }
            }
        }
        sw.stop();
        log.debug("爬取任务完成,耗时:" + sw.toString() + ",总共爬取jar包数量: [" + totalCount + "] 个");
    }


    private void download(@NotNull String beginUrl, @Nullable String linkUrl) {
        String url;
        if (linkUrl == null) {
            url = beginUrl;
            try {
                download(url);
            } catch (IOException ignored) {
            }
            return;
        }

        if (beginUrl.endsWith("/")) {
            beginUrl = beginUrl.substring(0, beginUrl.length() - 1);
        }
        if (linkUrl.startsWith("/")) {
            linkUrl = linkUrl.substring(1);
        }

        url = beginUrl + "/" + linkUrl;
        try {
            download(url);
            return;
        } catch (IOException ignored) {
        }
        url = beginUrl + "/" + linkUrl.replace("/", "");
        try {
            download(url);
            return;
        } catch (IOException ignored) {
        }
        url = linkUrl;
        try {
            download(url);
            return;
        } catch (IOException ignored) {
        }
        Logs.get().error("download fails for " + beginUrl + " , " + linkUrl);
    }

    private void download(@NotNull String fileUrl) throws IOException {
        System.out.println(fileUrl);
        String filePath = fileUrl
                .replace("http://", "./output/")
                .replace("https://", "./output/")
                .replace(":", "_");
        download(fileUrl, filePath, "GET");
    }

    private static void download(@NotNull String url, @NotNull String filePath, @NotNull String method) throws IOException {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        HttpURLConnection conn = null;
        // 建立链接
        URL httpUrl = new URL(url);
        conn = (HttpURLConnection) httpUrl.openConnection();
        //以Post方式提交表单，默认get方式
        conn.setRequestMethod(method);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        // post方式不能使用缓存
        conn.setUseCaches(false);
        //连接指定的资源
        conn.connect();
        //获取网络输入流

        try (InputStream inputStream = conn.getInputStream();
             BufferedInputStream bis = new BufferedInputStream(inputStream);
             FileOutputStream fileOut = new FileOutputStream(filePath);
             BufferedOutputStream bos = new BufferedOutputStream(fileOut);
        ) {
            byte[] buf = new byte[4096];
            int length = bis.read(buf);
            //保存文件
            while (length != -1) {
                bos.write(buf, 0, length);
                length = bis.read(buf);
            }
        }
        conn.disconnect();

    }

}
