// Simple test to verify ErrorContextBuilder fix
import com.example.liftrix.core.error.ErrorContextBuilder

fun main() {
    println("Testing ErrorContextBuilder fix...")
    
    // Create builder and add some context
    val builder = ErrorContextBuilder()
    builder.withUser("user_123")
    
    // Build twice
    val context1 = builder.build()
    val context2 = builder.build()
    
    // Check if they are different objects but equal content
    val areObjectsDifferent = context1 !== context2
    val areContentsEqual = context1 == context2
    
    println("Objects are different instances: $areObjectsDifferent")
    println("Contents are equal: $areContentsEqual")
    println("Context1 timestamp: ${context1["timestamp"]}")
    println("Context2 timestamp: ${context2["timestamp"]}")
    
    // Test should pass now
    if (areObjectsDifferent && areContentsEqual) {
        println("✅ Test PASSED - Fix is working correctly")
    } else {
        println("❌ Test FAILED - Fix needs more work")
    }
}