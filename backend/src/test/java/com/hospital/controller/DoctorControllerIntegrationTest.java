package com.hospital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.DoctorDTO;
import com.hospital.model.Doctor;
import com.hospital.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("DoctorController - integracion")
class DoctorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DoctorRepository doctorRepository;

    private Doctor doctorExistente;

    @BeforeEach
    void setUp() {
        doctorRepository.deleteAll();
        doctorExistente = doctorRepository.save(
                new Doctor("Carla", "Mendez", "Cardiologia", "carla@hospital.com", "022345678", "C-101"));
    }

    private DoctorDTO dtoValido() {
        DoctorDTO dto = new DoctorDTO();
        dto.setNombre("Juan");
        dto.setApellido("Reyes");
        dto.setEspecialidad("Pediatria");
        dto.setEmail("juan.reyes@hospital.com");
        dto.setTelefono("022998877");
        dto.setConsultorio("C-305");
        return dto;
    }

    @Test
    @DisplayName("GET /api/doctores devuelve 200 y la lista de doctores")
    void listar_devuelve200YLista() throws Exception {
        mockMvc.perform(get("/api/doctores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/doctores/{id} devuelve 200 con el doctor cuando existe")
    void buscar_existente_devuelve200() throws Exception {
        mockMvc.perform(get("/api/doctores/{id}", doctorExistente.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidad").value("Cardiologia"));
    }

    @Test
    @DisplayName("GET /api/doctores/{id} inexistente responde 200 con body de error (bug: deberia ser 404)")
    void buscar_inexistente_respondeConBugDeStatus() throws Exception {
        mockMvc.perform(get("/api/doctores/{id}", 999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/doctores con datos validos crea el doctor (bug: responde 200 en vez de 201)")
    void crear_datosValidos_creaDoctor() throws Exception {
        mockMvc.perform(post("/api/doctores")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dtoValido())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.especialidad").value("Pediatria"));
    }

    @Test
    @DisplayName("POST /api/doctores sin especialidad NO es rechazado por validacion (bug: falta @NotBlank)")
    void crear_sinEspecialidad_noEsRechazado() throws Exception {
        DoctorDTO dto = dtoValido();
        dto.setEspecialidad(null);

        mockMvc.perform(post("/api/doctores")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidad").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/doctores sin nombre responde 400 con errores de validacion")
    void crear_sinNombre_responde400() throws Exception {
        DoctorDTO dto = dtoValido();
        dto.setNombre("");

        mockMvc.perform(post("/api/doctores")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists());
    }

    @Test
    @DisplayName("PUT /api/doctores/{id} actualiza los datos del doctor existente")
    void actualizar_existente_actualizaDatos() throws Exception {
        DoctorDTO cambios = dtoValido();
        cambios.setEspecialidad("Neurologia");

        mockMvc.perform(put("/api/doctores/{id}", doctorExistente.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(cambios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidad").value("Neurologia"));
    }

    @Test
    @DisplayName("DELETE /api/doctores/{id} elimina el doctor (bug: responde 200 en vez de 204)")
    void eliminar_existente_respondeConBugDeStatus() throws Exception {
        mockMvc.perform(delete("/api/doctores/{id}", doctorExistente.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/doctores"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/doctores/buscar-especialidad reproduce la inyeccion SQL (bug real)")
    void buscarEspecialidadInsegura_conPayloadSqlInjection_devuelveTodoIgnorandoElFiltro() throws Exception {
        doctorRepository.save(new Doctor("Marta", "Ruiz", "Dermatologia", "marta@hospital.com", "022111222", "C-102"));

        // Payload que rompe la concatenacion ('%" + input + "%') y comenta el resto de la query,
        // forzando una condicion siempre verdadera: la busqueda deja de filtrar por especialidad.
        mockMvc.perform(get("/api/doctores/buscar-especialidad").param("q", "' OR '1'='1' -- "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
