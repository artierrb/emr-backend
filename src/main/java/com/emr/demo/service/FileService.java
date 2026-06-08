package com.emr.demo.service;

import com.emr.demo.model.PathConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    @Value("${emr.image.base-path}")
    private String basePath;

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

    public String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    public String getTodayString() {
        return java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
