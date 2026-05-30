# 🧹 HƯỚNG DẪN XÓA GRADLE CACHE (TIẾNG VIỆT)

## 🔴 Vấn Đề: Gradle Cache Cũ Gây Lỗi

Khi Gradle cache (bộ nhớ tạm) còn lưu cấu hình cũ, nó sẽ gây xung đột khi bạn thay đổi version.

---

## ✅ CÁCH 1: Xóa Cache Thông Qua Windows (Đơn Giản Nhất)

### Bước 1: Mở Windows Explorer
- Nhấn `Windows + E` để mở File Explorer

### Bước 2: Vào Thư Mục Gradle
- Copy this path: `%USERPROFILE%\.gradle`
- Paste vào thanh địa chỉ (Address Bar) của Windows Explorer
- Nhấn Enter

### Bước 3: Xóa Folder
- Bạn sẽ thấy folder `.gradle`
- Xóa nó (Delete key hoặc chuột phải > Delete)
- Xác nhận xóa

### Bước 4: Hoàn Tất
- Đóng Windows Explorer
- Mở Android Studio
- Gradle sẽ tự tạo lại cache mới khi build

---

## ✅ CÁCH 2: Xóa Qua Terminal (Nhanh Hơn)

### Nếu Dùng PowerShell (Windows):
```powershell
# Copy & Paste vào PowerShell:
rmdir -r -Force $env:USERPROFILE\.gradle
```

### Nếu Dùng Command Prompt (CMD):
```cmd
# Copy & Paste vào CMD:
rmdir /s /q %USERPROFILE%\.gradle
```

---

## ✅ CÁCH 3: Xóa Cache Trong Android Studio (Đầy Đủ)

Cách này sạch hơn vì còn xóa thêm IDE cache:

### Bước 1: Mở Android Studio
- Chọn menu: **File**

### Bước 2: Click "Invalidate Caches"
- Chọn: **File > Invalidate Caches > Invalidate and Restart**

### Bước 3: Chọn Tùy Chọn
- Một popup sẽ hiện lên
- Chọn: **Invalidate and Restart** (tất cả mục)
- Nhấn OK

### Bước 4: Android Studio Sẽ Khởi Động Lại
- IDE sẽ tự động đóng và mở lại
- Cache sẽ được xóa và tạo mới

---

## 🎯 CÁCH 4: Fix Hoàn Toàn (Nên Thử)

```bash
# 1. Xóa Gradle cache
rmdir /s /q %USERPROFILE%\.gradle

# 2. Xóa build folder của app
cd D:\MyDATA\SEMESTER6\Chuyende\Final\app
rmdir /s /q build

# 3. Xóa build folder của project
cd D:\MyDATA\SEMESTER6\Chuyende\Final
rmdir /s /q build

# 4. Clean Gradle
./gradlew clean

# 5. Rebuild
./gradlew build
```

---

## 📋 Sau Khi Xóa Cache - Bạn Nên Làm Gì?

### 1. Đóng Android Studio Hoàn Toàn
```
File > Exit
```

### 2. Mở Lại Android Studio
```
Start Android Studio lại
```

### 3. Sync Gradle Files
```
File > Sync Now
```

### 4. Build Project
```
Build > Build Project
```

---

## 🔍 Cách Kiểm Tra Cache Đã Bị Xóa?

### Nếu Thành Công:
✅ Gradle sẽ mất vài phút để download dependencies  
✅ Gradele sẽ show "Building" progress  
✅ Cuối cùng sẽ show "BUILD SUCCESSFUL"  

### Nếu Vẫn Lỗi:
❌ Có thể vấn đề khác
❌ Hãy check lại file build.gradle.kts

---

## 💡 Ghi Chú Quan Trọng

| Điều Cần Biết | Thông Tin |
|---------------|----------|
| Folder .gradle ở đâu? | `C:\Users\[YourName]\.gradle` |
| Xóa cache mất bao lâu? | 2-5 phút download lại |
| Download dữ liệu gì? | Dependencies, plugins, etc. |
| Có mất code không? | Không, chỉ cache tạm |
| Sau khi xóa cần gì? | Đơn giản xoá & mở lại |

---

## ✨ TÓM TẮT NHANH

| Bước | Hành Động | Thời Gian |
|------|----------|----------|
| 1 | Xóa `.gradle` folder | 1 phút |
| 2 | Restart Android Studio | 1 phút |
| 3 | Sync Gradle | 2-5 phút |
| 4 | Build Project | 3-10 phút |
| **Tổng cộng** | | **10-20 phút** |

---

## 🎉 Khi Xong Rồi

✅ Gradle cache sẽ sạch sẽ  
✅ Build mới sẽ không có cache conflicts  
✅ Project sẽ build thành công  

**Lỗi "Cannot add extension with name 'kotlin'" sẽ BIẾN MẤT!** 🚀

---

**📅 Hướng dẫn này: 10/05/2026**

