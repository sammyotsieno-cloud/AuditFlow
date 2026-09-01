package com.auditflow.app.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auditflow.app.R
import com.auditflow.app.domain.model.AuditPrincipleLevel
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.presentation.common.NotImplementedBadge
import com.auditflow.app.presentation.common.NotImplementedDialog
import com.auditflow.app.presentation.navigation.AuditFlowDestination
import com.auditflow.app.presentation.theme.Blue500
import com.auditflow.app.presentation.theme.Blue600
import com.auditflow.app.presentation.theme.Navy700
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
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDestination: (AuditFlowDestination) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // SAF Directory Picker Launcher for local project ingestion
    val localDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.ingestLocalProject(uri, context)
        }
    }

    // SAF File Picker Launcher for APK / ZIP / artifact ingestion
    val localArtifactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.ingestLocalArtifact(uri, context)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Navy900),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Blue500,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "AUDITFLOW",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Navy900
                        )
                    }
                },
                actions = {
                    NotImplementedBadge(
                        text = "PHASE 1B",
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Slate50
                )
            )
        },
        containerColor = Slate50
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Source Code Audit & Verification",
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate600,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Core Epistemic Invariant Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Slate200, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Navy900,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "CORE AUDIT INVARIANT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Navy900,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "EXISTS ≠ CONNECTED ≠ EXECUTED ≠ VALIDATED ≠ VERIFIED ≠ PRODUCES_EXPECTED_RESULT",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.SemiBold,
                            color = Slate600,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Current State Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate200, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val state = uiState.projectState) {
                        is ProjectState.NoProject -> {
                            // Status Badge
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Slate100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = Slate500,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.no_project_loaded),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "No audit data, simulated repository, or test results are loaded.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate500,
                                textAlign = TextAlign.Center
                            )
                        }

                        is ProjectState.ProjectLoading -> {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Slate100),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Navy900,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Ingesting Project...",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = state.statusMessage.ifBlank { "Processing source files..." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate600,
                                textAlign = TextAlign.Center
                            )
                        }

                        is ProjectState.ProjectLoaded -> {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFECFDF5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = state.metadata.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Navy900,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Authoritative Artifact Identity Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Navy900,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = state.metadata.artifactIdentity.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "${state.metadata.fileCount} entries • ${state.files.size} structural nodes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate600,
                                textAlign = TextAlign.Center
                            )

                            // Specific APK Package Details if available
                            state.metadata.apkMetadata?.let { apk ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Slate100, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "Package: ${apk.applicationId}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        fontWeight = FontWeight.Bold,
                                        color = Navy900
                                    )
                                    if (apk.versionName != null || apk.versionCode != null) {
                                        Text(
                                            text = "Version: ${apk.versionName ?: "N/A"} (${apk.versionCode ?: "N/A"})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate600
                                        )
                                    }
                                    if (apk.minSdk != null || apk.targetSdk != null) {
                                        Text(
                                            text = "SDK: min ${apk.minSdk ?: "?"} / target ${apk.targetSdk ?: "?"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate600
                                        )
                                    }
                                    Text(
                                        text = "DEX Files: ${apk.dexFiles.size} • Components: ${apk.components.size} • Permissions: ${apk.permissions.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate600
                                    )
                                }
                            }

                            // Specific ZIP Archive Details if available
                            state.metadata.zipMetadata?.let { zip ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Slate100, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "Content: ${zip.detectedContentIdentity.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Navy900
                                    )
                                    Text(
                                        text = "Total Entries: ${zip.totalEntries} • Size: ${zip.totalUncompressedSizeBytes} bytes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate600
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.onResetStateToEmpty() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Unload", color = Navy900)
                                }
                                Button(
                                    onClick = { onNavigateToDestination(AuditFlowDestination.SourceTree) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                                ) {
                                    Text("Source Tree")
                                }
                            }
                        }

                        is ProjectState.Error -> {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFEF2F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Ingestion Error",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF991B1B),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = { viewModel.onResetStateToEmpty() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Dismiss Error", color = Navy900)
                            }
                        }
                    }

                    if (uiState.projectState is ProjectState.NoProject || uiState.projectState is ProjectState.Error) {
                        Spacer(modifier = Modifier.height(24.dp))

                        HorizontalDivider(color = Slate200)

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.choose_input_method),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Navy900
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Local Project Directory Ingestion Button
                        Button(
                            onClick = { localDirectoryLauncher.launch(null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Folder, contentDescription = null)
                                Text(
                                    text = "LOCAL DIRECTORY PROJECT",
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Local File Artifact (APK / ZIP) Ingestion Button
                        Button(
                            onClick = {
                                localArtifactLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Code, contentDescription = null)
                                Text(
                                    text = "LOCAL ARTIFACT (APK / ZIP)",
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // GitHub Repository Ingestion Button
                        OutlinedButton(
                            onClick = { onNavigateToDestination(AuditFlowDestination.ProjectInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = Navy900)
                                Text(
                                    text = "GITHUB REPOSITORY",
                                    color = Navy900,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Foundation Explorer
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "DESTINATION DIRECTORY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Slate500,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                AuditFlowDestination.allDestinations
                    .filter { it != AuditFlowDestination.Home }
                    .forEach { dest ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onNavigateToDestination(dest) }
                                .border(1.dp, Slate200, RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dest.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Navy900
                                )

                                if (dest.isImplemented) {
                                    NotImplementedBadge(text = "PHASE 1B")
                                } else {
                                    NotImplementedBadge()
                                }
                            }
                        }
                    }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal dialog for un-implemented capabilities
    if (uiState.isNotImplementedDialogOpen) {
        NotImplementedDialog(
            featureTitle = uiState.pendingFeatureName,
            onDismiss = { viewModel.onDismissNotImplementedDialog() }
        )
    }
}

