# Góp ý nâng cấp hệ thống để xử lý tốt các case vận hành

> Phạm vi tài liệu này: chỉ tập trung vào **luồng xử lý case của hệ thống** như mất kết nối PLC, dữ liệu đến chậm, dữ liệu lỗi, xử lý quá tải, đồng bộ trạng thái máy, cảnh báo sai/lặp, và phản ứng của UI. Không bàn về lựa chọn database.

---

## 1. Nhận định nhanh về trạng thái hiện tại

Luồng hiện tại của backend đang đi đúng hướng cơ bản:

- Nhận telemetry chuẩn hóa.
- Lưu dữ liệu đo.
- Cập nhật trạng thái máy.
- Chạy rule engine để sinh alarm.
- Đẩy event realtime cho UI.

Điểm tốt là luồng này **đủ để chạy demo và ghép FE**.

Nhưng nếu xét theo góc nhìn hệ thống vận hành thật trong nhà máy, phần xử lý case hiện tại vẫn còn ở mức nền tảng. Hệ thống chưa thể hiện rõ các cơ chế sau:

- phát hiện **mất kết nối im lặng** (PLC không gửi gì nữa nhưng backend không biết),
- **reconnect** có kiểm soát,
- chống **gửi trùng / lưu trùng / alarm trùng**,
- chống **out-of-order data**,
- hấp thụ **burst dữ liệu** khi đường truyền hồi lại,
- tách riêng trách nhiệm giữa **ingest**, **rule**, **connection health**, **realtime push**,
- cho UI biết rõ trạng thái là **mất kết nối**, **stale data**, hay chỉ là **chưa có dữ liệu mới**.

Nói ngắn gọn: hiện tại hệ thống đúng ở luồng chính, nhưng chưa đủ “chịu đòn” khi đi vào môi trường thật.

---

## 2. Mục tiêu nâng cấp

Hệ thống sau nâng cấp nên đạt được các mục tiêu sau:

1. Không chỉ nhận dữ liệu, mà còn **hiểu tình trạng đường dữ liệu**.
2. Không để UI hiểu nhầm “không có dữ liệu mới” thành “máy vẫn ổn”.
3. Không để một lỗi mạng nhỏ tạo ra hàng loạt alarm hoặc record sai.
4. Khi PLC hồi kết nối, hệ thống phải **khôi phục có kiểm soát**, không spam UI và không làm sai timeline.
5. Có thể giải thích rõ ràng từng case:
   - chuyện gì vừa xảy ra,
   - backend đã làm gì,
   - UI phải hiển thị gì,
   - khi nào hệ thống quay lại bình thường.

---

## 3. Tư duy kiến trúc nên áp dụng

Backend nên tách tư duy xử lý thành 5 lớp:

### 3.1. Kết nối thiết bị
Nhiệm vụ:

- giữ kết nối với PLC/gateway,
- reconnect khi rớt,
- phát heartbeat,
- báo trạng thái `CONNECTED / DEGRADED / DISCONNECTED`.

### 3.2. Ingest gateway
Nhiệm vụ:

- nhận dữ liệu từ PLC/gateway hoặc simulator,
- validate,
- chuẩn hóa,
- gắn `machineId`, `sourceTs`, `receivedAt`, `sequence`, `connectionStatus`, `quality`.

### 3.3. Ingest processor
Nhiệm vụ:

- xử lý lưu dữ liệu,
- chống duplicate,
- xử lý out-of-order,
- cập nhật machine state,
- gọi rule engine.

### 3.4. Connection watchdog
Nhiệm vụ:

- theo dõi lần cuối có dữ liệu của từng máy,
- tự kết luận `STALE` hoặc `OFFLINE` khi quá timeout,
- sinh event trạng thái chứ không đợi PLC phải báo thủ công.

### 3.5. Realtime publisher
Nhiệm vụ:

- push event gọn, đúng loại,
- có debounce/throttle khi cần,
- tách topic telemetry / alarm / connection / downtime.

Hiện tại code của bạn đang để `ingest` ôm khá nhiều việc. Khi tải lớn hơn, cách này sẽ khó bảo trì và khó chống lỗi dây chuyền.

---

## 4. Các case quan trọng hệ thống bắt buộc phải xử lý

---

### Case A. PLC mất kết nối hoàn toàn

#### Triệu chứng
- PLC/gateway ngừng gửi dữ liệu.
- Backend không nhận gói mới.
- UI dễ bị “đóng băng” ở giá trị cuối cùng.

#### Rủi ro nếu không xử lý
- Người vận hành tưởng máy vẫn chạy.
- OEE, trạng thái máy, điện năng hiển thị sai theo cảm giác người dùng.
- Alarm không xuất hiện đúng bản chất “mất tín hiệu”.

#### Backend nên làm gì
1. Mỗi máy phải có `lastSeenAt`.
2. Có watchdog chạy theo chu kỳ ngắn, ví dụ 2–5 giây.
3. Nếu quá ngưỡng X giây không có dữ liệu mới:
   - đánh dấu `connectionState = STALE`.
4. Nếu quá ngưỡng Y giây lớn hơn:
   - đánh dấu `connectionState = OFFLINE`.
5. Sinh event realtime riêng kiểu:
   - `machine-connection-stale`
   - `machine-connection-offline`
6. Chỉ phát event khi **trạng thái thay đổi**, không phát lặp vô hạn.

#### UI nên hiển thị gì
- Badge màu riêng: `Online`, `Stale`, `Offline`.
- Khi stale/offline phải hiện “last update X giây trước”.
- Chart/live card phải làm mờ hoặc khóa nhãn “live”.

#### Ưu tiên
Rất cao.

---

### Case B. PLC chập chờn, kết nối lúc được lúc mất

#### Triệu chứng
- Vừa online xong lại mất.
- Trong vài giây hệ thống có thể nhảy trạng thái liên tục.

#### Rủi ro
- UI nhấp nháy.
- Alarm connection bị spam.
- Người dùng mất niềm tin vào hệ thống.

#### Backend nên làm gì
1. Dùng **debounce trạng thái kết nối**.
2. Chỉ đổi sang `ONLINE` khi nhận ổn định N gói liên tiếp hoặc ổn định trong T giây.
3. Chỉ đổi sang `OFFLINE` khi timeout thật sự đạt ngưỡng.
4. Ghi nhận số lần flap trong khoảng thời gian ngắn.
5. Nếu flap quá nhiều, sinh cảnh báo riêng kiểu `UNSTABLE_CONNECTION`.

#### UI nên hiển thị gì
- Không đổi badge liên tục từng nhịp nhỏ.
- Có thể hiện “connection unstable” nếu số lần rớt/ngắt vượt ngưỡng.

#### Ưu tiên
Cao.

---

### Case C. Dữ liệu đến chậm nhưng không mất

#### Triệu chứng
- Gói tin có timestamp cũ mới tới backend.
- Có thể do mạng chậm hoặc gateway buffer rồi gửi muộn.

#### Rủi ro
- Giá trị cũ đè lên trạng thái mới.
- Chart realtime bị giật ngược thời gian.
- Alarm tính sai nếu không phân biệt `sourceTs` và `receivedAt`.

#### Backend nên làm gì
1. Phân biệt rõ:
   - `sourceTs`: thời điểm PLC đo,
   - `receivedAt`: thời điểm backend nhận.
2. Nếu data trễ nhưng vẫn hợp lệ:
   - vẫn lưu,
   - gắn cờ `lateArrival = true`.
3. Không dùng data cũ để ghi đè `current machine status` nếu đã có dữ liệu mới hơn.
4. Rule engine cần biết dữ liệu đang xử lý có phải dữ liệu trễ không.

#### UI nên hiển thị gì
- Chart lịch sử có thể chèn đúng theo `sourceTs`.
- Khu vực live card không nên rollback về giá trị cũ.

#### Ưu tiên
Cao.

---

### Case D. PLC hoặc gateway gửi trùng dữ liệu

#### Triệu chứng
- Cùng một gói được gửi lại do retry.
- Backend lưu nhiều record giống nhau.

#### Rủi ro
- Tăng dữ liệu rác.
- Chart bị lặp điểm.
- Alarm bị tạo nhiều lần.

#### Backend nên làm gì
1. Mỗi message nên có `sourceMessageId` hoặc `sequence`.
2. Nếu chưa có, dùng chiến lược fallback theo fingerprint:
   - `machineId + sourceTs + metric-set`.
3. Ingest phải idempotent.
4. Rule engine không được bắn alarm trùng cho cùng một tình trạng đang mở.

#### UI nên hiển thị gì
- Không có biểu hiện đặc biệt, vì đây là lỗi mà backend phải hấp thụ.

#### Ưu tiên
Rất cao.

---

### Case E. Dữ liệu đến sai thứ tự

#### Triệu chứng
- Gói mới đến trước, gói cũ đến sau.

#### Rủi ro
- Status máy bị “quay ngược thời gian”.
- Alarm lifecycle sai.

#### Backend nên làm gì
1. Với từng máy, giữ `latestAcceptedSourceTs` cho phần trạng thái hiện hành.
2. Data cũ hơn vẫn có thể lưu cho lịch sử.
3. Nhưng không dùng data cũ hơn để cập nhật `current status`, `connection state`, hoặc đóng/mở alarm hiện tại.
4. Nếu cần, đưa các gói lệch quá xa vào nhánh `out_of_order_review`.

#### Ưu tiên
Cao.

---

### Case F. Payload thiếu trường hoặc giá trị vô lý

#### Ví dụ
- Nhiệt độ âm vô lý,
- công suất nhảy đột biến cực lớn,
- `machineId` thiếu,
- `cycleTime` âm,
- chuỗi trạng thái không thuộc enum.

#### Backend nên làm gì
1. Validate ở biên ingest.
2. Tách lỗi thành 2 nhóm:
   - lỗi cứng: từ chối luôn,
   - lỗi mềm: vẫn lưu nhưng gắn quality flag.
3. Không để dữ liệu rác đi thẳng vào rule engine như dữ liệu thật.
4. Log có cấu trúc để truy vết nguồn lỗi.

#### UI nên hiển thị gì
- Không nhất thiết phải hiện hết lỗi kỹ thuật.
- Chỉ nên hiện khi chất lượng dữ liệu của máy đang kém.

#### Ưu tiên
Cao.

---

### Case G. Lưu dữ liệu chậm hơn tốc độ nhận

#### Triệu chứng
- PLC gửi nhanh, backend lưu không kịp.
- Request bị dồn.
- Realtime trễ dần.

#### Rủi ro
- Mất tính realtime.
- Timeout API ingest.
- UI tưởng backend chết.

#### Backend nên làm gì
1. Tách ingest thành 2 pha:
   - nhận nhanh,
   - xử lý/lưu ở worker riêng.
2. Có hàng đợi nội bộ hoặc queue abstraction.
3. Có giới hạn tải và backpressure.
4. Có metric theo dõi:
   - queue depth,
   - processing lag,
   - dropped events,
   - avg ingest latency.
5. Realtime push không nên chặn ingest path.

#### UI nên hiển thị gì
- Có thể hiện `stream delay` nếu độ trễ lớn hơn ngưỡng.

#### Ưu tiên
Rất cao nếu số máy nhiều hoặc tần suất cao.

---

### Case H. Rule engine tạo alarm trùng hoặc không biết lúc nào kết thúc

#### Triệu chứng
- Cứ mỗi gói vượt ngưỡng là sinh một alarm mới.
- Khi thông số bình thường lại thì không đóng alarm.

#### Rủi ro
- Danh sách alarm phình rất nhanh.
- Người dùng không biết alarm nào đang thực sự active.

#### Backend nên làm gì
1. Mỗi rule phải có lifecycle:
   - `OPEN`
   - `ACTIVE`
   - `ACKNOWLEDGED`
   - `RESOLVED`
2. Chỉ mở alarm nếu chưa có alarm active cùng khóa logic.
3. Khi metric về bình thường trong khoảng ổn định đủ lâu, đóng alarm.
4. Có cooldown để chống rung ngưỡng.
5. Alarm connection phải tách khỏi alarm process.

#### UI nên hiển thị gì
- Chỉ hiện alarm active nổi bật.
- Alarm đã resolve nên chuyển vào history.

#### Ưu tiên
Rất cao.

---

### Case I. UI mất kết nối với backend realtime

#### Triệu chứng
- Máy vẫn chạy, backend vẫn nhận dữ liệu, nhưng UI không còn stream live.

#### Rủi ro
- Người dùng tưởng PLC lỗi trong khi thực ra chỉ là browser mất stream.

#### Backend nên làm gì
1. Tách bạch rõ 2 loại trạng thái:
   - `PLC connection state`
   - `UI realtime stream state`
2. Stream realtime phải có heartbeat.
3. UI reconnect phải tự động.
4. Khi reconnect, UI cần có cơ chế lấy phần missed updates gần nhất.

#### UI nên hiển thị gì
- `Disconnected from live stream` khác với `Machine offline`.

#### Ưu tiên
Cao.

---

### Case J. Máy đang dừng thật hay chỉ mất dữ liệu?

Đây là case người dùng rất hay nhầm.

#### Cần tách rõ 3 trạng thái
1. **Machine stopped**: có dữ liệu, nhưng máy báo trạng thái dừng.
2. **Data stale**: chưa đủ để kết luận mất kết nối, nhưng dữ liệu cũ.
3. **Disconnected**: quá timeout, coi như mất tín hiệu.

Nếu không tách ba trạng thái này, UI sẽ rất khó dùng và đội vận hành sẽ hiểu sai.

---

## 5. Các nâng cấp nên làm ngay trong backend

### 5.1. Tạo machine health model
Tạo một mô hình trạng thái sức khỏe cho từng máy, ví dụ:

- `connectionState`
- `lastSeenAt`
- `lastTelemetrySourceTs`
- `lastTelemetryReceivedAt`
- `dataFreshnessSec`
- `streamLagMs`
- `qualityState`
- `currentOperationalState`

Mục tiêu là backend luôn có một ảnh chụp “máy đang ra sao” chứ không chỉ là đống telemetry rời rạc.

---

### 5.2. Thêm watchdog service
Nên có service nền chạy định kỳ:

- scan từng máy,
- so `now - lastSeenAt`,
- đổi trạng thái sang `STALE` hoặc `OFFLINE`,
- phát event kết nối,
- sinh alarm nếu cần.

Tên gợi ý:

- `MachineConnectionWatchdogService`
- `MachineHealthService`

---

### 5.3. Tách ingest thành pipeline rõ ràng
Hiện tại ingest đang ôm nhiều việc trong một flow. Nên tách thành:

1. `TelemetryAcceptService`
2. `TelemetryValidationService`
3. `TelemetryProcessingService`
4. `MachineStatusUpdater`
5. `RuleEvaluationService`
6. `RealtimePublishService`

Lợi ích:

- test dễ hơn,
- thay đổi logic ít ảnh hưởng dây chuyền,
- dễ quan sát bottleneck.

---

### 5.4. Thêm idempotency và ordering guard
Cần thêm lớp bảo vệ cho ingest:

- phát hiện duplicate,
- nhận biết out-of-order,
- không update current state bằng packet cũ,
- log rõ packet bị bỏ qua hay chỉ lưu lịch sử.

---

### 5.5. Chuẩn hóa event nội bộ
Mọi event nên có envelope chung:

- `eventId`
- `eventType`
- `machineId`
- `sourceTs`
- `receivedAt`
- `severity`
- `payload`
- `traceId`

Nhờ vậy sau này realtime, log, audit và test sẽ thống nhất.

---

### 5.6. Thêm metrics vận hành
Nên đo ít nhất các chỉ số sau:

- số gói nhận mỗi giây,
- độ trễ ingest,
- độ trễ từ receive đến publish realtime,
- số packet duplicate,
- số packet late,
- số packet invalid,
- số máy stale/offline,
- số subscriber realtime,
- số alarm active.

Nếu không đo, bạn rất khó biết hệ thống đang kém ở đâu.

---

## 6. Đề xuất phản ứng của UI theo từng trạng thái

UI không nên chỉ hiển thị số liệu. UI phải truyền được “độ tin cậy của số liệu”.

### 6.1. Trạng thái online
- badge xanh,
- cập nhật live bình thường,
- chart live bật.

### 6.2. Trạng thái stale
- badge vàng,
- giữ giá trị cuối nhưng có nhãn `stale`,
- hiện “last update 12s ago”.

### 6.3. Trạng thái offline
- badge đỏ,
- khóa live,
- hiện cảnh báo mất tín hiệu,
- không tiếp tục animate như đang có dữ liệu.

### 6.4. Trạng thái degraded quality
- hiện biểu tượng cảnh báo dữ liệu,
- cho phép người dùng biết dữ liệu đang đến nhưng chất lượng thấp.

---

## 7. Thứ tự ưu tiên nâng cấp

### Ưu tiên 1
- watchdog phát hiện stale/offline,
- phân biệt machine stopped và disconnected,
- chống duplicate alarm,
- connection event riêng cho UI.

### Ưu tiên 2
- idempotency cho ingest,
- xử lý out-of-order,
- debounce connection flapping,
- metrics vận hành.

### Ưu tiên 3
- queue/backpressure,
- late-arrival strategy,
- chất lượng dữ liệu và quality flag,
- observability sâu hơn.

---

## 8. Gợi ý tổ chức code theo N-Architecture hiện tại

### Controller
- `IngestController`
- `RealtimeController`
- `MachineHealthController`

### Service interface
- `TelemetryAcceptService`
- `TelemetryProcessingService`
- `MachineHealthService`
- `ConnectionWatchdogService`
- `RealtimePublishService`
- `AlarmLifecycleService`

### Service impl
- `TelemetryAcceptServiceImpl`
- `TelemetryProcessingServiceImpl`
- `MachineHealthServiceImpl`
- `ConnectionWatchdogServiceImpl`
- `RealtimePublishServiceImpl`
- `AlarmLifecycleServiceImpl`

### Domain models / DTO
- `MachineHealthSnapshot`
- `ConnectionStateChangedEvent`
- `TelemetryAcceptedResult`
- `TelemetryQualityFlag`
- `AlarmLifecycleState`

### Infrastructure
- `SseEventPublisher`
- `PlcConnectionSupervisor`
- `HeartbeatScheduler`
- `IngestQueueAdapter`

---

## 9. Bộ test bắt buộc nên có

### Unit test
- packet hợp lệ,
- packet thiếu field,
- packet duplicate,
- packet out-of-order,
- metric vượt ngưỡng nhiều lần nhưng chỉ mở 1 alarm,
- metric hồi phục thì alarm resolve.

### Integration test
- máy ngừng gửi dữ liệu rồi bị mark stale/offline,
- reconnect xong status quay lại online,
- UI subscriber nhận đúng event connection change,
- ingest burst nhưng không làm vỡ luồng chính.

### Scenario test
- 1 máy bình thường,
- 1 máy mất kết nối,
- 1 máy gửi chậm,
- 1 máy spam alarm,
- UI vẫn phân biệt được từng case.

---

## 10. Checklist triển khai ngắn gọn

### Giai đoạn 1
- [ ] Thêm `MachineHealthService`
- [ ] Thêm `lastSeenAt` update trong ingest
- [ ] Thêm watchdog mark `STALE` / `OFFLINE`
- [ ] Phát realtime event cho connection state
- [ ] UI hiển thị badge online/stale/offline

### Giai đoạn 2
- [ ] Thêm duplicate guard
- [ ] Thêm out-of-order guard
- [ ] Thêm alarm lifecycle
- [ ] Thêm debounce cho reconnect flapping

### Giai đoạn 3
- [ ] Tách ingest pipeline
- [ ] Thêm hàng đợi nội bộ hoặc processing buffer
- [ ] Thêm metrics vận hành
- [ ] Thêm scenario test đầy đủ

---

## 11. Kết luận

Backend hiện tại đã đi đúng luồng cơ bản, nhưng mới mạnh ở phần **nhận và đẩy dữ liệu**, chưa mạnh ở phần **chịu lỗi và phản ứng đúng khi có sự cố**.

Muốn hệ thống dùng ổn trong môi trường thật, bạn nên nâng nó từ một luồng ingest đơn giản thành một hệ thống có:

- nhận biết sức khỏe kết nối,
- chống dữ liệu lỗi/trùng/chậm,
- lifecycle rõ cho alarm,
- trạng thái rõ ràng cho UI,
- quan sát được độ trễ và chất lượng toàn pipeline.

Khi làm được các phần này, hệ thống sẽ “ra chất monitoring thật”, chứ không chỉ là “nhận số rồi bắn lên giao diện”.
