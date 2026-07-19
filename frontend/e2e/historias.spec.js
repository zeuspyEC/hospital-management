const { test, expect } = require('@playwright/test');

test.describe('Flujo de creacion y consulta de historias clinicas', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/');
        await page.getByRole('button', { name: 'Historias Clínicas' }).click();
        await expect(page.locator('#section-historias')).toHaveClass(/active/);
    });

    test('crea una historia clinica con paciente y doctor, y la consulta con "Ver"', async ({ page }) => {
        const diagnostico = `Diagnostico E2E ${Date.now()}`;
        const tratamiento = 'Reposo por 5 dias y controles semanales';
        const observaciones = 'Paciente responde bien al tratamiento';

        await page.locator('#btn-nueva-historia').click();
        await expect(page.locator('#modal-historia')).toHaveClass(/show/);

        await page.locator('#historia-paciente').selectOption({ label: 'Juan Perez' });
        await page.locator('#historia-doctor').selectOption({ label: 'Elena Rodriguez' });
        await page.locator('#historia-diagnostico').fill(diagnostico);
        await page.locator('#historia-tratamiento').fill(tratamiento);
        await page.locator('#historia-observaciones').fill(observaciones);
        await page.locator('#historia-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Historia clinica creada exitosamente');
        await expect(page.locator('#modal-historia')).not.toHaveClass(/show/);

        const fila = page.locator('#historias-table tbody tr', { hasText: diagnostico });
        await expect(fila).toBeVisible();
        await expect(fila).toContainText('Juan Perez');
        await expect(fila).toContainText('Elena Rodriguez');

        // Consultar el detalle con "Ver"
        await fila.locator('.btn-view').click();
        await expect(page.locator('#modal-historia')).toHaveClass(/show/);
        const detalle = page.locator('#modal-historia .historia-detalle');
        await expect(detalle).toContainText(diagnostico);
        await expect(detalle).toContainText(tratamiento);
        await expect(detalle).toContainText(observaciones);
    });

    test('crea una historia clinica sin doctor (opcional) correctamente', async ({ page }) => {
        const diagnostico = `Chequeo general E2E ${Date.now()}`;

        await page.locator('#btn-nueva-historia').click();
        await page.locator('#historia-paciente').selectOption({ label: 'Maria Garcia' });
        // doctor se deja sin seleccionar (opcional)
        await page.locator('#historia-diagnostico').fill(diagnostico);
        await page.locator('#historia-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Historia clinica creada exitosamente');
        const fila = page.locator('#historias-table tbody tr', { hasText: diagnostico });
        await expect(fila).toContainText('N/A'); // BUG: doctor null se muestra como "N/A" sin mas contexto
    });

    test('el diagnostico con HTML se renderiza sin escapar en la tabla (bug XSS almacenado)', async ({ page }) => {
        const marcador = `XSS-${Date.now()}`;
        const diagnosticoConHtml = `<b class="xss-marker-${marcador}">Diagnostico en negritas</b>`;

        await page.locator('#btn-nueva-historia').click();
        await page.locator('#historia-paciente').selectOption({ label: 'Juan Perez' });
        await page.locator('#historia-diagnostico').fill(diagnosticoConHtml);
        await page.locator('#historia-form button[type="submit"]').click();

        await expect(page.locator('#alert-container')).toContainText('Historia clinica creada exitosamente');

        // Si el HTML se hubiera escapado, este selector no encontraria ningun elemento <b>:
        // el navegador habria mostrado el texto literal "<b class=...>" en vez de interpretarlo.
        const elementoInyectado = page.locator(`#historias-table b.xss-marker-${marcador}`);
        await expect(elementoInyectado).toBeVisible();
        await expect(elementoInyectado).toHaveText('Diagnostico en negritas');
    });
});
