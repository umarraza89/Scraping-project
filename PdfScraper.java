package scrap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.*;

public class PdfScraper {

    private static final String BASE_URL = "https://papers.nips.cc/paper/";
    private static final String DOWNLOAD_DIR = "mydocuments";
    private static final String[] FILE_EXTENSIONS = {".pdf", ".docx", ".txt", ".avis"};
    private static final int THREAD_POOL_SIZE = 30; // Adjust based on system capability

    private static final ConcurrentHashMap<String, Boolean> completedDownloads = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                downloadDir.mkdir();
            }

            for (int year = 2021; year >= 2021; year--) {
                final int currentYear = year; // Create a final copy of the year variable
                String yearUrl = BASE_URL + currentYear;
                System.out.println("\nScraping year: " + currentYear);

                Document document = Jsoup.connect(yearUrl).get();
                Elements paperElements = document.select("li.conference a[href], li.none a[href]");

                for (Element paperElement : paperElements) {
                    String paperTitle = paperElement.text();
                    String paperUrl = "https://papers.nips.cc" + paperElement.attr("href");

                    // Use the final copy of the year variable in the lambda
                    executor.submit(() -> processPaper(httpClient, paperTitle, paperUrl, currentYear));
                }
            }

            // Shutdown the executor and wait for all tasks to complete
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.MINUTES);

            // Print a summary of completed downloads
            System.out.println("\nDownload Summary:");
            completedDownloads.forEach((file, status) -> 
                System.out.println(file + " -> " + (status ? "Success" : "Failed"))
            );

            System.out.println("\nAll downloads complete!");

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void processPaper(CloseableHttpClient httpClient, String paperTitle, String paperUrl, int year) {
        try {
            System.out.println("\nProcessing: " + paperTitle);
            Document paperDocument = Jsoup.connect(paperUrl).get();
            boolean downloaded = false;

            Elements pdfLinks = paperDocument.select("a[href$=.pdf]");
            if (!pdfLinks.isEmpty()) {
                // Iterate over all PDF links and download each one
                for (int i = 0; i < pdfLinks.size(); i++) {
                    Element pdfLink = pdfLinks.get(i);
                    if (pdfLink != null) {
                        String fileUrl = "https://papers.nips.cc" + pdfLink.attr("href");
                        String fileSuffix = i == 0 ? "_1.pdf" : "_2.pdf"; // Add suffix to differentiate files
                        if (downloadFile(httpClient, fileUrl, paperTitle, fileSuffix)) {
                            completedDownloads.put(paperTitle + fileSuffix, true);
                            downloaded = true;
                        }
                    }
                }
            }

            if (!downloaded) {
                System.out.println("No valid document found for: " + paperTitle);
                completedDownloads.put(paperTitle, false);
            }
        } catch (IOException e) {
            System.out.println("Failed to process: " + paperTitle);
            completedDownloads.put(paperTitle, false);
        }
    }

    private static boolean downloadFile(CloseableHttpClient httpClient, String fileUrl, String paperTitle, String fileSuffix) {
        try {
            String fileName = paperTitle.replaceAll("[^a-zA-Z0-9.-]", "_") + fileSuffix;
            File outputFile = new File(DOWNLOAD_DIR, fileName);

            HttpGet request = new HttpGet(fileUrl);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getEntity() != null) {
                    FileUtils.copyInputStreamToFile(response.getEntity().getContent(), outputFile);
                    System.out.println("Downloaded: " + fileName);
                    return true;
                } else {
                    System.out.println("No content received for: " + paperTitle);
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to download: " + paperTitle);
        }
        return false;
    }
}