package moe.wymc.yande.crawler;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PopularCrawler {

  private static URI getPopularUrl(LocalDate startDate, int offset, ChronoField field) {
    int year = startDate.getYear();
    int monthValue = startDate.getMonthValue();
    int dayOfMonth = startDate.getDayOfMonth();
    String uri = null;
    //    if (field.equals(ChronoField.YEAR)) {
    //      year -= offset;
    //      return URI.create("https://yande.re/post/popular_by_week?" + "&year=" + year);
    //    } else
    if (field.equals(ChronoField.MONTH_OF_YEAR)) {
      monthValue -= offset;
      uri =
          "https://yande.re/post/popular_by_month.json?" + "&month=" + monthValue + "&year=" + year;
    } else if (field.equals(ChronoField.DAY_OF_MONTH)) {
      dayOfMonth -= offset;
      uri =
          "https://yande.re/post/popular_by_day.json?day="
              + dayOfMonth
              + "&month="
              + monthValue
              + "&year="
              + year;
    }
    if (uri == null) {
      throw new RuntimeException();
    }
    return URI.create(
        uri + "&login=1522204732%40qq.com&password_hash=5e37a6cb517bbd137904d662aa4f23d41ab8a064");
  }

  private static String getDir(LocalDate startDate, int offset, ChronoField field) {
    if (field.equals(ChronoField.YEAR)) {
      return DateTimeFormatter.ofPattern("yyyy").format(startDate.minusYears(offset));
    }
    if (field.equals(ChronoField.MONTH_OF_YEAR)) {
      return DateTimeFormatter.ofPattern("yyyy-MM").format(startDate.minusMonths(offset));
    }
    if (field.equals(ChronoField.DAY_OF_MONTH)) {
      return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(startDate.minusDays(offset));
    }
    throw new RuntimeException();
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    int threads = 5;
    int pages = 1;
    ChronoField mode = ChronoField.DAY_OF_MONTH;
    String images = new File("D://Images/yande.re").getAbsolutePath();
   LocalDate localDate =
       LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse("2022-08-13"));
        // LocalDate localDate = LocalDate.now();
    crawlPopular(threads, pages, images, localDate, mode);
  }

  public static void crawlPopular(
      int threads, int pages, String images, LocalDate startDate, ChronoField field)
      throws IOException, InterruptedException {
    Semaphore semaphore = new Semaphore(threads);
    ThreadPoolExecutor threadPoolExecutor =
        new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    HttpClient httpClient =
        HttpClient.newBuilder()
            .executor(threadPoolExecutor)
            .proxy(ProxySelector.of(new InetSocketAddress(7890)))
            .build();

    startDate = startDate == null ? LocalDate.now() : startDate;
    for (int i = 0; i < pages; i++) {
      String dir = getDir(startDate, i, field);
      System.out.println("下载: " + dir);
      Path dirPath = Path.of(images, dir);
      if (!Files.exists(dirPath)) {
        Files.createDirectories(dirPath);
      }
      String html =
          httpClient
              .send(
                  HttpRequest.newBuilder(getPopularUrl(startDate, i, field))
                             .GET()
                             .header(
                               "user-agent",
                               "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36")
                             .header(
                               "accept",
                               "application/json,text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                             .header("accept-encoding", "utf8")
                             .header("accept-language", "en,zh-CN;q=0.9,zh;q=0.8")
                             .header("cache-control", "max-age=0")
                             .header(
                               "cookie",
                               "vote=1; tag-script=; hide-news-ticker=20201219; hide_resized_notice=1; mode=view; forum_post_last_read_at=%222021-09-05T09%3A30%3A01.253Z%22; user_id=376629; user_info=376629%3B30%3B0; has_mail=0; comments_updated=1; block_reason=; resize_image=0; show_advanced_editing=0; my_tags=; held_post_count=0; locale=zh_CN; session_yande-re=U7U0H5NdCfG%2B55JK8Ylp%2FIP%2BjuQeWdCmQUtN%2FeuDSQebcvJMtLUwlARt8gITDb7dUo5jBsvklKWKvo3mvzIClNRx71yn2k6QUdmml%2BWdygrniPeJikoeLrZdcnPgTUrzl29KILk47bM6FMYx1DTD7oZIo0iOLNz1OOm0zYuB7ns%2Fgg72RruGHAl1S1oMEupIM47tFF4PLUq9XJ8iw4kscaEwja%2FImwwELNr5vgejfndgjgXX9cgehXTOPKGcyJ3NQkDao%2F%2B20%2BWGplAPPV2IasEdc1NBRCDA66GcAvEbzvomv0RsbhRoeL5l7k6yFzb7KpieXKlgNk%2BSTH%2BBcWdMeK8c--I3M8LpmjqI7B7bpx--ZmfkLIwU1QYsb39l%2F8iP6A%3D%3D")
                             .build(),
                  HttpResponse.BodyHandlers.ofString())
              .body();
      Pattern compile = Pattern.compile("\"file_url\":\"(?<url>.+?)\"");
      Matcher matcher = compile.matcher(html);
      while (matcher.find()) {
        String url = matcher.group("url");
        String filename =
            URLDecoder.decode(url.substring(url.lastIndexOf("/") + 1), StandardCharsets.UTF_8)
                .replaceAll(":", "-")
                .replaceAll("[*?#^]", "");
        Path image = Path.of(images, dir, filename);
        if (Files.exists(image)) {
          System.out.println("文件已存在: " + filename);
          continue;
        }
        semaphore.acquire();
        httpClient
            .sendAsync(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofFile(image))
            .whenComplete(
                (pathHttpResponse, throwable) -> {
                  if (throwable == null) {
                    System.out.println("下载成功: " + filename);
                  } else {
                    try {
                      Files.deleteIfExists(image);
                    } catch (IOException e) {
                      semaphore.release();
                      throw new RuntimeException(e);
                    }
                    System.out.println("下载失败: " + filename);
                    throwable.printStackTrace();
                  }
                  semaphore.release();
                });
      }
    }
    semaphore.acquire(threads);
    threadPoolExecutor.shutdown();
  }
}
