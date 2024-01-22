package ru.iskhakov.createdocumentapi.downloader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.extern.java.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Log
public class CrptApi {

    private static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final HttpClient httpClient;
    private TimeUnit timeUnit;
    private int requestLimit;
    private final Semaphore requestSemaphore;
    private final ScheduledExecutorService scheduler;
    ObjectMapper objectMapper = new ObjectMapper();

    public CrptApi(HttpClient httpClient, TimeUnit timeUnit, long duration, int requestLimit) {
        if (requestLimit < 1) {
            throw new IllegalArgumentException("Incorrect number of requests");
        }
        this.httpClient = httpClient;
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestSemaphore = new Semaphore(requestLimit, true);
        this.scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(() -> requestSemaphore.release(requestLimit - requestSemaphore.availablePermits()),
                0, duration, timeUnit);
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        String jsonDocument = convertDocumentToJson(document);
        HttpRequest httpRequest = generateHttpRequest(jsonDocument, signature);
        requestSemaphore.acquire();
        sendRequest(httpRequest);
    }

    private String convertDocumentToJson(Document document) throws JsonProcessingException {
        return objectMapper.writeValueAsString(document);
    }

    private HttpRequest generateHttpRequest(String jsonDocument, String signature) {
        return HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();
    }

    private void sendRequest(HttpRequest httpRequest) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            log.info("Successful request");
        } else {
            log.warning("Bad request: " + response.statusCode());
        }
    }

    @Builder
    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<Product> products;
        private String regDate;
        private String regNumber;
    }

    @Builder
    public static class Description {
        private String participantInn;
    }

    @Builder
    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }
}
