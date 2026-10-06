# Contexto de trabajo — Structa

Actualización 2026-10-05: «Formatear» añade YAML conservador (sangría de mapas y
secuencias de bloque inequívocos, sin reserializar; deja intactos escalares de
bloque y casos ambiguos). Se habilita para JSON, YAML y XML según el lenguaje
efectivo. Kotlin, Java y JavaScript dejan el selector, el registro de extensiones
y las ramas léxicas de resaltado: sus archivos se abren como texto plano, incluido
«Abrir con» de JavaScript. Go, JSON, YAML, Markdown y XML conservan resaltado.
Pruebas JVM de preservación, idempotencia y Undo adaptadas; pruebas instrumentadas
de sintaxis y selector actualizadas.

Actualización 2026-10-05: «Formatear» admite XML además de JSON. El formateador
XML usa el lenguaje efectivo de cada pestaña aunque XML mantenga `FileMode.TEXT`;
sangra la estructura con dos espacios y conserva texto mixto, atributos, comentarios,
CDATA y `xml:space="preserve"`. Ante DTD o estructura no reconocida deja el texto
intacto. La operación conserva Undo/Redo y las protecciones de revisión existentes.
Pruebas JVM específicas de formato XML y del ViewModel añadidas.

Actualización 2026-10-05: el selector «Lenguaje» ahora ofrece todos los lenguajes
con resaltado: Texto, Kotlin, Java, Go, JSON, YAML, Markdown, XML y JavaScript.
XML resalta etiquetas/atributos/entidades/comentarios/CDATA y JS añade plantillas
backtick y comentarios de bloque con estados entre líneas; detección por extensiones
xml/svg/xsd/xsl/xslt y js/mjs/cjs. La selección por pestaña utiliza `setLanguage`
y conserva `FileMode.TEXT` para XML/JS y demás código; solo JSON tiene formateo
automático. Sin gramática completa de interpolación JS, regex o código incrustado.

Actualización 2026-10-05: Android muestra Structa en «Abrir con» para URI
`content://` de texto/JSON/XML/YAML/JavaScript mediante ACTION_VIEW/ACTION_EDIT.
`MainActivity` recibe intents iniciales y nuevos, `StructaApp` consulta metadata
`OpenableColumns`, toma el permiso persistible si existe y abre/reutiliza pestaña
tras restauración. `SafFileSystem.stat` también consulta proveedores `content://`
no SAF. Para permisos solo de lectura, Guardar como exporta; guardar el original
requiere permiso de escritura. Android test con proveedor externo y suite completa
verificados en Pixel_6_Pro AVD; conservar cambios previos sin commit.

Actualización 2026-10-05: «Lenguaje» controla el resaltado además del formato.
Para archivos de más de 1 048 576 unidades UTF-16, `StructaEditorView` utiliza
`ViewportHighlighter` con ventanas visibles, checkpoints de estado cada 128 líneas
y un registro acotado de líneas modificadas en `PieceTableBuffer`; ya no indica
resaltado desactivado por tamaño. Los cierres históricos que afirman texto plano
por superar 1 MB corresponden al comportamiento previo. La primera visita lejana
puede requerir reconstruir el estado desde el inicio; edición IME y `syncInput`
conservan otros recorridos O(n).

Actualizado: edición de archivos grandes, validación UTC 2026-10-04.

## Objetivo y estado

Implementar el editor Android propio según `SPECS.md`, fase por fase. **Fase 11 (Syntax) completada; fase 12 pospuesta por el usuario.** La petición actual elimina el bloqueo heredado de edición por encima de 1 MB y su aviso multilínea.

Las fases 1–11 están implementadas y documentadas. Resultado actual: **138 JVM y 29 instrumentadas aprobadas**, APK de aplicación y pruebas compilados. Fase 11 cerró con 137/28; fase 10 con 129/25; fase 9 con 112/22; fase 8 con 107/19; fase 7 con 99/16. Sigue pendiente la matriz manual de teclados comerciales.

## Mejora posterior — archivos grandes

### Preferencias de apariencia y editor

- Configuración ordenada como Tema, Editor y Acceso a archivos. Tema ofrece Oscuro (predeterminado) y Claro; cambia el esquema Material y los colores del editor en la misma sesión.
- Editor permite elegir Monoespaciada o Sans monoespaciada y un tamaño de 10–32 sp. Se mantienen familias monoespaciadas para la geometría de columnas del Canvas; el renderer recalcula métricas/viewport al cambiar estilo sin editar el texto.
- `AndroidSettingsRepository` persiste tema, fuente y tamaño en SharedPreferences; `SettingsViewModel` los lee al crearse. El selector SAF/Shizuku sigue siendo informativo y no reemplaza el proveedor de archivos.
- JVM completa, APK de app/pruebas compilados. Instrumentadas focalizadas: persistencia de apariencia (1) y vista Canvas (8), sin fallos. `:app:installDebug` aprobado y UIAutomator verifica orden y controles visibles en Configuración. Se restauró Oscuro tras comprobar la selección de Claro.

### Barra inferior oculta en Editor

- `StructaApp` muestra `AppNavigation` solo fuera de `Screen.EDITOR`, independientemente del teclado. La flecha superior vuelve a Explorador y recupera la navegación inferior.
- Compilación e instalación aprobadas (`:app:installDebug`, 4 s). UIAutomator verifica editor con documento de 52 132 líneas en `[0,308][1440,2952]`, 280 px adicionales respecto a la barra visible; al volver a Explorador reaparecen las cuatro entradas. No se añadieron pruebas para este cambio de visibilidad.

### Corrección de paneles Buscar/Reemplazar invisibles

- `StructaEditorView.onDraw` acota el Canvas a los límites locales y restaura su estado en `finally`. `drawColor` llenaba el clip compartido con Compose y podía tapar los controles superiores aunque estos reservaran espacio.
- Nueva regresión gráfica `drawingEditorPreservesSurroundingControlsOnSharedCanvas`: Canvas trasladado, verifica todos los píxeles exteriores y restauración del clip, con/sin documento. Falló antes del arreglo por sobrescribir el píxel exterior 0,0; pasa después.
- Validación focalizada: 7 pruebas de StructaEditorViewTest aprobadas (XML 2026-10-04T23:48:54) y 3 de ProductivityIntegrationTest aprobadas en ejecución separada sin configuration cache (XML 23:49:44). La invocación con ambas clases solo produjo 7 resultados, por eso se verificó productividad por separado. No se repitió la suite completa. Instalación final aprobada en Pixel_6_Pro (3 s).

### Ajuste posterior de espacio del editor

- Actualización de menús: barra `flecha Atrás | información | Archivo | Editar`, con icono Material AutoMirrored y botones de texto enmarcados. Archivo ofrece Abrir, Guardar, Guardar Como... (antes Guardar copia) y Cerrar. Editar ofrece Buscar, Reemplazar, Ir a línea, Deshacer, Rehacer, Formatear y una única entrada Formato con selector Texto/JSON/YAML y modo actual marcado.
- Eliminada la entrada duplicada Quick Open; su selector se titula Abrir y conserva Ctrl+P. Ctrl+Shift+P abre Editar. La paleta filtrable anterior ha sido sustituida por el menú desplegable.
- Validación de esta reorganización: 6 instrumentadas de documentos/productividad aprobadas, BUILD SUCCESSFUL en 24 s; cubren guardado/cierre, búsqueda/reemplazo/historial, apertura/reuso de pestañas, atajos y selección de formato. Las notas siguientes describen el ajuste de espacio anterior.

- Eliminados DropdownMenu de formato, fila de herramientas, metadata separada y fila inferior de guardado. Las acciones y Guardar copia están en Comandos; los modos aparecen como `Formato: Texto/JSON/YAML` en la paleta.
- Una sola pestaña no muestra fila adicional; dirty y metadata se muestran en TopAppBar. Con varios documentos se mantiene la fila de pestañas. Búsqueda ocupa espacio únicamente al abrirla.
- Validadas las 6 instrumentadas de EditorDocumentsIntegrationTest y ProductivityIntegrationTest con el acceso nuevo mediante Comandos (BUILD SUCCESSFUL, 19 s). Instalación final `:app:installDebug` aprobada (3 s).
- Reabierto `f66007bb.json` desde Documents. UIAutomator confirma editor `[0,308][1440,2672]`, pegado a la barra superior y a la navegación inferior: altura 2364 px frente a 1664 px anteriores (+700 px).

- `EditorScreen`: retirados aviso de documento grande y restricciones por tamaño sobre edición, cursor, Guardar, Guardar copia, Undo/Redo, formato y reemplazo. `EditorViewModel.canEdit` ya no consulta el tamaño; reemplazar no rechaza el tamaño total resultante por superar 1 MB.
- Siguen independientes el máximo de apertura de 25 MB, el límite de sintaxis de 1 048 576 unidades UTF-16 (con indicador en UI) y el presupuesto de expansión de reemplazos de 1 048 576 unidades UTF-16 por operación.
- Una JVM nueva verifica edición, historial, reemplazo, copia y guardado original con más de 2 MB. Se adaptó la prueba de productividad que esperaba bloqueo por metadata de tamaño. Una instrumentada nueva verifica IME, selección, scroll, Undo/Redo y guardado con más de 2 MB. Los writers de estas pruebas son simulados.
- Medición puntual en emulador: 32 ms para la edición IME de la prueba. No es un benchmark de fluidez: `syncInput` y `derive` todavía reconstruyen/recorren el texto completo, O(n).
- JVM XML: 138 aprobadas, `2026-10-04T16:47:55Z`–`16:47:57Z`. Instrumentadas finales: 29 aprobadas, XML `2026-10-04T16:52:55`, Pixel_6_Pro Android 12, `:app:connectedDebugAndroidTest` BUILD SUCCESSFUL en 1m 24s.
- Hubo un fallo transitorio de portapapeles y una aserción temporal de sintaxis. La segunda se corrigió para admitir un resultado nuevo ya calculado, además de null; sigue rechazando spans obsoletos. Suite completa posterior aprobada.
- Instalación completada mediante `:app:installDebug` (BUILD SUCCESSFUL, 16 s) en Pixel_6_Pro. El intento inicial por ruta directa falló por APK ausente en esa ruta. Aplicación abierta y UIAutomator confirma `f66007bb.json`, 2 MB, 52 132 líneas, sin aviso de solo lectura y con Guardar/copia/reemplazo/formato habilitados. Se conserva el indicador de límite de resaltado.
- Los apartados siguientes son cierres históricos: sus referencias al límite editable de 1 MB ya no describen el estado actual.

## Fase 11 — cierre actual

- `domain/editor/syntax/Syntax.kt`: contratos `LanguageTokenizer`, `TokenizerState`, `TokenizationResult`, `SyntaxSpan`, `SyntaxStyle`, `Language` y `LanguageRegistry`. Kotlin puro, spans locales por línea `[start,end)` UTF-16; el buffer no recibe metadata visual.
- Registro: Kotlin kt/kts, Java, Go, JSON json/jsonl, YAML yaml/yml y Markdown md/markdown, case-insensitive. Desconocidos → plain text. El nombre se detecta fuera del renderer. La barra de documento informa lenguaje y límite de resaltado.
- Lexer determinista para código/JSON/YAML: keywords, números, cadenas, comentarios, operadores y claves. Estado para comentarios de bloque (anidados Kotlin), raw strings Kotlin/Java/Go y comillas YAML. Markdown: títulos/citas/fences; conserva carácter y longitud del fence. Cancelación comprobada dentro de tokens largos.
- `IncrementalHighlighter.kt`: snapshots con líneas, estado entrante y resultado saliente. Compara prefijo/sufijo; comienza en primera línea cambiada y reutiliza sufijo alineado cuando converge estado. Inserciones y eliminaciones de líneas no invalidan todo el sufijo. Exposición `tokenizedLines` permite verificar reuso real.
- Límite: **1 048 576 unidades UTF-16**; arriba, texto plano indicado en UI. No confundir con tamaño de archivo/límite editable. Snapshot capturado desde el hilo propietario del buffer; división de líneas/comparación/tokenización en Default. La captura y comparación aún son O(n); no se añadió un índice de deltas del buffer.
- `StructaEditorView` posee scope durante attach, cancela al detach, edición y rebind; publicación comprueba generación y engine. Nunca entrega el buffer mutable a workers. Mientras calcula, no renderiza spans obsoletos. `EditorViewState.syntax` conserva cache por tab en memoria; al cambiar de documento se recupera si corresponde al texto/lenguaje actuales.
- `TextRenderer`, `EditorRenderer`, `EditorStyle`: colores por runs con clipping horizontal y columnas de tabs, paletas clara/oscura. Fuente/geometría/selección/IME no cambian. `StructaEditor` conecta nombre de archivo y decoraciones de resultados de búsqueda.
- `domain/editor/decoration/Decoration.kt`: rangos independientes, índice por inicio/máximo fin para intersecciones y solapamientos. Tipos SYNTAX, ERROR, WARNING, SEARCH_MATCH, SELECTED_OCCURRENCE, GIT_CHANGE, BRACKET_MATCH, BREAKPOINT. Búsqueda produce fondos reales; renderer soporta subrayados error/warning y fondo de bracket. Git/breakpoints son tipos reservados; sintaxis se dibuja mediante sus spans de línea. No hay generador de diagnósticos ni LSP todavía.
- Alcance léxico inicial: no análisis semántico ni gramáticas completas. Sin análisis específico de interpolaciones, escapes Unicode preléxicos Java, escalares de bloque YAML, Markdown inline o lenguajes dentro de fences. Estas limitaciones están explícitas en SPECS.

### Validación de fase 11

- 8 JVM nuevas `domain/editor/SyntaxTest.kt`: detección/fallback, UTF-16 y estados, raw strings/JSON/YAML/fences, reuso exacto de sufijo, propagación/convergencia y Undo/Redo por snapshots, 250 ediciones aleatorias contra tokenización completa, cancelación/límite y rangos de decoraciones.
- 3 instrumentadas nuevas `ui/editor/SyntaxIntegrationTest.kt`: View adjunta con edición/Undo/cambio de documento/cache, worker simulado lento que no puede publicar tras rebind, colores Canvas tras tab y fondos/subrayados sin alterar texto/historial.
- **137 JVM y 28 instrumentadas, cero fallos/errores/omisiones**, suite completa y ambos APK compilados. JVM XML `2026-10-04T16:26:13Z`–`16:26:14Z`; Android XML `2026-10-04T16:27:00`, Pixel_6_Pro Android 12.
- Comando final **BUILD SUCCESSFUL en 50 s**:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

- `git diff --check` sin errores; se preservó staging previo. Archivos nuevos de fase 11 sin stage. No se crearon commits. Emulador ya conectado, no iniciado ni cerrado en esta fase.
- Próxima fase: **12 — IDE Features**, cuando se solicite; incluye varias integraciones grandes, revisar SPECS antes de decidir alcance/orden.

## Fase 10 — cierre histórico

- `domain/editor/search/DocumentSearch.kt`: snapshots inmutables, literal/regex, case sensitive y whole word Unicode. Rangos UTF-16, coincidencias no solapadas, admite espacios y coincidencias vacías. Reemplazo regex con `$0`, `$1`… y `${name}`, escapes con backslash; literal conserva el reemplazo literalmente.
- Límites: consulta 4096 caracteres, 10 000 coincidencias, presupuesto cooperativo 500 ms; expansión de reemplazos hasta 1 MB. Resultados truncados se indican y no se reemplazan. El presupuesto NO garantiza interrupción dura del regex nativo Android; la prueba patológica es JVM. El trabajo de búsqueda se ejecuta en Default, no en UI.
- `ReplaceMatchesCommand`: valida rangos, orden y tamaño antes de mutar; edita de atrás hacia delante en una transacción. `EditingSession.length` permite validar antes de abrirla. Un Undo restaura texto/selección y revisión. No guarda snapshots completos en historial.
- `EditorViewModel`: búsqueda asíncrona por documento con debounce 120 ms, cancelación al editar/cambiar consulta/opciones/cerrar. `findNext(backwards)` selecciona y recorre circularmente. `replace(all)` confirma composición, prepara plan en worker y valida revisión/generación/documento/consulta/opciones/reemplazo/selección antes de aplicar. Respeta solo lectura y tamaño máximo resultante.
- Reemplazar actual sin coincidencia seleccionada selecciona la siguiente; otra invocación la reemplaza. Search options y texto de reemplazo se conservan en memoria por pestaña, no en metadata persistente.
- `domain/workspace/QuickOpen.kt`: `IndexedFile` y ranking fuzzy por subsecuencia, con preferencia por nombre y coincidencias contiguas; también busca ruta relativa.
- `QuickOpenViewModel`: raíz sincronizada con Browser; recorrido de metadata en IO al abrir, filtrado en Default, publicaciones progresivas, deduplicación/ciclos y cancelación/generación. No lee contenido. Límites 20 000 archivos / 5 000 carpetas / 100 resultados. Informa errores parciales; reabrir vuelve a indexar. Seleccionar usa `EditorViewModel.open`, reutiliza pestañas existentes sin perder dirty.
- `EditorAction` en `KeyBindingHandler.kt`: registro compartido de acciones de aplicación, títulos y shortcuts. `applicationActionFor` se comparte con el preview de teclas Compose. Paleta y toolbar despachan las mismas acciones; los cambios de documento siguen usando comandos del engine.
- `EditorScreen`: búsqueda/reemplazo con opciones, siguiente/anterior, diálogo de línea validado, paleta filtrable, acceso Quick Open incluso sin pestañas. Find y línea disponibles en read-only. Ctrl/Meta F/H/G/P/Shift+P/S, F3/Shift+F3; Enter/Shift+Enter en campo de búsqueda. Paleta ofrece solo acciones actualmente habilitadas, no placeholders de features futuras.
- `ProductivityDialogs.kt`: picker compartido con filtro, flechas, Enter, Escape y click. El foco inicial se solicita dentro del diálogo tras un frame; se corrigió un fallo real de Escape al abrir la paleta sin escribir primero.
- `AppContainer` y `StructaApp` conectan Quick Open, workspace y callbacks nuevos.
- Save All, word wrap, cambio de encoding/lenguaje y Workspace Search siguen fuera de esta implementación; los ejemplos de paleta de SPECS no representan comandos implementados.

### Validación de fase 10

- Nuevas JVM: `DocumentSearchTest` (7), `ProductivityTest` (6), `QuickOpenTest` (4). Total **129**. Nuevas instrumentadas: `ProductivityIntegrationTest` (3); total **25**. Cero fallos/errores/omisiones en los informes finales.
- Instrumentadas completas: Pixel_6_Pro Android 12, XML `2026-10-04T16:08:41`, **25/25**. En esa invocación conjunta hubo un fallo JVM de lifecycle del fixture Quick Open: el proveedor simulado no cancelable terminaba después de restablecer Main.
- Corregido `QuickOpenTest`: espera los jobs anteriores y cancela/espera el scope de los ViewModels antes de `Dispatchers.resetMain()`. Esto también asegura que la aserción de resultado obsoleto se realiza tras la respuesta antigua. No se cambió producción tras las instrumentadas aprobadas.
- JVM final: **129/129**, timestamps `2026-10-04T16:09:25Z`–`16:09:26Z`. APK app/tests compilados. Comando final **BUILD SUCCESSFUL en 3 s**:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

- `:app:connectedDebugAndroidTest` se ejecutó en la invocación conjunta anterior, con las 25 instrumentadas aprobadas; no se repitieron tras cambiar únicamente el fixture JVM.
- Pruebas anteriores adaptadas a búsqueda asíncrona; `EditorInputIntegrationTest` añade comprobaciones F3/Shift+F3. Se añadió opt-in a ExperimentalTestApi para inyección de teclas Compose.
- `git diff --check` sin errores. Hay staging del usuario de fases anteriores y parte de fase 10: preservarlo. No se crearon commits ni se modificó el índice.
- Próxima fase: **11 — Syntax**, cuando se solicite.

## Fase 9 — cierre histórico

- `domain/filesystem/FileSystem.kt`: interfaz que extiende puertos actuales de lectura/escritura/listado y añade stat/create/rename/delete. `FileRef` asocia id opaco y proveedor; las firmas conservan `DocumentId`/`FileEntry`, texto UTF-8 y límites previos.
- `data/filesystem/SafFileSystem.kt`: delega lectura/escritura/listado existentes; metadata y mutaciones SAF mediante DocumentFile/DocumentsContract en IO. Rename devuelve el nuevo id. `AppContainer` usa este filesystem para todos los casos de uso.
- `domain/workspace/Workspace.kt`: Workspace con id, raíz y documentos abiertos; `EditorSession`, `SessionTab`, `SessionRepository` para metadata exclusivamente.
- `BrowserViewModel` expone workspace, expansión y children lazy, creación, rename/delete y refresh. Generación invalida respuestas de expansiones antiguas al navegar. `BrowserScreen` añade acciones y diálogos. No se precarga el árbol completo.
- Para renombrar/eliminar archivos abiertos se requiere cerrar la pestaña; para carpetas se exige cerrar todas, porque las URI son opacas. Se reutiliza el flujo de cierre de fase 8 para gestionar dirty. No hay remapeo de engines vivos tras rename.
- Raíz actual persistida mediante `SettingsRepository.lastFolder`. `StructaApp` sincroniza ids abiertos con workspace; cambiar raíz conserva pestañas. No hay múltiples sesiones independientes por raíz.
- `AndroidSessionRepository`: SharedPreferences `structa_session`, JSON solo de ids, orden, selección/cursor, scroll y tab activo. Metadata corrupta -> sesión vacía.
- `EditorViewModel` restaura al crearse: stat y lectura actual, registro independiente por tab, selección acotada, scroll, tab activo o primer superviviente. Omite archivos ausentes/inaccesibles con mensaje. No acepta nuevas aperturas durante la restauración.
- Checkpoints tras cambios con debounce de 300 ms; `EditorViewState.onScrollChanged` cubre scroll sin edición. `checkpointSession` al ON_STOP y al desmontar la composición. Evita sobrescribir metadata mientras restaura.
- Restauración relee disco: NO recupera borradores sin guardar, historial Undo/Redo, composición, búsqueda ni modo anterior. Crash Recovery sigue pendiente fuera de esta fase.
- Picker intenta conservar permiso de lectura si falla persistir lectura/escritura conjuntamente.

### Validación de fase 9

- `WorkspaceTest`: 5 JVM nuevas — recreación de ViewModel/metadata, contenido de disco, selección truncada, orden/activa/scroll, archivo ausente, cierre último tab, expansión lazy, operaciones/protección de documentos abiertos.
- `SessionPersistenceTest`: 2 instrumentadas — repositorio nuevo ve metadata guardada, vaciado y metadata corrupta.
- `SafFileSystemTest`: 1 instrumentada con DocumentsProvider real de pruebas — creación anidada, truncado al escribir, listar, rename con nueva URI, delete. Provider y receptor de grant declarados solo en `src/androidTest/AndroidManifest.xml`.
- Fixtures `TestDocumentsProvider.java` y `GrantTestDocumentsReceiver.java` usan Java: el proceso independiente del APK de pruebas no incluye Kotlin de la app. Primeros intentos fallaron por permiso y luego ausencia de `kotlin.jvm.internal.Intrinsics`; se resolvió con grant desde el UID del proveedor y fixtures Java. Prueba aislada y suite completa posteriores aprobadas.
- **112 JVM, 22 instrumentadas; cero fallos/errores/omitidas.** XML JVM `2026-10-04T04:06:19Z`; Android `2026-10-04T04:06:45`, Pixel_6_Pro Android 12.
- Comando final (BUILD SUCCESSFUL, 34 s):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

- `git diff --check` sin errores (avisos normales LF→CRLF). Se preservó staging previo de `Context.md`; no se crearon commits. Emulador ya conectado; no se inició ni se cerró.
- Validación SAF usa proveedor de prueba y grants reales, no certifica proveedores comerciales ni selector visual del sistema.
- Próxima fase: **10 — Productivity**, cuando se solicite.

## Fase 8 — cierre histórico

Las notas siguientes describen el estado al cerrar fase 8; la persistencia de metadata prevista entonces para fase 9 ya está implementada arriba.

- `EditorDocument` expone `revision`, `savedRevision`, `dirty` y `markSaved(revision)`. `EditHistory` asigna revisiones únicas y las restaura al hacer Undo/Redo; `EditTransaction` conserva revisión anterior/posterior. La revisión de una rama nueva no reutiliza identificadores abandonados.
- `EditorViewModel` administra documentos abiertos en un mapa por `DocumentId`, con engine/sesión independientes, y publica `EditorTab` (id, título, dirty, activo). Reabrir un id existente activa la pestaña sin releer.
- Conserva por documento texto, historial, cursor/selección, modo, búsqueda y `EditorViewState`. Esta última clase vive en `ui/editor/model/`, separada del documento, y conserva scroll y snapshot lógico; el engine sigue siendo autoritativo.
- `StructaEditorView.bind` recibe `savedViewState`; restaura ambos ejes al cambiar documento/recrear superficie. El revelado por resize se limita a reducción real de altura (`h < oldh`) para no perder el scroll al crear una View.
- Pantalla con pestañas, indicador `●`, cierre y diálogo Guardar y cerrar / Descartar / Cancelar. `StructaApp` conecta todos los callbacks.
- Guardar / Ctrl+S escriben al id del archivo abierto usando el puerto existente detrás de `SaveDocumentCopy`; Guardar copia exporta a otro destino y no marca limpio el original.
- `SaveSnapshot` captura documento, contenido y revisión. Guardar una revisión antigua no limpia ediciones posteriores ni otro documento. Guardar y cerrar deja abierto el documento si se editó durante la escritura. Errores preservan documento/dirty; copiar sobre otra pestaña abierta se rechaza.
- Pestañas en memoria durante la vida del ViewModel. Recuperación tras muerte de proceso y persistencia de metadata de sesión pertenecen a fase 9. No hay split editor ni recuperación de borradores.

### Pruebas y validación de fase 8

- Nuevas JVM: `domain/editor/DocumentRevisionTest.kt` (2) y `ui/EditorDocumentsTest.kt` (6).
- Nuevas instrumentadas: `ui/editor/EditorDocumentsIntegrationTest.kt` (2) y un caso de restauración de viewport en `StructaEditorViewTest.kt` (ahora 6).
- Totales confirmados en XML: **107 JVM, cero fallos/errores/omitidas**, timestamps alrededor de `2026-10-03T22:56:30Z`; **19 instrumentadas, cero fallos/errores/omitidas**, timestamp `2026-10-03T22:57:21`.
- Instrumentadas: 11 input + 6 renderer + 2 documentos, en Pixel_6_Pro Android 12. Las pruebas nuevas de documentos usan almacenamiento en memoria mediante puertos; no afirmar validación de todos los proveedores SAF.
- Comandos finales:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

- Resultados: BUILD SUCCESSFUL (3 s JVM/APKs; 30 s instrumentadas). Fallo inicial de las nuevas JVM corregido esperando el estado de apertura mediante `state.first`, porque OpenDocument cambia a Dispatchers.Default; `runCurrent` no esperaba ese trabajo.
- Emulador ya conectado al validar fase 8; no se inició ni se cerró en esta fase.
- `SPECS.md` actualizado con alcance, resultados y cierre. Próxima fase: 9, cuando se solicite.
- Persisten cambios sin commit de fases 7 y 8. `Context.md` tenía índice existente (`AM`); no alterar staging ajeno. No se han creado commits.

Las secciones siguientes conservan los detalles históricos de fase 7; sus recuentos y comandos corresponden a ese cierre, no a la validación más reciente.

## Decisiones y contratos

- Conversación en español. Editor con Canvas propio y UI Compose; sin motores externos, WebView ni `BasicTextField`/`TextField` como motor.
- Núcleo Kotlin puro en `domain/editor`. Offsets UTF-16, rangos `[start, end)`, líneas desde cero, documento vacío con una línea. LF separa líneas; CR se conserva. Grafemas y bidi avanzados pendientes.
- Documento propietario del buffer e historial; un `EditorEngine` por documento. Mutaciones mediante comandos; historial de operaciones y estados de selección, sin snapshots completos.
- Transacciones explícitas no anidadas; confirmar antes de Undo/Redo.
- Paquetes actuales: `domain.document.DocumentId`, `domain.filesystem.FileMode`, `data/filesystem/`, `data/settings/`; no restaurar `domain.model`.
- Edición interactiva sin bloqueo heredado de 1 MB; apertura limitada a 25 MB y resaltado con límite independiente.
- `TextFieldValue` permanece como snapshot de presentación/compatibilidad, no como motor. `syncInput` todavía reconstruye texto y derivados al cambiar contenido.
- Replace, Quick Open y Command Palette ya están implementados en fase 10; véase el cierre actual arriba.
- No se encontraron `AGENTS.md`. No crear commits salvo petición explícita. Al comenzar esta revisión, `git status --short` estaba vacío; las modificaciones descritas abajo son nuevas.

## Implementación completada

### Fases 1–6

- Piece Table, índice incremental, posiciones, selección, edición, historial, documentos y comandos.
- Canvas, viewport con dos líneas de margen, layouts cacheados, gutter fijo, cursor/selección, scroll bidireccional, fling/rueda, puente Compose `AndroidView`.
- `EditorLine`: checkpoints cada 256 unidades UTF-16 y expansión parcial horizontal de tabs. Ancho horizontal descubierto al visitar líneas.
- Fase 6 cerró con 84 pruebas JVM y 5 instrumentadas aprobadas y APK compilado.
- Bloqueos históricos de tests resueltos: `ContentFormatDetector`/`detect` abiertos para dobles; prueba de formateo TEXT/YAML usa implementación real; prueba concurrente usa `CompletableDeferred`/`NonCancellable`.

### Fase 7 ya integrada

- `EditorInputSession`: sesión compartida por engine, listeners, batches, composición provisional reemplazable agrupada en una transacción, commit/finish, selección y borrado circundante.
- Borrado circundante protege unión de selección/composición. Borrado por codepoints preserva surrogates y rechaza huérfanos sin edición parcial.
- `EditorInputConnection : BaseInputConnection(view, false)`, sin backing `Editable`; consultas, batches, composición, clipboard, key events y rechazo de mutaciones desde conexiones cerradas. `getSurroundingText` explícito.
- Teclado físico: caracteres, Enter/Tab, Backspace/Delete, flechas/Home/End, Shift-selección, Ctrl/Meta A/C/X/V/Z/Y, Ctrl+Shift+Z y acciones S/F/H/G/P/Shift+P.
- View: foco e IME, toque posiciona cursor, doble toque/pulsación larga selecciona palabra, arrastre extiende selección, menú contextual, clipboard, parpadeo y revelado del cursor. Composición subrayada.
- `bind(editor, version, inputSession)` conserva conexión al cambiar solo versión; al cambiar engine/sesión cierra conexión anterior y sustituye listeners.
- ViewModel y toolbar comparten sesión/historial IME. Estado con `inputSession`, `contentVersion`, `canUndo`, `canRedo`.
- `EditorScreen` utiliza `StructaEditor`, `imePadding` y enrutamiento de acciones.
- Informes anteriores confirmaban **99 JVM y 13 instrumentadas aprobadas** (8 de input y 5 de renderer). Instrumentadas: 2026-10-03 17:06:47. No confundir esos informes con la validación de los cambios nuevos.

## Cambios nuevos de esta revisión

Se identificó que `onSizeChanged` ajustaba viewport/scroll sin garantizar que el cursor permaneciera visible al reducirse la altura, como al abrir el IME.

Archivos modificados:

1. `app/src/main/java/com/edro08/structa/ui/editor/view/StructaEditorView.kt`
   - Tras `updateViewport()` en `onSizeChanged`, llama a `revealCursor()` si la vista es editable y tiene foco.
2. `app/src/main/java/com/edro08/structa/ui/editor/input/EditorInputConnection.kt`
   - Comentario corregido: las consultas incluyen la selección completa más contexto circundante acotado; no afirmar que todo el resultado es acotado.
3. `app/src/androidTest/java/com/edro08/structa/ui/editor/EditorInputIntegrationTest.kt`
   - Añadidas tres pruebas:
     - `shrinkingFocusedViewportKeepsCaretVisible`: reducción de altura mantiene visible el cursor al final del documento.
     - `readOnlyTransitionCommitsCompositionAndRejectsStaleImeWrites`: pasar a solo lectura confirma composición y rechaza escrituras desde conexión vieja; permite nueva conexión al reactivar edición.
     - `longPressSelectsWordAndContextMenuCopiesIt`: pulsación larga selecciona palabra y menú real «Copiar» escribe clipboard.
   - Añadidos imports Espresso para pulsar el menú contextual.

Estas tres pruebas **compilan y pasan**. Total instrumentado: 16 (11 input + 5 renderer), sin fallos, errores ni omisiones. La primera ejecución falló al buscar «Copiar» en la ventana principal; se corrigió la prueba con `.inRoot(isPlatformPopup())` para localizar el menú flotante real. La suite completa pasó después del ajuste.

## Última validación realizada

Comando ejecutado después de los cambios nuevos:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Resultado: **BUILD SUCCESSFUL in 14s**, 72 tareas, 11 ejecutadas y 61 actualizadas. Se ejecutó `testDebugUnitTest`; se compilaron APK de aplicación y de pruebas. Recuento confirmado en los diez XML JVM: 99, sin fallos, errores ni omisiones; timestamp 2026-10-03T22:26:24Z.

Validación instrumentada final:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:connectedDebugAndroidTest
```

**BUILD SUCCESSFUL in 27s**, 68 tareas; XML `2026-10-03T22:38:57`: 16 pruebas, cero fallos/errores/omitidas.

Se lanzó el AVD mediante:

```powershell
Start-Process -FilePath "C:\Users\Edro0\AppData\Local\Android\Sdk\emulator\emulator.exe" -ArgumentList '-avd Pixel_6_Pro -no-window -no-audio -no-boot-anim -no-snapshot-save'
```

El AVD arrancó como `emulator-5554` y ejecutó las pruebas en Android 12.

## Cierre y continuación

- `SPECS.md` actualizado: cierre técnico de fase 7 y segundo milestone, resultados y límites; corregida la nota histórica de fase 6.
- Comprobado que no hay imports Android/AndroidX en `domain/editor` ni `BasicTextField` en UI. Los `TextField` restantes son búsqueda y diálogo de línea, no motor del editor.
- Cambios de código limitados a los tres archivos descritos arriba; también modificados `SPECS.md` y este contexto. No se crearon commits. Al retomar había `AM Context.md`; se preservó el índice existente.
- Esta sección describe el cierre histórico de fase 7. La fase 8 ya está completada (véase arriba). La matriz manual de teclados sigue pendiente.

No afirmar compatibilidad manual verificada con todos los teclados (por ejemplo, Samsung): no se ha realizado esa matriz. Agrupación temporal automática de tecleo y grafemas/bidi avanzados siguen pendientes. Las pantallas de productividad ya se implementaron en fase 10. Revisar alcance de requisitos antes de declarar cumplimiento total.

Todo actual: revisión, correcciones, verificación automatizada y documentación completadas.

## Archivos y rutas útiles

- Workspace: `D:\Projects\Android\Structa` (Git, Windows PowerShell 5.1).
- SDK: `C:\Users\Edro0\AppData\Local\Android\Sdk`.
- `SPECS.md`: secciones aproximadas antes de editar:
  - Hit Testing: línea 1009; input táctil: 1045; Android IME: 1060; composición: 1091; clipboard: 1109; shortcuts: 1124.
  - Nota fase 6: 1937–1941; fase 7: 1970; segundo milestone: 2105.
- Núcleo: `app/src/main/java/com/edro08/structa/domain/editor/`.
- Input: `app/src/main/java/com/edro08/structa/ui/editor/input/` (`EditorInputSession.kt`, `EditorInputConnection.kt`, `KeyBindingHandler.kt`).
- Vista: `app/src/main/java/com/edro08/structa/ui/editor/view/StructaEditorView.kt`.
- Modelos/render: `ui/editor/model/EditorLine.kt`, `EditorViewport.kt`; `ui/editor/render/EditorRenderer.kt` (bajo la misma raíz de paquete).
- Compose: `ui/editor/component/StructaEditor.kt`.
- Pantalla/ViewModel: `ui/screen/editor/EditorScreen.kt`, `EditorViewModel.kt`.
- `app/StructaApp.kt`: integración de pantalla y `onMessage = editor::showMessage`.
- JVM: `app/src/test/java/com/edro08/structa/ui/editor/input/EditorInputSessionTest.kt` (13), `ui/editor/model/EditorLayoutTest.kt` (12), `ui/PresentationViewModelsTest.kt` (10).
- Instrumentadas: `app/src/androidTest/java/com/edro08/structa/ui/editor/EditorInputIntegrationTest.kt`, `StructaEditorViewTest.kt`.
- Resultados JVM: `app/build/test-results/testDebugUnitTest/TEST-*.xml`.
- Resultados instrumentados finales: `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_6_Pro(AVD) - 12.xml`.
- Informe JVM: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Ya no es necesario el antiguo init script externo de aislamiento de tests; ejecutar la suite completa.

## Notas de ejecución

Usar herramientas específicas para leer/buscar archivos y `apply_patch` para editarlos. PowerShell no soporta `&&`. Configurar `JAVA_HOME` en cada llamada de Gradle. No delegar a subagentes sin petición explícita. Evitar sobrescribir trabajo ajeno; revisar estado Git antes de continuar.
