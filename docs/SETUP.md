# EMR Demo — Setup Guide

## Prerequisites

### 1. ติดตั้ง Java 17+
- ดาวน์โหลด: https://adoptium.net (Eclipse Temurin 17 LTS)
- ตรวจสอบ: `java -version`

### 2. ติดตั้ง Maven 3.9+
- ดาวน์โหลด: https://maven.apache.org/download.cgi
- แตก zip แล้วเพิ่ม `bin` ใน PATH
- ตรวจสอบ: `mvn -version`

### 3. ติดตั้ง IntelliJ IDEA (แนะนำ Community Edition ฟรี)
- ดาวน์โหลด: https://www.jetbrains.com/idea/download

---

## การตั้งค่า Windows Authentication

Spring Boot ต้องการ `sqljdbc_auth.dll` สำหรับ Windows Auth

### ขั้นตอน:
1. หาไฟล์ `sqljdbc_auth.dll` จาก mssql-jdbc driver
   - path ที่มักอยู่: `C:\Program Files\Microsoft SQL Server\...\Tools\Binn\`
   - หรือ download จาก NuGet: `Microsoft.Data.SqlClient`

2. วาง dll ใน:
   ```
   C:\Windows\System32\sqljdbc_auth.dll    (สำหรับ 64-bit JVM)
   ```

3. ใน IntelliJ ตั้งค่า VM Options:
   - Run > Edit Configurations > VM options:
   ```
   -Djava.library.path=C:\Windows\System32
   ```

---

## การรันโปรแกรม

### วิธีที่ 1: ผ่าน IntelliJ
1. เปิด project folder `emr-demo`
2. IntelliJ จะ auto-detect Maven project
3. รอ download dependencies (ครั้งแรกอาจนาน 2-5 นาที)
4. เปิดไฟล์ `EmrDemoApplication.java`
5. กด Run (▶) หรือ `Shift+F10`
6. เปิด browser ไปที่ `http://localhost:8080`

### วิธีที่ 2: ผ่าน Command Line
```bash
cd emr-demo
mvn spring-boot:run
```

---

## การตั้งค่า application.properties

ไฟล์: `src/main/resources/application.properties`

```properties
# เปลี่ยน path ให้ตรงกับเครื่องจริง
emr.image.base-path=F:/A.Work/My Project/EMR Project/EMRIMAGE

# ถ้า SQL Server ไม่ได้รันที่ default port 1433 ให้แก้
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=IMGEMR;integratedSecurity=true;trustServerCertificate=true;encrypt=false
```

---

## โครงสร้างไฟล์ภาพ

```
F:\A.Work\My Project\EMR Project\EMRIMAGE\
    ├── 5892\
    │   ├── 29245892.jpg
    │   └── 29245893.jpg
    ├── 5893\
    │   └── 29245893.jpg
    └── ...
```

โฟลเดอร์ = 4 หลักท้ายของ PAGENO
ชื่อไฟล์  = PAGENO.extension

---

## API Endpoints (สำหรับ dev reference)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | หน้าหลัก |
| GET | `/api/treatments?hn=XXXXX` | ดึงรายการ treat ของ HN |
| GET | `/api/chartpages/{treatNo}` | ดึงรายการภาพของ treat |
| GET | `/api/image/{pageNo}?ext=jpg` | Serve ไฟล์ภาพ |
| POST | `/api/scan/upload` | อัปโหลด scan ใหม่ |
| GET | `/api/forms` | ดึงรายการฟอร์ม |

---

## Note สำหรับ Demo

- `CLASS = 'S'` ใน TREATT หมายถึง standalone record ที่สร้างจากโปรแกรมนี้
- ถ้า HN+วันที่เดิม scan เพิ่ม จะ reuse TREATNO เดิม ไม่สร้างซ้ำ
- PAGENO ที่ได้จาก identity = ชื่อไฟล์ด้วย
