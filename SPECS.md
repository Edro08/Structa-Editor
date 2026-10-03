# Structa — Plan técnico para editor Android propio

## 1. Objetivo

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

No implementar inmediatamente.

Primero completar edición estable.

Posteriormente crear:

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

Crear posteriormente:

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

Integración gradual: esta fase conecta la superficie de lectura para archivos grandes.
El renderer ya puede representar cursor y selección del engine. La sustitución del
área editable actual con input táctil, teclado e IME corresponde a la fase 7; por ello
el segundo milestone todavía requiere esa fase. Word wrap y layout Unicode avanzado
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
