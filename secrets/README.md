# Secret key cho JWT

Identity Service dùng cặp khóa RSA cố định để ký và xác minh JWT.

Tạo cặp khóa trong thư mục này bằng OpenSSL:

```bash
openssl genrsa -out jwt-private-key.pem 2048
openssl rsa -in jwt-private-key.pem -pubout -out jwt-public-key.pem
```

Hai file `.pem` được Git ignore và không được commit. Khi chạy Docker Compose, thư mục này được mount vào `/run/secrets` trong Identity Service.

Private key chỉ được dùng bởi Identity Service. Các service khác chỉ cần public key thông qua JWK endpoint.
