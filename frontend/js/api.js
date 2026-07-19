/**
 * api.js - Modulo de comunicacion con la API REST
 * Contiene funciones para interactuar con el backend del hospital
 *
 * BUGS INTENCIONALES IDENTIFICABLES MEDIANTE PRUEBAS:
 * 1. No hay manejo de timeout en fetch (puede colgarse indefinidamente)
 * 2. Algunas funciones no validan parametros de entrada
 * 3. Las respuestas de error no se propagan correctamente en algunos metodos
 */

const API_BASE = 'http://localhost:8080/api';

/**
 * Realiza una peticion HTTP generica a la API
 * @param {string} endpoint - Ruta relativa del endpoint
 * @param {object} options - Opciones de fetch (method, body, headers)
 * @returns {Promise<object>} - Respuesta parseada como JSON
 */
async function apiFetch(endpoint, options = {}) {
    const url = `${API_BASE}${endpoint}`;

    const config = {
        headers: {
            'Content-Type': 'application/json',
            ...options.headers,
        },
        ...options,
    };

    // BUG INTENCIONAL: Sin timeout — una peticion puede quedarse colgada
    const response = await fetch(url, config);

    // BUG INTENCIONAL: No maneja response.ok = false consistentemente
    // Para DELETE, intenta parsear JSON aunque el body este vacio
    const data = await response.json();

    // BUG: si !response.ok, deberia lanzar error pero solo retorna data
    return data;
}

// ================== PACIENTES ==================

const PacientesAPI = {
    listar: () => apiFetch('/pacientes'),

    buscar: (id) => apiFetch(`/pacientes/${id}`),

    crear: (paciente) => apiFetch('/pacientes', {
        method: 'POST',
        body: JSON.stringify(paciente),
    }),

    actualizar: (id, paciente) => apiFetch(`/pacientes/${id}`, {
        method: 'PUT',
        body: JSON.stringify(paciente),
    }),

    eliminar: (id) => apiFetch(`/pacientes/${id}`, {
        method: 'DELETE',
    }),

    // BUG INTENCIONAL: No sanitiza el parametro de busqueda (XSS reflejado)
    buscarPorNombre: (nombre) => apiFetch(`/pacientes/buscar?nombre=${encodeURIComponent(nombre)}`),

    // BUG: si no hay pacientes, division por 0
    edadPromedio: () => apiFetch('/pacientes/estadisticas/edad-promedio'),
};

// ================== DOCTORES ==================

const DoctoresAPI = {
    listar: () => apiFetch('/doctores'),

    buscar: (id) => apiFetch(`/doctores/${id}`),

    crear: (doctor) => apiFetch('/doctores', {
        method: 'POST',
        body: JSON.stringify(doctor),
    }),

    actualizar: (id, doctor) => apiFetch(`/doctores/${id}`, {
        method: 'PUT',
        body: JSON.stringify(doctor),
    }),

    eliminar: (id) => apiFetch(`/doctores/${id}`, {
        method: 'DELETE',
    }),

    // BUG INTENCIONAL: Este endpoint usa busqueda vulnerable (SQL Injection en backend)
    buscarPorEspecialidad: (especialidad) =>
        apiFetch(`/doctores/buscar-especialidad?q=${encodeURIComponent(especialidad)}`),

    // BUG: Sin validacion de parametros vacios
    buscarPorNombre: (nombre, apellido) =>
        apiFetch(`/doctores/buscar-nombre?nombre=${encodeURIComponent(nombre)}&apellido=${encodeURIComponent(apellido)}`),
};

// ================== CITAS ==================

const CitasAPI = {
    listar: () => apiFetch('/citas'),

    buscar: (id) => apiFetch(`/citas/${id}`),

    crear: (cita) => apiFetch('/citas', {
        method: 'POST',
        body: JSON.stringify(cita),
    }),

    actualizar: (id, cita) => apiFetch(`/citas/${id}`, {
        method: 'PUT',
        body: JSON.stringify(cita),
    }),

    eliminar: (id) => apiFetch(`/citas/${id}`, {
        method: 'DELETE',
    }),

    porPaciente: (pacienteId) => apiFetch(`/citas/paciente/${pacienteId}`),

    porDoctor: (doctorId) => apiFetch(`/citas/doctor/${doctorId}`),

    porEstado: (estado) => apiFetch(`/citas/estado/${estado}`),

    // BUG: No valida que inicio < fin
    porRangoFechas: (inicio, fin) =>
        apiFetch(`/citas/rango-fechas?inicio=${inicio}&fin=${fin}`),
};

// ================== HISTORIAS CLINICAS ==================

const HistoriasAPI = {
    listar: () => apiFetch('/historias-clinicas'),

    buscar: (id) => apiFetch(`/historias-clinicas/${id}`),

    crear: (historia) => apiFetch('/historias-clinicas', {
        method: 'POST',
        body: JSON.stringify(historia),
    }),

    porPaciente: (pacienteId) => apiFetch(`/historias-clinicas/paciente/${pacienteId}`),

    porDoctor: (doctorId) => apiFetch(`/historias-clinicas/doctor/${doctorId}`),
};

// Export CommonJS solo para pruebas con Jest (Node). En el navegador este
// bloque no se ejecuta; el comportamiento existente no cambia.
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        apiFetch,
        PacientesAPI,
        DoctoresAPI,
        CitasAPI,
        HistoriasAPI,
    };
}
