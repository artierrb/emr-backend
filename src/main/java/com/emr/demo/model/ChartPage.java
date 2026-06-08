package com.emr.demo.model;

public class ChartPage {
    private long pageNo;
    private long treatNo;
    private String formCode;
    private String formName;
    private long page;
    private String cDate;
    private String grpMid;
    private String extension;
    private String filePath;

    public long getPageNo() { return pageNo; }
    public void setPageNo(long pageNo) { this.pageNo = pageNo; }
    public long getTreatNo() { return treatNo; }
    public void setTreatNo(long treatNo) { this.treatNo = treatNo; }
    public String getFormCode() { return formCode; }
    public void setFormCode(String formCode) { this.formCode = formCode; }
    public String getFormName() { return formName; }
    public void setFormName(String formName) { this.formName = formName; }
    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public String getCDate() { return cDate; }
    public void setCDate(String cDate) { this.cDate = cDate; }
    public String getGrpMid() { return grpMid; }
    public void setGrpMid(String grpMid) { this.grpMid = grpMid; }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
