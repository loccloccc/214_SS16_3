# BÀI TẬP 3 – HỆ THỐNG QUẢN LÝ TỒN KHO – CACHE-ASIDE PATTERN

## 1. Giới thiệu

Bài tập này xây dựng một Spring Boot application minh họa **Cache-Aside Pattern** sử dụng Redis làm Distributed Cache.

Mục đích:
- Hiểu cách Cache-Aside hoạt động trong thực tế
- Sử dụng `@Cacheable`, `@CacheEvict`, `RedisCacheManager`
- Xử lý lỗi Redis mà không làm hệ thống sập
- Hiểu tại sao Database là nguồn dữ liệu chính (Source of Truth)

---

## 2. Kiến trúc tổng thể

```
Client (HTTP Request)
        ↓
InventoryController  (/api/inventory/{productId})
        ↓
InventoryService
        ↓
    [Spring Cache Interceptor]
        ↓
    Redis (Cache Layer)          H2 Database (Source of Truth)
```

- **Controller**: Nhận request, trả response, xử lý HTTP status
- **Service**: Chứa business logic, cache logic
- **Redis**: Lưu cache tạm thời với TTL 60 giây
- **H2 Database**: Lưu dữ liệu thật, luôn là nguồn tin cậy nhất

---

## 3. Cache-Aside READ – Đọc tồn kho

Khi client gọi `GET /api/inventory/{productId}`:

```
Request: GET /api/inventory/P001
        ↓
Spring Cache Interceptor kiểm tra Redis
        ↓
    ┌─────────────────────┐
    │     Cache HIT?      │
    └─────────────────────┘
        YES ↓                NO ↓
    Trả DTO từ Redis     Query Database
                              ↓
                         Tìm thấy entity
                              ↓
                         Tạo DTO
                              ↓
                    Spring Cache tự PUT vào Redis
                              ↓
                         Trả DTO cho client
```

**@Cacheable** làm điều này tự động:
- Trước khi vào method → kiểm tra Redis
- Cache HIT → trả ngay, không vào method
- Cache MISS → vào method → lấy DB → Spring tự lưu vào Redis

---

## 4. Cache-Aside WRITE – Cập nhật tồn kho

Khi client gọi `PUT /api/inventory/{productId}`:

```
Request: PUT /api/inventory/P001  body: {"quantity": 95}
        ↓
Validate input (quantity >= 0, productId hợp lệ)
        ↓
UPDATE Database (P001 = 95)
        ↓
EVICT Cache (xóa key inventory::P001 khỏi Redis)
        ↓
Trả DTO mới cho client
```

**Thứ tự quan trọng: DB trước, EVICT sau.**

Nếu làm ngược lại (EVICT trước, UPDATE DB sau) thì khi DB update thất bại, cache đã bị xóa mà không cần thiết. Lần đọc tiếp theo sẽ vào DB và lấy dữ liệu cũ – gây ra Cache MISS không đáng có.

---

## 5. Vì sao Database là Source of Truth?

Redis là cache – dữ liệu trong Redis có thể:
- Bị hết hạn (TTL expired)
- Bị xóa do Redis restart
- Không đồng bộ khi evict thất bại

**Database luôn có dữ liệu mới nhất và chính xác nhất.** Mọi quyết định nghiệp vụ phải dựa trên Database, không dựa trên Cache.

Redis chỉ giúp tăng tốc độ đọc, giảm tải Database.

---

## 6. Xử lý số lượng âm

```
PUT /api/inventory/P001  body: {"quantity": -10}
        ↓
InventoryService.updateInventory("P001", -10)
        ↓
if (newQuantity < 0) → throw InvalidQuantityException
        ↓
HTTP 400 Bad Request
```

Số lượng tồn kho không thể âm trong thực tế. Nếu không kiểm tra, Database sẽ lưu giá trị vô nghĩa và gây lỗi logic toàn hệ thống. Validate sớm tại Service Layer giúp bảo vệ toàn vẹn dữ liệu.

---

## 7. Redis Failure khi READ – CacheErrorHandler

Khi Redis bị ngắt kết nối trong quá trình đọc:

```
Request: GET /api/inventory/P001
        ↓
Spring Cache Interceptor → Redis GET
        ↓
Redis Connection Refused / Timeout
        ↓
RedisCacheErrorHandler.handleCacheGetError() được gọi
        ↓
Log lỗi, KHÔNG throw exception
        ↓
Spring tiếp tục vào InventoryService.getInventory()
        ↓
Query Database
        ↓
Trả kết quả bình thường
```

**API không trả HTTP 500 chỉ vì Redis chết.**

`CacheErrorHandler` là lớp bắt lỗi Redis. Khi `handleCacheGetError` không throw exception, Spring Cache sẽ coi như Cache MISS và tiếp tục chạy method bình thường → fallback xuống Database.

---

## 8. Redis Failure khi EVICT – Stale Cache Problem

Tình huống nguy hiểm:

```
PUT /api/inventory/P001  quantity=95
        ↓
Database UPDATE: P001 = 95  ✓ THÀNH CÔNG
        ↓
Redis EVICT: inventory::P001  ✗ THẤT BẠI (Redis timeout)
        ↓
Database = 95
Redis    = 100 (dữ liệu cũ)
```

Lúc này Redis đang giữ dữ liệu cũ (stale cache). Client đọc sẽ nhận về 100 thay vì 95.

**Giải pháp đã triển khai: TTL (Time To Live)**

TTL = 60 giây. Sau 60 giây, Redis tự xóa entry cũ:

```
Redis inventory::P001 = 100 (stale)
        ↓
Sau 60 giây
        ↓
TTL expired → Redis tự xóa
        ↓
Request tiếp theo: Cache MISS
        ↓
Query Database → P001 = 95 (đúng)
        ↓
Redis được cập nhật lại
```

**Lưu ý quan trọng:** TTL KHÔNG đảm bảo nhất quán tuyệt đối ngay lập tức. Trong 60 giây đó, client vẫn có thể đọc dữ liệu cũ. TTL chỉ giới hạn *thời gian* dữ liệu cũ tồn tại.

**Các giải pháp nâng cao (không bắt buộc trong bài này):**
- Retry evict với backoff
- Background retry job
- Message queue / event-driven cache invalidation
- Write-through cache

---

## 9. TTL – Time To Live

TTL được cấu hình trong `CacheConfig.java`:

```
TTL = 60 giây
```

Tác dụng:
- Giới hạn thời gian dữ liệu cũ tồn tại trong Redis
- Phòng ngừa memory leak (Redis không giữ entry mãi mãi)
- Đảm bảo dữ liệu được làm mới định kỳ từ Database

---

## 10. Danh sách Test

| Test | Mô tả | Kỳ vọng |
|------|-------|---------|
| test1_cacheMiss | Lần đầu GET P001 | Repository được gọi 1 lần |
| test2_cacheHit | GET P001 hai lần | Repository chỉ gọi 1 lần |
| test3_updateInventory | Update P001 = 95, GET lại | Cache bị evict, DB trả 95 |
| test4_negativeQuantity | Update quantity = -10 | Throw InvalidQuantityException, DB không được gọi |
| test5_nullProductId | GET với productId = null | Throw IllegalArgumentException |
| test6_blankProductId | GET với "" hoặc "   " | Throw IllegalArgumentException |
| test7_productNotFound | GET productId không tồn tại | Throw ProductNotFoundException |
| test8_redisError | Cache clear, GET lại | Fallback DB, trả đúng dữ liệu |

---

## 11. Cách chạy project

### Bước 1: Khởi động Redis

```bash
docker compose up -d
```

Kiểm tra Redis đang chạy:

```bash
docker ps
```

### Bước 2: Chạy application

```bash
cd inventory-cache-aside
mvn spring-boot:run
```

### Bước 3: Chạy test

```bash
mvn test
```

### Dừng Redis (mô phỏng Redis failure)

```bash
docker stop inventory-redis
```

### Khởi động lại Redis

```bash
docker start inventory-redis
```

---

## 12. API Examples

### GET – Đọc tồn kho

```
GET http://localhost:8080/api/inventory/P001
```

Response:
```json
{
  "productId": "P001",
  "quantity": 100
}
```

### PUT – Cập nhật tồn kho

```
PUT http://localhost:8080/api/inventory/P001
Content-Type: application/json

{
  "quantity": 95
}
```

Response:
```json
{
  "productId": "P001",
  "quantity": 95
}
```

### Lỗi quantity âm

```
PUT http://localhost:8080/api/inventory/P001
Content-Type: application/json

{
  "quantity": -10
}
```

Response (HTTP 400):
```json
{
  "error": "Số lượng không hợp lệ: -10. Số lượng phải >= 0"
}
```

### Lỗi product không tồn tại

```
GET http://localhost:8080/api/inventory/UNKNOWN
```

Response (HTTP 404):
```json
{
  "error": "Không tìm thấy sản phẩm với id: UNKNOWN"
}
```

---

## 13. Dữ liệu mẫu (data.sql)

| productId | quantity |
|-----------|----------|
| P001      | 100      |
| P002      | 50       |
| P003      | 20       |

---

## 14. H2 Console

Truy cập H2 Console tại: `http://localhost:8080/h2-console`

- JDBC URL: `jdbc:h2:mem:inventorydb`
- Username: `sa`
- Password: (để trống)
