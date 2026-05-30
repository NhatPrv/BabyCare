package com.example.babycare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
fun RegisterScreen(
    viewModel: BabyViewModel,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var agreeToNotifications by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) }

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
                .height(240.dp)
                .background(SecondaryBlue, shape = RoundedCornerShape(bottomStart = 80.dp, bottomEnd = 80.dp))
        ) {
            IconButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.padding(top = 40.dp, start = 16.dp).background(Color.White, CircleShape).size(40.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark, modifier = Modifier.size(20.dp))
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
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BabyCare", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 12.sp)
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ChildFriendly, contentDescription = null, modifier = Modifier.size(100.dp), tint = PrimaryBlue.copy(alpha = 0.5f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(SecondaryBlue, CircleShape)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = PrimaryBlue)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tạo tài khoản",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Nhập thông tin để tham gia BabyCare.",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(32.dp))

            RegisterTextField(label = "Họ và tên", value = fullName, onValueChange = { fullName = it }, placeholder = "Nhập họ và tên", icon = Icons.Default.Person)
            Spacer(modifier = Modifier.height(16.dp))
            RegisterTextField(label = "Số điện thoại hoặc tên đăng nhập", value = emailOrPhone, onValueChange = { emailOrPhone = it }, placeholder = "Nhập số điện thoại hoặc tên đăng nhập", icon = Icons.Default.Email)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Mật khẩu", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Tạo mật khẩu") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, contentDescription = null)
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

            Spacer(modifier = Modifier.height(16.dp))

            Text("Xác nhận mật khẩu", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Nhập lại mật khẩu") },
                leadingIcon = { Icon(Icons.Default.LockClock, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(imageVector = if (confirmPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, contentDescription = null)
                    }
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BackgroundLight,
                    unfocusedContainerColor = BackgroundLight,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = agreeToNotifications, 
                    onCheckedChange = { agreeToNotifications = it },
                    colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                )
                Text(
                    "Tôi đồng ý nhận thông báo và cập nhật về lịch bé yêu của tôi.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    errorMessage = null
                    when {
                        fullName.isBlank() || emailOrPhone.isBlank() || password.isBlank() -> {
                            errorMessage = "Vui lòng nhập đầy đủ thông tin"
                        }
                        password != confirmPassword -> {
                            errorMessage = "Mật khẩu xác nhận không khớp"
                        }
                        else -> {
                            isRegistering = true
                            viewModel.register(
                                username = emailOrPhone,
                                password = password,
                                fullName = fullName,
                                onSuccess = {
                                    isRegistering = false
                                    onRegisterSuccess()
                                },
                                onError = {
                                    isRegistering = false
                                    errorMessage = it
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = !isRegistering
            ) {
                if (isRegistering) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Đăng ký", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                    Row(
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
                    Row(
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Đã có tài khoản? ", color = TextSecondary)
                Text(
                    "Đăng nhập",
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }
}

@Composable
fun RegisterTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, icon: ImageVector) {
    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = TextSecondary) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BackgroundLight,
            unfocusedContainerColor = BackgroundLight,
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = Color.Transparent
        )
    )
}
