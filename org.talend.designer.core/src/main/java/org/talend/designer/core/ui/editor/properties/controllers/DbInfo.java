// ============================================================================
//
// Copyright (C) 2006-2007 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.core.ui.editor.properties.controllers;

import java.sql.Connection;

import org.talend.commons.exception.ExceptionHandler;
import org.talend.core.model.metadata.builder.database.EDatabaseDriver4Version;
import org.talend.core.model.metadata.builder.database.ExtractMetaDataUtils;

/**
 * DOC hyWang class global comment. Detailled comment
 */
public class DbInfo {

    private String username = null;

    private String pwd = null;

    private String url = null;

    private String driverClassName = null;

    private String driverJarPath = null;

    private String dbType = null;

    private String dbVersion = null;

    private Connection conn = null;

    public DbInfo(String dbType, String username, String pwd, String dbVersion, String url) {
        this.dbType = dbType;
        this.username = username;
        this.pwd = pwd;
        this.dbVersion = dbType;
        this.url = url;
        generateDriverName();
        getConnFromNode();
        genarateDriverJarPath();
    }

    public String getUrl() {
        return this.url;
    }

    public String getUsername() {
        return this.username;
    }

    public String getDriverClassName() {
        return this.driverClassName;
    }

    public String getDriverJarPath() {
        return this.driverJarPath;
    }

    public String getDbType() {
        return this.dbType;
    }

    public String getDbVersion() {
        return this.dbVersion;
    }

    public Connection getConn() {
        return this.conn;
    }

    public String getPwd() {
        return this.pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public void setDriverJarPath(String driverJarPath) {
        this.driverJarPath = driverJarPath;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public void setDbVersion(String dbVersion) {
        this.dbVersion = dbVersion;
    }

    private void getConnFromNode() {
        try {
            conn = ExtractMetaDataUtils.connect(dbType, url, username, pwd, driverClassName, driverJarPath, dbVersion);
        } catch (Exception e) {
            // e.printStackTrace();
            ExceptionHandler.process(e);
        }
    }

    private void generateDriverName() {
        driverClassName = ExtractMetaDataUtils.getDriverClassByDbType(dbType);
    }

    private void genarateDriverJarPath() {
        String driverName = EDatabaseDriver4Version.getDriver(dbType, dbVersion);
        if (driverName != null) {
            driverJarPath = ExtractMetaDataUtils.getJavaLibPath() + driverName;
        }
    }

}
