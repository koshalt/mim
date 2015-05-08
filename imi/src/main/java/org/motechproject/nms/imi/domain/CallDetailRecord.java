package org.motechproject.nms.imi.domain;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.nms.props.domain.CallStatus;

import java.io.Serializable;

@Entity(tableName = "nms_obd_cdrs")
public class CallDetailRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Field
    private String requestId;

    @Field
    private String serviceId;

    @Field
    private String msisdn;

    @Field
    private String cli;

    @Field
    private Integer priority;

    @Field
    private String callFlowUrl;

    @Field
    private String contentFileName;

    @Field
    private String weekId;

    @Field
    private String languageLocationCode;

    @Field
    private String circle;

    @Field
    private CallStatus finalStatus;

    @Field
    private Integer statusCode;

    @Field
    private Integer attempts;

    static final int NUMBER_OF_FIELDS = 13;

    public CallDetailRecord() { }

    public CallDetailRecord(String requestId, String serviceId, String msisdn, // NO CHECKSTYLE > than 7 params
            String cli, Integer priority, String callFlowUrl, String contentFileName, String weekId,
            String languageLocationCode, String circle, CallStatus finalStatus, Integer statusCode,
                            Integer attempts) {
        this.requestId = requestId;
        this.serviceId = serviceId;
        this.msisdn = msisdn;
        this.cli = cli;
        this.priority = priority;
        this.callFlowUrl = callFlowUrl;
        this.contentFileName = contentFileName;
        this.weekId = weekId;
        this.languageLocationCode = languageLocationCode;
        this.circle = circle;
        this.finalStatus = finalStatus;
        this.statusCode = statusCode;
        this.attempts = attempts;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getCli() {
        return cli;
    }

    public void setCli(String cli) {
        this.cli = cli;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getCallFlowUrl() {
        return callFlowUrl;
    }

    public void setCallFlowUrl(String callFlowUrl) {
        this.callFlowUrl = callFlowUrl;
    }

    public String getContentFileName() {
        return contentFileName;
    }

    public void setContentFileName(String contentFileName) {
        this.contentFileName = contentFileName;
    }

    public String getWeekId() {
        return weekId;
    }

    public void setWeekId(String weekId) {
        this.weekId = weekId;
    }

    public String getLanguageLocationCode() {
        return languageLocationCode;
    }

    public void setLanguageLocationCode(String languageLocationCode) {
        this.languageLocationCode = languageLocationCode;
    }

    public String getCircle() {
        return circle;
    }

    public void setCircle(String circle) {
        this.circle = circle;
    }

    public CallStatus getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(CallStatus finalStatus) {
        this.finalStatus = finalStatus;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public static CallDetailRecord fromLine(String line) {
        String[] fields = line.split(",");
        if (fields.length != NUMBER_OF_FIELDS) {
            throw new IllegalStateException(String.format("Wrong number of fields, expecting %d but seeing %d",
                    NUMBER_OF_FIELDS,
                    fields.length));
        }

        int i = 0;
        return new CallDetailRecord(
                fields[i++], //requestId
                fields[i++], //serviceId
                fields[i++], //msisdn
                fields[i++], //cli
                Integer.parseInt(fields[i++]), //priority - NOTE: may throw a NumberFormatException
                fields[i++], //callFlowUrl
                fields[i++], //contentFileName
                fields[i++], //weekId
                fields[i++], //languageLocationCode
                fields[i++], //circle
                // NOTE: may throw a NumberFormatException or IllegalArgumentException
                CallStatus.fromInt(Integer.parseInt(fields[i++])), //finalStatus
                Integer.parseInt(fields[i++]), //statusCode - NOTE: may throw a NumberFormatException
                Integer.parseInt(fields[i++]) //attempts - NOTE: may throw a NumberFormatException
        );
    }

    @Override
    public String toString() {
        return "CallDetailRecord{" +
                "requestId='" + requestId + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", msisdn='" + msisdn + '\'' +
                " ...}";
    }
}
