package com.auditflow.app.presentation.destination

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auditflow.app.presentation.common.NotImplementedBadge
import com.auditflow.app.presentation.navigation.AuditFlowDestination
import com.auditflow.app.presentation.theme.Amber500
import com.auditflow.app.presentation.theme.Navy900
import com.auditflow.app.presentation.theme.Slate50
import com.auditflow.app.presentation.theme.Slate500
import com.auditflow.app.presentation.theme.Slate600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotImplementedScreen(
    destination: AuditFlowDestination,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = destination.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Amber500,
                    modifier = Modifier.height(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                NotImplementedBadge(text = "NOT IMPLEMENTED YET")

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${destination.title} Screen",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Navy900
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This screen will be implemented in its dedicated AuditFlow phase. No synthetic audit reports or simulated file trees are shown here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                ) {
                    Text("Return to Home")
                }
            }
        }
    }
}
