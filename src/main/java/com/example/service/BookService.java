package com.example.library.service;
import com.example.library.aop.Audited;
import com.example.library.aop.RateLimited;
import com.example.library.aop.Retry;
import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import com.example.library.exception.DuplicateResourceException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.mapper.BookMapper;
import com.example.library.repository.BookRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository repository;
    private final BookMapper mapper;

    public BookService(BookRepository repository, BookMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Retry(maxAttempts = 3)
    public List<BookDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Retry(maxAttempts = 3)
    public BookDto findById(Long id) {
        return mapper.toDto(getEntity(id));
    }

    public long count() {
        return repository.count();
    }
    @RateLimited(maxCalls = 10, perSeconds = 60)
    @Audited(action = "CREATE_BOOK")
    @Transactional
    public BookDto create(BookDto dto) {
        if (repository.existsByIsbn(dto.getIsbn())) {
            throw new DuplicateResourceException("Book with ISBN " + dto.getIsbn() + " already exists");
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Audited(action = "UPDATE_BOOK")
    @Transactional
    public BookDto update(Long id, BookDto dto) {
        Book book = getEntity(id);
        if (repository.existsByIsbnAndIdNot(dto.getIsbn(), id)) {
            throw new DuplicateResourceException("Book with ISBN " + dto.getIsbn() + " already exists");
        }
        mapper.updateEntity(dto, book);
        return mapper.toDto(repository.save(book));
    }

    // Обмеження доступу на рівні методу: видаляти може тільки ADMIN
    @Audited(action = "DELETE_BOOK")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    private Book getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book with id " + id + " not found"));
    }
}