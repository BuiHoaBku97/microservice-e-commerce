# Elasticsearch từ cơ bản đến thực hành trong dự án e-commerce

Tài liệu này dành cho người chưa dùng Elasticsearch. Mục tiêu là hiểu Elasticsearch giải quyết vấn đề gì, chạy được một bài lab nhỏ, rồi đọc và kiểm thử luồng tìm kiếm sản phẩm đã có trong dự án.

## 1. Elasticsearch là gì?

Elasticsearch là một search engine lưu dữ liệu dạng JSON và tối ưu cho việc tìm kiếm. PostgreSQL vẫn là nơi lưu dữ liệu nghiệp vụ chính; Elasticsearch giữ một bản dữ liệu được tổ chức riêng để tìm kiếm nhanh.

Ví dụ, PostgreSQL lưu `Product`, `Sku`, lịch sử giá và quan hệ giữa các bảng. Elasticsearch gom những thông tin cần tìm kiếm của một sản phẩm thành một document:

```json
{
  "id": 101,
  "name": "Áo thun nam",
  "description": "Áo cotton màu đen",
  "categoryId": 5,
  "colors": ["đen"],
  "sizes": ["M", "L"],
  "minPrice": 125000,
  "status": "ACTIVE"
}
```

Một số khái niệm tương đương để dễ hình dung:

| PostgreSQL | Elasticsearch | Ý nghĩa |
|---|---|---|
| database/table | index | Nơi chứa một nhóm document |
| row | document | Một đối tượng JSON có `_id` |
| column definition | mapping | Kiểu và cách lập chỉ mục cho từng field |
| B-tree index | inverted index | Cấu trúc ánh xạ từ khóa tới document |
| query result | search hits | Các document khớp truy vấn |

Elasticsearch phân tích field kiểu `text` thành các token. Với chuỗi `Áo thun nam`, analyzer của dự án chuyển chữ thường và bỏ dấu để người dùng có thể tìm bằng `ao thun`. Field kiểu `keyword` không được tách từ, phù hợp với trạng thái, mã, màu, size và các bộ lọc chính xác.

Elasticsearch không nên thay PostgreSQL cho giao dịch đặt hàng, số dư kho, địa chỉ mặc định hoặc các ràng buộc cần tính nhất quán mạnh. Dữ liệu tìm kiếm có thể cập nhật chậm hơn dữ liệu gốc vài giây; đây gọi là eventual consistency.

## 2. Vì sao `AddressServiceImpl` không dùng Elasticsearch?

[`AddressServiceImpl`](../identity-service/src/main/java/com/ecommerce/identity/services/impls/AddressServiceImpl.java) thực hiện CRUD địa chỉ của chính người dùng, đổi địa chỉ mặc định và lấy snapshot khi checkout. Các thao tác này cần:

- transaction và khóa để tại một thời điểm chỉ có một địa chỉ mặc định;
- kiểm tra quyền sở hữu bằng `userId`;
- dữ liệu mới nhất ngay khi checkout;
- cập nhật, xóa và optimistic locking;
- bảo vệ dữ liệu cá nhân như tên, số điện thoại và địa chỉ.

Vì vậy lớp này dùng JPA/PostgreSQL qua [`AddressRepository`](../identity-service/src/main/java/com/ecommerce/identity/repositories/AddressRepository.java). Đưa Elasticsearch vào giữa `create`, `update`, `delete` hoặc `makeDefault` sẽ làm giao dịch khó nhất quán và không tạo giá trị rõ ràng.

Use case phù hợp trong dự án là tìm kiếm catalog: người mua nhập từ khóa, lọc danh mục/khoảng giá, sắp xếp và phân trang. Phần này đã được đặt đúng ở `catalog-service`.

## 3. Kiến trúc đang có trong dự án

Luồng ghi và luồng đọc được tách như sau:

```text
Admin tạo/sửa Product hoặc Sku
              |
              v
   PostgreSQL: product + outbox event
              |
       CatalogIndexer (mỗi 3 giây)
              |
              v
 Elasticsearch index <-- alias catalog-products
              |
              v
 GET /api/v1/products/search
```

Các thành phần chính:

| Thành phần | File | Vai trò |
|---|---|---|
| Docker service | [`docker-compose.yml`](../docker-compose.yml) | Chạy Elasticsearch 8.15.3 ở cổng 9200 |
| Java dependency | [`catalog-service/build.gradle`](../catalog-service/build.gradle) | Thêm Spring Data Elasticsearch |
| Kết nối | [`application.yml`](../catalog-service/src/main/resources/application.yml) | Dùng `SPRING_ELASTICSEARCH_URIS`; mặc định là `http://localhost:9200` của Spring Boot |
| Document | [`CatalogProductDocument.java`](../catalog-service/src/main/java/com/ecommerce/catalog/search/CatalogProductDocument.java) | Khai báo mapping của document sản phẩm |
| Analyzer | [`catalog-settings.json`](../catalog-service/src/main/resources/elasticsearch/catalog-settings.json) | Lowercase và bỏ dấu bằng `asciifolding` |
| Truy vấn | [`CatalogSearchService.java`](../catalog-service/src/main/java/com/ecommerce/catalog/services/CatalogSearchService.java) | Ghép tìm kiếm, filter, sort và pagination |
| Đồng bộ | [`CatalogIndexer.java`](../catalog-service/src/main/java/com/ecommerce/catalog/services/CatalogIndexer.java) | Đọc outbox và ghi document vào Elasticsearch |
| Quản lý index | [`CatalogIndexManager.java`](../catalog-service/src/main/java/com/ecommerce/catalog/services/CatalogIndexManager.java) | Tạo index, dùng alias và chặn bản cũ ghi đè bản mới |
| Reindex | [`CatalogReindexService.java`](../catalog-service/src/main/java/com/ecommerce/catalog/services/CatalogReindexService.java) | Tạo index mới rồi chuyển alias không gián đoạn tìm kiếm |

PostgreSQL là source of truth. Khi Elasticsearch tạm ngừng, transaction cập nhật sản phẩm và outbox vẫn được lưu. Scheduler sẽ thử lại sau khi Elasticsearch hoạt động. Không nên viết PostgreSQL rồi gọi Elasticsearch đồng bộ trong cùng request vì hai hệ thống không chia sẻ một transaction.

## 4. Cài và chạy Elasticsearch trên Windows

### Điều kiện

- Docker Desktop đã được cài và đang chạy Linux containers.
- Cổng 9200 còn trống.
- Dành được khoảng 1 GB RAM cho container; project giới hạn heap Elasticsearch ở 512 MB.

Kiểm tra Docker:

```powershell
docker version
docker compose version
```

Repository đã khai báo Elasticsearch trong `docker-compose.yml`. Khi đã có file `.env` chứa các khóa bắt buộc của toàn bộ hệ thống, chạy:

```powershell
docker compose up -d elasticsearch
docker compose ps elasticsearch
```

Docker Compose đọc và kiểm tra toàn bộ biến trong file, kể cả khi chỉ khởi động một service. Nếu chưa cấu hình các khóa JWT của dự án, có thể chạy Elasticsearch độc lập để học và chạy `catalog-service` từ IntelliJ:

```powershell
docker run --name ecommerce-elasticsearch `
  -p 9200:9200 `
  -e "discovery.type=single-node" `
  -e "xpack.security.enabled=false" `
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" `
  -v ecommerce-elasticsearch-data:/usr/share/elasticsearch/data `
  -d elasticsearch:8.15.3
```

Kiểm tra node và cluster:

```powershell
Invoke-RestMethod http://localhost:9200
Invoke-RestMethod http://localhost:9200/_cluster/health
```

Khi đã tạo container, những lần sau dùng:

```powershell
docker start ecommerce-elasticsearch
```

## 5. Bài lab 10 phút bằng REST API

Bài lab dùng index riêng `products-lab`, không ảnh hưởng index của ứng dụng.

### 5.1 Tạo index và mapping

```powershell
$mapping = @'
{
  "settings": {
    "analysis": {
      "analyzer": {
        "vietnamese_folding": {
          "tokenizer": "standard",
          "filter": ["lowercase", "asciifolding"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "name": {"type": "text", "analyzer": "vietnamese_folding"},
      "category": {"type": "keyword"},
      "price": {"type": "long"},
      "status": {"type": "keyword"}
    }
  }
}
'@

Invoke-RestMethod -Method Put `
  -Uri http://localhost:9200/products-lab `
  -ContentType application/json `
  -Body $mapping
```

Mapping phải được thiết kế trước. `name` là `text` để full-text search, còn `category` và `status` là `keyword` để filter chính xác.

### 5.2 Thêm hai document

```powershell
$product1 = '{"name":"Áo thun nam","category":"fashion","price":125000,"status":"ACTIVE"}'
$product2 = '{"name":"Điện thoại Android","category":"electronics","price":5000000,"status":"ACTIVE"}'

Invoke-RestMethod -Method Put -Uri 'http://localhost:9200/products-lab/_doc/1?refresh=true' `
  -ContentType application/json -Body $product1
Invoke-RestMethod -Method Put -Uri 'http://localhost:9200/products-lab/_doc/2?refresh=true' `
  -ContentType application/json -Body $product2
```

`refresh=true` chỉ tiện cho bài lab để document tìm thấy ngay. Trong luồng ghi tải cao, không nên gắn tham số này vào mọi request.

### 5.3 Tìm không dấu và lọc giá

```powershell
$query = @'
{
  "query": {
    "bool": {
      "must": [{"match": {"name": "ao thun"}}],
      "filter": [
        {"term": {"status": "ACTIVE"}},
        {"range": {"price": {"lte": 200000}}}
      ]
    }
  }
}
'@

(Invoke-RestMethod -Method Post `
  -Uri http://localhost:9200/products-lab/_search `
  -ContentType application/json `
  -Body $query).hits.hits
```

`match` chạy analyzer và tính độ liên quan. `term` yêu cầu giá trị chính xác, còn `range` lọc số. Filter không ảnh hưởng relevance score.

Xóa dữ liệu lab khi hoàn tất:

```powershell
Invoke-RestMethod -Method Delete http://localhost:9200/products-lab
```

## 6. Chạy ví dụ thật của `catalog-service`

### Cách A: chạy toàn hệ thống bằng Docker Compose

Sau khi cấu hình các biến bắt buộc của dự án trong `.env`:

```powershell
docker compose up -d --build
docker compose ps
```

API tìm kiếm đi qua gateway:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/products/search?q=ao&minPrice=50000&maxPrice=300000&sort=price_asc&page=0&size=20'
```

### Cách B: chạy dependency bằng Docker và service từ IntelliJ

1. Chạy PostgreSQL, Redis và Elasticsearch.
2. Cấp các biến JWT mà `catalog-service` yêu cầu.
3. Chạy `CatalogServiceApplication` từ IntelliJ hoặc dùng wrapper:

```powershell
./gradlew.bat :catalog-service:bootRun
```

Khi chạy ngoài Docker, các giá trị mặc định là PostgreSQL ở `localhost:5433`, Redis ở `localhost:6379` và Elasticsearch ở `localhost:9200`.

Sau khi admin tạo sản phẩm, tạo SKU và chuyển sản phẩm sang `ACTIVE`, `CatalogServiceImpl` lưu một outbox event cùng transaction PostgreSQL. Trong khoảng ba giây, `CatalogIndexer` tạo document tìm kiếm. Vì vậy API search có thể chưa thấy sản phẩm ngay ở mili giây đầu tiên.

Có thể quan sát trực tiếp trạng thái Elasticsearch:

```powershell
Invoke-RestMethod 'http://localhost:9200/_cat/indices?v'
Invoke-RestMethod 'http://localhost:9200/_cat/aliases?v'
Invoke-RestMethod 'http://localhost:9200/catalog-products/_mapping'
Invoke-RestMethod 'http://localhost:9200/catalog-products/_search?pretty'
```

Alias `catalog-products` trỏ đến một physical index có hậu tố timestamp. API chỉ dùng alias, nên reindex có thể xây index mới rồi chuyển alias bằng một thao tác atomic.

## 7. Đọc truy vấn trong code

`CatalogSearchService.search(...)` xây truy vấn theo các bước:

1. Chỉ lấy document có `status = ACTIVE`.
2. Thêm filter `categoryId`, `minPrice`, `maxPrice` khi client truyền vào.
3. Nếu có `q`, tìm trên `name`, `description`, `normalizedName`, `colors` và `sizes`.
4. Chọn sort theo `price_asc`, `price_desc`, `newest` hoặc để Elasticsearch xếp theo relevance.
5. Giới hạn page size từ 1 đến 100.

Ví dụ:

```http
GET /api/v1/products/search?q=ao+den&categoryId=5&minPrice=100000&maxPrice=300000&sort=price_asc&page=0&size=20
```

Đây là read model: response chỉ chứa dữ liệu cần cho trang kết quả tìm kiếm. Khi người dùng mở chi tiết sản phẩm, API sản phẩm lấy dữ liệu nghiệp vụ hiện hành theo thiết kế của service.

## 8. Kiểm thử và xử lý lỗi

Chạy unit test liên quan Elasticsearch mà không cần Docker:

```powershell
./gradlew.bat :catalog-service:test --tests '*CatalogIndexManagerTest'
```

Các kiểm tra thường dùng:

```powershell
# Elasticsearch có phản hồi không?
Test-NetConnection localhost -Port 9200

# Xem log container
docker logs ecommerce-elasticsearch --tail 100

# Xem document theo id
Invoke-RestMethod http://localhost:9200/catalog-products/_doc/101

# Xem analyzer tách từ thế nào
$body = '{"analyzer":"vietnamese_folding","text":"Áo thun Đen"}'
Invoke-RestMethod -Method Post -Uri http://localhost:9200/catalog-products/_analyze `
  -ContentType application/json -Body $body
```

Nếu `connection refused`, Docker Desktop hoặc container chưa chạy, cổng bị đổi, hoặc `SPRING_ELASTICSEARCH_URIS` sai. Nếu index chưa tồn tại, hãy chạy `catalog-service`; `CatalogIndexManager` sẽ tạo physical index và alias. Nếu sản phẩm không xuất hiện, kiểm tra sản phẩm/SKU đã `ACTIVE`, bảng `catalog_outbox_events`, log của `CatalogIndexer`, rồi kiểm tra document trực tiếp trong alias.

## 9. Những điểm cần nhớ khi áp dụng tiếp

- Giữ PostgreSQL làm source of truth; Elasticsearch là read model cho search.
- Chỉ index field thực sự cần tìm, lọc, sắp xếp hoặc hiển thị trong kết quả.
- Chọn `text` cho full-text và `keyword` cho exact match/filter/aggregation.
- Mapping hoặc analyzer thay đổi thường cần reindex; alias giúp chuyển index không downtime.
- Đồng bộ bằng outbox để không làm mất thay đổi khi Elasticsearch tạm ngừng.
- Chấp nhận và thiết kế rõ khoảng trễ eventual consistency.
- Không index dữ liệu địa chỉ cá nhân nếu chưa có use case, phân quyền, retention và bảo mật cụ thể.
