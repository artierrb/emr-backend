package com.emr.backend.model;

public class Form {
    private String formCode;
    private String name;
    private String grpMid;
    private String grpCode;
    private String orderBy;
    private String classType;
    private String active;
    private String ocrYn;
    private Integer pageCount;

    public String getFormCode() { return formCode; }
    public void setFormCode(String formCode) { this.formCode = formCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGrpMid() { return grpMid; }
    public void setGrpMid(String grpMid) { this.grpMid = grpMid; }
    public String getGrpCode() { return grpCode; }
    public void setGrpCode(String grpCode) { this.grpCode = grpCode; }
    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }
    public String getClassType() { return classType; }
    public void setClassType(String classType) { this.classType = classType; }
    public String getActive() { return active; }
    public void setActive(String active) { this.active = active; }
    public String getOcrYn() { return ocrYn; }
    public void setOcrYn(String ocrYn) { this.ocrYn = ocrYn; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
}
