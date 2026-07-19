package com.hospital.service;

import com.hospital.dto.HistoriaClinicaDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Doctor;
import com.hospital.model.HistoriaClinica;
import com.hospital.model.Paciente;
import com.hospital.repository.DoctorRepository;
import com.hospital.repository.HistoriaClinicaRepository;
import com.hospital.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoriaClinicaService")
class HistoriaClinicaServiceTest {

    @Mock
    private HistoriaClinicaRepository historiaRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private DoctorRepository doctorRepository;

    private HistoriaClinicaService historiaClinicaService;

    @BeforeEach
    void setUp() {
        historiaClinicaService = new HistoriaClinicaService(historiaRepository, pacienteRepository, doctorRepository);
    }

    private Paciente pacienteConId(Long id) {
        Paciente p = new Paciente("Ana", "Torres", LocalDate.of(1990, 5, 20), "ana@mail.com", "0991234567", "Quito");
        p.setId(id);
        return p;
    }

    private Doctor doctorConId(Long id) {
        Doctor d = new Doctor("Carla", "Mendez", "Cardiologia", "carla@hospital.com", "022345678", "C-101");
        d.setId(id);
        return d;
    }

    private HistoriaClinicaDTO dtoValido(Long pacienteId, Long doctorId, String diagnostico) {
        HistoriaClinicaDTO dto = new HistoriaClinicaDTO();
        dto.setPacienteId(pacienteId);
        dto.setDoctorId(doctorId);
        dto.setDiagnostico(diagnostico);
        dto.setTratamiento("Reposo y controles");
        dto.setObservaciones("Sin novedad");
        return dto;
    }

    // ---------- Caso feliz ----------

    @Nested
    @DisplayName("casos felices")
    class CasosFelices {

        @Test
        @DisplayName("listarTodas devuelve las historias ordenadas por fecha")
        void listarTodas_delegaAlRepositorio() {
            when(historiaRepository.findAllByOrderByFechaCreacionDesc())
                    .thenReturn(List.of(new HistoriaClinica(pacienteConId(1L), doctorConId(1L), "Gripe", "Reposo", "N/A")));

            List<HistoriaClinica> resultado = historiaClinicaService.listarTodas();

            assertThat(resultado).hasSize(1);
        }

        @Test
        @DisplayName("buscarPorId devuelve la historia cuando existe")
        void buscarPorId_existente_devuelveHistoria() {
            HistoriaClinica historia = new HistoriaClinica(pacienteConId(1L), doctorConId(1L), "Gripe", "Reposo", "N/A");
            historia.setId(10L);
            when(historiaRepository.findById(10L)).thenReturn(Optional.of(historia));

            HistoriaClinica resultado = historiaClinicaService.buscarPorId(10L);

            assertEquals(10L, resultado.getId());
        }

        @Test
        @DisplayName("crear con paciente y doctor existentes persiste la historia")
        void crear_pacienteYDoctorExistentes_persisteHistoria() {
            Paciente paciente = pacienteConId(1L);
            Doctor doctor = doctorConId(2L);
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
            when(doctorRepository.findById(2L)).thenReturn(Optional.of(doctor));
            when(historiaRepository.save(any(HistoriaClinica.class))).thenAnswer(inv -> {
                HistoriaClinica arg = inv.getArgument(0);
                arg.setId(50L);
                return arg;
            });

            HistoriaClinica resultado = historiaClinicaService.crear(dtoValido(1L, 2L, "Hipertension"));

            assertEquals(50L, resultado.getId());
            assertEquals(paciente, resultado.getPaciente());
            assertEquals(doctor, resultado.getDoctor());
            assertEquals("Hipertension", resultado.getDiagnostico());
        }

        @Test
        @DisplayName("crear sin doctorId (opcional) persiste la historia con doctor null")
        void crear_sinDoctorId_persisteConDoctorNull() {
            Paciente paciente = pacienteConId(1L);
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
            when(historiaRepository.save(any(HistoriaClinica.class))).thenAnswer(inv -> inv.getArgument(0));

            HistoriaClinica resultado = historiaClinicaService.crear(dtoValido(1L, null, "Chequeo general"));

            assertNull(resultado.getDoctor());
            verify(doctorRepository, never()).findById(any());
        }

        @Test
        @DisplayName("listarPorPaciente delega al repositorio con el id dado")
        void listarPorPaciente_delegaAlRepositorio() {
            when(historiaRepository.findByPacienteId(1L))
                    .thenReturn(List.of(new HistoriaClinica(pacienteConId(1L), null, "Gripe", null, null)));

            List<HistoriaClinica> resultado = historiaClinicaService.listarPorPaciente(1L);

            assertThat(resultado).hasSize(1);
        }
    }

    // ---------- Casos limite ----------

    @Nested
    @DisplayName("casos limite")
    class CasosLimite {

        @Test
        @DisplayName("crear no sanitiza el diagnostico: contenido tipo script se guarda tal cual (bug XSS)")
        void crear_diagnosticoConScript_seGuardaSinSanitizar() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteConId(1L)));
            when(historiaRepository.save(any(HistoriaClinica.class))).thenAnswer(inv -> inv.getArgument(0));

            String payload = "<script>alert('xss')</script>";
            HistoriaClinica resultado = historiaClinicaService.crear(dtoValido(1L, null, payload));

            assertThat(resultado.getDiagnostico()).isEqualTo(payload);
        }

        @Test
        @DisplayName("crear con diagnostico vacio no es rechazado por el servicio (bug: solo el DTO valida, no el service)")
        void crear_diagnosticoVacio_elServicioNoLoRechaza() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteConId(1L)));
            when(historiaRepository.save(any(HistoriaClinica.class))).thenAnswer(inv -> inv.getArgument(0));

            HistoriaClinica resultado = historiaClinicaService.crear(dtoValido(1L, null, ""));

            assertThat(resultado.getDiagnostico()).isEmpty();
        }
    }

    // ---------- Manejo de errores ----------

    @Nested
    @DisplayName("manejo de errores")
    class ManejoDeErrores {

        @Test
        @DisplayName("buscarPorId lanza ResourceNotFoundException cuando la historia no existe")
        void buscarPorId_inexistente_lanzaExcepcion() {
            when(historiaRepository.findById(99L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> historiaClinicaService.buscarPorId(99L));

            assertThat(ex.getMessage()).contains("99");
        }

        @Test
        @DisplayName("crear lanza ResourceNotFoundException cuando el paciente no existe y no guarda")
        void crear_pacienteInexistente_lanzaExcepcionYNoGuarda() {
            when(pacienteRepository.findById(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> historiaClinicaService.crear(dtoValido(404L, null, "Diagnostico")));

            verify(historiaRepository, never()).save(any());
        }

        @Test
        @DisplayName("crear lanza ResourceNotFoundException cuando el doctor no existe y no guarda")
        void crear_doctorInexistente_lanzaExcepcionYNoGuarda() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteConId(1L)));
            when(doctorRepository.findById(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> historiaClinicaService.crear(dtoValido(1L, 404L, "Diagnostico")));

            verify(historiaRepository, never()).save(any());
        }
    }
}
