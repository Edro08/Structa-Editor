# Cómo se construyó el Editor Core

Se implementó por capas en `domain/editor/`, verificando el núcleo sin Android
antes de conectar la UI:

1. **Buffer y posiciones:** `PieceTableBuffer` conserva el texto original y
   añade texto nuevo en un almacén append-only; las piezas reconstruyen el
   documento. `LineIndex` se actualiza con cada edición. Offsets y columnas
   usan UTF-16, rangos `[inicio, fin)`, líneas desde cero; LF separa líneas y
   CR se conserva (también en CRLF).
2. **Edición e historial:** `EditingSession` concentra cursor, selección con
   dirección, inserción, borrado y reemplazo. `EditHistory` guarda operaciones
   inversibles y estados de selección, no copias completas. Las transacciones
   explícitas agrupan operaciones para Undo/Redo.
3. **Documento y comandos:** `EditorDocument` posee buffer, historial y
   revisión guardada (`dirty`); `EditorEngine` ejecuta `EditorCommand` y es
   independiente de la UI y del filesystem.
4. **Integración:** `EditorInputSession`/`EditorInputConnection` traducen IME y
   teclado a operaciones del engine. `StructaEditorView` dibuja únicamente el
   viewport; la búsqueda y el resaltado trabajan con snapshots inmutables
   fuera del hilo UI y descartan resultados obsoletos.

El núcleo se cubrió con pruebas de límites, UTF-16, transacciones y secuencias
aleatorias comparadas con modelos de referencia. El contrato de posiciones
UTF-16 no equivale todavía a navegación completa por grafemas o bidi.
