const { test, expect } = require('@playwright/test');

function fechaHoraFutura(diasEnElFuturo) {
    const fecha = new Date();
    fecha.setDate(fecha.getDate() + diasEnElFuturo);
    fecha.setHours(10, 0, 0, 0);
    const pad = (n) => String(n).padStart(2, '0');
    return `${fecha.getFullYear()}-${pad(fecha.getMonth() + 1)}-${pad(fecha.getDate())}T${pad(fecha.getHours())}:${pad(fecha.getMinutes())}`;
}

test.describe('Flujo de creacion y consulta de citas', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/');
        await page.getByRole('button', { name: 'Citas' }).click();
        await expect(page.locator('#section-citas')).toHaveClass(/active/);
    });

    test('crea una cita con paciente y doctor precargados, y aparece en la tabla', async ({ page }) => {
        const motivo = `Consulta E2E ${Date.now()}`;

        await page.locator('#btn-nueva-cita').click();
        await expect(page.locator('#modal-cita')).toHaveClass(/show/);

        await page.locator('#cita-paciente').selectOption({ label: 'Juan Perez' });
        await page.locator('#cita-doctor').selectOption({ label: 'Elena Rodriguez (Cardiologia)' });
        await page.locator('#cita-fecha-hora').fill(fechaHoraFutura(3));
        await page.locator('#cita-motivo').fill(motivo);
        await page.locator('#cita-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Cita creada exitosamente');
        await expect(page.locator('#modal-cita')).not.toHaveClass(/show/);

        const fila = page.locator('#citas-table tbody tr', { hasText: motivo });
        await expect(fila).toBeVisible();
        await expect(fila).toContainText('Elena Rodriguez');
        await expect(fila).toContainText('PROGRAMADA');
    });

    test('editar una cita existente actualiza motivo y estado en la tabla', async ({ page }) => {
        const motivoOriginal = `Control E2E ${Date.now()}`;

        await page.locator('#btn-nueva-cita').click();
        await page.locator('#cita-paciente').selectOption({ label: 'Maria Garcia' });
        await page.locator('#cita-doctor').selectOption({ label: 'Miguel Torres (Pediatria)' });
        await page.locator('#cita-fecha-hora').fill(fechaHoraFutura(4));
        await page.locator('#cita-motivo').fill(motivoOriginal);
        await page.locator('#cita-form button[type="submit"]').click();
        await expect(page.locator('#alert-container')).toContainText('Cita creada exitosamente');

        const fila = page.locator('#citas-table tbody tr', { hasText: motivoOriginal });
        await fila.locator('.btn-edit').click();
        await expect(page.locator('#modal-cita')).toHaveClass(/show/);

        const motivoNuevo = `${motivoOriginal}-editado`;
        await page.locator('#cita-motivo').fill(motivoNuevo);
        await page.locator('#cita-estado').selectOption('COMPLETADA');
        await page.locator('#cita-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Cita actualizada exitosamente');
        const filaActualizada = page.locator('#citas-table tbody tr', { hasText: motivoNuevo });
        await expect(filaActualizada).toContainText('COMPLETADA');
    });

    test('permite crear dos citas con el mismo doctor a la misma hora (bug: sin validacion de doble booking)', async ({ page }) => {
        const fecha = fechaHoraFutura(6);
        const motivo1 = `DobleBooking-A-${Date.now()}`;
        const motivo2 = `DobleBooking-B-${Date.now()}`;

        for (const motivo of [motivo1, motivo2]) {
            await page.locator('#btn-nueva-cita').click();
            await page.locator('#cita-paciente').selectOption({ label: 'Juan Perez' });
            await page.locator('#cita-doctor').selectOption({ label: 'Sofia Castillo (Dermatologia)' });
            await page.locator('#cita-fecha-hora').fill(fecha);
            await page.locator('#cita-motivo').fill(motivo);
            await page.locator('#cita-form button[type="submit"]').click();
            await expect(page.locator('#alert-container')).toContainText('Cita creada exitosamente');
        }

        await expect(page.locator('#citas-table tbody tr', { hasText: motivo1 })).toBeVisible();
        await expect(page.locator('#citas-table tbody tr', { hasText: motivo2 })).toBeVisible();
    });

    test('el filtro por estado muestra solo las citas programadas', async ({ page }) => {
        const [respuesta] = await Promise.all([
            page.waitForResponse((r) => r.url().includes('/api/citas/estado/PROGRAMADA')),
            page.locator('#filter-estado-citas').selectOption('PROGRAMADA'),
        ]);
        expect(respuesta.ok()).toBeTruthy();

        const textos = await page.locator('#citas-table tbody tr').allTextContents();
        expect(textos.length).toBeGreaterThan(0);
        for (const texto of textos) {
            expect(texto).toContain('PROGRAMADA');
        }
    });
});
