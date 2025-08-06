import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll

fun main() {
    mockkStatic(Clock::class)
    every { Clock.System.now() } returns Instant.parse("2023-07-16T10:30:00Z")
    
    println("Clock.System.now() = ${Clock.System.now()}")
    
    unmockkAll()
}