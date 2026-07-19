/**
 * Pruebas unitarias de js/utils.js
 * Cubre entradas validas, invalidas y casos limite (null, undefined, string vacio)
 * para las 8 funciones exportadas.
 */
const {
    formatDate,
    formatDateTime,
    escapeHTML,
    showAlert,
    validateEmail,
    validateTelefono,
    isFutureDate,
    localToISO,
} = require('../utils');

describe('formatDate', () => {
    test('formatea una fecha ISO valida a texto legible en espanol', () => {
        expect(formatDate('1990-05-20')).toBe('19 de mayo de 1990');
    });

    test('con undefined devuelve el string "Invalid Date" (bug: no valida entrada)', () => {
        expect(formatDate(undefined)).toBe('Invalid Date');
    });

    test('con null NO lanza error pero produce una fecha distinta a la esperada (bug: null se interpreta como epoch)', () => {
        const resultado = formatDate(null);
        expect(resultado).not.toBe('Invalid Date');
        expect(resultado).toEqual(expect.any(String));
        expect(resultado.length).toBeGreaterThan(0);
    });

    test('con string vacio devuelve "Invalid Date"', () => {
        expect(formatDate('')).toBe('Invalid Date');
    });
});

describe('formatDateTime', () => {
    test('formatea fecha y hora validas', () => {
        const resultado = formatDateTime('2026-01-15T10:30:00');
        expect(resultado).toEqual(expect.stringContaining('2026'));
    });

    test('con undefined devuelve "Invalid Date" (bug: no valida entrada)', () => {
        expect(formatDateTime(undefined)).toBe('Invalid Date');
    });
});

describe('escapeHTML', () => {
    test('escapa &, <, >, " en un string con HTML', () => {
        expect(escapeHTML('<div class="x">A & B</div>'))
            .toBe('&lt;div class=&quot;x&quot;&gt;A &amp; B&lt;/div&gt;');
    });

    test('NO escapa comillas simples (bug de escape incompleto)', () => {
        expect(escapeHTML("O'Brien")).toBe("O'Brien");
    });

    test('NO escapa backticks (bug de escape incompleto)', () => {
        expect(escapeHTML('`alert(1)`')).toBe('`alert(1)`');
    });

    test('con null devuelve string vacio', () => {
        expect(escapeHTML(null)).toBe('');
    });

    test('con undefined devuelve string vacio', () => {
        expect(escapeHTML(undefined)).toBe('');
    });

    test('con string vacio devuelve string vacio', () => {
        expect(escapeHTML('')).toBe('');
    });
});

describe('showAlert', () => {
    beforeEach(() => {
        document.body.innerHTML = '<div id="alert-container"></div>';
        jest.useFakeTimers();
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    test('inserta el mensaje dentro de #alert-container con la clase del tipo dado', () => {
        showAlert('Guardado con exito', 'success');
        const container = document.getElementById('alert-container');
        expect(container.innerHTML).toContain('alert-success');
        expect(container.innerHTML).toContain('Guardado con exito');
    });

    test('usa "success" como tipo por defecto cuando no se especifica', () => {
        showAlert('Mensaje por defecto');
        expect(document.getElementById('alert-container').innerHTML).toContain('alert-success');
    });

    test('inserta el mensaje SIN escapar HTML/scripts (bug XSS via innerHTML)', () => {
        const payload = '<img src=x onerror="alert(1)">';
        showAlert(payload, 'error');
        const html = document.getElementById('alert-container').innerHTML;
        // El navegador normaliza el atributo (src=x -> src="x") al parsear el HTML,
        // pero la etiqueta <img> y el manejador onerror llegan intactos: no hay
        // ningun escape de '<' a '&lt;', que es justamente el bug a documentar.
        expect(html).toContain('<img');
        expect(html).toContain('onerror="alert(1)"');
        expect(html).not.toContain('&lt;img');
    });

    test('limpia el contenedor automaticamente despues de 4 segundos', () => {
        showAlert('Se va a borrar');
        expect(document.getElementById('alert-container').innerHTML).not.toBe('');

        jest.advanceTimersByTime(4000);

        expect(document.getElementById('alert-container').innerHTML).toBe('');
    });

    test('si no existe #alert-container no lanza error', () => {
        document.body.innerHTML = '';
        expect(() => showAlert('sin contenedor')).not.toThrow();
    });
});

describe('validateEmail', () => {
    test('acepta un email valido simple', () => {
        expect(validateEmail('user@example.com')).toBe(true);
    });

    test('acepta un email valido con "+" en la parte local', () => {
        expect(validateEmail('user+tag@example.com')).toBe(true);
    });

    test('rechaza un email sin TLD (comportamiento real: el regex SI lo rechaza, a diferencia de lo que dice el comentario del codigo)', () => {
        expect(validateEmail('usuario@dominio')).toBe(false);
    });

    test('rechaza un string sin arroba', () => {
        expect(validateEmail('no-es-un-email.com')).toBe(false);
    });

    test('con null devuelve false', () => {
        expect(validateEmail(null)).toBe(false);
    });

    test('con undefined devuelve false', () => {
        expect(validateEmail(undefined)).toBe(false);
    });

    test('con string vacio devuelve false', () => {
        expect(validateEmail('')).toBe(false);
    });
});

describe('validateTelefono', () => {
    test('acepta un numero de 10 digitos', () => {
        expect(validateTelefono('0991234567')).toBe(true);
    });

    test('rechaza un numero con menos de 10 digitos', () => {
        expect(validateTelefono('099123')).toBe(false);
    });

    test('rechaza un numero con letras', () => {
        expect(validateTelefono('099abc4567')).toBe(false);
    });

    test('rechaza un numero con prefijo invalido pero 10 digitos igual lo acepta (bug: no valida prefijos reales)', () => {
        expect(validateTelefono('1234567890')).toBe(true);
    });

    test('con string vacio devuelve false', () => {
        expect(validateTelefono('')).toBe(false);
    });
});

describe('isFutureDate', () => {
    test('una fecha claramente futura devuelve true', () => {
        const enUnAnio = new Date();
        enUnAnio.setFullYear(enUnAnio.getFullYear() + 1);
        expect(isFutureDate(enUnAnio.toISOString())).toBe(true);
    });

    test('una fecha claramente pasada devuelve false', () => {
        expect(isFutureDate('2000-01-01')).toBe(false);
    });

    test('con undefined devuelve false (Invalid Date no es mayor a la fecha actual)', () => {
        expect(isFutureDate(undefined)).toBe(false);
    });
});

describe('localToISO', () => {
    test('convierte un datetime-local al mismo resultado que Date().toISOString() (bug: asume la zona local del entorno, no Ecuador explicitamente)', () => {
        const input = '2026-07-20T10:30';
        expect(localToISO(input)).toBe(new Date(input).toISOString());
    });

    test('el resultado siempre termina en "Z" (formato ISO UTC)', () => {
        expect(localToISO('2026-01-01T00:00')).toMatch(/Z$/);
    });
});
