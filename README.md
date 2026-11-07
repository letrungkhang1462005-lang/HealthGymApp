# HealthGymApp - Ứng dụng Theo dõi Sức khỏe & Dinh dưỡng

Ứng dụng Android hỗ trợ theo dõi lịch tập luyện thể hình, quản lý chế độ dinh dưỡng và tích hợp trợ lý AI thông minh sử dụng Gemini API.

## 🚀 Tính năng chính
- Theo dõi chỉ số sức khỏe, lịch sử tập luyện và danh sách bài tập.
- Tích hợp Gemini AI hỗ trợ tư vấn dinh dưỡng và lộ trình tập.
- Quản lý cơ sở dữ liệu cục bộ an toàn bằng Room Database.

## 🛠 Công nghệ sử dụng
- **Ngôn ngữ:** Java
- **Database:** Room Database
- **AI Integration:** Gemini API
- **Libraries:** osmdroid, MPAndroidChart

## ⚙️ Hướng dẫn cài đặt & Cấu hình
1. Clone dự án về máy.
2. Tạo file `local.properties` ở thư mục gốc dự án.
3. Thêm khóa API của bạn vào file `local.properties`:
   ```properties
   GEMINI_API_KEY=khóa_của_bạn
   ```