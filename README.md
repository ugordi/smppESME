# Java SMPP 3.4 Client (ESME) — GSM7 + Türkçe Single-Shift + DB Log

Bu proje, **SMPP v3.4** protokolüne uygun bir **Java tabanlı SMPP Client (ESME)** uygulamasıdır.  
TCP üzerinden SMSC’ye bağlanır, **bind_transceiver** ile oturum açar, **submit_sm** ile SMS gönderir, **deliver_sm** ile gelen mesajları ve **DLR (Delivery Receipt)** kayıtlarını alır. Ayrıca gelen/giden PDU’ları PostgreSQL’e **ham hex + decode edilmiş alanlar** ile loglar.

---

## 1) Proje Amacı

### Ne yapar?
- SMSC’ye **TCP socket** ile bağlanır (`SmppSocketClient`)
- `bind_transceiver` ile oturum açar (`SmppSessionManager.bind()`)
- `submit_sm` ile SMS gönderir (`SmppSender.sendSubmitSm()`)
- `deliver_sm` ile gelen SMS / DLR alır (`SmppSessionManager.handleDeliverSm()`)
- **EnquireLink** ile bağlantıyı canlı tutar (`startEnquireLinkTask()`)
- Bağlantı koparsa otomatik **reconnect + rebind** dener (recover thread)
- DB varsa:
    - IN/OUT tüm PDU’ları **raw hex** ve **decoded JSON (map)** olarak kaydeder

### Hangi encoding?
- `data_coding=0x00` için GSM7 kullanır.
- Örnek akışta `Gsm7Codec.encodeUnpacked(msg)` ile **GSM7 (unpacked)** gönderim yapılır.
- (Proje içinde Türkçe single-shift tablosu/ESC desteği varsa) Türkçe karakterler ESC + kod ile gönderilebilir.

---

## 2) Gereksinimler

- Java 17+ (önerilir)
- Maven 3.8+
- PostgreSQL 13+ (DB log kullanılacaksa)
- Ulaşılabilir bir SMSC (host/port + credentials)

---

## 3) Nasıl Çalıştırılır?

### 3.1) Config dosyasını ayarla

Projede `SmppProperties.loadFromTestResources()` kullanıldığı için config genellikle:
- `src/test/resources/smpp.properties`
  veya proje yapına göre
- `src/main/resources/smpp.properties`
  altında olur.

Örnek `smpp.properties`:

```properties
# SMPP Bağlantı
smpp.host=192.168.0.135
smpp.port=9091
# smpp.host=127.0.0.1
# smpp.port=2775

# SMPP Kimlik
smpp.systemId=nettest
smpp.password=e6767
smpp.systemType=cp
smpp.interfaceVersion=0x34
smpp.addrTon=5
smpp.addrNpi=0
smpp.addressRange=nettest

# DB (PDU log için)
db.url=jdbc:postgresql://localhost:5432/postgres
db.user=postgres
db.pass=postgres
```
Not: interfaceVersion=0x34 SMPP v3.4 içindir.

### 3.2) PostgreSQL’i hazırla (opsiyonel ama önerilir)

DB log istiyorsan:

- PostgreSQL’i çalıştır
- Projedeki `schema.sql` (veya DB paketindeki kurulum scripti) ile tabloları oluştur

Örnek:

```bash
psql -h localhost -U postgres -d postgres -f schema.sql
```

> Eğer DB kullanmayacaksan `SmppSessionManager`’ı dao’suz da çalıştırabilirsin (projede uygun constructor mevcuttur).

---

### 3.3) Maven ile çalıştır

Test runner gibi çalıştırıyorsan:

```bash
mvn test
```

Main sınıfı ile çalıştırmak için:

```bash
mvn -q -DskipTests package
java -cp target/<jar-adi>.jar com.mycompany.smppclient.SmppMainRunner
```

Eğer shaded/assembly jar üretmiyorsan IDE üzerinden `SmppMainRunner` sınıfını doğrudan çalıştırmak en pratik yöntemdir.

## 4) Uygulama Akışı (Runtime)

`SmppMainRunner` şu akışla çalışır:

1. Properties yüklenir:
   ```java
   SmppProperties.loadFromTestResources();
   ```

2. Socket config oluşturulur:
   ```java
   SmppSocketConfig sockCfg = new SmppSocketConfig(5000, 5000, 3, 1000);
   ```

3. Session config oluşturulur:
   ```java
   SmppSessionConfig cfg = new SmppSessionConfig(15000, 30000);
   ```

    - `responseTimeoutMs = 15000` → request/response bekleme timeout’u
    - `enquireLinkIntervalMs = 30000` → 30 saniyede bir enquire_link

4. DB bağlantısı açılır (opsiyonel ama bu runner’da aktif):
   ```java
   Db db = new Db(p.dbUrl, p.dbUser, p.dbPass);
   SmppDao dao = new SmppDao(db);
   ```

5. Session manager oluşturulur:
   ```java
   SmppSessionManager sm = new SmppSessionManager(
       socket,
       cfg,
       inbox::offer,
       dao,
       "sess-1",
       p.systemId
   );
   ```

6. Bind yapılır:
   ```java
   sm.bind(p.host, p.port, bindReq);
   ```

7. EnquireLink başlatılır:
   ```java
   sm.startEnquireLinkTask();
   ```

8. CLI komutları:
    - `send <msisdn> <mesaj>`
    - `quit`

9. Gelen `DeliverSmEvent` nesneleri `inbox` kuyruğundan okunur ve ekrana basılır.

---

## 5) Komutlar ve Kullanım

Uygulama açıldıktan sonra:

```
READY. Komut: send <msisdn> <mesaj> | quit
```

### SMS gönderme

```
send +905xxxxxxxxx selam
```

Örnek çıktı:

```
[SUBMIT_SM] message_id=1234567890
```

### Çıkış

```
quit
```

- `unbind` atar
- Socket kapanır
- Uygulama sonlanır

---

## 6) Config Sınıfları Ne İşe Yarar?

### 6.1) SmppSocketConfig

Socket seviyesinde timeout ve reconnect davranışını belirler:

```java
new SmppSocketConfig(
    5000, // connectTimeoutMs
    5000, // readTimeoutMs
    3,    // maxReconnectAttempts
    1000  // reconnectBackoffMs
);
```

- **connectTimeoutMs**: TCP connect için maksimum süre
- **readTimeoutMs**: socket read timeout (data gelmezse read bloklamasın)
- **maxReconnectAttempts**: recover sırasında bir turda kaç deneme yapılacağı
- **reconnectBackoffMs**: denemeler arası bekleme süresi

---

### 6.2) SmppSessionConfig

SMPP request/response ve keepalive davranışı:

```java
new SmppSessionConfig(
    15000, // responseTimeoutMs
    30000  // enquireLinkIntervalMs
);
```

- **responseTimeoutMs**: bind / submit / enquire gibi response bekleme timeout
- **enquireLinkIntervalMs**: keepalive aralığı

---

## 7) GSM7 Encoding Notları

Runner’da aktif satırlar:

```java
req.setDataCoding((byte) 0x00); // GSM7
req.setShortMessage(Gsm7Codec.encodeUnpacked(msg));
```

Anlamı:

- `data_coding=0x00` → GSM default alphabet (GSM7)
- `encodeUnpacked` → her karakter 1 byte (7-bit maskeli) gönderim

> Bazı SMSC’ler packed (septet packing) bekleyebilir. Eğer karşı taraf packed bekliyorsa `encodePacked` gibi bir implementasyon gerekir.

---

## 8) DLR (Delivery Receipt)

DLR istemek için:

```java
req.setRegisteredDelivery((byte) 1); // DLR iste
```

DLR gelince `deliver_sm` içinde:

- `esm_class` ile receipt kontrolü yapılır
- Metin içinde `id:` ve `stat:` gibi alanlar parse edilir
- `DeliveryReceipt` nesnesine dönüştürülür

Ekrana örnek çıktı:

```
[DLR] DeliveryReceipt{...}
```

---

## 9) Sorun Giderme (Kısa)

### Bind olmuyor

- host/port doğru mu?
- systemId/password doğru mu?
- SMSC IP whitelist var mı?
- firewall açık mı?

### submit_sm_resp OK ama mesaj bozuk

- GSM7 packed/unpacked uyuşmuyor olabilir
- Türkçe karakterler için ESC + single-shift gerekiyor olabilir
- data_coding yanlış olabilir (UCS2 gerekiyorsa 0x08)

### DLR gelmiyor

- registered_delivery flag kontrol
- SMSC DLR üretmiyor olabilir
- route DLR desteklemiyor olabilir

---

## 10) Proje Dosya / Entry Point

### Main

```
com.mycompany.smppclient.SmppMainRunner
```

### Socket Katmanı

```
com.mycompany.smppclient.socket.SmppSocketClient
```

### Session Yönetimi

```
com.mycompany.smppclient.session.SmppSessionManager
```

### PDU Encode / Decode

```
com.mycompany.smppclient.pdu.encoder.PduEncoder
com.mycompany.smppclient.pdu.decoder.PduDecoder
```
