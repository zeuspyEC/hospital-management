package com.hospital.service;

import com.hospital.dto.PacienteDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Paciente;
import com.hospital.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PacienteService")
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    private PacienteService pacienteService;

    @BeforeEach
    void setUp() {
        pacienteService = new PacienteService(pacienteRepository);
    }

    private Paciente pacienteConId(Long id, String nombre, String apellido, LocalDate fechaNacimiento) {
        Paciente p = new Paciente(nombre, apellido, fechaNacimiento, nombre.toLowerCase() + "@mail.com", "0999999999", "Quito");
        p.setId(id);
        return p;
    }

    private PacienteDTO dtoValido() {
        PacienteDTO dto = new PacienteDTO();
        dto.setNombre("Ana");
        dto.setApellido("Torres");
        dto.setFechaNacimiento(LocalDate.of(1990, 5, 20));
        dto.setEmail("ana.torres@mail.com");
        dto.setTelefono("0991234567");
        dto.setDireccion("Av. Amazonas");
        return dto;
    }

    // ---------- Caso feliz ----------

    @Nested
    @DisplayName("casos felices")
    class CasosFelices {

        @Test
        @DisplayName("listarTodos devuelve todos los pacientes del repositorio")
        void listarTodos_devuelveListaCompleta() {
            List<Paciente> pacientes = Arrays.asList(
                    pacienteConId(1L, "Ana", "Torres", LocalDate.of(1990, 5, 20)),
                    pacienteConId(2L, "Luis", "Perez", LocalDate.of(1985, 1, 10))
            );
            when(pacienteRepository.findAll()).thenReturn(pacientes);

            List<Paciente> resultado = pacienteService.listarTodos();

            assertThat(resultado).hasSize(2).containsExactlyElementsOf(pacientes);
        }

        @Test
        @DisplayName("buscarPorId devuelve el paciente cuando existe")
        void buscarPorId_existente_devuelvePaciente() {
            Paciente paciente = pacienteConId(1L, "Ana", "Torres", LocalDate.of(1990, 5, 20));
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

            Paciente resultado = pacienteService.buscarPorId(1L);

            assertEquals("Ana", resultado.getNombre());
            assertEquals(1L, resultado.getId());
        }

        @Test
        @DisplayName("crear guarda el paciente mapeado desde el DTO y lo devuelve")
        void crear_dtoValido_persisteYDevuelvePaciente() {
            PacienteDTO dto = dtoValido();
            when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> {
                Paciente arg = invocation.getArgument(0);
                arg.setId(10L);
                return arg;
            });

            Paciente resultado = pacienteService.crear(dto);

            ArgumentCaptor<Paciente> captor = ArgumentCaptor.forClass(Paciente.class);
            verify(pacienteRepository).save(captor.capture());
            Paciente guardado = captor.getValue();

            assertEquals(10L, resultado.getId());
            assertEquals(dto.getNombre(), guardado.getNombre());
            assertEquals(dto.getApellido(), guardado.getApellido());
            assertEquals(dto.getEmail(), guardado.getEmail());
            assertThat(guardado.getActivo()).isTrue();
        }

        @Test
        @DisplayName("actualizar sobrescribe los campos del paciente existente")
        void actualizar_pacienteExistente_actualizaCampos() {
            Paciente existente = pacienteConId(1L, "Ana", "Torres", LocalDate.of(1990, 5, 20));
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

            PacienteDTO cambios = dtoValido();
            cambios.setNombre("Ana Maria");
            cambios.setTelefono("0987654321");

            Paciente resultado = pacienteService.actualizar(1L, cambios);

            assertEquals("Ana Maria", resultado.getNombre());
            assertEquals("0987654321", resultado.getTelefono());
            verify(pacienteRepository).save(existente);
        }

        @Test
        @DisplayName("calcularEdadPromedio calcula el promedio correcto con datos validos")
        void calcularEdadPromedio_conPacientes_calculaPromedio() {
            LocalDate hace30 = LocalDate.now().minusYears(30);
            LocalDate hace20 = LocalDate.now().minusYears(20);
            when(pacienteRepository.findAll()).thenReturn(Arrays.asList(
                    pacienteConId(1L, "A", "A", hace30),
                    pacienteConId(2L, "B", "B", hace20)
            ));

            double promedio = pacienteService.calcularEdadPromedio();

            int edad30 = Period.between(hace30, LocalDate.now()).getYears();
            int edad20 = Period.between(hace20, LocalDate.now()).getYears();
            assertEquals((edad30 + edad20) / 2.0, promedio);
        }
    }

    // ---------- Casos limite ----------

    @Nested
    @DisplayName("casos limite")
    class CasosLimite {

        @Test
        @DisplayName("calcularEdadPromedio con lista vacia produce NaN (bug de division por cero, no excepcion)")
        void calcularEdadPromedio_sinPacientes_devuelveNaN() {
            when(pacienteRepository.findAll()).thenReturn(Collections.emptyList());

            double promedio = pacienteService.calcularEdadPromedio();

            assertThat(promedio).isNaN();
        }

        @Test
        @DisplayName("buscarPorId con id negativo no es rechazado antes de ir al repositorio (bug: falta validacion)")
        void buscarPorId_idNegativo_delegaSinValidarAlRepositorio() {
            when(pacienteRepository.findById(-1L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> pacienteService.buscarPorId(-1L));
            verify(pacienteRepository).findById(-1L);
        }

        @Test
        @DisplayName("actualizar con campos null en el DTO los sobrescribe igual (bug: no valida nulls)")
        void actualizar_conCamposNull_sobrescribeConNull() {
            Paciente existente = pacienteConId(1L, "Ana", "Torres", LocalDate.of(1990, 5, 20));
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

            PacienteDTO dtoConNulls = new PacienteDTO();
            dtoConNulls.setNombre("Solo Nombre");
            // apellido, email, telefono, direccion y fechaNacimiento quedan null

            Paciente resultado = pacienteService.actualizar(1L, dtoConNulls);

            assertEquals("Solo Nombre", resultado.getNombre());
            assertThat(resultado.getApellido()).isNull();
            assertThat(resultado.getEmail()).isNull();
        }
    }

    // ---------- Manejo de errores ----------

    @Nested
    @DisplayName("manejo de errores")
    class ManejoDeErrores {

        @Test
        @DisplayName("buscarPorId lanza ResourceNotFoundException cuando el paciente no existe")
        void buscarPorId_inexistente_lanzaExcepcion() {
            when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> pacienteService.buscarPorId(99L));

            assertThat(ex.getMessage()).contains("99");
            verify(pacienteRepository, never()).save(any());
        }

        @Test
        @DisplayName("eliminar lanza ResourceNotFoundException y no llama a delete cuando el paciente no existe")
        void eliminar_inexistente_lanzaExcepcionYNoBorra() {
            when(pacienteRepository.findById(5L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> pacienteService.eliminar(5L));
            verify(pacienteRepository, never()).delete(any());
        }

        @Test
        @DisplayName("actualizar propaga ResourceNotFoundException cuando el paciente no existe")
        void actualizar_inexistente_propagaExcepcion() {
            when(pacienteRepository.findById(7L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> pacienteService.actualizar(7L, dtoValido()));
            verify(pacienteRepository, never()).save(any());
        }

        @Test
        @DisplayName("eliminar borra el paciente exactamente una vez cuando existe")
        void eliminar_existente_borraUnaVez() {
            Paciente existente = pacienteConId(1L, "Ana", "Torres", LocalDate.of(1990, 5, 20));
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));

            pacienteService.eliminar(1L);

            verify(pacienteRepository, times(1)).delete(existente);
        }
    }
}
