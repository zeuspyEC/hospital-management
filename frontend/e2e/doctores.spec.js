const { test, expect } = require('@playwright/test');

function datosUnicos() {
    const sufijo = Date.now();
    return {
        nombre: 'E2E',
        apellido: `Doctor${sufijo}`,
        especialidad: 'Traumatologia',
        email: `e2e.doctor${sufijo}@hospital.com`,
        telefono: '022345678',
        consultorio: 'C-999',
    };
}

test.describe('Flujo CRUD de doctores', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/');
        await page.getByRole('button', { name: 'Doctores' }).click();
        await expect(page.locator('#section-doctores')).toHaveClass(/active/);
    });

    test('crear, editar y eliminar un doctor de punta a punta', async ({ page }) => {
        const doctor = datosUnicos();
        const nombreCompleto = `${doctor.nombre} ${doctor.apellido}`;

        // Crear
        await page.locator('#btn-nuevo-doctor').click();
        await expect(page.locator('#modal-doctor')).toHaveClass(/show/);

        await page.locator('#doctor-nombre').fill(doctor.nombre);
        await page.locator('#doctor-apellido').fill(doctor.apellido);
        await page.locator('#doctor-especialidad').fill(doctor.especialidad);
        await page.locator('#doctor-email').fill(doctor.email);
        await page.locator('#doctor-telefono').fill(doctor.telefono);
        await page.locator('#doctor-consultorio').fill(doctor.consultorio);
        await page.locator('#doctor-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Doctor creado exitosamente');

        const fila = page.locator('#doctores-table tbody tr', { hasText: nombreCompleto });
        await expect(fila).toBeVisible();
        await expect(fila).toContainText(doctor.especialidad);

        // Editar
        await fila.locator('.btn-edit').click();
        await expect(page.locator('#modal-doctor')).toHaveClass(/show/);
        const nuevaEspecialidad = 'Ortopedia';
        await page.locator('#doctor-especialidad').fill(nuevaEspecialidad);
        await page.locator('#doctor-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Doctor actualizado exitosamente');
        await expect(fila).toContainText(nuevaEspecialidad);

        // Eliminar: el backend SI borra (DELETE responde 200 con body vacio), pero
        // apiFetch intenta parsear ese body vacio como JSON y lanza una excepcion
        // (bug documentado en api.js). El catch de eliminarDoctor no refresca la
        // tabla, asi que la fila queda visible aunque el doctor ya no exista en la BD.
        await fila.locator('.btn-delete').click();
        await expect(page.locator('#alert-container')).toContainText('Error al eliminar doctor');
        await expect(fila).toBeVisible();

        // Recargar y confirmar que el doctor si fue eliminado en el backend
        await page.reload();
        await page.getByRole('button', { name: 'Doctores' }).click();
        await expect(page.locator('#doctores-table tbody tr', { hasText: nombreCompleto })).toHaveCount(0);
    });

    test('crear un doctor sin especialidad SI lo permite (bug: falta @NotBlank en especialidad)', async ({ page }) => {
        const doctor = datosUnicos();
        doctor.apellido += '-sinesp';
        const nombreCompleto = `${doctor.nombre} ${doctor.apellido}`;

        await page.locator('#btn-nuevo-doctor').click();
        await page.locator('#doctor-nombre').fill(doctor.nombre);
        await page.locator('#doctor-apellido').fill(doctor.apellido);
        // especialidad se deja vacia a proposito
        await page.locator('#doctor-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Doctor creado exitosamente');
        await expect(page.locator('#doctores-table tbody tr', { hasText: nombreCompleto })).toBeVisible();
    });

    test('la busqueda por especialidad filtra la tabla de doctores precargados', async ({ page }) => {
        await page.locator('#search-doctores').fill('Cardiologia');
        await expect(page.locator('#doctores-table tbody tr').first()).toContainText('Cardiologia');
    });
});
