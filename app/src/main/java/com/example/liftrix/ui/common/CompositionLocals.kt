package com.example.liftrix.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.example.liftrix.data.service.FirebaseStorageUrlResolver
import com.example.liftrix.service.ProfileImageCache

/**
 * CompositionLocal for providing FirebaseStorageUrlResolver throughout the composition tree.
 * 
 * This eliminates the need to manually thread the resolver through every component that needs
 * to resolve Firebase Storage paths to URLs. Components can simply access the resolver using:
 * 
 * ```kotlin
 * val urlResolver = LocalFirebaseStorageUrlResolver.current
 * ```
 * 
 * Benefits:
 * - Clean component APIs without manual parameter threading
 * - Easy testing with mock resolvers
 * - Consistent resolver access across the app
 * - Better separation of concerns
 * 
 * Usage in root composable:
 * ```kotlin
 * CompositionLocalProvider(LocalFirebaseStorageUrlResolver provides urlResolver) {
 *     // App content
 * }
 * ```
 */
val LocalFirebaseStorageUrlResolver = compositionLocalOf<FirebaseStorageUrlResolver?> {
    null // Default to null, will be provided at app root
}

/**
 * Helper function to safely access the FirebaseStorageUrlResolver from CompositionLocal.
 * Returns null if no resolver is provided, allowing components to handle graceful fallback.
 */
@androidx.compose.runtime.Composable
fun getCurrentStorageUrlResolver(): FirebaseStorageUrlResolver? = LocalFirebaseStorageUrlResolver.current

/**
 * CompositionLocal for providing ProfileImageCache throughout the composition tree.
 *
 * This ensures a single shared cache instance across all ProfileImageDisplay components,
 * preventing memory waste and improving cache hit rates.
 *
 * Benefits:
 * - Single LRU cache shared across all profile images (32MB total, not per component)
 * - Higher cache hit rates due to shared memory pool
 * - Proper singleton behavior despite Compose recompositions
 * - Easy testing with mock caches
 *
 * Usage in root composable:
 * ```kotlin
 * @Composable
 * fun LiftrixApp(profileImageCache: ProfileImageCache) {
 *     CompositionLocalProvider(LocalProfileImageCache provides profileImageCache) {
 *         // App content with profile images
 *     }
 * }
 * ```
 *
 * Usage in components:
 * ```kotlin
 * @Composable
 * fun ProfileImageDisplay(...) {
 *     val cache = LocalProfileImageCache.current
 *     // Use cache for loading images
 * }
 * ```
 */
val LocalProfileImageCache = compositionLocalOf<ProfileImageCache?> {
    null // Default to null, must be provided at app root
}