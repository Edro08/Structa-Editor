package com.edro08.structa.domain.editor.command

import com.edro08.structa.domain.editor.editing.EditingSession

/** Engine-created command context. Commands use editing primitives, not the buffer. */
class EditorContext internal constructor(internal val session: EditingSession)
