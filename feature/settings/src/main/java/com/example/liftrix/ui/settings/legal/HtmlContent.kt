package com.example.liftrix.ui.settings.legal

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import timber.log.Timber

/**
 * Renders HTML content using a hybrid approach:
 *
 * 1. **Default**: HtmlCompat → AnnotatedString (fast, native, themeable)
 * 2. **Fallback**: WebView only when complex CSS/markup is detected
 *
 * Design Decisions:
 * - WebView is a last-mile renderer, NOT the default
 * - Most legal documents use semantic HTML (<p>, <b>, <ul>, <a>)
 * - HtmlCompat handles 90% of cases with better:
 *   - Performance (no lifecycle overhead)
 *   - Theming (respects Material colors/fonts)
 *   - Accessibility (native text selection)
 *   - Scroll behavior (native Compose scrolling)
 *
 * WebView Escalation Criteria:
 * - Inline CSS detected (<style>, font-family, etc.)
 * - Complex attributes (class=, id=) that affect rendering
 * - External resources that HtmlCompat can't handle
 *
 * @param html Raw HTML string to render
 * @param modifier Composable modifier
 */
@Composable
fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier,
    anchors: List<HtmlAnchor> = emptyList(),
    scrollToAnchorId: String? = null,
    scrollRequestKey: Int = 0
) {
    // Deterministic detection: use WebView only when necessary
    if (requiresWebView(html)) {
        Timber.d("HTML requires WebView rendering (complex markup detected)")
        HtmlWebView(
            html = html,
            modifier = modifier,
            anchors = anchors,
            scrollToAnchorId = scrollToAnchorId,
            scrollRequestKey = scrollRequestKey
        )
    } else {
        Timber.d("HTML using HtmlCompat rendering (semantic markup)")
        HtmlText(
            html = html,
            modifier = modifier
        )
    }
}

/**
 * Deterministic WebView requirement detection
 *
 * Returns true if HTML contains features unsupported by HtmlCompat:
 * - Inline CSS (<style> tags, style= attributes)
 * - CSS classes/IDs (class=, id=)
 * - Complex positioning (CSS properties in style attributes)
 * - External resources (link tags, @import)
 *
 * Unit-testable and intentional.
 */
private fun requiresWebView(html: String): Boolean {
    return html.contains("<style", ignoreCase = true) ||
           html.contains("font-family", ignoreCase = true) ||
           html.contains("class=", ignoreCase = true) ||
           html.contains("id=", ignoreCase = true) ||
           html.contains("<link", ignoreCase = true) ||
           html.contains("@import", ignoreCase = true) ||
           html.contains("position:", ignoreCase = true) ||
           html.contains("display:", ignoreCase = true)
}

/**
 * Primary HTML renderer using HtmlCompat (recommended for 90% of cases)
 *
 * Advantages:
 * - Fast cold start
 * - Native Compose theming (respects dark mode)
 * - No lifecycle management
 * - Better accessibility
 * - Clickable links with LinkMovementMethod
 *
 * Supported HTML:
 * - <p>, <br>, <b>, <i>, <u>, <strong>, <em>
 * - <ul>, <ol>, <li>
 * - <a href="...">
 * - <h1> through <h6>
 * - Basic text formatting
 *
 * Theme Handling:
 * - Background: Transparent (inherits from parent)
 * - Text: onSurface color (auto dark/light)
 * - Links: primary color
 */
@Composable
private fun HtmlText(
    html: String,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                // Parse HTML with HtmlCompat
                val spanned = HtmlCompat.fromHtml(
                    html,
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                )
                text = spanned

                // Enable clickable links
                movementMethod = LinkMovementMethod.getInstance()

                // Apply Material theme colors
                setTextColor(textColor)
                setLinkTextColor(linkColor)

                // CRITICAL: Set transparent background to inherit theme
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                // Use system font (respects user font size settings)
                typeface = Typeface.DEFAULT
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.35f)

                // Proper padding for readability
                setPadding(0, 0, 0, 0)

                // Enable text selection
                setTextIsSelectable(true)

                Timber.d("HtmlText rendered ${spanned.length} characters with theming")
            }
        },
        update = { textView ->
            // Update when HTML or theme changes
            val spanned = HtmlCompat.fromHtml(
                html,
                HtmlCompat.FROM_HTML_MODE_COMPACT
            )
            textView.text = spanned
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
    )
}

/**
 * Fallback WebView renderer (only when complex CSS is detected OR theme issues occur)
 *
 * Use cases:
 * - Custom fonts specified in CSS
 * - Complex layouts with positioning
 * - External stylesheets
 * - When HtmlCompat has theme conflicts
 *
 * Advantages over HtmlCompat:
 * - Perfect theme control via CSS injection
 * - Full HTML/CSS support
 * - Better for large documents
 *
 * Downsides:
 * - Heavier lifecycle management
 * - More memory usage
 * - Security surface (JS enabled only for anchor navigation)
 */
@Composable
private fun HtmlWebView(
    html: String,
    modifier: Modifier = Modifier,
    anchors: List<HtmlAnchor> = emptyList(),
    scrollToAnchorId: String? = null,
    scrollRequestKey: Int = 0
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    // Convert Material colors to CSS hex format
    val backgroundHex = String.format("#%06X", 0xFFFFFF and surfaceColor.toArgb())
    val surfaceVariantHex = String.format("#%06X", 0xFFFFFF and surfaceVariantColor.toArgb())
    val textColorHex = String.format("#%06X", 0xFFFFFF and onSurfaceColor.toArgb())
    val linkColorHex = String.format("#%06X", 0xFFFFFF and primaryColor.toArgb())

    val anchoredHtml = remember(html, anchors) {
        if (anchors.isEmpty()) html else injectAnchorIds(html, anchors)
    }

    // Wrap HTML with Material theme-aware CSS
    val styledHtml = remember(
        anchoredHtml,
        backgroundHex,
        surfaceVariantHex,
        textColorHex,
        linkColorHex
    ) {
        buildThemedHtml(
            html = anchoredHtml,
            backgroundHex = backgroundHex,
            surfaceVariantHex = surfaceVariantHex,
            textColorHex = textColorHex,
            linkColorHex = linkColorHex,
            baseFontSizePx = 14
        )
    }

    var lastScrollRequestKey by remember { mutableStateOf(-1) }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            android.webkit.WebView(context).apply {
                // Required for in-document anchor jumps; content is local
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false

                // Enable zoom controls for accessibility
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                // Responsive layout
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NORMAL

                // Transparent background (CSS provides the actual background)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                Timber.d("HtmlWebView rendering with Material theme: bg=$backgroundHex, text=$textColorHex")
                loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Update when HTML or theme changes (e.g., dark mode toggle)
            webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)

            if (scrollRequestKey != lastScrollRequestKey) {
                lastScrollRequestKey = scrollRequestKey
                val anchorId = scrollToAnchorId
                if (anchorId.isNullOrBlank()) {
                    webView.evaluateJavascript("window.scrollTo(0, 0);", null)
                } else {
                    val safeId = anchorId.replace("'", "\\'")
                    webView.evaluateJavascript(
                        "var el = document.getElementById('$safeId'); if (el) { el.scrollIntoView(); }",
                        null
                    )
                }
            }
        }
    )
}

private fun buildThemedHtml(
    html: String,
    backgroundHex: String,
    surfaceVariantHex: String,
    textColorHex: String,
    linkColorHex: String,
    baseFontSizePx: Int
): String {
    val bodyFontSize = baseFontSizePx.coerceIn(14, 20)
    val codeFontSize = (bodyFontSize - 2).coerceAtLeast(12)
    val css = """
        :root { color-scheme: light dark; }
        * { box-sizing: border-box; }
        html, body {
            background-color: $backgroundHex !important;
            color: $textColorHex !important;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            font-size: ${bodyFontSize}px;
            line-height: 1.7;
            margin: 0;
            padding: 0;
            -webkit-text-size-adjust: 100%;
        }
        body { padding: 16px !important; }
        p, li, td, th { font-size: ${bodyFontSize}px !important; }
        a { color: $linkColorHex !important; text-decoration: none; }
        a:hover, a:active { text-decoration: underline; }
        p { margin: 0 0 16px 0; }
        h1, h2, h3, h4, h5, h6 {
            color: $textColorHex !important;
            margin: 20px 0 12px 0;
            font-weight: 600;
            line-height: 1.3;
        }
        h1 { font-size: 24px; }
        h2 { font-size: 20px; }
        h3 { font-size: 18px; }
        h4 { font-size: 16px; }
        h5, h6 { font-size: 15px; }
        ul, ol { margin: 0 0 16px 0; padding-left: 20px !important; }
        li { margin-bottom: 8px; }
        img, table { max-width: 100%; height: auto; }
        table { border-collapse: collapse; width: 100%; margin: 16px 0; }
        th, td {
            border: 1px solid rgba(128, 128, 128, 0.3);
            padding: 8px;
            text-align: left;
        }
        th { font-weight: 600; }
        code, pre {
            background-color: rgba(128, 128, 128, 0.12);
            padding: 2px 6px;
            border-radius: 4px;
            font-family: "Courier New", monospace;
            font-size: ${codeFontSize}px;
        }
        pre { padding: 12px; overflow-x: auto; }
        blockquote {
            border-left: 4px solid $linkColorHex;
            padding-left: 16px;
            margin: 16px 0;
            font-style: italic;
        }
        .container {
            max-width: 100% !important;
            width: 100% !important;
            margin: 0 auto !important;
            padding: 0 !important;
            background-color: transparent !important;
            border-radius: 0 !important;
            box-shadow: none !important;
        }
        .important, .contact-info {
            background-color: $surfaceVariantHex !important;
            border-left: 4px solid $linkColorHex !important;
        }
    """.trimIndent()

    val styleBlock = "<style>$css</style>"
    val viewportMeta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=yes\">"
    val headRegex = Regex("(?i)<head[^>]*>")
    val viewportRegex = Regex("(?i)<meta[^>]*name=[\"']viewport[\"'][^>]*>")
    val closingHeadRegex = Regex("(?i)</head>")

    return if (headRegex.containsMatchIn(html)) {
        var updated = html
        if (!viewportRegex.containsMatchIn(html)) {
            val headMatch = headRegex.find(updated)
            if (headMatch != null) {
                val insertAt = headMatch.range.last + 1
                updated = updated.substring(0, insertAt) + viewportMeta + updated.substring(insertAt)
            }
        }
        val closingHeadMatch = closingHeadRegex.find(updated)
        if (closingHeadMatch != null) {
            val insertAt = closingHeadMatch.range.first
            updated.substring(0, insertAt) + styleBlock + updated.substring(insertAt)
        } else {
            updated + styleBlock
        }
    } else {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            $viewportMeta
            $styleBlock
        </head>
        <body>
            $html
        </body>
        </html>
        """.trimIndent()
    }
}

data class HtmlAnchor(
    val id: String,
    val title: String,
    val level: Int,
    val hasId: Boolean
)

internal fun extractHtmlAnchors(
    html: String,
    minLevel: Int = 2,
    maxLevel: Int = 3
): List<HtmlAnchor> {
    val headingRegex = Regex("(?is)<h([1-6])([^>]*)>(.*?)</h\\1>")
    val idRegex = Regex("(?i)\\bid\\s*=\\s*[\"']([^\"']+)[\"']")
    val tagStripRegex = Regex("(?is)<[^>]+>")
    val whitespaceRegex = Regex("\\s+")
    val anchors = mutableListOf<HtmlAnchor>()
    var counter = 1

    for (match in headingRegex.findAll(html)) {
        val level = match.groupValues[1].toInt()
        if (level !in minLevel..maxLevel) {
            continue
        }

        val attributes = match.groupValues[2]
        val rawText = match.groupValues[3]
        val title = whitespaceRegex.replace(tagStripRegex.replace(rawText, ""), " ").trim()
        if (title.isEmpty()) {
            continue
        }

        val existingId = idRegex.find(attributes)?.groupValues?.get(1)
        val id = existingId ?: "section-$counter"
        anchors.add(
            HtmlAnchor(
                id = id,
                title = title,
                level = level,
                hasId = existingId != null
            )
        )
        counter++
    }

    return anchors
}

internal fun injectAnchorIds(
    html: String,
    anchors: List<HtmlAnchor>
): String {
    if (anchors.isEmpty()) {
        return html
    }

    val headingRegex = Regex("(?is)<h([1-6])([^>]*)>")
    val idRegex = Regex("(?i)\\bid\\s*=")
    val sb = StringBuilder()
    var lastIndex = 0
    var anchorIndex = 0

    for (match in headingRegex.findAll(html)) {
        if (anchorIndex >= anchors.size) {
            break
        }

        sb.append(html.substring(lastIndex, match.range.first))

        val level = match.groupValues[1].toInt()
        val anchor = anchors[anchorIndex]
        val shouldApply = level == anchor.level
        val originalTag = match.value
        val updatedTag = if (shouldApply && !anchor.hasId && !idRegex.containsMatchIn(match.groupValues[2])) {
            originalTag.dropLast(1) + " id=\"" + anchor.id + "\">"
        } else {
            originalTag
        }

        sb.append(updatedTag)
        lastIndex = match.range.last + 1

        if (shouldApply) {
            anchorIndex++
        }
    }

    sb.append(html.substring(lastIndex))
    return sb.toString()
}
