# Structa Editor

Structa Editor es un editor de código para Android construido en Kotlin y Jetpack Compose.

Está enfocado en ofrecer una experiencia de edición móvil rápida, limpia y extensible, con un motor de editor propio y acceso flexible al sistema de archivos.

---

## ✨ Características

- Editor de código personalizado.
- Soporte para múltiples documentos.
- Pestañas.
- Buscar y reemplazar.
- Ir a línea.
- Ir al inicio y al final del documento.
- Deshacer y rehacer.
- Formateo de documentos.
- Selección de lenguaje.
- Ajuste de línea.
- Atajos de teclado.
- Explorador de archivos.
- Workspaces.
- Favoritos.
- Archivos recientes.
- Guardar y Guardar como.
- Acceso mediante SAF.
- Acceso completo al almacenamiento compartido.
- Tema claro y oscuro.
- Configuración de fuente y tamaño del editor.

---

## 📂 Acceso a archivos

Structa soporta dos modos de acceso.

### SAF

Utiliza Storage Access Framework de Android.

Permite seleccionar carpetas mediante el selector del sistema y trabajar con referencias `content://`.

### Acceso completo

Utiliza acceso directo al almacenamiento compartido mediante:

    MANAGE_EXTERNAL_STORAGE

Permite trabajar con rutas reales como:

    /storage/emulated/0/Documents
    /storage/emulated/0/Download
    /storage/emulated/0/Projects

---

## 🎨 UI

La interfaz está construida con Jetpack Compose.

Principales pantallas:

    Inicio
    Explorador
    Editor
    Configuración

El editor personalizado utiliza renderizado propio para el área de código.

---

## 🚧 Estado

Structa Editor está en desarrollo activo.

---

## ⚠️ Nota

Structa es un proyecto personal.

Actualmente no está orientado a distribución comercial ni a producción.
