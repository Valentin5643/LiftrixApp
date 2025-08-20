// QUICK FIX: Replace simulated validation with real validation in UsernameChangeScreen.kt

// REMOVE lines 67-78 in UsernameChangeScreen.kt:
/*
suspend fun checkAvailability(username: String): UsernameAvailability {
    delay(800) // Simulate network delay
    
    // Simulate some usernames being taken
    val takenUsernames = setOf("john", "jane", "user123", "test123", "admin123")
    
    return if (takenUsernames.contains(username.lowercase())) {
        UsernameAvailability.Unavailable("Username is already taken")
    } else {
        UsernameAvailability.Available
    }
}
*/

// REPLACE with direct ViewModel call:
LaunchedEffect(newUsername) {
    if (newUsername.isNotEmpty()) {
        val validation = UsernameValidator.validateUsername(newUsername)
        availability = validation
        
        if (validation is UsernameAvailability.Checking) {
            delay(1000) // Debounce for 1 second
            if (newUsername.isNotEmpty()) {
                // ✅ USE REAL VALIDATION from ViewModel
                viewModel.onEvent(AccountManagementEvent.CheckUsernameAvailability(newUsername))
                
                // Map ViewModel state to UI state
                val realValidation = viewModel.uiState.value.usernameValidation
                availability = when (realValidation) {
                    is UsernameValidation.Available -> UsernameAvailability.Available
                    is UsernameValidation.Invalid -> UsernameAvailability.Unavailable(realValidation.message)
                    is UsernameValidation.Checking -> UsernameAvailability.Checking
                    is UsernameValidation.None -> UsernameAvailability.None
                }
            }
        }
    } else {
        availability = UsernameAvailability.None
        suggestions = emptyList()
    }
}