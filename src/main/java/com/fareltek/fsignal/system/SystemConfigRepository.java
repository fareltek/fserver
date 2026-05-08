package com.fareltek.fsignal.system;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface SystemConfigRepository extends ReactiveCrudRepository<SystemConfig, String> {

    @Query("SELECT * FROM system_config ORDER BY config_group, config_key")
    Flux<SystemConfig> findAllOrdered();
}
