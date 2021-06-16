package ru.gontarenko.codesharingplatform.service.impl;

import org.hibernate.service.spi.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import ru.gontarenko.codesharingplatform.enitity.CodeSnippet;
import ru.gontarenko.codesharingplatform.enitity.User;
import ru.gontarenko.codesharingplatform.exception.SnippetException;
import ru.gontarenko.codesharingplatform.repository.SnippetRepository;
import ru.gontarenko.codesharingplatform.secutrity.AccountType;
import ru.gontarenko.codesharingplatform.service.SnippetService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public final class SnippetServiceImpl implements SnippetService {
    private final SnippetRepository snippetRepository;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");


    @Autowired
    public SnippetServiceImpl(SnippetRepository snippetRepository) {
        this.snippetRepository = snippetRepository;
    }

    @Override
    public CodeSnippet findByLink(String link) {
        CodeSnippet snippet = snippetRepository.findByLink(link).orElseThrow(
                () -> new SnippetException("not found")
        );
        if (!checkExpirationTime(snippet)) {
            throw new SnippetException("snippet was expired");
        }
        return snippet;
    }

    private boolean checkExpirationTime(CodeSnippet snippet) {
        if (snippet.getExpirationTime() != null) {
            long timeBetween = Duration.between(
                    LocalDateTime.parse(snippet.getDateCreate(), formatter),
                    LocalDateTime.now()
            ).toSeconds();
            if(snippet.getExpirationTime() - timeBetween <= 0) {
                snippetRepository.delete(snippet);
                return false;
            }
        }
        return true;
    }

    @Override
    public List<CodeSnippet> findAll() {
        return snippetRepository.findAllByHiddenOrderByDateCreateDesc(false);
    }

    @Override
    public List<CodeSnippet> findPublicSnippetsByUser(User user) {
        return snippetRepository.findAllByUserAndHiddenOrderByDateCreateDesc(user, false);
    }

    @Override
    public List<CodeSnippet> findAllByUser(User user) {
        return snippetRepository.findAllByUserOrderByDateCreateDesc(user);
    }

    @Override
    public void save(CodeSnippet snippet, BindingResult bindingResult) {
        if (snippetRepository.existsByLink(snippet.getLink())) {
            throw new SnippetException("link already taken");
        }
        snippet.setDateCreate(LocalDateTime.now().format(formatter));
        if (snippet.getTitle() == null || snippet.getTitle().equals("")) {
            snippet.setTitle("Untitled");
        }
        snippetRepository.save(snippet);
        if (snippet.getLink() == null || snippet.getLink().equals("")) {
            snippet.setLink(String.valueOf(snippet.getId()));
            snippetRepository.save(snippet);
        }
    }

    @Override
    public void delete(String link, User user) {
        Optional<CodeSnippet> byLink = snippetRepository.findByLink(link);
        if (byLink.isPresent()) {
            CodeSnippet snippet = byLink.get();
            if (snippet.getUser().getId().equals(user.getId()) || user.getAccountType().equals(AccountType.ADMIN)) {
                snippetRepository.delete(snippet);
            }
        }
    }
}
