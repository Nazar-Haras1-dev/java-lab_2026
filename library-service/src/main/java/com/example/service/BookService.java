package com.example.library.service;
import com.example.library.aop.Audited;
import com.example.library.async.EmailSender;
import com.example.library.aop.RateLimited;
import com.example.library.aop.Retry;
import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import com.example.library.exception.DuplicateResourceException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.mapper.BookMapper;
import com.example.library.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository repository;
    private final BookMapper mapper;
    private final EmailSender emailSender;

    public BookService(BookRepository repository, BookMapper mapper, EmailSender emailSender) {
        this.repository = repository;
        this.mapper = mapper;
        this.emailSender = emailSender;
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
        BookDto saved = mapper.toDto(repository.save(mapper.toEntity(dto)));
        notifyBookCreated(saved);
        return saved;
    }

    /** Fire-and-forget: лист іде у фоні; якщо пул переповнений, створення книги НЕ падає. */
    private void notifyBookCreated(BookDto saved) {
        try {
            emailSender.sendBookCreatedEmail(saved.getTitle(), saved.getIsbn());
        } catch (TaskRejectedException ex) {
            log.warn("Email for book {} skipped: async executor is saturated", saved.getId());
        }
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