package com.example.liftrix.ui.common.event

/**
 * Base interface for all ViewModel events in the MVI pattern.
 * 
 * This interface serves as the foundation for event-driven communication between
 * UI components and ViewModels, enabling consistent event handling across the application.
 * 
 * Key benefits:
 * - Type-safe event handling with sealed class hierarchies
 * - Consistent event processing patterns across all ViewModels
 * - Clear separation between user actions and internal state updates
 * - Enhanced testability through predictable event flows
 * - Supports complex form interactions and simple user actions
 * 
 * Usage:
 * ```kotlin
 * sealed class MyScreenEvent : ViewModelEvent {
 *     object LoadData : MyScreenEvent()
 *     data class UpdateField(val value: String) : MyScreenEvent()
 *     data class SubmitForm(val data: FormData) : MyScreenEvent()
 * }
 * 
 * class MyViewModel : BaseViewModel<MyUiState, MyScreenEvent>() {
 *     override fun onEvent(event: MyScreenEvent) {
 *         when (event) {
 *             is MyScreenEvent.LoadData -> loadData()
 *             is MyScreenEvent.UpdateField -> updateField(event.value)
 *             is MyScreenEvent.SubmitForm -> submitForm(event.data)
 *         }
 *     }
 * }
 * ```
 * 
 * Event Design Guidelines:
 * - Use object declarations for simple events without parameters
 * - Use data classes for events that carry data
 * - Group related events under feature-specific sealed classes
 * - Name events clearly to indicate user intent (e.g., LoadData, SubmitForm)
 * - Avoid exposing internal state management concerns in events
 */
interface ViewModelEvent

/**
 * Common event types that can be used across multiple ViewModels.
 * 
 * These events represent frequent patterns found throughout the application
 * and provide consistent naming and structure for common user interactions.
 */
sealed class CommonEvent : ViewModelEvent {
    
    /**
     * Event for refreshing data from remote sources.
     * Typically triggers a full data reload with loading states.
     */
    object Refresh : CommonEvent()
    
    /**
     * Event for retrying a failed operation.
     * Should be used when an error state provides retry functionality.
     */
    object Retry : CommonEvent()
    
    /**
     * Event for dismissing error messages or dialogs.
     * Clears error state and returns to normal operation.
     */
    object DismissError : CommonEvent()
    
    /**
     * Event for navigating back to the previous screen.
     * Triggers navigation back action in the ViewModel.
     */
    object NavigateBack : CommonEvent()
    
    /**
     * Event for loading more data in paginated lists.
     * Triggers loading of additional items without clearing existing data.
     */
    object LoadMore : CommonEvent()
    
    /**
     * Event for clearing search or filter state.
     * Resets search/filter criteria and reloads original data.
     */
    object ClearFilters : CommonEvent()
}

/**
 * Event types specific to form interactions and data input.
 * 
 * These events handle complex form scenarios where validation,
 * field dependencies, and multi-step processes are involved.
 */
sealed class FormEvent : ViewModelEvent {
    
    /**
     * Event for updating a single form field.
     * 
     * @property fieldName The name/identifier of the field being updated
     * @property value The new value for the field
     * @property shouldValidate Whether to trigger validation immediately
     */
    data class UpdateField(
        val fieldName: String,
        val value: Any?,
        val shouldValidate: Boolean = true
    ) : FormEvent()
    
    /**
     * Event for validating the entire form.
     * Triggers comprehensive validation of all form fields.
     */
    object ValidateForm : FormEvent()
    
    /**
     * Event for submitting the form.
     * Should include all necessary form data for processing.
     * 
     * @property formData The complete form data ready for submission
     */
    data class SubmitForm(val formData: Map<String, Any?>) : FormEvent()
    
    /**
     * Event for resetting the form to its initial state.
     * Clears all field values and validation errors.
     */
    object ResetForm : FormEvent()
    
    /**
     * Event for saving form as draft without validation.
     * Allows users to save progress without completing the form.
     */
    object SaveDraft : FormEvent()
}

/**
 * Event types for search and filtering functionality.
 * 
 * These events handle search queries, filter applications,
 * and result management for search-enabled screens.
 */
sealed class SearchEvent : ViewModelEvent {
    
    /**
     * Event for updating the search query.
     * 
     * @property query The search query string
     * @property shouldTriggerSearch Whether to immediately trigger search
     */
    data class UpdateQuery(
        val query: String,
        val shouldTriggerSearch: Boolean = true
    ) : SearchEvent()
    
    /**
     * Event for executing search with current query.
     * Triggers search operation with existing query parameters.
     */
    object ExecuteSearch : SearchEvent()
    
    /**
     * Event for applying filters to search results.
     * 
     * @property filters Map of filter criteria to apply
     */
    data class ApplyFilters(val filters: Map<String, Any>) : SearchEvent()
    
    /**
     * Event for clearing search query and results.
     * Resets search state to initial empty state.
     */
    object ClearSearch : SearchEvent()
    
    /**
     * Event for selecting a search suggestion.
     * 
     * @property suggestion The selected search suggestion
     */
    data class SelectSuggestion(val suggestion: String) : SearchEvent()
}

/**
 * Event types for list management and selection.
 * 
 * These events handle item selection, multi-selection,
 * and list manipulation operations.
 */
sealed class ListEvent : ViewModelEvent {
    
    /**
     * Event for selecting a single item in a list.
     * 
     * @property itemId The unique identifier of the selected item
     * @property item The selected item data (optional)
     */
    data class SelectItem(
        val itemId: String,
        val item: Any? = null
    ) : ListEvent()
    
    /**
     * Event for toggling selection of an item.
     * Handles both selection and deselection in multi-select scenarios.
     * 
     * @property itemId The unique identifier of the item to toggle
     */
    data class ToggleSelection(val itemId: String) : ListEvent()
    
    /**
     * Event for selecting all items in a list.
     * Used in multi-select scenarios with "select all" functionality.
     */
    object SelectAll : ListEvent()
    
    /**
     * Event for clearing all selections.
     * Deselects all currently selected items.
     */
    object ClearSelection : ListEvent()
    
    /**
     * Event for deleting selected items.
     * Triggers deletion of all currently selected items.
     * 
     * @property selectedIds List of item IDs to delete
     */
    data class DeleteSelected(val selectedIds: List<String>) : ListEvent()
    
    /**
     * Event for reordering items in a list.
     * 
     * @property fromIndex Original position of the item
     * @property toIndex New position for the item
     */
    data class ReorderItem(
        val fromIndex: Int,
        val toIndex: Int
    ) : ListEvent()
}

/**
 * Extension functions for event validation and processing.
 */

/**
 * Validates that a FormEvent.UpdateField has a non-null field name.
 * 
 * @return true if the field name is valid, false otherwise
 */
fun FormEvent.UpdateField.isValid(): Boolean = fieldName.isNotBlank()

/**
 * Validates that a SearchEvent.UpdateQuery has a valid query string.
 * 
 * @return true if the query is valid for searching, false otherwise
 */
fun SearchEvent.UpdateQuery.isValidQuery(): Boolean = query.trim().length >= 2

/**
 * Gets the trimmed query string from a SearchEvent.UpdateQuery.
 * 
 * @return Trimmed query string
 */
fun SearchEvent.UpdateQuery.getTrimmedQuery(): String = query.trim()

/**
 * Checks if a ListEvent involves multiple items.
 * 
 * @return true if the event affects multiple items, false otherwise
 */
fun ListEvent.affectsMultipleItems(): Boolean = when (this) {
    is ListEvent.SelectAll, 
    is ListEvent.ClearSelection,
    is ListEvent.DeleteSelected -> true
    else -> false
}