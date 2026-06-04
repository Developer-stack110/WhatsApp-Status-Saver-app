package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.StatusItem
import com.example.util.AppLanguage
import com.example.util.Localization
import com.example.viewmodel.StatusSaverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: StatusSaverViewModel,
    onRequestSAF: (isBusiness: Boolean) -> Unit,
    onRequestStoragePermission: () -> Unit
) {
    val language by viewModel.selectedLanguage.collectAsState()
    val route by viewModel.currentRoute.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Home, 1 = Downloads, 2 = Settings
    var homeMediaFilter by remember { mutableStateOf(0) } // 0 = Images, 1 = Videos
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val allStatuses by viewModel.whatsappStatuses.collectAsState()
    val savedStatuses by viewModel.savedStatuses.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val showRatingModal by viewModel.showRatingPopup.collectAsState()
    val activeTutorialStep by viewModel.activeTutorialStep.collectAsState()
    val selectedMedia by viewModel.selectedMediaItem.collectAsState()

    val context = LocalContext.current

    // Dialog state for clear all files
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var privacyPolicyOpen by remember { mutableStateOf(false) }

    // Sequential onboarding tutorial steps
    if (activeTutorialStep != null) {
        TutorialDialog(step = activeTutorialStep!!, viewModel = viewModel)
    }

    // Interactive rating stars dialogue
    if (showRatingModal) {
        RatingPopupDialog(viewModel = viewModel)
    }

    // Clear all files validation model
    if (showClearAllConfirm) {
        ConfirmationDialog(
            title = Localization.getString("dialog_clear_all_title", language),
            desc = Localization.getString("dialog_clear_all_desc", language),
            confirmBtnText = Localization.getString("dialog_delete", language),
            cancelBtnText = Localization.getString("dialog_cancel", language),
            onConfirm = {
                showClearAllConfirm = false
                viewModel.clearAllDownloads { success ->
                    if (success) {
                        Toast.makeText(context, Localization.getString("toast_deleted", language), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showClearAllConfirm = false }
        )
    }

    // Privacy Policy custom detail banner popup
    if (privacyPolicyOpen) {
        AlertDialog(
            onDismissRequest = { privacyPolicyOpen = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 20.dp.value.sp) },
            text = {
                Column(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Status Saver 2026 respects user privacy. We do NOT host servers or collect personal metrics.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "The application uses local Storage Access Framework (SAF) folder references to scan and detect view-concluded media statuses within WhatsApp or WhatsApp Business directories strictly offline inside your sandbox.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Saved files are safely formatted and written into local/public pictures or movie galleries under your directory authority. No data leaves your mobile environment.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { privacyPolicyOpen = false }) {
                    Text("Got It")
                }
            }
        )
    }

    // Primary Scaffold Structure
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Top Header ToolBar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = when (activeTab) {
                                0 -> Localization.getString("welcome_title", language)
                                1 -> Localization.getString("nav_downloads", language)
                                else -> Localization.getString("nav_settings", language)
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (activeTab == 0) {
                            Text(
                                text = "WhatsApp & Business companion",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rating option button
                        IconButton(
                            onClick = { viewModel.showRatingDialog() },
                            modifier = Modifier.testTag("toolbar_rate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Reviews and rating",
                                tint = Color(0xFFFFC107)
                            )
                        }

                        // Refresh engine button
                        IconButton(
                            onClick = {
                                viewModel.loadAllStatuses()
                                Toast.makeText(context, Localization.getString("toast_refreshed", language), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("toolbar_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }



                // Sub-tabs based on chosen active Tab
                if (activeTab == 0 || activeTab == 1) {
                    val currentFilter = if (activeTab == 0) homeMediaFilter else homeMediaFilter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TabBadgeRowItem(
                            title = Localization.getString("tab_images", language),
                            isSelected = homeMediaFilter == 0,
                            imageVector = Icons.Default.Image,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_photos"),
                            onClick = { homeMediaFilter = 0 }
                        )

                        TabBadgeRowItem(
                            title = Localization.getString("tab_videos", language),
                            isSelected = homeMediaFilter == 1,
                            imageVector = Icons.Default.PlayCircleOutline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_movies"),
                            onClick = { homeMediaFilter = 1 }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.GridView, contentDescription = "Home") },
                    label = { Text(Localization.getString("nav_home", language)) },
                    modifier = Modifier.testTag("nav_home_btn")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.FolderZip, contentDescription = "Downloads") },
                    label = { Text(Localization.getString("nav_downloads", language)) },
                    modifier = Modifier.testTag("nav_downloads_btn")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(Localization.getString("nav_settings", language)) },
                    modifier = Modifier.testTag("nav_settings_btn")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                0 -> {
                    // Home Statuses Grid list
                    val isVideo = homeMediaFilter == 1
                    val filteredStatuses = allStatuses.filter {
                        it.isVideo == isVideo && (searchQuery.isEmpty() || it.fileName.contains(searchQuery, ignoreCase = true))
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // SAF Setup / Permission Warning banners
                        val waGranted = viewModel.waFolderUriStr.collectAsState().value != null
                        val wabGranted = viewModel.wabFolderUriStr.collectAsState().value != null
                        val storageGranted = viewModel.storagePermissionGranted.collectAsState().value
                        val isAndroid10OrBelow = android.os.Build.VERSION.SDK_INT <= 29

                        if (isAndroid10OrBelow) {
                            if (!storageGranted) {
                                StoragePermissionBanner(
                                    language = language,
                                    onRequestPermission = onRequestStoragePermission
                                )
                            }
                        } else {
                            if (!waGranted || !wabGranted) {
                                PermissionHelperBanner(
                                    waGranted = waGranted,
                                    wabGranted = wabGranted,
                                    language = language,
                                    onRequestStandard = { onRequestSAF(false) },
                                    onRequestBusiness = { onRequestSAF(true) }
                                )
                            }
                        }

                        if (filteredStatuses.isEmpty()) {
                            StatusEmptyState(
                                message = Localization.getString("empty_statuses", language),
                                onGuide = { viewModel.openTutorial() }
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("home_grid")
                            ) {
                                items(filteredStatuses) { item ->
                                    StatusCard(
                                        status = item,
                                        language = language,
                                        onPreview = { viewModel.selectMediaItem(item) },
                                        onSave = {
                                            viewModel.saveStatus(item) { success ->
                                                if (success) {
                                                    Toast.makeText(context, Localization.getString("toast_saved", language), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, Localization.getString("toast_save_err", language), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onShare = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = if (item.isVideo) "video/*" else "image/*"
                                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uriString))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Status"))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Downloads Screen
                    val isVideo = homeMediaFilter == 1
                    val filteredSaved = savedStatuses.filter {
                        it.isVideo == isVideo && (searchQuery.isEmpty() || it.fileName.contains(searchQuery, ignoreCase = true))
                    }

                    if (filteredSaved.isEmpty()) {
                        StatusEmptyState(
                            message = Localization.getString("empty_downloads", language),
                            onGuide = { activeTab = 0 }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("downloads_grid")
                        ) {
                            items(filteredSaved) { item ->
                                StatusCard(
                                    status = item,
                                    language = language,
                                    onPreview = { viewModel.selectMediaItem(item) },
                                    onSave = {}, // Already saved
                                    onShare = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = if (item.isVideo) "video/*" else "image/*"
                                            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uriString))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Status"))
                                    },
                                    onDelete = {
                                        viewModel.deleteSavedStatus(item) { success ->
                                            if (success) {
                                                Toast.makeText(context, Localization.getString("toast_deleted", language), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Settings Panel Screen
                    val autoSave by viewModel.autoSaveEnabled.collectAsState()
                    val notifyOn by viewModel.notificationsEnabled.collectAsState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Section General
                        Text(
                            text = Localization.getString("settings_general", language),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SettingsSwitchRow(
                            title = Localization.getString("settings_auto_save", language),
                            subtitle = "Downloads recently watched statuses in background.",
                            icon = Icons.Default.SaveAlt,
                            checked = autoSave,
                            onCheckedChange = { viewModel.toggleAutoSave(it) },
                            modifier = Modifier.testTag("switch_auto_save")
                        )

                        SettingsSwitchRow(
                            title = Localization.getString("settings_notifications", language),
                            subtitle = "Displays alerts for newly watched status availability.",
                            icon = Icons.Default.NotificationsActive,
                            checked = notifyOn,
                            onCheckedChange = { viewModel.toggleNotifications(it) },
                            modifier = Modifier.testTag("switch_notifications")
                        )

                        SettingsActionRow(
                            title = Localization.getString("settings_clear_all", language),
                            subtitle = "Deletes all downloaded statuses permanently.",
                            icon = Icons.Default.FolderDelete,
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { showClearAllConfirm = true },
                            modifier = Modifier.testTag("action_clear_all")
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Section Support
                        Text(
                            text = Localization.getString("settings_support", language),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SettingsActionRow(
                            title = Localization.getString("settings_how_to_use", language),
                            subtitle = "Review the 3-step guide on how to save items.",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            onClick = { viewModel.openTutorial() },
                            modifier = Modifier.testTag("action_how_to_use")
                        )

                        SettingsActionRow(
                            title = Localization.getString("lang_title", language),
                            subtitle = "Modify application visual labels language.",
                            icon = Icons.Default.Translate,
                            onClick = { viewModel.setRoute("language") },
                            modifier = Modifier.testTag("action_lang_set")
                        )

                        SettingsActionRow(
                            title = Localization.getString("settings_privacy", language),
                            subtitle = "View database, folder rights and system policy.",
                            icon = Icons.Default.Security,
                            onClick = { privacyPolicyOpen = true },
                            modifier = Modifier.testTag("action_privacy")
                        )

                        SettingsActionRow(
                            title = Localization.getString("settings_rate", language),
                            subtitle = "Provide glowing stars feedback to the developers.",
                            icon = Icons.Default.ThumbUp,
                            onClick = { viewModel.showRatingDialog() },
                            modifier = Modifier.testTag("action_rate")
                        )

                        SettingsActionRow(
                            title = Localization.getString("settings_share", language),
                            subtitle = "Recommend this companion tools saver to friends.",
                            icon = Icons.Default.Share,
                            onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Save WhatsApp stories instantly! Download Status Saver 2026: https://play.google.com/store/apps/details?id=com.aistudio.statussaver")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Recommend to Friends"))
                            },
                            modifier = Modifier.testTag("action_share_app")
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Footer Panel
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Status Saver for WhatsApp",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                Localization.getString("settings_version", language),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                Localization.getString("settings_footer_desc", language),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Overlay full-screen Zoom-enabled Media viewer if selected
    if (selectedMedia != null) {
        MediaViewerScreen(
            status = selectedMedia!!,
            viewModel = viewModel,
            onBack = { viewModel.selectMediaItem(null) }
        )
    }
}

@Composable
fun TabBadgeRowItem(
    title: String,
    isSelected: Boolean,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StoragePermissionBanner(
    language: AppLanguage,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("storage_permission_banner"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = Localization.getString("storage_perm_title", language),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Localization.getString("storage_perm_desc", language),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("grant_storage_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = Localization.getString("storage_perm_btn", language),
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PermissionHelperBanner(
    waGranted: Boolean,
    wabGranted: Boolean,
    language: AppLanguage,
    onRequestStandard: () -> Unit,
    onRequestBusiness: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = Localization.getString("permission_title", language),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Localization.getString("permission_desc", language),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!waGranted) {
                    Button(
                        onClick = onRequestStandard,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("grant_wa_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("WhatsApp", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                if (!wabGranted) {
                    Button(
                        onClick = onRequestBusiness,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("grant_wab_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("W. Business", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    status: StatusItem,
    language: AppLanguage,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .clickable { onPreview() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.height(160.dp).fillMaxWidth()) {
            // Media Preview Thumbnail (Images load naturally, videos query Coil fallback or local placeholder)
            AsyncImage(
                model = status.uriString,
                contentDescription = status.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Client Type Badge Indicator (WhatsApp vs Business)
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (status.isBusiness) MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (status.isBusiness) Localization.getString("status_whatsapp_business", language) else Localization.getString("status_whatsapp", language),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Video Play Icon center overlay
            if (status.isVideo) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom Actions shade overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.fileName,
                    fontSize = 11.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (status.isSaved && onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp).testTag("delete_file_card_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else if (!status.isSaved) {
                        IconButton(
                            onClick = onSave,
                            modifier = Modifier.size(28.dp).testTag("save_file_card_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusEmptyState(
    message: String,
    onGuide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.HourglassEmpty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGuide,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Guide on How to Save", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val finalTint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(finalTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = finalTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}
