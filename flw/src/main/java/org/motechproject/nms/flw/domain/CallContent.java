package org.motechproject.nms.flw.domain;

import org.joda.time.DateTime;
import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;

@Entity(tableName = "nms_call_content")
public class CallContent {

    @Field
    private Long id; //NOPMD UnusedPrivateField

    @Field
    private CallDetailRecord callDetailRecord;

    @Field
    private String type;

    @Field
    private String mobileKunjiCardNumber;

    @Field
    private String contentName;

    @Field
    private String contentFile;

    @Field
    private DateTime startTime;

    @Field
    private DateTime endTime;

    @Field
    private Boolean completionFlag;

    public CallDetailRecord getCallDetailRecord() {
        return callDetailRecord;
    }

    public void setCallDetailRecord(CallDetailRecord callDetailRecord) {
        this.callDetailRecord = callDetailRecord;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMobileKunjiCardNumber() {
        return mobileKunjiCardNumber;
    }

    public void setMobileKunjiCardNumber(String mobileKunjiCardNumber) {
        this.mobileKunjiCardNumber = mobileKunjiCardNumber;
    }

    public String getContentName() {
        return contentName;
    }

    public void setContentName(String contentName) {
        this.contentName = contentName;
    }

    public String getContentFile() {
        return contentFile;
    }

    public void setContentFile(String contentFile) {
        this.contentFile = contentFile;
    }

    public DateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(DateTime startTime) {
        this.startTime = startTime;
    }

    public DateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(DateTime endTime) {
        this.endTime = endTime;
    }

    public Boolean getCompletionFlag() {
        return completionFlag;
    }

    public void setCompletionFlag(Boolean completionFlag) {
        this.completionFlag = completionFlag;
    }
}
