package com.emr.demo.model;

public class PathConfig {
    private String pathId;
    private String ipAddress;
    private String localPath;
    private int pathPort;
    private String ftpUser;
    private String ftpPasswd;
    private String alias;
    private String active;

    public String getPathId() { return pathId; }
    public void setPathId(String pathId) { this.pathId = pathId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    public int getPathPort() { return pathPort; }
    public void setPathPort(int pathPort) { this.pathPort = pathPort; }
    public String getFtpUser() { return ftpUser; }
    public void setFtpUser(String ftpUser) { this.ftpUser = ftpUser; }
    public String getFtpPasswd() { return ftpPasswd; }
    public void setFtpPasswd(String ftpPasswd) { this.ftpPasswd = ftpPasswd; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getActive() { return active; }
    public void setActive(String active) { this.active = active; }
}
