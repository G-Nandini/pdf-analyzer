package com.nandhini.pdf_analyzer.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;

@Service
public class PdfService {

    private static final int MAX_CHARS = 15000;

    public String extractText(String pdfUrl) throws Exception {

        // Trust all SSL certificates
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        // Open connection with browser-like headers
        URL url = new URL(pdfUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", "application/pdf,*/*");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        // Check response code
        int responseCode = connection.getResponseCode();
        if (responseCode == 403) {
            throw new Exception("Access denied (403). This PDF URL is blocked.");
        }
        if (responseCode != 200) {
            throw new Exception("Failed to download PDF. HTTP code: " + responseCode);
        }

        // Check content type
        String contentType = connection.getContentType();
        if (contentType != null && contentType.contains("text/html")) {
            throw new Exception("URL returned an HTML page, not a PDF. Please use a direct PDF link.");
        }

        try (InputStream inputStream = connection.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text == null || text.isBlank()) {
                throw new Exception("PDF has no extractable text. It may be a scanned image PDF.");
            }

            return text.substring(0, Math.min(text.length(), MAX_CHARS));
        }
    }
}