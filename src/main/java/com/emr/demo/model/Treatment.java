package com.emr.demo.model;

public class Treatment {
    private long treatNo;
    private String patId;
    private String inDate;
    private String clinCode;
    private String docCode;
    private String classType;
    private String ocmNum;
    private String ward;
    private String vstNum;
    private String admNum;

    public long getTreatNo() { return treatNo; }
    public void setTreatNo(long treatNo) { this.treatNo = treatNo; }
    public String getPatId() { return patId; }
    public void setPatId(String patId) { this.patId = patId; }
    public String getInDate() { return inDate; }
    public void setInDate(String inDate) { this.inDate = inDate; }
    public String getClinCode() { return clinCode; }
    public void setClinCode(String clinCode) { this.clinCode = clinCode; }
    public String getDocCode() { return docCode; }
    public void setDocCode(String docCode) { this.docCode = docCode; }
    public String getClassType() { return classType; }
    public void setClassType(String classType) { this.classType = classType; }
    public String getOcmNum() { return ocmNum; }
    public void setOcmNum(String ocmNum) { this.ocmNum = ocmNum; }
    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }
    public String getVstNum() { return vstNum; }
    public void setVstNum(String vstNum) { this.vstNum = vstNum; }
    public String getAdmNum() { return admNum; }
    public void setAdmNum(String admNum) { this.admNum = admNum; }
}
