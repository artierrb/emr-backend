package com.emr.backend.service;

import com.emr.backend.model.PathConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    @Value("${emr.image.base-path}")
    private String basePath;

    // ขนาดด้านยาวสุดของ thumbnail (px)
    private static final int THUMB_MAX = 240;

    public String getFolderName(long pageNo) {
        if (pageNo < 10000) {
            // น้อยกว่า 10000 → zero-pad 4 หลัก: 1 → "0001", 999 → "0999"
            return String.format("%04d", pageNo);
        } else {
            // 10000 ขึ้นไป → 4 หลักท้าย: 29245892 → "5892"
            String s = String.valueOf(pageNo);
            return s.substring(s.length() - 4);
        }
    }

    public String buildLocalPath(long pageNo, String extension) {
        String folder = getFolderName(pageNo);
        String ext = (extension != null && !extension.isBlank()) ? extension : "jpg";
        return basePath + File.separator + folder + File.separator + pageNo + "." + ext;
    }

    public String buildUncPath(PathConfig pathConfig, long pageNo, String extension) {
        String localPath = pathConfig.getLocalPath();
        String shareName = localPath.contains("\\")
                ? localPath.substring(localPath.lastIndexOf("\\") + 1).toLowerCase()
                : localPath.toLowerCase();
        String folder = getFolderName(pageNo);
        String ext = (extension != null && !extension.isBlank()) ? extension : "jpg";
        return "\\\\" + pathConfig.getIpAddress() + "\\" + shareName + "\\" + folder + "\\" + pageNo + "." + ext;
    }

    /**
     * ตรวจสอบว่าไฟล์ pageNo นี้มีอยู่แล้วหรือไม่
     */
    public boolean fileExists(long pageNo, String extension) {
        String filePath = buildLocalPath(pageNo, extension);
        return Files.exists(Paths.get(filePath));
    }

    /**
     * บันทึกไฟล์ — เรียกหลังจาก fileExists() คืน false แล้วเท่านั้น
     */
    public long saveFile(MultipartFile file, long pageNo, String extension) throws IOException {
        String targetPath = buildLocalPath(pageNo, extension);
        Path path = Paths.get(targetPath);
        Files.createDirectories(path.getParent());
        Files.write(path, file.getBytes());
        log.info("Saved file: {}", targetPath);
        return file.getSize();
    }

    public byte[] readFile(long pageNo, String extension) throws IOException {
        String filePath = buildLocalPath(pageNo, extension);
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        return Files.readAllBytes(path);
    }

    /**
     * คืน "signature" ของไฟล์ = lastModified + size สำหรับทำ ETag
     * อ่านแค่ metadata (ไม่อ่านทั้งไฟล์) → เร็ว
     * re-scan ทับ → lastModified/size เปลี่ยน → ETag เปลี่ยน → browser โหลดใหม่อัตโนมัติ
     */
    public String getFileSignature(long pageNo, String extension) throws IOException {
        String filePath = buildLocalPath(pageNo, extension);
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
        return attr.lastModifiedTime().toMillis() + "-" + attr.size();
    }

    /**
     * อ่านไฟล์แล้ว resize เป็น thumbnail (JPEG, ด้านยาวสุด THUMB_MAX px)
     * ใช้ ImageIO ที่มากับ JDK — ไม่ต้องลง library เพิ่ม
     * รองรับ jpg/png; ถ้าอ่าน/แปลงไม่ได้ (เช่น tiff บาง format) จะ throw → controller fallback ส่งภาพเต็มแทน
     */
    public byte[] readThumbnail(long pageNo, String extension) throws IOException {
        String filePath = buildLocalPath(pageNo, extension);
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }

        BufferedImage src = ImageIO.read(path.toFile());
        if (src == null) {
            // ImageIO อ่านไม่ได้ (เช่น tiff ที่ไม่มี reader) → ให้ caller fallback
            throw new IOException("ImageIO cannot read: " + filePath);
        }

        int w = src.getWidth();
        int h = src.getHeight();
        // ถ้าภาพเล็กกว่า thumbnail อยู่แล้ว ไม่ต้อง resize — return ภาพเดิมเป็น jpeg
        double scale = (double) THUMB_MAX / Math.max(w, h);
        if (scale >= 1.0) scale = 1.0;

        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));

        BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // พื้นขาว (กัน transparent png กลายเป็นดำ)
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, tw, th);
            g.drawImage(src, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(thumb, "jpg", baos);
        return baos.toByteArray();
    }

    /**
     * อ่านไฟล์แล้วแปลงเป็น JPEG เต็มขนาด (ไม่ย่อ) — สำหรับ viewer zoom ที่ต้องแสดง TIFF บน Chrome/Edge
     * ต่างจาก readThumbnail ตรงที่ไม่ resize (คงความละเอียดเดิม)
     */
    public byte[] readAsJpeg(long pageNo, String extension) throws IOException {
        String filePath = buildLocalPath(pageNo, extension);
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        BufferedImage src = ImageIO.read(path.toFile());
        if (src == null) {
            throw new IOException("ImageIO cannot read: " + filePath);
        }
        // แปลงเป็น RGB (กัน tiff/png ที่มี alpha หรือ color model แปลกๆ)
        BufferedImage rgb = src;
        if (src.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, src.getWidth(), src.getHeight());
                g.drawImage(src, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", baos);
        return baos.toByteArray();
    }

    public String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    public String getTodayString() {
        return java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
