package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.StatusItem
import com.example.service.StatusStorageManager
import com.example.util.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatusSaverViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("status_saver_settings", Context.MODE_PRIVATE)

    // Screen navigation state
    private val _currentRoute = MutableStateFlow("splash")
    val currentRoute: StateFlow<String> = _currentRoute.asStateFlow()

    // Lang State
    private val _selectedLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    // Status Lists
    private val _whatsappStatuses = MutableStateFlow<List<StatusItem>>(emptyList())
    val whatsappStatuses: StateFlow<List<StatusItem>> = _whatsappStatuses.asStateFlow()

    private val _savedStatuses = MutableStateFlow<List<StatusItem>>(emptyList())
    val savedStatuses: StateFlow<List<StatusItem>> = _savedStatuses.asStateFlow()

    // SAF permissions
    private val _waFolderUriStr = MutableStateFlow<String?>(null)
    val waFolderUriStr: StateFlow<String?> = _waFolderUriStr.asStateFlow()

    private val _wabFolderUriStr = MutableStateFlow<String?>(null)
    val wabFolderUriStr: StateFlow<String?> = _wabFolderUriStr.asStateFlow()

    // Storage runtime permission (Android 10 and below)
    private val _storagePermissionGranted = MutableStateFlow(true)
    val storagePermissionGranted: StateFlow<Boolean> = _storagePermissionGranted.asStateFlow()

    // UI parameters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Settings Parameters
    private val _autoSaveEnabled = MutableStateFlow(false)
    val autoSaveEnabled: StateFlow<Boolean> = _autoSaveEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    // Rating Popup state
    private val _showRatingPopup = MutableStateFlow(false)
    val showRatingPopup: StateFlow<Boolean> = _showRatingPopup.asStateFlow()

    // Tutorial Dialogs (index indicates: 1, 2, 3 or null if hidden)
    private val _activeTutorialStep = MutableStateFlow<Int?>(null)
    val activeTutorialStep: StateFlow<Int?> = _activeTutorialStep.asStateFlow()

    // Media viewer detail status
    private val _selectedMediaItem = MutableStateFlow<StatusItem?>(null)
    val selectedMediaItem: StateFlow<StatusItem?> = _selectedMediaItem.asStateFlow()

    init {
        // Load initial values from SharedPreferences
        val langCode = prefs.getString("lang_code", AppLanguage.ENGLISH.code) ?: AppLanguage.ENGLISH.code
        _selectedLanguage.value = AppLanguage.fromCode(langCode)

        _autoSaveEnabled.value = prefs.getBoolean("settings_auto_save", false)
        _notificationsEnabled.value = prefs.getBoolean("settings_notifications", true)

        if (Build.VERSION.SDK_INT <= 29) {
            val check = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            _storagePermissionGranted.value = check
        } else {
            _storagePermissionGranted.value = true
        }

        _waFolderUriStr.value = StatusStorageManager.getPersistedUriPermission(context, false)
        _wabFolderUriStr.value = StatusStorageManager.getPersistedUriPermission(context, true)

        // Load downloads and statuses
        loadAllStatuses()
    }

    fun setRoute(route: String) {
        _currentRoute.value = route
    }

    fun completeSplash() {
        val onboardingDone = prefs.getBoolean("onboarding_complete", false)
        if (onboardingDone) {
            _currentRoute.value = "home"
        } else {
            _currentRoute.value = "welcome"
        }
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        _currentRoute.value = "home"
        
        // Show tutorial dialog index 1 when first opening home
        val tutorialsDismissed = prefs.getBoolean("tutorials_dismissed", false)
        if (!tutorialsDismissed) {
            _activeTutorialStep.value = 1
        }
    }

    fun setLanguage(language: AppLanguage) {
        _selectedLanguage.value = language
        prefs.edit().putString("lang_code", language.code).apply()
    }

    fun toggleAutoSave(enabled: Boolean) {
        _autoSaveEnabled.value = enabled
        prefs.edit().putBoolean("settings_auto_save", enabled).apply()
    }

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("settings_notifications", enabled).apply()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectMediaItem(item: StatusItem?) {
        _selectedMediaItem.value = item
    }

    fun showRatingDialog() {
        _showRatingPopup.value = true
    }

    fun dismissRatingDialog() {
        _showRatingPopup.value = false
    }

    fun grantWhatsAppFolder(uriStr: String, isBusiness: Boolean) {
        if (isBusiness) {
            _wabFolderUriStr.value = uriStr
        } else {
            _waFolderUriStr.value = uriStr
        }
        loadAllStatuses()
    }

    fun updateStoragePermission(granted: Boolean) {
        _storagePermissionGranted.value = granted
        loadAllStatuses()
    }

    fun removeWhatsAppFolder(isBusiness: Boolean) {
        if (isBusiness) {
            _wabFolderUriStr.value = null
            StatusStorageManager.clearPersistedUriPermission(context, true)
        } else {
            _waFolderUriStr.value = null
            StatusStorageManager.clearPersistedUriPermission(context, false)
        }
        loadAllStatuses()
    }

    fun loadAllStatuses() {
        viewModelScope.launch {
            _isRefreshing.value = true
            
            // 1. Fetch saved downloads
            val savedList = StatusStorageManager.loadSavedStatuses(context)
            _savedStatuses.value = savedList

            // 2. Fetch SAF folders if granted
            val loadedWAs = mutableListOf<StatusItem>()

            // On Android 10 and below, if standard storage permission is granted, load files directly
            if (Build.VERSION.SDK_INT <= 29 && _storagePermissionGranted.value) {
                val fsWA = StatusStorageManager.loadStatusesFromFileSystem(context, false)
                val fsWAB = StatusStorageManager.loadStatusesFromFileSystem(context, true)
                loadedWAs.addAll(fsWA)
                loadedWAs.addAll(fsWAB)
            } else {
                val waUri = _waFolderUriStr.value
                if (waUri != null) {
                    val statuses = StatusStorageManager.loadStatusesFromTree(context, waUri, false)
                    loadedWAs.addAll(statuses)
                }

                val wabUri = _wabFolderUriStr.value
                if (wabUri != null) {
                    val statuses = StatusStorageManager.loadStatusesFromTree(context, wabUri, true)
                    loadedWAs.addAll(statuses)
                }
            }

            // 3. Fallback to high quality mock data if real statuses are empty
            if (loadedWAs.isEmpty()) {
                // Combine mock images and videos for testing
                // Also update item: isSaved if it is in savedStatuses list
                val allMocks = (StatusStorageManager.MockImages + StatusStorageManager.MockVideos).map { mock ->
                    val isDownloaded = savedList.any { it.fileName == mock.fileName }
                    mock.copy(isSaved = isDownloaded)
                }
                _whatsappStatuses.value = allMocks
            } else {
                // If real SAF list exists, update the downloaded flags
                val updatedReal = loadedWAs.map { item ->
                    val isDownloaded = savedList.any { it.fileName == item.fileName }
                    item.copy(isSaved = isDownloaded)
                }
                _whatsappStatuses.value = updatedReal
            }

            // 4. Handle auto-save if enabled and new items found
            if (_autoSaveEnabled.value) {
                _whatsappStatuses.value.forEach { item ->
                    if (!item.isSaved) {
                        saveStatus(item)
                    }
                }
            }

            _isRefreshing.value = false
        }
    }

    fun saveStatus(item: StatusItem, onCompleted: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = StatusStorageManager.saveStatusFile(context, item)
            if (success) {
                // Refresh list
                loadAllStatuses()
            }
            onCompleted?.invoke(success)
        }
    }

    fun deleteSavedStatus(item: StatusItem, onCompleted: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = StatusStorageManager.deleteSavedFile(context, item)
            if (success) {
                loadAllStatuses()
            }
            onCompleted?.invoke(success)
        }
    }

    fun clearAllDownloads(onCompleted: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = StatusStorageManager.clearAllSavedFiles(context)
            if (success) {
                loadAllStatuses()
            }
            onCompleted?.invoke(success)
        }
    }

    // Tutorial Flow management
    fun nextTutorialStep(dontShowAgain: Boolean) {
        val currentStep = _activeTutorialStep.value ?: return
        if (dontShowAgain) {
            prefs.edit().putBoolean("tutorials_dismissed", true).apply()
            _activeTutorialStep.value = null
        } else {
            if (currentStep < 3) {
                _activeTutorialStep.value = currentStep + 1
            } else {
                _activeTutorialStep.value = null
            }
        }
    }

    fun dismissTutorials() {
        _activeTutorialStep.value = null
    }

    fun openTutorial() {
        _activeTutorialStep.value = 1
    }
}
