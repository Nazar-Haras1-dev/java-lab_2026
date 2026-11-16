package com.example.library.async;

/** Підсумок двох незалежних повільних викликів. degraded=true - частину даних не вдалося отримати. */
public record BookReport(Long bookId, String title, int pdfBytes, String publisher, boolean degraded) { }
