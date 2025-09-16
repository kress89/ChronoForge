package io.chronoforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.chronoforge.store.EventStore;
import io.chronoforge.store.InMemoryEventStore;
import io.chronoforge.store.pg.PostgresEventStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class Beans {
    @Bean
    @Profile("inmem")
    EventStore inMemoryStore() {
        return new InMemoryEventStore();
    }

    @Bean
    @Profile("pg")
    EventStore postgresStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        return new PostgresEventStore(jdbc, mapper);
    }
}
