package com.fareltek.fsignal.section;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SectionRepository extends ReactiveCrudRepository<Section, Integer> {
    Flux<Section> findByEnabledTrue();
}
