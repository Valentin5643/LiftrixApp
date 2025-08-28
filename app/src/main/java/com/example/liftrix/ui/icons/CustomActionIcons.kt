package com.example.liftrix.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Custom Action Icons for ModernActionButton components
 * 
 * These are clean, properly aligned vector icons designed specifically
 * for the Liftrix app's button system. They provide consistent styling
 * and perfect alignment compared to Unicode text symbols.
 */
object CustomActionIcons {
    
    /**
     * Sign Out / Exit icon
     * Clean arrow pointing right with exit door metaphor
     */
    val SignOut: ImageVector
        get() {
            if (_signOut != null) {
                return _signOut!!
            }
            _signOut = materialIcon(name = "Custom.SignOut") {
                materialPath {
                    moveTo(10.09f, 15.59f)
                    lineTo(11.5f, 17.0f)
                    lineTo(16.5f, 12.0f)
                    lineTo(11.5f, 7.0f)
                    lineTo(10.09f, 8.41f)
                    lineTo(12.67f, 11.0f)
                    lineTo(3.0f, 11.0f)
                    lineTo(3.0f, 13.0f)
                    lineTo(12.67f, 13.0f)
                    lineTo(10.09f, 15.59f)
                    close()
                    
                    moveTo(19.0f, 3.0f)
                    lineTo(5.0f, 3.0f)
                    curveTo(3.89f, 3.0f, 3.0f, 3.89f, 3.0f, 5.0f)
                    lineTo(3.0f, 9.0f)
                    lineTo(5.0f, 9.0f)
                    lineTo(5.0f, 5.0f)
                    lineTo(19.0f, 5.0f)
                    lineTo(19.0f, 19.0f)
                    lineTo(5.0f, 19.0f)
                    lineTo(5.0f, 15.0f)
                    lineTo(3.0f, 15.0f)
                    lineTo(3.0f, 19.0f)
                    curveTo(3.0f, 20.11f, 3.89f, 21.0f, 5.0f, 21.0f)
                    lineTo(19.0f, 21.0f)
                    curveTo(20.11f, 21.0f, 21.0f, 20.11f, 21.0f, 19.0f)
                    lineTo(21.0f, 5.0f)
                    curveTo(21.0f, 3.89f, 20.11f, 3.0f, 19.0f, 3.0f)
                    close()
                }
            }
            return _signOut!!
        }
    
    private var _signOut: ImageVector? = null
    
    /**
     * Settings / Manage icon
     * Clean gear/cog icon for settings and management actions
     */
    val Settings: ImageVector
        get() {
            if (_settings != null) {
                return _settings!!
            }
            _settings = materialIcon(name = "Custom.Settings") {
                materialPath {
                    moveTo(19.14f, 12.94f)
                    curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12.0f)
                    curveTo(19.2f, 11.68f, 19.18f, 11.36f, 19.14f, 11.06f)
                    lineTo(21.16f, 9.48f)
                    curveTo(21.34f, 9.34f, 21.39f, 9.07f, 21.28f, 8.87f)
                    lineTo(19.36f, 5.55f)
                    curveTo(19.24f, 5.33f, 18.97f, 5.26f, 18.73f, 5.35f)
                    lineTo(16.38f, 6.29f)
                    curveTo(15.93f, 5.93f, 15.45f, 5.64f, 14.92f, 5.43f)
                    lineTo(14.54f, 2.81f)
                    curveTo(14.5f, 2.55f, 14.27f, 2.36f, 14.0f, 2.36f)
                    lineTo(10.0f, 2.36f)
                    curveTo(9.73f, 2.36f, 9.51f, 2.55f, 9.47f, 2.81f)
                    lineTo(9.08f, 5.43f)
                    curveTo(8.56f, 5.64f, 8.07f, 5.92f, 7.62f, 6.29f)
                    lineTo(5.27f, 5.35f)
                    curveTo(5.03f, 5.26f, 4.76f, 5.33f, 4.64f, 5.55f)
                    lineTo(2.72f, 8.87f)
                    curveTo(2.61f, 9.07f, 2.66f, 9.34f, 2.84f, 9.48f)
                    lineTo(4.86f, 11.06f)
                    curveTo(4.82f, 11.36f, 4.8f, 11.69f, 4.8f, 12.0f)
                    curveTo(4.8f, 12.31f, 4.82f, 12.64f, 4.86f, 12.94f)
                    lineTo(2.84f, 14.52f)
                    curveTo(2.66f, 14.66f, 2.61f, 14.93f, 2.72f, 15.13f)
                    lineTo(4.64f, 18.45f)
                    curveTo(4.76f, 18.67f, 5.03f, 18.74f, 5.27f, 18.65f)
                    lineTo(7.62f, 17.71f)
                    curveTo(8.07f, 18.07f, 8.56f, 18.36f, 9.08f, 18.57f)
                    lineTo(9.47f, 21.19f)
                    curveTo(9.51f, 21.45f, 9.73f, 21.64f, 10.0f, 21.64f)
                    lineTo(14.0f, 21.64f)
                    curveTo(14.27f, 21.64f, 14.5f, 21.45f, 14.54f, 21.19f)
                    lineTo(14.92f, 18.57f)
                    curveTo(15.45f, 18.36f, 15.93f, 18.07f, 16.38f, 17.71f)
                    lineTo(18.73f, 18.65f)
                    curveTo(18.97f, 18.74f, 19.24f, 18.67f, 19.36f, 18.45f)
                    lineTo(21.28f, 15.13f)
                    curveTo(21.39f, 14.93f, 21.34f, 14.66f, 21.16f, 14.52f)
                    lineTo(19.14f, 12.94f)
                    close()
                    
                    moveTo(12.0f, 15.6f)
                    curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12.0f)
                    curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12.0f, 8.4f)
                    curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12.0f)
                    curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12.0f, 15.6f)
                    close()
                }
            }
            return _settings!!
        }
    
    private var _settings: ImageVector? = null
    
    /**
     * Add / Plus icon
     * Clean plus sign for adding items
     */
    val Add: ImageVector
        get() {
            if (_add != null) {
                return _add!!
            }
            _add = materialIcon(name = "Custom.Add") {
                materialPath {
                    moveTo(19.0f, 13.0f)
                    lineTo(13.0f, 13.0f)
                    lineTo(13.0f, 19.0f)
                    lineTo(11.0f, 19.0f)
                    lineTo(11.0f, 13.0f)
                    lineTo(5.0f, 13.0f)
                    lineTo(5.0f, 11.0f)
                    lineTo(11.0f, 11.0f)
                    lineTo(11.0f, 5.0f)
                    lineTo(13.0f, 5.0f)
                    lineTo(13.0f, 11.0f)
                    lineTo(19.0f, 11.0f)
                    lineTo(19.0f, 13.0f)
                    close()
                }
            }
            return _add!!
        }
    
    private var _add: ImageVector? = null
    
    /**
     * Edit / Pencil icon  
     * Clean pencil icon for editing actions
     */
    val Edit: ImageVector
        get() {
            if (_edit != null) {
                return _edit!!
            }
            _edit = materialIcon(name = "Custom.Edit") {
                materialPath {
                    moveTo(3.0f, 17.25f)
                    lineTo(3.0f, 21.0f)
                    lineTo(6.75f, 21.0f)
                    lineTo(17.81f, 9.94f)
                    lineTo(14.06f, 6.19f)
                    lineTo(3.0f, 17.25f)
                    close()
                    
                    moveTo(20.71f, 7.04f)
                    curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
                    lineTo(18.37f, 3.29f)
                    curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
                    lineTo(15.13f, 5.12f)
                    lineTo(18.88f, 8.87f)
                    lineTo(20.71f, 7.04f)
                    close()
                }
            }
            return _edit!!
        }
    
    private var _edit: ImageVector? = null
    
    /**
     * Close / Cancel icon
     * Clean X icon for canceling actions
     */
    val Close: ImageVector
        get() {
            if (_close != null) {
                return _close!!
            }
            _close = materialIcon(name = "Custom.Close") {
                materialPath {
                    moveTo(19.0f, 6.41f)
                    lineTo(17.59f, 5.0f)
                    lineTo(12.0f, 10.59f)
                    lineTo(6.41f, 5.0f)
                    lineTo(5.0f, 6.41f)
                    lineTo(10.59f, 12.0f)
                    lineTo(5.0f, 17.59f)
                    lineTo(6.41f, 19.0f)
                    lineTo(12.0f, 13.41f)
                    lineTo(17.59f, 19.0f)
                    lineTo(19.0f, 17.59f)
                    lineTo(13.41f, 12.0f)
                    lineTo(19.0f, 6.41f)
                    close()
                }
            }
            return _close!!
        }
    
    private var _close: ImageVector? = null
    
    /**
     * Refresh / Retry icon
     * Clean circular arrow for refresh actions
     */
    val Refresh: ImageVector
        get() {
            if (_refresh != null) {
                return _refresh!!
            }
            _refresh = materialIcon(name = "Custom.Refresh") {
                materialPath {
                    moveTo(17.65f, 6.35f)
                    curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
                    curveTo(7.58f, 4.0f, 4.0f, 7.58f, 4.0f, 12.0f)
                    curveTo(4.0f, 16.42f, 7.58f, 20.0f, 12.0f, 20.0f)
                    curveTo(15.73f, 20.0f, 18.84f, 17.45f, 19.73f, 14.0f)
                    lineTo(17.65f, 14.0f)
                    curveTo(16.83f, 16.33f, 14.61f, 18.0f, 12.0f, 18.0f)
                    curveTo(8.69f, 18.0f, 6.0f, 15.31f, 6.0f, 12.0f)
                    curveTo(6.0f, 8.69f, 8.69f, 6.0f, 12.0f, 6.0f)
                    curveTo(13.66f, 6.0f, 15.14f, 6.69f, 16.22f, 7.78f)
                    lineTo(13.0f, 11.0f)
                    lineTo(20.0f, 11.0f)
                    lineTo(20.0f, 4.0f)
                    lineTo(17.65f, 6.35f)
                    close()
                }
            }
            return _refresh!!
        }
    
    private var _refresh: ImageVector? = null
}