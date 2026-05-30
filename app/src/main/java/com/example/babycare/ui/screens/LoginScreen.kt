package com.example.babycare.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.babycare.R
import com.example.babycare.ui.theme.*
import com.example.babycare.viewmodel.BabyViewModel

@Composable
fun LoginScreen(
    viewModel: BabyViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        // Header Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(SecondaryBlue, shape = RoundedCornerShape(bottomStart = 80.dp, bottomEnd = 80.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder for illustration
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = PrimaryBlue.copy(alpha = 0.5f)
                )
            }
            
            Surface(
                modifier = Modifier
                    .padding(top = 40.dp, end = 24.dp)
                    .align(Alignment.TopEnd),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BabyCare", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 12.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(text = "Chào mừng trở lại!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Đăng nhập để theo dõi sức khỏe và lịch tiêm chủng của bé yêu.",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text("Điện thoại hoặc tên đăng nhập", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Nhập số điện thoại hoặc tên đăng nhập") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BackgroundLight,
                    unfocusedContainerColor = BackgroundLight,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("Мật khẩu", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Nhập mật khẩu của bạn") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        // Using standard icons to avoid unresolved reference issues if sync is slow
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Info else Icons.Default.Info, // Placeholder
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BackgroundLight,
                    unfocusedContainerColor = BackgroundLight,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            TextButton(
                onClick = { /* TODO */ },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Quên mật khẩu?", color = PrimaryBlue, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    errorMessage = null
                    isLoggingIn = true
                    viewModel.login(
                        username = email,
                        password = password,
                        onSuccess = {
                            isLoggingIn = false
                            onLoginSuccess()
                        },
                        onError = {
                            isLoggingIn = false
                            errorMessage = it
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = !isLoggingIn
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Đăng nhập →", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMessage ?: "", color = Color(0xFFDC2626), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                Text("  HOẶC ĐĂNG NHẬP VỚI  ", color = TextSecondary, fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google",
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Google", color = TextDark)
                    }
                }
                OutlinedButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_apple),
                            contentDescription = "Apple",
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Apple", color = TextDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Chưa có tài khoản? ", color = TextSecondary)
                Text(
                    "Đăng ký ngay",
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }
        }
    }
}
