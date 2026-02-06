## Çalıştırma
- Testler: mvn clean test
- Uygulama: mvn -q exec:java (ileride eklenecek) veya IntelliJ'den App çalıştır.

## Config dosyası formatı (taslak)
Bu proje ilerleyen adımlarda SMSC bağlantı bilgilerini bir config dosyasından okuyacak.
Örnek: config.properties

smsc.host=127.0.0.1
smsc.port=2775
smsc.systemId=test
smsc.password=test
smsc.systemType=
smsc.connectTimeoutMs=5000
smsc.readTimeoutMs=5000
smsc.reconnect.maxAttempts=3
smsc.enquireLink.intervalSeconds=30

Not: Adım 2'de socket bağlantısı ve timeout/reconnect bu alanları kullanacak.
