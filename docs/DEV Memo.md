## ไอเดียทุกอย่าง จะมาเมมไว้ในนี้ กันลืม

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
   - เตรียมไว้แล้ว อยู่ใน Installer mssql-jdbc_auth-13.4.0.x64.dll rename เอา

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

ผ่าน IntelliJ
1. เปิด project folder `emr-demo`
2. IntelliJ จะ auto-detect Maven project
3. รอ download dependencies (ครั้งแรกอาจนาน 2-5 นาที)
4. เปิดไฟล์ `EmrDemoApplication.java`
5. กด Run (▶) หรือ `Shift+F10`
6. เปิด browser ไปที่ `http://localhost:8080`

---

## การตั้งค่า application.properties

ไฟล์: `src/main/resources/application.properties`

```properties
# เปลี่ยน path img select จาก PATHT
ตัว EMRScan จะตัดชื่อไดรฟ์ออกให้เอง แก้ไว้ละ

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

## ระบบ login admin check password plain text ได้ user เดียว เพราะ
1. interface usert,treatt,patientt ผ่าน store procedure แนวคิดคือ ทำเพื่อรองรับ โรงที่ซื้อ Lite version = โปรแกรมแยกจาก HIS
จะได้แก้ที่ store procedure ไม่ต้องแก้โปรแกรม
2. ตอน interface มา password มัน encrypt ด้วย cryptograph vb6 พอมาฝั่ง java มันใช้วิธีถอดรหัสไม่เหมือนกัน และทำให้เหมือนไม่ได้ ถึงแม้จะใช้ cryptograph เหมือนกัน
จึงต้องมีประตูให้ admin เข้าได้ก่อนรอบนึง ปิด-เปิด ได้จาก config ให้ admin พาสเป็น plain text ได้ ละค่อยเปลี่ยนผ่านโปรแกรม ละไปปิด config ด้วย

## user panel, scan ใช้ได้แค่ user ที่ AUTH = 3 นอกนั้นดูได้แค่ viewer
## user managerment, form management, programconfig  เริ่มหน้าแรกมา ให้แสดง top 100
## detail master index = dtldspseq

