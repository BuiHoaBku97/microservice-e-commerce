import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;

public final class GenerateDevEnv {
  private GenerateDevEnv() {}

  public static void main(String[] args) throws Exception {
    Path output = Path.of(args.length > 0 ? args[0] : ".env").toAbsolutePath().normalize();
    boolean force = args.length > 1 && Boolean.parseBoolean(args[1]);
    if (Files.exists(output) && !force) {
      throw new IllegalStateException(output + " already exists. Use -Force to replace it.");
    }

    KeyPair user = pair();
    KeyPair order = pair();
    KeyPair catalog = pair();
    KeyPair payment = pair();
    KeyPair notification = pair();
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);

    String content = String.join("\n",
        "JWT_PRIVATE_KEY_BASE64=" + base64(user.getPrivate().getEncoded()),
        "JWT_PUBLIC_KEY_BASE64=" + base64(user.getPublic().getEncoded()),
        "ORDER_SERVICE_JWT_PRIVATE_KEY_BASE64=" + base64(order.getPrivate().getEncoded()),
        "ORDER_SERVICE_JWT_PUBLIC_KEY_BASE64=" + base64(order.getPublic().getEncoded()),
        "CATALOG_SERVICE_JWT_PRIVATE_KEY_BASE64=" + base64(catalog.getPrivate().getEncoded()),
        "CATALOG_SERVICE_JWT_PUBLIC_KEY_BASE64=" + base64(catalog.getPublic().getEncoded()),
        "PAYMENT_SERVICE_JWT_PRIVATE_KEY_BASE64=" + base64(payment.getPrivate().getEncoded()),
        "PAYMENT_SERVICE_JWT_PUBLIC_KEY_BASE64=" + base64(payment.getPublic().getEncoded()),
        "NOTIFICATION_SERVICE_JWT_PRIVATE_KEY_BASE64=" + base64(notification.getPrivate().getEncoded()),
        "NOTIFICATION_SERVICE_JWT_PUBLIC_KEY_BASE64=" + base64(notification.getPublic().getEncoded()),
        "PAYMENT_CALLBACK_SECRET=" + base64(secret),
        "ADMIN_BOOTSTRAP_ENABLED=true",
        "ADMIN_EMAIL=admin@example.test",
        "ADMIN_PASSWORD=ChangeMe123!",
        "ADMIN_FULL_NAME=System Administrator",
        "POSTGRES_PORT=5433",
        "KAFKA_PORT=9092",
        "REDIS_PORT=6379",
        "ELASTICSEARCH_PORT=9200",
        "GRAFANA_PORT=3000",
        "PROMETHEUS_PORT=9090",
        "LOKI_PORT=3100",
        "JAEGER_UI_PORT=16686",
        "OTEL_GRPC_PORT=4317",
        "OTEL_HTTP_PORT=4318",
        "MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0",
        "MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces",
        "GRAFANA_ADMIN_USER=admin",
        "GRAFANA_ADMIN_PASSWORD=admin",
        "ORDER_SHIPPING_FEE=30000",
        "");
    if (output.getParent() != null) {
      Files.createDirectories(output.getParent());
    }
    Files.writeString(output, content, StandardCharsets.UTF_8);
    System.out.println("Generated " + output + ". Change the bootstrap admin password before shared use.");
  }

  private static KeyPair pair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static String base64(byte[] value) {
    return Base64.getEncoder().encodeToString(value);
  }
}
