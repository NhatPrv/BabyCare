package com.example.babycare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.babycare.ui.theme.BackgroundLight
import com.example.babycare.ui.theme.PrimaryBlue
import com.example.babycare.ui.theme.SecondaryBlue
import com.example.babycare.ui.theme.TextDark
import com.example.babycare.ui.theme.TextSecondary
import com.example.babycare.ui.theme.WarningOrange
import com.example.babycare.viewmodel.BabyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var renameSessionId by remember { mutableStateOf<String?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val selectedChatSessionId by viewModel.selectedChatSessionId.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val aiSuggestions by viewModel.aiSuggestions.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val hasUserMessage = chatMessages.any { it.role == "user" }
    val suggestions = if (aiSuggestions.isNotEmpty()) aiSuggestions else listOf(
        "Lịch tiêm tháng này",
        "Nhiệt độ bao nhiêu là sốt",
        "Bé biếng ăn nên làm gì",
        "Lịch ngủ cho bé"
    )

    LaunchedEffect(Unit) {
        viewModel.loadChatSessions()
    }

    if (renameSessionId != null) {
        AlertDialog(
            onDismissRequest = { renameSessionId = null },
            title = { Text("Đổi tên đoạn chat") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it },
                    placeholder = { Text("Tên mới...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameTitle.isNotBlank()) {
                            viewModel.updateChatSessionTitle(renameSessionId!!, renameTitle)
                            renameSessionId = null
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = { renameSessionId = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    fun sendUserMessage(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank() || isChatLoading) return
        viewModel.sendChatMessage(cleaned)
        messageText = ""
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(304.dp),
                drawerContainerColor = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Đoạn chat", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                    IconButton(
                        onClick = {
                            viewModel.createNewChatSession()
                            scope.launch { drawerState.close() }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New chat", tint = PrimaryBlue)
                    }
                }

                if (chatSessions.isEmpty()) {
                    Text(
                        "Chưa có đoạn chat nào",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chatSessions) { session ->
                            ChatSessionItem(
                                title = session.title,
                                selected = session.id == selectedChatSessionId,
                                onClick = {
                                    viewModel.selectChatSession(session.id)
                                    scope.launch { drawerState.close() }
                                },
                                onEdit = {
                                    renameSessionId = session.id
                                    renameTitle = session.title
                                },
                                onDelete = {
                                    viewModel.deleteChatSession(session.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text("Trợ lý AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Chat history", tint = TextDark)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = TextSecondary)
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = TextSecondary)
                        }
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            placeholder = { Text("Nhập câu hỏi...", fontSize = 14.sp) },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            enabled = !isChatLoading
                        )
                        val canSend = messageText.trim().isNotEmpty() && !isChatLoading
                        FloatingActionButton(
                            onClick = { if (canSend) sendUserMessage(messageText) },
                            containerColor = if (canSend) PrimaryBlue else PrimaryBlue.copy(alpha = 0.45f),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (isChatLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.White)
            ) {
                // Fixed medical warning (always visible, not part of message list)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.Transparent
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Không phải lời khuyên y tế. Luôn hỏi ý kiến bác sĩ khi cần.",
                                fontSize = 12.sp,
                                color = TextDark.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {

                    if (!hasUserMessage) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(SecondaryBlue, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Xin chào! Tôi là BabyCare AI của bạn!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text(
                                    "Tôi có thể gợi ý các mẹo nuôi con về ăn uống, giấc ngủ, sốt và tiêm chủng.",
                                    textAlign = TextAlign.Center,
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestions.take(2).forEach { tip ->
                                SuggestionChip(text = tip, onClick = { sendUserMessage(tip) })
                            }
                        }
                    }

                    if (!error.isNullOrBlank()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = WarningOrange.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    error ?: "",
                                    modifier = Modifier.padding(12.dp),
                                    color = TextDark,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    if (chatMessages.isEmpty()) {
                        item {
                            Text(
                                "Chọn một đoạn chat cũ hoặc gửi câu hỏi mới.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                textAlign = TextAlign.Center,
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                    items(chatMessages) { msg ->
                        ChatBubble(ChatMessage(msg.content, msg.role == "user"))
                    }
                }
                LaunchedEffect(chatMessages.size) {
                    if (chatMessages.isNotEmpty()) {
                        // Small delay to allow LazyColumn to compose items before scrolling
                        delay(120)
                        try {
                            listState.animateScrollToItem(chatMessages.size - 1)
                        } catch (_: Exception) {
                            // ignore layout exceptions during fast updates
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatSessionItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SecondaryBlue else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = if (selected) PrimaryBlue else TextSecondary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    title,
                    color = TextDark,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Đổi tên", fontSize = 13.sp) },
                        onClick = {
                            onEdit()
                            expanded = false
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Xóa", fontSize = 13.sp) },
                        onClick = {
                            onDelete()
                            expanded = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        color = Color.White,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isUser) PrimaryBlue else BackgroundLight
    val textColor = if (message.isUser) Color.White else TextDark
    val shape = if (message.isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(color = bgColor, shape = shape) {
            Text(
                text = message.text,
                modifier = Modifier.padding(16.dp),
                color = textColor,
                fontSize = 14.sp
            )
        }
    }
}
