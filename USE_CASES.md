# CÁC CA CHỨC NĂNG CỦA HỆ THỐNG QUẢN LÝ PHÒNG KHÁM NHA KHOA

Tài liệu này mô tả các ca chức năng đang được triển khai trong Backend của hệ thống quản lý phòng khám nha khoa.

Hệ thống có năm nhóm tác nhân chính:

- Khách/người dùng chưa đăng nhập.
- Bệnh nhân (`PATIENT`).
- Bác sĩ (`DOCTOR`).
- Nhân viên lễ tân kiêm thu ngân (`STAFF`).
- Quản trị viên (`ADMIN`).

Lưu ý: hệ thống không sử dụng `username`. Người dùng đăng ký và đăng nhập bằng email. Mật khẩu phải có từ 8 đến 72 ký tự.

## A. KHÁCH VÀ NGƯỜI DÙNG CHUNG

### UC01 - Đăng ký tài khoản bệnh nhân

- **Tác nhân:** Khách.
- **Mục đích:** Tạo tài khoản bệnh nhân để sử dụng hệ thống.
- **Luồng chính:** Người dùng nhập email, mật khẩu và họ tên; hệ thống chuẩn hóa và kiểm tra email; mã hóa mật khẩu bằng BCrypt; tạo đồng thời User và Patient; trả về JWT Access Token.
- **Điều kiện:** Email đúng định dạng và chưa được sử dụng; họ tên từ 2 đến 150 ký tự; mật khẩu từ 8 đến 72 ký tự.
- **Ngoại lệ:** Email đã tồn tại hoặc dữ liệu không hợp lệ thì không tạo bất kỳ bản ghi nào.

### UC02 - Đăng nhập

- **Tác nhân:** Admin, Nhân viên, Bác sĩ, Bệnh nhân.
- **Mục đích:** Xác thực và nhận JWT để truy cập chức năng theo vai trò.
- **Luồng chính:** Người dùng nhập email và mật khẩu; hệ thống xác thực mật khẩu BCrypt; kiểm tra trạng thái tài khoản; trả Access Token, thông tin người dùng và vai trò.
- **Điều kiện:** Tài khoản tồn tại và đang ở trạng thái `ACTIVE`.
- **Ngoại lệ:** Sai thông tin đăng nhập trả HTTP 401; tài khoản bị vô hiệu hóa trả HTTP 403.
- **Ghi chú:** Hệ thống không có Refresh Token và API đăng xuất. Frontend đăng xuất bằng cách xóa Access Token.

### UC03 - Tra cứu bác sĩ

- **Tác nhân:** Khách hoặc người dùng đã đăng nhập.
- **Mục đích:** Tìm bác sĩ phù hợp với nhu cầu khám.
- **Luồng chính:** Người dùng xem danh sách; tìm theo tên, chuyên khoa hoặc số giấy phép; lọc theo chuyên khoa hoặc trạng thái làm việc; chọn bác sĩ để xem chi tiết.
- **Điều kiện:** Chỉ hiển thị bác sĩ có tài khoản đang hoạt động.
- **Kết quả:** Trả họ tên, chuyên khoa, giấy phép, tiểu sử, đánh giá, số năm kinh nghiệm và trạng thái làm việc.

### UC04 - Tra cứu dịch vụ nha khoa

- **Tác nhân:** Khách hoặc người dùng đã đăng nhập.
- **Mục đích:** Xem các dịch vụ và giá tham khảo.
- **Luồng chính:** Người dùng tìm kiếm dịch vụ; hệ thống trả danh sách phân trang; người dùng chọn dịch vụ để xem chi tiết.
- **Điều kiện:** Mặc định chỉ lấy dịch vụ đang hoạt động.
- **Kết quả:** Trả tên, mô tả, giá và trạng thái dịch vụ.

### UC05 - Tra cứu bác sĩ theo dịch vụ

- **Tác nhân:** Khách hoặc người dùng đã đăng nhập.
- **Mục đích:** Biết bác sĩ nào thực hiện được một dịch vụ.
- **Luồng chính:** Người dùng chọn dịch vụ; hệ thống đọc quan hệ `doctor_services`; trả danh sách bác sĩ được phân công.
- **Điều kiện:** Dịch vụ phải tồn tại; bác sĩ phải có tài khoản đang hoạt động.

### UC06 - Xem dịch vụ của bác sĩ

- **Tác nhân:** Khách hoặc người dùng đã đăng nhập.
- **Mục đích:** Biết một bác sĩ đang cung cấp những dịch vụ nào.
- **Luồng chính:** Người dùng chọn bác sĩ; hệ thống trả danh sách dịch vụ được gán cho bác sĩ.
- **Điều kiện:** Bác sĩ phải tồn tại và có tài khoản đang hoạt động.

### UC07 - Xem lịch làm việc và ngày nghỉ của bác sĩ

- **Tác nhân:** Khách hoặc người dùng đã đăng nhập.
- **Mục đích:** Xem lịch cố định và các ngày bác sĩ không làm việc.
- **Luồng chính:** Người dùng chọn bác sĩ; xem lịch theo thứ trong tuần; xem ngày nghỉ theo khoảng ngày.
- **Điều kiện:** Bác sĩ phải tồn tại.
- **Bảo mật:** API công khai ngày nghỉ chỉ trả ngày và buổi nghỉ, không công khai lý do nội bộ.

### UC08 - Xem khung giờ còn trống

- **Tác nhân:** Khách hoặc người dùng đã đăng nhập.
- **Mục đích:** Tìm thời gian có thể đặt lịch.
- **Luồng chính:** Người dùng chọn dịch vụ và bác sĩ; hệ thống trả lịch khả dụng dạng rolling 30 ngày hoặc sinh slot 60 phút cho một ngày cụ thể.
- **Điều kiện:** Bác sĩ và dịch vụ đang hoạt động; bác sĩ được phân công dịch vụ; ngày được chọn có lịch làm việc.
- **Quy tắc:** Hệ thống loại ngày nghỉ, slot đã có lịch và slot cách thời điểm hiện tại dưới 120 phút.
- **Trạng thái calendar:** Phân biệt `AVAILABLE`, `FULL`, `DAY_OFF`, `NOT_WORKING` và `UNAVAILABLE`; không trả thông tin bệnh nhân đã đặt lịch.

## B. CHỨC NĂNG BỆNH NHÂN

### UC09 - Xem hồ sơ cá nhân

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Xem thông tin cá nhân và tiền sử sức khỏe.
- **Luồng chính:** Bệnh nhân đăng nhập; hệ thống xác định Patient từ JWT; trả hồ sơ của chính bệnh nhân.
- **Điều kiện:** Có JWT hợp lệ với vai trò `PATIENT`.
- **Bảo mật:** Hệ thống không nhận Patient ID từ client để xác định quyền sở hữu.

### UC10 - Cập nhật hồ sơ cá nhân

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Cập nhật họ tên, ngày sinh, giới tính, số điện thoại, địa chỉ, tiền sử bệnh và dị ứng.
- **Luồng chính:** Bệnh nhân nhập thông tin; hệ thống kiểm tra dữ liệu; cập nhật User và Patient.
- **Điều kiện:** Họ tên từ 2 đến 150 ký tự; giới tính bắt buộc; số điện thoại tối đa 30 ký tự; ngày sinh không được nằm trong tương lai.
- **Bảo mật:** Bệnh nhân chỉ được cập nhật hồ sơ của chính mình.

### UC11 - Đặt lịch khám

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Đặt lịch với bác sĩ và dịch vụ đã chọn.
- **Luồng chính:** Bệnh nhân chọn bác sĩ, dịch vụ và slot; hệ thống khóa dữ liệu bác sĩ; kiểm tra lại slot; tạo Appointment `BOOKED`; ghi lịch sử `BOOK`.
- **Điều kiện:** Slot thuộc lịch làm việc; không rơi vào ngày nghỉ; phải đặt trước ít nhất 120 phút (2 giờ).
- **Ngoại lệ:** Nếu người khác vừa đặt cùng slot, request thua nhận HTTP 409 Conflict.
- **Kết quả:** Chỉ một bệnh nhân có thể đặt thành công cùng một slot.

### UC12 - Xem lịch hẹn cá nhân

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Theo dõi các lịch đã đặt.
- **Luồng chính:** Bệnh nhân xem danh sách phân trang; tìm kiếm; lọc theo trạng thái, bác sĩ, dịch vụ và khoảng ngày; chọn lịch để xem chi tiết.
- **Điều kiện:** Chỉ trả các lịch thuộc bệnh nhân đang đăng nhập.
- **Trạng thái hiện hành:** `BOOKED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`. Giá trị `CHECKED_IN` chỉ được giữ để tương thích dữ liệu lịch sử.

### UC13 - Hủy lịch hẹn

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Hủy lịch mà bệnh nhân không thể tham gia.
- **Luồng chính:** Bệnh nhân chọn lịch và nhập lý do; hệ thống khóa Appointment; kiểm tra điều kiện; chuyển `BOOKED` sang `CANCELLED`; ghi lịch sử `CANCEL`.
- **Điều kiện:** Lịch thuộc bệnh nhân, đang `BOOKED` và còn ít nhất 90 phút trước giờ hẹn.
- **Ngoại lệ:** Không thể hủy lịch đang khám, đã hoàn thành, đã hủy hoặc quá thời hạn.

### UC14 - Đổi lịch hẹn

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Chuyển lịch hiện tại sang slot khác.
- **Luồng chính:** Bệnh nhân chọn lịch, slot mới và nhập lý do; hệ thống khóa bác sĩ và Appointment; kiểm tra slot; cập nhật chính Appointment; ghi lịch sử `RESCHEDULE`.
- **Điều kiện:** Lịch đang `BOOKED`; còn ít nhất 90 phút tính từ giờ hẹn cũ; slot mới phải khác slot hiện tại và nằm trong tương lai.
- **Quy tắc:** Không tạo Appointment mới; không áp dụng quy tắc đặt trước 120 phút cho slot đổi lịch.
- **Ngoại lệ:** Nếu slot mới bị tranh chấp, lịch cũ vẫn được giữ nguyên.

### UC15 - Xem lịch sử khám bệnh

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Xem kết quả các lần khám đã hoàn thành.
- **Luồng chính:** Bệnh nhân xem danh sách bệnh án phân trang; chọn một Appointment để xem chi tiết.
- **Kết quả:** Hiển thị bác sĩ, dịch vụ, chẩn đoán, điều trị, kế hoạch điều trị, ghi chú, đơn thuốc và hướng dẫn tái khám.
- **Điều kiện:** Chỉ hiển thị bệnh án của Appointment `COMPLETED` thuộc bệnh nhân đang đăng nhập.

### UC16 - Xem phiếu thanh toán cá nhân

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Theo dõi chi phí điều trị và tình trạng thanh toán.
- **Luồng chính:** Bệnh nhân xem danh sách phiếu; chọn phiếu để xem các dịch vụ, số lượng, đơn giá, thành tiền và tổng tiền.
- **Điều kiện:** Chỉ xem phiếu của chính bệnh nhân.
- **Bảo mật:** Phiếu `DRAFT` không hiển thị; chỉ hiển thị `UNPAID` hoặc `PAID`.

## C. CHỨC NĂNG BÁC SĨ

### UC17 - Xem lịch làm việc cá nhân

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Xem lịch làm việc cố định theo tuần đã được Admin thiết lập.
- **Luồng chính:** Bác sĩ đăng nhập; hệ thống xác định hồ sơ bác sĩ từ JWT; trả lịch làm việc của chính bác sĩ.
- **Bảo mật:** Bác sĩ chỉ có quyền xem, không được tạo, cập nhật hoặc xóa lịch tuần.

### UC18 - Xem danh sách lịch khám được phân công

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Theo dõi bệnh nhân cần khám.
- **Luồng chính:** Bác sĩ xem toàn bộ lịch hoặc lịch hôm nay; tìm kiếm và lọc theo trạng thái, bệnh nhân, dịch vụ và khoảng ngày.
- **Điều kiện:** Chỉ trả các Appointment thuộc bác sĩ đang đăng nhập.

### UC20 - Bắt đầu khám

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Bắt đầu quá trình khám và điều trị.
- **Luồng chính:** Bác sĩ chọn lịch đã đặt; hệ thống khóa bác sĩ và Appointment; chuyển trực tiếp `BOOKED` sang `IN_PROGRESS`; lưu `actual_start_at`; ghi lịch sử `START_EXAM`.
- **Điều kiện:** Appointment thuộc bác sĩ và đang `BOOKED`.
- **Ngoại lệ:** Một bác sĩ không thể có đồng thời hai ca `IN_PROGRESS`.
- **Tương thích dữ liệu cũ:** Appointment lịch sử đang `CHECKED_IN` vẫn được phép chuyển sang `IN_PROGRESS`, nhưng hệ thống không còn cung cấp API check-in mới.

### UC21 - Xác nhận bệnh nhân không đến

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Đánh dấu bệnh nhân vắng mặt.
- **Luồng chính:** Sau thời gian chờ, bác sĩ chọn No-show; hệ thống chuyển `BOOKED` sang `NO_SHOW`; ghi lịch sử `NO_SHOW`.
- **Điều kiện:** Lịch thuộc bác sĩ; vẫn đang `BOOKED`; đã qua giờ hẹn ít nhất 15 phút.
- **Ngoại lệ:** Không được đánh dấu No-show khi bác sĩ đã bắt đầu khám. Dữ liệu lịch sử đã check-in cũng được bảo vệ.

### UC22 - Tạo bệnh án

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Ghi nhận kết quả khám và điều trị.
- **Luồng chính:** Bác sĩ nhập chẩn đoán, điều trị, kế hoạch, ghi chú, đơn thuốc và thông tin tái khám; hệ thống lưu MedicalRecord.
- **Điều kiện:** Appointment thuộc bác sĩ, đang `IN_PROGRESS` và chưa có bệnh án.
- **Quy tắc:** Mỗi Appointment có tối đa một MedicalRecord; đơn thuốc được lưu dưới dạng JSONB.
- **Ngoại lệ:** Nếu yêu cầu tái khám thì bắt buộc phải có ngày tái khám đề xuất; ghi chú không thay thế ngày đề xuất.

### UC23 - Cập nhật bệnh án

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Chỉnh sửa bệnh án trước khi hoàn thành khám.
- **Luồng chính:** Bác sĩ mở bệnh án; sửa thông tin; hệ thống kiểm tra và cập nhật.
- **Điều kiện:** Appointment thuộc bác sĩ, đang `IN_PROGRESS` và đã có MedicalRecord.
- **Ngoại lệ:** Không sửa bệnh án sau khi ca khám đã hoàn thành.

### UC24 - Ghi nhận dịch vụ thực tế

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Ghi các dịch vụ thực sự đã thực hiện để lập phiếu thanh toán.
- **Luồng chính:** Bác sĩ gửi danh sách `serviceId` và số lượng; hệ thống lấy giá từ database; lưu tên, đơn giá, số lượng và thành tiền vào Payment `DRAFT`.
- **Điều kiện:** Appointment thuộc bác sĩ và đang `IN_PROGRESS`; mỗi số lượng từ 1 đến 100; dịch vụ mới phải đang hoạt động và được gán cho bác sĩ.
- **Bảo mật:** Frontend không được gửi giá; hệ thống không tin giá do client tính.
- **Ngoại lệ:** Không cho phép trùng dịch vụ hoặc chỉnh sửa phiếu sau khi đã chốt.

### UC25 - Xem phiếu thanh toán của ca khám

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Kiểm tra dịch vụ và tổng tiền trước khi hoàn thành khám.
- **Luồng chính:** Bác sĩ chọn Appointment; hệ thống trả Payment tương ứng.
- **Điều kiện:** Appointment và Payment phải thuộc bác sĩ đang đăng nhập.

### UC26 - Hoàn tất khám

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Kết thúc ca khám và chuyển sang bước thu tiền.
- **Luồng chính:** Hệ thống kiểm tra Appointment, MedicalRecord và Payment; chuyển Payment `DRAFT` sang `UNPAID`; chuyển Appointment `IN_PROGRESS` sang `COMPLETED`; lưu `actual_end_at`; ghi lịch sử `COMPLETE`.
- **Điều kiện:** Có bệnh án hợp lệ; có ít nhất một dịch vụ thực tế; tổng tiền khớp với các item.
- **Transaction:** Hoàn thành Appointment, chốt Payment và ghi audit trong cùng giao dịch. Nếu một bước lỗi thì toàn bộ thay đổi được rollback.

### UC27 - Xem slot để tự đặt tái khám

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Chọn khung giờ phù hợp sau khi bác sĩ đã khuyến nghị tái khám.
- **Luồng chính:** Bệnh nhân xem hướng dẫn trong bệnh án; chọn dịch vụ, bác sĩ và ngày; sử dụng API slot công khai giống đặt lịch thường.
- **Điều kiện:** Lần khám trước đã `COMPLETED` và MedicalRecord có `followUpRequired = true` cùng ngày tái khám đề xuất.

### UC28 - Bệnh nhân tự đặt lịch tái khám

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Chủ động đặt lịch tái khám theo khuyến nghị điều trị.
- **Luồng chính:** Bệnh nhân gửi yêu cầu đặt lịch bình thường kèm `previousAppointmentId`; hệ thống xác minh quyền sở hữu lần khám cũ, trạng thái hoàn thành và khuyến nghị tái khám; tạo Appointment mới `BOOKED`; liên kết `previous_appointment_id`; ghi lịch sử `BOOK`.
- **Điều kiện:** Slot còn trống; bác sĩ cung cấp dịch vụ; đáp ứng quy tắc đặt trước 120 phút (2 giờ); chưa có lịch tái khám đang hoạt động cho cùng lần khám cũ.
- **Ngoại lệ:** Không được dùng Appointment của bệnh nhân khác; hai request cho cùng slot hoặc cùng khuyến nghị tái khám chỉ một request thành công.

## D. CHỨC NĂNG QUẢN TRỊ VIÊN

### UC29 - Tra cứu tài khoản

- **Tác nhân:** Admin.
- **Mục đích:** Quản lý người dùng hệ thống.
- **Luồng chính:** Admin xem danh sách phân trang; tìm theo email hoặc họ tên; lọc theo vai trò và trạng thái; chọn tài khoản để xem chi tiết.
- **Điều kiện:** Có JWT hợp lệ với vai trò `ADMIN`.

### UC30 - Thay đổi trạng thái tài khoản

- **Tác nhân:** Admin.
- **Mục đích:** Kích hoạt hoặc vô hiệu hóa tài khoản.
- **Luồng chính:** Admin chọn tài khoản; đổi trạng thái `ACTIVE` hoặc `INACTIVE`; hệ thống cập nhật trạng thái.
- **Điều kiện bảo vệ:** Admin không thể tự vô hiệu hóa chính mình; hệ thống luôn phải còn ít nhất một Admin đang hoạt động.
- **Cơ chế:** Sử dụng transaction và PostgreSQL advisory lock để tránh hai Admin đồng thời vô hiệu hóa hết tài khoản quản trị.

### UC31 - Tạo tài khoản bác sĩ

- **Tác nhân:** Admin.
- **Mục đích:** Thêm bác sĩ mới vào hệ thống.
- **Luồng chính:** Admin nhập email, mật khẩu, họ tên, chuyên khoa, giấy phép, tiểu sử và kinh nghiệm; hệ thống tạo User `DOCTOR`, Doctor và 12 DoctorSchedule mặc định trong một transaction.
- **Điều kiện:** Email và số giấy phép chưa tồn tại; mật khẩu từ 8 đến 72 ký tự; số năm kinh nghiệm không âm.
- **Kết quả:** Tài khoản và trạng thái làm việc mặc định là `ACTIVE`; lịch gồm Thứ Hai-Thứ Bảy, mỗi ngày `MORNING 08:00-11:00` và `AFTERNOON 13:00-17:00`.
- **Transaction:** Nếu bất kỳ DoctorSchedule nào không lưu được thì User, Doctor và toàn bộ lịch đều rollback.

### UC32 - Cập nhật hồ sơ bác sĩ

- **Tác nhân:** Admin.
- **Mục đích:** Chỉnh sửa thông tin chuyên môn của bác sĩ.
- **Luồng chính:** Admin cập nhật họ tên, chuyên khoa, giấy phép, tiểu sử, đánh giá và kinh nghiệm.
- **Điều kiện:** Giấy phép không trùng; đánh giá từ 0 đến 5, tối đa một chữ số thập phân; kinh nghiệm không âm.

### UC33 - Thay đổi trạng thái làm việc của bác sĩ

- **Tác nhân:** Admin.
- **Mục đích:** Cho phép hoặc ngừng nhận lịch đối với bác sĩ.
- **Luồng chính:** Admin chọn trạng thái làm việc; hệ thống cập nhật `Doctor.workingStatus`.
- **Ảnh hưởng:** Bác sĩ không ở trạng thái `ACTIVE` sẽ không sinh slot mới và không thể được đặt lịch.
- **Lưu ý:** Trạng thái làm việc của Doctor khác với trạng thái tài khoản User.

### UC34 - Quản lý lịch làm việc tuần của bác sĩ

- **Tác nhân:** Admin.
- **Mục đích:** Xem và điều chỉnh lịch tuần đã được hệ thống tự tạo cho bác sĩ.
- **Luồng chính:** Admin chọn bác sĩ; xem, tạo lại buổi đã xóa, cập nhật hoặc xóa lịch tuần của bác sĩ đó.
- **Điều kiện:** Chỉ Thứ Hai-Thứ Bảy; `MORNING` nằm trong `08:00-11:00`; `AFTERNOON` nằm trong `13:00-17:00`; thời gian khớp ranh giới slot 60 phút.
- **Ngoại lệ:** Không được có hai lịch trùng cùng thứ và cùng buổi; lịch phải thuộc đúng bác sĩ; không được sửa/xóa nếu làm mất Appointment tương lai đang hoạt động.
- **Ngày nghỉ:** Nghỉ một ngày hoặc một buổi cụ thể phải dùng `doctor_days_off`, không xóa lịch tuần.
- **Bảo mật:** Chỉ Admin được tạo, cập nhật và xóa lịch tuần; bác sĩ chỉ được xem lịch của mình.

### UC35 - Tạo dịch vụ nha khoa

- **Tác nhân:** Admin.
- **Mục đích:** Thêm dịch vụ mới.
- **Luồng chính:** Admin nhập tên, mô tả và giá; hệ thống kiểm tra; tạo dịch vụ ở trạng thái hoạt động.
- **Điều kiện:** Tên không trùng; giá không âm và đúng định dạng `NUMERIC(14,2)`.

### UC36 - Cập nhật dịch vụ nha khoa

- **Tác nhân:** Admin.
- **Mục đích:** Chỉnh sửa tên, mô tả hoặc giá dịch vụ.
- **Luồng chính:** Admin chọn dịch vụ; cập nhật dữ liệu; hệ thống kiểm tra và lưu thay đổi.
- **Điều kiện:** Tên mới không trùng và giá hợp lệ.
- **Lưu ý:** Giá mới không làm thay đổi snapshot giá trong các Payment đã ghi nhận trước đó.

### UC37 - Thay đổi trạng thái dịch vụ

- **Tác nhân:** Admin.
- **Mục đích:** Tạm ngừng hoặc kích hoạt dịch vụ.
- **Luồng chính:** Admin cập nhật `is_active` của dịch vụ.
- **Ảnh hưởng:** Dịch vụ ngừng hoạt động không được dùng cho slot hoặc lịch đặt mới.
- **Quy tắc:** Không hard delete dịch vụ đã được sử dụng.

### UC38 - Phân công dịch vụ cho bác sĩ

- **Tác nhân:** Admin.
- **Mục đích:** Xác định những dịch vụ một bác sĩ được phép thực hiện.
- **Luồng chính:** Admin gửi toàn bộ danh sách Service ID; hệ thống thay thế mapping hiện tại trong `doctor_services`.
- **Điều kiện:** Bác sĩ và tất cả dịch vụ phải tồn tại; danh sách không chứa ID trùng.
- **Ngoại lệ:** Không xóa mapping nếu việc xóa vi phạm dữ liệu lịch hẹn đang tham chiếu.

### UC39 - Xem trước ảnh hưởng ngày nghỉ

- **Tác nhân:** Admin.
- **Mục đích:** Kiểm tra các lịch hẹn bị ảnh hưởng trước khi đăng ký ngày nghỉ.
- **Luồng chính:** Admin chọn bác sĩ, ngày, buổi nghỉ và lý do; hệ thống phân loại lịch sẽ hủy, lịch đang khám gây chặn và lịch cần xem xét thủ công.
- **Điều kiện:** Ngày nghỉ và buổi nghỉ chưa tồn tại.
- **Kết quả:** Chỉ xem trước, chưa thay đổi dữ liệu.

### UC40 - Đăng ký ngày nghỉ bác sĩ

- **Tác nhân:** Admin.
- **Mục đích:** Ngăn đặt lịch vào ngày bác sĩ nghỉ.
- **Luồng chính:** Admin xác nhận ngày nghỉ; hệ thống khóa bác sĩ và lịch liên quan; tạo ngày nghỉ; tự động hủy các lịch `BOOKED` trong tương lai; ghi lịch sử `CANCEL`.
- **Buổi nghỉ:** `MORNING`, `AFTERNOON` hoặc `FULL_DAY`.
- **Ngoại lệ:** Không tạo ngày nghỉ nếu có Appointment `CHECKED_IN` hoặc `IN_PROGRESS`.
- **Lưu ý:** Chỉ Admin quản lý ngày nghỉ; bác sĩ không có API tự đăng ký ngày nghỉ.

### UC41 - Xem và xóa ngày nghỉ bác sĩ

- **Tác nhân:** Admin.
- **Mục đích:** Theo dõi hoặc gỡ ngày nghỉ đã khai báo.
- **Luồng chính:** Admin xem danh sách ngày nghỉ theo khoảng ngày; chọn bản ghi; xóa ngày nghỉ.
- **Điều kiện:** Ngày nghỉ phải thuộc đúng bác sĩ.
- **Lưu ý:** Xóa ngày nghỉ không tự khôi phục những Appointment đã bị hủy trước đó.

### UC42 - Tra cứu toàn bộ lịch hẹn

- **Tác nhân:** Admin.
- **Mục đích:** Theo dõi hoạt động đặt và khám bệnh trong toàn hệ thống.
- **Luồng chính:** Admin tìm kiếm; lọc theo trạng thái, bệnh nhân, bác sĩ, dịch vụ và khoảng ngày; xem kết quả phân trang.
- **Kết quả:** Trả dữ liệu Appointment của toàn phòng khám.

### UC43 - Xem lịch sử thay đổi Appointment

- **Tác nhân:** Admin.
- **Mục đích:** Kiểm tra audit của một lịch hẹn.
- **Luồng chính:** Admin chọn Appointment; hệ thống trả lịch sử thay đổi theo thứ tự thời gian.
- **Nội dung:** `BOOK`, `CANCEL`, `RESCHEDULE`, `CHECK_IN`, `START_EXAM`, `COMPLETE`, `NO_SHOW`, người thực hiện, lý do, thời điểm và chi tiết JSONB.

### UC44 - Xem dashboard tổng quan

- **Tác nhân:** Admin.
- **Mục đích:** Theo dõi tình hình hoạt động trong ngày.
- **Kết quả:** Tổng số bệnh nhân, tổng số bác sĩ, số lịch hôm nay, số lịch hoàn thành, số lịch đã hủy và số lịch No-show.
- **Quy tắc:** Số liệu ngày được tính theo múi giờ `Asia/Ho_Chi_Minh`.

### UC45 - Tra cứu phiếu thanh toán

- **Tác nhân:** Nhân viên; Admin là vai trò dự phòng.
- **Mục đích:** Theo dõi các khoản phải thu và đã thu.
- **Luồng chính:** Admin tìm theo bệnh nhân hoặc bác sĩ; lọc trạng thái và ngày; sắp xếp và phân trang; chọn phiếu để xem chi tiết.
- **Kết quả:** Hiển thị bệnh nhân, bác sĩ, dịch vụ gốc và dịch vụ bổ sung, tổng tiền, trạng thái và thông tin xác nhận.

### UC46 - Xác nhận đã thu tiền

- **Tác nhân:** Nhân viên; Admin là vai trò dự phòng.
- **Mục đích:** Ghi nhận bệnh nhân đã thanh toán trực tiếp tại phòng khám.
- **Luồng chính:** Nhân viên chọn Payment `UNPAID`; chọn `CASH` hoặc `BANK_TRANSFER`; hệ thống khóa Payment; kiểm tra tổng tiền; chuyển `UNPAID` sang `PAID`; lưu người xác nhận và `paid_at`.
- **Điều kiện:** Appointment đã `COMPLETED`; tài khoản người thu tiền đang hoạt động; Payment đang `UNPAID`.
- **Concurrency:** Hai người xác nhận đồng thời chỉ một request thành công.
- **Lưu ý:** `BANK_TRANSFER` là xác nhận thủ công, không phải cổng thanh toán trực tuyến.

### UC47 - Xem thống kê doanh thu

- **Tác nhân:** Admin.
- **Mục đích:** Theo dõi doanh thu thực nhận.
- **Luồng chính:** Admin chọn khoảng ngày hoặc tháng; hệ thống tổng hợp dữ liệu theo `paid_at`.
- **Kết quả:** Doanh thu hôm nay, doanh thu theo khoảng ngày, doanh thu tháng, số phiếu `PAID`, số phiếu `UNPAID` và tổng tiền chưa thu.
- **Quy tắc:** Chỉ Payment `PAID` được tính là doanh thu.

### UC48 - Tạo tài khoản nhân viên

- **Tác nhân:** Admin.
- **Mục đích:** Cấp tài khoản cho nhân viên lễ tân kiêm thu ngân.
- **Luồng chính:** Admin nhập email, mật khẩu và họ tên; hệ thống kiểm tra dữ liệu; mã hóa BCrypt; tạo User có vai trò `STAFF` và trạng thái `ACTIVE`.
- **Điều kiện:** Email chưa tồn tại; mật khẩu từ 8 đến 72 ký tự; Admin đang hoạt động.
- **Bảo mật:** Không có API công khai để tự đăng ký vai trò `STAFF`.

### UC49 - Quản lý tài khoản nhân viên

- **Tác nhân:** Admin.
- **Mục đích:** Tra cứu và kích hoạt hoặc vô hiệu hóa nhân viên.
- **Luồng chính:** Admin xem danh sách, tìm theo email hoặc họ tên, lọc trạng thái, xem chi tiết và cập nhật `ACTIVE`/`INACTIVE`.
- **Điều kiện:** Endpoint nhân viên chỉ thao tác trên tài khoản có vai trò `STAFF`.
- **Ảnh hưởng:** Nhân viên `INACTIVE` không thể đăng nhập hoặc sử dụng API nghiệp vụ.

## E. CHỨC NĂNG NHÂN VIÊN

### UC50 - Tra cứu toàn bộ lịch khám để hướng dẫn bệnh nhân

- **Tác nhân:** Nhân viên.
- **Mục đích:** Tìm lịch hẹn và hướng dẫn bệnh nhân đến đúng bác sĩ.
- **Luồng chính:** Nhân viên tìm theo tên/số điện thoại bệnh nhân, tên bác sĩ hoặc dịch vụ; lọc trạng thái, bác sĩ, dịch vụ và khoảng ngày; xem kết quả phân trang.
- **Kết quả:** Trả thông tin lịch hẹn, bệnh nhân, bác sĩ, dịch vụ và thời gian khám của toàn phòng khám.
- **Bảo mật:** Nhân viên không có quyền đọc hoặc sửa MedicalRecord, đơn thuốc hay thông tin điều trị.

### UC51 - Tra cứu phiếu cần thu và đã thu

- **Tác nhân:** Nhân viên.
- **Mục đích:** Xác định đúng bệnh nhân và khoản tiền cần thu tại quầy.
- **Luồng chính:** Nhân viên tìm theo tên/số điện thoại bệnh nhân hoặc tên bác sĩ; lọc `UNPAID`/`PAID`, ngày; xem chi tiết dịch vụ và lịch khám.
- **Bảo mật:** Payment `DRAFT` không hiển thị cho nhân viên vì ca khám chưa hoàn tất.

### UC52 - Xác nhận thanh toán tại quầy

- **Tác nhân:** Nhân viên.
- **Mục đích:** Ghi nhận khoản tiền thực nhận.
- **Luồng chính:** Nhân viên mở Payment `UNPAID`; chọn `CASH` hoặc `BANK_TRANSFER`; xác nhận; hệ thống chuyển sang `PAID`, lưu nhân viên xác nhận và thời điểm thu.
- **Điều kiện:** Payment có tổng tiền hợp lệ và Appointment đã hoàn tất.
- **Concurrency:** Khóa bi quan đảm bảo chỉ một người có thể xác nhận thành công cùng một Payment.

## F. LUỒNG TRẠNG THÁI QUAN TRỌNG

### Luồng Appointment

```text
BOOKED ── bác sĩ bắt đầu khám ──→ IN_PROGRESS ──→ COMPLETED
   │
   ├──→ CANCELLED
   └──→ NO_SHOW
```

`CHECKED_IN` chỉ còn để đọc và xử lý dữ liệu lịch sử; hệ thống không cung cấp API tạo trạng thái này.

### Luồng Payment

```text
DRAFT ── hoàn thành khám ──→ UNPAID ── Staff thu tiền ──→ PAID
```

Admin vẫn có endpoint xác nhận thanh toán làm phương án dự phòng.

## G. CHỨC NĂNG CHƯA CÓ TRONG HỆ THỐNG

- Đăng ký tài khoản Doctor hoặc Admin từ trang công khai.
- Thanh toán trực tuyến và tích hợp cổng thanh toán.
- Webhook ngân hàng, hoàn tiền hoặc trả góp.
- Hóa đơn kế toán.
- Frontend React.
- AI Chatbot, Google Gemini, RAG và tư vấn triệu chứng.

## H. QUẢN LÝ PHIÊN ĐĂNG NHẬP VÀ BẢN NHÁP BỆNH ÁN

### UC53 - Đăng nhập và tạo phiên phía Backend

- **Tác nhân:** Admin, Bác sĩ, Nhân viên, Bệnh nhân.
- **Mục đích:** Tạo phiên đăng nhập có thể bị khóa, hết hạn hoặc thu hồi độc lập với Access Token.
- **Luồng chính:** Người dùng đăng nhập bằng email và mật khẩu; Backend kiểm tra tài khoản; tạo `auth_sessions`; phát Access Token 15 phút; gửi Refresh Token bằng cookie HttpOnly và CSRF token bằng cookie riêng.
- **Thời hạn tuyệt đối:** Admin 8 giờ; Doctor/Staff 12 giờ; Patient 7 ngày, tính từ lần đăng nhập ban đầu.
- **Bảo mật:** PostgreSQL chỉ lưu hash Refresh Token; Refresh Token không xuất hiện trong JSON response.

### UC54 - Làm mới Access Token

- **Tác nhân:** Admin, Bác sĩ, Nhân viên, Bệnh nhân.
- **Mục đích:** Tiếp tục phiên hợp lệ mà không nhập lại mật khẩu sau mỗi 15 phút.
- **Luồng chính:** Client gửi cookie Refresh Token, cookie `XSRF-TOKEN` và header `X-XSRF-TOKEN`; Backend khóa phiên và token; kiểm tra trạng thái/tài khoản/thời hạn; đánh dấu token cũ đã dùng; phát token mới.
- **Quy tắc:** Refresh Token Rotation bắt buộc; refresh không thay đổi thời hạn tuyệt đối hoặc `last_activity_at`.
- **Concurrency:** Khóa bi quan theo phiên đảm bảo hai refresh đồng thời không tạo hai token hiện hành.
- **Ngoại lệ:** Tái sử dụng token đã tiêu thụ sẽ thu hồi toàn bộ phiên.

### UC55 - Ghi nhận hoạt động thực sự và khóa phiên

- **Tác nhân:** Admin, Bác sĩ, Nhân viên.
- **Mục đích:** Khóa màn hình khi người dùng rời máy nhưng chưa hết ca làm việc.
- **Luồng chính:** Frontend chỉ gọi `/api/auth/activity` khi có tương tác thực sự; Backend cập nhật thời điểm hoạt động cuối.
- **Ngưỡng:** Admin 15 phút; Doctor/Staff 20 phút; Patient không áp dụng.
- **Quy tắc:** Request nền, refresh token và autosave bệnh án không tự cập nhật hoạt động.
- **Kết quả:** Khi quá ngưỡng, API nghiệp vụ trả `SESSION_LOCKED` và không thực thi nghiệp vụ.

### UC56 - Mở khóa phiên

- **Tác nhân:** Admin, Bác sĩ, Nhân viên.
- **Mục đích:** Tiếp tục phiên đang bị khóa mà không tạo phiên đăng nhập mới.
- **Luồng chính:** Người dùng nhập lại mật khẩu; Backend kiểm tra Refresh Token, CSRF, tài khoản và thời hạn tuyệt đối; xác minh BCrypt; kích hoạt lại phiên và rotation token.
- **Quy tắc:** Mở khóa không kéo dài thời hạn tuyệt đối ban đầu.
- **Ngoại lệ:** Phiên đã hết hạn hoặc bị thu hồi bắt buộc đăng nhập lại.

### UC57 - Đăng xuất và thu hồi phiên

- **Tác nhân:** Admin, Bác sĩ, Nhân viên, Bệnh nhân.
- **Mục đích:** Chấm dứt phiên ở Backend, không chỉ xóa dữ liệu phía trình duyệt.
- **Luồng chính:** Client gửi Refresh Token và CSRF hợp lệ; Backend chuyển phiên sang `REVOKED`, thu hồi các token liên quan và xóa cookie ở response.
- **Ảnh hưởng:** Access Token chưa hết 15 phút cũng bị từ chối vì mỗi request bảo vệ đều kiểm tra phiên phía Backend.
- **Tài khoản vô hiệu hóa:** Mọi phiên còn hoạt động của tài khoản bị thu hồi.

### UC58 - Xem trạng thái phiên hiện tại

- **Tác nhân:** Admin, Bác sĩ, Nhân viên, Bệnh nhân.
- **Mục đích:** Cho client biết role, trạng thái, thời hạn tuyệt đối, hoạt động gần nhất và chính sách Idle Lock của phiên.
- **Bảo mật:** Endpoint yêu cầu Access Token và chỉ trả phiên gắn với chính token đó.

### UC59 - Lưu và khôi phục bản nháp bệnh án

- **Tác nhân:** Bác sĩ phụ trách Appointment.
- **Mục đích:** Lưu nội dung khám chưa hoàn chỉnh lên PostgreSQL để tiếp tục sau khi mở khóa hoặc tải lại trang.
- **Luồng chính:** Bác sĩ đang khám gửi dữ liệu có thể chưa đầy đủ; hệ thống upsert `medical_record_drafts`; bác sĩ đọc lại qua Appointment tương ứng.
- **Điều kiện:** Appointment thuộc bác sĩ đăng nhập và đang `IN_PROGRESS`.
- **Bảo mật:** Patient, Staff và bác sĩ khác không được đọc hoặc sửa bản nháp.
- **Quy tắc:** Bản nháp không được dùng để tự động hoàn thành khám; khi lưu bệnh án chính thức thành công, bản nháp tương ứng bị xóa.
