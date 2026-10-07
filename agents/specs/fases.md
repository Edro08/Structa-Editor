# Fases de desarrollo de Structa

Las fases 1–11 están terminadas; la 12 está pospuesta. Los cierres de cada fase
describen lo entregado **en ese momento**. La sección «Evolución posterior» recoge
las decisiones que sustituyeron límites o funciones anteriores. La descripción
de cada componente en uso está en [arquitectura y pantallas](arquitectura.md)
y [Editor Core](editor-core.md).

## Fase 1 — Buffer y posiciones

- Se creó `TextBuffer` con `PieceTableBuffer`: texto original inmutable, almacén
  de inserciones append-only y piezas que representan el texto lógico. Inserción,
  borrado y reemplazo actualizan `LineIndex` sin reconstruirlo desde el inicio.
- Se añadieron `TextOffset`, `TextRange`, `LineColumn`, `Cursor` y `Selection`, con
  conversiones entre offsets y líneas/columnas. Contrato: unidades UTF-16,
  rangos `[inicio, fin)`, líneas desde cero y una línea incluso en el documento
  vacío. LF divide líneas; CR se conserva, también con CRLF.
- Consultas de línea por índice O(1), localización por offset O(log líneas);
  la localización de piezas en esta implementación es lineal.
- Validación inicial: 21 pruebas del núcleo, incluidas 6.000 ediciones aleatorias
  del buffer y 500 conversiones tras cambios aleatorios. La suite general tenía
  bloqueos preexistentes, resueltos en la fase 5.

## Fase 2 — Operaciones de edición

- `EditingSession` reúne insertar, reemplazar rango o selección, Backspace,
  Delete, mover cursor y extender/limpiar selección. Se conservan `anchor` y
  extremo activo; el cursor coincide con este último.
- Flechas sin Shift colapsan una selección al extremo correspondiente;
  Home/End actúan en la línea del extremo activo. Una edición deja el cursor
  al final del texto insertado; los límites y rangos inválidos se rechazan sin
  corromper cursor o selección.
- En este nivel el movimiento y borrado operan sobre UTF-16, no sobre grafemas
  Unicode completos. Cierre: 36 pruebas del núcleo.

## Fase 3 — Historial

- `EditOperation` registra Insert/Delete/Replace y el texto afectado;
  `EditTransaction` agrupa operaciones y estado lógico previo/posterior.
  `EditHistory` invierte operaciones para Undo y las reproduce para Redo, sin
  snapshots completos de cada versión.
- Undo/Redo restauran también cursor y dirección de selección. Las
  transacciones son explícitas y no anidadas; se confirman antes de Undo/Redo.
  Una edición efectiva invalida la rama Redo; movimientos y operaciones vacías
  no lo hacen. La agrupación temporal automática de tecleo sigue pendiente.
- Cierre: 49 pruebas del núcleo, incluidas 300 ediciones con Undo/Redo contra
  un modelo de referencia.

## Fase 4 — Documento, engine y comandos

- `EditorDocument` posee buffer e historial sin depender de UI o filesystem.
  `EditorEngine` coordina una `EditingSession` y ejecuta `EditorCommand` para
  inserción, reemplazo, borrado, selección, movimientos, transacciones e
  historial. Los comandos no contienen referencias a documentos concretos.
- La mutación pasa por el engine; un documento tiene un engine activo, para
  evitar cursores obsoletos. El primer hito obligatorio —editar, Undo y Redo
  exclusivamente mediante comandos y sin UI— quedó verificado.
- Cierre: 57 pruebas del núcleo.

## Fase 5 — Estabilización de pruebas

- Se habilitó la suite JVM completa antes de construir el renderer: 73 pruebas
  aprobadas (57 del núcleo y 16 de aplicación/ViewModels). Se verificó que
  `domain.editor` no importa Android/AndroidX.
- Se corrigieron dobles de `ContentFormatDetector` y pruebas de formato y
  concurrencia que bloqueaban la suite general. Ya no hace falta ejecutar el
  núcleo mediante un init script de aislamiento.

## Fase 6 — Renderer

- `StructaEditorView` dibuja en Canvas fondo, línea actual, selección, texto,
  cursor y gutter fijo; se integra en Compose mediante `AndroidView`. El
  viewport renderiza solo líneas visibles más dos de margen por lado y conserva
  layouts cercanos entre frames.
- Scroll en ambos ejes, arrastre, fling y rueda; la fuente monoespaciada da
  geometría de columnas. Las líneas largas usan checkpoints cada 256 unidades
  UTF-16 y expanden solo el tramo horizontal visible. Los tabs avanzan cada
  cuatro columnas. El ancho horizontal se descubre al visitar líneas.
- Cierre: 84 JVM y 5 instrumentadas; pruebas de geometría, líneas largas,
  viewport de 100.000 líneas, Canvas y desplazamiento en emulador Android 12.

## Fase 7 — Entrada táctil, teclado e IME

- `EditorInputSession` y `EditorInputConnection` adaptan la View al IME sin
  `Editable` como motor. Composición provisional y batches se agrupan en una
  transacción; hay consultas de contexto, selección y borrado circundante por
  UTF-16/codepoints, con protección de pares surrogate y conexiones cerradas.
- Toque mueve el cursor, doble toque o pulsación larga seleccionan palabra,
  arrastre extiende la selección y hay menú contextual y portapapeles. Teclado
  físico y atajos Ctrl/Meta cubren edición, navegación, selección e historial;
  la View revela el cursor también cuando el IME reduce su altura.
- Cierre: 99 JVM y 16 instrumentadas. Se verificaron composición, historial
  compartido, portapapeles, transiciones a solo lectura y pulsación larga real.
  La matriz manual Gboard/SwiftKey/Samsung sigue pendiente.

## Fase 8 — Documentos y guardado

- Cada pestaña posee documento, engine y sesión de input independientes.
  Reabrir el mismo identificador activa la pestaña existente sin perder cambios.
  Se conservan cursor, selección, búsqueda, modo y scroll mientras vive la app.
- `revision` y `savedRevision` determinan el indicador dirty; Undo/Redo
  restauran revisiones y una rama nueva no reutiliza las abandonadas. Guardar
  escribe en el archivo original; Guardar como exporta una copia sin marcar
  limpio el original.
- Guardado asíncrono captura contenido y revisión: si se sigue editando durante
  la escritura, no marca como guardados los cambios posteriores. Cerrar un
  documento modificado ofrece Guardar y cerrar, Descartar o Cancelar.
- Cierre: 107 JVM y 19 instrumentadas. La persistencia entre procesos llegó
  en la fase 9; los borradores no guardados no se recuperan.

## Fase 9 — Filesystem, explorador y sesión

- `FileSystem` permite lectura/escritura, `stat`, existencia, listado, creación,
  renombrado y borrado sin acoplar el editor a un proveedor. Se implementó SAF
  con `DocumentsContract`; `FileRef` conserva identificador y proveedor.
- Explorador con carpeta raíz, navegación y expansión diferida, refresco y
  operaciones de archivo/carpeta. Una respuesta de una raíz anterior no puede
  reemplazar la vista actual. Renombrar/borrar un archivo abierto exige cerrar
  su pestaña; para una carpeta se exige cerrar todas, pues los ID SAF son opacos.
- `SessionRepository` guarda orden de pestañas, activa, cursor/selección y
  scroll en SharedPreferences. Los checkpoints se hacen con debounce y al
  pasar a segundo plano. Al restaurar, se relee el contenido del disco y se
  omiten archivos ausentes o inaccesibles; no se recuperan Undo, búsqueda ni
  texto sin guardar. Una sola raíz de workspace está activa a la vez.
- Cierre: 112 JVM y 22 instrumentadas, incluida una prueba SAF de
  lectura/escritura y operaciones reales con un `DocumentsProvider` de pruebas.

## Fase 10 — Productividad

- `DocumentSearch` busca en el documento con literal o regex, distinción de
  mayúsculas, palabra completa y rangos UTF-16. Se ejecuta sobre snapshots
  inmutables fuera de UI, con debounce/cancelación; siguiente/anterior recorren
  coincidencias circularmente.
- Reemplazar actual/todo admite referencias de grupos regex. El plan valida
  documento, revisión, selección y opciones antes de aplicar cambios;
  `ReplaceMatchesCommand` edita de atrás hacia delante en una transacción, de
  modo que un Undo revierte el reemplazo completo. Ir a línea usa el índice.
- Quick Open (`Ctrl/Meta+P`) indexa nombres y rutas relativas, con ranking fuzzy,
  publicación progresiva y cancelación. No lee el contenido de los archivos:
  **no es** búsqueda de texto en todo el workspace.
- Inicialmente se añadió una paleta de comandos filtrable con `Ctrl+Shift+P`;
  después se reemplazó por el menú Editar. El registro de acciones y los atajos
  continúan en `EditorAction`.
- Presupuestos de búsqueda: consulta de 4.096 caracteres, hasta 10.000
  coincidencias y 500 ms cooperativos (sin timeout duro del regex nativo).
  Reemplazar limita la expansión a 1.048.576 unidades UTF-16 por operación;
  no permite sustituir resultados truncados. Quick Open limita el índice a
  20.000 archivos/5.000 carpetas y muestra hasta 100 resultados.
- Cierre: 129 JVM y 25 instrumentadas. Guardar todo, ajuste de línea y selector
  efectivo de lenguaje existen **ahora**, aunque no pertenecían al cierre
  original de esta fase. Workspace Search y codificaciones siguen pendientes.

## Fase 11 — Sintaxis y decoraciones

- `LanguageRegistry` detecta extensiones; tokenizadores léxicos propios producen
  spans UTF-16 por línea y estado multilínea. `IncrementalHighlighter` reutiliza
  prefijos y sufijos cuando converge el estado, incluso al insertar o eliminar
  líneas. Se cancela el trabajo obsoleto al editar, cambiar pestaña o desmontar
  la View; no se publican spans de otro documento.
- El renderer colorea texto respetando tabs, selección y temas claro/oscuro.
  `DecorationSet` indexa rangos superpuestos; búsqueda muestra fondos. Hay
  soporte visual para subrayados de error/advertencia y fondos de brackets,
  pero no un productor automático de diagnósticos, Git ni breakpoints.
- El cierre inicial incluía tokenizadores Kotlin/Java; se retiraron después.
  **Hoy resaltan Go, JSON, YAML, Markdown y XML**; Kotlin, Java y JavaScript
  se editan como texto plano. La tokenización es léxica, no una gramática
  completa ni análisis semántico.
- Cierre de fase: 137 JVM y 28 instrumentadas. La estrategia inicial de
  texto plano por encima de 1.048.576 unidades UTF-16 fue reemplazada
  posteriormente por resaltado por ventanas (véase abajo).

## Evolución posterior a las fases

- **Archivos grandes:** se quitó el bloqueo heredado de edición a partir de
  1 MB. Edición, Undo/Redo, guardar, formato y reemplazo funcionan por encima
  de ese tamaño; la apertura sigue limitada a 25 MB. Por encima de 1.048.576
  unidades UTF-16 se resalta el viewport y un margen, con checkpoints léxicos
  cada 128 líneas e invalidación desde la primera línea cambiada. Una primera
  visita lejana puede reconstruir el estado desde el inicio; `syncInput`
  todavía tiene recorridos O(n).
- **Formato y lenguaje:** el selector de lenguaje afecta el resaltado de cada
  pestaña durante la sesión. «Formatear» admite JSON, YAML y XML; YAML solo
  corrige sangría de estructuras de bloque inequívocas y XML conserva texto
  mixto, comentarios, CDATA y `xml:space="preserve"`. Si una estructura no es
  segura de transformar, deja el documento intacto. Otros lenguajes no se
  formatean automáticamente.
- **Archivos externos y proveedores:** Android «Abrir con» recibe `content://`
  para texto/JSON/XML/YAML/JavaScript y reutiliza pestañas; conserva permisos
  persistibles si están disponibles. Guardar el original requiere escritura;
  Guardar como permite exportar un archivo de solo lectura a un destino nuevo.
  A SAF se sumó Direct con rutas absolutas del almacenamiento compartido,
  selector propio y permiso
  `MANAGE_EXTERNAL_STORAGE`. `RoutedFileSystem` despacha por proveedor.
  Direct bloquea `Android/data` y `Android/obb`. Se retiró el prototipo de
  acceso restringido; ese acceso queda fuera de Structa.
- **Interfaz:** tema claro/oscuro, fuentes monoespaciadas y 10–32 sp. El menú
  Archivo ofrece Guardar todo y cerrar varias pestañas; Editar ofrece ajuste
  de línea. La barra inferior se oculta en Editor; se corrigió el recorte del
  Canvas para que Buscar/Reemplazar no queden tapados.

## Fase 12 — Funciones de IDE (pospuesta)

No se ha implementado como fase. El alcance propuesto era amplio y debe
dividirse antes de ejecutarlo:

- **LSP:** autocompletado, diagnósticos, hover, definición, referencias,
  símbolos y renombrado mediante una capa independiente; las ediciones deben
  pasar por comandos del engine.
- **Git:** primero estado de archivos; después diff, stage, commit, ramas y
  operaciones remotas, sin acoplar Git al núcleo de archivos.
- **Otras funciones:** terminal, dos vistas simultáneas (split editor),
  multicursor, minimapa y plegado de código.
- **OneDrive:** otro `FileSystem` con listado, lectura y mutaciones; resolver
  caché, offline y conflictos de versiones sin cambiar el Editor Core.

Fuera de esta fase también siguen pendientes la recuperación de borradores
tras muerte del proceso, búsqueda de contenido en todo el workspace,
codificaciones distintas de UTF-8, sesiones separadas por workspace,
navegación por grafemas/bidi avanzados, agrupación temporal del tecleo y
validación manual con teclados comerciales. No se debe confundir restauración
de metadata con recuperación de texto no guardado.

## Validación y límites de las cifras

| Hito | JVM aprobadas | Instrumentadas aprobadas |
| --- | ---: | ---: |
| Fase 5 (suite completa inicial) | 73 | — |
| Fase 6 | 84 | 5 |
| Fase 7 | 99 | 16 |
| Fase 8 | 107 | 19 |
| Fase 9 | 112 | 22 |
| Fase 10 | 129 | 25 |
| Fase 11 | 137 | 28 |
| Tras retirar el acceso restringido | 167 | 54 |

Son recuentos de los cierres correspondientes, no cifras acumulables. Las
últimas APK de aplicación y pruebas compilaron; la suite de 54 instrumentadas
pasó en Pixel_6_Pro AVD (Android 12). Las dos pruebas Direct pueden terminar
sin operar si falta el permiso; se repitieron individualmente con permiso
temporal, ejercieron almacenamiento y selector, y se devolvió el app-op a
`default`. Esto no certifica todos los proveedores SAF ni todos los teclados.

Comandos de verificación desde la raíz del proyecto en Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```
