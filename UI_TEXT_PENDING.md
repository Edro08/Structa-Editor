# Textos pendientes del editor fuera de sus pantallas Compose

Se migraron `EditorScreen` y `ProductivityDialogs`. Este inventario deja localizado el texto del motor/editor propio y sus capas asociadas para una revisión posterior, sin introducir `R.string` en `domain/`.

| Ubicación | Textos visibles o procedencia | Recomendación |
| --- | --- | --- |
| `domain/editor/search/DocumentSearch.kt` | Errores de consulta demasiado larga, regex inválida/compleja, búsqueda agotada, reemplazo demasiado grande y referencias de grupo inválidas (`SearchResult.error: String?`). | Sustituir por un tipo de error (enum/sealed) y localizarlo en UI; conservar límites y cancelación del buscador. |
| `domain/editor/syntax/Syntax.kt` | `Language.title`: `Plain Text`, Kotlin, Java, Go, JSON, YAML, Markdown. | `EditorScreen` ya resuelve por `Language.id` mediante recursos; verificar si otros consumidores muestran `title` antes de eliminar esa propiedad del dominio. |
| `ui/editor/view/StructaEditorView.kt` | `contentDescription` del editor con número de líneas; acciones del menú nativo: `Seleccionar todo`, `Copiar`, `Cortar`, `Pegar`. | Usar recursos con `context.getString(...)`, `getQuantityString(...)` y plural de líneas. `ClipData.newPlainText("Structa", ...)` es etiqueta técnica, no un control visible. |
| `ui/editor/input/KeyBindingHandler.kt` | `EditorAction.title` contiene nombres visibles de acciones, incluida `Command Palette`. `EditorScreen` ya presenta los títulos del menú con recursos, pero el registro aún contiene los textos. | Dejar solo identidad y shortcuts técnicos en el enum; resolver títulos en la UI al reutilizar el registro. |
| `ui/screen/editor/EditorViewModel.kt` | Avisos sobre restauración, apertura, formato, guardado, destino abierto, cierre durante guardado y reemplazos; algunos interpolan detalles de excepción. | Exponer códigos/mensajes tipados y argumentos; resolverlos con `R.string`/`R.plurals` al presentarlos en `StructaApp`. |
| `ui/screen/editor/QuickOpenViewModel.kt` | Aviso sin workspace, cantidad de carpetas fallidas y límites de indexado; `QuickOpenState.message` llega al diálogo. | Exponer estado tipado y usar plurales para carpetas fallidas. |
| `app/StructaApp.kt` | `No se pudo iniciar el guardado: ...` al lanzar el selector del editor; también muestra mensajes del ViewModel y Quick Open. | Usar recurso de error y resolver los estados tipados del editor cuando se migren. |
| `ui/component/FileRow.kt` / `ui/screen/editor/EditorScreen.kt` | `formatBytes` ya resuelve B/KB/MB desde recursos, para fila y cabecera. | No queda texto fijo en este formateador. |

Las cadenas de `require`/`check`, mensajes de índices y transacciones del núcleo (`domain/editor/buffer`, `cursor`, `history`, `editing`) son diagnósticos de programación, no textos directos de controles. Si alguna excepción termina mostrándose al usuario a través de `exception.message`, debe mapearse a un error tipado antes de su presentación.
