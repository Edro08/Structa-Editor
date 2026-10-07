# Arquitectura actual

Raíz de código: `app/src/main/java/com/edro08/structa/`.

| Capa | Responsabilidad |
| --- | --- |
| `domain/editor/` | Núcleo Kotlin puro: buffer, posiciones, comandos, historial, búsqueda, sintaxis y decoraciones. Sin Android ni Compose. |
| `domain/filesystem/`, `domain/workspace/` | Identificadores opacos, contratos de archivos y metadata de workspace/sesión. |
| `application/` | Casos de uso de lectura, apertura, guardado y formato. |
| `data/filesystem/` | `RoutedFileSystem` despacha por proveedor a `SafFileSystem` o `DirectFileSystem`; lectura/escritura UTF-8. |
| `data/settings/` | Preferencias, historial de workspaces y metadata de sesión en SharedPreferences. |
| `ui/editor/`, `ui/screen/` | Canvas, puente IME, renderer, pantallas Compose y ViewModels. |
| `app/` | `AppContainer` construye dependencias; `StructaApp` conecta navegación, permisos y apertura externa. |

Cada pestaña posee su `EditorEngine` y su sesión de input. La UI envía cambios
mediante comandos; ni el renderer ni el documento conocen el proveedor de archivos.
`FileRef` distingue `saf` y `direct`; preferencias e historial conservan el
proveedor de la raíz. La restauración guarda identificadores, orden, pestaña
activa, selección y scroll; vuelve a leer el contenido del archivo desde disco.

## Pantallas actuales

- **Inicio:** workspaces recientes y favoritos; abrir, marcar favorito, quitar
  del historial y elegir carpeta.
- **Explorador:** carpeta actual y breadcrumb, filtro de entradas, expansión
  diferida del árbol, refrescar, crear archivo/carpeta, renombrar, borrar y abrir
  documentos. Para renombrar o borrar archivos abiertos se exige cerrar sus
  pestañas; en carpetas se exige cerrar todas.
- **Selector Direct:** usa el Explorador en modo `SELECT_DIRECTORY`: solo
  carpetas, breadcrumb navegable, refrescar, crear carpeta y «Usar esta carpeta».
  Se abre tras obtener acceso completo; no tiene navegación inferior.
- **Editor:** Canvas con líneas, cursor, selección, scroll, IME, portapapeles,
  teclado físico y resaltado. Pestañas con indicador de cambios; Archivo ofrece
  Abrir (Quick Open), Guardar, Guardar como, Guardar todo y cierre de pestañas.
  Editar ofrece búsqueda/reemplazo literal o regex con opciones, ir a línea,
  Undo/Redo, formateo JSON/YAML/XML, selector de lenguaje y ajuste de línea.
  `Ctrl+P` abre Quick Open; `Ctrl+Shift+P` abre el menú Editar (ya no hay
  paleta filtrable). La barra inferior se oculta en el editor.
- **Configuración:** tema claro/oscuro, dos fuentes monoespaciadas y tamaño
  10–32 sp; proveedor SAF o Direct, con acceso a Ajustes del permiso Direct.

Lenguajes con resaltado: Go, JSON, YAML, Markdown y XML; Kotlin, Java y
JavaScript se tratan como texto plano. La selección de lenguaje por pestaña
vive en memoria durante la sesión. Android «Abrir con» reutiliza la pestaña
del mismo URI; un archivo con permiso solo de lectura puede exportarse con
Guardar como.
