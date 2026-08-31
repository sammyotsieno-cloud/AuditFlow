package com.auditflow.app.presentation.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.presentation.home.HomeViewModel
import com.auditflow.app.presentation.theme.Blue500
import com.auditflow.app.presentation.theme.Navy900
import com.auditflow.app.presentation.theme.Slate100
import com.auditflow.app.presentation.theme.Slate200
import com.auditflow.app.presentation.theme.Slate400
import com.auditflow.app.presentation.theme.Slate50
import com.auditflow.app.presentation.theme.Slate500
import com.auditflow.app.presentation.theme.Slate600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceTreeScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProjectInput: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SOURCE TREE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Navy900
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Navy900
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Slate50
                )
            )
        },
        containerColor = Slate50
    ) { paddingValues ->
        when (val state = uiState.projectState) {
            is ProjectState.ProjectLoaded -> {
                val filteredFiles = remember(state.files, searchQuery) {
                    if (searchQuery.isBlank()) {
                        state.files
                    } else {
                        state.files.filter {
                            it.relativePath.contains(searchQuery, ignoreCase = true) ||
                                    it.name.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Project Header Info
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .border(1.dp, Slate200, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = state.metadata.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.metadata.fileCount} source files • ${state.files.size} total nodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600
                            )
                        }
                    }

                    // Search / Filter Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Filter files by name or path...") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Slate400)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Slate400)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Navy900,
                            unfocusedBorderColor = Slate200
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // File List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        items(filteredFiles, key = { it.relativePath }) { fileNode ->
                            SourceFileItem(node = fileNode)
                        }
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Slate100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No Project Loaded",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy900
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Ingest a local directory or GitHub repository to view its verified source tree.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceFileItem(node: SourceFileNode) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .border(1.dp, Slate200, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (node.isDirectory) Navy900 else Blue500,
                modifier = Modifier.size(22.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (node.isDirectory) FontWeight.Bold else FontWeight.SemiBold,
                    color = Navy900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (node.relativePath.isNotBlank() && node.relativePath != node.name) {
                    Text(
                        text = node.relativePath,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Slate500,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!node.isDirectory) {
                    Text(
                        text = formatFileSize(node.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                }
            }

            if (!node.isDirectory && node.extension.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Slate100)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = node.extension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Slate600
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    if (digitGroups == 0) return "$bytes B"
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
}
