# Góp ý chi tiết nâng cấp backend Manufacturing Monitor

> Phạm vi tài liệu này **cố ý bỏ qua phần DB** theo yêu cầu.
> Tài liệu tập trung vào: kiến trúc code, luồng ingest, rule engine, realtime, API contract, simulator, logging, test, bảo mật và lộ trình nâng cấp.

---

## 1. Nhận xét tổng quan

Backend hiện tại đã có một bộ khung khá tốt để đi tiếp:

- Tách package rõ theo hướng `api / common / config / domain / infrastructure`.
- Có service interface và service implementation riêng.
- Có các nhóm controller tương đối đầy đủ cho dashboard, machine, energy, OEE, alarm, tool, maintenance, ingest, realtime, settings.
- Có simulator và scheduler để tạo dữ liệu chạy thử khi chưa gắn thiết bị thật.
- Có SSE để đẩy realtime lên UI.
- Có chuẩn response và global exception handler.

Điểm quan trọng là repo này **không phải dạng dựng folder cho có**, mà đã có những luồng chạy được thật:

- Telemetry được ingest.
- Dữ liệu liên quan được tách ra theo từng nhóm nghiệp vụ.
- Rule engine được gọi sau ingest.
- SSE được broadcast sau khi có dữ liệu mới.
- Simulator có thể nuôi dữ liệu liên tục để test giao diện.

Tuy nhiên, nếu nhìn ở góc độ nâng cấp lên mức “dùng ổn định cho hệ thống realtime thật”, thì hiện tại project đang ở mức **Phase 1 tốt**, chưa phải mức production-ready. Phần cần tập trung nâng cấp sớm nhất nằm ở:

1. Luồng ingest đang hơi “ôm quá nhiều việc” trong một service.
2. Rule engine còn đơn giản, dễ bắn alarm trùng lặp.
3. SSE mới ở mức broadcast chung, chưa tối ưu cho UI lớn.
4. API contract chưa thật sự chặt.
5. Test coverage còn quá mỏng.
6. Chưa có lớp bảo vệ đủ tốt nếu sau này mở ingest cho gateway/PLC thật.

---

## 2. Những điểm đang làm đúng và nên giữ

## 2.1. Chia lớp theo hướng dễ mở rộng

Cách bạn chia package đang khá hợp lý:

- `api` giữ vai trò giao tiếp ngoài.
- `domain` giữ service, entity, dto, repository.
- `infrastructure` giữ adapter, realtime, scheduler.
- `common` gom response và exception.

Đây là nền rất tốt để sau này thêm:

- adapter PLC thật,
- event bus nội bộ,
- push qua WebSocket/MQTT,
- alarm engine nâng cao,
- health monitoring.

**Khuyến nghị:** giữ nguyên triết lý chia lớp này. Đừng quay lại kiểu gom mọi thứ vào controller/service lớn.

---

## 2.2. Có simulator từ sớm là rất đúng

`SimulatorAdapter` + `SimulationScheduler` là một quyết định tốt vì nó giúp bạn:

- test UI realtime mà không cần chờ phần cứng,
- test alarm và dashboard sớm,
- kiểm tra luồng ingest liên tục,
- tạo dữ liệu demo để review nghiệp vụ.

**Khuyến nghị:** nâng simulator thành công cụ kiểm thử tình huống, không chỉ random dữ liệu đẹp.

---

## 2.3. Có chuẩn response và exception chung

`ApiResponse`, `PageResponse`, `GlobalExceptionHandler` giúp API đỡ rối ngay từ đầu.

**Khuyến nghị:** tiếp tục dùng một chuẩn response thống nhất, tránh endpoint này trả kiểu này endpoint khác trả kiểu khác.

---

## 3. Góp ý nâng cấp theo mức ưu tiên

## P0 – Nên làm sớm nhất

1. Tách nhỏ luồng ingest để giảm độ phức tạp.
2. Nâng cấp rule engine để chống alarm trùng.
3. Hoàn thiện realtime theo topic/machine thay vì broadcast chung mọi thứ.
4. Thêm validation request đầu vào.
5. Bổ sung test cho luồng quan trọng nhất.

## P1 – Nên làm ngay sau khi P0 ổn

1. Chuẩn hóa API contract và error contract.
2. Thêm heartbeat / machine connectivity semantics cho UI.
3. Nâng simulator thành simulator theo kịch bản bất thường.
4. Thêm request id / trace id / logging có cấu trúc.
5. Tăng mức bảo vệ cho ingest endpoints.

## P2 – Nâng cấp chất lượng dài hạn

1. Tách domain event nội bộ.
2. Đưa rule engine về hướng cấu hình được.
3. Thêm contract test giữa UI và backend.
4. Tổ chức test theo pyramid rõ ràng.
5. Chuẩn hóa tài liệu nghiệp vụ và flow tài nguyên realtime.

---

## 4. Góp ý chi tiết theo từng khu vực

## 4.1. Kiến trúc tổng thể

### Hiện trạng

Kiến trúc hiện tại có định hướng tốt, nhưng một số service implementation đang có xu hướng trở thành “điểm tụ logic”. Nếu không chặn sớm, vài tháng nữa code sẽ khó bảo trì.

### Vấn đề chính

- Một service xử lý quá nhiều bước liên tiếp.
- Một số nghiệp vụ “hệ thống” đang nằm trực tiếp trong service chính thay vì tách thành component nhỏ.
- Dễ xuất hiện tình trạng thêm tính năng mới bằng cách nhét thêm `if/else` vào service cũ.

### Đề xuất nâng cấp

Tách các bước xử lý thành pipeline rõ ràng trong ingest:

- `TelemetryNormalizer` hoặc bước chuẩn hóa đầu vào.
- `TelemetryPersistenceHandler` hoặc `TelemetrySaveHandler`.
- `MachineStatusUpdater`.
- `AlarmEvaluationHandler`.
- `RealtimePublisher`.

Mục tiêu là để `IngestServiceImpl` chỉ còn vai trò điều phối:

```text
receive -> validate -> persist domain pieces -> evaluate rules -> publish realtime
```

### Lợi ích

- Dễ test từng bước độc lập.
- Dễ thay đổi thứ tự xử lý.
- Dễ thay adapter thật vào mà không chạm quá nhiều code cũ.
- Dễ cô lập lỗi khi telemetry hỏng.

---

## 4.2. Luồng ingest telemetry

### Hiện trạng

`IngestServiceImpl` đang làm khá nhiều việc trong một method:

- nhận dto,
- suy ra timestamp,
- lưu telemetry chính,
- lưu energy/maintenance/tool usage nếu có,
- cập nhật machine status,
- gọi rule engine,
- broadcast SSE.

### Điểm tốt

Luồng này đúng bản chất nghiệp vụ của hệ thống realtime monitor.

### Điểm cần nâng cấp

#### a. Method đang quá dài theo trách nhiệm

Khi method này dài dần lên, mỗi thay đổi nhỏ sẽ có nguy cơ làm hỏng luồng khác.

**Nên làm:** tách thành private method hoặc component chuyên trách:

- `saveMachineTelemetry(...)`
- `saveEnergyTelemetryIfPresent(...)`
- `saveMaintenanceTelemetryIfPresent(...)`
- `saveToolUsageIfPresent(...)`
- `updateMachineRuntimeState(...)`
- `publishTelemetryEvent(...)`

#### b. Thiếu validation đầu vào ở controller

`IngestController` hiện nhận `@RequestBody NormalizedTelemetryDto dto` nhưng chưa thấy `@Valid`.

Điều này khiến các payload thiếu `machineId`, timestamp sai format, giá trị âm bất hợp lý... có thể lọt sâu vào service mới vỡ.

**Nên làm:**

- Gắn `@Valid` tại controller.
- Đặt constraint rõ ở DTO:
  - `machineId` bắt buộc,
  - các số lượng không âm,
  - `remainingToolLifePct` trong khoảng hợp lệ,
  - các field phần trăm có min/max rõ ràng.

#### c. Chưa có khái niệm idempotency / chống xử lý lặp

Trong thực tế, gateway hoặc adapter có thể gửi lặp cùng một event do retry. Nếu backend xử lý event đó như mới hoàn toàn, UI sẽ bị nhiễu và nghiệp vụ đi lệch.

**Nên làm:**

- Thiết kế `eventId` hoặc `sourceMessageId` ở tầng ingest.
- Nếu chưa muốn làm đầy đủ, ít nhất thêm `traceId` / `ingestId` để dễ debug luồng.

#### d. Cập nhật trạng thái máy còn đơn giản

Hiện tại status máy được cập nhật gần như trực tiếp từ `machineState` trong telemetry mới nhất.

**Rủi ro:**

- Một gói telemetry lỗi có thể kéo trạng thái máy sai.
- Trạng thái “ONLINE nhưng không có dữ liệu mới” và “OFFLINE” là hai chuyện khác nhau.

**Nên làm:**

Tách 3 khái niệm:

- `connectionStatus`: còn kết nối hay không,
- `machineState`: running/idle/stopped/alarm,
- `freshness`: dữ liệu còn mới hay stale.

Phần này rất quan trọng cho UI realtime.

#### e. Broadcast realtime đang gắn chặt vào ingest

Hiện tại sau khi ingest là broadcast luôn. Cách này chạy nhanh nhưng khiến ingest service kiêm luôn trách nhiệm publish.

**Nên làm:**

- Tách `RealtimePublisher` riêng.
- Hoặc bắn internal domain event rồi listener publish SSE.

Như vậy sau này đổi SSE sang WebSocket/MQTT sẽ nhẹ hơn.

---

## 4.3. Rule engine

### Hiện trạng

`RuleEngineServiceImpl` hiện có logic threshold khá thẳng:

- đọc threshold,
- lấy metric value,
- nếu vượt critical thì tạo alarm,
- nếu vượt warning thì tạo alarm,
- nếu tool life thấp thì tạo alarm.

### Đây là nơi cần nâng cấp mạnh nhất

Vấn đề lớn nhất hiện tại không phải là “chưa có nhiều rule”, mà là **rule chưa có state**.

### Các vấn đề cụ thể

#### a. Dễ tạo alarm trùng lặp liên tục

Nếu nhiệt độ vượt ngưỡng trong 2 phút và telemetry đổ liên tục, rule hiện tại có thể tạo rất nhiều alarm cùng loại.

**Nên làm ngay:**

Trước khi tạo alarm mới, cần kiểm tra xem alarm cùng mã + cùng máy + cùng loại đã active hay chưa.

Nếu đã active rồi thì:

- không tạo mới,
- hoặc chỉ update `lastSeenAt`,
- hoặc tăng counter số lần vi phạm.

#### b. Chưa có cơ chế recovery / close alarm

Rule hiện tại chỉ biết “vi phạm thì tạo alarm”, chưa có logic “hết vi phạm thì đóng alarm”.

**Nên làm:**

- Khi metric quay về bình thường, đóng active alarm tương ứng.
- Bắn event `alarm-recovered` hoặc `alarm-resolved`.
- UI sẽ hiển thị đúng hơn nhiều.

#### c. Chưa có hysteresis / cooldown

Nếu giá trị cứ lắc quanh ngưỡng warning/critical thì alarm có thể mở/đóng liên tục.

**Nên làm:**

- Thêm vùng hysteresis.
- Hoặc yêu cầu vượt ngưỡng liên tiếp `N` lần mới bắn alarm.
- Hoặc cooldown trong một khoảng ngắn.

#### d. Chưa có severity escalation logic rõ ràng

Một alarm warning đang active rồi mà tiếp tục lên critical thì nên nâng cấp alarm hiện tại hay tạo alarm mới? Hiện code thiên về tạo mới.

**Nên chốt rule nghiệp vụ:**

- cùng một metric cùng một máy chỉ có 1 alarm active,
- severity có thể được nâng từ warning lên critical,
- message có thể được cập nhật,
- lịch sử escalation nên được giữ.

#### e. Tool life rule đang quá “tức thời”

Hiện tại cứ `<20` hoặc `<10` là tạo alarm. Nếu tool life vẫn thấp trong nhiều chu kỳ ingest thì sẽ bị spam.

**Nên làm:**

- chỉ tạo 1 alarm active cho 1 tool code,
- khi tool được thay thì đóng alarm cũ,
- cân nhắc rule “low”, “critical”, “expired”.

#### f. Chưa tách loại rule

Hiện threshold rule và tool-life rule đang nằm chung một service. Khi thêm vibration trend, abnormal stop, maintenance prediction, rule engine sẽ phình nhanh.

**Nên làm:**

Tách strategy theo loại rule:

- `ThresholdRuleEvaluator`
- `ToolLifeRuleEvaluator`
- `ConnectivityRuleEvaluator`
- `DowntimeRuleEvaluator`

Rồi `RuleEngineService` chỉ điều phối.

### Kết luận phần rule engine

Đây là hạng mục **nên ưu tiên cao nhất**. Chỉ cần nâng phần này tốt lên thì chất lượng hệ thống tăng rất mạnh ngay cả khi chưa thêm nhiều tính năng mới.

---

## 4.4. Realtime / SSE

### Hiện trạng

Bạn đã có `RealtimeController` và `SseEmitterRegistry`, đây là một nền ổn để UI subscribe realtime.

### Điểm tốt

- Triển khai đơn giản.
- Dễ test trên browser/Postman.
- Phù hợp cho dashboard giai đoạn đầu.

### Điểm cần nâng cấp

#### a. Hiện mới là một stream chung

`/api/v1/realtime/stream` hiện phù hợp demo, nhưng khi UI lớn lên sẽ gặp vấn đề:

- client nào cũng nhận mọi event,
- frontend phải tự lọc nhiều,
- lãng phí băng thông,
- khó scale mental model.

**Nên làm:**

Thêm stream theo phạm vi:

- `/realtime/stream`
- `/realtime/stream/machines/{machineId}`
- `/realtime/stream/alarms`
- `/realtime/stream/dashboard`

#### b. Chưa có heartbeat

SSE kết nối lâu mà không có event có thể khiến frontend khó biết connection còn sống hay đã treo.

**Nên làm:**

- gửi heartbeat định kỳ,
- hoặc gửi event `ping` mỗi 15–30s.

#### c. Chưa có event contract rõ ràng

Hiện `broadcast(eventName, data)` serialize object thành JSON string rồi gửi đi. Điều này dễ khiến frontend phải parse hai lần hoặc khó thống nhất schema event.

**Nên làm:**

Chuẩn hóa envelope SSE:

```json
{
  "type": "machine-telemetry-updated",
  "ts": "...",
  "machineId": "...",
  "payload": { ... }
}
```

Như vậy frontend sẽ ổn định hơn.

#### d. Chưa có snapshot ban đầu

Khi client mới subscribe, nếu chỉ chờ event mới thì UI có thể trống vài giây đầu.

**Nên làm:**

Khi connect, backend gửi ngay:

- latest machine states,
- active alarms,
- connection overview,
- rồi mới subscribe stream incremental.

#### e. Chưa có semantic riêng cho connection state

SSE connection của UI và connection của máy là hai khái niệm khác nhau. Nên tách event rõ:

- `ui-stream-connected`
- `machine-connection-updated`
- `machine-data-stale`
- `machine-offline`

---

## 4.5. API controller và contract

### Hiện trạng

Controller đã chia nhóm khá rõ, đây là điểm cộng lớn.

### Cần nâng cấp

#### a. Ingest API chưa đủ bề mặt

Service đã có `ingestAlarm(...)` và `ingestDowntime(...)`, nhưng controller hiện mới thấy ingest telemetry.

**Nên làm:**

Thêm endpoint rõ ràng cho:

- `POST /api/v1/ingest/telemetry`
- `POST /api/v1/ingest/alarm`
- `POST /api/v1/ingest/downtime`
- sau này có thể thêm `POST /api/v1/ingest/batch`

#### b. Thiếu validation annotation ở nhiều điểm

Không nên đẩy dữ liệu không hợp lệ quá sâu vào service.

**Nên làm:**

- `@Valid`
- `@NotNull`, `@NotBlank`, `@PositiveOrZero`, `@DecimalMin`, `@DecimalMax`
- validate query params cho paging/filter.

#### c. Nên chuẩn hóa response code và semantics

Ví dụ:

- tạo thành công: `201` nếu thực sự là create,
- ingest accepted bất đồng bộ: `202` nếu sau này chuyển sang queue,
- validation lỗi: `400`,
- không tìm thấy: `404`,
- conflict: `409`.

Hiện tại response wrapper đã có nền, chỉ cần siết semantics chặt hơn.

#### d. Nên chuẩn hóa paging/filter/sort ở các API list

Các API list như alarm, machine history, maintenance history, tool history nên có format query thống nhất:

- `page`
- `size`
- `sort`
- `from`
- `to`
- `machineId`
- `severity`
- `status`

#### e. Nên có contract riêng cho UI dashboard

Không phải lúc nào UI cũng nên gọi 5 endpoint nhỏ để ghép dữ liệu. Với dashboard tổng quan, backend nên có endpoint “view model” đúng thứ FE cần.

Ví dụ:

- dashboard overview card,
- latest machine tiles,
- active alarms,
- OEE summary.

---

## 4.6. Simulator và scheduler

### Hiện trạng

Simulator hiện tạo dữ liệu đẹp, có drift, có random machine state, có tool life, có energy/maintenance values.

### Đây là nền tốt nhưng chưa đủ để test tình huống thật

### Đề xuất nâng cấp

#### a. Chuyển từ random sang scenario-based simulation

Thay vì chỉ random, hãy có các kịch bản:

- normal production,
- overheating,
- high vibration,
- machine idle too long,
- abnormal stop,
- tool near end-of-life,
- intermittent connection.

Frontend và rule engine sẽ được test thực tế hơn nhiều.

#### b. Cho phép bật/tắt scenario theo machine

Ví dụ:

- máy A đang normal,
- máy B cố tình vào chế độ vibration cao,
- máy C mất kết nối 2 phút.

Điều này cực hữu ích khi demo và debug.

#### c. Nên có “offline simulation”

Hiện simulator mặc định đẩy `connectionStatus = ONLINE`. Như vậy bạn chưa test được case mất kết nối.

**Nên làm:**

- random skip emit,
- hoặc phát event machine stale/offline,
- hoặc có control API đổi trạng thái simulator.

#### d. Scheduler nên có guard tránh chồng chéo

Nếu một vòng simulate/aggregate chạy lâu hơn chu kỳ tiếp theo, có thể sinh hiện tượng đè lịch.

**Nên làm:**

- thêm guard re-entrance,
- log thời gian chạy mỗi job,
- cảnh báo khi job vượt ngưỡng.

---

## 4.7. Exception handling, logging, observability

### Hiện trạng

Bạn đã có `GlobalExceptionHandler`, đây là tốt. Nhưng mới ở mức nền.

### Các nâng cấp nên có

#### a. Bắt riêng validation exception

Ngoài `AppException` và generic `Exception`, nên bắt riêng:

- `MethodArgumentNotValidException`
- `ConstraintViolationException`
- `HttpMessageNotReadableException`

Để trả lỗi rõ cho frontend/gateway.

#### b. Thêm request id / trace id

Trong hệ thống realtime, khi một alarm sai xuất hiện, bạn rất cần truy ngược:

- request nào vào,
- machine nào,
- rule nào bắn,
- có publish realtime chưa.

**Nên làm:**

- gắn `traceId` vào MDC,
- log ra cùng `machineId`, `eventType`, `alarmCode`.

#### c. Logging nên có cấu trúc

Đừng chỉ log text chung chung. Nên log theo key:

- machineId,
- eventType,
- thresholdCode,
- severity,
- schedulerName,
- elapsedMs.

#### d. Actuator đã có, nên tận dụng sâu hơn

Nên bổ sung metric nội bộ:

- số subscriber SSE,
- số event ingest/phút,
- số alarm active,
- số simulate cycle lỗi,
- thời gian trung bình xử lý ingest.

---

## 4.8. Bảo mật và mức độ phơi bày API

### Hiện trạng

Hiện project có OpenAPI và WebConfig, nhưng chưa thấy lớp security chuyên biệt trong cấu trúc hiện tại.

### Góp ý

Nếu sau này ingest endpoint được cho gateway/PLC thật gọi vào, đây là phần phải bổ sung sớm:

#### a. Xác thực tối thiểu cho ingest

Không nên để bất kỳ ai biết URL cũng có thể đẩy telemetry vào hệ thống.

Có thể chọn một trong các mức:

- API key theo gateway,
- HMAC signature,
- mTLS,
- JWT service-to-service.

#### b. Rate limiting

Nếu adapter lỗi gửi spam, hệ thống sẽ bị ngập event.

**Nên làm:**

- rate limit theo source,
- hoặc theo machine,
- hoặc theo API key.

#### c. Phân quyền read API

Sau này cần phân role:

- dashboard viewer,
- operator,
- maintenance,
- admin,
- gateway ingest.

Không nên để từ đầu tới cuối mọi API đều “public trong mạng nội bộ là được”.

---

## 4.9. Test

### Hiện trạng

Hiện test đang rất mỏng. Nếu mới chỉ có test khởi động ứng dụng thì gần như chưa bảo vệ được logic nghiệp vụ chính.

### Đây là điểm yếu rõ nhất về chất lượng dài hạn

### Đề xuất test theo tầng

#### a. Unit test cho rule engine

Bắt buộc nên có cho các case:

- dưới warning không tạo alarm,
- vượt warning tạo warning,
- warning đang active không tạo trùng,
- warning lên critical thì escalate,
- hồi phục thì close alarm,
- tool life low/critical đúng logic.

#### b. Unit test cho ingest service

- telemetry đầy đủ field,
- telemetry thiếu field optional,
- machine disabled,
- SSE publish thành công,
- rule engine được gọi đúng lúc.

#### c. Controller test

- request hợp lệ trả đúng status,
- request thiếu field trả validation error,
- body sai format trả lỗi chuẩn.

#### d. Integration test cho realtime

- subscribe SSE nhận được event,
- disconnect thì cleanup emitter,
- nhiều subscriber cùng nhận event.

#### e. Scheduler test

- simulator enabled thì job chạy,
- lỗi simulator không làm app crash,
- aggregation job gọi đúng service.

### Gợi ý chiến lược

Tối thiểu trong giai đoạn này nên có:

- test rule engine,
- test ingest service,
- test ingest controller,
- test SSE registry.

Chỉ cần có 4 nhóm test này là độ tự tin khi refactor sẽ tăng mạnh.

---

## 4.10. Chất lượng code và maintainability

### a. Tránh “god service”

`IngestServiceImpl` và sau này `DashboardServiceImpl` rất dễ thành class phình to.

**Cách chặn:**

- tách theo use case,
- tách helper có trách nhiệm rõ,
- ưu tiên tên method nói đúng ý nghiệp vụ.

### b. Tên event nên chuẩn hóa

Hiện có các event như:

- `machine-telemetry-updated`
- `alarm-created`
- `downtime-created`

Nên thống nhất naming convention:

- `machine.telemetry.updated`
- `alarm.created`
- `alarm.resolved`
- `machine.connection.changed`

Hoặc giữ kebab-case nhưng phải nhất quán.

### c. Hạn chế business rule ẩn trong if/else dài

Khi thêm OEE realtime, downtime reason mapping, maintenance health scoring..., nếu cứ viết trực tiếp trong service impl sẽ rất khó đọc.

**Nên làm:**

- tách calculator / evaluator / mapper riêng.

### d. DTO nên phân vai rõ

Hiện có `NormalizedTelemetryDto`, đây là tốt. Nhưng về lâu dài nên tách rõ:

- request DTO,
- internal normalized DTO,
- response DTO cho UI,
- realtime event DTO.

Không nên để một DTO kiêm luôn mọi vai trò.

---

## 5. Góp ý cụ thể theo class/file

## 5.1. `IngestController.java`

### Hiện tại

- mới có telemetry endpoint,
- chưa thấy `@Valid`,
- response đang khá đơn giản.

### Nâng cấp đề xuất

- thêm `@Valid`,
- thêm endpoint ingest alarm/downtime,
- hỗ trợ batch ingest về sau,
- thêm header trace id,
- mô tả rõ hơn OpenAPI examples.

---

## 5.2. `RealtimeController.java`

### Hiện tại

- mới có stream tổng.

### Nâng cấp đề xuất

- thêm stream theo machine/topic,
- gửi snapshot ban đầu,
- thêm heartbeat,
- cân nhắc expose active subscriber count cho admin/debug.

---

## 5.3. `IngestServiceImpl.java`

### Điểm mạnh

- đúng luồng nghiệp vụ cơ bản,
- dễ hiểu,
- từ simulator tới ingest khá mượt.

### Nâng cấp đề xuất

- tách các bước lưu và publish,
- thêm trace logging,
- chống duplicate event,
- tách cập nhật machine state thành service riêng,
- không để method chính tiếp tục dài thêm.

---

## 5.4. `RuleEngineServiceImpl.java`

### Đây là file nên ưu tiên refactor đầu tiên

Nâng cấp theo thứ tự:

1. dedupe active alarm,
2. close/recover alarm,
3. cooldown/hysteresis,
4. escalation warning -> critical,
5. tách evaluator theo loại rule.

---

## 5.5. `SseEmitterRegistry.java`

### Điểm tốt

- đơn giản,
- đủ dùng cho giai đoạn đầu.

### Nâng cấp đề xuất

- chuẩn hóa envelope event,
- heartbeat,
- topic subscription,
- metrics active emitter,
- cleanup/log chi tiết hơn khi send lỗi.

---

## 5.6. `SimulatorAdapter.java`

### Điểm tốt

- có trạng thái theo machine,
- có drift hợp lý,
- sinh được nhóm dữ liệu khá phong phú.

### Nâng cấp đề xuất

- thêm scenario abnormal,
- thêm simulate offline/stale,
- thêm control API để đổi mode mô phỏng,
- cố định seed trong test để kết quả lặp lại được.

---

## 5.7. `SimulationScheduler.java` và `AggregationScheduler.java`

### Nâng cấp đề xuất

- thêm log thời gian chạy job,
- chống re-entrance,
- metric số lần lỗi,
- phân biệt log warn/error theo mức độ.

---

## 5.8. `GlobalExceptionHandler.java`

### Nâng cấp đề xuất

- bắt riêng validation exception,
- trả field error chi tiết,
- thêm traceId trong error response,
- phân loại rõ business error và unexpected error.

---

## 6. Lộ trình refactor thực tế đề xuất

## Giai đoạn 1 – Ổn định lõi realtime

1. Refactor `RuleEngineServiceImpl`.
2. Thêm `@Valid` và validation DTO.
3. Tách bớt `IngestServiceImpl`.
4. Chuẩn hóa SSE event envelope.
5. Viết unit test cho rule engine + ingest.

**Kết quả mong đợi:**

- không spam alarm,
- UI realtime ổn định hơn,
- tự tin refactor tiếp.

---

## Giai đoạn 2 – Hoàn thiện bề mặt tích hợp

1. Thêm ingest alarm/downtime APIs.
2. Thêm SSE theo machine/topic.
3. Bổ sung heartbeat và snapshot initial.
4. Nâng simulator theo scenario.
5. Chuẩn hóa logging + traceId.

**Kết quả mong đợi:**

- dễ tích hợp FE,
- dễ demo,
- dễ debug case realtime.

---

## Giai đoạn 3 – Tăng chất lượng triển khai

1. Thêm auth/rate limit cho ingest.
2. Tổ chức test coverage theo tầng.
3. Tách rule evaluator theo module.
4. Hoàn thiện contract API cho dashboard và machine detail.
5. Hoàn thiện tài liệu nghiệp vụ/event contract.

**Kết quả mong đợi:**

- code bền hơn,
- dễ mở rộng sang PLC/gateway thật,
- giảm rủi ro vỡ hệ thống khi thêm tính năng mới.

---

## 7. Nếu chỉ được làm 5 việc ngay bây giờ

Nếu bạn muốn nâng cấp nhanh mà hiệu quả cao nhất, tôi đề xuất đúng 5 việc này trước:

1. Refactor `RuleEngineServiceImpl` để chống alarm trùng và có recovery.
2. Thêm validation cho ingest request.
3. Tách `IngestServiceImpl` thành các bước nhỏ dễ test.
4. Chuẩn hóa SSE envelope + thêm heartbeat.
5. Viết test cho rule engine, ingest service và SSE registry.

Chỉ cần 5 việc này làm xong, chất lượng backend sẽ nhảy lên rất rõ mà chưa cần mở rộng quá nhiều tính năng mới.

---

## 8. Kết luận

Backend hiện tại của bạn **đã đi đúng hướng** và có nền đủ tốt để nâng cấp tiếp. Điểm mạnh lớn nhất là:

- chia module hợp lý,
- có luồng realtime cơ bản,
- có simulator,
- có service layer rõ ràng,
- có tiềm năng mở rộng.

Điểm yếu lớn nhất hiện tại là:

- rule engine còn đơn giản,
- ingest service đang ôm nhiều việc,
- realtime SSE mới ở mức cơ bản,
- test coverage rất mỏng,
- lớp bảo vệ API chưa đủ cho giai đoạn tích hợp thật.

Nếu bạn xử lý tốt 4 trục này:

- **ingest**,
- **rule engine**,
- **realtime**,
- **test**,

thì project này hoàn toàn có thể nâng từ một backend demo tốt thành một backend vận hành rất ổn cho hệ thống giám sát sản xuất realtime.

