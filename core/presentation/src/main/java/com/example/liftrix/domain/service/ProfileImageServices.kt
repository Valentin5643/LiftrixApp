package com.example.liftrix.domain.service

import android.graphics.Bitmap

interface ProfileImageUrlResolver {
    suspend fun resolveUrl(storagePath: String): String?
}

interface ProfileImageLoader {
    suspend fun loadImage(url: String, userId: String): Bitmap?
}
