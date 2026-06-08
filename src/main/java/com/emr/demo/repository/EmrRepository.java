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

    public Long findTreatNo(String patId, String inDate) {
        List<Long> r = jdbc.query(
            "SELECT TOP 1 TREATNO FROM TREATT WHERE PATID=? AND INDATE=? AND CLASS='S' ORDER BY TREATNO DESC",
            (rs, rn) -> rs.getLong("TREATNO"), patId, inDate);
        return r.isEmpty() ? null : r.get(0);
    }

    public void updateScanLastTime(long treatNo) {
        jdbc.update("UPDATE TREATT SET SCANLASTTIME=GETDATE() WHERE TREATNO=?", treatNo);
    }

    // ─── Program Config ───────────────────────────────────────

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
            "SELECT TOP 50 PATID,NAME,JUMINNO,SEX,BIRTHDATE,AGE FROM PATIENTT WHERE " + col + " LIKE ? ORDER BY PATID",
            "%" + keyword.trim() + "%");
    }

    public void insertPatient(String patId, String name, String sex, String jumiNno, String birthDate, int age, String userId) {
        jdbc.update(
            "INSERT INTO PATIENTT(PATID,NAME,SEX,JUMINNO,BIRTHDATE,AGE,CDATE,CUSERID) VALUES(?,?,?,?,?,?,GETDATE(),?)",
            patId, name, sex, jumiNno, birthDate, age, userId);
    }

    public boolean patientExists(String patId) {
        Integer cnt = jdbc.queryForObject("SELECT COUNT(1) FROM PATIENTT WHERE PATID=?", Integer.class, patId);
        return cnt != null && cnt > 0;
    }
}
