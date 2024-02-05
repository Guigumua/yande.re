package moe.wymc.yande.crawler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostCrawler {

//  public static String getNextPageUrl(int startPage, int offset) {
//    return "";
//  }

  public static void main(String[] args) throws IOException, InterruptedException {
    Properties properties = new Properties();
    if (args.length == 0) {
      File file = new File("config.properties");
      if (file.exists()) {
        FileInputStream fileInputStream = new FileInputStream(file);
        properties.load(fileInputStream);
        fileInputStream.close();
      }
    } else {
      if (args[0].equals("-h") || args[0].equals("--help")) {
        System.out.println(
            "-s --start \n\tstart page, default 1\n"
                + "-d --dir \n\timages directory, default ./images\n"
                + "-e --end \n\tend page, default 2147483647\n"
                + "-t --threads \n\tdownload thread quantity, default 5\n"
                + "--proxy-host \n\thttp proxy host. if only set '--proxy-port', this config default set 127.0.0.1, else don't use proxy\n"
                + "--proxy-port \n\thttp proxy port, if only set '--proxy-host', this config default set 1080, else don't use proxy\n");
        return;
      }
      String propertiesString = String.join("\n", args);
      ByteArrayInputStream inStream =
          new ByteArrayInputStream(propertiesString.getBytes(StandardCharsets.UTF_8));
      properties.load(inStream);
      inStream.close();
    }

    int start =
        Integer.parseInt(properties.getProperty("-s", properties.getProperty("--start", "1")));
    String dir = properties.getProperty("-d", properties.getProperty("--dir", "images"));
    int end =
        Integer.parseInt(
            properties.getProperty("-e", properties.getProperty("--end", "2147483647")));
    int threads =
        Integer.parseInt(properties.getProperty("-t", properties.getProperty("--threads", "5")));
    String host = properties.getProperty("--proxy-host");
    Integer port =
        Optional.ofNullable(properties.getProperty("--proxy-port"))
            .map(Integer::parseInt)
            .orElse(null);
    var tags = properties.getProperty("-T", properties.getProperty("--tags", null));
    HttpClient.Builder builder = HttpClient.newBuilder();
    if (host != null || port != null) {
      host = Objects.requireNonNullElse(host, "127.0.0.1");
      port = Objects.requireNonNullElse(port, 1080);
      builder.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(host, port)));
      System.out.println(
          MessageFormat.format(
              "User Config: \nStart Page: {0}\nEnd Page: {1}\nThreads: {2}\nProxy: {3}",
              start, end == Integer.MAX_VALUE ? "Unlimited" : end, threads, host + ":" + port));
    } else {
      System.out.println(
          MessageFormat.format(
              "User Config: \nStart Page: {0}\nEnd Page: {1}\nThreads: {2}",
              start, end == Integer.MAX_VALUE ? "Unlimited" : end, threads));
    }
    HttpClient httpClient = builder.build();
    Semaphore semaphore = new Semaphore(threads);
    File images = new File(dir);
    if (!images.exists()) {
      if (!images.mkdir()) {
        throw new RuntimeException("create directory failed!");
      }
    }
    Pattern fileUrlPattern = Pattern.compile("\"file_url\":\"(?<url>.+?)\"");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    // 等待下载完成再终止
                    semaphore.acquire(threads);
                    FileOutputStream fileOutputStream = new FileOutputStream("config.properties");
                    properties.store(fileOutputStream, "");
                    fileOutputStream.close();
                    httpClient
                        .executor()
                        .map(ThreadPoolExecutor.class::cast)
                        .ifPresent(ThreadPoolExecutor::shutdown);
                    System.out.println("Exit successfully");
                  } catch (Exception e) {
                    System.out.println("Exit with Exception");
                    e.printStackTrace();
                  }
                }));
    while (start <= end) {
      String pageUrl = "https://yande.re/post.json?page=" + start;
      if (tags != null) {
        pageUrl = pageUrl + "&tags=" + tags;
      }
      HttpRequest get =
          HttpRequest.newBuilder(URI.create(pageUrl))
              .header(
                  "user-agent",
                  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.69 Safari/537.36")
              .header(
                  "accept",
                  "application/json")
              .header("accept-encoding", "utf8")
              .header("accept-language", "en,zh-CN;q=0.9,zh;q=0.8")
              .header("cache-control", "max-age=0")
              .header(
                  "cookie",
                  "vote=1; login=1522204732%40qq.com; pass_hash=5e37a6cb517bbd137904d662aa4f23d41ab8a064; tag-script=; forum_post_last_read_at=%222021-11-06T04%3A30%3A37.503-04%3A00%22; session_yande-re=258OTQbWuRI0zemfDsFFYjwJbb323lXS4%2BK6h2recOReQg5PBgIPVrZEF8rJwR0v12ndiRbG8DUpkb%2BhL2cngBPvrXUNXmbhitw56gs%2BRPDzQBaXnBK9097n33m855AWcJ3fG94SMJ3oXmW%2BIMf7gqofFnZAuGgV0I2E2HR7gG65K2ydYAXU8D4AzLZtNTfcKFzbX9VELYC7qw26wUJUt1I3ry4qUFCDx%2F8tWv%2FE9O5LKeFUROtf%2FLa5XZ97YZcONXGxoMKZLVzpaDMl4riZfBP9%2BsVSKnLMN%2Bgdud2BAFsZ72AI4trhwFX9IPtZ5g%3D%3D--1vtIWTpvWxh4O2Hs--BjmTdWzhPeNfYA2ZBkfjDg%3D%3D")
              .build();
      HttpResponse<String> send = httpClient.send(get, HttpResponse.BodyHandlers.ofString());
      if (send.statusCode() != 200) {
        System.out.println("Get page failed!");
        System.out.println(send.body());
        return;
      }
      String body = send.body();
      if (body.equals("[]")) {
        break;
      }
      Matcher matcher = fileUrlPattern.matcher(body);
      while (matcher.find()) {
        String url = matcher.group("url");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        String fileName = url.substring(url.lastIndexOf('/') + 10);
        File image = new File(images, fileName);
        if (!image.exists()) {
          semaphore.acquire();
          httpClient
              .sendAsync(request, HttpResponse.BodyHandlers.ofFile(image.toPath()))
              .handle(
                  (pathHttpResponse, throwable) -> {
                    semaphore.release();
                    if (pathHttpResponse != null) {
                      System.out.println("success: " + fileName);
                    } else if (throwable != null) {
                      throwable.printStackTrace();
                      try {
                        Files.delete(image.toPath());
                      } catch (IOException e) {
                        e.printStackTrace();
                      }
                    }
                    return null;
                  });
        } else {
          System.out.println("exits: " + fileName);
        }
      }
      System.out.println("page of " + start + " download");
      start++;
      properties.setProperty("--start", String.valueOf(start));
    }
  }
}
