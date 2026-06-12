package com.kafka.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ErrorPageTemplate {
    public ResponseEntity<String> badRequest(String title, String message, String returnUrl) {
        String body = """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <style>
                    body{margin:0;min-height:100vh;display:grid;place-items:center;background:#f2f4f6;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#191f28}
                    main{width:min(420px,calc(100%% - 32px));padding:28px;border:1px solid #e5e8eb;border-radius:8px;background:#fff;box-shadow:0 18px 50px rgb(25 31 40 / 8%%)}
                    h1{margin:0 0 10px;font-size:24px}p{margin:0 0 18px;color:#6b7684;line-height:1.5}a{display:inline-flex;align-items:center;min-height:42px;padding:0 15px;border-radius:8px;background:#0064ff;color:#fff;text-decoration:none;font-weight:800}
                  </style>
                </head>
                <body>
                  <main>
                    <h1>%s</h1>
                    <p>%s</p>
                    <a href="%s">돌아가기</a>
                  </main>
                </body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(title), escapeHtml(message), escapeHtml(returnUrl));
        return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
