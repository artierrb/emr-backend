package com.emr.demo.repository;

import com.emr.demo.model.*;
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
                "SELECT FORMCODE,NAME,GRPMID,ORDERBY,CLASS,ACTIVE,OCRYN,PAGECOUNT FROM FORMT WHERE ACTIVE='1' ORDER BY ORDERBY",
                (rs, rn) -> {
                    Form f = new Form();
                    f.setFormCode(rs.getString("FORMCODE")); f.setName(rs.getString("NAME"));
                    f.setGrpMid(rs.getString("GRPMID")); f.setOrderBy(rs.getString("ORDERBY"));
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
                                    String vstNum, String admNum, String userId) {
        jdbc.update(
                "INSERT INTO TREATT(PATID,INDATE,CLINCODE,DOCCODE,CLASS,VSTNUM,ADMNUM) " +
                        "VALUES(?,?,?,?,?,?,?)",
                patId, inDate, clinCode, docCode, classType, vstNum, admNum);
        return jdbc.queryForObject("SELECT @@IDENTITY", Long.class);
    }

    public void deleteTreatment(long treatNo) {
        jdbc.update("DELETE FROM TREATT WHERE TREATNO=?", treatNo);
    }

    // ─── CLINICT ──────────────────────────────────────────────

    public List<Map<String, Object>> searchClinic(String keyword) {
        return jdbc.queryForList(
                "SELECT TOP 50 CLINCODE, NAME FROM CLINICT WHERE ACTIVE='1' AND (NAME LIKE ? OR CLINCODE LIKE ?) ORDER BY NAME",
                "%" + keyword + "%", "%" + keyword + "%");
    }

    // ─── USERT ────────────────────────────────────────────────

    public List<Map<String, Object>> searchUser(String keyword) {
        return jdbc.queryForList(
                "SELECT TOP 50 USERID, NAME FROM USERT WHERE NAME LIKE ? OR USERID LIKE ? ORDER BY NAME",
                "%" + keyword + "%", "%" + keyword + "%");
    }

    // ─── OCR Print ────────────────────────────────────────────────

    public List<Map<String, Object>> getOcrPrintSetup(String inputGubun, String loginUserId, String loginClinic) {
        String gubunCondition = switch (inputGubun) {
            case "D" -> " AND s.INPUTGUBUN='D' AND s.SETUPNAME='" + loginClinic + "'";
            case "U" -> " AND s.INPUTGUBUN='U' AND s.SETUPNAME='" + loginUserId + "'";
            default  -> "";
        };
        String sql =
                "SELECT DISTINCT s.FORMCODE, f.NAME AS FORMNAME, s.INPUTGUBUN, s.SETUPNAME, s.SEQ " +
                        "FROM OCRPRINTSETUPT s " +
                        "JOIN FORMT f ON f.FORMCODE = s.FORMCODE " +
                        "WHERE ISNULL(s.USEYN,'Y')='Y'" + gubunCondition +
                        " ORDER BY s.SEQ, s.FORMCODE";
        return jdbc.queryForList(sql);
    }

    public boolean checkReprint(String ocmNum, String formCode) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM OCRPRINT WHERE OCMNUM=? AND FORMCODE=?",
                Integer.class, ocmNum, formCode);
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
                               String ocmNum, String classType) {
        String now  = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
        jdbc.update(
                "INSERT INTO OCRPRINT(OCRPK,PATID,INDATE,CLINCODE,DOCCODE,FORMCODE,PRINTYN,PRINTUSERID,PRINTDATE,PRINTTIME,OCMNUM,CLASS) " +
                        "VALUES(?,?,?,?,?,?,'Y',?,?,?,?,?)",
                ocrPk, patId, inDate, clinCode, docCode, formCode, userId, now, time,
                ocmNum == null ? "" : String.format("%10s", ocmNum.trim()), classType);
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
                        "FROM IMGEMR.dbo.TabMst WHERE RTRIM(TabCodTyp)=? ORDER BY TabCodNam", tabCodTyp);
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
                "SELECT USERID,NAME,AUTH,EDATE,CLINCODE FROM USERT WHERE " + col + " LIKE ? ORDER BY USERID",
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

    private String str(Object o) { return o == null ? "" : o.toString().trim(); }

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
}