// Debug test to check enum names
import com.example.liftrix.domain.model.analytics.AnalyticsWidget

fun main() {
    println("Available widget names:")
    AnalyticsWidget.values().forEach { widget ->
        println("${widget.name} -> ${widget.displayName}")
    }
}