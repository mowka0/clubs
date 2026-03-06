package com.clubs.storage

interface FileStorageService {
    fun uploadFile(bytes: ByteArray, key: String): String
    fun getFileUrl(key: String): String
    fun deleteFile(key: String)
}
