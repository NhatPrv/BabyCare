# 🎯 HƯỚNG DẪN THỰC HIỆN - TỪNG BƯỚC

## 📍 BẠN ĐANG Ở ĐÂU?

✅ Tất cả file đã được fix  
✅ Bây giờ bạn chỉ cần thực hiện 4 bước dưới đây

---

## 🚀 4 BƯỚC FIX

### 1️⃣ MỞ TERMINAL

- Windows: Nhấn `Windows + R`, gõ `powershell`, Enter
- Hoặc: Click phải trên folder, chọn "Open PowerShell here"

---

### 2️⃣ XÓA GRADLE CACHE

Copy & Paste vào PowerShell:
```powershell
rmdir -r -Force $env:USERPROFILE\.gradle
```

Nhấn Enter, chờ xong (khoảng 30 giây)

---

### 3️⃣ XÓA BUILD FOLDERS

Copy & Paste vào PowerShell:
```powershell
rmdir /s /q app\build
rmdir /s /q build
```

Nhấn Enter, chờ xong (khoảng 30 giây)

---

### 4️⃣ RELOAD ANDROID STUDIO

```
1. Tắt Android Studio hoàn toàn
   (File > Exit hoặc Alt + F4)

2. Chờ 10 giây

3. Mở Android Studio lại

4. Chờ "Indexing..." hoàn tất

5. Click Build > Clean Project

6. Click Build > Build Project

7. Chờ "BUILD SUCCESSFUL"
```

---

## ✅ HOÀN THÀNH!

Khi bạn thấy:
```
BUILD SUCCESSFUL in XXs
```

**Lỗi "Cannot add extension" BIẾN MẤT!** 🎉

---

## 📊 TIẾN TRÌNH

```
[ ] Mở PowerShell
[ ] Xóa cache gradle
[ ] Xóa build folders
[ ] Tắt Android Studio
[ ] Mở Android Studio lại
[ ] Clean Project
[ ] Build Project
[ ] ✅ BUILD SUCCESSFUL
```

---

## ⏱️ THỜI GIAN

```
Xóa cache    : 1 phút
Xóa build   : 1 phút
Tắt/Mở IDE  : 3 phút
Build       : 15 phút
────────────────────
TỔNG        : 20 phút
```

---

## 🟢 NEXT (NGAY SAU FIX)

```
BUILD SUCCESSFUL?
       ↓
    [ ]YES  [ ]NO
       ↓          ↓
   ✅ XONG   ❌ Restart
                 máy tính
              rồi thử lại
```

---

##  📖 CẦN GIÚP TRONG QUỐC TRÌNH?

Đọc những file này:

1. **README_FIX_TIENG_VIET.md** - Tính năng tính năng
2. **CHECKLIST_TIENG_VIET.md** - Chi tiết từng bước
3. **XOA_GRADLE_CACHE_TIENG_VIET.md** - Cách xóa cache

---

## 🎯 ĐIỀU QUAN TRỌNG

> ⚠️ **PHẢI XÓA CACHE!**  
> Nếu không xóa, lỗi vẫn tồn tại

> 💾 **CODE AN TOÀN**  
> Xóa cache không mất code

> ⏰ **ĐỢI 20 PHÚT**  
> Lần đầu build sẽ lâu

---

## 🏁 FINISH!

```
┌─────────────────────────┐
│ GHI NHỚ 4 BƯỚC:        │
│                        │
│ 1. rmdir cache gradle  │
│ 2. rmdir build folders │
│ 3. Tắt / Mở ID         │
│ 4. Build Project       │
│                        │
│ => BUILD SUCCESSFUL! ✅│
└─────────────────────────┘
```

---

**START NOW! 🚀**

