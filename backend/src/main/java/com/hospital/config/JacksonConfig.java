package com.hospital.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Corrige un bug real (no intencional, no documentado en los comentarios del
 * codigo base): sin este modulo, Jackson no sabe serializar los proxies
 * Hibernate de las relaciones @ManyToOne(LAZY) como Cita.doctor o
 * HistoriaClinica.paciente/doctor, y GET /api/citas y
 * GET /api/historias-clinicas fallan siempre con 500
 * (InvalidDefinitionException sobre HibernateProxy).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        // Mantiene el comportamiento (y el bug intencional de N+1) que el
        // enunciado pide detectar: las relaciones lazy SI se inicializan al
        // serializar, en vez de devolverse siempre como null.
        module.enable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        return module;
    }
}
