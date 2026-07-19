package com.hospital.service;

import com.hospital.dto.CitaDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Cita;
import com.hospital.model.Doctor;
import com.hospital.repository.CitaRepository;
import com.hospital.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CitaService")
class CitaServiceTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private DoctorRepository doctorRepository;

    private CitaService citaService;

    @BeforeEach
    void setUp() {
        citaService = new CitaService(citaRepository, doctorRepository);
    }

    private Doctor doctorConId(Long id) {
        Doctor d = new Doctor("Carla", "Mendez", "Cardiologia", "carla@hospital.com", "022345678", "C-101");
        d.setId(id);
        return d;
    }

    private CitaDTO dtoValido(Long pacienteId, Long doctorId, LocalDateTime fechaHora) {
        CitaDTO dto = new CitaDTO();
        dto.setPacienteId(pacienteId);
        dto.setDoctorId(doctorId);
        dto.setFechaHora(fechaHora);
        dto.setMotivo("Control rutinario");
        return dto;
    }

    // ---------- Caso feliz ----------

    @Nested
    @DisplayName("casos felices")
    class CasosFelices {

        @Test
        @DisplayName("listarTodas devuelve todas las citas del repositorio")
        void listarTodas_devuelveListaCompleta() {
            Cita c1 = new Cita(1L, doctorConId(1L), LocalDateTime.now().plusDays(1), "Consulta", "PROGRAMADA");
            when(citaRepository.findAll()).thenReturn(List.of(c1));

            List<Cita> resultado = citaService.listarTodas();

            assertThat(resultado).hasSize(1);
        }

        @Test
        @DisplayName("buscarPorId devuelve la cita cuando existe")
        void buscarPorId_existente_devuelveCita() {
            Cita cita = new Cita(1L, doctorConId(1L), LocalDateTime.now().plusDays(1), "Consulta", "PROGRAMADA");
            cita.setId(10L);
            when(citaRepository.findById(10L)).thenReturn(Optional.of(cita));

            Cita resultado = citaService.buscarPorId(10L);

            assertEquals(10L, resultado.getId());
        }

        @Test
        @DisplayName("crear guarda la cita cuando el doctor existe")
        void crear_doctorExistente_persisteCita() {
            Doctor doctor = doctorConId(1L);
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
            when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> {
                Cita arg = inv.getArgument(0);
                arg.setId(100L);
                return arg;
            });

            LocalDateTime fecha = LocalDateTime.now().plusDays(2);
            Cita resultado = citaService.crear(dtoValido(5L, 1L, fecha));

            assertEquals(100L, resultado.getId());
            assertEquals(5L, resultado.getPacienteId());
            assertEquals(doctor, resultado.getDoctor());
            assertEquals("PROGRAMADA", resultado.getEstado());
        }

        @Test
        @DisplayName("actualizar modifica fecha, motivo y estado de una cita existente")
        void actualizar_citaExistente_actualizaCampos() {
            Cita cita = new Cita(5L, doctorConId(1L), LocalDateTime.now().plusDays(1), "Consulta", "PROGRAMADA");
            cita.setId(10L);
            when(citaRepository.findById(10L)).thenReturn(Optional.of(cita));
            when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

            LocalDateTime nuevaFecha = LocalDateTime.now().plusDays(5);
            CitaDTO cambios = dtoValido(5L, 1L, nuevaFecha);
            cambios.setEstado("CONFIRMADA");

            Cita resultado = citaService.actualizar(10L, cambios);

            assertEquals(nuevaFecha, resultado.getFechaHora());
            assertEquals("CONFIRMADA", resultado.getEstado());
        }

        @Test
        @DisplayName("listarPorPaciente delega al repositorio con el id dado")
        void listarPorPaciente_delegaAlRepositorio() {
            when(citaRepository.findByPacienteId(5L)).thenReturn(
                    List.of(new Cita(5L, doctorConId(1L), LocalDateTime.now().plusDays(1), "Consulta", "PROGRAMADA")));

            List<Cita> resultado = citaService.listarPorPaciente(5L);

            assertThat(resultado).hasSize(1);
            verify(citaRepository).findByPacienteId(5L);
        }
    }

    // ---------- Casos limite ----------

    @Nested
    @DisplayName("casos limite")
    class CasosLimite {

        @Test
        @DisplayName("crear no valida que el paciente exista (bug: no hay FK ni chequeo)")
        void crear_pacienteInexistente_igualPersiste() {
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctorConId(1L)));
            when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

            Long pacienteQueNoExiste = 999999L;
            Cita resultado = citaService.crear(dtoValido(pacienteQueNoExiste, 1L, LocalDateTime.now().plusDays(1)));

            assertEquals(pacienteQueNoExiste, resultado.getPacienteId());
            verify(citaRepository).save(any(Cita.class));
        }

        @Test
        @DisplayName("crear permite doble booking: mismo doctor, misma hora (bug: falta validacion)")
        void crear_dobleBooking_noLanzaExcepcion() {
            Doctor doctor = doctorConId(1L);
            LocalDateTime mismaHora = LocalDateTime.now().plusDays(3);
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
            when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

            citaService.crear(dtoValido(1L, 1L, mismaHora));
            citaService.crear(dtoValido(2L, 1L, mismaHora));

            verify(citaRepository, times(2)).save(any(Cita.class));
        }

        @Test
        @DisplayName("listarPorRangoFechas no valida que inicio sea antes que fin (bug: falta validacion)")
        void listarPorRangoFechas_inicioDespuesDeFin_delegaSinValidar() {
            LocalDateTime inicio = LocalDateTime.now().plusDays(10);
            LocalDateTime fin = LocalDateTime.now();
            when(citaRepository.findByFechaHoraBetween(inicio, fin)).thenReturn(List.of());

            List<Cita> resultado = citaService.listarPorRangoFechas(inicio, fin);

            assertThat(resultado).isEmpty();
            verify(citaRepository).findByFechaHoraBetween(inicio, fin);
        }
    }

    // ---------- Manejo de errores ----------

    @Nested
    @DisplayName("manejo de errores")
    class ManejoDeErrores {

        @Test
        @DisplayName("buscarPorId lanza ResourceNotFoundException cuando la cita no existe")
        void buscarPorId_inexistente_lanzaExcepcion() {
            when(citaRepository.findById(50L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> citaService.buscarPorId(50L));

            assertThat(ex.getMessage()).contains("50");
        }

        @Test
        @DisplayName("crear lanza ResourceNotFoundException cuando el doctor no existe y no guarda la cita")
        void crear_doctorInexistente_lanzaExcepcionYNoGuarda() {
            when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> citaService.crear(dtoValido(1L, 99L, LocalDateTime.now().plusDays(1))));

            verify(citaRepository, never()).save(any());
        }

        @Test
        @DisplayName("actualizar propaga ResourceNotFoundException cuando la cita no existe")
        void actualizar_inexistente_propagaExcepcion() {
            when(citaRepository.findById(7L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> citaService.actualizar(7L, dtoValido(1L, 1L, LocalDateTime.now().plusDays(1))));
            verify(citaRepository, never()).save(any());
        }
    }
}
