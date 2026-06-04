package com.example.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.model.StatusItem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StatusStorageManager {

    private const val SHARED_PREFS_NAME = "status_saver_prefs"
    private const val PREF_KEY_WA_URI = "whatsapp_saf_uri"
    private const val PREF_KEY_WAB_URI = "whatsapp_business_saf_uri"

    // High quality live images for simulation inside streaming emulator
    val MockImages = listOf(
        StatusItem("mock_img_1", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800", "sunset_mountains.jpg", false, 1245000L, isSaved = false, isBusiness = false, fallbackUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800"),
        StatusItem("mock_img_2", "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800", "forest_sunrise.jpg", false, 843000L, isSaved = false, isBusiness = false, fallbackUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800"),
        StatusItem("mock_img_3", "https://images.unsplash.com/photo-1513836279014-a89f7a76ae86?w=800", "mystic_forest.jpg", false, 2304000L, isSaved = false, isBusiness = true, fallbackUrl = "https://images.unsplash.com/photo-1513836279014-a89f7a76ae86?w=800"),
        StatusItem("mock_img_4", "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=800", "alpine_meadows.jpg", false, 915000L, isSaved = false, isBusiness = true, fallbackUrl = "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=800"),
        StatusItem("mock_img_5", "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=800", "redwood_cathedral.jpg", false, 1850000L, isSaved = false, isBusiness = false, fallbackUrl = "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=800")
    )

    // High quality videos for simulation inside streaming emulator
    val MockVideos = listOf(
        StatusItem("mock_vid_1", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", "bigger_blazes.mp4", true, 4512000L, isSaved = false, isBusiness = false, fallbackUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
        StatusItem("mock_vid_2", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", "bigger_escapes.mp4", true, 3121000L, isSaved = false, isBusiness = true, fallbackUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"),
        StatusItem("mock_vid_3", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4", "bigger_fun.mp4", true, 5821000L, isSaved = false, isBusiness = false, fallbackUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"),
        StatusItem("mock_vid_4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4", "bigger_joyrides.mp4", true, 2984000L, isSaved = false, isBusiness = true, fallbackUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4")
    )

    fun getPersistedUriPermission(context: Context, isBusiness: Boolean): String? {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (isBusiness) PREF_KEY_WAB_URI else PREF_KEY_WA_URI
        val savedUri = prefs.getString(key, null) ?: return null

        // Check if our app still has permissions
        val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persistedUriPermissions = context.contentResolver.persistedUriPermissions
        for (perm in persistedUriPermissions) {
            if (perm.uri.toString() == savedUri && perm.isReadPermission) {
                return savedUri
            }
        }
        return null
    }

    fun savePersistedUriPermission(context: Context, uri: Uri, isBusiness: Boolean) {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (isBusiness) PREF_KEY_WAB_URI else PREF_KEY_WA_URI
        prefs.edit().putString(key, uri.toString()).apply()
    }

    fun clearPersistedUriPermission(context: Context, isBusiness: Boolean) {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (isBusiness) PREF_KEY_WAB_URI else PREF_KEY_WA_URI
        prefs.edit().remove(key).apply()
    }

    fun getWhatsAppTreeUri(isBusiness: Boolean): Uri {
        val path = if (Build.VERSION.SDK_INT < 30) {
            if (isBusiness) {
                "WhatsApp Business/Media/.Statuses"
            } else {
                "WhatsApp/Media/.Statuses"
            }
        } else {
            if (isBusiness) {
                "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
            } else {
                "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
            }
        }
        return Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A${path.replace("/", "%2F")}")
    }

    suspend fun loadStatusesFromTree(context: Context, treeUriStr: String, isBusiness: Boolean): List<StatusItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StatusItem>()
        try {
            val treeUri = Uri.parse(treeUriStr)
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex)
                    val mime = cursor.getString(mimeIndex) ?: ""
                    val size = cursor.getLong(sizeIndex)

                    val isVideo = mime.startsWith("video/")
                    val isImage = mime.startsWith("image/")

                    if (isVideo || isImage) {
                        val singleDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val id = docId.hashCode().toString()
                        
                        // Check if this file is already saved
                        val isSaved = isStatusAlreadyDownloaded(context, name)

                        results.add(
                            StatusItem(
                                id = id,
                                uriString = singleDocumentUri.toString(),
                                fileName = name,
                                isVideo = isVideo,
                                fileSize = size,
                                isSaved = isSaved,
                                isBusiness = isBusiness
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    fun loadStatusesFromFileSystem(context: Context, isBusiness: Boolean): List<StatusItem> {
        val results = mutableListOf<StatusItem>()
        if (Build.VERSION.SDK_INT >= 30) return results
        
        // Define possible WhatsApp status directories on standard public storage
        val sdcard = Environment.getExternalStorageDirectory()
        val paths = if (isBusiness) {
            listOf(
                File(sdcard, "WhatsApp Business/Media/.Statuses"),
                File(sdcard, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses")
            )
        } else {
            listOf(
                File(sdcard, "WhatsApp/Media/.Statuses"),
                File(sdcard, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses")
            )
        }
        
        for (dir in paths) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && !file.name.startsWith(".")) {
                            val name = file.name
                            val isVideo = name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".mkv", ignoreCase = true)
                            val isImage = name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) || name.endsWith(".png", ignoreCase = true)
                            
                            if (isVideo || isImage) {
                                val isSaved = isStatusAlreadyDownloaded(context, name)
                                results.add(
                                    StatusItem(
                                        id = file.absolutePath.hashCode().toString(),
                                        uriString = Uri.fromFile(file).toString(),
                                        fileName = name,
                                        isVideo = isVideo,
                                        fileSize = file.length(),
                                        isSaved = isSaved,
                                        isBusiness = isBusiness
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    private fun getLocalAppSavedDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "SavedStatuses")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun isStatusAlreadyDownloaded(context: Context, filename: String): Boolean {
        // Check local storage folder
        val file = File(getLocalAppSavedDir(context), filename)
        if (file.exists()) return true

        // Also check if it exists in public picture/movie gallery
        return false
    }

    suspend fun saveStatusFile(context: Context, item: StatusItem): Boolean = withContext(Dispatchers.IO) {
        try {
            var inputStream: InputStream? = null
            
            if (item.fallbackUrl != null || item.uriString.startsWith("http")) {
                // If it is mock URL, download it via HTTP
                val url = URL(item.fallbackUrl ?: item.uriString)
                val connection = url.openConnection()
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                inputStream = connection.getInputStream()
            } else {
                // Otherwise read from SAF Uri content resolver
                val uri = Uri.parse(item.uriString)
                inputStream = context.contentResolver.openInputStream(uri)
            }

            if (inputStream == null) return@withContext false

            // Save to app private directory
            val savedDir = getLocalAppSavedDir(context)
            val destFile = File(savedDir, item.fileName)
            FileOutputStream(destFile).use { out ->
                inputStream.copyTo(out)
            }

            // Save to absolute gallery pictures/movies for Android OS gallery visibility
            saveToAndroidGallery(context, destFile, item.isVideo)

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun saveToAndroidGallery(context: Context, localFile: File, isVideo: Boolean) {
        try {
            val contentResolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, localFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = if (isVideo) {
                        "${Environment.DIRECTORY_MOVIES}/StatusSaver2026"
                    } else {
                        "${Environment.DIRECTORY_PICTURES}/StatusSaver2026"
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val externalUri = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = contentResolver.insert(externalUri, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { output ->
                    localFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }

                // Notify media scanner so gallery indexes it instantly
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(localFile.absolutePath),
                    arrayOf(if (isVideo) "video/mp4" else "image/jpeg")
                ) { _, _ -> }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteSavedFile(context: Context, item: StatusItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(getLocalAppSavedDir(context), item.fileName)
            if (file.exists()) {
                val deleted = file.delete()
                
                // Scan to update gallery
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null
                ) { _, _ -> }
                
                return@withContext deleted
            }
            return@withContext false
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun clearAllSavedFiles(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getLocalAppSavedDir(context)
            if (dir.exists()) {
                val files = dir.listFiles()
                if (files != null) {
                    for (file in files) {
                        file.delete()
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun loadSavedStatuses(context: Context): List<StatusItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StatusItem>()
        try {
            val savedDir = getLocalAppSavedDir(context)
            if (savedDir.exists()) {
                val files = savedDir.listFiles()
                if (files != null) {
                    // Sort descending by last modified to show newest downloads first
                    files.sortByDescending { it.lastModified() }
                    for (file in files) {
                        if (file.isFile) {
                            val name = file.name
                            val isVideo = name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".mkv", ignoreCase = true)
                            results.add(
                                StatusItem(
                                    id = file.absolutePath.hashCode().toString(),
                                    uriString = Uri.fromFile(file).toString(),
                                    fileName = name,
                                    isVideo = isVideo,
                                    fileSize = file.length(),
                                    isSaved = true,
                                    isBusiness = name.contains("business", ignoreCase = true)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }
}
