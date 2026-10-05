# Structa — Plan técnico para editor Android propio

## Estado posterior a fase 11 — edición de archivos grandes (2026-10-04)

La fase 12 queda pospuesta por petición del usuario. Se retiró el bloqueo heredado
de edición de archivos mayores de 1 MB y el aviso multilínea de solo lectura.
Edición, cursor, guardar original/copia, Undo/Redo, formato y reemplazo ya no se
deshabilitan por ese tamaño. Tampoco se rechaza un reemplazo porque el documento
resultante exceda 1 MB. Las referencias al límite editable en los cierres de fases
anteriores son históricas y quedan sustituidas por esta nota.

Se mantienen límites independientes: apertura de 25 MB, resaltado hasta 1 048 576
unidades UTF-16 y expansión de reemplazos hasta 1 048 576 unidades UTF-16 por
operación. El indicador de límite de resaltado permanece visible.

Validación: **138 JVM y 29 instrumentadas aprobadas**, cero fallos/errores/omisiones
en los informes finales; APK app y tests compilados. Las pruebas nuevas cubren
documentos de más de 2 MB: IME, selección, scroll, Undo/Redo, reemplazo y ambos
guardados mediante writers simulados. JVM XML 2026-10-04T16:47:55Z–16:47:57Z;
Android XML 2026-10-04T16:52:55, Pixel_6_Pro Android 12. Última ejecución
`:app:connectedDebugAndroidTest`: BUILD SUCCESSFUL en 1m 24s. Se ajustó la prueba
de sintaxis para admitir resultados nuevos que terminan antes de su aserción,
manteniendo el rechazo de rangos obsoletos.

La edición IME medida puntualmente tardó 32 ms en el emulador. No constituye una
garantía de fluidez: `syncInput` y `derive` conservan recorridos completos O(n).

Instalación final: `:app:installDebug`, BUILD SUCCESSFUL en 16 s. Aplicación
abierta en Pixel_6_Pro; UIAutomator confirma el documento de 2 MB y 52 132 líneas,
sin el aviso de solo lectura y con las acciones de edición/guardado habilitadas.

## 1. Objetivo

**Corrección gráfica posterior:** el Canvas de `StructaEditorView` se recorta
a sus límites locales antes de dibujar y restaura el clip al terminar. Evita que
el fondo tape los paneles Compose de Buscar/Reemplazar. Regresión con Canvas
compartido reproducida antes del arreglo y aprobada después; 7 instrumentadas de
vista y 3 de productividad aprobadas en ejecuciones separadas.

**Ajuste de UI posterior:** retirado el DropdownMenu de formato y las filas
permanentes de herramientas, metadata y guardado. Acciones, guardar copia y modos
de formato se acceden desde Comandos. Metadata y dirty de documento único están
en la barra superior; pestañas solo con varios documentos. Editor ocupa desde la
barra superior hasta navegación inferior, salvo panel de búsqueda abierto.
Verificado en Pixel_6_Pro: 2364 px de altura (+700 px); 6 instrumentadas de
documentos/productividad aprobadas y aplicación instalada.

**Reorganización posterior de menús:** barra con icono Atrás, información del
archivo y botones enmarcados Archivo / Editar. Archivo: Abrir, Guardar,
Guardar Como... y Cerrar. Editar: Buscar, Reemplazar, Ir a línea, Deshacer,
Rehacer, Formatear y Formato (selector Texto/JSON/YAML). Sustituye Comandos y
su paleta filtrable; Ctrl+Shift+P abre Editar. Quick Open deja de ser una opción
separada: el selector se llama Abrir, conservando Ctrl+P. Guardar Como... conserva
la semántica de exportar copia. Seis instrumentadas focalizadas aprobadas (24 s).

Construir un editor de código propio para Android, sin utilizar motores de edición de terceros como Sora Editor.

El editor debe evolucionar hasta ofrecer capacidades similares a un editor de escritorio moderno:

- Abrir archivos.
- Abrir carpetas/workspaces.
- Navegar archivos y directorios.
- Editar texto.
- Guardar cambios.
- Undo / Redo.
- Selección de texto.
- Copiar / cortar / pegar.
- Buscar / reemplazar.
- Múltiples archivos abiertos mediante tabs.
- Recuperar sesiones.
- Syntax highlighting.
- Keyboard shortcuts.
- Command Palette.
- Quick Open.
- LSP en fases posteriores.
- Git en fases posteriores.
- Providers adicionales:
    - SAF.
    - Shizuku.
    - OneDrive.

El motor de edición debe ser desarrollado completamente dentro de Structa.

---

# 2. Restricciones principales

## No utilizar

No usar motores de edición externos como:

- Sora Editor.
- Monaco.
- CodeMirror.
- Ace.
- WebView como editor principal.
- `TextField` / `BasicTextField` como motor principal del editor.

Compose puede utilizarse para la UI general de la aplicación.

El área de edición debe utilizar un renderer propio.

---

# 3. Arquitectura actual

La aplicación actualmente utiliza aproximadamente esta estructura:

```text
com.edro08.structa
├── app
├── application
│   ├── browser
│   ├── document
│   └── editor
├── data
│   └── repository
│       ├── files
│       └── settings
├── domain
│   ├── model
│   └── port
└── ui
    ├── component
    ├── navigation
    ├── screen
    │   ├── browser
    │   ├── editor
    │   ├── home
    │   └── settings
    └── theme
```

No reestructurar todo el proyecto inmediatamente.

La evolución debe realizarse progresivamente.

---

# 4. Arquitectura objetivo inicial

Reorganizar gradualmente hacia:

```text
com.edro08.structa
│
├── app
│
├── application
│   ├── browser
│   ├── document
│   └── editor
│
├── domain
│   │
│   ├── editor
│   │   ├── buffer
│   │   ├── cursor
│   │   ├── document
│   │   ├── history
│   │   ├── command
│   │   └── search
│   │
│   ├── filesystem
│   └── settings
│
├── data
│   ├── filesystem
│   │   ├── saf
│   │   └── local
│   └── settings
│
└── ui
    ├── common
    ├── navigation
    ├── browser
    ├── editor
    │   ├── component
    │   ├── input
    │   ├── model
    │   ├── render
    │   ├── view
    │   ├── EditorScreen.kt
    │   └── EditorViewModel.kt
    ├── home
    ├── settings
    └── theme
```

---

# 5. Regla arquitectónica principal

`domain.editor` debe ser Kotlin puro.

No debe importar:

```kotlin
android.*
androidx.*
androidx.compose.*
```

El motor debe poder probarse mediante unit tests sin Android.

La dependencia conceptual debe ser:

```text
UI
 ↓
Application
 ↓
Domain Editor
```

Y para archivos:

```text
Application
 ↓
FileSystem
 ↓
SAF / Local / Shizuku / OneDrive
```

El editor no debe conocer SAF, Shizuku, OneDrive o Android Storage directamente.

---

# 6. Editor Core

Crear:

```text
domain/editor/
```

Este paquete contiene el motor real del editor.

---

# 7. TextBuffer

No almacenar el documento editable únicamente como:

```kotlin
var text: String
```

Crear una abstracción:

```kotlin
interface TextBuffer {

    val length: Int

    val lineCount: Int

    fun charAt(offset: Int): Char

    fun insert(
        offset: Int,
        text: CharSequence
    )

    fun delete(
        start: Int,
        end: Int
    )

    fun replace(
        start: Int,
        end: Int,
        text: CharSequence
    )

    fun getText(
        start: Int,
        end: Int
    ): CharSequence

    fun getLine(
        line: Int
    ): CharSequence

    fun getLineStart(
        line: Int
    ): Int

    fun getLineEnd(
        line: Int
    ): Int
}
```

---

# 8. Piece Table

Implementar el primer `TextBuffer` utilizando Piece Table.

Crear:

```text
domain/editor/buffer/
├── TextBuffer.kt
├── PieceTableBuffer.kt
├── Piece.kt
├── BufferSource.kt
└── LineIndex.kt
```

Modelo inicial:

```kotlin
enum class BufferSource {
    ORIGINAL,
    ADDED
}
```

```kotlin
data class Piece(
    val source: BufferSource,
    val start: Int,
    val length: Int
)
```

Mantener:

```text
OriginalBuffer
AddBuffer
Pieces
```

`OriginalBuffer`:

- Contiene el texto inicial.
- Nunca se modifica.

`AddBuffer`:

- Recibe todo texto nuevo.
- Debe ser append-only.

`Pieces`:

- Describe cómo reconstruir el documento lógico.

---

# 9. Operaciones obligatorias del Piece Table

Implementar y probar:

```text
insert
delete
replace
getText
charAt
length
```

Casos obligatorios:

- Insertar al inicio.
- Insertar al final.
- Insertar en medio.
- Borrar al inicio.
- Borrar al final.
- Borrar en medio.
- Borrar atravesando varios pieces.
- Insertar múltiples veces.
- Replace.
- Documento vacío.
- Insertar `\n`.
- Borrar `\n`.

---

# 10. LineIndex

Crear un índice de líneas.

Debe permitir:

```kotlin
interface LineIndex {

    val lineCount: Int

    fun getLineStart(line: Int): Int

    fun getLineEnd(line: Int): Int

    fun getLineForOffset(offset: Int): Int
}
```

Debe actualizarse al realizar:

```text
insert
delete
replace
```

No recorrer todo el documento desde el inicio cada vez que se solicite una posición de línea.

---

# 11. Posiciones de texto

Crear:

```text
domain/editor/cursor/
├── TextOffset.kt
├── TextRange.kt
├── LineColumn.kt
├── Cursor.kt
└── Selection.kt
```

Usar offset como representación principal.

Ejemplo:

```kotlin
@JvmInline
value class TextOffset(
    val value: Int
)
```

Crear:

```kotlin
data class TextRange(
    val start: TextOffset,
    val end: TextOffset
)
```

Crear:

```kotlin
data class LineColumn(
    val line: Int,
    val column: Int
)
```

Implementar conversiones:

```text
offset → line/column

line/column → offset
```

---

# 12. Cursor

Crear:

```kotlin
data class Cursor(
    val offset: TextOffset
)
```

El cursor debe soportar posteriormente:

```text
left
right
up
down
home
end
document start
document end
word left
word right
```

Inicialmente implementar:

```text
left
right
home
end
```

---

# 13. Selection

Crear selección usando:

```kotlin
data class Selection(
    val anchor: TextOffset,
    val active: TextOffset
)
```

No guardar únicamente `start/end`.

Debe conservarse la dirección de selección.

Agregar propiedades o funciones para obtener:

```text
start
end
range
isEmpty
```

---

# 14. EditorDocument

Crear:

```text
domain/editor/document/
```

Ejemplo:

```kotlin
class EditorDocument(
    val id: DocumentId,
    val buffer: TextBuffer,
    val history: EditHistory
)
```

Añadir progresivamente:

```text
encoding
lineEnding
language
dirty
revision
```

No guardar estado visual aquí.

No guardar:

```text
scroll
zoom
cursor visual
viewport
```

---

# 15. Undo / Redo

Crear:

```text
domain/editor/history/
├── EditOperation.kt
├── EditTransaction.kt
└── EditHistory.kt
```

No guardar snapshots completos del documento.

Guardar operaciones.

Ejemplo:

```kotlin
sealed interface EditOperation {

    data class Insert(
        val offset: Int,
        val text: String
    ) : EditOperation

    data class Delete(
        val offset: Int,
        val text: String
    ) : EditOperation
}
```

`Undo` debe ejecutar la operación inversa.

Ejemplo:

```text
Insert("hello")
↓ undo
Delete("hello")
```

---

# 16. EditTransaction

Crear agrupación de operaciones.

Ejemplo:

```text
beginTransaction

insert
insert
insert

commitTransaction
```

Debe permitir que escribir:

```text
hello
```

pueda convertirse en una sola acción de Undo cuando corresponda.

Debe servir posteriormente para:

- Paste.
- Auto-indent.
- Replace All.
- Multi-cursor.
- Auto-close brackets.

---

# 17. Command System

Crear:

```text
domain/editor/command/
```

Las modificaciones del editor deben realizarse mediante comandos.

Ejemplo conceptual:

```kotlin
interface EditorCommand {
    fun execute(context: EditorContext)
}
```

Comandos iniciales:

```text
InsertTextCommand
DeleteBackwardCommand
DeleteForwardCommand
MoveCursorLeftCommand
MoveCursorRightCommand
MoveCursorHomeCommand
MoveCursorEndCommand
SelectLeftCommand
SelectRightCommand
UndoCommand
RedoCommand
```

No modificar directamente el buffer desde botones o eventos de UI.

Flujo obligatorio:

```text
Input
 ↓
Command
 ↓
Editor Engine
 ↓
TextBuffer
 ↓
History
```

---

# 18. Editor Engine

Crear una clase responsable de coordinar:

```text
document
cursor
selection
history
commands
```

Ejemplo conceptual:

```kotlin
class EditorEngine(
    val document: EditorDocument
) {

    var cursor: Cursor
        private set

    var selection: Selection?
        private set

    fun execute(
        command: EditorCommand
    )
}
```

No añadir Android aquí.

---

# 19. Tests del Editor Core

Antes de desarrollar el renderer, crear tests para:

## Buffer

```text
insert
delete
replace
```

## Lines

```text
line count
line start
line end
offset → line
line → offset
```

## Cursor

```text
left
right
home
end
```

## Selection

```text
forward selection
backward selection
clear selection
```

## History

```text
undo insert
redo insert
undo delete
redo delete
multiple operations
transactions
```

El Editor Core debe ser completamente funcional sin UI.

---

# 20. FileSystem

Cambiar progresivamente:

```text
data/repository/files
```

hacia una abstracción de filesystem.

Crear:

```text
domain/filesystem/
├── FileSystem.kt
└── FileRef.kt
```

Ejemplo:

```kotlin
interface FileSystem {

    suspend fun read(
        file: FileRef
    ): ByteArray

    suspend fun write(
        file: FileRef,
        data: ByteArray
    )

    suspend fun list(
        directory: FileRef
    ): List<FileRef>

    suspend fun createFile(
        parent: FileRef,
        name: String
    ): FileRef

    suspend fun createDirectory(
        parent: FileRef,
        name: String
    ): FileRef

    suspend fun rename(
        file: FileRef,
        name: String
    )

    suspend fun delete(
        file: FileRef
    )
}
```

---

# 21. FileRef

No asumir que todo archivo posee una ruta física normal.

`FileRef` debe poder representar:

```text
SAF URI
Local path
Shizuku path
OneDrive identifier
```

No diseñarlo únicamente alrededor de `java.io.File`.

---

# 22. FileSystem implementations

Primera implementación:

```text
data/filesystem/saf/
└── SafFileSystem.kt
```

Posteriormente:

```text
data/filesystem/local/
data/filesystem/shizuku/
data/filesystem/onedrive/
```

Cada provider debe implementar la misma interfaz.

---

# 23. Application Layer

`application/editor` debe coordinar Editor + FileSystem.

Crear casos de uso o servicios para:

```text
OpenDocument
SaveDocument
CloseDocument
SaveAllDocuments
RestoreSession
```

Ejemplo:

```text
OpenDocument
     │
     ├── FileSystem.read()
     │
     └── EditorDocument
```

El Piece Table no debe saber qué es un archivo.

---

# 24. Renderizado

Después de completar Editor Core, desarrollar el renderer.

Ubicación:

```text
ui/editor/
├── render
├── view
├── input
└── model
```

---

# 25. StructaEditorView

Crear un custom Android View:

```text
StructaEditorView
```

Integrarlo dentro de Compose mediante:

```kotlin
AndroidView(...)
```

Compose seguirá manejando:

- Tabs.
- Toolbar.
- Explorer.
- Menús.
- Settings.
- Search panel.
- Bottom panel.

El área de edición será renderizada manualmente.

---

# 26. Renderer inicial

Crear:

```text
ui/editor/render/
├── EditorRenderer.kt
├── TextRenderer.kt
├── CursorRenderer.kt
├── SelectionRenderer.kt
└── GutterRenderer.kt
```

Pipeline inicial:

```text
background
↓
current line
↓
selection
↓
text
↓
cursor
↓
gutter
```

---

# 27. Fuente

La primera versión debe utilizar solamente fuente monoespaciada.

Esto permite calcular:

```text
x = column * characterWidth
```

y:

```text
y = line * lineHeight
```

No implementar inicialmente layout complejo de fuentes proporcionales.

---

# 28. Viewport

No renderizar todo el archivo.

Crear:

```kotlin
data class EditorViewport(
    val firstVisibleLine: Int,
    val lastVisibleLine: Int,
    val scrollX: Float,
    val scrollY: Float
)
```

Renderizar únicamente:

```text
líneas visibles
+
pequeño margen superior/inferior
```

Ejemplo:

```text
archivo: 100000 líneas

viewport:
2340 → 2380

solo renderizar ~40-50 líneas
```

---

# 29. Scroll

Implementar:

```text
vertical scroll
horizontal scroll
```

Debe manejarse mediante estado propio del editor view.

No recrear todo el documento al hacer scroll.

---

# 30. Hit Testing

Convertir coordenadas de pantalla:

```text
x / y
```

a:

```text
line / column
```

y posteriormente:

```text
offset
```

Con fuente monoespaciada:

```text
line =
(y + scrollY) / lineHeight
```

```text
column =
(x + scrollX - gutterWidth) / characterWidth
```

Limitar column a la longitud real de la línea.

---

# 31. Input táctil

Implementar progresivamente:

```text
tap → mover cursor
drag → selección
double tap → seleccionar palabra
long press → selección/context menu
```

Los handles de selección pueden añadirse después.

---

# 32. Android IME

Crear:

```text
ui/editor/input/
└── EditorInputConnection.kt
```

El custom editor debe soportar correctamente:

```text
commitText
setComposingText
finishComposingText
deleteSurroundingText
setSelection
```

No depender solamente de KeyEvent.

Debe funcionar con:

```text
Gboard
SwiftKey
Samsung Keyboard
```

---

# 33. Composing Text

Mantener separado:

```text
committed text
```

de:

```text
IME composing text
```

El texto en composición debe poder actualizarse sin crear operaciones erróneas de Undo.

---

# 34. Clipboard

Agregar:

```text
copy
cut
paste
select all
```

Estas acciones deben utilizar el Command System cuando modifiquen contenido.

---

# 35. Keyboard shortcuts

Crear:

```text
KeyBindingHandler
```

Mapear:

```text
Ctrl + Z
Ctrl + Shift + Z
Ctrl + Y
Ctrl + S
Ctrl + A
Ctrl + C
Ctrl + X
Ctrl + V
Ctrl + F
Ctrl + H
Ctrl + G
Ctrl + P
```

Los shortcuts deben ejecutar comandos.

No lógica duplicada.

---

# 36. Tabs

Implementar administración de documentos abiertos.

Cada tab debe referenciar un `EditorDocument`.

Estado mínimo:

```text
documentId
title
dirty
active
```

Posteriormente:

```text
pinned
preview
```

---

# 37. EditorViewState

Separar estado visual del documento.

Crear:

```kotlin
data class EditorViewState(
    val cursor: Cursor,
    val selection: Selection?,
    val scrollX: Float,
    val scrollY: Float
)
```

Esto permitirá eventualmente mostrar el mismo documento en dos vistas diferentes.

---

# 38. Dirty State

Cada `EditorDocument` debe detectar si fue modificado desde el último save.

Mostrar:

```text
main.go ●
```

cuando existan cambios pendientes.

`Save` debe actualizar la revisión guardada.

---

# 39. Session Recovery

Guardar metadata de sesión.

Guardar:

```text
workspace abierto
tabs abiertos
tab activo
cursor por documento
scroll por documento
```

No guardar el documento completo permanentemente en Room.

---

# 40. Crash Recovery

Añadir posteriormente snapshots temporales para documentos modificados.

Objetivo:

```text
app muere
↓
se vuelve a abrir
↓
Structa ofrece recuperar cambios no guardados
```

Mantener separado:

```text
Save
```

de:

```text
Recovery
```

---

# 41. Search en documento

Crear:

```text
domain/editor/search/
```

Soportar:

```text
Find
Find Next
Find Previous
Replace
Replace All
Case Sensitive
Whole Word
Regex
```

No bloquear la UI.

---

# 42. Workspace Search

Posteriormente implementar búsqueda en todos los archivos del workspace.

Requisitos:

- Ejecutarse con Coroutines.
- Emitir resultados progresivamente.
- Poder cancelarse.
- Ignorar binarios.
- Permitir exclusiones.

---

# 43. Syntax Highlighting

Implementado en fase 11, después de completar edición estable. Alcance léxico y
límites documentados en el cierre de esa fase. API y tokenizadores en:

```text
domain/editor/syntax/
```

Diseñar API tipo:

```kotlin
interface LanguageTokenizer {

    fun tokenize(
        text: CharSequence,
        state: TokenizerState
    ): TokenizationResult
}
```

Cada línea debe producir spans.

Ejemplo:

```kotlin
data class SyntaxSpan(
    val start: Int,
    val end: Int,
    val style: SyntaxStyle
)
```

---

# 44. Tokenización incremental

No volver a tokenizar todo el archivo por cada tecla.

Al editar:

```text
identificar línea afectada
↓
retokenizar desde esa línea
↓
continuar hasta recuperar estado estable
```

---

# 45. Decorations

Crear un sistema independiente del buffer.

Ejemplo:

```kotlin
data class Decoration(
    val range: TextRange,
    val type: DecorationType
)
```

Debe poder representar posteriormente:

```text
syntax
errors
warnings
search matches
selected occurrence
git changes
bracket matching
breakpoints
```

Nunca introducir metadata visual dentro del texto.

---

# 46. Word Wrap

No implementar en la primera versión del renderer.

Primera versión:

```text
wordWrap = false
```

Usar scroll horizontal.

Añadir word wrap una vez estable el sistema básico.

---

# 47. Unicode

Primera versión:

Utilizar offsets UTF-16 consistentemente.

No intentar resolver inicialmente todas las reglas Unicode avanzadas.

Después implementar navegación correcta por grapheme clusters para:

```text
←
→
Backspace
Delete
selection
```

Casos como:

```text
emoji
combining characters
ZWJ sequences
```

deben tratarse en una fase posterior.

---

# 48. Browser / Explorer

Mantener la feature actual.

Debe utilizar `FileSystem`.

Funciones necesarias:

```text
open folder
list directory
expand folder
collapse folder
create file
create folder
rename
delete
refresh
```

No cargar recursivamente todo el árbol al abrir un workspace.

Cargar children de forma lazy.

---

# 49. Workspace

Crear concepto de Workspace.

Debe contener al menos:

```text
id
root
filesystem
open documents
```

Posteriormente:

```text
settings
language configuration
git root
```

---

# 50. Quick Open

Después del workspace:

Implementar comportamiento similar a:

```text
Ctrl + P
```

Indexar:

```text
file name
relative path
```

Permitir búsqueda fuzzy.

No leer contenido completo de cada archivo para Quick Open.

---

# 51. Command Palette

Implementar:

```text
Ctrl + Shift + P
```

Los comandos deben provenir del mismo Command System.

Ejemplos:

```text
Save
Save All
Close File
Find
Replace
Go To Line
Toggle Word Wrap
Change Language
Change Encoding
```

---

# 52. Go To Line

Implementar:

```text
Ctrl + G
```

Convertir:

```text
line
```

a:

```text
offset
```

usando `LineIndex`.

---

# 53. Language Registry

Implementado en fase 11:

```text
LanguageRegistry
```

Mapear:

```text
.go → Go
.kt → Kotlin
.java → Java
.json → JSON
.yaml → YAML
.md → Markdown
```

No acoplar lenguaje directamente al nombre del archivo dentro del renderer.

---

# 54. LSP

No implementar antes de tener:

```text
buffer estable
renderer estable
search
tabs
workspace
syntax highlighting
```

Posteriormente crear una capa LSP independiente.

Funciones futuras:

```text
autocomplete
diagnostics
hover
go to definition
references
symbols
rename
```

El LSP no debe modificar directamente el buffer.

Debe pasar por Editor Commands.

---

# 55. Git

Agregar posteriormente.

Primera fase:

```text
modified
added
deleted
untracked
```

Después:

```text
diff
stage
commit
branches
pull
push
```

No acoplar Git al FileSystem Core.

---

# 56. Shizuku

Agregar solamente cuando FileSystem esté estable.

Crear:

```text
ShizukuFileSystem
```

Debe implementar la misma interfaz:

```kotlin
FileSystem
```

El editor no debe requerir cambios para soportarlo.

---

# 57. OneDrive

Agregar posteriormente:

```text
OneDriveFileSystem
```

Debe resolver:

```text
list
read
write
create
rename
delete
```

Considerar:

```text
cache local
offline
version conflicts
remote modification
```

No mezclar API de OneDrive con Editor Core.

---

# 58. Orden de implementación

Seguir este orden.

## Fase 1 — Editor Core

### Avance de implementación

- [x] Fase 1A: `TextBuffer`, `PieceTableBuffer`, `Piece`, `BufferSource` y `LineIndex`.
- [x] Pruebas JVM del buffer: 11 tests aprobados, incluyendo 6.000 ediciones aleatorias
  reproducibles contrastadas con `StringBuilder` y verificación de líneas tras cada edición.
- [x] Fase 1B: `TextOffset`, `TextRange`, `LineColumn`, `Cursor` y `Selection`.
- [x] Conversiones `TextBuffer.toLineColumn` y `TextBuffer.toOffset` mediante el índice.
- [x] 10 tests adicionales de posiciones y selección, incluyendo conversiones tras
  500 ediciones aleatorias. Total del núcleo: 21 tests aprobados, sin fallos.

Contrato implementado: offsets UTF-16, rangos `[start, end)`, líneas desde cero y una
línea para el documento vacío. LF separa líneas; CR se conserva como contenido,
también en CRLF. `getLineEnd` excluye LF y `getLine` conserva CR. No se normalizan
saltos de línea. El índice se actualiza por edición sin releer el documento completo;
consultas por línea O(1), por offset O(log líneas). La búsqueda de piezas es lineal
en esta primera implementación.

Las posiciones y columnas son no negativas; `TextRange` requiere `start <= end`.
Las conversiones aceptan fin de línea y EOF, y rechazan posiciones fuera del buffer
sin ajustarlas silenciosamente. Las columnas cuentan unidades UTF-16, incluidos CR
y tabs. `Selection` conserva `anchor/active`, expone el rango ordenado y permite
colapsar la selección en su extremo activo. `Cursor` representa la posición lógica;
sus movimientos y la edición de selecciones corresponden a la fase 2.

Verificación inicial: `:app:testDebugUnitTest` compilaba producción, pero la suite general
estaba bloqueada por pruebas preexistentes en `DocumentUseCasesTest` y
`PresentationViewModelsTest` que heredan de `ContentFormatDetector`, actualmente
final en ese momento. Los 21 tests del núcleo se ejecutaron correctamente aislando el source set de
test mediante un init script temporal de Gradle, sin cambiar la configuración del
proyecto. Ese bloqueo quedó resuelto en la fase 5, con la suite completa aprobada.

Implementar:

```text
TextBuffer
PieceTableBuffer
Piece
LineIndex
TextOffset
TextRange
LineColumn
Cursor
Selection
```

---

## Fase 2 — Editing

### Avance de implementación

- [x] `domain/editor/editing/EditingSession.kt`: primitivas de edición en Kotlin puro
  para su coordinación posterior mediante `EditorEngine` y comandos.
- [x] Inserción en cursor, reemplazo de selección y reemplazo de rango explícito.
- [x] Backspace y Delete: eliminan primero la selección, o una unidad UTF-16 adyacente.
- [x] Movimientos left/right/home/end y posicionamiento por offset.
- [x] Extensión de selección conservando el anchor, selección explícita y limpieza.
- [x] 15 tests nuevos; 36 tests del núcleo aprobados sin fallos en ejecución aislada.

Contrato: el cursor coincide con el extremo activo de la selección; una selección
vacía se representa como `null`. Left/right sin extensión colapsan la selección al
inicio/final, sin dar un paso adicional. Home/end usan la línea del extremo activo.
Reemplazar deja el cursor al final del texto insertado y limpia la selección.
Insertar texto vacío con selección elimina su contenido. Los movimientos y borrados
se limitan a los extremos del documento. Se mantiene el contrato UTF-16 y LF/CR de
la fase 1; la navegación por grafemas queda para la fase Unicode posterior.

Mientras exista una `EditingSession`, sus ediciones deben ser la única vía de
mutación del buffer asociado. No contiene estado visual; desde la fase 3 coordina
el historial de operaciones.
Las pruebas cubren también rangos inválidos y rechazo de edición por el buffer,
verificando que el cursor y la selección no se alteran si falla la operación.
La ejecución general quedó pendiente en esta fase; el bloqueo se resolvió en la fase 5.

Implementar:

```text
insert
delete backward
delete forward
replace
cursor movement
selection
```

---

## Fase 3 — History

### Avance de implementación

- [x] `EditOperation`: Insert, Delete y Replace; almacena únicamente el texto afectado.
- [x] `EditTransaction`: operaciones agrupadas y estado lógico anterior/posterior.
- [x] `EditHistory`: pilas de Undo/Redo, ejecución inversa y transacciones explícitas.
- [x] Integración en `EditingSession`: `undo`, `redo`, `canUndo`, `canRedo`,
  `beginTransaction`, `commitTransaction` e `isInTransaction`.
- [x] 13 tests nuevos; 49 tests del núcleo aprobados sin fallos en ejecución aislada.

Undo aplica las inversas en orden inverso; Redo aplica las operaciones en orden
original. Ambos restauran cursor y selección, incluida su dirección. Un reemplazo
se ejecuta mediante una sola llamada al buffer. No se guardan snapshots completos
del documento para el historial.

Las transacciones son explícitas y no anidadas. Deben confirmarse antes de Undo/Redo;
mientras están abiertas `canUndo` y `canRedo` son falsos. Una transacción vacía no
genera historial. Una edición efectiva invalida Redo, incluso dentro de una
transacción; movimientos, ediciones idénticas y operaciones vacías lo conservan.
La agrupación temporal automática de tecleo queda para la integración de input.

Se verificaron restauración de selecciones, agrupaciones mixtas, fallos de edición
y replay con reversión de pasos ya aplicados, además de 300 ediciones aleatorias
seguidas por Undo completo y Redo completo contra un modelo de referencia.
La ejecución general quedó pendiente en esta fase; el bloqueo se resolvió en la fase 5.

Implementar:

```text
EditOperation
EditTransaction
Undo
Redo
```

---

## Fase 4 — Commands

### Avance de implementación

- [x] `EditorDocument`: identidad en memoria, buffer e historial, sin estado visual
  ni dependencia del filesystem. Reutiliza `domain.document.DocumentId`.
- [x] `EditorEngine`: coordina estado lógico y expone `execute(EditorCommand)`.
- [x] `EditorCommand` y `EditorContext`: comandos sin referencias a documentos concretos.
- [x] Comandos de inserción, reemplazo, borrado hacia atrás/delante, movimiento,
  selección, Undo/Redo y begin/commit de transacciones.
- [x] Primer milestone funcional sin UI verificado mediante comandos.
- [x] 8 tests de integración nuevos; 57 tests del núcleo aprobados sin fallos.

El documento es dueño del historial; el engine conserva cursor y selección mediante
su `EditingSession`. Los comandos delegan en las mismas primitivas ya probadas y no
modifican el buffer directamente. El buffer entregado al documento se considera
transferido: sus modificaciones deben pasar por el engine. Inicialmente se permite
un engine por documento para evitar cursores obsoletos; las vistas compartidas se
abordarán en la fase de split editor.

Las pruebas verifican el ejemplo del milestone, selección direccional, reemplazo,
borrados, movimiento, transacciones, rechazo de comandos inválidos e independencia
de documentos. Verificación JVM aislada del núcleo, sin dependencias Android en
`domain.editor`. El bloqueo de la suite general descrito en la fase 1 quedó
resuelto en la fase 5 antes de avanzar al renderer.

Implementar:

```text
EditorCommand
EditorEngine
InsertTextCommand
DeleteCommand
MoveCursorCommand
UndoCommand
RedoCommand
```

---

## Fase 5 — Tests

No continuar con renderer hasta que Editor Core tenga tests estables.

### Estado: completada

- [x] Revisión de los casos críticos del núcleo exigidos por la sección 19.
- [x] Suite completa ejecutada con Gradle, sin init scripts ni exclusiones de tests.
- [x] 73 pruebas aprobadas: 57 del núcleo y 16 de aplicación/ViewModels;
  cero fallos, cero errores y cero pruebas omitidas.
- [x] Verificado que `domain.editor` no importa Android ni AndroidX.
- [x] Primer milestone sin UI cubierto por pruebas de integración de comandos.

Cobertura funcional revisada (no representa un porcentaje de cobertura de código):

| Área | Tests | Casos principales |
| --- | ---: | --- |
| Piece Table e índice | 11 | Inserción, borrado y reemplazo, múltiples piezas, límites, LF/CRLF, UTF-16 y 6.000 ediciones aleatorias |
| Posiciones y selección | 10 | Conversiones, fin de documento, rangos inválidos, dirección y 500 ediciones aleatorias |
| Edición | 15 | Movimientos, selección, reemplazo, borrados, límites y rechazo de operaciones |
| Historial | 13 | Undo/Redo, transacciones, ramas de Redo, restauración de estado, fallos de replay y 300 ediciones con Undo/Redo completos |
| Engine y comandos | 8 | Primer milestone, estado lógico, transacciones y documentos independientes |
| Casos de uso | 7 | Apertura, validación, errores, guardado y conservación de contenido no JSON |
| ViewModels | 9 | Edición, búsquedas, guardado, resultados asíncronos obsoletos, explorador y preferencias |

Bloqueos resueltos:

1. `ContentFormatDetector` y `detect` permiten sustitución mediante `open`, coherente
   con el patrón existente de `FormatJsonDocument` y los dobles de prueba utilizados.
2. La prueba de formateo dejó de esperar el cambio de hilo de un método que ella misma
   sustituía. Ahora verifica el formateador real preservando contenido TEXT/YAML.
3. La prueba de formateo concurrente usa `CompletableDeferred` y suspensión en lugar
   de bloquear el hilo del test con `CountDownLatch`; comprueba que un resultado
   obsoleto no sobrescriba una edición posterior.

Comando de verificación desde la raíz del proyecto:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

En este entorno se usó el JDK de Android Studio configurando `JAVA_HOME` solo para
el proceso de ejecución. Resultado: `BUILD SUCCESSFUL`.
Informe: `app/build/reports/tests/testDebugUnitTest/index.html`.

El criterio de pruebas previo al renderer queda satisfecho. La fase 6 es el
siguiente trabajo; esta validación corresponde al núcleo y a la suite JVM actual.

---

## Fase 6 — Renderer

### Estado: completada

- [x] `ui/editor/view/StructaEditorView`: View propio sobre Canvas, sin mutaciones del buffer.
- [x] `EditorRenderer`, `TextRenderer`, `CursorRenderer`, `SelectionRenderer` y `GutterRenderer`.
- [x] Pipeline: fondo, línea actual, selección, texto, cursor y gutter fijo.
- [x] Fuente monoespaciada, colores de MaterialTheme y tamaño de texto en sp.
- [x] `EditorViewport`: líneas visibles más dos líneas de margen por extremo.
- [x] Scroll vertical/horizontal, arrastre, fling, rueda de ratón y barras de scroll.
- [x] Integración Compose mediante `ui/editor/component/StructaEditor` y `AndroidView`.
- [x] Integración en el visor de archivos grandes de `EditorScreen`, reemplazando
  los chunks de texto por líneas reales renderizadas en Canvas.
- [x] 11 pruebas JVM nuevas y 5 instrumentadas; resultado global: 84 JVM y 5 Android
  aprobadas, cero fallos, errores u omisiones. APK debug compilado correctamente.

El estado de scroll reside en la View. Los layouts se conservan solo para el
viewport y se reutilizan entre frames. El ancho horizontal se descubre al visitar
líneas, sin escanear todo el documento al abrirlo; se reinicia al invalidar contenido.
Las líneas largas usan checkpoints cada 256 unidades UTF-16 para mapear columnas y
solo expanden el tramo horizontal visible. Las tabulaciones avanzan a stops de cuatro
columnas; CR conserva una celda en blanco conforme al contrato actual del núcleo.
La View no recrea el documento al desplazarse y el gutter no se mueve horizontalmente.

Contrato de actualización: `StructaEditorView.bind(engine, version)` conserva scroll
para el mismo engine; cambiar el engine vuelve al inicio. Después de comandos externos
se llama a `refresh()` o se incrementa `contentVersion` en el componente Compose.
Al cambiar contenido o dimensiones se ajusta el scroll a los límites disponibles.

Integración gradual: esta fase conectó la superficie de lectura para archivos grandes.
La fase 7 completó la sustitución del área editable con input táctil, teclado e IME
y la verificación del segundo milestone. Word wrap y layout Unicode avanzado
mantienen su planificación posterior.

Verificación ejecutada en el emulador `Pixel_6_Pro` con Android 12:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest
```

Pruebas críticas: viewport de 100.000 líneas sin lecturas del documento completo,
reutilización de layouts, dibujo de texto/selección/cursor/gutter en bitmap, scroll
táctil en ambos ejes sin editar, refresco tras comandos, límites al reducir el
documento y cambio de documento. Las pruebas JVM cubren geometría, tabulaciones,
selección de LF, líneas vacías y recorte horizontal en líneas largas.

Implementar:

```text
StructaEditorView
EditorRenderer
TextRenderer
CursorRenderer
SelectionRenderer
GutterRenderer
Viewport
scroll
```

---

## Fase 7 — Input

### Estado: implementación completada y verificación automatizada aprobada

Cierre técnico: 2026-10-03. La matriz manual de teclados comerciales de la sección 32
queda pendiente; este cierre no certifica compatibilidad verificada con Gboard,
SwiftKey y Samsung Keyboard en todos sus dispositivos/versiones.

- [x] Toque para posicionar cursor, doble toque/pulsación larga para seleccionar
  palabra y arrastre para extender selección; menú contextual flotante.
- [x] `EditorInputSession` compartida por engine, View e integración de pantalla:
  composición provisional reemplazable, batches y confirmación agrupada en historial.
- [x] `EditorInputConnection` sin backing `Editable`: commit, composición, selección,
  consultas de contexto y borrado circundante UTF-16/por codepoints.
- [x] Borrado respeta selección/composición y pares surrogate; rechazo de secuencias
  inválidas sin ediciones parciales y de escrituras desde conexiones cerradas.
- [x] Teclado físico: escritura, Enter/Tab, Backspace/Delete, navegación y selección
  con Shift; Ctrl/Meta para selección, clipboard e historial.
- [x] Shortcuts de aplicación enrutados mediante `EditorAction`, sin duplicar edición.
- [x] Clipboard del sistema: copiar, cortar, pegar y seleccionar todo; las mutaciones
  utilizan comandos y comparten Undo/Redo con el teclado y la toolbar.
- [x] Foco, apertura real del IME, cursor parpadeante, composición subrayada y revelado
  del cursor también al reducirse la altura de la vista enfocada.
- [x] `EditorScreen` utiliza `StructaEditor` con `imePadding`; conservar engine/sesión
  al actualizar versión no reinicia la conexión IME. Cambiar documento o pasar a solo
  lectura cierra la conexión anterior y confirma composición.
- [x] Segundo milestone implementado y verificado sobre la View propia.

**Resultados comprobados:** 99 pruebas JVM y 16 instrumentadas (11 de input y 5 de
renderer), cero fallos, errores u omisiones. APK debug y APK de pruebas compilados.
Instrumentadas ejecutadas en `Pixel_6_Pro`, Android 12; XML del cierre con timestamp
`2026-10-03T22:38:57`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Cobertura crítica: composición y Undo agrupado, cursores relativos del IME, batches,
borrado por codepoints, consultas de selección, teclado físico, clipboard, gestos,
apertura del teclado del sistema, cambio/cierre de conexión, transición a solo lectura
y cursor visible al reducir el viewport. La pulsación larga verifica la acción real
«Copiar» del menú flotante mediante Espresso. La integración del ViewModel comprueba
historial compartido y guardado durante composición.

Alcance y límites actuales:

- Contrato UTF-16; grafemas y bidi avanzados siguen pendientes. Los handles de selección
  conservan su carácter opcional/posterior.
- Composición agrupada en una transacción; sin agrupación temporal automática del tecleo.
- Replace, Quick Open y Command Palette tienen shortcuts enrutados y mensaje de función
  pendiente; sus interfaces corresponden a la fase 10.
- Edición interactiva hasta 1 MB; archivos mayores en solo lectura. `TextFieldValue`
  permanece como snapshot de presentación y `syncInput` reconstruye texto/derivados
  al cambiar contenido; no es el motor de edición.
- Validación automatizada del contrato IME y apertura del teclado del emulador;
  pendiente la matriz manual de Gboard, SwiftKey y Samsung Keyboard.

Implementar:

```text
tap
drag selection
keyboard
IME
clipboard
shortcuts
```

---

## Fase 8 — Documents

### Estado: completada

Cierre técnico: 2026-10-03.

- [x] Múltiples documentos abiertos, identificados por `DocumentId`, con un
  `EditorDocument`, `EditorEngine` e `EditorInputSession` independientes por pestaña.
- [x] Abrir un documento ya abierto activa su pestaña sin releer el archivo ni
  sustituir sus cambios, selección o historial.
- [x] Pestañas con título, estado activo, indicador `●` de cambios pendientes y cierre.
- [x] Cambio de documento conserva texto, Undo/Redo, cursor/selección, modo, búsqueda
  y scroll horizontal/vertical. La composición IME se confirma antes del cambio.
- [x] `EditorViewState` separado del documento: snapshot de cursor/selección y scroll
  en memoria; la posición lógica autoritativa sigue perteneciendo al engine.
  La View restaura el scroll al cambiar pestaña y al recrear la superficie.
- [x] `EditorDocument.revision`, `savedRevision` y `dirty` basados en identidades de
  revisión del historial, sin comparar ni guardar snapshots completos del contenido.
  Undo/Redo restaura revisiones; una rama nueva recibe identificadores distintos.
- [x] Guardar y Ctrl/Meta+S escriben en el archivo abierto mediante el puerto
  `FileWriter` y su adaptador SAF existente. Solo un guardado exitoso actualiza la
  revisión guardada del documento correspondiente.
- [x] Guardar copia mantiene la exportación mediante el selector del sistema; un
  destino distinto no marca limpio el archivo original ni cambia su identidad.
- [x] Cierre de documento limpio inmediato; documento modificado ofrece Guardar y
  cerrar, Descartar o Cancelar. Un fallo de escritura conserva la pestaña y sus cambios.
- [x] Guardado asíncrono captura contenido y revisión: editar o cambiar de pestaña
  durante la escritura no marca limpios cambios posteriores ni otro documento.
  Guardar y cerrar conserva la pestaña si aparecieron cambios nuevos.

**Verificación:** 107 pruebas JVM y 19 instrumentadas, sin fallos, errores u omisiones;
APK debug y APK de pruebas compilados. JVM: XML `2026-10-03T22:56:30Z`;
instrumentadas en `Pixel_6_Pro`, Android 12: `2026-10-03T22:57:21`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Pruebas nuevas: `DocumentRevisionTest` (2), `EditorDocumentsTest` (6),
`EditorDocumentsIntegrationTest` (2 instrumentadas) y restauración de ambos ejes
del viewport en `StructaEditorViewTest` (1 instrumentada adicional). Cubren revisiones
guardadas, historial divergente, sesiones independientes, composición al cambiar,
guardar/cerrar/reabrir, errores, cancelación/descarte y edición durante el guardado.
Las pruebas de pantalla pulsan las pestañas y los botones del diálogo real de cierre;
usan puertos de lectura/escritura en memoria, no una matriz de proveedores SAF.

Alcance: las pestañas y su estado viven en el ViewModel durante la sesión de la app.
La restauración después de morir el proceso y la metadata persistida del workspace
corresponden a la fase 9; recuperación de borradores y vistas simultáneas del mismo
documento mantienen su planificación posterior. Continúa el límite de edición
interactiva de 1 MB. El indicador dirty identifica la revisión guardada, no equivalencia
textual entre ediciones independientes que casualmente produzcan el mismo contenido.

Implementar:

```text
open
save
close
dirty
multiple documents
tabs
```

---

## Fase 9 — Workspace

### Estado: completada

Validación final: 2026-10-04 (timestamps UTC de los informes).

- [x] `FileSystem` neutral al proveedor, integrado con los puertos existentes
  `FileReader`, `FileWriter` y `DirectoryReader`. Ofrece lectura/escritura de texto,
  listado, metadata (`stat`), creación de archivo/carpeta, renombrado y eliminación.
  `FileRef` identifica documento y filesystem sin exigir una ruta física.
- [x] `SafFileSystem` coordina los adaptadores SAF existentes y las operaciones de
  `DocumentsContract` en IO. Renombrar devuelve la nueva referencia del proveedor,
  porque la URI puede cambiar. El editor continúa usando puertos neutrales.
- [x] Browser permite elegir workspace, navegar, expandir/contraer carpetas con
  carga diferida, crear archivos/carpetas, renombrar, eliminar con confirmación y
  actualizar. Las respuestas de expansiones antiguas se descartan al navegar.
- [x] `Workspace` contiene id, raíz (`FileRef` con proveedor) y documentos abiertos.
  La raíz actual se conserva con `SettingsRepository.lastFolder`; cambiar de raíz
  mantiene las pestañas abiertas. Hay un workspace raíz actual, no un catálogo de
  sesiones independientes por workspace.
- [x] Metadata de pestañas ordenadas, pestaña activa, selección/cursor y ambos ejes
  de scroll mediante `SessionRepository` y SharedPreferences. Checkpoints con
  debounce de 300 ms, más checkpoint al pasar la Activity a segundo plano.
- [x] Restauración en un ViewModel nuevo: consulta metadata actual del archivo,
  relee contenido, reconstruye sesiones y ajusta selección al tamaño disponible.
  Archivos ausentes/inaccesibles se omiten con aviso; se restauran los restantes.
  Metadata corrupta se interpreta como sesión vacía.
- [x] Permisos persistentes SAF, con intento de conservar solo lectura si el
  proveedor no admite persistir lectura/escritura conjuntamente.

**Política de operaciones sobre documentos abiertos:** renombrar/eliminar un archivo
abierto exige cerrar antes su pestaña, utilizando el flujo Guardar/Descartar existente.
Para renombrar/eliminar una carpeta se exige cerrar todas las pestañas, pues un id
opaco no permite deducir por prefijo qué documentos son descendientes. No se remapean
historiales vivos a nuevas URI. Errores de operaciones se muestran en el explorador.

**Verificación:** 112 JVM y 22 instrumentadas, cero fallos/errores/omisiones, APK de
aplicación y pruebas compilados. `WorkspaceTest` añade 5 JVM; `SessionPersistenceTest`
añade 2 instrumentadas; `SafFileSystemTest` añade 1 instrumentada con llamadas reales
de ContentResolver a un DocumentsProvider aislado del APK de pruebas. Esta última
comprueba creación anidada, escritura con truncado, listado, cambio de URI al renombrar
y eliminación. El proveedor y receptor de permisos de prueba están en Java porque se
ejecutan en el proceso del APK de pruebas, sin el runtime Kotlin de la aplicación.

Comando final, **BUILD SUCCESSFUL en 34 s**:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

XML JVM: alrededor de `2026-10-04T04:06:19Z`; instrumentadas en Pixel_6_Pro Android 12:
`2026-10-04T04:06:45`. `git diff --check` sin errores.

**Alcance de restauración:** se recupera metadata, no texto no guardado, composición,
historial Undo/Redo ni búsqueda/modo de la sesión anterior. El contenido se toma del
archivo y arranca limpio. Crash Recovery sigue planificado aparte. Las pruebas no
certifican una matriz de proveedores comerciales ni la UI del selector del sistema.
Siguiente fase: 10 — Productivity.

Implementar:

```text
FileSystem
SAF
file browser
workspace
session restore
```

---

## Fase 10 — Productivity

### Estado: completada

Cierre verificado el 2026-10-04.

- [x] `domain/editor/search/DocumentSearch`: búsqueda literal o regex, mayúsculas,
  palabra completa Unicode y offsets UTF-16. Admite espacios como consulta,
  coincidencias de longitud cero y errores de patrón/referencias de grupo.
- [x] Búsqueda sobre snapshots inmutables en `Dispatchers.Default`, debounce de
  120 ms y cancelación por documento al cambiar texto, consulta u opciones.
  Siguiente/anterior seleccionan la coincidencia y recorren circularmente los
  resultados; el Canvas revela la selección mediante la sesión de input existente.
- [x] Reemplazar actual y todo, con expansión regex `$0`, `$1`… y `${name}`.
  Si no hay una coincidencia seleccionada, Reemplazar actual selecciona la siguiente
  antes de editar. Los reemplazos literales no interpretan referencias de grupo.
- [x] `ReplaceMatchesCommand` valida el lote antes de mutar y aplica rangos en orden
  inverso dentro de una transacción: un Undo restaura texto y selección. Se confirma
  composición IME antes de reemplazar y se rechazan planes obsoletos tras edición,
  cambio de pestaña, consulta, opciones, reemplazo o selección de coincidencia actual.
- [x] Ir a línea integrado en botones, paleta y Ctrl/Meta+G; entrada positiva válida,
  Enter para aceptar y Escape para cancelar. Las líneas fuera de rango superior
  conservan el comportamiento existente de ir al final del documento.
- [x] Quick Open (`Ctrl/Meta+P`) indexa únicamente nombres/rutas relativas desde la
  raíz del workspace al abrir el diálogo. Recorrido en IO, filtrado fuzzy en Default,
  resultados progresivos, ids deduplicados y protección ante ciclos/respuestas de
  raíces antiguas. Cerrar cancela; reabrir reconstruye metadata actual.
- [x] Command Palette (`Ctrl/Meta+Shift+P`) comparte el registro `EditorAction` y el
  despachador con toolbar/atajos. Ofrece las acciones disponibles: guardar, cerrar,
  buscar/reemplazar, siguiente/anterior, ir a línea, Quick Open, Undo/Redo y formatear.
  Las operaciones de edición continúan llegando al Command System del engine.
- [x] Selectores con filtro, flechas, Enter, Escape y selección táctil. Foco inicial
  solicitado dentro del diálogo después de su primer frame. F3/Shift+F3 y
  Enter/Shift+Enter en búsqueda navegan coincidencias.
- [x] Find e ir a línea también funcionan en documentos de solo lectura; reemplazo
  respeta el límite de edición existente de 1 MB y valida el tamaño del resultado.

**Límites explícitos:** consultas de hasta 4096 caracteres y hasta 10 000 coincidencias;
la UI indica truncado y exige acotar antes de reemplazar resultados truncados. Regex
usa `java.util.regex` con presupuesto cooperativo de 500 ms: se comprueba entre
coincidencias y accesos al CharSequence. No es un timeout duro del motor nativo Android;
la prueba de regex patológica corresponde al motor JVM. El análisis se realiza fuera
del hilo UI. Quick Open limita el índice a 20 000 archivos / 5 000 carpetas y muestra
hasta 100 resultados; comunica índices parciales y carpetas inaccesibles. No lee el
contenido de los archivos para indexar. Workspace Search, Save All, word wrap y cambios
de encoding/lenguaje de los ejemplos de paleta no se añaden en esta fase.

**Verificación:** 129 JVM y 25 instrumentadas aprobadas, sin fallos, errores ni omisiones;
APK de app y pruebas compilados. Nuevas pruebas: 7 `DocumentSearchTest`, 6
`ProductivityTest`, 4 `QuickOpenTest` y 3 `ProductivityIntegrationTest`. Cubren rangos,
regex/grupos, lotes inválidos sin edición parcial, Undo, read-only, resultados obsoletos,
recorrido fuzzy, cambios de raíz, flujos Compose y atajos. Se amplió la prueba de input
con F3/Shift+F3 y se adaptaron las pruebas previas al resultado de búsqueda asíncrono.

La suite instrumentada completa pasó en Pixel_6_Pro Android 12, XML
`2026-10-04T16:08:41`. En esa ejecución conjunta falló una JVM por un trabajo simulado
no esperado al finalizar la prueba; se corrigió su lifecycle y la suite JVM completa
pasó después (XML `2026-10-04T16:09:25Z`–`16:09:26Z`). Último comando: **BUILD SUCCESSFUL
en 3 s**:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

La validación instrumentada se ejecutó con `:app:connectedDebugAndroidTest` en la
invocación conjunta anterior. No hubo cambios de producción ni instrumentadas tras
esa ejecución. `git diff --check` sin errores. Siguiente fase: **11 — Syntax**.

Implementar:

```text
find
replace
go to line
quick open
command palette
```

---

## Fase 11 — Syntax

### Estado: completada

Cierre verificado el 2026-10-04.

- [x] `domain/editor/syntax/LanguageRegistry`: detección por extensión sin distinguir
  mayúsculas de Kotlin (`kt/kts`), Java, Go, JSON (`json/jsonl`), YAML (`yaml/yml`) y
  Markdown (`md/markdown`). Extensiones desconocidas → texto plano. El renderer
  recibe spans; no interpreta nombres de archivo. La barra informa el lenguaje.
- [x] `LanguageTokenizer`, `TokenizerState`, `TokenizationResult`, `SyntaxSpan` y
  `SyntaxStyle`: contratos Kotlin puros, spans ordenados por línea en offsets UTF-16,
  estados de comentarios/cadenas/fences multilínea. Tokenizadores léxicos propios
  deterministas, con comprobaciones de cancelación también dentro de tokens largos.
- [x] Palabras clave, cadenas, números, comentarios, operadores y claves JSON/YAML.
  Comentarios anidados Kotlin, cadenas raw Kotlin/Java/Go; Markdown resalta encabezados,
  citas y bloques cercados, conservando tipo y longitud del delimitador de apertura.
- [x] `IncrementalHighlighter`: conserva prefijo común; retokeniza desde la primera
  línea modificada y reutiliza el sufijo cuando coincide el estado de entrada.
  Inserción/eliminación de líneas ajusta la correspondencia del sufijo. Los snapshots
  son independientes; cancelar un cálculo no modifica el último cache publicado.
- [x] `StructaEditorView` analiza snapshots en `Dispatchers.Default`; cancela/invalida
  trabajos al editar, cambiar engine/lenguaje o desmontar la View. Una generación y
  la identidad del engine impiden publicar resultados de otro documento. Durante el
  cálculo se muestra texto sin spans antiguos. `EditorViewState` conserva el cache
  por pestaña en memoria; no se persiste ni participa en historial/dirty.
- [x] `TextRenderer` aplica colores por runs sobre el layout monoespaciado existente,
  con clipping horizontal, columnas de tab y gutter fijo. Paletas clara/oscura sin
  cambiar fuente, peso ni geometría. Cursores, selección y composición se conservan.
- [x] `domain/editor/decoration/DecorationSet`: índice independiente del buffer para
  rangos superpuestos, con tipos de sintaxis, error, warning, coincidencia de búsqueda,
  ocurrencia seleccionada, Git, brackets y breakpoints. La búsqueda alimenta fondos
  visibles; el renderer admite subrayados de diagnóstico y fondo de bracket. Git,
  breakpoints y productores LSP quedan reservados. La sintaxis usa spans por línea.

**Alcance y límites:** resaltado léxico inicial, sin análisis semántico ni validación
de gramáticas completas. Interpolaciones dentro de cadenas, escapes Unicode previos
al lexer Java, escalares de bloque/estructuras complejas YAML y Markdown inline o
lenguajes embebidos en fences no tienen análisis específico. Los marcadores de error
no se generan automáticamente: la capa está preparada para futuros productores.

El límite de resaltado es **1 048 576 unidades UTF-16**, independiente del límite de
edición por tamaño del archivo; al superarlo se usa texto plano y la UI lo indica.
La captura del texto se realiza en el hilo propietario del buffer. La comparación y
división del snapshot en líneas aún recorren el documento en el worker: lo incremental
es la tokenización y reutilización de resultados, no un índice de deltas del buffer.
Los caches solo viven en memoria. Los archivos desconocidos no se tokenizan.

**Verificación:** **137 JVM y 28 instrumentadas**, cero fallos/errores/omisiones;
APK de app y pruebas compilados. Nuevas: 8 `SyntaxTest` y 3 `SyntaxIntegrationTest`.
Cubren rangos UTF-16, estados multilínea, convergencia, reuso ante inserción/eliminación,
250 ediciones aleatorias comparadas con análisis completo, cancelación/límites,
decoraciones superpuestas, edición/Undo/cambio de documento, un worker antiguo lento
y colores reales del Canvas después de tabs, fondos de búsqueda y subrayados.

Suite completa en Pixel_6_Pro Android 12; XML JVM `2026-10-04T16:26:13Z`–`16:26:14Z`,
Android `2026-10-04T16:27:00`. **BUILD SUCCESSFUL en 50 s**:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

`git diff --check` sin errores. Siguiente fase: **12 — IDE Features**.

Implementar:

```text
language registry
tokenizer
syntax spans
incremental highlighting
decorations
```

---

## Fase 12 — IDE Features

Implementar posteriormente:

```text
LSP
Git
terminal
split editor
multi-cursor
minimap
code folding
Shizuku
OneDrive
```

---

# 59. Primer milestone obligatorio

**Estado: implementado y verificado en la fase 4.** El ejemplo siguiente se prueba
en `EditorEngineTest.firstMilestoneRunsEntirelyThroughCommands`; las demás pruebas
de integración cubren selección, historial e índice de líneas.

El primer milestone debe funcionar completamente sin UI.

Debe ser posible ejecutar algo equivalente a:

```kotlin
val buffer = PieceTableBuffer(
    "fun main() {\n}"
)

val editor = EditorEngine(
    EditorDocument(buffer)
)

editor.execute(
    InsertTextCommand("hello")
)

editor.execute(
    UndoCommand
)

editor.execute(
    RedoCommand
)
```

Y verificar:

```text
buffer correcto
cursor correcto
selection correcta
undo correcto
redo correcto
line index correcto
```

---

# 60. Segundo milestone obligatorio

**Estado: implementado y verificado al cerrar la fase 7 (2026-10-03).**
`StructaEditorViewTest` cubre Canvas, gutter, cursor, selección y scroll;
`EditorInputIntegrationTest` cubre toque, escritura, Backspace, Enter, Undo/Redo
y el puente IME sobre la View adjunta. Resultado global: 99 JVM y 16 instrumentadas
aprobadas. Véanse el alcance y los límites de validación de teclados en la fase 7.

Crear `StructaEditorView`.

Debe mostrar:

```text
line numbers
texto
cursor
selection
```

Y permitir:

```text
scroll
tap
typing
backspace
enter
undo
redo
```

Sin syntax highlighting todavía.

---

# 61. Tercer milestone obligatorio

Integrar archivos reales.

Flujo mínimo:

```text
Abrir carpeta
↓
Navegar archivos
↓
Abrir archivo
↓
Editar
↓
Undo / Redo
↓
Guardar
↓
Cerrar
↓
Volver a abrir
```

---

# 62. Cuarto milestone obligatorio

Añadir experiencia de editor real:

```text
tabs
dirty indicator
find
replace
keyboard shortcuts
session restore
quick open
command palette
```

---

# 63. Reglas de implementación

Mantener siempre estas reglas:

1. `domain.editor` no depende de Android.
2. El renderer nunca modifica directamente el buffer.
3. Las modificaciones pasan por Editor Commands.
4. FileSystem no conoce EditorDocument.
5. EditorDocument no conoce FileSystem.
6. UI no contiene lógica del Piece Table.
7. No almacenar snapshots completos para Undo.
8. No renderizar todas las líneas del documento.
9. No cargar todo el árbol de archivos de forma recursiva.
10. No implementar LSP antes de estabilizar el editor básico.
11. No implementar word wrap antes del renderer básico.
12. No introducir múltiples módulos Gradle hasta que el código realmente lo justifique.
13. Utilizar packages para separación inicial.
14. Mantener componentes testeables independientemente.

---

# 64. Prioridad inmediata

La próxima implementación debe centrarse únicamente en:

```text
domain/editor/buffer/
```

Crear:

```text
TextBuffer.kt
PieceTableBuffer.kt
Piece.kt
BufferSource.kt
LineIndex.kt
```

Después crear:

```text
domain/editor/cursor/
```

con:

```text
TextOffset.kt
TextRange.kt
LineColumn.kt
Cursor.kt
Selection.kt
```

Después:

```text
domain/editor/history/
```

con:

```text
EditOperation.kt
EditTransaction.kt
EditHistory.kt
```

No comenzar todavía:

```text
LSP
Git
syntax highlighting
Shizuku
OneDrive
terminal
minimap
code folding
```

hasta completar el núcleo.

---

# 65. Resultado esperado

Al finalizar estas fases, Structa deberá poseer un motor de edición propio compuesto por:

```text
Piece Table
Line Index
Cursor Engine
Selection Engine
Undo / Redo
Command System
Custom Renderer
Android IME Integration
FileSystem abstraction
Workspace system
Document manager
Search
Syntax engine
```

Sobre esta base podrán añadirse capacidades de IDE sin reemplazar el motor principal.

La prioridad principal del proyecto debe ser:

```text
correctitud
↓
arquitectura
↓
rendimiento
↓
features avanzadas
```

No sacrificar el núcleo por intentar implementar rápidamente características visuales.
