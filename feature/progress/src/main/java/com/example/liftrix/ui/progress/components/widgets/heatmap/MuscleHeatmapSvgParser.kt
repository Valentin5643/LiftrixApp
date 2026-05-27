package com.example.liftrix.ui.progress.components.widgets.heatmap

import android.content.Context
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Path as AndroidPath
import androidx.annotation.RawRes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.PathParser
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.analytics.MuscleHeatmapViewSide
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

internal data class MuscleHeatmapSvg(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val contentBounds: Rect,
    val paths: List<MuscleHeatmapPath>
)

internal data class MuscleHeatmapPath(
    val id: String,
    val path: Path,
    val muscleGroup: MuscleGroup?,
    val isSeparator: Boolean,
    val isFilledSeparator: Boolean = false
)

internal object MuscleHeatmapSvgParser {
    private val cache = ConcurrentHashMap<String, MuscleHeatmapSvg>()

    fun parse(
        context: Context,
        @RawRes resourceId: Int,
        viewSide: MuscleHeatmapViewSide
    ): MuscleHeatmapSvg {
        val cacheKey = "$resourceId:${viewSide.name}"
        return cache.getOrPut(cacheKey) {
            context.resources.openRawResource(resourceId).use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(stream, null)
                parseXml(parser, viewSide)
            }
        }
    }

    private fun parseXml(
        parser: XmlPullParser,
        viewSide: MuscleHeatmapViewSide
    ): MuscleHeatmapSvg {
        var viewportWidth = 900f
        var viewportHeight = 1500f
        val paths = mutableListOf<MuscleHeatmapPath>()
        val pathsById = mutableMapOf<String, MuscleHeatmapPath>()
        val groupIds = mutableListOf<String>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "g" -> {
                        groupIds.add(parser.getAttributeValue(null, "id").orEmpty())
                    }
                    "svg" -> {
                        val viewBox = parser.getAttributeValue(null, "viewBox")
                        if (!viewBox.isNullOrBlank()) {
                            val parts = viewBox.split(" ").mapNotNull { it.toFloatOrNull() }
                            if (parts.size == 4) {
                                viewportWidth = parts[2]
                                viewportHeight = parts[3]
                            }
                        }
                    }
                    "path" -> {
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        val pathData = parser.getAttributeValue(null, "d")
                        if (id.isNotBlank() && !pathData.isNullOrBlank()) {
                            val isBackground = id.contains("background", ignoreCase = true)
                            if (!isBackground) {
                                val isSeparatorGroup = groupIds.any(::isSeparatorGroupId)
                                val isSeparator = isSeparatorId(id) || isSeparatorGroup
                                runCatching {
                                    MuscleHeatmapPath(
                                        id = id,
                                        path = PathParser().parsePathString(pathData).toPath(),
                                        muscleGroup = muscleGroupForSvgId(id, viewSide),
                                        isSeparator = isSeparator,
                                        isFilledSeparator = isSeparator && isSeparatorGroup && parser.hasVisibleFill()
                                    )
                                }.getOrNull()?.let { path ->
                                    paths.add(path)
                                    pathsById[id] = path
                                }
                            }
                        }
                    }
                    "use" -> {
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        val href = parser.getAttributeValue(null, "href")
                            ?: parser.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                        val referencedId = href?.removePrefix("#").orEmpty()
                        val referencedPath = pathsById[referencedId]
                        val isSeparatorGroup = groupIds.any(::isSeparatorGroupId)
                        val effectiveId = id.takeIf { it.isNotBlank() }
                            ?: if (isSeparatorGroup && referencedId.isNotBlank()) {
                                "$referencedId-separator-${paths.size}"
                            } else {
                                ""
                            }
                        if (effectiveId.isNotBlank() && referencedPath != null) {
                            val isSeparator = isSeparatorId(effectiveId) || isSeparatorGroup
                            paths.add(
                                referencedPath.copy(
                                    id = effectiveId,
                                    path = referencedPath.path.transformedBySvgUse(
                                        parser.getAttributeValue(null, "transform").orEmpty()
                                    ),
                                    muscleGroup = muscleGroupForSvgId(effectiveId, viewSide)
                                        ?: referencedPath.muscleGroup,
                                    isSeparator = isSeparator,
                                    isFilledSeparator = isSeparator && isSeparatorGroup && parser.hasVisibleFill()
                                )
                            )
                        }
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG && parser.name == "g") {
                if (groupIds.isNotEmpty()) {
                    groupIds.removeAt(groupIds.lastIndex)
                }
            }
            eventType = parser.next()
        }

        return MuscleHeatmapSvg(
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            contentBounds = paths.contentBounds(
                fallbackWidth = viewportWidth,
                fallbackHeight = viewportHeight
            ),
            paths = paths
        )
    }

    private fun List<MuscleHeatmapPath>.contentBounds(
        fallbackWidth: Float,
        fallbackHeight: Float
    ): Rect {
        val drawablePaths = filterNot { it.isSeparator }
            .takeIf { it.isNotEmpty() }
            ?: this
        if (drawablePaths.isEmpty()) return Rect(0f, 0f, fallbackWidth, fallbackHeight)

        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        drawablePaths.forEach { region ->
            val bounds = region.path.getBounds()
            left = minOf(left, bounds.left)
            top = minOf(top, bounds.top)
            right = maxOf(right, bounds.right)
            bottom = maxOf(bottom, bounds.bottom)
        }

        return if (left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            Rect(left, top, right, bottom)
        } else {
            Rect(0f, 0f, fallbackWidth, fallbackHeight)
        }
    }

    private fun isSeparatorId(id: String): Boolean {
        return id.contains("separator", ignoreCase = true) ||
            id.contains("line", ignoreCase = true) ||
            id.contains("white", ignoreCase = true) ||
            id.contains("outline", ignoreCase = true) ||
            id.contains("divider", ignoreCase = true) ||
            id.contains("internal", ignoreCase = true) ||
            id.contains("slit", ignoreCase = true)
    }

    private fun isSeparatorGroupId(id: String): Boolean {
        return id.contains("separator", ignoreCase = true) ||
            id.contains("outline", ignoreCase = true)
    }

    private fun XmlPullParser.hasVisibleFill(): Boolean {
        val fill = getAttributeValue(null, "fill")
        return !fill.isNullOrBlank() && !fill.equals("none", ignoreCase = true)
    }

    private fun Path.transformedBySvgUse(transform: String): Path {
        if (transform.isBlank()) return this

        val matrix = AndroidMatrix()
        transformFunctionRegex.findAll(transform).forEach { match ->
            val operation = match.groupValues[1]
            val values = match.groupValues[2]
                .split(',', ' ')
                .mapNotNull { it.takeIf(String::isNotBlank)?.toFloatOrNull() }

            when (operation) {
                "translate" -> matrix.postTranslate(
                    values.getOrNull(0) ?: 0f,
                    values.getOrNull(1) ?: 0f
                )
                "scale" -> matrix.preScale(
                    values.getOrNull(0) ?: 1f,
                    values.getOrNull(1) ?: values.getOrNull(0) ?: 1f
                )
            }
        }

        return AndroidPath(asAndroidPath()).apply {
            transform(matrix)
        }.asComposePath()
    }

    private val transformFunctionRegex = Regex("""(\w+)\(([^)]*)\)""")
}
