package com.example.library.controller;

import com.example.library.dto.BookDto;
import com.example.library.exception.DuplicateResourceException;
import com.example.library.service.BookService;
import jakarta.validation.Valid;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class BookUiController {

    private final BookService service;

    public BookUiController(BookService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/books";
    }

    @GetMapping("/books")
    public String list(Model model, @AuthenticationPrincipal OidcUser user) {
        model.addAttribute("books", service.findAll());
        model.addAttribute("username", user.getPreferredUsername());
        model.addAttribute("roles", user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_USER"))
                .toList());
        return "books/list";
    }

    @GetMapping("/books/new")
    public String newForm(Model model) {
        model.addAttribute("book", new BookDto());
        return "books/form";
    }

    @PostMapping("/books")
    public String create(@Valid @ModelAttribute("book") BookDto book, BindingResult binding) {
        if (binding.hasErrors()) {
            return "books/form";
        }
        try {
            service.create(book);
        } catch (DuplicateResourceException ex) {
            binding.rejectValue("isbn", "duplicate", ex.getMessage());
            return "books/form";
        }
        return "redirect:/books";
    }

    @GetMapping("/books/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("book", service.findById(id));
        return "books/form";
    }

    @PostMapping("/books/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("book") BookDto book,
                         BindingResult binding) {
        book.setId(id);
        if (binding.hasErrors()) {
            return "books/form";
        }
        try {
            service.update(id, book);
        } catch (DuplicateResourceException ex) {
            binding.rejectValue("isbn", "duplicate", ex.getMessage());
            return "books/form";
        }
        return "redirect:/books";
    }

    @PostMapping("/books/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);          // для USER буде 403 через @PreAuthorize
        return "redirect:/books";
    }
}