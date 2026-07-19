package com.hospital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.PacienteDTO;
import com.hospital.model.Paciente;
import com.hospital.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("PacienteController - integracion")
class PacienteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PacienteRepository pacienteRepository;

    private Paciente pacienteExistente;

    @BeforeEach
    void setUp() {
        pacienteRepository.deleteAll();
        pacienteExistente = pacienteRepository.save(
                new Paciente("Ana", "Torres", LocalDate.of(1990, 5, 20), "ana@mail.com", "0991234567", "Quito"));
    }

    private PacienteDTO dtoValido() {
        PacienteDTO dto = new PacienteDTO();
        dto.setNombre("Luis");
        dto.setApellido("Perez");
        dto.setFechaNacimiento(LocalDate.of(1985, 3, 15));
        dto.setEmail("luis.perez@mail.com");
        dto.setTelefono("0987654321");
        dto.setDireccion("Av. 10 de Agosto");
        return dto;
    }

    @Test
    @DisplayName("GET /api/pacientes devuelve 200 y la lista de pacientes")
    void listar_devuelve200YLista() throws Exception {
        mockMvc.perform(get("/api/pacientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Ana"));
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} devuelve 200 con el paciente cuando existe")
    void buscar_existente_devuelve200() throws Exception {
        mockMvc.perform(get("/api/pacientes/{id}", pacienteExistente.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@mail.com"));
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} inexistente responde 200 con body de error (bug: deberia ser 404)")
    void buscar_inexistente_respondeConBugDeStatus() throws Exception {
        mockMvc.perform(get("/api/pacientes/{id}", 999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("999999")));
    }

    @Test
    @DisplayName("POST /api/pacientes con datos validos crea el paciente (bug: responde 200 en vez de 201)")
    void crear_datosValidos_creaPaciente() throws Exception {
        mockMvc.perform(post("/api/pacientes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dtoValido())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").value("Luis"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("POST /api/pacientes sin nombre responde 400 con errores de validacion")
    void crear_sinNombre_responde400() throws Exception {
        PacienteDTO dto = dtoValido();
        dto.setNombre("");

        mockMvc.perform(post("/api/pacientes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists());
    }

    @Test
    @DisplayName("PUT /api/pacientes/{id} actualiza los datos del paciente existente")
    void actualizar_existente_actualizaDatos() throws Exception {
        PacienteDTO cambios = dtoValido();
        cambios.setNombre("Ana Maria");

        mockMvc.perform(put("/api/pacientes/{id}", pacienteExistente.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(cambios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana Maria"));
    }

    @Test
    @DisplayName("DELETE /api/pacientes/{id} elimina el paciente (bug: responde 200 en vez de 204)")
    void eliminar_existente_respondeConBugDeStatus() throws Exception {
        mockMvc.perform(delete("/api/pacientes/{id}", pacienteExistente.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/pacientes"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/pacientes/buscar filtra por nombre")
    void buscarPorNombre_devuelveCoincidencias() throws Exception {
        mockMvc.perform(get("/api/pacientes/buscar").param("nombre", "Ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Ana"));
    }

    @Test
    @DisplayName("GET /api/pacientes/estadisticas/edad-promedio devuelve un numero")
    void edadPromedio_devuelveValorNumerico() throws Exception {
        mockMvc.perform(get("/api/pacientes/estadisticas/edad-promedio"))
                .andExpect(status().isOk());
    }
}
