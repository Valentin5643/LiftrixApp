package com.example.liftrix.ui.folder.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.folder.GetFoldersUseCase
import com.example.liftrix.ui.folder.state.FolderUiState
import com.example.liftrix.ui.folder.state.FolderSelectionData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Folder Selection Screen
 * 
 * Manages folder selection state during template creation workflow with comprehensive
 * search functionality, user preference handling, and seamless integration with
 * the template creation process.
 * 
 * Key Features:
 * - Real-time folder loading with user authentication
 * - Debounced search with 300ms delay for optimal performance
 * - Single-selection state management with visual feedback
 * - Template context awareness for workflow integration
 * - Error handling with retry mechanisms
 * - Performance optimized with proper state sharing strategies
 * 
 * Architecture:
 * - Follows MVVM pattern with Clean Architecture principles
 * - Uses StateFlow for reactive UI updates
 * - Integrates with domain use cases for business logic
 * - Maintains separation of concerns between UI and domain layers
 * 
 * @param getFoldersUseCase Use case for retrieving user folders
 * @param authRepository Repository for user authentication state
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class FolderSelectionViewModel @Inject constructor(
    private val getFoldersUseCase: GetFoldersUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    // Private mutable state
    private val _searchQuery = MutableStateFlow("")
    private val _selectedFolderId = MutableStateFlow<FolderId?>(null)
    private val _templateId = MutableStateFlow<String?>(null)
    
    // Public observable state
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val selectedFolderId: StateFlow<FolderId?> = _selectedFolderId.asStateFlow()
    
    // Main UI state combining folder data and search
    private val _uiState = MutableStateFlow<UiState<FolderSelectionData>>(UiState.Loading<FolderSelectionData>())
    val uiState: StateFlow<UiState<FolderSelectionData>> = _uiState.asStateFlow()
    
    init {
        // Set up search flow
        viewModelScope.launch {
            _searchQuery.debounce(300).distinctUntilChanged()
                .collect { query ->
                    loadFolders(query)
                }
        }
    }
    
    /**
     * Initialize selection with template context
     * 
     * @param templateId Optional template ID for context-aware folder selection
     */
    fun initializeSelection(templateId: String?) {
        Timber.d("FolderSelectionViewModel: Initializing selection with templateId: $templateId")
        _templateId.value = templateId
        _selectedFolderId.value = null // Reset selection on initialization
        _searchQuery.value = "" // Reset search on initialization
        
        // Start initial load
        loadFolders("")
    }
    
    /**
     * Update search query with immediate UI feedback
     * 
     * @param query New search query string
     */
    fun updateSearchQuery(query: String) {
        Timber.d("FolderSelectionViewModel: Updating search query: '$query'")
        _searchQuery.value = query
    }
    
    /**
     * Clear search query
     */
    fun clearSearchQuery() {
        Timber.d("FolderSelectionViewModel: Clearing search query")
        _searchQuery.value = ""
    }
    
    /**
     * Select a folder for template assignment
     * 
     * @param folderId The ID of the folder to select
     */
    fun selectFolder(folderId: FolderId) {
        Timber.d("FolderSelectionViewModel: Selecting folder: ${folderId.value}")
        _selectedFolderId.value = folderId
    }
    
    /**
     * Clear folder selection
     */
    fun clearSelection() {
        Timber.d("FolderSelectionViewModel: Clearing folder selection")
        _selectedFolderId.value = null
    }
    
    /**
     * Load folders with optional search filtering
     */
    private fun loadFolders(searchQuery: String) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading<FolderSelectionData>()
                
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _uiState.value = UiState.Error<FolderSelectionData>(
                        LiftrixError.AuthenticationError("User not authenticated")
                    )
                    return@launch
                }
                
                loadFoldersWithSearch(userId, searchQuery, _templateId.value)
                    .collect { result ->
                        _uiState.value = result
                    }
            } catch (exception: Exception) {
                Timber.e(exception, "FolderSelectionViewModel: Error loading folders")
                _uiState.value = UiState.Error<FolderSelectionData>(
                    LiftrixError.DataRetrievalError("Failed to load folders: ${exception.message}")
                )
            }
        }
    }
    
    /**
     * Retry loading folders after error
     */
    fun retryLoadFolders() {
        Timber.d("FolderSelectionViewModel: Retrying folder load")
        loadFolders(_searchQuery.value)
    }
    
    /**
     * Load folders with optional search filtering
     * 
     * @param userId Current user ID
     * @param searchQuery Optional search query for filtering
     * @param templateId Optional template ID for context
     * @return Flow of UiState with folder selection data
     */
    private fun loadFoldersWithSearch(
        userId: String,
        searchQuery: String,
        templateId: String?
    ): Flow<UiState<FolderSelectionData>> {
        Timber.d("FolderSelectionViewModel: Loading folders for user: $userId, query: '$searchQuery'")
        
        return getFoldersUseCase(GetFoldersUseCase.GetFoldersInput(userId))
            .map { result ->
                result.fold(
                    onSuccess = { folders ->
                        Timber.d("FolderSelectionViewModel: Successfully loaded ${folders.size} folders")
                        
                        // Filter folders based on search query
                        val filteredFolders = if (searchQuery.isBlank()) {
                            folders
                        } else {
                            folders.filter { folder ->
                                folder.name.value.contains(searchQuery, ignoreCase = true)
                            }
                        }
                        
                        Timber.d("FolderSelectionViewModel: Filtered to ${filteredFolders.size} folders")
                        
                        // Handle empty state
                        if (folders.isEmpty()) {
                            UiState.Empty<FolderSelectionData>(
                                message = "No folders created yet"
                            )
                        } else {
                            // Create selection data
                            val selectionData = FolderSelectionData(
                                availableFolders = filteredFolders,
                                selectedFolderId = _selectedFolderId.value,
                                selectionPurpose = "Select folder for workout template",
                                canCreateNew = true,
                                excludedFolderIds = emptySet() // No exclusions for template assignment
                            )
                            
                            UiState.Success(selectionData)
                        }
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "FolderSelectionViewModel: Failed to load folders")
                        UiState.Error<FolderSelectionData>(
                            LiftrixError.DataRetrievalError("Failed to load folders: ${exception.message}")
                        )
                    }
                )
            }
            .catch { exception ->
                Timber.e(exception, "FolderSelectionViewModel: Error in folder loading flow")
                emit(UiState.Error<FolderSelectionData>(
                    LiftrixError.DataRetrievalError("Error in folder loading: ${exception.message}")
                ))
            }
    }
    
    /**
     * Get current selection state for external observers
     * 
     * @return Current selected folder ID or null
     */
    fun getCurrentSelection(): FolderId? = _selectedFolderId.value
    
    /**
     * Check if a specific folder is currently selected
     * 
     * @param folderId Folder ID to check
     * @return True if the folder is selected
     */
    fun isFolderSelected(folderId: FolderId): Boolean = _selectedFolderId.value == folderId
    
    /**
     * Get template context ID
     * 
     * @return Current template ID or null
     */
    fun getTemplateId(): String? = _templateId.value
    
    /**
     * Validate current selection state
     * 
     * @return True if selection is valid for submission
     */
    fun isSelectionValid(): Boolean = _selectedFolderId.value != null
    
    /**
     * Get current search query
     * 
     * @return Current search query string
     */
    fun getCurrentSearchQuery(): String = _searchQuery.value
    
    /**
     * Handle cleanup when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        Timber.d("FolderSelectionViewModel: ViewModel cleared")
    }
}