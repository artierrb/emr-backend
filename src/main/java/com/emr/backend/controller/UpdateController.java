package com.emr.backend.controller;

import com.emr.backend.service.EmrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Client self-update endpoints.
 *
 * ไฟล์ .exe ใหม่ให้ build machine วางไว้ในโฟลเดอร์เดียว (server path)
 * ที่ตั้งค่าใน HSPCFG key = EMRUPDPTH  (เช่น C:/EMR/updates/)
 *   - ในโฟลเดอร์นั้นวาง EMRPrint.exe, EMRScan.exe (เวอร์ชันล่าสุด) ตามชื่อ app
 *   - version อ่านจาก AssemblyVersion ของไฟล์ .exe เอง (FileVersionInfo)
 *
 * ── endpoints ──
 *   GET /api/update/{app}            → { app, version, sha256, size, fileName }
 *   GET /api/update/{app}/download   → stream ไฟล์ .exe (application/octet-stream)
 *
 * {app} = "emrprint" | "emrscan"  (map เป็นชื่อไฟล์จริง กัน path traversal)
 *
 * NOTE: การอ่าน AssemblyVersion ของ PE (ตำแหน่ง VS_VERSION_INFO) ทำที่ backend ฝั่ง Java
 *       ผ่าน parser เล็กๆ ด้านล่าง — ไม่ต้องรัน .exe และไม่พึ่ง native lib
 */
@RestController
@RequestMapping("/api/update")
public class UpdateController {

    private static final Logger log = LoggerFactory.getLogger(UpdateController.class);

    private final EmrService emrService;

    public UpdateController(EmrService emrService) {
        this.emrService = emrService;
    }

    // whitelist: app key → ชื่อไฟล์จริง (กัน path traversal / arbitrary file read)
    private static final Map<String, String> APP_FILES = Map.of(
            "emrprint", "EMRPrint.exe",
            "emrscan",  "EMRScan.exe"
    );

    // ─── metadata ───────────────────────────────────────────────
    @GetMapping("/{app}")
    public ResponseEntity<?> getMeta(@PathVariable String app) {
        String key = app == null ? "" : app.toLowerCase(Locale.ROOT).trim();
        String fileName = APP_FILES.get(key);
        if (fileName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown app: " + app));
        }
        try {
            File f = resolveFile(fileName);
            if (f == null || !f.isFile()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "update file not found on server"));
            }
            byte[] bytes = Files.readAllBytes(f.toPath());
            String version = readAssemblyVersion(bytes);
            String sha256  = sha256Hex(bytes);

            Map<String, Object> out = new HashMap<>();
            out.put("app", key);
            out.put("fileName", fileName);
            out.put("version", version);       // เช่น "1.4.0.0" ("" ถ้าอ่านไม่ได้)
            out.put("sha256", sha256);
            out.put("size", (long) bytes.length);
            return ResponseEntity.ok(out);
        } catch (IOException e) {
            log.warn("update meta read failed app={}", key, e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── download ───────────────────────────────────────────────
    @GetMapping("/{app}/download")
    public ResponseEntity<Resource> download(@PathVariable String app) {
        String key = app == null ? "" : app.toLowerCase(Locale.ROOT).trim();
        String fileName = APP_FILES.get(key);
        if (fileName == null) return ResponseEntity.badRequest().build();

        File f = resolveFile(fileName);
        if (f == null || !f.isFile()) return ResponseEntity.notFound().build();

        Resource res = new FileSystemResource(f);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentLength(f.length())
                .body(res);
    }

    // ── resolve ไฟล์จาก HSPCFG EMRUPDPTH + ชื่อไฟล์ (whitelist แล้ว) ──
    private File resolveFile(String fileName) {
        String base = emrService.getConfigValue("EMRUPDPTH");
        if (base == null || base.trim().isEmpty()) {
            log.warn("EMRUPDPTH not configured in HSPCFG");
            return null;
        }
        return new File(base.trim(), fileName);
    }

    // ─── SHA-256 hex ────────────────────────────────────────────
    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * อ่าน AssemblyVersion (จริงๆ คือ FileVersion/ProductVersion ใน VS_VERSION_INFO)
     * จาก byte ของ PE โดยหา signature ของ block "VS_VERSION_INFO" (UTF-16LE)
     * แล้วอ่าน VS_FIXEDFILEINFO ที่ตามหลัง — ดึง FileVersion (dwFileVersionMS/LS)
     *
     * คืน "" ถ้าหาไม่เจอ (client จะ fallback ไป compare ด้วย sha256 แทน)
     */
    static String readAssemblyVersion(byte[] pe) {
        // "VS_VERSION_INFO" as UTF-16LE
        byte[] key = "VS_VERSION_INFO".getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        int keyPos = indexOf(pe, key, 0);
        if (keyPos < 0) return "";

        // หลัง key (null-terminated UTF-16 → +2) จะ align 4 ไบต์ แล้วเจอ VS_FIXEDFILEINFO
        int p = keyPos + key.length + 2;
        // align to 32-bit boundary
        while (p % 4 != 0) p++;

        // VS_FIXEDFILEINFO เริ่มด้วย signature 0xFEEF04BD (little-endian)
        // สแกนหาในช่วงสั้นๆ หลัง key (โดยปกติอยู่ติดกัน)
        int sigPos = -1;
        for (int i = p; i < Math.min(pe.length - 4, p + 64); i++) {
            if ((pe[i] & 0xFF) == 0xBD && (pe[i + 1] & 0xFF) == 0x04
                    && (pe[i + 2] & 0xFF) == 0xEF && (pe[i + 3] & 0xFF) == 0xFE) {
                sigPos = i;
                break;
            }
        }
        if (sigPos < 0) return "";

        // layout VS_FIXEDFILEINFO:
        //   +0  dwSignature (4)
        //   +4  dwStrucVersion (4)
        //   +8  dwFileVersionMS (4)   → high word . low word  = major . minor
        //   +12 dwFileVersionLS (4)   → high word . low word  = build . revision
        int msOff = sigPos + 8;
        int lsOff = sigPos + 12;
        if (lsOff + 4 > pe.length) return "";

        long ms = readUInt32LE(pe, msOff);
        long ls = readUInt32LE(pe, lsOff);
        int major = (int) ((ms >> 16) & 0xFFFF);
        int minor = (int) (ms & 0xFFFF);
        int build = (int) ((ls >> 16) & 0xFFFF);
        int rev   = (int) (ls & 0xFFFF);
        return major + "." + minor + "." + build + "." + rev;
    }

    private static long readUInt32LE(byte[] b, int off) {
        return ((b[off] & 0xFFL))
                | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16)
                | ((b[off + 3] & 0xFFL) << 24);
    }

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}