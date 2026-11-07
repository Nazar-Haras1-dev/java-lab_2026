package com.example.library.dto;

import jakarta.validation.constraints.*;
import com.example.library.validation.ValidIsbn;

public class BookDto {

    private Long id;

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "Author is required")
    @Size(max = 255)
    private String author;

    @NotBlank(message = "ISBN is required")
    @Size(max = 20)
    @ValidIsbn
    private String isbn;

    @Min(value = 1000, message = "Year must be >= 1000")
    @Max(value = 2100, message = "Year must be <= 2100")
    private Integer publishedYear;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public Integer getPublishedYear() { return publishedYear; }
    public void setPublishedYear(Integer publishedYear) { this.publishedYear = publishedYear; }
}