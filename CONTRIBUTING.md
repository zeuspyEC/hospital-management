# Guía de contribución — Hospital Management System

Proyecto final de Validación y Verificación de Software (EPN, 2026A).
Equipo: Erick (backend), Jimmy (frontend), Paul (QA/seguridad).

El flujo completo de ramas, commits, PRs y estándares de calidad está en
[`.claude/skills/team-git-workflow/SKILL.md`](.claude/skills/team-git-workflow/SKILL.md).
Este documento es el **paso a paso para que cada integrante se conecte con su propia cuenta**
y empiece a trabajar en su rama.

## Regla base

Cada commit debe salir de la cuenta real de quien lo hizo, en el momento en que lo hizo. Nadie
commitea con el token o la sesión de otro integrante, y no se fabrican fechas. Ver la sección
"Principio no negociable" de la skill para el detalle de por qué.

## Paso 1 — Revocar cualquier token expuesto

Si tu Personal Access Token (PAT) se compartió alguna vez por chat, correo o cualquier canal no
seguro, considéralo comprometido:

1. GitHub → **Settings → Developer settings → Personal access tokens**
2. Revoca el token expuesto.
3. Genera uno nuevo (scope `repo` es suficiente) y guárdalo solo tú, en un gestor de contraseñas
   o `gh auth login` (que lo guarda cifrado localmente). Nunca lo pegues en un chat, issue o commit.

## Paso 2 — Autenticarte con tu propia cuenta

En la máquina desde la que vayas a trabajar (la tuya, o esta si la comparten por turnos):

```bash
gh auth login
# elegir GitHub.com -> HTTPS -> pegar/loguear con TU cuenta
```

Verifica que quedaste como tú mismo:

```bash
gh auth status
```

## Paso 3 — Configurar tu identidad de Git en el repo

Desde la carpeta del proyecto (no global, para no pisar la identidad de otro si la máquina es
compartida):

```bash
cd hospital-management
git config user.name "<tu-usuario-de-github>"
git config user.email "<tu-correo>@epn.edu.ec"
```

| Integrante | user.name | user.email |
|---|---|---|
| Erick | `zeuspyEC` | `erick.costa@epn.edu.ec` |
| Jimmy | `Jimmy1224xdd` | `jimmy.arias@epn.edu.ec` |
| Paul | `paulstna` | `paul.salas@epn.edu.ec` |

## Paso 4 — Ir a tu rama

Las ramas ya existen en el remoto, creadas desde `develop`:

```bash
git fetch origin
git checkout feature/backend-erick     # Erick
git checkout feature/frontend-jimmy    # Jimmy
git checkout feature/qa-paul           # Paul
```

## Paso 5 — Trabajar y commitear

Escribe tu parte (tests, configuración, informes según tu rol — ver la tabla de reparto en la
skill), y commitea normal:

```bash
git add <archivos>
git commit -m "test: agrega casos felices para PacienteService"
git push
```

Si usas Claude Code para ayudarte a escribir el código, es tu sesión, autenticada como tú: el
commit queda con tu nombre porque lo generaste tú usando una herramienta, igual que con
autocompletado o un IDE. Lo único que no se hace es que otra persona (o una IA con tus
credenciales) commitee simulando ser tú sin que tú estés al mando.

## Paso 6 — Pull Request hacia `develop`

Cuando tu parte esté lista:

```bash
gh pr create --base develop --head <tu-rama> --title "..." --body "..."
```

Usa el checklist de [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md) —
se completa solo al abrir el PR en GitHub.

## Dudas

Revisar primero `DOCUMENTACION.md` (enunciado del proyecto) y la skill de flujo de trabajo. Si el
bloqueo persiste, coordinar en el grupo antes de forzar cualquier cosa sobre `develop` o `main`.
