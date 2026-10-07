# Historial breve de Structa

La [guía del proyecto](../../AGENTS.md) enlaza las fichas del estado vigente; aquí se
registran hitos y decisiones, incluidos prototipos posteriormente retirados.

## 2026-10-03 — Base del editor

- Fases 1–5: Piece Table, índice de líneas, cursor/selección, edición, comandos,
  Undo/Redo y pruebas del núcleo Kotlin puro. Se resolvieron bloqueos de tests
  previos antes de avanzar al renderer.
- Fases 6–7: Canvas con viewport y puente Compose; entrada táctil, teclado,
  IME/composición y portapapeles. Se corrigió el revelado del cursor al reducir
  la vista y se comprobaron transiciones a solo lectura y menú de pulsación larga.
- Fase 8: pestañas independientes, dirty por revisión y guardado asíncrono;
  cierre de documentos modificados con Guardar/Descartar/Cancelar.

## 2026-10-04 — Workspace, productividad y sintaxis

- Fase 9: filesystem SAF, explorador con operaciones y carga diferida;
  persistencia de pestañas/cursor/scroll y restauración leyendo disco.
- Fase 10: búsqueda/reemplazo con regex, Quick Open y paleta filtrable inicial.
  Posteriormente esa paleta se sustituyó por Archivo/Editar y `Ctrl+Shift+P`
  pasó a abrir Editar; el editor ganó altura al ocultar barras adicionales.
- Fase 11: resaltado léxico incremental y decoraciones; la fase 12 quedó
  pospuesta. Se quitó el bloqueo de edición por encima de 1 MB; permaneció el
  máximo de apertura de 25 MB. Se corrigió el Canvas que tapaba Buscar/Reemplazar.
- Configuración incorporó tema y estilo monoespaciado del editor; la navegación
  inferior dejó de mostrarse en la pantalla Editor.

## 2026-10-05 — Archivos grandes, apertura y lenguajes

- El resaltado de archivos grandes pasó a ventanas visibles con checkpoints de
  estado cada 128 líneas; los cambios invalidan los estados desde la primera
  línea modificada. `syncInput` conserva trabajo O(n) y un salto lejano puede
  reconstruir el estado desde el inicio.
- Android «Abrir con» incorporó URI externos `content://` y permisos de lectura/
  escritura según el proveedor; Guardar como permite exportar archivos de solo
  lectura.
- Se añadieron XML y JavaScript al selector/resaltado y formato conservador
  de XML/YAML. Después se retiró el resaltado de Kotlin, Java y JavaScript:
  quedan como texto plano; el formato vigente es JSON/YAML/XML.

## 2026-10-06 — Proveedores de archivos

- Direct incorporó rutas absolutas de almacenamiento compartido junto a SAF;
  `RoutedFileSystem` conserva el editor independiente del proveedor. Hay
  selector Direct propio y permiso `MANAGE_EXTERNAL_STORAGE` mediante Ajustes.
- Se revirtió el prototipo de acceso a rutas restringidas: Configuración solo
  ofrece SAF y Direct. Direct sigue bloqueando `Android/data` y `Android/obb`;
  ese acceso queda fuera de Structa.
- Tras la limpieza pasaron 167 JVM y 54 instrumentadas; las dos pruebas Direct
  se repitieron con permiso temporal para ejercer operaciones reales y se
  devolvió el app-op a `default`.
