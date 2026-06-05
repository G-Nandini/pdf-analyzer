package com.nandhini.pdf_analyzer.controller;

import com.nandhini.pdf_analyzer.model.AnalysisRequest;
import com.nandhini.pdf_analyzer.model.AnalysisResponse;
import com.nandhini.pdf_analyzer.service.GroqService;
import com.nandhini.pdf_analyzer.service.PdfService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PdfAnalysisController {

    @Autowired
    private PdfService pdfService;

    @Autowired
    private GroqService groqService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzePdf(@RequestBody AnalysisRequest request) {

        String pdfUrl = request.getPdfUrl();

        // Step 1: Validate input
        if (pdfUrl == null || pdfUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"PDF URL is required\"}");
        }

        try {
            // Step 2: Extract text from PDF
            String pdfText = pdfService.extractText(pdfUrl);

            if (pdfText == null || pdfText.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("{\"error\": \"Could not extract text from PDF\"}");
            }

            // Step 3: Send text to Groq and get raw response
            String groqRawResponse = groqService.analyzePdf(pdfText);

            // Step 4: Extract the JSON content from Groq response
            String content = extractContent(groqRawResponse);

            // Step 5: Parse and return as AnalysisResponse
            AnalysisResponse response = parseResponse(content);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"Something went wrong: " + e.getMessage() + "\"}");
        }
    }

    // Extract "content" field from Groq's response
    private String extractContent(String groqResponse) {
        try {
            int contentIndex = groqResponse.indexOf("\"content\":");
            if (contentIndex == -1) return groqResponse;

            int start = groqResponse.indexOf("\"", contentIndex + 10) + 1;
            int end = groqResponse.lastIndexOf("\"");

            String raw = groqResponse.substring(start, end);
            return raw
                    .replace("\\\"", "\"")
                    .replace("\\n", "")
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();
        } catch (Exception e) {
            return groqResponse;
        }
    }

    // Parse JSON string into AnalysisResponse object
    private AnalysisResponse parseResponse(String json) {
        AnalysisResponse response = new AnalysisResponse();

        response.setDocumentType(extractField(json, "documentType"));
        response.setTitle(extractField(json, "title"));
        response.setAuthors(extractField(json, "authors"));
        response.setSummary(extractField(json, "summary"));
        response.setKeyTakeaway(extractField(json, "keyTakeaway"));

        return response;
    }

    // Extract a single field value from JSON string
    private String extractField(String json, String fieldName) {
        try {
            String key = "\"" + fieldName + "\":";
            int keyIndex = json.indexOf(key);
            if (keyIndex == -1) return "Not found";

            int start = json.indexOf("\"", keyIndex + key.length()) + 1;
            int end = json.indexOf("\"", start);

            return json.substring(start, end);
        } catch (Exception e) {
            return "Not found";
        }
    }
}
