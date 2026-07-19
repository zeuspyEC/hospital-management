const { test, expect } = require('@playwright/test');

function datosUnicos() {
    const sufijo = Date.now();
    return {
        nombre: 'E2E',
        apellido: `Paciente${sufijo}`,
        email: `e2e.paciente${sufijo}@mail.com`,
        telefono: '0991234567',
        direccion: 'Av. Siempre Viva 123',
    };
}

test.describe('Flujo CRUD de pacientes', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/');
        await page.getByRole('button', { name: 'Pacientes' }).click();
        await expect(page.locator('#section-pacientes')).toHaveClass(/active/);
    });

    test('crear, editar y eliminar un paciente de punta a punta', async ({ page }) => {
        const paciente = datosUnicos();
        const nombreCompleto = `${paciente.nombre} ${paciente.apellido}`;

        // Crear
        await page.locator('#btn-nuevo-paciente').click();
        await expect(page.locator('#modal-paciente')).toHaveClass(/show/);

        await page.locator('#paciente-nombre').fill(paciente.nombre);
        await page.locator('#paciente-apellido').fill(paciente.apellido);
        await page.locator('#paciente-email').fill(paciente.email);
        await page.locator('#paciente-telefono').fill(paciente.telefono);
        await page.locator('#paciente-direccion').fill(paciente.direccion);
        await page.locator('#paciente-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Paciente creado exitosamente');
        await expect(page.locator('#modal-paciente')).not.toHaveClass(/show/);

        const fila = page.locator('#pacientes-table tbody tr', { hasText: nombreCompleto });
        await expect(fila).toBeVisible();
        await expect(fila).toContainText(paciente.email);

        // Editar (el telefono SI se muestra en la tabla; la direccion no es una columna visible)
        await fila.locator('.btn-edit').click();
        await expect(page.locator('#modal-paciente')).toHaveClass(/show/);
        await expect(page.locator('#paciente-nombre')).toHaveValue(paciente.nombre);

        const nuevoTelefono = '0987000111';
        await page.locator('#paciente-telefono').fill(nuevoTelefono);
        await page.locator('#paciente-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Paciente actualizado exitosamente');
        await expect(fila).toContainText(nuevoTelefono);

        // Eliminar: el backend SI borra el registro (DELETE responde 200 con body vacio),
        // pero apiFetch intenta parsear ese body vacio como JSON y lanza una excepcion
        // (bug documentado en api.js). El catch de eliminarPaciente no refresca la tabla,
        // asi que la fila queda visible en pantalla aunque el paciente ya no exista en la BD.
        await fila.locator('.btn-delete').click();
        await expect(page.locator('#alert-container')).toContainText('Error al eliminar paciente');
        await expect(fila).toBeVisible();

        // Recargar y confirmar que el paciente si fue eliminado en el backend
        await page.reload();
        await page.getByRole('button', { name: 'Pacientes' }).click();
        await expect(page.locator('#pacientes-table tbody tr', { hasText: nombreCompleto })).toHaveCount(0);
    });

    test('el formulario no permite guardar sin nombre (validacion HTML5 required)', async ({ page }) => {
        await page.locator('#btn-nuevo-paciente').click();
        await page.locator('#paciente-apellido').fill('SoloApellido');
        await page.locator('#paciente-form button[type="submit"]').click();

        // El modal sigue abierto: el navegador bloqueo el submit por el campo required vacio
        await expect(page.locator('#modal-paciente')).toHaveClass(/show/);
    });

    test('la busqueda por nombre filtra la tabla de pacientes precargados', async ({ page }) => {
        await page.locator('#search-pacientes').fill('Juan');
        await expect(page.locator('#pacientes-table tbody tr')).toHaveCount(1);
        await expect(page.locator('#pacientes-table tbody tr').first()).toContainText('Juan');
    });
});
