package com.edro08.structa.data.filesystem

import com.edro08.structa.data.filesystem.direct.DirectFileSystem
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.*

/** Resolve the provider at the boundary, leaving browser/editor/use cases provider-neutral. */
class RoutedFileSystem(saf: FileSystem, direct: FileSystem = DirectFileSystem(),
    additionalProviders: Map<String, FileSystem> = emptyMap()) : FileSystem {
    override val key = "routed"
    private val providers = mapOf(saf.key to saf, direct.key to direct) + additionalProviders
    private fun provider(id: DocumentId): FileSystem = providers[FileRef.of(id).fileSystem]
        ?: throw IllegalArgumentException("Proveedor de archivos no disponible")

    override suspend fun stat(id: DocumentId) = provider(id).stat(id)
    override suspend fun exists(id: DocumentId) = provider(id).exists(id)
    override suspend fun list(directory: DocumentId) = provider(directory).list(directory)
    override suspend fun read(file: FileEntry) = provider(file.id).read(file)
    override suspend fun write(destination: DocumentId, content: String) = provider(destination).write(destination, content)
    override suspend fun create(parent: DocumentId, name: String, directory: Boolean) = provider(parent).create(parent, name, directory)
    override suspend fun rename(file: DocumentId, name: String) = provider(file).rename(file, name)
    override suspend fun delete(file: DocumentId) = provider(file).delete(file)
}
