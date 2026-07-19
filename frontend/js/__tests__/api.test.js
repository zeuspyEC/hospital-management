/**
 * Pruebas unitarias de js/api.js con mock de fetch.
 * No golpea el backend real: cada test controla la respuesta simulada.
 */
const { apiFetch, PacientesAPI, DoctoresAPI, CitasAPI, HistoriasAPI } = require('../api');

function mockFetchOnce(body, { ok = true, status = 200 } = {}) {
    global.fetch = jest.fn().mockResolvedValue({
        ok,
        status,
        json: () => Promise.resolve(body),
    });
}

afterEach(() => {
    jest.restoreAllMocks();
});

describe('apiFetch', () => {
    test('construye la URL con el base de la API y hace GET por defecto', async () => {
        mockFetchOnce([{ id: 1 }]);

        await apiFetch('/pacientes');

        expect(global.fetch).toHaveBeenCalledWith(
            'http://localhost:8080/api/pacientes',
            expect.objectContaining({ headers: expect.objectContaining({ 'Content-Type': 'application/json' }) })
        );
    });

    test('devuelve el JSON parseado de la respuesta', async () => {
        mockFetchOnce({ id: 5, nombre: 'Ana' });

        const resultado = await apiFetch('/pacientes/5');

        expect(resultado).toEqual({ id: 5, nombre: 'Ana' });
    });

    test('con headers personalizados PIERDE el Content-Type por defecto (bug: el spread de ...options sobrescribe headers)', async () => {
        mockFetchOnce({});

        await apiFetch('/pacientes', { headers: { 'X-Custom': 'valor' } });

        const [, config] = global.fetch.mock.calls[0];
        // El bug: "headers: {...}, ...options" hace que options.headers reemplace
        // por completo al objeto headers ya construido, en vez de fusionarse con el.
        expect(config.headers).toEqual({ 'X-Custom': 'valor' });
        expect(config.headers['Content-Type']).toBeUndefined();
    });

    test('sin headers personalizados SI conserva el Content-Type por defecto', async () => {
        mockFetchOnce({});

        await apiFetch('/pacientes');

        const [, config] = global.fetch.mock.calls[0];
        expect(config.headers).toEqual({ 'Content-Type': 'application/json' });
    });

    test('con response.ok = false NO lanza excepcion, solo retorna el body (bug: no propaga el error)', async () => {
        mockFetchOnce({ message: 'Error interno' }, { ok: false, status: 500 });

        await expect(apiFetch('/pacientes')).resolves.toEqual({ message: 'Error interno' });
    });
});

describe('PacientesAPI', () => {
    test('listar hace GET a /pacientes', async () => {
        mockFetchOnce([]);
        await PacientesAPI.listar();
        expect(global.fetch).toHaveBeenCalledWith('http://localhost:8080/api/pacientes', expect.anything());
    });

    test('crear hace POST con el paciente serializado en el body', async () => {
        mockFetchOnce({ id: 10 });
        const paciente = { nombre: 'Luis', apellido: 'Perez' };

        await PacientesAPI.crear(paciente);

        const [, config] = global.fetch.mock.calls[0];
        expect(config.method).toBe('POST');
        expect(JSON.parse(config.body)).toEqual(paciente);
    });

    test('eliminar hace DELETE al endpoint con el id dado', async () => {
        mockFetchOnce(null);
        await PacientesAPI.eliminar(7);
        const [url, config] = global.fetch.mock.calls[0];
        expect(url).toBe('http://localhost:8080/api/pacientes/7');
        expect(config.method).toBe('DELETE');
    });

    test('buscarPorNombre codifica el parametro en la URL (incluye caracteres especiales)', async () => {
        mockFetchOnce([]);
        await PacientesAPI.buscarPorNombre('Ana & Cia');
        const [url] = global.fetch.mock.calls[0];
        expect(url).toBe('http://localhost:8080/api/pacientes/buscar?nombre=Ana%20%26%20Cia');
    });
});

describe('DoctoresAPI', () => {
    test('buscarPorEspecialidad construye la URL contra el endpoint inseguro conocido', async () => {
        mockFetchOnce([]);
        await DoctoresAPI.buscarPorEspecialidad('Cardiologia');
        const [url] = global.fetch.mock.calls[0];
        expect(url).toBe('http://localhost:8080/api/doctores/buscar-especialidad?q=Cardiologia');
    });

    test('buscarPorNombre no valida parametros vacios, igual arma la URL (bug documentado)', async () => {
        mockFetchOnce([]);
        await DoctoresAPI.buscarPorNombre('', '');
        const [url] = global.fetch.mock.calls[0];
        expect(url).toBe('http://localhost:8080/api/doctores/buscar-nombre?nombre=&apellido=');
    });
});

describe('CitasAPI', () => {
    test('porRangoFechas no valida que inicio sea antes que fin (bug documentado)', async () => {
        mockFetchOnce([]);
        await CitasAPI.porRangoFechas('2026-12-31', '2026-01-01');
        const [url] = global.fetch.mock.calls[0];
        expect(url).toBe('http://localhost:8080/api/citas/rango-fechas?inicio=2026-12-31&fin=2026-01-01');
    });

    test('porDoctor hace GET al endpoint filtrado por doctor', async () => {
        mockFetchOnce([]);
        await CitasAPI.porDoctor(3);
        expect(global.fetch).toHaveBeenCalledWith('http://localhost:8080/api/citas/doctor/3', expect.anything());
    });
});

describe('HistoriasAPI', () => {
    test('crear hace POST con la historia serializada en el body', async () => {
        mockFetchOnce({ id: 1 });
        const historia = { pacienteId: 1, diagnostico: 'Gripe' };

        await HistoriasAPI.crear(historia);

        const [, config] = global.fetch.mock.calls[0];
        expect(config.method).toBe('POST');
        expect(JSON.parse(config.body)).toEqual(historia);
    });

    test('porPaciente hace GET al endpoint filtrado por paciente', async () => {
        mockFetchOnce([]);
        await HistoriasAPI.porPaciente(2);
        expect(global.fetch).toHaveBeenCalledWith('http://localhost:8080/api/historias-clinicas/paciente/2', expect.anything());
    });
});
