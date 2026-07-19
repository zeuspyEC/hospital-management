package com.hospital.service;

import com.hospital.dto.DoctorDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Doctor;
import com.hospital.repository.DoctorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DoctorService")
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private EntityManager entityManager;

    private DoctorService doctorService;

    @BeforeEach
    void setUp() {
        doctorService = new DoctorService(doctorRepository);
        ReflectionTestUtils.setField(doctorService, "entityManager", entityManager);
    }

    private Doctor doctorConId(Long id, String nombre, String apellido, String especialidad) {
        Doctor d = new Doctor(nombre, apellido, especialidad, nombre.toLowerCase() + "@hospital.com", "022345678", "C-101");
        d.setId(id);
        return d;
    }

    private DoctorDTO dtoValido() {
        DoctorDTO dto = new DoctorDTO();
        dto.setNombre("Carla");
        dto.setApellido("Mendez");
        dto.setEspecialidad("Cardiologia");
        dto.setEmail("carla.mendez@hospital.com");
        dto.setTelefono("022345678");
        dto.setConsultorio("C-204");
        return dto;
    }

    // ---------- Caso feliz ----------

    @Nested
    @DisplayName("casos felices")
    class CasosFelices {

        @Test
        @DisplayName("listarTodos devuelve todos los doctores del repositorio")
        void listarTodos_devuelveListaCompleta() {
            List<Doctor> doctores = Arrays.asList(
                    doctorConId(1L, "Carla", "Mendez", "Cardiologia"),
                    doctorConId(2L, "Juan", "Reyes", "Pediatria")
            );
            when(doctorRepository.findAll()).thenReturn(doctores);

            List<Doctor> resultado = doctorService.listarTodos();

            assertThat(resultado).hasSize(2).containsExactlyElementsOf(doctores);
        }

        @Test
        @DisplayName("buscarPorId devuelve el doctor cuando existe")
        void buscarPorId_existente_devuelveDoctor() {
            Doctor doctor = doctorConId(1L, "Carla", "Mendez", "Cardiologia");
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));

            Doctor resultado = doctorService.buscarPorId(1L);

            assertEquals("Carla", resultado.getNombre());
        }

        @Test
        @DisplayName("crear guarda el doctor mapeado desde el DTO y lo devuelve")
        void crear_dtoValido_persisteYDevuelveDoctor() {
            when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> {
                Doctor arg = inv.getArgument(0);
                arg.setId(5L);
                return arg;
            });

            Doctor resultado = doctorService.crear(dtoValido());

            assertEquals(5L, resultado.getId());
            assertEquals("Cardiologia", resultado.getEspecialidad());
        }

        @Test
        @DisplayName("actualizar sobrescribe los campos del doctor existente")
        void actualizar_doctorExistente_actualizaCampos() {
            Doctor existente = doctorConId(1L, "Carla", "Mendez", "Cardiologia");
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

            DoctorDTO cambios = dtoValido();
            cambios.setEspecialidad("Neurologia");

            Doctor resultado = doctorService.actualizar(1L, cambios);

            assertEquals("Neurologia", resultado.getEspecialidad());
            verify(doctorRepository).save(existente);
        }

        @Test
        @DisplayName("buscarPorEspecialidad (version segura) delega al repositorio con parametros")
        void buscarPorEspecialidad_delegaAlRepositorio() {
            when(doctorRepository.findByEspecialidadContainingIgnoreCase("Cardiologia"))
                    .thenReturn(List.of(doctorConId(1L, "Carla", "Mendez", "Cardiologia")));

            List<Doctor> resultado = doctorService.buscarPorEspecialidad("Cardiologia");

            assertThat(resultado).hasSize(1);
            verify(doctorRepository).findByEspecialidadContainingIgnoreCase("Cardiologia");
        }
    }

    // ---------- Casos limite ----------

    @Nested
    @DisplayName("casos limite")
    class CasosLimite {

        @Test
        @DisplayName("eliminar no verifica citas activas antes de borrar (bug documentado)")
        void eliminar_noValidaDependencias_borraDirecto() {
            doctorService.eliminar(1L);

            verify(doctorRepository, times(1)).deleteById(1L);
            verify(doctorRepository, never()).findById(any());
        }

        @Test
        @DisplayName("buscarPorNombreCompleto con strings vacios no lanza excepcion, delega igual (bug: falta validacion)")
        void buscarPorNombreCompleto_conStringsVacios_delegaSinValidar() {
            when(doctorRepository.findByNombreAndApellido("", "")).thenReturn(Collections.emptyList());

            List<Doctor> resultado = doctorService.buscarPorNombreCompleto("", "");

            assertThat(resultado).isEmpty();
            verify(doctorRepository).findByNombreAndApellido("", "");
        }

        @Test
        @DisplayName("buscarPorEspecialidadInsegura concatena la entrada sin sanitizar (SQL Injection reproducible)")
        void buscarPorEspecialidadInsegura_concatenaEntradaSinSanitizar() {
            Query mockQuery = org.mockito.Mockito.mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(Doctor.class))).thenReturn(mockQuery);
            when(mockQuery.getResultList()).thenReturn(Collections.emptyList());

            String entradaMaliciosa = "Cardiologia' OR '1'='1";
            doctorService.buscarPorEspecialidadInsegura(entradaMaliciosa);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(entityManager).createNativeQuery(sqlCaptor.capture(), eq(Doctor.class));
            assertThat(sqlCaptor.getValue())
                    .as("la entrada del usuario llega concatenada tal cual al SQL nativo")
                    .contains(entradaMaliciosa);
        }
    }

    // ---------- Manejo de errores ----------

    @Nested
    @DisplayName("manejo de errores")
    class ManejoDeErrores {

        @Test
        @DisplayName("buscarPorId lanza ResourceNotFoundException cuando el doctor no existe")
        void buscarPorId_inexistente_lanzaExcepcion() {
            when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> doctorService.buscarPorId(99L));

            assertThat(ex.getMessage()).contains("99");
        }

        @Test
        @DisplayName("actualizar propaga ResourceNotFoundException cuando el doctor no existe")
        void actualizar_inexistente_propagaExcepcion() {
            when(doctorRepository.findById(7L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> doctorService.actualizar(7L, dtoValido()));
            verify(doctorRepository, never()).save(any());
        }
    }
}
