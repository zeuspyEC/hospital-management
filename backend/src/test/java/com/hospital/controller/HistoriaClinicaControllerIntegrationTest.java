package com.hospital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.HistoriaClinicaDTO;
import com.hospital.model.Doctor;
import com.hospital.model.HistoriaClinica;
import com.hospital.model.Paciente;
import com.hospital.repository.DoctorRepository;
import com.hospital.repository.HistoriaClinicaRepository;
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
@DisplayName("HistoriaClinicaController - integracion")
class HistoriaClinicaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HistoriaClinicaRepository historiaRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    private Paciente paciente;
    private Doctor doctor;
    private HistoriaClinica historiaExistente;

    @BeforeEach
    void setUp() {
        historiaRepository.deleteAll();
        pacienteRepository.deleteAll();
        doctorRepository.deleteAll();

        paciente = pacienteRepository.save(
                new Paciente("Ana", "Torres", LocalDate.of(1990, 5, 20), "ana@mail.com", "0991234567", "Quito"));
        doctor = doctorRepository.save(
                new Doctor("Carla", "Mendez", "Cardiologia", "carla@hospital.com", "022345678", "C-101"));
        historiaExistente = historiaRepository.save(
                new HistoriaClinica(paciente, doctor, "Gripe comun", "Reposo e hidratacion", "Sin novedad"));
    }

    private HistoriaClinicaDTO dtoValido() {
        HistoriaClinicaDTO dto = new HistoriaClinicaDTO();
        dto.setPacienteId(paciente.getId());
        dto.setDoctorId(doctor.getId());
        dto.setDiagnostico("Hipertension leve");
        dto.setTratamiento("Control de presion mensual");
        dto.setObservaciones("Paciente estable");
        return dto;
    }

    @Test
    @DisplayName("GET /api/historias-clinicas devuelve 200 y la lista de historias")
    void listar_devuelve200YLista() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/historias-clinicas/{id} devuelve 200 con la historia cuando existe")
    void buscar_existente_devuelve200() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas/{id}", historiaExistente.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostico").value("Gripe comun"));
    }

    @Test
    @DisplayName("GET /api/historias-clinicas/{id} inexistente responde 200 con body de error (bug: deberia ser 404)")
    void buscar_inexistente_respondeConBugDeStatus() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas/{id}", 999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/historias-clinicas con paciente y doctor validos crea la historia (bug: 200 en vez de 201)")
    void crear_datosValidos_creaHistoria() throws Exception {
        mockMvc.perform(post("/api/historias-clinicas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dtoValido())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.diagnostico").value("Hipertension leve"));
    }

    @Test
    @DisplayName("POST /api/historias-clinicas con diagnostico tipo <script> se persiste sin sanitizar (bug XSS)")
    void crear_diagnosticoConScript_sePersisteSinSanitizar() throws Exception {
        HistoriaClinicaDTO dto = dtoValido();
        dto.setDiagnostico("<script>alert('xss')</script>");

        mockMvc.perform(post("/api/historias-clinicas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostico").value("<script>alert('xss')</script>"));
    }

    @Test
    @DisplayName("POST /api/historias-clinicas sin diagnostico responde 400 con errores de validacion")
    void crear_sinDiagnostico_responde400() throws Exception {
        HistoriaClinicaDTO dto = dtoValido();
        dto.setDiagnostico("");

        mockMvc.perform(post("/api/historias-clinicas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.diagnostico").exists());
    }

    @Test
    @DisplayName("POST /api/historias-clinicas con paciente inexistente responde 200 con body de error (bug: deberia ser 404)")
    void crear_pacienteInexistente_respondeConBugDeStatus() throws Exception {
        HistoriaClinicaDTO dto = dtoValido();
        dto.setPacienteId(999999L);

        mockMvc.perform(post("/api/historias-clinicas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/historias-clinicas/paciente/{id} filtra historias por paciente")
    void listarPorPaciente_devuelveCoincidencias() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas/paciente/{pacienteId}", paciente.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/historias-clinicas/doctor/{id} filtra historias por doctor")
    void listarPorDoctor_devuelveCoincidencias() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas/doctor/{doctorId}", doctor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
