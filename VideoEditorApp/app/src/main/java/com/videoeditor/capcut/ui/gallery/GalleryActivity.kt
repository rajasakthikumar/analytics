package com.videoeditor.capcut.ui.gallery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoeditor.capcut.data.models.MediaItem
import com.videoeditor.capcut.data.models.MediaType
import com.videoeditor.capcut.ui.editor.VideoEditorActivity
import com.videoeditor.capcut.ui.theme.VideoEditorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GalleryActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VideoEditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val mediaItems by viewModel.mediaItems.collectAsState()

                    GalleryScreen(
                        uiState = uiState,
                        mediaItems = mediaItems,
                        selectedItems = uiState.selectedItems,
                        onTabChange = { viewModel.setMediaType(it) },
                        onItemClick = { viewModel.toggleSelection(it) },
                        onContinue = { navigateToEditor(viewModel.getSelectedPaths()) },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private fun navigateToEditor(selectedPaths: List<String>) {
        val intent = Intent(this, VideoEditorActivity::class.java).apply {
            putStringArrayListExtra("media_items", ArrayList(selectedPaths))
        }
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    uiState: GalleryUiState,
    mediaItems: List<MediaItem>,
    selectedItems: List<String>,
    onTabChange: (MediaType) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Media") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (selectedItems.isNotEmpty()) {
                        TextButton(onClick = onContinue) {
                            Text("Continue (${selectedItems.size})")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedItems.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp
                ) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue with ${selectedItems.size} item${if (selectedItems.size > 1) "s" else ""}")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = when (uiState.selectedType) {
                    MediaType.VIDEO -> 0
                    MediaType.IMAGE -> 1
                    else -> 0
                }
            ) {
                Tab(
                    selected = uiState.selectedType == MediaType.VIDEO,
                    onClick = { onTabChange(MediaType.VIDEO) },
                    text = { Text("Videos") },
                    icon = { Icon(Icons.Outlined.VideoLibrary, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedType == MediaType.IMAGE,
                    onClick = { onTabChange(MediaType.IMAGE) },
                    text = { Text("Images") },
                    icon = { Icon(Icons.Outlined.Image, contentDescription = null) }
                )
            }

            // Content
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (uiState.selectedType == MediaType.VIDEO)
                                Icons.Outlined.VideoLibrary
                            else
                                Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No ${if (uiState.selectedType == MediaType.VIDEO) "videos" else "images"} found",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(mediaItems) { item ->
                        MediaGridItem(
                            item = item,
                            isSelected = selectedItems.contains(item.id.toString()),
                            selectionIndex = selectedItems.indexOf(item.id.toString()) + 1,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaGridItem(
    item: MediaItem,
    isSelected: Boolean,
    selectionIndex: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Selection indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    selectionIndex.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Duration badge for videos
        if (item.type == MediaType.VIDEO && item.duration > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    formatDuration(item.duration),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
