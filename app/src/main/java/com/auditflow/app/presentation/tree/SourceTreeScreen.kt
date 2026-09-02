package com.auditflow.app.presentation.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.domain.util.ProjectTreeLine
import com.auditflow.app.domain.util.ProjectTreeReconstructor
import com.auditflow.app.presentation.home.HomeViewModel
import com.auditflow.app.presentation.theme.Navy800
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
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }

    val loadedState = uiState.projectState as? ProjectState.ProjectLoaded

    val fullTreeRoot = remember(loadedState?.metadata?.name, loadedState?.files) {
        loadedState?.let {
            ProjectTreeReconstructor.reconstruct(it.metadata.name, it.files)
        }
    }

    val allTreeLines = remember(fullTreeRoot) {
        fullTreeRoot?.let {
            ProjectTreeReconstructor.generateTreeLines(it)
        } ?: emptyList()
    }

    val displayedLines = remember(allTreeLines, searchQuery) {
        if (searchQuery.isBlank()) {
            allTreeLines
        } else {
            allTreeLines.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                        it.relativePath.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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
                actions = {
                    if (allTreeLines.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val treeText = allTreeLines.joinToString("\n") { line ->
                                    "${line.prefix}${line.displayName}"
                                }
                                clipboardManager.setText(AnnotatedString(treeText))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy source tree",
                                tint = Navy900
                            )
                        }
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Project Header Info
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .border(1.dp, Slate200, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.metadata.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy900,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Navy900
                                ) {
                                    Text(
                                        text = state.metadata.artifactIdentity.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${state.metadata.fileCount} entries • ${state.files.size} total nodes",
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
                        placeholder = { Text("Filter tree by name or path...") },
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
                            focusedTextColor = Navy900,
                            unfocusedTextColor = Navy900,
                            focusedPlaceholderColor = Slate400,
                            unfocusedPlaceholderColor = Slate400,
                            focusedBorderColor = Navy900,
                            unfocusedBorderColor = Slate200,
                            cursorColor = Navy900,
                            focusedLeadingIconColor = Navy900,
                            unfocusedLeadingIconColor = Slate400,
                            focusedTrailingIconColor = Navy900,
                            unfocusedTrailingIconColor = Slate400
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Canonical Tree Container
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                            .border(1.dp, Slate200, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val horizontalScrollState = rememberScrollState()

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            items(
                                items = displayedLines,
                                key = { "${it.depth}_${it.relativePath}_${it.displayName}_${it.prefix}" }
                            ) { treeLine ->
                                CanonicalTreeRow(line = treeLine)
                            }
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
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

/**
 * Renders an exact line of the locked canonical tree format:
 *
 * PROJECT
 * ├── README.md
 * ├── app/
 * │   ├── build.gradle.kts
 * │   └── src/
 * │       ├── main/
 * │       │   └── AndroidManifest.xml
 * │       └── test/
 * │           └── ExampleTest.kt
 * └── ...
 */
@Composable
private fun CanonicalTreeRow(line: ProjectTreeLine) {
    Row(
        modifier = Modifier
            .padding(vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Monospace structural branch / continuation symbols: ├──, └──, │
        if (line.prefix.isNotEmpty()) {
            Text(
                text = line.prefix,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.sp
                ),
                color = Slate400
            )
        }

        // Node name with trailing slash for directories
        Text(
            text = line.displayName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            fontWeight = when {
                line.isRoot -> FontWeight.Bold
                line.isDirectory -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
            color = when {
                line.isRoot -> Navy900
                line.isDirectory -> Navy800
                else -> Navy900
            }
        )

        // Metadata for leaf files (formatted size + extension badge)
        if (!line.isDirectory && !line.isRoot) {
            if (line.sizeBytes > 0L) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = formatFileSize(line.sizeBytes),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Slate400
                )
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

