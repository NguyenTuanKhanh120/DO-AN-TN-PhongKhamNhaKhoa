# CÁC CA CHỨC NĂNG CỦA HỆ THỐNG QUẢN LÝ PHÒNG KHÁM NHA KHOA

Tài liệu này mô tả các ca chức năng đang được triển khai trong Backend của hệ thống quản lý phòng khám nha khoa.

Hệ thống có bốn nhóm tác nhân chính:

- Khách/người dùng chưa đăng nhập.
- Bệnh nhân (`PATIENT`).
- Bác sĩ (`DOCTOR`).
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

- **Tác nhân:** Admin, Bác sĩ, Bệnh nhân.
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
- **Luồng chính:** Người dùng chọn dịch vụ, bác sĩ và ngày khám; hệ thống sinh các slot 60 phút từ lịch làm việc.
- **Điều kiện:** Bác sĩ và dịch vụ đang hoạt động; bác sĩ được phân công dịch vụ; ngày được chọn có lịch làm việc.
- **Quy tắc:** Hệ thống loại ngày nghỉ, slot đã có lịch và slot cách thời điểm hiện tại dưới 120 phút.

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
- **Trạng thái:** `BOOKED`, `CHECKED_IN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`.

### UC13 - Hủy lịch hẹn

- **Tác nhân:** Bệnh nhân.
- **Mục đích:** Hủy lịch mà bệnh nhân không thể tham gia.
- **Luồng chính:** Bệnh nhân chọn lịch và nhập lý do; hệ thống khóa Appointment; kiểm tra điều kiện; chuyển `BOOKED` sang `CANCELLED`; ghi lịch sử `CANCEL`.
- **Điều kiện:** Lịch thuộc bệnh nhân, đang `BOOKED` và còn ít nhất 90 phút trước giờ hẹn.
- **Ngoại lệ:** Không thể hủy lịch đã check-in, đang khám, đã hoàn thành, đã hủy hoặc quá thời hạn.

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

### UC19 - Check-in bệnh nhân

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Xác nhận bệnh nhân đã đến phòng khám.
- **Luồng chính:** Bác sĩ chọn lịch; hệ thống khóa Appointment; chuyển `BOOKED` sang `CHECKED_IN`; lưu `checked_in_at`; ghi lịch sử `CHECK_IN`.
- **Điều kiện:** Appointment thuộc bác sĩ và đang `BOOKED`.
- **Ngoại lệ:** Không check-in lịch đã hủy, No-show hoặc đã chuyển sang trạng thái khác.

### UC20 - Bắt đầu khám

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Bắt đầu quá trình khám và điều trị.
- **Luồng chính:** Bác sĩ chọn lịch đã check-in; hệ thống khóa bác sĩ và Appointment; chuyển `CHECKED_IN` sang `IN_PROGRESS`; lưu `actual_start_at`; ghi lịch sử `START_EXAM`.
- **Điều kiện:** Appointment thuộc bác sĩ và đang `CHECKED_IN`.
- **Ngoại lệ:** Một bác sĩ không thể có đồng thời hai ca `IN_PROGRESS`.

### UC21 - Xác nhận bệnh nhân không đến

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Đánh dấu bệnh nhân vắng mặt.
- **Luồng chính:** Sau thời gian chờ, bác sĩ chọn No-show; hệ thống chuyển `BOOKED` sang `NO_SHOW`; ghi lịch sử `NO_SHOW`.
- **Điều kiện:** Lịch thuộc bác sĩ; vẫn đang `BOOKED`; đã qua giờ hẹn ít nhất 15 phút.
- **Ngoại lệ:** Không được đánh dấu No-show nếu bệnh nhân đã check-in.

### UC22 - Tạo bệnh án

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Ghi nhận kết quả khám và điều trị.
- **Luồng chính:** Bác sĩ nhập chẩn đoán, điều trị, kế hoạch, ghi chú, đơn thuốc và thông tin tái khám; hệ thống lưu MedicalRecord.
- **Điều kiện:** Appointment thuộc bác sĩ, đang `IN_PROGRESS` và chưa có bệnh án.
- **Quy tắc:** Mỗi Appointment có tối đa một MedicalRecord; đơn thuốc được lưu dưới dạng JSONB.
- **Ngoại lệ:** Nếu yêu cầu tái khám thì phải có ngày đề xuất hoặc ghi chú tái khám.

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

### UC27 - Xem slot tái khám

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Tìm khung giờ tái khám cho bệnh nhân.
- **Luồng chính:** Bác sĩ chọn ca khám cũ, dịch vụ và ngày; hệ thống trả các slot phù hợp.
- **Điều kiện:** Ca cũ thuộc bác sĩ, đang `IN_PROGRESS` hoặc `COMPLETED`; MedicalRecord có `followUpRequired = true`.

### UC28 - Tạo lịch tái khám

- **Tác nhân:** Bác sĩ.
- **Mục đích:** Đặt lịch tái khám cho bệnh nhân sau điều trị.
- **Luồng chính:** Bác sĩ chọn slot; hệ thống tạo Appointment mới `BOOKED`; liên kết `previous_appointment_id` với lịch cũ; ghi lịch sử `BOOK`.
- **Điều kiện:** Bệnh án yêu cầu tái khám; slot còn trống; bác sĩ cung cấp dịch vụ; đáp ứng quy tắc đặt trước 120 phút (2 giờ).
- **Ngoại lệ:** Hai request đặt cùng slot chỉ một request thành công.

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
- **Luồng chính:** Admin nhập email, mật khẩu, họ tên, chuyên khoa, giấy phép, tiểu sử và kinh nghiệm; hệ thống tạo User `DOCTOR` và Doctor trong một transaction.
- **Điều kiện:** Email và số giấy phép chưa tồn tại; mật khẩu từ 8 đến 72 ký tự; số năm kinh nghiệm không âm.
- **Kết quả:** Tài khoản và trạng thái làm việc mặc định là `ACTIVE`.

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
- **Mục đích:** Thiết lập lịch làm việc cố định theo tuần cho từng bác sĩ.
- **Luồng chính:** Admin chọn bác sĩ; xem, tạo, cập nhật hoặc xóa lịch tuần của bác sĩ đó.
- **Điều kiện:** Ngày trong tuần từ 1 đến 7; buổi `MORNING` hoặc `AFTERNOON`; giờ kết thúc phải sau giờ bắt đầu.
- **Ngoại lệ:** Không được có hai lịch trùng cùng thứ và cùng buổi của một bác sĩ; lịch được sửa hoặc xóa phải thuộc đúng bác sĩ trên URL.
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

- **Tác nhân:** Admin.
- **Mục đích:** Theo dõi các khoản phải thu và đã thu.
- **Luồng chính:** Admin tìm theo bệnh nhân hoặc bác sĩ; lọc trạng thái và ngày; sắp xếp và phân trang; chọn phiếu để xem chi tiết.
- **Kết quả:** Hiển thị bệnh nhân, bác sĩ, dịch vụ gốc và dịch vụ bổ sung, tổng tiền, trạng thái và thông tin xác nhận.

### UC46 - Xác nhận đã thu tiền

- **Tác nhân:** Admin.
- **Mục đích:** Ghi nhận bệnh nhân đã thanh toán trực tiếp tại phòng khám.
- **Luồng chính:** Admin chọn Payment `UNPAID`; chọn `CASH` hoặc `BANK_TRANSFER`; hệ thống khóa Payment; kiểm tra tổng tiền; chuyển `UNPAID` sang `PAID`; lưu người xác nhận và `paid_at`.
- **Điều kiện:** Appointment đã `COMPLETED`; Admin đang hoạt động; Payment đang `UNPAID`.
- **Concurrency:** Hai Admin xác nhận đồng thời chỉ một request thành công.
- **Lưu ý:** `BANK_TRANSFER` là xác nhận thủ công, không phải cổng thanh toán trực tuyến.

### UC47 - Xem thống kê doanh thu

- **Tác nhân:** Admin.
- **Mục đích:** Theo dõi doanh thu thực nhận.
- **Luồng chính:** Admin chọn khoảng ngày hoặc tháng; hệ thống tổng hợp dữ liệu theo `paid_at`.
- **Kết quả:** Doanh thu hôm nay, doanh thu theo khoảng ngày, doanh thu tháng, số phiếu `PAID`, số phiếu `UNPAID` và tổng tiền chưa thu.
- **Quy tắc:** Chỉ Payment `PAID` được tính là doanh thu.

## E. LUỒNG TRẠNG THÁI QUAN TRỌNG

### Luồng Appointment

```text
BOOKED ──→ CHECKED_IN ──→ IN_PROGRESS ──→ COMPLETED
   │
   ├──→ CANCELLED
   └──→ NO_SHOW
```

### Luồng Payment

```text
DRAFT ── hoàn thành khám ──→ UNPAID ── Admin thu tiền ──→ PAID
```

## F. CHỨC NĂNG CHƯA CÓ TRONG HỆ THỐNG

- Đăng ký tài khoản Doctor hoặc Admin từ trang công khai.
- Refresh Token và API logout phía server.
- Thanh toán trực tuyến và tích hợp cổng thanh toán.
- Webhook ngân hàng, hoàn tiền hoặc trả góp.
- Hóa đơn kế toán.
- Frontend React.
- AI Chatbot, Google Gemini, RAG và tư vấn triệu chứng.
