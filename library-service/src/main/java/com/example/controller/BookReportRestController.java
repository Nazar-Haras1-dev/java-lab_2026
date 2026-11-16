package com.example.library.controller;

import com.example.library.async.BookReport;
import com.example.library.async.BookReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookReportRestController {

    private final BookReportService reportService;

    public BookReportRestController(BookReportService reportService) {
        this.reportService = reportService;
    }

    /** Два повільні виклики (PDF + видавець) виконуються паралельно, відповідь ~1 с, а не ~2 с. */
    @GetMapping("/{id}/report")
    public BookReport report(@PathVariable Long id) {
        return reportService.buildReport(id);
    }
}
