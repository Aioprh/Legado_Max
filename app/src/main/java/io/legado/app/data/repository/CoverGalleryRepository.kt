package io.legado.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.CoverGalleryGroup
import io.legado.app.data.entities.CoverGalleryImage
import io.legado.app.model.BookCover
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import java.io.FileOutputStream

class CoverGalleryRepository {

    private val dao = appDb.coverGalleryDao

    fun flowGroupsWithImages(query: String) = if (query.isBlank()) {
        dao.flowGroupsWithImages()
    } else {
        dao.flowGroupsWithImages(query)
    }

    fun flowGroupWithImages(groupId: Long) = dao.flowGroupWithImages(groupId)

    suspend fun addGroup(name: String): Long {
        val order = (dao.getMaxGroupOrder() ?: -1) + 1
        return dao.insertGroup(
            CoverGalleryGroup(
                name = name.trim(),
                order = order
            )
        )
    }

    suspend fun renameGroup(groupId: Long, name: String) {
        val group = dao.getGroup(groupId) ?: return
        dao.updateGroup(
            group.copy(
                name = name.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
        refreshDefaultCover()
    }

    suspend fun deleteGroup(groupId: Long) {
        dao.deleteGroup(groupId)
        refreshDefaultCover()
    }

    suspend fun addImage(context: Context, groupId: Long, uri: Uri) {
        val path = copyImageToCovers(context, uri)
        val order = (dao.getMaxImageOrder(groupId) ?: -1) + 1
        dao.insertImage(
            CoverGalleryImage(
                groupId = groupId,
                path = path,
                order = order
            )
        )
        refreshDefaultCover()
    }

    suspend fun deleteImage(imageId: Long) {
        dao.deleteImage(imageId)
        refreshDefaultCover()
    }

    suspend fun setDefaultGroup(groupId: Long) {
        dao.setDefaultGroup(groupId)
        refreshDefaultCover()
    }

    suspend fun unsetDefaultGroup(groupId: Long) {
        dao.unmarkDefaultGroup(groupId, System.currentTimeMillis())
        refreshDefaultCover()
    }

    fun getDefaultCoverPath(): String? {
        return dao.getDefaultCoverPath()?.takeIf { it.isNotBlank() }
    }

    private fun copyImageToCovers(context: Context, uri: Uri): String {
        var file = context.externalFiles
        val sourceName = DocumentFile.fromSingleUri(context, uri)?.name.orEmpty()
        val suffix = if (sourceName.contains(".9.png", true)) {
            ".9.png"
        } else {
            "." + sourceName.substringAfterLast(".", "jpg")
        }
        val fileName = uri.inputStream(context).getOrThrow().use {
            MD5Utils.md5Encode(it) + suffix
        }
        file = FileUtils.createFileIfNotExist(file, "covers", fileName)
        uri.inputStream(context).getOrThrow().use { inputStream ->
            FileOutputStream(file).use {
                inputStream.copyTo(it)
            }
        }
        return file.absolutePath
    }

    private fun refreshDefaultCover() {
        BookCover.upDefaultCover()
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }
}
