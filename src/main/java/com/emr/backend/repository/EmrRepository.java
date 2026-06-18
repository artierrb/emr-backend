package com.emr.backend.repository;

import com.emr.backend.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class EmrRepository {

    private final JdbcTemplate jdbc;

    public EmrRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ─── PATHT ───────────────────────────────────────────────

    public PathConfig getActivePath() {
        return jdbc.queryForObject(
                "SELECT TOP 1 PATHID,IPADDRESS,LOCALPATH,PATHPORT,FTPUSER,FTPPASSWD,ALIAS,ACTIVE FROM PATHT WHERE ACTIVE='Y'",
                (rs, rn) -> {
                    PathConfig p = new PathConfig();
                    p.setPathId(rs.getString("PATHID")); p.setIpAddress(rs.getString("IPADDRESS"));
                    p.setLocalPath(rs.getString("LOCALPATH")); p.setPathPort(rs.getInt("PATHPORT"));
                    p.setFtpUser(rs.getString("FTPUSER")); p.setFtpPasswd(rs.getString("FTPPASSWD"));
                    p.setAlias(rs.getString("ALIAS")); p.setActive(rs.getString("ACTIVE"));
                    return p;
                });
    }

    // ─── FORMT ────────────────────────────────────────────────

    public List<Form> getActiveForms() {
        return jdbc.query(
                "SELECT FORMCODE,NAME,GRPMID,GRPCODE,ORDERBY,CLASS,ACTIVE,OCRYN,PAGECOUNT FROM FORMT WHERE ACTIVE='1' ORDER BY ORDERBY",
                (rs, rn) -> {
                    Form f = new Form();
                    f.setFormCode(rs.getString("FORMCODE")); f.setName(rs.getString("NAME"));
                    f.setGrpMid(rs.getString("GRPMID")); f.setGrpCode(rs.getString("GRPCODE"));
                    f.setOrderBy(rs.getString("ORDERBY"));
                    f.setActive(rs.getString("ACTIVE")); f.setOcrYn(rs.getString("OCRYN"));
                    f.setPageCount(rs.getInt("PAGECOUNT"));
                    return f;
                });
    }

    // ─── TREATT ───────────────────────────────────────────────

    public List<Treatment> getTreatmentsByPatId(String patId) {
        return jdbc.query(
                "SELECT TREATNO,PATID,INDATE,CLINCODE,DOCCODE,CLASS,OCMNUM,WARD,VSTNUM,ADMNUM FROM TREATT WHERE PATID=? ORDER BY INDATE DESC",
                (rs, rn) -> {
                    Treatment t = new Treatment();
                    t.setTreatNo(rs.getLong("TREATNO")); t.setPatId(rs.getString("PATID"));
                    t.setInDate(rs.getString("INDATE")); t.setClinCode(rs.getString("CLINCODE"));
                    t.setDocCode(rs.getString("DOCCODE")); t.setClassType(rs.getString("CLASS"));
                    t.setOcmNum(rs.getString("OCMNUM")); t.setWard(rs.getString("WARD"));
                    t.setVstNum(rs.getString("VSTNUM")); t.setAdmNum(rs.getString("ADMNUM"));
                    return t;
                }, patId);
    }

    public List<Map<String, Object>> getTreatmentsFull(String patId) {
        String sql =
                "SELECT t.TREATNO, t.PATID, t.CLASS, t.CLINCODE, " +
                        "  ISNULL(c.NAME,'') AS CLINNAME, " +
                        "  t.INDATE, ISNULL(t.OUTDATE,'') AS OUTDATE, t.DOCCODE, " +
                        "  ISNULL(u.NAME,'') AS DOCNAME, " +
                        "  ISNULL(t.CHECKED,'0') AS CHECKED, " +
                        "  ISNULL(t.CHECKED2,'0') AS CHECKED2, " +
                        "  ISNULL(t.CHECKED3,'0') AS CHECKED3, " +
                        "  ISNULL(t.CHKDATE,'') AS CHKDATE, ISNULL(t.CHKUSRID,'') AS CHKUSRID, " +
                        "  ISNULL(t.CHKDATE2,'') AS CHKDATE2, ISNULL(t.CHK2USRID,'') AS CHK2USRID, " +
                        "  ISNULL(t.CHKDATE3,'') AS CHKDATE3, ISNULL(t.CHK3USRID,'') AS CHK3USRID, " +
                        "  ISNULL(t.VSTNUM,'') AS VSTNUM, ISNULL(t.ADMNUM,'') AS ADMNUM, " +
                        "  (SELECT COUNT(1) FROM CHARTPAGET cp WHERE cp.TREATNO = t.TREATNO) AS CNT " +
                        "FROM TREATT t " +
                        "LEFT JOIN CLINICT c ON c.CLINCODE = t.CLINCODE " +
                        "LEFT JOIN USERT u ON u.USERID = t.DOCCODE " +
                        "WHERE t.PATID = ? " +
                        "ORDER BY t.INDATE DESC";
        return jdbc.queryForList(sql, patId);
    }

    // ดึง CLINCODE + DOCCODE จาก TREATT ตาม treatNo (ใช้ตอน insert OCRPRINT)
    public Map<String, Object> getTreatClinDoc(long treatNo) {
        try {
            return jdbc.queryForMap(
                    "SELECT RTRIM(ISNULL(CLINCODE,'')) AS CLINCODE, RTRIM(ISNULL(DOCCODE,'')) AS DOCCODE " +
                            "FROM TREATT WHERE TREATNO=?",
                    treatNo);
        } catch (Exception e) {
            return null;
        }
    }

    public void updateTreatCheck(long treatNo, int checkNo, String value, String userId) {
        String now = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sql = switch (checkNo) {
            case 1 -> "UPDATE TREATT SET CHECKED=?,CHKDATE=?,CHKUSRID=? WHERE TREATNO=?";
            case 2 -> "UPDATE TREATT SET CHECKED2=?,CHKDATE2=?,CHK2USRID=? WHERE TREATNO=?";
            case 3 -> "UPDATE TREATT SET CHECKED3=?,CHKDATE3=?,CHK3USRID=? WHERE TREATNO=?";
            default -> throw new IllegalArgumentException("Invalid checkNo: " + checkNo);
        };
        jdbc.update(sql, value, now, userId, treatNo);
    }

    public long insertTreatmentFull(String patId, String inDate, String clinCode,
                                    String docCode, String classType,
                                    String vstNum, String admNum, String userId,
                                    String ocmNum) {
        jdbc.update(
                "INSERT INTO TREATT(PATID,INDATE,CLINCODE,DOCCODE,CLASS,VSTNUM,ADMNUM,OCMNUM) " +
                        "VALUES(?,?,?,?,?,?,?,?)",
                patId, inDate, clinCode, docCode, classType, vstNum, admNum, ocmNum);
        return jdbc.queryForObject("SELECT @@IDENTITY", Long.class);
    }

    public void deleteTreatment(long treatNo) {
        jdbc.update("DELETE FROM TREATT WHERE TREATNO=?", treatNo);
    }

    public synchronized String getNextEOcmNum() {
        try {
            String max = jdbc.queryForObject(
                    "SELECT TOP 1 RTRIM(LTRIM(OCMNUM)) FROM TREATT " +
                            "WHERE OCMNUM LIKE 'E%' " +
                            "ORDER BY CAST(LTRIM(SUBSTRING(RTRIM(LTRIM(OCMNUM)),2,LEN(RTRIM(LTRIM(OCMNUM)))-1)) AS BIGINT) DESC",
                    String.class);
            long next = 1L;
            if (max != null && max.length() > 1) {
                try { next = Long.parseLong(max.substring(1).trim()) + 1L; }
                catch (NumberFormatException ignored) {}
            }
            return String.format("E%9d", next);
        } catch (Exception e) {
            return String.format("E%9d", 1L);
        }
    }

    // ─── CLINICT ──────────────────────────────────────────────

    public List<Map<String, Object>> searchClinic(String keyword) {
        return jdbc.queryForList(
                "SELECT TOP 50 CLINCODE, NAME FROM CLINICT WHERE ACTIVE='1' AND (NAME LIKE ? OR CLINCODE LIKE ?) ORDER BY NAME",
                "%" + keyword + "%", "%" + keyword + "%");
    }

    public List<Map<String, Object>> getActiveClinics() {
        return jdbc.queryForList(
                "SELECT CLINCODE, NAME FROM CLINICT WHERE ACTIVE='1' ORDER BY NAME");
    }

    // ─── USERT ────────────────────────────────────────────────

    public List<Map<String, Object>> searchUser(String keyword) {
        return jdbc.queryForList(
                "SELECT TOP 50 USERID, NAME FROM USERT WHERE NAME LIKE ? OR USERID LIKE ? ORDER BY NAME",
                "%" + keyword + "%", "%" + keyword + "%");
    }

    // ─── OCR Print ────────────────────────────────────────────────

    public List<Map<String, Object>> getOcrPrintSetup(String inputGubun, String loginUserId, String loginClinic) {
        String sql;
        Object[] params;

        switch (inputGubun) {
            case "D" -> {
                sql = """
                    SELECT DISTINCT s.FORMCODE, f.NAME AS FORMNAME, s.INPUTGUBUN, s.SETUPNAME, s.SEQ
                    FROM OCRPRINTSETUPT s
                    JOIN FORMT f ON f.FORMCODE = s.FORMCODE
                    WHERE ISNULL(s.USEYN,'Y')='Y' AND s.INPUTGUBUN='D' AND s.SETUPNAME=?
                    ORDER BY s.SEQ, s.FORMCODE
                    """;
                params = new Object[]{ loginClinic };
            }
            case "U" -> {
                sql = """
                    SELECT DISTINCT s.FORMCODE, f.NAME AS FORMNAME, s.INPUTGUBUN, s.SETUPNAME, s.SEQ
                    FROM OCRPRINTSETUPT s
                    JOIN FORMT f ON f.FORMCODE = s.FORMCODE
                    WHERE ISNULL(s.USEYN,'Y')='Y' AND s.INPUTGUBUN='U' AND s.SETUPNAME=?
                    ORDER BY s.SEQ, s.FORMCODE
                    """;
                params = new Object[]{ loginUserId };
            }
            default -> {
                sql = """
                    SELECT DISTINCT f.FORMCODE, f.NAME AS FORMNAME
                    FROM FORMT f
                    WHERE ISNULL(f.PRINTYN,'Y')='Y'
                    ORDER BY f.FORMCODE
                    """;
                params = new Object[]{};
            }
        }

        return jdbc.queryForList(sql, params);
    }

    public boolean checkReprint(String ocmNum, String formCode) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM OCRPRINT WHERE RTRIM(LTRIM(OCMNUM))=RTRIM(LTRIM(?)) AND RTRIM(FORMCODE)=RTRIM(?)",
                Integer.class, ocmNum == null ? "" : ocmNum.trim(), formCode);
        return cnt != null && cnt > 0;
    }

    public String getPrepth() {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(DtlCodVal,'') FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' AND DtlCod='PREPTH'",
                    String.class);
        } catch (Exception e) { return ""; }
    }

    public String getActivePathIp() {
        try {
            return jdbc.queryForObject(
                    "SELECT TOP 1 ISNULL(IPADDRESS,'') FROM PATHT WHERE ACTIVE='Y'",
                    String.class);
        } catch (Exception e) { return ""; }
    }

    public List<Map<String, Object>> getReprintReasons() {
        return jdbc.queryForList(
                "SELECT DtlCod, DtlCodNam FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='OCRRSN' ORDER BY DtlDspSeq, DtlCod");
    }

    public synchronized String getNextOcrPk() {
        // Lock, read, increment, update - synchronized to prevent duplicate OCRPK
        try {
            jdbc.execute("BEGIN TRANSACTION");
            String current = jdbc.queryForObject(
                    "SELECT OCRPK FROM OCRPK WITH (UPDLOCK, HOLDLOCK)", String.class);
            if (current == null || current.trim().isEmpty()) current = "0000000000000";
            long next = Long.parseLong(current.trim()) + 1L;
            String nextStr = String.format("%013d", next);
            jdbc.update("UPDATE OCRPK SET OCRPK=?", nextStr);
            jdbc.execute("COMMIT TRANSACTION");
            return nextStr;
        } catch (Exception e) {
            try { jdbc.execute("ROLLBACK TRANSACTION"); } catch (Exception ignored) {}
            throw e;
        }
    }

    public String getHostId() {
        try {
            return jdbc.queryForObject("SELECT HOST_ID() AS HOSTID", String.class);
        } catch (Exception e) { return ""; }
    }

    public String getFormOcrYn(String formCode) {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(OCRYN,'N') FROM FORMT WHERE FORMCODE=?", String.class, formCode);
        } catch (Exception e) { return "N"; }
    }

    public String getFormGrpMid(String formCode) {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(GRPMID,'') FROM FORMT WHERE FORMCODE=?", String.class, formCode);
        } catch (Exception e) { return ""; }
    }

    public void insertPrmTmp(String hostId, String formCode, String ocmNum, String outDate,
                             String ocrYn, String ocrPk, String userId) {
        // DELETE existing row first to avoid PrmRptNam unique violation on reprint
        jdbc.update("DELETE FROM BITHIS.dbo.PrmTmp WHERE RTRIM(PrmEmrCod) = ?", ocrPk);

        // PrmRptNam = HOST_ID() + FORMCODE
        String prmRptNam = String.format("%-100s", hostId + formCode);
        // PrmChr_1=ocmNum(10), PrmChr_2=outDate(8), PrmChr_3=ocrYn(1)
        // PrmEmrCod=ocrPk(13), PrmUidCod=userId(8)
        jdbc.update(
                "INSERT INTO BITHIS.dbo.PrmTmp(PrmRptNam,PrmChr_1,PrmChr_2,PrmChr_3,PrmEmrCod,PrmUidCod) " +
                        "VALUES(?,?,?,?,?,?)",
                prmRptNam,
                String.format("%-12s", ocmNum),
                String.format("%-12s", outDate),
                String.format("%-12s", ocrYn),
                ocrPk,
                String.format("%-8s", userId));
    }

    public void insertOcrPrint(String ocrPk, String patId, String inDate, String clinCode,
                               String docCode, String formCode, String userId,
                               String ocmNum, String classType, String reason) {
        String now  = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
        String rptMemo = (reason == null || reason.isBlank()) ? "" :
                reason.length() > 50 ? reason.substring(0, 50) : reason;
        jdbc.update(
                "INSERT INTO OCRPRINT(OCRPK,PATID,INDATE,CLINCODE,DOCCODE,FORMCODE,PRINTYN,PRINTUSERID,PRINTDATE,PRINTTIME,OCMNUM,CLASS,SCANYN,RPTMEMO) " +
                        "VALUES(?,?,?,?,?,?,'Y',?,?,?,?,?,'N',?)",
                ocrPk, patId, inDate, clinCode, docCode, formCode, userId, now, time,
                ocmNum == null ? "" : String.format("%10s", ocmNum.trim()), classType, rptMemo);
    }

    // ─── Viewer query ─────────────────────────────────────────────

    public List<Map<String, Object>> getViewerTreatments(String patId) {
        String sql =
                "SELECT t.TREATNO, t.PATID, t.CLASS, t.CLINCODE, " +
                        "  ISNULL(c.NAME,'') AS CLINNAME, " +
                        "  t.INDATE, ISNULL(t.OUTDATE,'') AS OUTDATE, " +
                        "  ISNULL(t.DOCCODE,'') AS DOCCODE, " +
                        "  ISNULL(u.NAME,'') AS DOCNAME, " +
                        "  (SELECT COUNT(1) FROM CHARTPAGET cp WHERE cp.TREATNO = t.TREATNO) AS CNT " +
                        "FROM TREATT t " +
                        "LEFT JOIN CLINICT c ON c.CLINCODE = t.CLINCODE " +
                        "LEFT JOIN USERT u ON u.USERID = t.DOCCODE " +
                        "WHERE t.PATID = ? " +
                        "  AND ISNULL(t.CHECKED,'0') = '1' " +
                        "  AND ISNULL(t.CHECKED2,'0') = '1' " +
                        "ORDER BY t.INDATE DESC";
        return jdbc.queryForList(sql, patId);
    }

    public List<Map<String, Object>> getViewerPages(String patId, String formCode, String classFilter) {
        String classCondition = (classFilter != null && !classFilter.isBlank() && !classFilter.equals("ALL"))
                ? " AND t.CLASS = '" + classFilter + "'" : "";
        String sql =
                "SELECT cp.PAGENO, cp.TREATNO, cp.FORMCODE, cp.PAGE, cp.CDATE, " +
                        "  ISNULL(p.EXTENSION,'jpg') AS EXTENSION " +
                        "FROM CHARTPAGET cp " +
                        "JOIN TREATT t ON t.TREATNO = cp.TREATNO " +
                        "JOIN PAGET p ON p.PAGENO = cp.PAGENO " +
                        "WHERE t.PATID = ? AND cp.FORMCODE = ? " +
                        "  AND ISNULL(t.CHECKED,'0') = '1' " +
                        "  AND ISNULL(t.CHECKED2,'0') = '1'" +
                        classCondition +
                        " ORDER BY cp.TREATNO DESC, cp.PAGE";
        return jdbc.queryForList(sql, patId, formCode);
    }

    public List<Map<String, Object>> getViewerPagesByTreat(long treatNo, String formCode) {
        String sql =
                "SELECT cp.PAGENO, cp.TREATNO, cp.FORMCODE, cp.PAGE, cp.CDATE, " +
                        "  ISNULL(p.EXTENSION,'jpg') AS EXTENSION " +
                        "FROM CHARTPAGET cp " +
                        "JOIN PAGET p ON p.PAGENO = cp.PAGENO " +
                        "WHERE cp.TREATNO = ? AND cp.FORMCODE = ? " +
                        "ORDER BY cp.PAGE";
        return jdbc.queryForList(sql, treatNo, formCode);
    }

    // ─── CHARTPAGET + PAGET ───────────────────────────────────

    public List<ChartPage> getChartPagesByTreatNo(long treatNo) {
        return jdbc.query(
                "SELECT cp.PAGENO,cp.TREATNO,cp.FORMCODE,f.NAME AS FORMNAME,cp.PAGE,cp.CDATE,cp.GRPMID,p.EXTENSION " +
                        "FROM CHARTPAGET cp LEFT JOIN FORMT f ON f.FORMCODE=cp.FORMCODE LEFT JOIN PAGET p ON p.PAGENO=cp.PAGENO " +
                        "WHERE cp.TREATNO=? ORDER BY cp.FORMCODE,cp.PAGE",
                (rs, rn) -> {
                    ChartPage c = new ChartPage();
                    c.setPageNo(rs.getLong("PAGENO")); c.setTreatNo(rs.getLong("TREATNO"));
                    c.setFormCode(rs.getString("FORMCODE")); c.setFormName(rs.getString("FORMNAME"));
                    c.setPage(rs.getLong("PAGE")); c.setCDate(rs.getString("CDATE"));
                    c.setGrpMid(rs.getString("GRPMID")); c.setExtension(rs.getString("EXTENSION"));
                    return c;
                }, treatNo);
    }

    public List<Map<String, Object>> getFormCountByTreatNo(long treatNo) {
        return jdbc.queryForList(
                "SELECT cp.FORMCODE, COUNT(1) AS CNT FROM CHARTPAGET cp WHERE cp.TREATNO=? GROUP BY cp.FORMCODE",
                treatNo);
    }

    // ─── PAGET identity ───────────────────────────────────────

    public long peekNextPageNo() {
        Long cur = jdbc.queryForObject("SELECT ISNULL(IDENT_CURRENT('PAGET'),0)", Long.class);
        return (cur == null ? 0L : cur) + 1L;
    }

    public void reseedPageNo(long seed) {
        jdbc.execute("DBCC CHECKIDENT ('PAGET', RESEED, " + seed + ")");
    }

    public long insertPage(String pathId, String cDate, String userId, long fileSize, String ext, String ocrPk) {
        jdbc.update("INSERT INTO PAGET(PATHID,CDATE,CUSERID,FILESIZE,EXTENSION,OCRPK) VALUES(?,?,?,?,?,?)",
                pathId, cDate, userId, fileSize, ext, ocrPk);
        return jdbc.queryForObject("SELECT @@IDENTITY", Long.class);
    }

    public void insertChartPage(long pageNo, long treatNo, String formCode, String cDate, String userId, String grpMid) {
        jdbc.update(
                "INSERT INTO CHARTPAGET(PAGENO,TREATNO,FORMCODE,PAGE,CDATE,CUSERID,GRPMID) " +
                        "SELECT ?,?,?,ISNULL(MAX(PAGE),0)+1,?,?,? FROM CHARTPAGET WHERE TREATNO=? AND FORMCODE=? AND GRPMID=?",
                pageNo, treatNo, formCode, cDate, userId, grpMid, treatNo, formCode, grpMid);
    }

    public long insertTreatment(String patId, String inDate, String clinCode) {
        jdbc.update("INSERT INTO TREATT(PATID,INDATE,CLINCODE,CLASS) VALUES(?,?,?,'S')", patId, inDate, clinCode);
        return jdbc.queryForObject("SELECT @@IDENTITY", Long.class);
    }

    public void moveChartPages(java.util.List<Long> pageNos, String newFormCode) {
        for (Long pageNo : pageNos) {
            jdbc.update("UPDATE CHARTPAGET SET FORMCODE=? WHERE PAGENO=?", newFormCode, pageNo);
        }
    }

    public void updateScanLastTime(long treatNo) {
        jdbc.update("UPDATE TREATT SET SCANLASTTIME=GETDATE() WHERE TREATNO=?", treatNo);
    }

    // ─── Config ───────────────────────────────────────────────

    public List<Map<String, Object>> getConfig(String search) {
        if (search != null && !search.isBlank()) {
            return jdbc.queryForList(
                    "SELECT DtlCod,DtlCodNam,DtlCodVal,DtlDspSeq FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' AND DtlCodNam LIKE ? ORDER BY DtlCodNam",
                    "%" + search + "%");
        }
        return jdbc.queryForList(
                "SELECT DtlCod,DtlCodNam,DtlCodVal,DtlDspSeq FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' ORDER BY DtlCodNam");
    }

    public void saveConfigItem(String dtlCod, String dtlCodVal, String userId) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        jdbc.update("UPDATE IMGEMR.dbo.DtlMst SET DtlCodVal=?,DtlUpdDtm=?,DtlUidCod=? WHERE DtlTblCod='[HSPCFG]' AND DtlCod=?",
                dtlCodVal, now, userId, dtlCod);
    }

    public String getConfigValue(String dtlCod) {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(DtlCodVal,'') FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' AND DtlCod=?",
                    String.class, dtlCod);
        } catch (Exception e) { return ""; }
    }

    // ─── Detail Master queries ────────────────────────────────

    public List<Map<String, Object>> getTabTyp() {
        return jdbc.queryForList(
                "SELECT * FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[TABTYP]' AND DtlCod<>'CFG' AND DtlCod<>'ETC' ORDER BY DtlDspSeq,DtlCod");
    }

    public List<Map<String, Object>> getTabMst(String tabCodTyp) {
        return jdbc.queryForList(
                "SELECT RTRIM(TabCod) AS TabCod, RTRIM(ISNULL(TabCodNam,'')) AS TabCodNam, RTRIM(ISNULL(TabCodTyp,'')) AS TabCodTyp, TabUpdYon " +
                        "FROM IMGEMR.dbo.TabMst WHERE RTRIM(TabCodTyp)=? ORDER BY TabCod", tabCodTyp);
    }

    public List<Map<String, Object>> getDtlMst(String dtlTblCod) {
        return jdbc.queryForList("SELECT * FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod=? ORDER BY DtlDspSeq,DtlCod", dtlTblCod);
    }

    public List<Map<String, Object>> getDtsMst(String dtsTblCod, String dtsCod) {
        return jdbc.queryForList(
                "SELECT * FROM IMGEMR.dbo.DtsMst WHERE DtsTblCod=? AND DtsCod=? ORDER BY DtsDspSeq,DtsSubCod",
                dtsTblCod, dtsCod);
    }

    // ─── TabMst CRUD ──────────────────────────────────────────

    public void insertTabMst(String tabCod, String tabCodNam, String tabCodTyp) {
        jdbc.update("INSERT INTO IMGEMR.dbo.TabMst(TabCod,TabCodNam,TabCodTyp,TabUpdYon) VALUES(?,?,?,'N')",
                tabCod.trim().toUpperCase(), tabCodNam, tabCodTyp);
    }

    public void updateTabMst(String tabCod, String tabCodNam, String tabCodTyp) {
        jdbc.update("UPDATE IMGEMR.dbo.TabMst SET TabCodNam=?,TabCodTyp=? WHERE TabCod=?", tabCodNam, tabCodTyp, tabCod);
    }

    public void deleteTabMst(String tabCod) {
        jdbc.update("DELETE FROM IMGEMR.dbo.TabMst WHERE TabCod=?", tabCod);
    }

    // ─── DtlMst CRUD ──────────────────────────────────────────

    public int getNextDtlDspSeq(String dtlTblCod) {
        Integer max = jdbc.queryForObject("SELECT ISNULL(MAX(DtlDspSeq),0)+1 FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod=?", Integer.class, dtlTblCod);
        return max == null ? 1 : max;
    }

    public void insertDtlMst(String dtlTblCod, String dtlCod, String dtlCodNam, String dtlCodVal, int dtlDspSeq, String userId) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        jdbc.update("INSERT INTO IMGEMR.dbo.DtlMst(DtlTblCod,DtlCod,DtlCodNam,DtlCodVal,DtlDspSeq,DtlUpdDtm,DtlUidCod) VALUES(?,?,?,?,?,?,?)",
                dtlTblCod, dtlCod.trim().toUpperCase(), dtlCodNam, dtlCodVal, dtlDspSeq, now, userId);
    }

    public void updateDtlMst(String dtlTblCod, String dtlCod, String dtlCodNam, String dtlCodVal, String userId) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        jdbc.update("UPDATE IMGEMR.dbo.DtlMst SET DtlCodNam=?,DtlCodVal=?,DtlUpdDtm=?,DtlUidCod=? WHERE DtlTblCod=? AND DtlCod=?",
                dtlCodNam, dtlCodVal, now, userId, dtlTblCod, dtlCod);
    }

    public void deleteDtlMst(String dtlTblCod, String dtlCod) {
        jdbc.update("DELETE FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod=? AND DtlCod=?", dtlTblCod, dtlCod);
    }

    public void updateDtlDspSeq(String dtlTblCod, String dtlCod, int seq) {
        jdbc.update("UPDATE IMGEMR.dbo.DtlMst SET DtlDspSeq=? WHERE DtlTblCod=? AND DtlCod=?", seq, dtlTblCod, dtlCod);
    }

    // ─── DtsMst CRUD ──────────────────────────────────────────

    public int getNextDtsDspSeq(String dtsTblCod, String dtsCod) {
        Integer max = jdbc.queryForObject(
                "SELECT ISNULL(MAX(DtsDspSeq),0)+1 FROM IMGEMR.dbo.DtsMst WHERE DtsTblCod=? AND DtsCod=?",
                Integer.class, dtsTblCod, dtsCod);
        return max == null ? 1 : max;
    }

    public void insertDtsMst(String dtsTblCod, String dtsCod, String dtsSubCod, String dtsCodNam, String dtsCodVal, int dtsDspSeq, String userId) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        jdbc.update("INSERT INTO IMGEMR.dbo.DtsMst(DtsTblCod,DtsCod,DtsSubCod,DtsCodNam,DtsCodVal,DtsDspSeq,DtsUpdDtm,DtsUidCod) VALUES(?,?,?,?,?,?,?,?)",
                dtsTblCod, dtsCod, dtsSubCod.trim().toUpperCase(), dtsCodNam, dtsCodVal, dtsDspSeq, now, userId);
    }

    public void updateDtsMst(String dtsTblCod, String dtsCod, String dtsSubCod, String dtsCodNam, String dtsCodVal, String userId) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        jdbc.update("UPDATE IMGEMR.dbo.DtsMst SET DtsCodNam=?,DtsCodVal=?,DtsUpdDtm=?,DtsUidCod=? WHERE DtsTblCod=? AND DtsCod=? AND DtsSubCod=?",
                dtsCodNam, dtsCodVal, now, userId, dtsTblCod, dtsCod, dtsSubCod);
    }

    public void deleteDtsMst(String dtsTblCod, String dtsCod, String dtsSubCod) {
        jdbc.update("DELETE FROM IMGEMR.dbo.DtsMst WHERE DtsTblCod=? AND DtsCod=? AND DtsSubCod=?", dtsTblCod, dtsCod, dtsSubCod);
    }

    public void updateDtsDspSeq(String dtsTblCod, String dtsCod, String dtsSubCod, int seq) {
        jdbc.update("UPDATE IMGEMR.dbo.DtsMst SET DtsDspSeq=? WHERE DtsTblCod=? AND DtsCod=? AND DtsSubCod=?",
                seq, dtsTblCod, dtsCod, dtsSubCod);
    }

    // ─── USERT Auth ───────────────────────────────────────────────

    public Map<String, Object> findUserById(String userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT USERID,PASSWD,NAME,AUTH,EDATE,CLINCODE FROM USERT WHERE USERID=?", userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> searchUsers(String field, String keyword) {
        String col = "NAME".equals(field) ? "NAME" : "USERID";
        return jdbc.queryForList(
                "SELECT TOP 100 USERID,NAME,AUTH,EDATE,CLINCODE FROM USERT WHERE " + col + " LIKE ? ORDER BY " + col,
                "%" + keyword + "%");
    }

    public void insertUser(String userId, String encPwd, String name, String auth,
                           String clinCode, String edate) {
        jdbc.update(
                "INSERT INTO USERT(USERID,PASSWD,NAME,AUTH,CLINCODE,EDATE,PRINTAUTH,SECUOKAUTH,NEEDPRTAUTH,SCANMOVAUTH,SCANDELAUTH,RENTAUTH) " +
                        "VALUES(?,?,?,?,?,?,'0','N','N','N','N','N')",
                userId, encPwd, name, auth, clinCode, edate);
    }

    public void updateUser(String userId, String name, String auth,
                           String clinCode, String edate) {
        jdbc.update("UPDATE USERT SET NAME=?,AUTH=?,CLINCODE=?,EDATE=? WHERE USERID=?",
                name, auth, clinCode, edate, userId);
    }

    public void updateUserPassword(String userId, String encPwd) {
        jdbc.update("UPDATE USERT SET PASSWD=? WHERE USERID=?", encPwd, userId);
    }

    public void deleteUser(String userId) {
        jdbc.update("DELETE FROM USERT WHERE USERID=?", userId);
    }

    public void userInterfaceFromHis() {
        jdbc.execute("EXEC IMGEMR.dbo.SP_InterfaceUSERT");
        jdbc.execute("EXEC IMGEMR.dbo.SP_InterfaceTREATT");
        jdbc.execute("EXEC IMGEMR.dbo.SP_InterfacePATIENTT");
    }

    // ─── HIS Interface by PATID (sync เฉพาะ HN เดียว ก่อนหาคนไข้) ──────────
    // ใช้ตอนพิมพ์ HN ในหน้า Scan/View: sync ข้อมูลของ HN นั้นจาก HIS ก่อน
    // เพื่อให้คนไข้/แฟ้มที่เพิ่งมาใหม่ใน HIS ปรากฏใน EMR ทันที
    // ห้าม trim — SP ต้องการ HN แบบ padded 8 หลักตามที่ผู้ใช้กรอก (เช่น '69     2')
    public void interfaceByPatId(String patId) {
        if (patId == null || patId.isBlank()) return;
        try {
            jdbc.update("EXEC IMGEMR.dbo.SP_InterfaceTREATT_PATID ?", patId);
            jdbc.update("EXEC IMGEMR.dbo.SP_InterfacePATIENTT_PATID ?", patId);
        } catch (Exception e) {
            // ไม่ให้ sync ล้มเหลวทำให้โหลดข้อมูลพังทั้งหมด — log แล้วไปต่อด้วยข้อมูลเดิม
            org.slf4j.LoggerFactory.getLogger(EmrRepository.class)
                    .warn("interfaceByPatId failed for HN=[{}]: {}", patId, e.getMessage());
        }
    }

    // หาคนไข้รายเดียวด้วย PATID (เทียบแบบ trim padding) — แยกจาก searchPatient ที่ใช้ใน search modal
    public List<Map<String, Object>> findPatientByPatId(String patId) {
        return jdbc.queryForList(
                "SELECT TOP 50 PATID,NAME,JUMINNO,SEX,BIRTHDATE,AGE FROM PATIENTT " +
                        "WHERE RTRIM(LTRIM(PATID)) LIKE ? ORDER BY PATID",
                "%" + (patId == null ? "" : patId.trim()) + "%");
    }

    private String str(Object o) { return o == null ? "" : o.toString().trim(); }

    // ─── OCS Launch ───────────────────────────────────────────

    // sync USERT เฉพาะ 1 user จาก HIS (ใช้ตอน OCS launch — ให้ user ที่เพิ่งมีใน HIS ใช้งานได้)
    public void interfaceUsertByUserId(String userId) {
        if (userId == null || userId.isBlank()) return;
        try {
            jdbc.update("EXEC IMGEMR.dbo.SP_InterfaceUSERT_USERID ?", userId);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmrRepository.class)
                    .warn("interfaceUsertByUserId failed for USERID=[{}]: {}", userId, e.getMessage());
        }
    }

    // อ่าน config เปิด/ปิด IP whitelist: '[HSPCFG]','IPWHITELST' → Y=เปิด N=ปิด
    // ─── Watermark config ([HSPCFG]) ─────────────────────────────

    // WTRMRKYN: 'Y'=เปิด watermark ตอนพิมพ์, อื่นๆ=ปิด
    public String getWatermarkFlag() {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(DtlCodVal,'N') FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' AND DtlCod='WTRMRKYN'",
                    String.class);
        } catch (Exception e) { return "N"; }
    }

    // WTRMRKPTH: path ไฟล์ watermark บน server (เช่น C:\emr\watermark.png)
    public String getWatermarkPath() {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(DtlCodVal,'') FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' AND DtlCod='WTRMRKPTH'",
                    String.class);
        } catch (Exception e) { return ""; }
    }

    // WTRMRKOVL: ความโปร่งใส watermark เป็นเปอร์เซ็นต์ เช่น 20 (= opacity 0.20)
    public String getWatermarkOverlay() {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(DtlCodVal,'') FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' AND DtlCod='WTRMRKOVL'",
                    String.class);
        } catch (Exception e) { return ""; }
    }

    public String getIpWhitelistFlag() {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(DtlCodVal,'N') FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' AND DtlCod='IPWHITELST'",
                    String.class);
        } catch (Exception e) { return "N"; }
    }

    // อ่าน range ที่อนุญาต (เก็บใน DtlCodVal, หลาย range คั่นด้วย comma)
    // WHERE DtlTblCod='WLRANGE' AND DtlCod='RANGE'
    public String getWhitelistRanges() {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(DtlCodVal,'') FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='WLRANGE' AND DtlCod='RANGE'",
                    String.class);
        } catch (Exception e) { return ""; }
    }

    // verify ว่า PATID + OCMNUM มีคู่กันจริงใน TREATT แล้วคืน treat object เต็ม (มี treatNo) — กันปลอม PATID/OCMNUM มั่ว
    // ห้าม trim — OCMNUM/PATID เป็นค่า padded ที่ OCS ส่งมา ต้อง match แบบ trim ทั้งสองฝั่งใน SQL
    public Map<String, Object> findTreatByPatIdOcmNum(String patId, String ocmNum) {
        try {
            return jdbc.queryForMap(
                    "SELECT TOP 1 t.TREATNO, RTRIM(t.PATID) AS PATID, t.CLASS, RTRIM(ISNULL(t.CLINCODE,'')) AS CLINCODE, " +
                            "  RTRIM(ISNULL(c.NAME,'')) AS CLINNAME, " +
                            "  RTRIM(ISNULL(p.NAME,'')) AS PATNAME, " +
                            "  t.INDATE, ISNULL(t.OUTDATE,'') AS OUTDATE, RTRIM(ISNULL(t.DOCCODE,'')) AS DOCCODE, " +
                            "  RTRIM(ISNULL(u.NAME,'')) AS DOCNAME, " +
                            "  RTRIM(ISNULL(t.OCMNUM,'')) AS OCMNUM, RTRIM(ISNULL(t.VSTNUM,'')) AS VSTNUM, RTRIM(ISNULL(t.ADMNUM,'')) AS ADMNUM " +
                            "FROM TREATT t " +
                            "LEFT JOIN CLINICT c ON c.CLINCODE = t.CLINCODE " +
                            "LEFT JOIN USERT u ON u.USERID = t.DOCCODE " +
                            "LEFT JOIN PATIENTT p ON RTRIM(LTRIM(p.PATID)) = RTRIM(LTRIM(t.PATID)) " +
                            "WHERE RTRIM(LTRIM(t.OCMNUM))=RTRIM(LTRIM(?)) AND RTRIM(LTRIM(t.PATID))=RTRIM(LTRIM(?))",
                    ocmNum, patId);
        } catch (Exception e) {
            return null;
        }
    }

    // ─── OCS Access Control (4 ชั้น) ──────────────────────────────

    // ชั้น 1: SecurePatient — คนไข้ร้องขอปกปิดข้อมูล
    public boolean isSecurePatient(String patId) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM USERSECUSECRET " +
                            "WHERE SECUAGREE='Y' AND SECUENDYN='N' AND RTRIM(SECUPAT)=RTRIM(?)",
                    Integer.class, patId);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            // ถ้า query error ให้ถือว่าไม่ปลอดภัย → block (fail-safe สำหรับข้อมูล secure)
            org.slf4j.LoggerFactory.getLogger(EmrRepository.class)
                    .warn("isSecurePatient check failed for PATID=[{}]: {}", patId, e.getMessage());
            return true;
        }
    }

    // ชั้น 2: WRKRANGE จาก config [HSPCFG] (DtlMst) — จำนวนวันที่ดูได้นับจาก INDATE
    //   ค่าเป็นตัวเลขปกติ เช่น 30 (ไม่ใช่ติดลบแล้ว) — ถ้าไม่เจอ/null/parse ไม่ได้ คืน null → service block
    public Integer getWorkRange() {
        try {
            String val = jdbc.queryForObject(
                    "SELECT DtlCodVal FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' AND DtlCod='WRKRANGE'",
                    String.class);
            if (val == null || val.trim().isEmpty()) return null;
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ชั้น 3: เช็คว่า login user เป็นหมอหรือไม่ — v_UidMst.UidDtrYon ('Y'=หมอ)
    public boolean isDoctor(String userId) {
        try {
            String yn = jdbc.queryForObject(
                    "SELECT ISNULL(UidDtrYon,'N') FROM v_UidMst WHERE RTRIM(UidCod)=RTRIM(?)",
                    String.class, userId);
            return "Y".equalsIgnoreCase(yn == null ? "N" : yn.trim());
        } catch (Exception e) {
            return false;
        }
    }

    // ชั้น 4: RentPatient — เช็คสิทธิ์ยืมแฟ้มที่ active และ admin อนุมัติแล้ว (RENTYN='Y')
    public boolean hasActiveRent(String userId, String patId, String curDate) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM RENTT " +
                            "WHERE RTRIM(USERID)=RTRIM(?) AND RTRIM(LTRIM(PATID))=RTRIM(LTRIM(?)) " +
                            "  AND ? BETWEEN RENTSDT AND RENTEDT " +
                            "  AND BANNABYN='N' AND RENTYN='Y'",
                    Integer.class, userId, patId, curDate);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // BLOCKRENT: เช็คว่ามี request ยืมแฟ้มค้างอยู่แล้วหรือยัง (รวม BANNABYN='N')
    public boolean hasRentRequest(String userId, String patId, String curDate) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM RENTT " +
                            "WHERE RTRIM(USERID)=RTRIM(?) AND RTRIM(LTRIM(PATID))=RTRIM(LTRIM(?)) " +
                            "  AND ? BETWEEN RENTSDT AND RENTEDT " +
                            "  AND BANNABYN='N'",
                    Integer.class, userId, patId, curDate);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ─── RENTT register (ลงทะเบียนขอยืมแฟ้ม) ───────────────────────

    public List<Map<String, Object>> getRentReasons() {
        return jdbc.queryForList(
                "SELECT DtlCod, DtlCodNam FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='RENTRSN' ORDER BY DtlDspSeq");
    }

    public void insertRent(String userId, String clinCode, String patId,
                           String rentCode, String rentName,
                           String rentSdt, String rentEdt, String rentRemark) {
        // RENTNO เป็น identity column → DB รันเลขเอง ห้าม insert
        // SEQ=0, RENTAUTO='N', RENTYN='N', AGREENO=null, RENTDT=''(รอ admin approve),
        // RENTHOPEDT='', CDATE=GETDATE(), BANNABYN='N', BANNABDATE=GETDATE()
        jdbc.update(
                "INSERT INTO RENTT(USERID,CLINCODE,RENTCODE,RENTNAME,RENTDT,RENTSDT,RENTEDT,SEQ,PATID," +
                        "RENTAUTO,RENTREMARK,RENTYN,AGREENO,CDATE,BANNABYN,BANNABDATE,RENTHOPEDT) " +
                        "VALUES(?,?,?,?,'',?,?,0,?,'N',?,'N',NULL,GETDATE(),'N',GETDATE(),'')",
                userId, clinCode, rentCode, rentName, rentSdt, rentEdt, patId, rentRemark);
    }

    // ─── USERSECUSECRET register (ลงทะเบียนปกปิดข้อมูลผู้ป่วย) ─────

    // เช็คว่ามี request ปกปิดค้างอยู่แล้วหรือยัง (PATID + SECUENDYN='N')
    // กันลงทะเบียนซ้ำ — ครอบทั้งที่รออนุมัติ (SECUAGREE='N') และอนุมัติแล้ว (SECUAGREE='Y') ที่ยังไม่ end
    public boolean hasSecureRequest(String patId) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM USERSECUSECRET " +
                            "WHERE RTRIM(SECUPAT)=RTRIM(?) AND SECUENDYN='N'",
                    Integer.class, patId);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void insertSecure(String userId, String patId, String memo) {
        // ลงทะเบียนใหม่: SECUAGREE='N' (รอ admin อนุมัติ), SECUENDYN='N',
        // SECUDATE=GETDATE(), agree/end date = NULL, cuserid/enduserid/agreeuserid = ''
        jdbc.update(
                "INSERT INTO USERSECUSECRET(USERID,SECUDATE,SECUAGREE,SECUAGREEDATE,SECUENDYN," +
                        "SECUENDDATE,SECUMEMO,SECUPAT,CUSERID,ENDUSERID,AGREEUSERID) " +
                        "VALUES(?,GETDATE(),'N',NULL,'N',NULL,?,?,'','','')",
                userId, memo, patId);
    }

    // ─── Security admin (confirm/end) ─────────────────────────────

    // list สำหรับหน้า ScanSecurityView — filter ด้วย HN / status / ช่วงวันที่ SECUDATE
    // PK ใช้ key = CONVERT(varchar(23),SECUDATE,121) + '|' + RTRIM(SECUPAT) (กัน datetime precision)
    public List<Map<String, Object>> searchSecure(String patId, String status,
                                                  String dateFrom, String dateTo) {
        StringBuilder sql = new StringBuilder(
                "SELECT CONVERT(varchar(23), s.SECUDATE, 121) AS ROWKEY, " +
                        "  RTRIM(ISNULL(u.NAME,'')) AS REQUESTER, " +
                        "  RTRIM(s.SECUPAT) AS SECUPAT, " +
                        "  RTRIM(ISNULL(p.NAME,'')) AS PATNAME, " +
                        "  CONVERT(varchar(23), s.SECUDATE, 121) AS SECUDATE, " +
                        "  ISNULL(s.SECUMEMO,'') AS SECUMEMO, " +
                        "  ISNULL(s.SECUAGREE,'N') AS SECUAGREE, " +
                        "  CONVERT(varchar(23), s.SECUAGREEDATE, 121) AS SECUAGREEDATE, " +
                        "  ISNULL(s.SECUENDYN,'N') AS SECUENDYN, " +
                        "  CONVERT(varchar(23), s.SECUENDDATE, 121) AS SECUENDDATE, " +
                        "  RTRIM(ISNULL(s.AGREEUSERID,'')) AS AGREEUSERID, " +
                        "  RTRIM(ISNULL(s.ENDUSERID,'')) AS ENDUSERID " +
                        "FROM USERSECUSECRET s " +
                        "LEFT JOIN USERT u ON RTRIM(u.USERID)=RTRIM(s.USERID) " +
                        "LEFT JOIN PATIENTT p ON RTRIM(LTRIM(p.PATID))=RTRIM(LTRIM(s.SECUPAT)) " +
                        "WHERE 1=1 ");
        List<Object> args = new java.util.ArrayList<>();

        if (patId != null && !patId.trim().isEmpty()) {
            sql.append("AND RTRIM(s.SECUPAT)=RTRIM(?) ");
            args.add(patId);
        }
        // status: WAITING / CONFIRMED / END / ALL
        switch (status == null ? "ALL" : status) {
            case "WAITING":   sql.append("AND ISNULL(s.SECUAGREE,'N')='N' AND ISNULL(s.SECUENDYN,'N')='N' "); break;
            case "CONFIRMED": sql.append("AND ISNULL(s.SECUAGREE,'N')='Y' AND ISNULL(s.SECUENDYN,'N')='N' "); break;
            case "END":       sql.append("AND ISNULL(s.SECUAGREE,'N')='Y' AND ISNULL(s.SECUENDYN,'N')='Y' "); break;
            default: break; // ALL
        }
        if (dateFrom != null && !dateFrom.isEmpty()) {
            sql.append("AND s.SECUDATE >= ? ");
            args.add(dateFrom + " 00:00:00");
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            sql.append("AND s.SECUDATE <= ? ");
            args.add(dateTo + " 23:59:59");
        }
        sql.append("ORDER BY s.SECUDATE DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // Confirm — เฉพาะ row ที่ SECUAGREE='N' (match ด้วย SECUPAT + SECUDATE string)
    public int confirmSecure(String patId, String rowKey, String loginId) {
        return jdbc.update(
                "UPDATE USERSECUSECRET SET SECUAGREE='Y', SECUAGREEDATE=GETDATE(), CUSERID=?, AGREEUSERID=? " +
                        "WHERE RTRIM(SECUPAT)=RTRIM(?) AND CONVERT(varchar(23),SECUDATE,121)=? " +
                        "  AND ISNULL(SECUAGREE,'N')='N'",
                loginId, loginId, patId, rowKey);
    }

    // End — เฉพาะ row ที่ SECUAGREE='Y' AND SECUENDYN='N'
    public int endSecure(String patId, String rowKey, String loginId) {
        return jdbc.update(
                "UPDATE USERSECUSECRET SET SECUENDYN='Y', SECUENDDATE=GETDATE(), ENDUSERID=? " +
                        "WHERE RTRIM(SECUPAT)=RTRIM(?) AND CONVERT(varchar(23),SECUDATE,121)=? " +
                        "  AND ISNULL(SECUAGREE,'N')='Y' AND ISNULL(SECUENDYN,'N')='N'",
                loginId, patId, rowKey);
    }

    // ─── Rent admin (search/confirm) ─────────────────────────────

    // list หน้า ScanRentView — filter: RENTNO / HN / status / ช่วง RENTSDT / RENTCODE(reason)
    public List<Map<String, Object>> searchRent(String rentNo, String patId, String status,
                                                String dateFrom, String dateTo, String rentCode) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.RENTNO, RTRIM(LTRIM(r.PATID)) AS PATID, " +
                        "  RTRIM(ISNULL(p.NAME,'')) AS PATNAME, " +
                        "  r.RENTSDT, r.RENTEDT, RTRIM(ISNULL(r.RENTNAME,'')) AS RENTNAME, " +
                        "  RTRIM(ISNULL(r.RENTCODE,'')) AS RENTCODE, " +
                        "  ISNULL(r.RENTYN,'N') AS RENTYN, " +
                        "  RTRIM(ISNULL(r.USERID,'')) AS USERID, " +
                        "  ISNULL(r.AGREENO, 0) AS AGREENO " +
                        "FROM RENTT r " +
                        "LEFT JOIN PATIENTT p ON RTRIM(LTRIM(p.PATID))=RTRIM(LTRIM(r.PATID)) " +
                        "WHERE 1=1 ");
        List<Object> args = new java.util.ArrayList<>();

        if (rentNo != null && !rentNo.trim().isEmpty()) {
            sql.append("AND r.RENTNO = ? ");
            args.add(Long.parseLong(rentNo.trim()));
        }
        if (patId != null && !patId.trim().isEmpty()) {
            sql.append("AND RTRIM(LTRIM(r.PATID))=RTRIM(LTRIM(?)) ");
            args.add(patId);
        }
        // status: WAITING (RENTYN='N') / CONFIRMED (RENTYN='Y') / ALL
        switch (status == null ? "ALL" : status) {
            case "WAITING":   sql.append("AND ISNULL(r.RENTYN,'N')='N' "); break;
            case "CONFIRMED": sql.append("AND ISNULL(r.RENTYN,'N')='Y' "); break;
            default: break;
        }
        if (dateFrom != null && !dateFrom.isEmpty()) {
            sql.append("AND r.RENTSDT >= ? ");
            args.add(dateFrom.replace("-", ""));   // RENTSDT เก็บเป็น yyyyMMdd (varchar)
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            sql.append("AND r.RENTSDT <= ? ");
            args.add(dateTo.replace("-", ""));
        }
        if (rentCode != null && !rentCode.trim().isEmpty()) {
            sql.append("AND RTRIM(r.RENTCODE)=RTRIM(?) ");
            args.add(rentCode);
        }
        sql.append("ORDER BY r.RENTNO DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // Confirm — set RENTYN='Y', AGREENO=Max+1, BANNABDATE=now (เฉพาะ RENTYN='N')
    public int confirmRent(long rentNo) {
        Long nextAgree = jdbc.queryForObject("SELECT ISNULL(MAX(AGREENO),0)+1 FROM RENTT", Long.class);
        return jdbc.update(
                "UPDATE RENTT SET RENTYN='Y', AGREENO=?, BANNABDATE=GETDATE() " +
                        "WHERE RENTNO=? AND ISNULL(RENTYN,'N')='N'",
                nextAgree, rentNo);
    }

    // ─── PATIENTT ─────────────────────────────────────────────

    public String getHnSepConfig() {
        try {
            return jdbc.queryForObject(
                    "SELECT ISNULL(DtlCodVal,'N') FROM IMGEMR.dbo.DtlMst WHERE DtlTblCod='[HSPCFG]' AND DtlCod='HNSEP'",
                    String.class);
        } catch (Exception e) { return "N"; }
    }

    public List<Map<String, Object>> searchPatient(String field, String keyword) {
        String col = switch (field) {
            case "NAME"    -> "NAME";
            case "JUMINNO" -> "JUMINNO";
            default        -> "PATID";
        };
        return jdbc.queryForList(
                "SELECT TOP 50 PATID,NAME,JUMINNO,SEX,BIRTHDATE,AGE FROM PATIENTT WHERE RTRIM(LTRIM(" + col + ")) LIKE ? ORDER BY PATID",
                "%" + keyword.trim() + "%");
    }

    public void insertPatient(String patId, String name, String sex, String jumiNno, String birthDate, int age, String userId) {
        jdbc.update(
                "INSERT INTO PATIENTT(PATID,NAME,SEX,JUMINNO,BIRTHDATE,AGE,CDATE,CUSERID) VALUES(?,?,?,?,?,?,GETDATE(),?)",
                patId, name, sex, jumiNno, birthDate, age, userId);
    }

    public boolean patientExists(String patId) {
        // Compare trimmed values to handle different padding formats
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(1) FROM PATIENTT WHERE RTRIM(LTRIM(PATID))=RTRIM(LTRIM(?))",
                Integer.class, patId);
        return cnt != null && cnt > 0;
    }

    // ─── FORMT CRUD ───────────────────────────────────────────

    public List<Map<String, Object>> searchForms(String field, String keyword) {
        String col = "NAME".equals(field) ? "f.NAME" : "f.FORMCODE";
        String sql =
                "SELECT f.FORMCODE, f.NAME, f.GRPCODE, ISNULL(g.NAME,'') AS GRPNAME, " +
                        "  ISNULL(f.ACTIVE,'0') AS ACTIVE, ISNULL(f.OCRYN,'N') AS OCRYN, " +
                        "  ISNULL(f.MEDIYN,'N') AS MEDIYN, ISNULL(f.OCRPRINT,'N') AS OCRPRINT, " +
                        "  ISNULL(f.PAGECOUNT,1) AS PAGECOUNT, ISNULL(f.FOLLOWYN,'N') AS FOLLOWYN, " +
                        "  ISNULL(f.PRINTYN,'N') AS PRINTYN " +
                        "FROM FORMT f " +
                        "LEFT JOIN GRPFORMT g ON g.GRPCODE = f.GRPCODE " +
                        "WHERE " + col + " LIKE ? " +
                        "ORDER BY f.FORMCODE";
        return jdbc.queryForList(sql, "%" + (keyword == null ? "" : keyword) + "%");
    }

    public List<Map<String, Object>> getFormGroups() {
        return jdbc.queryForList(
                "SELECT GRPCODE, NAME FROM GRPFORMT ORDER BY ORDERBY, NAME");
    }

    public void insertForm(String formCode, String name, String grpCode, String active,
                           String ocrYn, String mediYn, String ocrPrint, int pageCount,
                           String followYn, String printYn) {
        jdbc.update(
                "INSERT INTO FORMT(FORMCODE,NAME,GRPCODE,ACTIVE,OCRYN,MEDIYN,OCRPRINT,PAGECOUNT,FOLLOWYN,PRINTYN,ORDERBY) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,(SELECT ISNULL(MAX(CAST(ORDERBY AS INT)),0)+1 FROM FORMT))",
                formCode.trim(), name, grpCode, active, ocrYn, mediYn, ocrPrint, pageCount, followYn, printYn);
    }

    public void updateForm(String formCode, String name, String grpCode, String active,
                           String ocrYn, String mediYn, String ocrPrint, int pageCount,
                           String followYn, String printYn) {
        jdbc.update(
                "UPDATE FORMT SET NAME=?,GRPCODE=?,ACTIVE=?,OCRYN=?,MEDIYN=?,OCRPRINT=?,PAGECOUNT=?,FOLLOWYN=?,PRINTYN=? " +
                        "WHERE FORMCODE=?",
                name, grpCode, active, ocrYn, mediYn, ocrPrint, pageCount, followYn, printYn, formCode);
    }

    public void deleteForm(String formCode) {
        jdbc.update("DELETE FROM FORMT WHERE FORMCODE=?", formCode);
    }

    // ─── OCR Return ───────────────────────────────────────────

    public List<Map<String, Object>> getOcrReturnList(String startDate, String endDate,
                                                      String clinCode, String scanYn, String grpCode, String hn, String ocrPk) {

        StringBuilder sql = new StringBuilder(
                "SELECT RTRIM(O.PATID) AS PATID, P.NAME, " +
                        "  (CASE T.CLASS WHEN 'O' THEN T.VSTNUM ELSE T.ADMNUM END) AS VN, C.DEPTCODE AS DEPTCODE, " +
                        "  O.INDATE, O.INTIME, O.SCANYN, RTRIM(O.OCRPK) AS OCRPK, " +
                        "  (CASE WHEN CC.FORMCODE IS NOT NULL AND CC.FORMCODE <> '' " +
                        "        THEN dbo.GetFormName(CC.FORMCODE) ELSE dbo.GetFormName(O.FORMCODE) END) AS FORMNAME, " +
                        "  (CASE WHEN O.SCANYN='N' THEN '0/' + CAST(F.PAGECOUNT AS VARCHAR) " +
                        "        WHEN O.SCANYN='Y' THEN ISNULL(CC.SCAN,'0') + '/' + ISNULL(CC.PAGE, CAST(F.PAGECOUNT AS VARCHAR)) " +
                        "        ELSE '' END) AS PAGECOUNT, " +
                        "  O.CLASS, ISNULL(O.BIGO,'') AS BIGO " +
                        "FROM OCRPRINT O " +
                        "LEFT JOIN ( " +
                        "    SELECT PAGET.OCRPK, CHARTPAGET.FORMCODE, " +
                        "      CAST(COUNT(PAGET.PAGENO) AS VARCHAR) AS SCAN, CAST(FORMT.PAGECOUNT AS VARCHAR) AS PAGE " +
                        "    FROM PAGET " +
                        "    JOIN CHARTPAGET ON PAGET.PAGENO = CHARTPAGET.PAGENO " +
                        "    JOIN FORMT ON CHARTPAGET.FORMCODE = FORMT.FORMCODE " +
                        "    GROUP BY PAGET.OCRPK, CHARTPAGET.FORMCODE, FORMT.PAGECOUNT " +
                        ") CC ON O.OCRPK = CC.OCRPK " +
                        "INNER JOIN PATIENTT P ON P.PATID = O.PATID " +
                        "INNER JOIN CLINICT C ON O.CLINCODE = C.CLINCODE " +
                        "INNER JOIN FORMT F ON O.FORMCODE = F.FORMCODE " +
                        "INNER JOIN TREATT T ON LEFT(T.OCMNUM,10) = LEFT(O.OCMNUM,10) " +
                        "WHERE O.INDATE BETWEEN ? AND ? "
        );

        List<Object> params = new java.util.ArrayList<>();
        params.add(startDate);
        params.add(endDate);

        if (clinCode != null && !clinCode.isBlank() && !"ALL".equalsIgnoreCase(clinCode)) {
            sql.append("AND O.CLINCODE = ? ");
            params.add(clinCode);
        }
        if (scanYn != null && !scanYn.isBlank() && !"ALL".equalsIgnoreCase(scanYn)) {
            sql.append("AND O.SCANYN = ? ");
            params.add(scanYn);
        }
        if (grpCode != null && !grpCode.isBlank() && !"ALL".equalsIgnoreCase(grpCode)) {
            sql.append("AND F.GRPCODE = ? ");
            params.add(grpCode);
        }
        if (hn != null && !hn.isBlank()) {
            sql.append("AND RTRIM(O.PATID) = RTRIM(?) ");
            params.add(hn);
        }
        if (ocrPk != null && !ocrPk.isBlank()) {
            sql.append("AND RTRIM(O.OCRPK) LIKE ? ");
            params.add("%" + ocrPk.trim() + "%");
        }

        sql.append("ORDER BY O.PATID, O.OCRPK, O.CLINCODE");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public void updateOcrReturnMemo(String ocrPk, String bigo) {
        jdbc.update("UPDATE OCRPRINT SET BIGO=? WHERE RTRIM(OCRPK)=RTRIM(?)", bigo, ocrPk);
    }

}