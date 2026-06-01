package com.example.liftrix.ui.share

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class WorkoutShareImageExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun exportToCache(
        stats: WorkoutShareStoryStats,
        template: WorkoutShareTemplate
    ): LiftrixResult<WorkoutShareExportResult> = liftrixCatching(
        errorMapper = { error ->
            LiftrixError.BusinessLogicError(
                code = "WORKOUT_STORY_EXPORT_FAILED",
                errorMessage = "Unable to generate workout story: ${error.message}",
                analyticsContext = mapOf("template" to template.id)
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val bitmap = renderStoryBitmap(stats, template)
            val shareDir = File(context.cacheDir, "workout_story_exports").apply {
                mkdirs()
                listFiles()?.forEach { oldFile -> oldFile.delete() }
            }
            val outputFile = File(shareDir, "workout_story_${System.currentTimeMillis()}.png")
            outputFile.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Bitmap compression failed"
                }
            }
            bitmap.recycle()
            WorkoutShareExportResult(
                filePath = outputFile.absolutePath,
                width = ExportWidth,
                height = ExportHeight
            )
        }
    }

    suspend fun saveToDevice(
        stats: WorkoutShareStoryStats,
        template: WorkoutShareTemplate
    ): LiftrixResult<Uri> = liftrixCatching(
        errorMapper = { error ->
            LiftrixError.BusinessLogicError(
                code = "WORKOUT_STORY_SAVE_FAILED",
                errorMessage = error.message ?: "Unable to save workout story",
                analyticsContext = mapOf("template" to template.id)
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw IllegalStateException("Saving stories requires Android 10 or newer on this screen")
            }

            val bitmap = renderStoryBitmap(stats, template)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "liftrix_workout_story_${System.currentTimeMillis()}.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Liftrix")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create MediaStore entry")

            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Bitmap compression failed"
                }
            } ?: throw IllegalStateException("Unable to open image output stream")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            bitmap.recycle()
            uri
        }
    }

    fun renderStoryBitmap(
        stats: WorkoutShareStoryStats,
        template: WorkoutShareTemplate
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(ExportWidth, ExportHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, template)
        drawOverlay(canvas)
        drawStoryContent(canvas, stats)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, template: WorkoutShareTemplate) {
        val background = runCatching {
            context.assets.open(template.assetPath).use(BitmapFactory::decodeStream)
        }.getOrNull()

        if (background == null) {
            canvas.drawColor(Color.rgb(18, 22, 28))
            return
        }

        val sourceRatio = background.width.toFloat() / background.height.toFloat()
        val targetRatio = ExportWidth.toFloat() / ExportHeight.toFloat()
        val source = if (sourceRatio > targetRatio) {
            val width = (background.height * targetRatio).toInt()
            val left = (background.width - width) / 2
            Rect(left, 0, left + width, background.height)
        } else {
            val height = (background.width / targetRatio).toInt()
            val top = (background.height - height) / 2
            Rect(0, top, background.width, top + height)
        }
        canvas.drawBitmap(background, source, Rect(0, 0, ExportWidth, ExportHeight), Paint(Paint.ANTI_ALIAS_FLAG))
        background.recycle()
    }

    private fun drawOverlay(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                ExportHeight.toFloat(),
                intArrayOf(
                    Color.argb(25, 0, 0, 0),
                    Color.argb(115, 0, 0, 0),
                    Color.argb(225, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, ExportWidth.toFloat(), ExportHeight.toFloat(), paint)
    }

    private fun drawStoryContent(canvas: Canvas, stats: WorkoutShareStoryStats) {
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(205, 255, 255, 255) }
        val darkPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }
        val accentPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 42, 135, 105) }

        white.textSize = 34f
        white.isFakeBoldText = true
        canvas.drawText("LIFTRIX", Padding, 102f, white)

        muted.textSize = 30f
        canvas.drawText(stats.displayDate.uppercase(), Padding, 150f, muted)

        white.textSize = 76f
        white.isFakeBoldText = true
        drawMultilineText(canvas, stats.workoutName, Padding, 1220f, ExportWidth - Padding * 2, white, 88f, 3)

        val tileTop = 1480f
        val tileWidth = (ExportWidth - Padding * 2 - 28f) / 2f
        drawStatTile(canvas, "Volume", stats.totalVolume, Padding, tileTop, tileWidth, darkPanel, white, muted)
        drawStatTile(canvas, "Exercises", stats.exerciseCount, Padding + tileWidth + 28f, tileTop, tileWidth, darkPanel, white, muted)

        var nextTop = tileTop + 154f
        stats.duration?.let {
            drawStatTile(canvas, "Duration", it, Padding, nextTop, ExportWidth - Padding * 2, darkPanel, white, muted)
            nextTop += 154f
        }

        val prPanel = RectF(Padding, nextTop + 18f, ExportWidth - Padding, ExportHeight - 86f)
        canvas.drawRoundRect(prPanel, 26f, 26f, accentPanel)
        white.textSize = 38f
        white.isFakeBoldText = true
        canvas.drawText(stats.prSummary, prPanel.left + 30f, prPanel.top + 58f, white)
        muted.textSize = 28f
        muted.isFakeBoldText = false
        stats.prLabels.take(3).forEachIndexed { index, label ->
            canvas.drawText(label.take(56), prPanel.left + 30f, prPanel.top + 106f + index * 40f, muted)
        }
    }

    private fun drawStatTile(
        canvas: Canvas,
        label: String,
        value: String,
        left: Float,
        top: Float,
        width: Float,
        backgroundPaint: Paint,
        valuePaint: Paint,
        labelPaint: Paint
    ) {
        canvas.drawRoundRect(RectF(left, top, left + width, top + 126f), 22f, 22f, backgroundPaint)
        labelPaint.textSize = 27f
        canvas.drawText(label, left + 28f, top + 44f, labelPaint)
        valuePaint.textSize = 40f
        valuePaint.isFakeBoldText = true
        canvas.drawText(value.take(20), left + 28f, top + 94f, valuePaint)
    }

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
        maxLines: Int
    ) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
                current = candidate
            } else {
                lines.add(current)
                current = word
            }
            if (lines.size == maxLines) break
        }
        if (lines.size < maxLines && current.isNotBlank()) lines.add(current)
        lines.take(maxLines).forEachIndexed { index, line ->
            canvas.drawText(line, x, y + index * lineHeight, paint)
        }
    }

    companion object {
        const val ExportWidth = 1080
        const val ExportHeight = 1920
        private const val Padding = 72f
    }
}
