# TÀI LIỆU TECH STACK CẬP NHẬT

## ĐỀ TÀI: HỆ THỐNG QUẢN LÝ PHÒNG KHÁM NHA KHOA TÍCH HỢP AI CHATBOT

> Tài liệu này được cập nhật theo đặc tả chức năng `CHUC_NANG_TONG_HOP_CAP_NHAT_V5.txt`.

---

## 1. KIẾN TRÚC TỔNG THỂ

Hệ thống sử dụng mô hình **Client - Server, Single Backend Architecture**.

```text
┌──────────────────────────────────────┐
│            React Frontend            │
│ Public / Patient / Doctor / Admin    │
└──────────────────┬───────────────────┘
                   │
              REST API / SSE
                   │
┌──────────────────▼───────────────────┐
│          Spring Boot Backend         │
│                                      │
│ Auth & Authorization                 │
│ Doctor / Patient / Service           │
│ Schedule / Slot / Appointment        │
│ Check-in / No-show / Check-out       │
│ Medical Record / Follow-up           │
│ RAG Chatbot                          │
└──────────────────┬───────────────────┘
                   │
┌──────────────────▼───────────────────┐
│            PostgreSQL                │
│ Business Data + Chat Data            │
│ pgvector                             │
└──────────────────┬───────────────────┘
                   │
             Google Gemini API
```

Nguyên tắc kiến trúc:

- Frontend và Backend tách riêng.
- Backend dùng một ứng dụng Spring Boot duy nhất.
- PostgreSQL là cơ sở dữ liệu chính.
- `pgvector` được dùng cho dữ liệu embedding của RAG.
- Không cần một Backend Python riêng cho AI.
- Không dùng Microservice trong phạm vi đồ án.
- Các thành phần được cài đặt và chạy trực tiếp trên máy phát triển hoặc máy chủ.

---

## 2. CÔNG NGHỆ SỬ DỤNG

### 2.1. Backend

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 LTS |
| Framework | Spring Boot 3.x |
| REST API | Spring Web |
| Security | Spring Security |
| Authentication | JWT Access Token + Refresh Token |
| Password Hashing | BCrypt |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation |
| Transaction | Spring Transaction |
| Database Migration | Flyway |
| AI Integration | Spring AI |
| Streaming Chat | SSE |
| API Documentation | Springdoc OpenAPI / Swagger UI |
| Build Tool | Maven |
| Utility | Lombok |

### 2.2. Frontend

| Thành phần | Công nghệ |
|---|---|
| UI | React |
| Build Tool | Vite |
| Ngôn ngữ | TypeScript |
| Routing | React Router |
| HTTP Client | Axios |
| Server State / Cache | TanStack Query |
| Form | React Hook Form |
| Validation | Zod |
| Icons | Lucide React |
| Styling | TailwindCSS hoặc CSS Modules |
| Chat Streaming | EventSource / Fetch ReadableStream |

### 2.3. Database

| Thành phần | Công nghệ |
|---|---|
| CSDL chính | PostgreSQL 16+ |
| ORM Mapping | JPA / Hibernate |
| Vector Extension | pgvector |
| Migration | Flyway |

### 2.4. AI

| Thành phần | Công nghệ |
|---|---|
| LLM | Google Gemini API |
| AI Framework | Spring AI |
| Vector Store | PostgreSQL + pgvector |
| Kiến trúc | RAG |
| Streaming | SSE |

---

## 3. ROLE VÀ PHÂN QUYỀN

Hệ thống có 3 role:

```text
ADMIN
DOCTOR
PATIENT
```

### PATIENT

- Đăng ký / đăng nhập.
- Quản lý hồ sơ.
- Xem dịch vụ và bác sĩ.
- Đặt lịch.
- Đổi lịch.
- Hủy lịch.
- Xem lịch hẹn.
- Xem lịch sử khám.
- Xem kết quả khám.
- Sử dụng chatbot.
- Xem lại lịch sử chat.

### DOCTOR

- Xem và quản lý lịch làm việc.
- Xem danh sách lịch khám.
- Check-in bệnh nhân.
- Bắt đầu khám.
- Nhập kết quả khám.
- Chọn có/không tái khám.
- Đặt lịch tái khám.
- Check-out.
- Đánh dấu NO_SHOW.

### ADMIN

- Quản lý tài khoản.
- Tạo tài khoản bác sĩ.
- Quản lý bác sĩ.
- Quản lý dịch vụ.
- Theo dõi hoạt động hệ thống.
- Xử lý trường hợp ngoại lệ.

Bác sĩ và Admin **không cần xác nhận từng lịch hẹn mới** do bệnh nhân đặt.

---

## 4. AUTHENTICATION & SECURITY

Luồng:

```text
Email + Password
        ↓
Spring Security
        ↓
BCrypt
        ↓
Access Token + Refresh Token
```

Các thành phần Backend:

```text
SecurityConfig
JwtTokenProvider
JwtAuthenticationFilter
CustomUserDetailsService
AuthService
AuthController
```

Yêu cầu:

- Mật khẩu chỉ lưu dưới dạng hash BCrypt.
- API được phân quyền theo Role.
- Bệnh nhân chỉ thao tác trên dữ liệu thuộc tài khoản của mình.
- Tài khoản bác sĩ do Admin tạo.

---

## 5. QUẢN LÝ LỊCH LÀM VIỆC

Bác sĩ có lịch làm việc theo ngày trong tuần và theo từng buổi.

Ví dụ:

```text
Buổi sáng: 07:00 - 12:00
Buổi chiều: 13:00 - 18:00
```

Có:

- Lịch mặc định.
- Lịch nghỉ.
- Lịch override theo ngày hoặc tuần cụ thể.

Các thông tin dự kiến:

```text
doctor_id
day_of_week
session
start_time
end_time
is_off
```

Override có thể chứa:

```text
doctor_id
override_date hoặc week_start
start_time
end_time
is_off
```

---

## 6. SLOT KHÁM

Khoảng cách slot cố định:

```text
slot_duration_minutes = 60
```

Ví dụ:

```text
07:00 - 08:00
08:00 - 09:00
09:00 - 10:00
10:00 - 11:00
11:00 - 12:00

13:00 - 14:00
14:00 - 15:00
15:00 - 16:00
16:00 - 17:00
17:00 - 18:00
```

Mỗi bác sĩ chỉ nhận một bệnh nhân trong một slot.

Lý do chọn slot 60 phút:

- Tránh lịch quá dày.
- Dễ quản lý và hiển thị.
- Cho bác sĩ có thời gian khám, tư vấn, nhập hồ sơ.
- Nếu khám xong sớm, thời gian còn lại có thể dùng để nghỉ hoặc chuẩn bị ca tiếp theo.

Backend sinh slot từ `DoctorSchedule`, sau đó loại bỏ:

- Slot bác sĩ nghỉ.
- Slot đã có Appointment.
- Slot không đáp ứng điều kiện đặt trước.
- Slot nằm ngoài giờ làm việc.

---

## 7. QUY TẮC ĐẶT LỊCH

Bệnh nhân đặt lịch theo luồng:

```text
Dịch vụ
   ↓
Bác sĩ
   ↓
Ngày
   ↓
Slot trống
   ↓
Đặt lịch
   ↓
BOOKED
```

Không sử dụng luồng:

```text
PENDING → CONFIRMED
```

Khi Backend tạo Appointment thành công:

```text
status = BOOKED
```

### Điều kiện đặt trước

Bệnh nhân phải đặt lịch trước giờ bắt đầu khám ít nhất:

```text
booking_min_advance_minutes = 180
```

Tức:

```text
appointment_start_time - current_time >= 3 giờ
```

Ví dụ:

```text
Hiện tại: 10:00
Slot: 13:00
→ hợp lệ

Hiện tại: 10:01
Slot: 13:00
→ không hợp lệ
```

Không còn áp dụng quy tắc:

```text
Buổi sáng chỉ đặt buổi chiều
Buổi chiều chỉ đặt ngày hôm sau
```

Nếu trong ngày vẫn còn slot cách thời điểm hiện tại ít nhất 3 giờ thì vẫn được đặt.

Ví dụ bác sĩ làm đến 18:00:

```text
Hiện tại: 14:00
17:00 → được đặt

Hiện tại: 16:00
17:00 → không đủ 3 giờ
Ngày hiện tại không còn slot hợp lệ
→ chuyển sang ngày làm việc tiếp theo
```

Backend phải dùng thời gian Server theo:

```text
Asia/Ho_Chi_Minh
```

---

## 8. CHỐNG TRÙNG LỊCH

Ràng buộc:

```sql
UNIQUE (
    doctor_id,
    appointment_date,
    start_time
)
```

Kết hợp:

```text
Spring Transaction
+
PostgreSQL UNIQUE Constraint
```

Luồng:

```text
Patient chọn slot
        ↓
Backend kiểm tra slot
        ↓
Transaction
        ↓
INSERT Appointment
        ↓
Database kiểm tra UNIQUE
```

Nếu hai người cùng đặt một slot:

```text
User A → thành công
User B → từ chối vì slot đã được đặt
```

---

## 9. APPOINTMENT STATUS

Enum dự kiến:

```text
BOOKED
CHECKED_IN
IN_PROGRESS
COMPLETED
CANCELLED
NO_SHOW
```

Luồng chính:

```text
BOOKED
   ↓
CHECKED_IN
   ↓
IN_PROGRESS
   ↓
COMPLETED
```

Nhánh khác:

```text
BOOKED → CANCELLED
BOOKED → NO_SHOW
```

Đổi lịch không nhất thiết dùng `RESCHEDULED` làm trạng thái cuối.

Thay vào đó:

- Cập nhật lịch hiện tại.
- Lưu lịch sử thay đổi trong `appointment_histories`.

---

## 10. HỦY LỊCH

Thời hạn tự hủy:

```text
cancellation_cutoff_minutes = 90
```

Bệnh nhân chỉ được tự hủy khi còn ít nhất **1 giờ 30 phút** trước giờ hẹn.

Ví dụ lịch 14:00:

```text
12:30 → được hủy
12:31 → không được hủy
```

Khi hủy:

```text
BOOKED → CANCELLED
```

Lưu:

```text
cancelled_by
cancel_reason
cancelled_at
```

Frontend có thể disable nút khi không đủ điều kiện.

Backend vẫn bắt buộc kiểm tra lại.

---

## 11. ĐỔI LỊCH

Thời hạn tự đổi:

```text
reschedule_cutoff_minutes = 90
```

Bệnh nhân chỉ được đổi khi còn ít nhất **1 giờ 30 phút trước giờ hẹn hiện tại**.

Điều kiện tính theo lịch cũ:

```text
old_appointment_time - current_time >= 90 phút
```

Không tính theo slot mới.

Nếu đủ điều kiện, có thể đổi sang:

- Slot khác trong cùng buổi.
- Slot khác trong cùng ngày.
- Ngày khác.

Slot mới phải:

- Thuộc lịch làm việc của bác sĩ.
- Còn trống.
- Nằm trong tương lai.
- Không trùng Appointment khác.

Việc đổi phải nằm trong Transaction.

Nếu slot mới vừa bị người khác đặt:

- Đổi lịch thất bại.
- Lịch cũ vẫn được giữ nguyên.

Lưu lịch sử:

```text
old_date
old_start_time
new_date
new_start_time
changed_by
change_reason
changed_at
```

---

## 12. CHECK-IN

Khi bệnh nhân đến:

```text
BOOKED → CHECKED_IN
```

Lưu:

```text
checked_in_at
```

`CHECKED_IN` giúp phân biệt:

```text
Bệnh nhân chưa đến
```

và:

```text
Bệnh nhân đã đến nhưng đang chờ bác sĩ
```

---

## 13. NO_SHOW

Có thời gian chờ:

```text
no_show_grace_minutes = 15
```

Ví dụ:

```text
Lịch: 14:00
14:00 → BOOKED
14:10 → BOOKED
14:15+ → có thể đánh dấu NO_SHOW
```

Bác sĩ hoặc nhân viên xác nhận:

```text
BOOKED → NO_SHOW
```

Lưu:

```text
no_show_at
marked_by
```

Nếu bệnh nhân đã:

```text
CHECKED_IN
```

thì **không được đánh dấu NO_SHOW**.

---

## 14. BẮT ĐẦU KHÁM

Khi bác sĩ bắt đầu:

```text
CHECKED_IN → IN_PROGRESS
```

Lưu:

```text
actual_start_time
```

Giờ bắt đầu thực tế có thể khác giờ Appointment ban đầu.

---

## 15. XỬ LÝ BÁC SĨ KHÁM QUÁ 1 GIỜ

Slot 60 phút là **thời lượng dự kiến**, không phải giới hạn bắt buộc phải kết thúc.

Ví dụ:

```text
07:00 - 08:00 → Patient A
08:00 - 09:00 → Patient B
```

Nếu A khám đến 08:15:

```text
A = IN_PROGRESS
B = CHECKED_IN
```

Không được:

- Tự động kết thúc A lúc 08:00.
- Tự động chuyển B sang IN_PROGRESS.
- Tự động thay đổi giờ Appointment gốc của B.

Khi A hoàn tất lúc 08:15:

```text
A → COMPLETED
B → IN_PROGRESS
```

Appointment nên lưu cả giờ dự kiến và giờ thực tế:

```text
scheduled_start_time
scheduled_end_time
checked_in_at
actual_start_time
actual_end_time
```

Ví dụ:

```text
Patient B

scheduled_start_time = 08:00
scheduled_end_time   = 09:00
checked_in_at        = 07:55
actual_start_time    = 08:15
```

Có thể cấu hình cảnh báo:

```text
delay_warning_minutes = 30
```

Nếu bệnh nhân phải đổi lịch do bác sĩ/phòng khám trễ:

```text
reschedule_reason = DOCTOR_DELAY
```

Trường hợp này không được xem là NO_SHOW hoặc lỗi của bệnh nhân.

---

## 16. KẾT QUẢ KHÁM

Medical Record dự kiến chứa:

```text
diagnosis
treatment
treatment_plan
prescription
notes
follow_up_required
recommended_follow_up_date
follow_up_note
```

Sau khi khám, bác sĩ chọn:

```text
[ Không cần tái khám ]
hoặc
[ Cần tái khám ]
```

---

## 17. TÁI KHÁM

### Không cần tái khám

```text
follow_up_required = false
recommended_follow_up_date = null
```

Sau đó bác sĩ có thể Check-out.

### Cần tái khám

```text
follow_up_required = true
recommended_follow_up_date = ...
follow_up_note = ...
```

Bác sĩ có thể:

- Xem slot trống.
- Trao đổi với bệnh nhân.
- Tạo Appointment tái khám.

Không bắt buộc phải tạo lịch tái khám ngay.

---

## 18. TÁI KHÁM KHI FULL SLOT

Nếu ngày đề xuất đã kín:

```text
20/10 → FULL
```

Hệ thống hiển thị ngày/slot gần nhất.

Ví dụ:

```text
21/10
07:00
08:00
09:00

22/10
13:00
14:00
```

Không cho chen thêm Appointment vào slot đã có bệnh nhân.

Lịch tái khám vẫn sử dụng:

```text
slot_duration_minutes = 60
booking_min_advance_minutes = 180
```

nếu lịch được tạo theo quy trình đặt lịch thông thường.

---

## 19. FOLLOW-UP APPOINTMENT

Lịch tái khám là Appointment mới.

Ví dụ:

```text
Appointment #100
status = COMPLETED

        ↓

Appointment #125
status = BOOKED
previous_appointment_id = 100
```

Có thể lưu:

```text
previous_appointment_id
```

để theo dõi chuỗi điều trị.

---

## 20. CHECK-OUT

Sau khi bác sĩ:

- Nhập kết quả khám.
- Chọn có/không tái khám.
- Lưu thông tin cần thiết.

Bác sĩ bấm:

```text
CHECK-OUT / HOÀN TẤT KHÁM
```

Backend:

```text
IN_PROGRESS → COMPLETED
```

Lưu:

```text
actual_end_time
completed_at
completed_by
```

Check-out kết thúc **lần khám hiện tại**, không nhất thiết kết thúc toàn bộ quá trình điều trị.

---

## 21. AI CHATBOT - MỤC TIÊU

Chatbot có 4 nhóm chức năng chính:

1. Hỗ trợ nhận định tình trạng nha khoa từ triệu chứng.
2. Gợi ý bác sĩ phù hợp.
3. Hiển thị slot trống để bệnh nhân tự đặt lịch.
4. Tra cứu dịch vụ và bảng giá từ CSDL.

Chatbot không thay thế bác sĩ.

Không cho chatbot:

- Khẳng định chắc chắn chẩn đoán.
- Tự kê đơn thuốc.
- Tự tạo phác đồ điều trị.
- Tự bịa bác sĩ.
- Tự bịa dịch vụ.
- Tự bịa giá.
- Tự bịa slot.

---

## 22. CHATBOT - TƯ VẤN TRIỆU CHỨNG

Ví dụ người dùng:

```text
"Tôi đau răng."
```

Chatbot hỏi thêm:

- Vị trí đau.
- Mức độ đau.
- Thời gian đau.
- Sưng.
- Chảy máu.
- Ê buốt.
- Sốt.
- Các thông tin liên quan khác.

Sau đó:

```text
Symptoms Context
      ↓
Embedding
      ↓
pgvector Search
      ↓
Top-K
      ↓
Confidence Threshold
      ↓
Gemini
      ↓
Nhận định tham khảo
```

Cách diễn đạt:

```text
"Các triệu chứng bạn mô tả có thể liên quan đến..."
```

Không sử dụng dạng:

```text
"Bạn chắc chắn bị..."
```

---

## 23. PHÂN CHIA NGUỒN DỮ LIỆU CHATBOT

### Knowledge Base / RAG

Dùng cho:

- Triệu chứng.
- Bệnh lý.
- Nguyên nhân.
- Kiến thức nha khoa.
- Chăm sóc răng miệng.
- FAQ chuyên môn.

### PostgreSQL

Dùng cho dữ liệu động:

- Bác sĩ.
- Chuyên khoa.
- Dịch vụ.
- Bảng giá.
- Lịch làm việc.
- Slot trống.

Nguyên tắc:

```text
Knowledge Base
→ kiến thức chuyên môn

PostgreSQL
→ dữ liệu hiện tại của phòng khám

Gemini
→ tổng hợp và diễn đạt câu trả lời
```

---

## 24. GỢI Ý BÁC SĨ

Sau nhận định:

```text
Nhận định tình trạng
       ↓
Xác định chuyên khoa / dịch vụ
       ↓
Query PostgreSQL
       ↓
Danh sách bác sĩ phù hợp
```

Bác sĩ đề xuất có thể hiển thị:

- Tên.
- Chuyên khoa.
- Kinh nghiệm.
- Đánh giá.
- Dịch vụ liên quan.

Tên bác sĩ phải đến từ Database.

---

## 25. CHATBOT HIỂN THỊ SLOT

Luồng:

```text
Doctor
   ↓
Doctor Schedule
   ↓
Appointments
   ↓
Available Slots
```

Chatbot hiển thị slot để bệnh nhân tự chọn.

Chatbot **không tự tạo Appointment**.

Khi bệnh nhân bấm đặt:

```text
Backend kiểm tra lại:
- booking_min_advance_minutes = 180
- lịch làm việc
- slot còn trống
- UNIQUE constraint
- transaction
```

Sau đó mới:

```text
Appointment = BOOKED
```

---

## 26. DỊCH VỤ VÀ BẢNG GIÁ

Thông tin dịch vụ và giá lấy trực tiếp từ PostgreSQL.

Ví dụ Intent:

```text
SERVICE_INFO
SERVICE_PRICE
```

Query:

```text
dental_services
```

Dữ liệu:

```text
name
description
price
```

Nếu người dùng hỏi:

```text
"Giá cạo vôi răng bao nhiêu?"
```

Backend lấy `price` hiện tại từ Database.

Nếu không có giá:

- Không cho Gemini tự tạo một mức giá.
- Trả thông báo chưa có thông tin.

---

## 27. CHATBOT INTENT

Có thể sử dụng các nhóm Intent:

```text
SYMPTOM_CONSULTATION
FIND_DOCTOR
SERVICE_INFO
SERVICE_PRICE
```

Luồng:

```text
User Message
      ↓
Intent
      ├── SYMPTOM_CONSULTATION → RAG
      ├── FIND_DOCTOR          → PostgreSQL
      ├── SERVICE_INFO         → PostgreSQL
      └── SERVICE_PRICE        → PostgreSQL
```

---

## 28. CONFIDENCE THRESHOLD

Phần Retrieval phải có ngưỡng:

```text
topScore < threshold
```

Nếu không đủ độ liên quan:

```text
Không để AI tự suy diễn.
```

Ví dụ response:

```text
"Chưa có đủ thông tin phù hợp trong dữ liệu để đưa ra nhận định.
Bạn nên liên hệ hoặc đến phòng khám để được bác sĩ kiểm tra trực tiếp."
```

Threshold chủ yếu dùng cho Knowledge Base.

Dữ liệu bác sĩ, giá và slot được kiểm tra trực tiếp trong PostgreSQL.

---

## 29. CHAT HISTORY

Cấu trúc:

```text
User
 ├── ChatSession 1
 │    ├── ChatMessage
 │    └── ChatMessage
 ├── ChatSession 2
 └── ...
```

Lưu:

- User message.
- Bot message.
- Symptoms context.
- Suggested doctors.
- Dịch vụ đã tra cứu.
- Thời gian tạo.

Bệnh nhân có thể:

- Xem lại.
- Tiếp tục phiên chat cũ.

---

## 30. DATABASE TABLES DỰ KIẾN

### Authentication

```text
users
refresh_tokens
```

### Patient

```text
patients
```

### Doctor

```text
doctors
doctor_services
```

### Service

```text
dental_services
```

### Schedule

```text
doctor_schedules
doctor_schedule_overrides
```

### Appointment

```text
appointments
appointment_histories
```

### Medical

```text
medical_records
prescriptions
```

### Chatbot

```text
chat_sessions
chat_messages
vector_store
```

---

## 31. APPOINTMENTS - CÁC TRƯỜNG QUAN TRỌNG

Bảng `appointments` nên có tối thiểu các nhóm dữ liệu:

```text
id

patient_id
doctor_id
service_id

appointment_date
start_time
end_time

status

checked_in_at
actual_start_time
actual_end_time

cancelled_at
cancelled_by
cancel_reason

no_show_at
no_show_marked_by

completed_at
completed_by

previous_appointment_id

created_at
updated_at
```

Vì slot cố định 60 phút:

```text
end_time = start_time + 60 phút
```

Có thể lưu trực tiếp `end_time` để truy vấn và hiển thị thuận tiện.

---

## 32. APPOINTMENT HISTORY

Bảng `appointment_histories` dùng để ghi lại thay đổi.

Có thể chứa:

```text
appointment_id
action

old_date
old_start_time

new_date
new_start_time

changed_by
change_reason
changed_at
```

Ví dụ Action:

```text
RESCHEDULE
CANCEL
STATUS_CHANGE
```

---

## 33. CẤU HÌNH NGHIỆP VỤ

Nên đưa các giá trị nghiệp vụ vào cấu hình thay vì viết rải rác trong code.

Ví dụ:

```yaml
clinic:
  timezone: Asia/Ho_Chi_Minh

  appointment:
    slot-duration-minutes: 60
    booking-min-advance-minutes: 180
    cancellation-cutoff-minutes: 90
    reschedule-cutoff-minutes: 90
    no-show-grace-minutes: 15
    delay-warning-minutes: 30
```

Ý nghĩa:

```text
slot-duration-minutes
→ mỗi slot 60 phút

booking-min-advance-minutes
→ đặt trước ít nhất 3 giờ

cancellation-cutoff-minutes
→ hủy trước ít nhất 1 giờ 30 phút

reschedule-cutoff-minutes
→ đổi trước ít nhất 1 giờ 30 phút

no-show-grace-minutes
→ sau 15 phút mới được xác nhận NO_SHOW

delay-warning-minutes
→ cảnh báo khi bác sĩ trễ 30 phút
```

---

## 34. BACKEND MODULES

Các Controller chính:

```text
AuthController
PatientController
DoctorController
DentalServiceController
ScheduleController
AppointmentController
MedicalRecordController
ChatbotController
```

Các Service chính:

```text
AuthService
PatientService
DoctorService
DentalServiceService
ScheduleService
AppointmentService
MedicalRecordService
ChatbotService
RagService
KnowledgeIngestionService
```

Có thể thêm:

```text
SlotService
AppointmentHistoryService
```

để tách logic tính slot và lưu lịch sử.

---

## 35. BACKEND PROJECT STRUCTURE

```text
backend/
└── src/
    ├── main/
    │   ├── java/com/dentalclinic/
    │   │   ├── DentalClinicApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── CorsConfig.java
    │   │   │   ├── AiConfig.java
    │   │   │   └── ClinicProperties.java
    │   │   │
    │   │   ├── controller/
    │   │   │   ├── AuthController.java
    │   │   │   ├── PatientController.java
    │   │   │   ├── DoctorController.java
    │   │   │   ├── DentalServiceController.java
    │   │   │   ├── ScheduleController.java
    │   │   │   ├── AppointmentController.java
    │   │   │   ├── MedicalRecordController.java
    │   │   │   └── ChatbotController.java
    │   │   │
    │   │   ├── dto/
    │   │   ├── entity/
    │   │   ├── enums/
    │   │   ├── repository/
    │   │   ├── security/
    │   │   ├── exception/
    │   │   │
    │   │   └── service/
    │   │       ├── AuthService.java
    │   │       ├── PatientService.java
    │   │       ├── DoctorService.java
    │   │       ├── DentalServiceService.java
    │   │       ├── ScheduleService.java
    │   │       ├── SlotService.java
    │   │       ├── AppointmentService.java
    │   │       ├── AppointmentHistoryService.java
    │   │       ├── MedicalRecordService.java
    │   │       ├── ChatbotService.java
    │   │       ├── RagService.java
    │   │       └── KnowledgeIngestionService.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── db/migration/
    │       └── data/knowledge/
    │
    └── test/
```

---

## 36. FRONTEND PROJECT STRUCTURE

```text
frontend/
├── public/
└── src/
    ├── assets/
    ├── components/
    │   ├── common/
    │   ├── layout/
    │   ├── appointment/
    │   └── chatbot/
    │
    ├── pages/
    │   ├── public/
    │   ├── auth/
    │   ├── patient/
    │   ├── doctor/
    │   └── admin/
    │
    ├── routes/
    ├── services/
    ├── hooks/
    ├── context/
    ├── types/
    ├── App.tsx
    └── main.tsx
```

Các Frontend service dự kiến:

```text
authService
doctorService
dentalServiceService
scheduleService
appointmentService
medicalRecordService
chatbotService
```

---

## 37. DATASET STRUCTURE

```text
dataset/
├── knowledge/
│   ├── dental_diseases.csv
│   ├── treatments_faq.md
│   └── clinic_policies.json
│
└── benchmark/
    └── rag_benchmark_30.json
```

Benchmark tối thiểu:

```text
>= 30 câu hỏi
```

Nhóm đánh giá:

- Triệu chứng.
- Tìm bác sĩ.
- Dịch vụ.
- Bảng giá.
- Slot.
- Không có dữ liệu.
- Ngoài phạm vi.

---

## 38. MÔI TRƯỜNG PHÁT TRIỂN

### Backend

```text
Java 21
Spring Boot
Maven
IntelliJ IDEA
```

Chạy:

```bash
mvn spring-boot:run
```

Build:

```bash
mvn clean package
```

Chạy JAR:

```bash
java -jar target/<application>.jar
```

### Frontend

```text
Node.js 20+
npm
React
Vite
VS Code
```

Chạy:

```bash
npm install
npm run dev
```

Build:

```bash
npm run build
```

### Database

```text
PostgreSQL 16+
pgvector
pgAdmin hoặc DBeaver
```

### Source Control

```text
Git
GitHub
```

---

## 39. STACK CHỐT CUỐI CÙNG

```text
BACKEND
Java 21
Spring Boot 3.x
Spring Web
Spring Security
Spring Data JPA
Spring Transaction
JWT
BCrypt
Bean Validation
Spring AI
Flyway
Maven

FRONTEND
React
Vite
TypeScript
React Router
Axios
TanStack Query
React Hook Form
Zod

DATABASE
PostgreSQL 16+
pgvector

AI
Google Gemini API
RAG

STREAMING
SSE

SOURCE CONTROL
Git
GitHub
```

---

## 40. TÓM TẮT BUSINESS CONFIG CHỐT

```text
slot_duration_minutes          = 60
booking_min_advance_minutes    = 180
cancellation_cutoff_minutes    = 90
reschedule_cutoff_minutes      = 90
no_show_grace_minutes          = 15
delay_warning_minutes          = 30
clinic_timezone                = Asia/Ho_Chi_Minh
```

Luồng Appointment:

```text
BOOKED
   ↓
CHECKED_IN
   ↓
IN_PROGRESS
   ↓
COMPLETED
```

Ngoại lệ:

```text
BOOKED → CANCELLED
BOOKED → NO_SHOW
```

Luồng hệ thống:

```text
React
   ↓
REST API / SSE
   ↓
Spring Boot
   ├── Authentication & Authorization
   ├── Doctor / Patient / Service
   ├── Schedule / Slot
   ├── Appointment
   ├── Check-in / No-show
   ├── Medical Record
   ├── Follow-up / Check-out
   └── RAG Chatbot
          ↓
PostgreSQL + pgvector
          ↓
Google Gemini API
```

Đây là kiến trúc và tech stack được đồng bộ với các quy tắc nghiệp vụ hiện tại trong tài liệu chức năng V5.
