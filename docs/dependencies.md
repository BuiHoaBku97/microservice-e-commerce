# Dependency and environment baseline

Ngày ghi nhận: 06/10/2026

Tài liệu này khóa baseline kỹ thuật cho repository. Các phiên bản chỉ được đánh dấu sử dụng sau khi smoke build và Docker Compose đã chạy thành công.

## Build

| Thành phần | Baseline | Ghi chú |
|---|---:|---|
| Java toolchain | 21 | JDK 21.0.10 hiện có tại `C:\Users\ACER\.jdks\ms-21.0.10` |
| Spring Boot | 3.5.15 | Dùng BOM của Spring Boot |
| Spring Cloud | 2025.0.3 | Release train công bố tương thích Boot 3.5.15 |
| Gradle Wrapper | 8.14.3 | Mỗi service có Wrapper riêng; không phụ thuộc Gradle cài global |
| Build DSL | Groovy | Theo thiết kế dự án |

Spring Boot 3.5.15 yêu cầu Java tối thiểu 17, hỗ trợ đến Java 25 và Gradle 8.4 trở lên. Spring Cloud 2025.0.3 công bố hỗ trợ Spring Boot 3.5.15.

## Runtime images

| Thành phần | Image baseline | Ghi chú |
|---|---|---|
| PostgreSQL | `postgres:16.4` | Một container, năm database và user riêng |
| Kafka | `apache/kafka:3.9.1` | KRaft đơn node, replication factor 1 cho demo |
| Elasticsearch | `elasticsearch:8.15.3` | Single-node, heap giới hạn rõ ràng |
| Redis | `redis:7.4.1` | Lock/cache/rate limit; không là nguồn dữ liệu nghiệp vụ |

Các image phải được smoke test trước khi coi baseline là hoàn tất. Không dùng tag `latest`.

## Environment findings

- Repository chứa các Gradle project độc lập, các service Spring Boot, migration Identity/Catalog và Docker Compose.
- Gradle global chưa có trong PATH; build dùng `gradlew.bat`.
- Docker CLI và Docker Compose đã cài, nhưng Docker daemon hiện chưa chạy.
- Port `5432` đang được process khác sử dụng; Compose phải dùng port cấu hình được hoặc cần xác định process trước khi khởi động PostgreSQL.
- `JAVA_HOME` hiện trỏ tới JDK 26; build phải dùng JDK 21 bằng toolchain hoặc `org.gradle.java.home`.
- Chưa chạy được Docker runtime smoke test vì Docker daemon chưa chạy.

## Rules

- Không commit secret thật.
- Không dùng `ddl-auto=update`; schema do Flyway quản lý và JPA dùng `validate`.
- Mọi API response và notification hướng tới người dùng dùng message tiếng Việt.
- Error code, trạng thái và tên field kỹ thuật giữ ổn định theo REST/Kafka contract.
