package com.fareltek.fsignal.section;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class SectionService {

    private final SectionRepository repository;

    public SectionService(SectionRepository repository) {
        this.repository = repository;
    }

    public Flux<Section> getAll() {
        return repository.findAll();
    }

    public Flux<Section> getAllEnabled() {
        return repository.findByEnabledTrue();
    }

    public Mono<Section> save(Section s) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (s.getId() == null) {
            if (s.getEnabled() == null) s.setEnabled(true);
            if (s.getPort() == null)    s.setPort(4001);
            s.setCreatedAt(now);
        }
        s.setUpdatedAt(now);
        return repository.save(s);
    }

    public Mono<Void> delete(int id) {
        return repository.deleteById(id);
    }
}
