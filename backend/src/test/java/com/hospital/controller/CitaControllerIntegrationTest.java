package com.hospital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.CitaDTO;
import com.hospital.model.Cita;
import com.hospital.model.Doctor;
import com.hospital.repository.CitaRepository;
import com.hospital.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("CitaController - integracion")
class CitaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    private Doctor doctor;
    private Cita citaExistente;

    @BeforeEach
    void setUp() {
        citaRepository.deleteAll();
        doctorRepository.deleteAll();
        doctor = doctorRepository.save(
                new Doctor("Carla", "Mendez", "Cardiologia", "carla@hospital.com", "022345678", "C-101"));
        citaExistente = citaRepository.save(
                new Cita(1L, doctor, LocalDateTime.now().plusDays(2), "Consulta inicial", "PROGRAMADA"));
    }

    private CitaDTO dtoValido(Long pacienteId, LocalDateTime fechaHora) {
        CitaDTO dto = new CitaDTO();
        dto.setPacienteId(pacienteId);
        dto.setDoctorId(doctor.getId());
        dto.setFechaHora(fechaHora);
        dto.setMotivo("Control rutinario");
        return dto;
    }

    @Test
    @DisplayName("GET /api/citas devuelve 200 y la lista de citas")
    void listar_devuelve200YLista() throws Exception {
        mockMvc.perform(get("/api/citas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/citas/{id} devuelve 200 con la cita cuando existe")
    void buscar_existente_devuelve200() throws Exception {
        mockMvc.perform(get("/api/citas/{id}", citaExistente.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivo").value("Consulta inicial"));
    }

    @Test
    @DisplayName("GET /api/citas/{id} inexistente responde 200 con body de error (bug: deberia ser 404)")
    void buscar_inexistente_respondeConBugDeStatus() throws Exception {
        mockMvc.perform(get("/api/citas/{id}", 999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/citas con doctor existente crea la cita (bug: responde 200 en vez de 201)")
    void crear_doctorExistente_creaCita() throws Exception {
        CitaDTO dto = dtoValido(5L, LocalDateTime.now().plusDays(3));

        mockMvc.perform(post("/api/citas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"));
    }

    @Test
    @DisplayName("POST /api/citas con doctor inexistente responde 200 con body de error (bug: deberia ser 404)")
    void crear_doctorInexistente_respondeConBugDeStatus() throws Exception {
        CitaDTO dto = dtoValido(5L, LocalDateTime.now().plusDays(3));
        dto.setDoctorId(999999L);

        mockMvc.perform(post("/api/citas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/citas con fecha pasada responde 400 (validacion @Future)")
    void crear_fechaPasada_responde400() throws Exception {
        CitaDTO dto = dtoValido(5L, LocalDateTime.now().minusDays(1));

        mockMvc.perform(post("/api/citas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/citas permite doble booking: mismo doctor y hora que una cita existente (bug)")
    void crear_dobleBooking_noEsRechazado() throws Exception {
        CitaDTO dto = dtoValido(2L, citaExistente.getFechaHora());

        mockMvc.perform(post("/api/citas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());

        mockMvc.perform(get("/api/citas/doctor/{doctorId}", doctor.getId()))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("PUT /api/citas/{id} actualiza fecha y motivo de una cita existente")
    void actualizar_existente_actualizaDatos() throws Exception {
        CitaDTO cambios = dtoValido(1L, LocalDateTime.now().plusDays(10));
        cambios.setMotivo("Reprogramada");

        mockMvc.perform(put("/api/citas/{id}", citaExistente.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(cambios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivo").value("Reprogramada"));
    }

    @Test
    @DisplayName("DELETE /api/citas/{id} elimina la cita (bug: responde 200 en vez de 204)")
    void eliminar_existente_respondeConBugDeStatus() throws Exception {
        mockMvc.perform(delete("/api/citas/{id}", citaExistente.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/citas"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/citas/paciente/{id} filtra citas por paciente")
    void listarPorPaciente_devuelveCoincidencias() throws Exception {
        mockMvc.perform(get("/api/citas/paciente/{pacienteId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/citas/rango-fechas devuelve 200 aunque inicio sea posterior a fin (bug: falta validacion)")
    void listarPorRangoFechas_inicioDespuesDeFin_noEsRechazado() throws Exception {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        String inicio = LocalDateTime.now().plusDays(10).format(fmt);
        String fin = LocalDateTime.now().format(fmt);

        mockMvc.perform(get("/api/citas/rango-fechas")
                        .param("inicio", inicio)
                        .param("fin", fin))
                .andExpect(status().isOk());
    }
}
